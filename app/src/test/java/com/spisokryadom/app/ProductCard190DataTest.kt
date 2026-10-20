package com.spisokryadom.app

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.ui.productcard.*
import kotlinx.coroutines.runBlocking
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
class ProductCard190DataTest {
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 private lateinit var db:AppDatabase;private lateinit var locations:LocationRepository
 private lateinit var products:ProductRepository;private lateinit var shops:ShopRepository;private lateinit var classifiers:ClassifierRepository
 private val store=ViewModelStore();private var shop=0L;private var first=0L;private var second=0L;private var group=0L;private var otherGroup=0L;private var product=0L
 private val prefs get()=app.getSharedPreferences("190-editing",0)
 private val drafts get()=app.getSharedPreferences("190-drafts",0)
 private fun await(c:()->Boolean){val end=System.nanoTime()+25_000_000_000;while(!c()&&System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(25));Thread.sleep(5)};assertTrue(c())}
 @Before fun setup():Unit=runBlocking{
  prefs.edit().clear().commit();drafts.edit().clear().commit()
  db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build();db.cityDao().insert(CityEntity(1,"Город"))
  locations=LocationRepository(db.cityDao(),db.shopDao(),app.getSharedPreferences("190-location",0));shops=ShopRepository(db.shopDao(),db.shopDepartmentDao(),locations,db)
  products=ProductRepository(db.productDao(),db.productShopLinkDao(),db);classifiers=ClassifierRepository(db.productGroupDao(),db.recipientDao(),db.unitDao(),db)
  shop=db.shopDao().insert(ShopEntity(name="Магазин"));first=db.shopBranchDao().insert(ShopBranchEntity(shopId=shop,address="Первый"));second=db.shopBranchDao().insert(ShopBranchEntity(shopId=shop,address="Второй",displayOrder=1))
  val a=db.shopDepartmentDao().insertType(DepartmentTypeEntity(name="Молочка",normalizedName="молочка"));val b=db.shopDepartmentDao().insertType(DepartmentTypeEntity(name="Сыры",normalizedName="сыры"))
  group=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=shop,name="Молочка",typeId=a));otherGroup=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=shop,name="Сыры",typeId=b))
  product=products.saveWithLinks(ProductEntity(name="Сыр"),listOf(ProductShopLinkEntity(productId=0,shopId=shop,departmentId=group)))
 }
 @After fun close(){store.clear();await{!ProductEditorTransactions.mutex.isLocked};locations.closeForTest();db.close()}
 private fun card(id:Long=product,preselected:Long=-1)=ProductCardViewModel(id,preselected,products,shops,classifiers,drafts,prefs).also{store.put("card",it);await{!it.uiState.value.isLoading&&it.uiState.value.branches.size==2&&it.uiState.value.allDepartments.size==2}}
 @Test fun allOutletsStayUnrestrictedAfterPriorityAndNewAddress():Unit=runBlocking{
  products.saveWithLinks(db.productDao().getById(product)!!,db.productShopLinkDao().getLinksForProductSync(product),availability=mapOf(shop to setOf(first)))
  val v=card();v.configureShop(shop,group,null,listOf(second,first));v.save();await{v.uiState.value.isSaved}
  assertFalse(db.productShopLinkDao().getLinksForProductSync(product).single().restrictBranches);assertTrue(db.shopBranchDao().availability(product).isEmpty())
  val third=db.shopBranchDao().insert(ShopBranchEntity(shopId=shop,address="Новый адрес",displayOrder=2))
  val purchases=ShoppingListRepository(db.shoppingListEntryDao(),db,locations)
  val id=purchases.addProduct(product,choice=ShopChoice.EXPLICIT,shopId=shop,branchId=third);assertEquals(third,db.shoppingListEntryDao().getById(id)!!.assignedBranchId)
 }
 @Test fun oneConfirmationUpdatesPendingButPreservesBoughtAndGoalPriceUnit():Unit=runBlocking{
  val purchases=ShoppingListRepository(db.shoppingListEntryDao(),db,locations)
  val pending=purchases.addProduct(product,quantity=3.0,unit="кг");val bought=db.shoppingListEntryDao().insert(db.shoppingListEntryDao().getById(pending)!!.copy(id=0,isBought=true))
  val goal=db.goalListDao().insert(GoalListEntity(name="Блюдо"));val plan=db.shoppingListEntryDao().insert(db.shoppingListEntryDao().getById(pending)!!.copy(id=0,listId=goal,unit="пачка",plannedUnitPrice=12.0))
  val before=db.shoppingListEntryDao().getById(bought)!!;val v=card();v.configureShop(shop,otherGroup,setOf(second),listOf(second,first));v.save();await{v.uiState.value.isSaved}
  assertEquals(second,db.shoppingListEntryDao().getById(pending)!!.assignedBranchId);assertEquals(otherGroup,db.shoppingListEntryDao().getById(pending)!!.assignedDepartmentId);assertEquals(3.0,db.shoppingListEntryDao().getById(pending)!!.quantity,0.0)
  assertEquals(before,db.shoppingListEntryDao().getById(bought));assertEquals("пачка",db.shoppingListEntryDao().getById(plan)!!.unit);assertEquals(12.0,db.shoppingListEntryDao().getById(plan)!!.plannedUnitPrice!!,0.0)
 }
 @Test fun commonShopGroupClearsLegacyOverridesOnlyForConfirmedShop():Unit=runBlocking{
  val other=db.shopDao().insert(ShopEntity(name="Другой"));val point=db.shopBranchDao().insert(ShopBranchEntity(shopId=other,address="Адрес"));val type=db.shopDepartmentDao().getById(otherGroup)!!.typeId
  val placement=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=other,name="Сыры",typeId=type))
  products.ensureShopLink(product,other);db.retailStructureDao().setProductDepartment(ProductBranchDepartmentEntity(product,first,shop,type));db.retailStructureDao().setProductDepartment(ProductBranchDepartmentEntity(product,point,other,type))
  val v=ProductCardViewModel(product,-1,products,shops,classifiers,drafts,prefs);store.put("card",v);await{!v.uiState.value.isLoading&&v.uiState.value.branchDepartments.size==2}
  v.configureShop(shop,group,null,emptyList());v.save();await{v.uiState.value.isSaved}
  assertEquals(listOf(point),products.branchDepartments(product).map{it.branchId});assertNotNull(db.shopDepartmentDao().getById(placement))
 }
 @Test fun invalidEmptyOrStaleSelectionLeavesWholeShopConfigurationUntouched(){
  val v=card();val before=v.uiState.value
  v.configureShop(shop,otherGroup,emptySet(),listOf(second));assertNotNull(v.uiState.value.error);assertEquals(before.branchSelections,v.uiState.value.branchSelections);assertEquals(before.shopLinks,v.uiState.value.shopLinks)
  v.clearError();v.configureShop(shop,otherGroup,setOf(99999),emptyList());assertEquals(before.shopLinks,v.uiState.value.shopLinks)
  v.clearError();v.configureShop(shop,99999,null,emptyList());assertEquals(before.shopLinks,v.uiState.value.shopLinks)
 }
 @Test fun newCardCommitsDepartmentRestrictionAndPriorityTogether():Unit=runBlocking{
  val v=card(-1,shop);v.updateName("Новый сыр");v.configureShop(shop,otherGroup,setOf(second),listOf(second,first));v.save();await{v.uiState.value.isSaved}
  val id=v.uiState.value.savedProductId!!;val link=db.productShopLinkDao().getLinksForProductSync(id).single();assertEquals(otherGroup,link.departmentId);assertTrue(link.restrictBranches);assertEquals(listOf(second),db.shopBranchDao().availability(id).map{it.branchId});assertEquals(second,products.branchPriorities(id).minBy{it.displayOrder}.branchId)
 }
 @Test fun rawRoundTripRetainsAllModeAndPriorityIndependently(){
  val v=card();v.configureShop(shop,otherGroup,null,listOf(second,first));val raw=ProductEditDraft("test",product,false,false,v.uiState.value)
  val restored=ProductEditDraft.decode(raw.encode());assertFalse(restored.state.branchSelections.containsKey(shop));assertEquals(listOf(second,first),restored.state.branchPriorities[shop]);assertEquals(otherGroup,restored.state.shopLinks.single().link.departmentId)
 }
 @Test fun autosaveCommitsSettingsWithoutExplicitSave():Unit=runBlocking{
  val v=card();v.configureShop(shop,otherGroup,setOf(second),listOf(second,first));await{runBlocking{db.productShopLinkDao().getLinksForProductSync(product).single().departmentId==otherGroup}}
  assertTrue(db.productShopLinkDao().getLinksForProductSync(product).single().restrictBranches);assertEquals(listOf(second),db.shopBranchDao().availability(product).map{it.branchId});assertEquals(second,products.branchPriorities(product).minBy{it.displayOrder}.branchId)
 }
 @Test fun manualModeBackDiscardsConfirmedDialogWithRestOfCard():Unit=runBlocking{
  prefs.edit().putBoolean(com.spisokryadom.app.util.ProductEditingPreferences.KEY,false).commit()
  val v=card();v.configureShop(shop,otherGroup,setOf(second),listOf(second,first));v.finishEditing();await{v.uiState.value.isSaved}
  val link=db.productShopLinkDao().getLinksForProductSync(product).single();assertEquals(group,link.departmentId);assertFalse(link.restrictBranches);assertTrue(db.shopBranchDao().availability(product).isEmpty());assertTrue(products.branchPriorities(product).isEmpty())
 }
}
