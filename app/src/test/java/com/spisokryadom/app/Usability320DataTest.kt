package com.spisokryadom.app

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.backup.BackupManager
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.db.ProductVariantSchema
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.ui.addtolist.BulkProductViewModel
import com.spisokryadom.app.ui.shopcard.ShopCardViewModel
import com.spisokryadom.app.util.ShopPhotos
import com.spisokryadom.app.widget.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import java.io.File
import java.time.Duration
import java.util.UUID
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class Usability320DataTest {
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:AppDatabase
    private lateinit var places:LocationRepository
    private lateinit var products:ProductRepository
    private lateinit var basket:ShoppingListRepository
    private val models=ViewModelStore()
    @Before fun seed():Unit=runBlocking {
        db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build()
        db.cityDao().insert(CityEntity(1,"Город"));db.cityDao().insert(CityEntity(2,"Другой"))
        ProductVariantSchema.install(db.openHelper.writableDatabase)
        val prefs=app.getSharedPreferences("usability320-locations",0);prefs.edit().clear().commit()
        places=LocationRepository(db.cityDao(),db.shopDao(),prefs)
        products=ProductRepository(db.productDao(),db.productShopLinkDao(),db)
        basket=ShoppingListRepository(db.shoppingListEntryDao(),db,places)
    }
    @After fun close(){models.clear();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();places.closeForTest();db.close()}
    private fun await(check:()->Boolean){val end=System.nanoTime()+20_000_000_000;while(!check()&&System.nanoTime()<end){Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(20));Thread.sleep(5)};assertTrue(check())}

    @Test fun widgetEyePersistsForTheFamilyAndNeverChangesPurchases():Unit=runBlocking {
        val id=products.saveWithLinks(ProductEntity(name="Личный товар",isWidgetPrivate=true),emptyList())
        val variant=products.variants.create(id,"Другая марка")
        basket.addProduct(variant,2.0,exactVariant=true)
        val before=db.shoppingListEntryDao().getAllSync()
        val stable=db.productDao().getById(variant)!!.stableId
        assertTrue(products.setWidgetPrivacy(stable,true,false))
        assertFalse(products.setWidgetPrivacy(stable,true,false)) // Cached button is idempotent.
        assertTrue(db.productDao().getAllSync().all{!it.isWidgetPrivate})
        WidgetInteractionState.clear()
        assertFalse(ShoppingWidgetData(db,basket,places).rows().single().isPrivate)
        assertTrue(products.setWidgetPrivacy(stable,false,true))
        assertTrue(db.productDao().getAllSync().all{it.isWidgetPrivate})
        assertEquals(before,db.shoppingListEntryDao().getAllSync())
    }

    @Test fun tenCachedChecksCommitWithoutUndoingAnotherItem():Unit=runBlocking {
        (1..10).forEach{val id=products.saveWithLinks(ProductEntity(name="Товар $it"),emptyList());basket.addProduct(id)}
        val data=ShoppingWidgetData(db,basket,places);val cached=data.rows()
        cached.forEach{assertTrue(data.check(it))}
        cached.reversed().forEach{assertFalse(data.check(it))}
        assertEquals(10,db.shoppingListEntryDao().getAllSync().size)
        assertTrue(db.shoppingListEntryDao().getAllSync().all{it.isBought})
        assertTrue(data.presentation(WidgetSettings(showBought=false)).isEmpty())
    }

    @Test fun bulkDraftKeepsMixedChoicesAndNewNameAcrossBackAndNewViewModel():Unit=runBlocking {
        val bread=db.productDao().insert(ProductEntity(name="Хлеб",defaultUnit="шт"))
        val repo=BulkPurchaseRepository(db,basket,places)
        val handle=SavedStateHandle();val prefs=app.getSharedPreferences("usability320-bulk",0);prefs.edit().clear().commit()
        val model=BulkProductViewModel(repo,handle,store=prefs);models.put("first",model)
        model.text("Хлеб, Белый хлеб. Особый товар");model.prepare();await{model.state.value.preview&&!model.state.value.busy}
        assertEquals(bread,model.state.value.rows[0].productId)
        model.selectFor(1,model.state.value.matches.getValue(1).single{it.id==bread})
        model.selectFor(2,null);model.newName(2,"Особый товар для дома");model.quantity(2,"3")
        val chosen=model.state.value.rows
        model.choose(1);await{!model.state.value.searching};model.back()
        assertTrue(model.state.value.preview);assertEquals(chosen,model.state.value.rows)
        model.back();assertEquals("Хлеб, Белый хлеб. Особый товар",model.state.value.text)
        model.prepare();await{!model.state.value.busy};assertEquals(chosen,model.state.value.rows)
        val fresh=BulkProductViewModel(repo,SavedStateHandle(prefs.all),store=prefs);models.put("second",fresh)
        await{fresh.state.value.matches.isNotEmpty()};assertEquals(chosen,fresh.state.value.rows)
        assertTrue(db.shoppingListEntryDao().getAllSync().isEmpty())
        fresh.add(null,null);await{!fresh.state.value.busy};assertNull(fresh.state.value.error)
        val added=db.shoppingListEntryDao().getAllSync();assertEquals(2,added.size);assertEquals(2.0,added.single{it.productId==bread}.quantity,0.0)
        val created=db.productDao().getAllSync().single{it.name=="Особый товар для дома"}
        assertEquals(3.0,added.single{it.productId==created.id}.quantity,0.0)
        fresh.consumeCompletion();assertEquals("",JSONObject(prefs.getString("draft","")!!).getString("text"))
    }

    @Test fun orderedShopGalleryRoundTripsFilesCaptionsAndMissingMedia():Unit=runBlocking {
        val a=File(app.filesDir,"shop_photos/first.jpg").also{it.parentFile!!.mkdirs();it.writeBytes(byteArrayOf(1,3,5))}
        val b=File(app.filesDir,"shop_photos/second.jpg").also{it.writeBytes(byteArrayOf(2,4,6))}
        val photos=listOf(ProductPhotoEntity(0,0,b.path,"Вывеска"),ProductPhotoEntity(0,1,a.path,"Вход"),ProductPhotoEntity(0,2,"/missing-shop-photo.jpg","Прилавок"))
        val id=db.shopDao().insert(ShopEntity(name="Павильон",photosJson=ShopPhotos.encode(photos),logoUri=null))
        val manager=BackupManager(app,db);val file=manager.exportFile()
        assertEquals(1,manager.inspect(file).missingPhotos)
        ZipFile(file).use{zip->
            val j=JSONObject(zip.getInputStream(zip.getEntry("manifest.json")).reader().readText())
            assertEquals(28,j.getInt("version"));assertFalse(j.toString().contains(a.path))
            assertNotNull(zip.getEntry("shop_photos/${id}_0.jpg"));assertArrayEquals(b.readBytes(),zip.getInputStream(zip.getEntry("shop_photos/${id}_0.jpg")).readBytes())
        }
        manager.importFile(file)
        val restored=ShopPhotos.decode(db.shopDao().getById(id)!!.photosJson)
        assertEquals(listOf("Вывеска","Вход","Прилавок"),restored.map{it.caption})
        assertArrayEquals(b.readBytes(),File(restored[0].uri).readBytes());assertArrayEquals(a.readBytes(),File(restored[1].uri).readBytes())
        assertEquals("unavailable",restored[2].uri)
        val before=db.shopDao().getById(id)!!;val invalid=before.copy(photosJson=ShopPhotos.encode(restored.reversed()))
        db.shopDao().update(invalid);assertEquals("Прилавок",ShopPhotos.decode(db.shopDao().getById(id)!!.photosJson)[0].caption)
    }

    @Test fun shopPhotoEditorSaveCancelAndCityProfilesStayIndependent():Unit=runBlocking {
        val id=db.shopDao().insert(ShopEntity(name="Магазин",logoUri="/logo.png",cityGroupKey="shared"))
        val repo=ShopRepository(db.shopDao(),db.shopDepartmentDao(),places,db)
        val model=ShopCardViewModel(id,repo);models.put("shop",model);await{!model.uiState.value.isLoading}
        val gallery=listOf(ProductPhotoEntity(0,0,"/shop.jpg","Вход"))
        model.updatePhotos(gallery)
        assertEquals("[]",db.shopDao().getById(id)!!.photosJson) // Explicit Save remains required.
        model.updateCity(2);await{!model.uiState.value.isLoading};assertTrue(model.uiState.value.photos.isEmpty())
        model.updateCity(1);await{!model.uiState.value.isLoading};assertEquals(gallery,model.uiState.value.photos)
        model.save();await{model.uiState.value.isSaved||model.uiState.value.error!=null};assertNull(model.uiState.value.error)
        val saved=db.shopDao().getById(id)!!;assertEquals(gallery,ShopPhotos.decode(saved.photosJson));assertEquals("/logo.png",saved.logoUri)
    }

    @Test fun migration28KeepsExistingProductsPurchasesLogoAndPointPhotos():Unit=runBlocking {
        val name="usability320-${UUID.randomUUID()}.db"
        app.openOrCreateDatabase(name,0,null).use{old->
            val entities=JSONObject(javaClass.classLoader!!.getResourceAsStream("schema-v28.json")!!.reader().readText()).getJSONObject("database").getJSONArray("entities")
            for(i in 0 until entities.length()){
                val e=entities.getJSONObject(i);val table=e.getString("tableName")
                old.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",table))
                val indices=e.getJSONArray("indices");for(j in 0 until indices.length())old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))
            }
            old.execSQL("INSERT INTO cities(id,name)VALUES(1,'Город')")
            old.execSQL("INSERT INTO shops(id,name,cityId,logoUri,displayOrder,manualOrderOverride)VALUES(73,'Магазин',1,'/logo.png',0,0)")
            old.execSQL("INSERT INTO shop_branches(id,shopId,address,photoUri,displayOrder)VALUES(74,73,'Адрес','/point.jpg',0)")
            old.execSQL("INSERT INTO products(id,name,purchaseType,isTemporary,stableId,isWidgetPrivate)VALUES(99,'Товар','offline',0,'usability320',1)")
            old.execSQL("INSERT INTO shopping_list_entries(id,productId,quantity,unit,isBought,isUrgent,createdAt,updatedAt)VALUES(118,99,3,'шт',1,0,100,200)")
            old.version=28
        }
        val migrated=Room.databaseBuilder(app,AppDatabase::class.java,name).allowMainThreadQueries().addMigrations(AppDatabase.MIGRATION_28_29,AppDatabase.MIGRATION_29_30).build()
        try{
            assertEquals(30,migrated.openHelper.writableDatabase.version)
            assertEquals("[]",migrated.shopDao().getById(73)!!.photosJson);assertEquals("/logo.png",migrated.shopDao().getById(73)!!.logoUri)
            assertEquals("/point.jpg",migrated.shopBranchDao().getAll().single{it.id==74L}.photoUri)
            assertTrue(migrated.productDao().getById(99)!!.isWidgetPrivate);assertEquals(3.0,migrated.shoppingListEntryDao().getById(118)!!.quantity,0.0)
            migrated.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
        }finally{migrated.close();app.deleteDatabase(name)}
    }

    @Test fun invalidShopPhotoArchiveFailsBeforeReplacingAnyData():Unit=runBlocking {
        val id=db.shopDao().insert(ShopEntity(name="Магазин",photosJson=ShopPhotos.encode(listOf(ProductPhotoEntity(0,0,"/missing.jpg","Вход")))))
        val manager=BackupManager(app,db);val file=manager.exportFile();val before=db.shopDao().getAllSync()
        for(mode in 0..2){
            val changed=File(app.cacheDir,"tampered-$mode.zip")
            ZipFile(file).use{z->ZipOutputStream(changed.outputStream()).use{out->z.entries().asSequence().forEach{e->
                out.putNextEntry(ZipEntry(e.name));val bytes=z.getInputStream(e).readBytes()
                if(e.name=="manifest.json"){
                    val root=JSONObject(String(bytes));val row=root.getJSONObject("data").getJSONArray("shops").getJSONObject(0)
                    when(mode){0->row.remove("shopPhotos");1->row.getJSONArray("shopPhotos").getJSONObject(0).put("photo","shop_photos/${id}_0.jpg");else->row.getJSONArray("shopPhotos").getJSONObject(0).put("position",2)}
                    out.write(root.toString().toByteArray())
                }else out.write(bytes);out.closeEntry()
            }}}
            try{manager.importFile(changed);fail("Malformed gallery must be rejected")}catch(expected:IllegalArgumentException){}
            assertEquals(before,db.shopDao().getAllSync())
        }
    }
}
