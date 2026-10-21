package com.spisokryadom.app

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.backup.BackupManager
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.db.RetailConstraints
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.ui.addtolist.AddToListViewModel
import com.spisokryadom.app.ui.bulk.ShoppingTextViewModel
import com.spisokryadom.app.ui.shopcard.ShopCardViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.*
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class Audit201DataTest {
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:AppDatabase
    private lateinit var locations:LocationRepository
    private lateinit var products:ProductRepository
    private lateinit var shops:ShopRepository
    private lateinit var basket:ShoppingListRepository
    private lateinit var groups:ClassifierRepository
    private val store=ViewModelStore()
    private val prefs get()=app.getSharedPreferences("shopping_list_prefs",0)
    private var shop=0L;private var product=0L
    @Before fun setup():Unit=runBlocking {
        prefs.edit().clear().commit()
        db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build()
        RetailConstraints.install(db.openHelper.writableDatabase)
        db.cityDao().insert(CityEntity(1,"Город"))
        locations=LocationRepository(db.cityDao(),db.shopDao(),app.getSharedPreferences("location_prefs",0))
        products=ProductRepository(db.productDao(),db.productShopLinkDao(),db)
        shops=ShopRepository(db.shopDao(),db.shopDepartmentDao(),locations,db)
        basket=ShoppingListRepository(db.shoppingListEntryDao(),db,locations)
        groups=ClassifierRepository(db.productGroupDao(),db.recipientDao(),db.unitDao(),db)
        shop=db.shopDao().insert(ShopEntity(name="Магазин"))
        product=products.saveWithLinks(ProductEntity(name="Яблоки",defaultUnit="кг"),listOf(ProductShopLinkEntity(productId=0,shopId=shop)))
    }
    private suspend fun pump(condition:()->Boolean) {
        val until=System.nanoTime()+15_000_000_000
        while(!condition()&&System.nanoTime()<until){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20));yield();Thread.sleep(5)}
        assertTrue("Async operation did not finish",condition())
    }
    @After fun close():Unit=runBlocking {store.clear();pump{!ProductEditorTransactions.mutex.isLocked};locations.closeForTest();db.close()}
    private suspend fun restore() {
        val manager=BackupManager(app,db);val file=manager.exportFile();val generation=ProductEditorTransactions.generation.get()
        try{manager.importFile(file);assertEquals(generation+1,ProductEditorTransactions.generation.get())}finally{manager.discard(file)}
    }
    private suspend fun addModel():AddToListViewModel=AddToListViewModel(products,shops,basket,prefs).also{vm->store.put("add",vm);pump{vm.uiState.value.searchResults.any{it.id==product}}}
    private suspend fun shopModel():ShopCardViewModel=ShopCardViewModel(shop,shops).also{vm->store.put("shop",vm);pump{!vm.uiState.value.isLoading};assertNull(vm.uiState.value.error)}

    @Test fun importedMaximumGroupOrderIsIdempotentAndCanAppendWithoutChangingRelations():Unit=runBlocking {
        val z=groups.insertGroup(ProductGroupEntity(name="Ягоды",isFavorite=true,favoriteOrder=Int.MAX_VALUE,displayOrder=7,accent="green"))
        val a=groups.insertGroup(ProductGroupEntity(name="Аптека",isFavorite=true,favoriteOrder=Int.MAX_VALUE,displayOrder=9))
        val n=groups.insertGroup(ProductGroupEntity(name="Продукты",displayOrder=13))
        db.productDao().update(db.productDao().getById(product)!!.copy(productGroupId=z))
        val before=db.productGroupDao().getAllSync();groups.favoriteGroup(z,true);assertEquals(before,db.productGroupDao().getAllSync())
        groups.favoriteGroup(n,true)
        assertEquals(listOf(a,z,n),db.productGroupDao().getAllSync().filter{it.isFavorite}.sortedBy{it.favoriteOrder}.map{it.id})
        assertEquals(listOf(0,1,2),db.productGroupDao().getAllSync().filter{it.isFavorite}.sortedBy{it.favoriteOrder}.map{it.favoriteOrder})
        assertEquals(7,groups.getGroupById(z)!!.displayOrder);assertEquals("green",groups.getGroupById(z)!!.accent);assertEquals(z,db.productDao().getById(product)!!.productGroupId)
        assertEquals(listOf(a,n,z),groups.getAllGroups().first().map{it.id})
    }
    @Test fun importedMaximumDepartmentOrderCanAppendAndUnpinWithoutChangingLocalRoute():Unit=runBlocking {
        val t=shops.createDepartmentType("Молочка");val n=shops.createDepartmentType("Выпечка")
        db.shopDepartmentDao().updateType(db.shopDepartmentDao().type(t)!!.copy(isFavorite=true,favoriteOrder=Int.MAX_VALUE))
        val local=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=shop,name="Молочка",typeId=t,displayOrder=91))
        products.saveWithLinks(db.productDao().getById(product)!!,products.getShopLinksForProductSync(product).map{it.copy(departmentId=local)})
        val row=basket.addProduct(product);val original=db.shoppingListEntryDao().getById(row)
        val before=db.shopDepartmentDao().types();shops.favoriteDepartmentType(t,true);assertEquals(before,db.shopDepartmentDao().types())
        shops.favoriteDepartmentType(n,true);assertEquals(1,db.shopDepartmentDao().type(n)!!.favoriteOrder)
        shops.favoriteDepartmentType(t,false);assertFalse(db.shopDepartmentDao().type(t)!!.isFavorite)
        assertEquals(91,db.shopDepartmentDao().getById(local)!!.displayOrder);assertEquals(original,db.shoppingListEntryDao().getById(row))
    }
    @Test fun fullBackupRetainsExtremeFavoriteOrderAndRemainsEditable():Unit=runBlocking {
        val id=groups.insertGroup(ProductGroupEntity(name="Гигиена",isFavorite=true,favoriteOrder=Int.MAX_VALUE))
        restore();assertEquals(Int.MAX_VALUE,groups.getGroupById(id)!!.favoriteOrder)
        val next=groups.insertGroup(ProductGroupEntity(name="Быт"));groups.favoriteGroup(next,true)
        assertEquals(1,groups.getGroupById(next)!!.favoriteOrder);assertEquals(20,db.openHelper.writableDatabase.version)
        db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
    }
    @Test fun catalogueSelectionFromBeforeRestoreCannotAddToReplacementDatabase():Unit=runBlocking {
        val vm=addModel();vm.selectProduct(products.getProductById(product)!!);restore();vm.add();pump{vm.uiState.value.error!=null}
        assertTrue(vm.uiState.value.error!!.contains("База восстановлена"));assertTrue(db.shoppingListEntryDao().getAllSync().isEmpty());assertNull(vm.uiState.value.addedMessage)
    }
    @Test fun newPurchaseFromBeforeRestoreCannotCreateAProduct():Unit=runBlocking {
        val vm=addModel();vm.onSearchQueryChanged("Новая покупка");pump{vm.uiState.value.isNewProduct};restore();vm.add();pump{vm.uiState.value.error!=null}
        assertEquals(listOf(product),db.productDao().getAllSync().map{it.id});assertTrue(db.shoppingListEntryDao().getAllSync().isEmpty())
    }
    @Test fun staleAddWindowCannotCreateStoreOrCallSuccessCallback():Unit=runBlocking {
        val vm=addModel();restore();var called=false;vm.createShop("Ошибочный магазин"){called=true};pump{vm.uiState.value.error!=null}
        assertFalse(called);assertEquals(listOf(shop),db.shopDao().getAllSync().map{it.id});assertFalse(vm.uiState.value.isLoading)
    }
    @Test fun storeCardFromBeforeRestoreCannotOverwriteRestoredStore():Unit=runBlocking {
        val vm=shopModel();vm.updateName("Старый черновик");restore();val original=db.shopDao().getById(shop);vm.save();pump{vm.uiState.value.error!=null}
        assertEquals(original,db.shopDao().getById(shop));assertFalse(vm.uiState.value.isSaved);assertFalse(vm.uiState.value.isSaving)
    }
    @Test fun storeCardFromBeforeRestoreCannotDeleteRestoredStore():Unit=runBlocking {
        val vm=shopModel();restore();vm.deleteShop();pump{vm.uiState.value.error!=null}
        assertNotNull(db.shopDao().getById(shop));assertEquals(shop,products.getShopLinksForProductSync(product).single().shopId);assertFalse(vm.uiState.value.isDeleted)
    }
    @Test fun reopeningStoreCardAfterRestoreLoadsAndSavesNormally():Unit=runBlocking {
        restore();val vm=shopModel();vm.updateName("Новое название");vm.save();pump{vm.uiState.value.isSaved||vm.uiState.value.error!=null}
        assertNull(vm.uiState.value.error);assertEquals("Новое название",db.shopDao().getById(shop)!!.name)
    }
    @Test fun suggestionContextsFollowVenueRenameWithoutWritingDisplayNameBack():Unit=runBlocking {
        val venue=db.retailStructureDao().insertVenue(VenueEntity(cityId=1,name="Рынок"))
        db.shopDao().update(db.shopDao().getById(shop)!!.copy(name="Павильон 29"))
        db.shopBranchDao().insert(ShopBranchEntity(shopId=shop,address="Павильон 29",venueId=venue))
        val vm=addModel();pump{vm.uiState.value.productContexts[product]?.contains("Рынок — Павильон 29")==true}
        shops.updateVenue(db.retailStructureDao().venue(venue)!!.copy(name="Новый рынок"))
        pump{vm.uiState.value.productContexts[product]?.contains("Новый рынок — Павильон 29")==true}
        assertEquals("Павильон 29",db.shopDao().getById(shop)!!.name)
    }
    @Test fun disposableShareTextNeverMutatesRegularOrGoalPurchases():Unit=runBlocking {
        basket.addProduct(product,2.0);val goals=GoalListRepository(db,basket);val list=goals.create("Праздник");goals.addQuick(list,"Грибы")
        val before=db.shoppingListEntryDao().getAllSync();val vm=ShoppingTextViewModel(basket);store.put("text",vm);pump{!vm.uiState.value.loading}
        vm.text("Удалить всё и добавить другой товар");assertEquals(before,db.shoppingListEntryDao().getAllSync())
        vm.reloadFromBasket();pump{!vm.uiState.value.loading};assertTrue(vm.uiState.value.text.contains("Яблоки"));assertFalse(vm.uiState.value.text.contains("Грибы"));assertEquals(before,db.shoppingListEntryDao().getAllSync())
    }
}
