package com.spisokryadom.app

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.backup.*
import com.spisokryadom.app.data.db.*
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.ui.productcard.*
import com.spisokryadom.app.ui.shoppinglist.ViewMode
import com.spisokryadom.app.widget.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import java.io.File
import java.util.UUID
import java.util.zip.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class Widget310DataTest {
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:AppDatabase
    private lateinit var places:LocationRepository
    private lateinit var products:ProductRepository
    private lateinit var basket:ShoppingListRepository
    private val models=ViewModelStore()
    private val prefs get()=app.getSharedPreferences("310-editing",0)
    private val drafts get()=app.getSharedPreferences("310-drafts",0)
    private fun await(test:()->Boolean){val end=System.nanoTime()+25_000_000_000;while(!test()&&System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5)};assertTrue(test())}
    @Before fun setup():Unit=runBlocking {
        prefs.edit().clear().putBoolean("autosave_product_cards",false).commit();drafts.edit().clear().commit()
        app.getSharedPreferences("product_card_drafts",0).edit().clear().commit()
        db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build();RetailConstraints.install(db.openHelper.writableDatabase)
        db.cityDao().insert(CityEntity(1,"Город"))
        places=LocationRepository(db.cityDao(),db.shopDao(),app.getSharedPreferences("310-location",0))
        products=ProductRepository(db.productDao(),db.productShopLinkDao(),db);basket=ShoppingListRepository(db.shoppingListEntryDao(),db,places)
    }
    @After fun close(){models.clear();await{!ProductEditorTransactions.mutex.isLocked};places.closeForTest();db.close()}
    private fun card(id:Long)=ProductCardViewModel(id,-1,products,ShopRepository(db.shopDao(),db.shopDepartmentDao(),places,db),ClassifierRepository(db.productGroupDao(),db.recipientDao(),db.unitDao(),db),drafts,prefs).also{models.put("card-$id",it);await{!it.uiState.value.isLoading}}
    @Test fun cardSaveDraftRecoveryAndVariantSwitchKeepSharedPrivacy():Unit=runBlocking {
        val root=products.saveWithLinks(ProductEntity(name="Личный товар"),emptyList());val child=products.variants.create(root,"Марка Б")
        val vm=card(root);vm.widgetPrivacy(true)
        val raw=ProductEditDraft(db.productDao().getById(root)!!.stableId,root,false,false,vm.uiState.value)
        assertTrue(ProductEditDraft.decode(raw.encode()).state.isWidgetPrivate)
        vm.save();await{vm.uiState.value.isSaved}
        assertTrue(db.productDao().getById(root)!!.isWidgetPrivate);assertTrue(db.productDao().getById(child)!!.isWidgetPrivate)
        val reopened=card(child);assertTrue(reopened.uiState.value.isWidgetPrivate)
        reopened.widgetPrivacy(false);reopened.save();await{reopened.uiState.value.isSaved}
        assertTrue(db.productDao().getAllSync().none{it.isWidgetPrivate})
        assertEquals(setOf(root,child),db.productDao().getAllSync().map{it.id}.toSet())
    }
    @Test fun oldRawDraftDoesNotSilentlyClearNewPrivacyOnRecovery():Unit=runBlocking {
        val id=products.saveWithLinks(ProductEntity(name="Личный товар",isWidgetPrivate=true),emptyList())
        val vm=card(id);val p=db.productDao().getById(id)!!
        val json=JSONObject(ProductEditDraft(p.stableId,id,false,false,vm.uiState.value.copy(note="Незавершённая заметка")).encode()).apply{remove("isWidgetPrivate")}
        val old=ProductEditDraft.decode(json.toString())
        assertTrue(old.applyTo(vm.uiState.value).isWidgetPrivate)
        old.persist(products)
        assertTrue(db.productDao().getById(id)!!.isWidgetPrivate);assertEquals("Незавершённая заметка",db.productDao().getById(id)!!.note)
    }
    @Test fun everyWidgetModeKeepsActualPrivateVariantsAndCasStillRejectsRepeat():Unit=runBlocking {
        val id=products.saveWithLinks(ProductEntity(name="Личный товар",isWidgetPrivate=true),emptyList());val child=products.variants.create(id,"Марка Б")
        val purchase=basket.addProduct(child);val data=ShoppingWidgetData(db,basket,places)
        for(mode in ViewMode.entries){
            val rows=data.presentation(WidgetSettings(mode=mode)).filterIsInstance<WidgetItem.Purchase>()
            assertEquals(listOf(purchase),rows.map{it.row.id});assertTrue(rows.single().row.isPrivate)
            assertEquals(db.productDao().getById(child)!!.stableId,rows.single().row.productStableId)
        }
        val before=db.shoppingListEntryDao().getById(purchase)!!;val row=data.rows().single()
        assertTrue(data.check(row));assertFalse(data.check(row));assertTrue(db.shoppingListEntryDao().getById(purchase)!!.isBought)
        assertEquals(before.quantity,db.shoppingListEntryDao().getById(purchase)!!.quantity,0.0)
        assertTrue(data.presentation(WidgetSettings(showBought=false)).isEmpty());assertEquals(1,db.shoppingListEntryDao().getAllSync().size)
    }
    private fun manifest(f:File)=ZipFile(f).use{JSONObject(it.getInputStream(it.getEntry("manifest.json")).reader().readText())}
    @Test fun legacyCatalogueWithoutFlagKeepsLocalPrivacyButExplicitFlagCanChangeIt():Unit=runBlocking {
        val id=products.saveWithLinks(ProductEntity(name="Личный товар",isWidgetPrivate=true),emptyList())
        val encoded=BackupJson.encode(db.productDao().getById(id)!!).apply{remove("isWidgetPrivate");put("note","Из старого каталога")}
        val root=JSONObject().put("version",1).put("data",JSONObject().put("products",org.json.JSONArray().put(encoded)))
        val file=File(app.cacheDir,"widget310-legacy.json");file.writeText(root.toString())
        val manager=BackupManager(app,db);manager.importFile(file)
        assertTrue(db.productDao().getById(id)!!.isWidgetPrivate);assertEquals("Из старого каталога",db.productDao().getById(id)!!.note)
        val stable=db.productDao().getById(id)!!.stableId
        encoded.remove("stableId");file.writeText(root.toString());manager.importFile(file)
        assertEquals("Old metadata matching must not duplicate a private product",1,db.productDao().getAllSync().size)
        assertTrue(db.productDao().getById(id)!!.isWidgetPrivate)
        encoded.put("stableId",stable).put("isWidgetPrivate",false);file.writeText(root.toString());manager.importFile(file)
        assertFalse(db.productDao().getById(id)!!.isWidgetPrivate);assertEquals(1,db.productDao().getAllSync().size)
    }
    private fun altered(root:JSONObject)=File(app.cacheDir,"widget310-${UUID.randomUUID()}.zip").also{file->ZipOutputStream(file.outputStream()).use{z->z.putNextEntry(ZipEntry("manifest.json"));z.write(root.toString().toByteArray());z.closeEntry()}}
    @Test fun zipRoundTripOldZipAndMalformedPrivacyPreserveData():Unit=runBlocking {
        val id=products.saveWithLinks(ProductEntity(name="Личный товар",isWidgetPrivate=true),emptyList());val entry=basket.addProduct(id)
        val manager=BackupManager(app,db);val file=manager.exportFile();val json=manifest(file)
        assertEquals(27,json.getInt("version"));assertTrue(json.getJSONObject("data").getJSONArray("products").getJSONObject(0).getBoolean("isWidgetPrivate"))
        db.productDao().update(db.productDao().getById(id)!!.copy(isWidgetPrivate=false));manager.importFile(file)
        assertTrue(db.productDao().getById(id)!!.isWidgetPrivate);assertEquals(entry,db.shoppingListEntryDao().getAllSync().single().id)
        val expected=db.productDao().getAllSync()
        val bad=JSONObject(json.toString()).apply{getJSONObject("data").getJSONArray("products").getJSONObject(0).put("isWidgetPrivate","true")}
        assertTrue(runCatching{manager.importFile(altered(bad))}.isFailure);assertEquals(expected,db.productDao().getAllSync())
        val missing=JSONObject(json.toString()).apply{getJSONObject("data").getJSONArray("products").getJSONObject(0).remove("isWidgetPrivate")}
        assertTrue(runCatching{manager.importFile(altered(missing))}.isFailure);assertEquals(expected,db.productDao().getAllSync())
        missing.put("version",25);manager.importFile(altered(missing))
        assertFalse(db.productDao().getById(id)!!.isWidgetPrivate);assertEquals(entry,db.shoppingListEntryDao().getAllSync().single().id)
    }
    @Test fun previousDatabaseMigratesAndPrivacySurvivesCloseAndReopen():Unit=runBlocking {
        val name="widget310-${UUID.randomUUID()}.db"
        app.openOrCreateDatabase(name,0,null).use{old->
            val entities=JSONObject(javaClass.classLoader!!.getResourceAsStream("schema-v27.json")!!.reader().readText()).getJSONObject("database").getJSONArray("entities")
            for(i in 0 until entities.length()){
                val e=entities.getJSONObject(i);val table=e.getString("tableName");old.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",table))
                val indices=e.getJSONArray("indices");for(j in 0 until indices.length())old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))
            }
            old.execSQL("INSERT INTO cities(id,name)VALUES(1,'Город')")
            old.execSQL("INSERT INTO products(id,name,purchaseType,isTemporary,stableId,photoUri)VALUES(40,'Личный товар','offline',0,'widget310-root','/missing.jpg'),(41,'Личный товар','offline',0,'widget310-child',NULL)")
            old.execSQL("INSERT INTO product_variants(productId,familyId,label,displayOrder)VALUES(40,40,'Основной',0),(41,40,'Другая марка',1)")
            old.execSQL("INSERT INTO shopping_list_entries(id,productId,quantity,unit,isBought,isUrgent,createdAt,updatedAt)VALUES(60,41,2,'шт',1,0,100,200)")
            old.version=27
        }
        val migrated=Room.databaseBuilder(app,AppDatabase::class.java,name).allowMainThreadQueries().addMigrations(AppDatabase.MIGRATION_27_28,AppDatabase.MIGRATION_28_29).build()
        try {
            assertEquals(29,migrated.openHelper.writableDatabase.version);assertTrue(migrated.productDao().getAllSync().none{it.isWidgetPrivate})
            val before=migrated.shoppingListEntryDao().getById(60)!!;assertEquals(41L,before.productId);assertTrue(before.isBought)
            migrated.productDao().update(migrated.productDao().getById(41)!!.copy(isWidgetPrivate=true))
            assertTrue(migrated.productDao().getById(40)!!.isWidgetPrivate);assertEquals(before,migrated.shoppingListEntryDao().getById(60))
            migrated.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
        }finally{migrated.close()}
        val reopened=Room.databaseBuilder(app,AppDatabase::class.java,name).allowMainThreadQueries().build()
        try{assertTrue(reopened.productDao().getAllSync().all{it.isWidgetPrivate});assertEquals("/missing.jpg",reopened.productDao().getById(40)!!.photoUri);assertEquals(200L,reopened.shoppingListEntryDao().getById(60)!!.updatedAt)}finally{reopened.close();app.deleteDatabase(name)}
    }
}
