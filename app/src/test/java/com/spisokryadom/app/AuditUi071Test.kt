package com.spisokryadom.app

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.db.RetailConstraints
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.ui.productcard.ProductCardViewModel
import com.spisokryadom.app.ui.productdb.ProductDatabaseViewModel
import com.spisokryadom.app.ui.productdb.ProductViewMode
import com.spisokryadom.app.ui.shopcard.ShopCardViewModel
import com.spisokryadom.app.ui.shopcontent.ShopContentViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.*
import java.time.Duration

/** Independent UI command/projection regressions against real Room, not fake repositories. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class AuditUi071Test {
 private lateinit var app:Application
 private lateinit var db:AppDatabase
 private lateinit var locations:LocationRepository
 private lateinit var products:ProductRepository
 private lateinit var shops:ShopRepository
 private lateinit var classifiers:ClassifierRepository
 private lateinit var basket:ShoppingListRepository
 private val store=ViewModelStore()
 private var shop=0L
 private var point=0L
 private fun await(condition:()->Boolean){
  val end=System.nanoTime()+15_000_000_000L
  while(!condition()&&System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20));Thread.sleep(5)}
  assertTrue("UI command/projection did not finish",condition())
 }
 @Before fun setup()=runBlocking{
  app=ApplicationProvider.getApplicationContext()
  listOf("audit071-location","audit071-catalog","audit071-text").forEach{app.getSharedPreferences(it,0).edit().clear().commit()}
  db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build()
  RetailConstraints.install(db.openHelper.writableDatabase)
  db.cityDao().insert(CityEntity(1,"Красноярск"));db.cityDao().insert(CityEntity(2,"Томск"))
  locations=LocationRepository(db.cityDao(),db.shopDao(),app.getSharedPreferences("audit071-location",0));locations.select(1)
  products=ProductRepository(db.productDao(),db.productShopLinkDao(),db)
  shops=ShopRepository(db.shopDao(),db.shopDepartmentDao(),locations,db)
  classifiers=ClassifierRepository(db.productGroupDao(),db.recipientDao(),db.unitDao(),db)
  basket=ShoppingListRepository(db.shoppingListEntryDao(),db,locations)
  shop=db.shopDao().insert(ShopEntity(name="Батон"));point=db.shopBranchDao().insert(ShopBranchEntity(shopId=shop,address="Весны, 7"))
  Unit
 }
 @After fun close(){store.clear();locations.closeForTest();db.close()}
 private fun productCard()=ProductCardViewModel(-1,shop,products,shops,classifiers).also{store.put("product-card",it);await{it.uiState.value.pendingShopLinks.size==1}}

 @Test fun deletingPreselectedShopInNewCardIsRespectedOnSave()=runBlocking{
  val vm=productCard();vm.updateName("Без привязки");vm.removePendingShopLink(vm.uiState.value.pendingShopLinks.single().tempId)
  vm.save();await{vm.uiState.value.isSaved||vm.uiState.value.error!=null};assertNull(vm.uiState.value.error)
  val id=db.productDao().getAllSync().single().id
  assertTrue("Removing the initial link must not silently re-add it",db.productShopLinkDao().getLinksForProductSync(id).isEmpty())
 }
 @Test fun deletingPreselectedShopAlsoAppliesWhenReusingAnExistingProduct()=runBlocking{
  val existing=db.productDao().insert(ProductEntity(name="Чай"))
  val vm=productCard();vm.removePendingShopLink(vm.uiState.value.pendingShopLinks.single().tempId);vm.useExistingProduct(existing)
  await{vm.uiState.value.usedExistingId==existing||vm.uiState.value.error!=null};assertNull(vm.uiState.value.error)
  assertTrue(db.productShopLinkDao().getLinksForProductSync(existing).isEmpty())
 }
 @Test fun anUntouchedInitialShopLinkStillSavesNormally()=runBlocking{
  val vm=productCard();vm.updateName("Молоко");vm.save();await{vm.uiState.value.isSaved||vm.uiState.value.error!=null};assertNull(vm.uiState.value.error)
  assertEquals(shop,db.productShopLinkDao().getAllSync().single().shopId)
 }
 @Test fun selectingSameCityPreservesVenueButChangingCityClearsIt()=runBlocking{
  val venue=shops.ensureVenue(1,"Енисейский привоз");db.shopBranchDao().update(db.shopBranchDao().forShop(shop).single().copy(venueId=venue))
  val vm=ShopCardViewModel(shop,shops);store.put("shop-card",vm);await{!vm.uiState.value.isLoading}
  vm.updateCity(1);assertEquals(venue,vm.uiState.value.branches.single().venueId)
  vm.updateName("Батон обновлённый");vm.save();await{vm.uiState.value.isSaved||vm.uiState.value.error!=null};assertNull(vm.uiState.value.error)
  assertEquals(venue,db.shopBranchDao().forShop(shop).single().venueId)
  val other=ShopCardViewModel(shop,shops);store.put("other-card",other);await{!other.uiState.value.isLoading};other.updateCity(2)
  assertNull(other.uiState.value.branches.single().venueId)
 }
 @Test fun conversionUsesCurrentCardDraftIncludingAddedPavilionAndNotes()=runBlocking{
  val vm=ShopCardViewModel(shop,shops);store.put("shop-card",vm);await{!vm.uiState.value.isLoading}
  vm.updateName("Рынок после правок");vm.updateNote("Новая заметка рынка")
  vm.updateBranches(vm.uiState.value.branches.map{it.copy(address="29 павильон",note="Сыры") }+ShopBranchEntity(id=-1,shopId=shop,address="68 павильон",note="Мясо"))
  vm.convertToVenue();await{vm.uiState.value.isSaved||vm.uiState.value.error!=null};assertNull(vm.uiState.value.error)
  val venue=db.retailStructureDao().venues().single();assertEquals("Рынок после правок",venue.name);assertEquals("Новая заметка рынка",venue.note)
  val points=db.shopBranchDao().getAll();assertEquals(setOf("29 павильон","68 павильон"),db.shopDao().getAllSync().map{it.name}.toSet());assertTrue(points.all{it.venueId==venue.id&&it.address.isEmpty()})
  assertEquals("Сыры",points.single{it.id==point}.note)
 }
 @Test fun pointOverrideUsesItsEffectiveDepartmentOrderAndUpdatesWhileScreenLives()=runBlocking{
  val first=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=shop,name="Базовая",displayOrder=0))
  val middle=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=shop,name="Выпечка",displayOrder=1))
  val last=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=shop,name="Сыры",displayOrder=2))
  val cheese=products.saveWithLinks(ProductEntity(name="Сыр"),listOf(ProductShopLinkEntity(productId=0,shopId=shop,departmentId=first)),branchDepartments=mapOf(point to last))
  val bread=products.saveWithLinks(ProductEntity(name="Хлеб"),listOf(ProductShopLinkEntity(productId=0,shopId=shop,departmentId=middle)))
  val vm=ShopContentViewModel(shop,products,shops,basket,classifiers);store.put("content",vm)
  await{vm.uiState.value.sections.singleOrNull()?.products?.singleOrNull{it.product.id==cheese}?.departmentName=="Сыры"}
  assertEquals(listOf(bread,cheese),vm.uiState.value.sections.single().products.map{it.product.id})
  val dept=db.shopDepartmentDao().getById(last)!!
  shops.renameDepartmentGlobally(dept.typeId,"Молочные сыры")
  await{vm.uiState.value.sections.single().products.single{it.product.id==cheese}.departmentName=="Молочные сыры"}
  db.shopDepartmentDao().updatePlacement(ShopDepartmentPlacementEntity(last,shop,dept.typeId,-1))
  await{vm.uiState.value.sections.single().products.first().product.id==cheese}
 }
 @Test fun liveCatalogueRespondsToRestoredPreferencesWithoutRecreation()=runBlocking{
  db.productDao().insert(ProductEntity(name="Обычный"));val favorite=db.productDao().insert(ProductEntity(name="Избранный",isFavorite=true))
  val prefs=app.getSharedPreferences("audit071-catalog",0)
  val vm=ProductDatabaseViewModel(products,basket,shops,classifiers,prefs);store.put("catalog",vm)
  val subscription=launch(Dispatchers.Unconfined){vm.uiState.collect{}}
  try{
   await{!vm.uiState.value.isLoading&&vm.uiState.value.products.size==2}
   prefs.edit().putBoolean("favorites_only",true).putString("product_db_view_mode",ProductViewMode.BY_GROUPS.name).commit()
   await{vm.uiState.value.favoritesOnly&&vm.uiState.value.viewMode==ProductViewMode.BY_GROUPS&&vm.uiState.value.products.size==1}
   assertEquals(listOf(favorite),vm.uiState.value.products.map{it.id})
   prefs.edit().remove("favorites_only").remove("product_db_view_mode").commit();await{!vm.uiState.value.favoritesOnly&&vm.uiState.value.viewMode==ProductViewMode.ALPHABETICAL&&vm.uiState.value.products.size==2}
   assertEquals(2,vm.uiState.value.products.size)
  }finally{subscription.cancelAndJoin()}
 }
}
