package com.spisokryadom.app

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.data.backup.BackupManager
import com.spisokryadom.app.ui.addtolist.*
import com.spisokryadom.app.ui.bulk.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class Refine140DataTest {
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 private val prefs get()=app.getSharedPreferences("shopping_list_prefs",0)
 private lateinit var db:AppDatabase
 private lateinit var locations:LocationRepository
 private lateinit var products:ProductRepository
 private lateinit var shops:ShopRepository
 private lateinit var basket:ShoppingListRepository
 private val store=ViewModelStore()
 private var a=0L;private var b=0L;private var point=0L;private var otherPoint=0L
 private var banana=0L;private var water=0L;private var apple=0L
 @Before fun setup()=runBlocking{
  prefs.edit().clear().commit()
  db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build()
  db.cityDao().insert(CityEntity(1,"Тестовый город"));locations=LocationRepository(db.cityDao(),db.shopDao(),app.getSharedPreferences("140-location",0))
  shops=ShopRepository(db.shopDao(),db.shopDepartmentDao(),locations,db);products=ProductRepository(db.productDao(),db.productShopLinkDao(),db)
  basket=ShoppingListRepository(db.shoppingListEntryDao(),db,locations)
  a=db.shopDao().insert(ShopEntity(name="Батон"));b=db.shopDao().insert(ShopEntity(name="Красный Яр"))
  point=db.shopBranchDao().insert(ShopBranchEntity(shopId=a,address="Адрес 1"));otherPoint=db.shopBranchDao().insert(ShopBranchEntity(shopId=a,address="Адрес 2"))
  banana=products.saveWithLinks(ProductEntity(name="Банан",isFavorite=true,defaultUnit="кг",defaultQuantity=.5),listOf(ProductShopLinkEntity(productId=0,shopId=a)))
  water=products.saveWithLinks(ProductEntity(name="Вода",defaultUnit="бутылка"),listOf(ProductShopLinkEntity(productId=0,shopId=a,restrictBranches=true)),mapOf(a to setOf(point)))
  apple=products.saveWithLinks(ProductEntity(name="Яблоко"),listOf(ProductShopLinkEntity(productId=0,shopId=b)))
 }
 @After fun close(){store.clear();Shadows.shadowOf(Looper.getMainLooper()).idle();locations.closeForTest();db.close()}
 private fun await(c:()->Boolean){val until=System.nanoTime()+15_000_000_000;while(!c()&&System.nanoTime()<until){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5)};assertTrue("Timed out",c())}
 private fun model(shop:Long?=null,branch:Long?=null)=AddToListViewModel(products,shops,basket,prefs,shop,branch).also{store.put("add",it)}
 private fun textModel(key:String)=ShoppingTextViewModel(basket).also{store.put(key,it);await{!it.uiState.value.loading}}
 @Test fun autoBrowseHasAlphabeticalWholeCatalogueAndRepeatedFavorites(){
  val vm=model();await{vm.uiState.value.searchResults.size==3}
  assertEquals(listOf("Банан","Вода","Яблоко"),vm.uiState.value.searchResults.map{it.name})
  assertEquals(listOf(banana),vm.uiState.value.favoriteResults.map{it.id})
  assertTrue(vm.uiState.value.searchResults.any{it.id==banana})
 }
 @Test fun explicitOutletHonorsRestrictedAvailabilityAndShopChanges(){
  val vm=model(a,otherPoint);await{vm.uiState.value.searchResults.isNotEmpty()}
  assertEquals(listOf(banana),vm.uiState.value.searchResults.map{it.id})
  vm.onShopSelected(b);await{vm.uiState.value.searchResults.map{it.id}==listOf(apple)}
  vm.onShopSelected(b);assertFalse(vm.uiState.value.isSearching)
 }
 @Test fun disablingBrowseKeepsTypedSearchAndReactsToPreferenceChanges(){
  val vm=model(a,point);await{vm.uiState.value.searchResults.size==2}
  prefs.edit().putBoolean(AddToListViewModel.PREF_BROWSE_PRODUCTS,false).commit();await{!vm.uiState.value.browseProducts&&vm.uiState.value.searchResults.isEmpty()}
  vm.onSearchQueryChanged("Банан");await{!vm.uiState.value.isSearching&&vm.uiState.value.searchResults.size==1}
  assertEquals(banana,vm.uiState.value.searchResults.single().id);assertTrue(vm.uiState.value.favoriteResults.isEmpty())
  vm.onSearchQueryChanged("");await{vm.uiState.value.searchResults.isEmpty()}
  prefs.edit().putBoolean(AddToListViewModel.PREF_BROWSE_PRODUCTS,true).commit();await{vm.uiState.value.searchResults.size==2}
 }
 @Test fun quickAddingSeveralProductsKeepsBrowserAndUsesDefaults()=runBlocking{
  val vm=model(a,point);await{vm.uiState.value.searchResults.size==2}
  vm.quickAdd(vm.uiState.value.searchResults.first{it.id==banana});await{!vm.uiState.value.isLoading&&vm.uiState.value.browseMessage!=null}
  assertNull(vm.uiState.value.selectedProduct);assertNull(vm.uiState.value.addedMessage);assertEquals(2,vm.uiState.value.searchResults.size)
  vm.clearBrowseMessage();vm.quickAdd(vm.uiState.value.searchResults.first{it.id==water});await{!vm.uiState.value.isLoading&&vm.uiState.value.browseMessage!=null}
  val rows=db.shoppingListEntryDao().getAllSync();assertEquals(2,rows.size);assertEquals(.5,rows.single{it.productId==banana}.quantity,0.0);assertEquals("кг",rows.single{it.productId==banana}.unit);assertTrue(rows.all{it.assignedBranchId==point})
 }
 @Test fun selectingWithoutTypingCanSetQuantityAndAdd()=runBlocking{
  val vm=model();await{vm.uiState.value.searchResults.size==3}
  vm.selectProduct(vm.uiState.value.searchResults.first{it.id==banana});vm.quantity("2,5");vm.add();await{vm.uiState.value.addedMessage!=null}
  assertEquals(2.5,db.shoppingListEntryDao().getAllSync().single().quantity,0.0)
 }
 @Test fun typingNewProductStillCreatesAndAssignsIt()=runBlocking{
  val vm=model(a,point);await{vm.uiState.value.searchResults.size==2}
  vm.onSearchQueryChanged("Газировка вишнёвая");await{!vm.uiState.value.isSearching&&vm.uiState.value.isNewProduct};vm.unit("бутылка");vm.add();await{vm.uiState.value.addedMessage!=null}
  val row=db.shoppingListEntryDao().getAllSync().single();assertEquals("Газировка вишнёвая",db.productDao().getById(row.productId)!!.name);assertEquals(a,row.assignedShopId);assertEquals(point,row.assignedBranchId)
 }
 @Test fun shareMessageIgnoresOldDraftsAndNeverMutatesBasket()=runBlocking{
  val row=basket.addProduct(banana,.5);basket.addProduct(water).also{basket.markBought(it)}
  val before=db.shoppingListEntryDao().getAllSync();val old=app.getSharedPreferences("drafts",0);old.edit().putString("text_view_draft","СТАРЫЙ ЧЕРНОВИК").putString("bulk_text","СТАРЫЙ ВВОД").commit()
  val vm=textModel("text");assertTrue(vm.uiState.value.text.contains("Банан — 0,5 кг"));assertFalse(vm.uiState.value.text.contains("Вода"));assertFalse(vm.uiState.value.text.contains("СТАРЫЙ"))
  vm.text("Заменил всё сообщение");assertEquals("Заменил всё сообщение",shoppingListShareIntent(vm.uiState.value.text).getParcelableExtra<android.content.Intent>(android.content.Intent.EXTRA_INTENT)!!.getStringExtra(android.content.Intent.EXTRA_TEXT))
  assertEquals(before,db.shoppingListEntryDao().getAllSync());assertEquals("СТАРЫЙ ЧЕРНОВИК",old.getString("text_view_draft",null))
  basket.updateQuantity(row,3.0)
  val reopened=textModel("reopened");assertTrue(reopened.uiState.value.text.contains("Банан — 3 кг"));assertFalse(reopened.uiState.value.dirty)
  vm.reloadFromBasket();await{!vm.uiState.value.loading};assertEquals(reopened.uiState.value.text,vm.uiState.value.text)
 }
 @Test fun browsePreferenceRoundTripsWithFullBackup()=runBlocking{
  prefs.edit().putBoolean(AddToListViewModel.PREF_BROWSE_PRODUCTS,false).commit()
  val manager=BackupManager(app,db);val archive=manager.exportFile()
  prefs.edit().putBoolean(AddToListViewModel.PREF_BROWSE_PRODUCTS,true).commit();manager.importFile(archive)
  assertFalse(prefs.getBoolean(AddToListViewModel.PREF_BROWSE_PRODUCTS,true));assertEquals(3,db.productDao().getAllSync().size);manager.discard(archive)
 }
}
