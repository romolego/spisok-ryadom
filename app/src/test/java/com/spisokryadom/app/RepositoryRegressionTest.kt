package com.spisokryadom.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.data.backup.BackupManager
import kotlinx.coroutines.*
import org.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.File
import java.util.UUID
import java.util.zip.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class RepositoryRegressionTest {
    private lateinit var context:Context
    private lateinit var db:AppDatabase
    private lateinit var locations:LocationRepository
    private lateinit var basket:ShoppingListRepository
    private lateinit var backup:BackupManager
    @Before fun setup()=runBlocking {
        context=ApplicationProvider.getApplicationContext<Application>()
        listOf("location_prefs","shopping_list_prefs","product_db_prefs").forEach{context.getSharedPreferences(it,0).edit().clear().commit()}
        db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).allowMainThreadQueries().build()
        db.cityDao().insert(CityEntity(1,"Иркутск"));db.cityDao().insert(CityEntity(2,"Красноярск"))
        locations=LocationRepository(db.cityDao(),db.shopDao(),context.getSharedPreferences("location_prefs",0))
        basket=ShoppingListRepository(db.shoppingListEntryDao(),db,locations)
        backup=BackupManager(context,db)
    }
    @After fun close(){locations.closeForTest();db.close()}
    private suspend fun product(name:String="Яблоки Гала",temporary:Boolean=false)=db.productDao().insert(ProductEntity(name=name,isTemporary=temporary))
    private suspend fun shop(name:String,city:Long)=db.shopDao().insert(ShopEntity(name=name,cityId=city))
    private suspend fun seed():Triple<Long,Long,Long>{
        val p=product();val a=shop("Слата",1);val b=shop("Батон",2)
        db.productShopLinkDao().insert(ProductShopLinkEntity(productId=p,shopId=a,priority=1))
        db.productShopLinkDao().insert(ProductShopLinkEntity(productId=p,shopId=b,priority=2))
        return Triple(p,a,b)
    }
    private suspend fun rejected(block:suspend()->Unit){try{block();fail("Expected validation failure")}catch(e:IllegalArgumentException){}catch(e:IllegalStateException){}}
    @Test fun cityRoutingPreservesExistingPurchases()=runBlocking {
        val(p,a,b)=seed();val first=basket.addProduct(p,2.0,"кг");assertEquals(a,basket.getEntryById(first)?.assignedShopId)
        locations.select(2);val second=basket.addProduct(p,3.0,"кг");assertEquals(b,basket.getEntryById(second)?.assignedShopId)
        assertNotEquals(first,second);assertEquals(2,db.shoppingListEntryDao().getAllSync().size)
        assertEquals(a,basket.getEntryById(first)?.assignedShopId)
        rejected{basket.addProduct(p,choice=ShopChoice.EXPLICIT,shopId=a)}
        basket.movePendingToCurrentCity();assertTrue(db.shoppingListEntryDao().getAllSync().all{it.assignedShopId==b})
    }
    @Test fun compatibleMergingSeparatesUnitsAndStores()=runBlocking {
        val(p,a,_)=seed();val one=basket.addProduct(p,1.0,"кг");assertEquals(one,basket.addProduct(p,.5,"кг."));assertEquals(1.5,basket.getEntryById(one)!!.quantity,0.0)
        val two=basket.addProduct(p,2.0,"шт");assertNotEquals(one,two)
        val unassigned=basket.addProduct(p,2.0,"кг",ShopChoice.NONE);assertNotEquals(one,unassigned)
        basket.markBought(one);assertNotEquals(one,basket.addProduct(p,1.0,"кг",ShopChoice.EXPLICIT,a))
    }
    @Test fun concurrentTapsAreNotLost()=runBlocking {
        val p=product();val id=basket.addProduct(p,1.0,"шт")
        coroutineScope{repeat(40){launch(Dispatchers.Default){basket.adjustQuantity(id,1.0)}}}
        assertEquals(41.0,basket.getEntryById(id)!!.quantity,0.0)
        coroutineScope{repeat(40){launch(Dispatchers.Default){basket.adjustQuantity(id,-1.0)}}}
        assertEquals(1.0,basket.getEntryById(id)!!.quantity,0.0)
        coroutineScope{repeat(20){launch(Dispatchers.Default){basket.toggleUrgent(id)}}}
        assertFalse(basket.getEntryById(id)!!.isUrgent)
    }
    @Test fun temporaryLifecycleKeepsCatalogClean()=runBlocking {
        val entry=basket.addNewProduct("Разовый товар",1.0,"шт",false);val p=basket.getEntryById(entry)!!.productId
        assertTrue(db.productDao().getById(p)!!.isTemporary);basket.markBought(entry);basket.clearBoughtEntries();assertNull(db.productDao().getById(p))
        val permanent=basket.addNewProduct("Нужный товар",1.0,"шт",true);val id=basket.getEntryById(permanent)!!.productId
        basket.removeEntry(permanent);assertNotNull(db.productDao().getById(id))
    }
    @Test fun productAndLinksSaveAtomically()=runBlocking {
        val(p,a,b)=seed();val dept=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=a,name="Фрукты"))
        val repo=ProductRepository(db.productDao(),db.productShopLinkDao(),db)
        val original=db.productDao().getById(p)!!
        rejected{repo.saveWithLinks(original.copy(name="Испорчено"),listOf(ProductShopLinkEntity(productId=p,shopId=b,departmentId=dept)))}
        assertEquals(original,db.productDao().getById(p));assertEquals(2,db.productShopLinkDao().getLinksForProductSync(p).size)
        repo.saveWithLinks(original.copy(name="Гала"),listOf(ProductShopLinkEntity(productId=p,shopId=a,departmentId=dept)))
        assertEquals("Гала",db.productDao().getById(p)?.name);assertEquals(1,db.productShopLinkDao().getLinksForProductSync(p).size)
    }
    @Test fun shopAndDepartmentsSaveAtomically()=runBlocking {
        val a=shop("Слата",1);val repo=ShopRepository(db.shopDao(),db.shopDepartmentDao(),locations,db)
        val dept=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=a,name="Фрукты"));val original=db.shopDao().getById(a)!!
        rejected{repo.saveWithDepartments(original.copy(name="Плохое имя"),listOf(ShopDepartmentEntity(shopId=a,name="")))}
        assertEquals(original,db.shopDao().getById(a));assertNotNull(db.shopDepartmentDao().getById(dept))
        repo.saveWithDepartments(original.copy(name="Новое имя"),listOf(ShopDepartmentEntity(shopId=a,name="Овощи")))
        assertEquals("Новое имя",db.shopDao().getById(a)?.name);assertNull(db.shopDepartmentDao().getById(dept))
    }
    @Test fun fullBackupRoundTripIncludesBasketPhotosCitiesAndPreferences()=runBlocking {
        val(p,a,_)=seed();val image=File(context.filesDir,"product_photos/test.jpg").also{it.parentFile!!.mkdirs();it.writeBytes(byteArrayOf(1,2,3,4))}
        val original=db.productDao().getById(p)!!.copy(photoUri=image.absolutePath);db.productDao().update(original)
        val entry=basket.addProduct(p,.5,"кг");basket.toggleUrgent(entry);val temp=basket.addNewProduct("Разовый",3.0,"шт",false)
        context.getSharedPreferences("shopping_list_prefs",0).edit().putBoolean("global_boost_urgent_shop",false).commit();locations.select(2)
        val file=backup.exportFile();assertTrue(backup.inspect(file).full)
        basket.clearBoughtEntries();db.productDao().insert(ProductEntity(name="Лишний"));locations.select(1)
        backup.importFile(file)
        val restored=db.productDao().getById(p)!!;assertEquals(original.stableId,restored.stableId);assertArrayEquals(image.readBytes(),File(restored.photoUri!!).readBytes())
        assertEquals(2,db.productDao().getAllSync().size);assertTrue(basket.getEntryById(entry)!!.isUrgent);assertNotNull(basket.getEntryById(temp));assertEquals(2L,locations.currentId())
        assertFalse(context.getSharedPreferences("shopping_list_prefs",0).getBoolean("global_boost_urgent_shop",true));assertEquals(2,db.cityDao().getAllSync().size)
    }
    private fun archive(manifest:JSONObject,extras:Map<String,ByteArray> = emptyMap()):File {
        val file=File(context.cacheDir,"test_${UUID.randomUUID()}.zip")
        ZipOutputStream(file.outputStream()).use{zip->(mapOf("manifest.json" to manifest.toString().toByteArray())+extras).forEach{(name,data)->zip.putNextEntry(ZipEntry(name));zip.write(data);zip.closeEntry()}}
        return file
    }
    private fun manifest(file:File):JSONObject=ZipFile(file).use{z->JSONObject(z.getInputStream(z.getEntry("manifest.json")).reader().readText())}
    @Test fun corruptBackupCannotDeleteExistingDataOrPhotos()=runBlocking {
        val p=product();val image=File(context.filesDir,"product_photos/keep.jpg").also{it.parentFile!!.mkdirs();it.writeBytes(byteArrayOf(9))};db.productDao().update(db.productDao().getById(p)!!.copy(photoUri=image.absolutePath));val entry=basket.addProduct(p)
        val root=manifest(backup.exportFile());root.getJSONObject("data").getJSONArray("shoppingListEntries").getJSONObject(0).put("productId",999)
        // A missing photo is detected before committing as well.
        rejected{backup.importFile(archive(root))};assertNotNull(basket.getEntryById(entry));assertArrayEquals(byteArrayOf(9),image.readBytes())
        rejected{backup.importFile(archive(root,mapOf("../escape.jpg" to byteArrayOf(1))))};assertEquals(1,db.productDao().getAllSync().size)
    }
    @Test fun databaseWriteFailureRollsBackRestoreAndKeepsOldPhotos()=runBlocking {
        val p=product();val image=File(context.filesDir,"product_photos/original.jpg").also{it.parentFile!!.mkdirs();it.writeBytes(byteArrayOf(8,7,6))}
        val original=db.productDao().getById(p)!!.copy(photoUri=image.absolutePath);db.productDao().update(original);val entry=basket.addProduct(p)
        val root=manifest(backup.exportFile());root.getJSONObject("data").getJSONArray("products").getJSONObject(0).put("name","Replacement")
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_restore BEFORE INSERT ON products WHEN NEW.name='Replacement' BEGIN SELECT RAISE(ABORT,'Injected write failure'); END")
        try{backup.importFile(archive(root,mapOf("photos/${original.stableId}.jpg" to image.readBytes())));fail("Expected database failure")}catch(e:android.database.sqlite.SQLiteException){}
        assertEquals(original,db.productDao().getById(p));assertNotNull(basket.getEntryById(entry));assertArrayEquals(byteArrayOf(8,7,6),image.readBytes())
    }
    @Test fun classifierRenameAndOrderingAreConsistent()=runBlocking {
        val repo=ClassifierRepository(db.productGroupDao(),db.recipientDao(),db.unitDao(),db)
        val unit=db.unitDao().insert(UnitEntity(name="пак"));val p=db.productDao().insert(ProductEntity(name="Крупа",defaultUnit="пак"))
        repo.renameUnitAndProducts(db.unitDao().getById(unit)!!,"уп");assertEquals("уп",db.productDao().getById(p)?.defaultUnit)
        val second=db.unitDao().insert(UnitEntity(name="шт",displayOrder=1));rejected{repo.renameUnitAndProducts(db.unitDao().getById(unit)!!,"шт")}
        assertEquals("уп",db.productDao().getById(p)?.defaultUnit);repo.moveUnit(second,-1);assertEquals(second,db.unitDao().getAllSync().first().id)
        repo.deleteUnitAndProducts(db.unitDao().getById(unit)!!);assertNull(db.productDao().getById(p)?.defaultUnit)
    }
    @Test fun legacyNumericIdCollisionDoesNotOverwritePurchasedProduct()=runBlocking {
        val p=product("Существующий");val entry=basket.addProduct(p,4.0,"шт")
        val root=JSONObject().put("version",3).put("data",JSONObject().put("products",JSONArray().put(JSONObject().put("id",p).put("name","Другой товар").put("purchaseType","offline"))).put("shops",JSONArray()))
        val file=File(context.cacheDir,"legacy.json").also{it.writeText(root.toString())}
        backup.importFile(file);assertEquals("Существующий",db.productDao().getById(basket.getEntryById(entry)!!.productId)?.name);assertEquals(2,db.productDao().getAllSync().size)
        backup.importFile(file);assertEquals(2,db.productDao().getAllSync().size)
    }
}
