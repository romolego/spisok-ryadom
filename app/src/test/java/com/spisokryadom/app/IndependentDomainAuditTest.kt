package com.spisokryadom.app

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.dao.ShoppingListEntryDao
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.ui.shoppinglist.ShoppingListViewModel
import com.spisokryadom.app.ui.productdb.ProductDatabaseViewModel
import com.spisokryadom.app.ui.shopcontent.ShopContentViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.*
import java.util.concurrent.atomic.AtomicBoolean

/** Independent expected-correct regressions retained after the fixes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
class IndependentDomainAuditTest {
 private lateinit var context:Application
 private lateinit var db:AppDatabase
 private lateinit var locations:LocationRepository
 private lateinit var products:ProductRepository
 private lateinit var shops:ShopRepository
 private lateinit var classifiers:ClassifierRepository
 private lateinit var basket:ShoppingListRepository
 private val store=ViewModelStore()
 private fun await(condition:()->Boolean){val end=System.nanoTime()+10_000_000_000;while(!condition()&&System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5)};assertTrue("ViewModel did not finish; mutex="+ProductEditorTransactions.mutex.isLocked+"; catalog="+(store["catalog"] as? ProductDatabaseViewModel)?.uiState?.value?.error+"; shop="+(store["shop"] as? ShopContentViewModel)?.uiState?.value?.error+"; list="+(store["list"] as? ShoppingListViewModel)?.uiState?.value?.error,condition())}
 @Before fun setup()=runBlocking {
  context=ApplicationProvider.getApplicationContext<Application>()
  val prefs=context.getSharedPreferences("independent_domain_location",0);prefs.edit().clear().commit()
  context.getSharedPreferences("independent_domain_list",0).edit().clear().commit()
  db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).allowMainThreadQueries().build()
  db.cityDao().insert(CityEntity(1,"Томск"))
  locations=LocationRepository(db.cityDao(),db.shopDao(),prefs)
  products=ProductRepository(db.productDao(),db.productShopLinkDao(),db)
  shops=ShopRepository(db.shopDao(),db.shopDepartmentDao(),locations,db)
  classifiers=ClassifierRepository(db.productGroupDao(),db.recipientDao(),db.unitDao(),db)
  basket=ShoppingListRepository(db.shoppingListEntryDao(),db,locations)
 }
 @After fun cleanup(){
  store.clear()
  // A SQLite row can be visible before its VM transaction resumes on Main and
  // releases the restore mutex. Drain that completion before closing/resetting
  // the test database and its paused Looper; never unlock a live job by hand.
  await { !ProductEditorTransactions.mutex.isLocked }
  locations.closeForTest();db.close()
 }
 private fun viewModel(repo:ShoppingListRepository=basket)=ShoppingListViewModel(repo,products,shops,classifiers,context.getSharedPreferences("independent_domain_list",0)).also{store.put("list",it)}

 @Test fun quantityStepMustNotUseDefaultNumberFromAnotherUnit()=runBlocking {
  val id=db.productDao().insert(ProductEntity(name="Гайки",defaultUnit="шт",defaultQuantity=100.0))
  val entry=basket.addProduct(id,0.5,"кг",ShopChoice.NONE)
  val vm=viewModel();await{vm.uiState.value.totalCount==1&&vm.uiState.value.groups.flatMap{it.entries}.single().defaultQuantity==100.0}
  assertEquals("The rendered step must match the purchase unit",1.0,vm.uiState.value.groups.flatMap{it.entries}.single().quantityStep,0.0)
  vm.increaseQuantity(entry)
  await{runBlocking{db.shoppingListEntryDao().getById(entry)!!.quantity!=0.5}}
  assertEquals("100 шт must not become a 100 кг increment",1.5,db.shoppingListEntryDao().getById(entry)!!.quantity,0.0)
 }

 @Test fun catalogQuantityControlMustModifyTheEntryItDisplays()=runBlocking {
  val p=db.productDao().insert(ProductEntity(name="Картофель"))
  // Valid persisted rows; createdAt and IDs need not have the same order (e.g. a clock correction).
  val shown=db.shoppingListEntryDao().insert(ShoppingListEntryEntity(productId=p,quantity=1.0,unit="кг",createdAt=2_000))
  val other=db.shoppingListEntryDao().insert(ShoppingListEntryEntity(productId=p,quantity=2.0,unit="шт",createdAt=1_000))
  val vm=ProductDatabaseViewModel(products,basket,shops,classifiers,context.getSharedPreferences("independent_domain_catalog",0));store.put("catalog",vm)
  val subscriber=launch(Dispatchers.Unconfined){vm.uiState.collect{}}
  try {
   await{vm.uiState.value.shoppingListMap.containsKey(p)}
   assertEquals(shown,vm.uiState.value.shoppingListMap[p]!!.entryId)
   assertEquals(setOf(shown,other),vm.uiState.value.shoppingListMap[p]!!.purchases.map{it.id}.toSet())
   vm.incrementEntry(shown)
   await{runBlocking{db.shoppingListEntryDao().getById(shown)!!.quantity!=1.0||db.shoppingListEntryDao().getById(other)!!.quantity!=2.0}}
   assertEquals("The visible kg row must receive the increment",2.0,db.shoppingListEntryDao().getById(shown)!!.quantity,0.0)
   assertEquals("The hidden piece row must not receive the kg control's increment",2.0,db.shoppingListEntryDao().getById(other)!!.quantity,0.0)
  } finally {subscriber.cancelAndJoin()}
 }

 @Test fun movingShopMustPreserveConcurrentQuantityBoughtAndUrgencyChanges()=runBlocking {
  val p=db.productDao().insert(ProductEntity(name="Молоко"))
  val a=db.shopDao().insert(ShopEntity(name="Первый",cityId=1))
  val b=db.shopDao().insert(ShopEntity(name="Второй",cityId=1))
  val id=basket.addProduct(p,2.0,"л",ShopChoice.EXPLICIT,a)
  val original=db.shoppingListEntryDao();val captured=CompletableDeferred<Unit>();val resume=CompletableDeferred<Unit>();val pause=AtomicBoolean(true);val transactionalRead=AtomicBoolean(false)
  // Pause the command after reading. Other commands must serialize behind that transaction.
  val observedDao=object:ShoppingListEntryDao by original {
   override suspend fun getById(id:Long):ShoppingListEntryEntity? {
    val value=original.getById(id)
    if(pause.compareAndSet(true,false)){transactionalRead.set(db.inTransaction());captured.complete(Unit);resume.await()}
    return value
   }
  }
  val observedBasket=ShoppingListRepository(observedDao,db,locations)
  val vm=viewModel(observedBasket);await{vm.uiState.value.totalCount==1}
  vm.changeShop(id,b,null);await{captured.isCompleted}
  val writerStarted=CompletableDeferred<Unit>()
  val writer=launch(Dispatchers.Default){writerStarted.complete(Unit);basket.adjustQuantity(id,1.0);basket.markBought(id);basket.toggleUrgent(id)}
  writerStarted.await()
  resume.complete(Unit)
  // Let the VM finish its transaction before awaiting the queued writer.
  await{vm.uiState.value.groups.any{g->g.entries.any{it.entry.id==id&&it.entry.assignedShopId==b}}}
  writer.join()
  assertTrue("The shop command must read its source row inside the write transaction",transactionalRead.get())
  val result=original.getById(id)!!
  assertEquals("Shop command must preserve the intervening quantity increment",3.0,result.quantity,0.0)
  assertTrue("Shop command must preserve bought status",result.isBought)
  assertTrue("Shop command must preserve urgency",result.isUrgent)
 }

 @Test fun defaultStepUsesCanonicalUnitAndValidCardValues()=runBlocking {
  val p=db.productDao().insert(ProductEntity(name="Весовой",defaultUnit="КГ.",defaultQuantity=.25))
  val weight=basket.addProduct(p,1.0,"килограмма",ShopChoice.NONE)
  basket.adjustByDefaultStep(weight,1)
  assertEquals(1.25,basket.getEntryById(weight)!!.quantity,0.0)
  basket.adjustByDefaultStep(weight,-1)
  assertEquals(1.0,basket.getEntryById(weight)!!.quantity,0.0)
  val pieces=basket.addProduct(p,2.0,"шт",ShopChoice.NONE)
  basket.adjustByDefaultStep(pieces,1)
  assertEquals(3.0,basket.getEntryById(pieces)!!.quantity,0.0)
  db.productDao().update(db.productDao().getById(p)!!.copy(defaultQuantity=Double.NaN))
  basket.adjustByDefaultStep(weight,1)
  assertEquals(2.0,basket.getEntryById(weight)!!.quantity,0.0)
 }

 @Test fun departmentFallbackAndDetailCommandsPreserveOtherFields()=runBlocking {
  val p=db.productDao().insert(ProductEntity(name="Хлеб"))
  val a=db.shopDao().insert(ShopEntity(name="Первый",cityId=1))
  val b=db.shopDao().insert(ShopEntity(name="Второй",cityId=1))
  val ad=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=a,name="Выпечка"))
  val bd=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=b,name="выпечка"))
  val preferred=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=b,name="Хлеб"))
  val id=basket.addOrMergeEntry(ShoppingListEntryEntity(productId=p,quantity=2.0,unit="шт",assignedShopId=a,assignedDepartmentId=ad,isUrgent=true))
  basket.markBought(id)
  basket.changeShop(id,b,null)
  assertEquals(bd,basket.getEntryById(id)!!.assignedDepartmentId)
  db.productShopLinkDao().insert(ProductShopLinkEntity(productId=p,shopId=b,departmentId=preferred))
  basket.changeShop(id,b,null)
  assertEquals(preferred,basket.getEntryById(id)!!.assignedDepartmentId)
  basket.updateEntryDetails(id,3.0,b,"  Для супа  ","  Не резать  ")
  val result=basket.getEntryById(id)!!
  assertTrue(result.isBought);assertTrue(result.isUrgent)
  assertEquals(3.0,result.quantity,0.0);assertEquals(preferred,result.assignedDepartmentId)
  assertEquals("Для супа",result.note);assertEquals("Не резать",result.comment)
  try{basket.changeShop(id,b,ad);fail("Department belongs to another shop")}catch(_:IllegalArgumentException){}
  assertEquals(result,basket.getEntryById(id))
  basket.changeShop(id,null,null)
  assertNull(basket.getEntryById(id)!!.assignedDepartmentId)
 }

 @Test fun catalogEntryCommandsStayOnDisplayedIdentityAndUseEntryUnit()=runBlocking {
  val p=db.productDao().insert(ProductEntity(name="Гайки",defaultUnit="шт",defaultQuantity=100.0))
  val visible=basket.addProduct(p,.5,"кг",ShopChoice.NONE)
  val hidden=basket.addProduct(p,5.0,"шт",ShopChoice.NONE)
  val vm=ProductDatabaseViewModel(products,basket,shops,classifiers,context.getSharedPreferences("independent_domain_catalog",0));store.put("catalog",vm)
  val subscriber=launch(Dispatchers.Unconfined){vm.uiState.collect{}}
  try {
   await{vm.uiState.value.shoppingListMap[p]?.entryId==visible}
   repeat(15){vm.incrementEntry(visible)}
   await{runBlocking{basket.getEntryById(visible)!!.quantity==15.5}}
   vm.decrementEntry(visible)
   await{runBlocking{basket.getEntryById(visible)!!.quantity==14.5}}
   vm.setEntryQuantity(visible,.25)
   await{runBlocking{basket.getEntryById(visible)!!.quantity==.25}}
   assertEquals(5.0,basket.getEntryById(hidden)!!.quantity,0.0)
  } finally{subscriber.cancelAndJoin()}
 }

 @Test fun shopContentEntryControlsUseMatchingUnitAndPreserveOtherPurchases()=runBlocking {
  val p=db.productDao().insert(ProductEntity(name="Гайки",defaultUnit="шт",defaultQuantity=100.0))
  val shop=db.shopDao().insert(ShopEntity(name="Строймаркет",cityId=1))
  db.productShopLinkDao().insert(ProductShopLinkEntity(productId=p,shopId=shop))
  val weight=basket.addProduct(p,.5,"кг",ShopChoice.EXPLICIT,shop)
  val pieces=basket.addProduct(p,5.0,"шт",ShopChoice.EXPLICIT,shop)
  val vm=ShopContentViewModel(shop,products,shops,basket,classifiers);store.put("shop",vm)
  await{vm.uiState.value.products.singleOrNull()?.purchases?.map{it.id}?.toSet()==setOf(weight,pieces)}
  vm.incrementEntry(weight)
  await{runBlocking{basket.getEntryById(weight)!!.quantity==1.5}}
  vm.decrementEntry(weight)
  await{runBlocking{basket.getEntryById(weight)!!.quantity==.5}}
  vm.setEntryQuantity(weight,.25)
  await{runBlocking{basket.getEntryById(weight)!!.quantity==.25}}
  assertEquals(5.0,basket.getEntryById(pieces)!!.quantity,0.0)
 }
}
