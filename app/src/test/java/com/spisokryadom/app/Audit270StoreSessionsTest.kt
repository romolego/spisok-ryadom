package com.spisokryadom.app

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.backup.BackupManager
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.ui.shoplist.ShopListViewModel
import com.spisokryadom.app.ui.shopcontent.ShopContentViewModel
import kotlinx.coroutines.*
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
class Audit270StoreSessionsTest {
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 private lateinit var db:AppDatabase
 private lateinit var locations:LocationRepository
 private lateinit var shops:ShopRepository
 private lateinit var products:ProductRepository
 private lateinit var basket:ShoppingListRepository
 private lateinit var content:ShopContentViewModel
 private lateinit var list:ShopListViewModel
 private val store=ViewModelStore()
 private var shop=0L;private var other=0L;private var product=0L
 private suspend fun waitFor(c:()->Boolean){val end=System.nanoTime()+20_000_000_000;while(!c()&&System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20));yield();Thread.sleep(5)};assertTrue(c())}
 @Before fun setup():Unit=runBlocking{
  db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build();db.cityDao().insert(CityEntity(1,"Город"))
  locations=LocationRepository(db.cityDao(),db.shopDao(),app.getSharedPreferences("audit270-location",0));shops=ShopRepository(db.shopDao(),db.shopDepartmentDao(),locations,db)
  products=ProductRepository(db.productDao(),db.productShopLinkDao(),db);basket=ShoppingListRepository(db.shoppingListEntryDao(),db,locations)
  shop=db.shopDao().insert(ShopEntity(name="Первый",isFavorite=true));other=db.shopDao().insert(ShopEntity(name="Второй",displayOrder=1,isFavorite=true,favoriteOrder=1))
  product=products.saveWithLinks(ProductEntity(name="Товар"),listOf(ProductShopLinkEntity(productId=0,shopId=shop)))
  content=ShopContentViewModel(shop,products,shops,basket,ClassifierRepository(db.productGroupDao(),db.recipientDao(),db.unitDao(),db));list=ShopListViewModel(shops)
  store.put("content",content);store.put("list",list);waitFor{!content.uiState.value.isLoading&&!list.uiState.value.isLoading}
 }
 @After fun close():Unit=runBlocking{store.clear();waitFor{!ProductEditorTransactions.mutex.isLocked};locations.closeForTest();db.close()}
 @Test fun contentUsesLiveShopMetadata():Unit=runBlocking{
  db.shopDao().update(db.shopDao().getById(shop)!!.copy(name="Обновлённый",shoppingBackground="rose",isFavorite=false))
  waitFor{content.uiState.value.shop?.name=="Обновлённый"};assertEquals("rose",content.uiState.value.shop!!.shoppingBackground);assertFalse(content.uiState.value.shop!!.isFavorite)
 }
 @Test fun oldStoreCommandsCannotMutateRestoredRowsEvenWhenIdsMatch():Unit=runBlocking{
  val manager=BackupManager(app,db);val file=manager.exportFile();manager.importFile(file);manager.discard(file)
  val beforeShops=db.shopDao().getAllSync();val beforeLinks=db.productShopLinkDao().getAllSync()
  content.quickAddToShoppingList(products.getProductById(product)!!);waitFor{content.uiState.value.error!=null}
  assertTrue(db.shoppingListEntryDao().getAllSync().isEmpty());content.clearError()
  content.toggleProductLink(product);waitFor{content.uiState.value.error!=null};assertEquals(beforeLinks,db.productShopLinkDao().getAllSync())
  list.moveShopDown(beforeShops.first());waitFor{list.uiState.value.error!=null};assertEquals(beforeShops,db.shopDao().getAllSync());list.clearError()
  list.moveFavoriteDown(beforeShops.first());waitFor{list.uiState.value.error!=null};assertEquals(beforeShops,db.shopDao().getAllSync())
  assertEquals(listOf(product),db.productDao().getAllSync().map{it.id})
 }
}
