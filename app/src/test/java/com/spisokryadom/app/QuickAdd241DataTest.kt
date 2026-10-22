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
import com.spisokryadom.app.ui.addtolist.AddToListViewModel
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
class QuickAdd241DataTest {
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 private lateinit var db:AppDatabase
 private lateinit var places:LocationRepository
 private lateinit var shops:ShopRepository
 private lateinit var products:ProductRepository
 private lateinit var basket:ShoppingListRepository
 private val models=ViewModelStore()
 @Before fun setup()=runBlocking{
  app.getSharedPreferences("241",0).edit().clear().commit()
  db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build()
  db.cityDao().insert(CityEntity(1,"Город"));places=LocationRepository(db.cityDao(),db.shopDao(),app.getSharedPreferences("241",0))
  shops=ShopRepository(db.shopDao(),db.shopDepartmentDao(),places,db)
  products=ProductRepository(db.productDao(),db.productShopLinkDao(),db)
  basket=ShoppingListRepository(db.shoppingListEntryDao(),db,places)
 }
 @After fun close(){models.clear();Shadows.shadowOf(Looper.getMainLooper()).idle();places.closeForTest();db.close()}
 private fun await(check:()->Boolean){val end=System.nanoTime()+15_000_000_000;while(!check()&&System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5)};assertTrue(check())}
 private fun model()=AddToListViewModel(products,shops,basket,app.getSharedPreferences("241",0),units=db.unitDao().getAll()).also{models.put("add",it)}
 @Test fun typingKeepsResolvedFormAndRejectsStaleQuickAddUntilLatestResult(){
  val id=runBlocking{db.productDao().insert(ProductEntity(name="Банан"))}
  val vm=model();await{vm.uiState.value.searchResults.size==1}
  vm.onSearchQueryChanged("Помада");await{!vm.uiState.value.isSearching&&vm.uiState.value.isNewProduct}
  vm.onSearchQueryChanged("Помада ги")
  vm.onShopSelected(vm.uiState.value.selectedShopId)
  assertTrue(vm.uiState.value.isSearching);assertTrue(vm.uiState.value.isNewProduct)
  vm.add();assertFalse(vm.uiState.value.isLoading)
  vm.onSearchQueryChanged("Банан");vm.onSearchQueryChanged("Помада гигиеническая")
  vm.quickAdd(runBlocking{db.productDao().getById(id)!!})
  await{!vm.uiState.value.isSearching};assertTrue(vm.uiState.value.isNewProduct);assertTrue(vm.uiState.value.searchResults.isEmpty())
  assertTrue(runBlocking{db.shoppingListEntryDao().getAllSync().isEmpty()})
 }
 @Test fun quickFormObservesUserUnitsAndSavesChosenUnit(){
  runBlocking{db.unitDao().insert(UnitEntity(name="мешок"));db.unitDao().insert(UnitEntity(name="ед."))}
  val vm=model();await{vm.uiState.value.unitSuggestions.size==2}
  vm.onSearchQueryChanged("Покупка");await{!vm.uiState.value.isSearching&&vm.uiState.value.isNewProduct}
  runBlocking{db.unitDao().insert(UnitEntity(name="кг"))};await{"кг" in vm.uiState.value.unitSuggestions}
  vm.quantity("2");vm.unit("мешок");vm.add();await{vm.uiState.value.addedMessage!=null}
  assertEquals("мешок",runBlocking{db.shoppingListEntryDao().getAllSync().single().unit})
 }
 @Test fun networkGroupsShareOrderKeepIdsAndDoNotAffectUnrelatedShops()=runBlocking{
  val network=shops.ensureNetwork("Фикс Прайс")
  val a=db.shopDao().insert(ShopEntity(name="Фикс Прайс",networkId=network))
  val b=db.shopDao().insert(ShopEntity(name="Фикс Прайс у дома",networkId=network))
  val c=db.shopDao().insert(ShopEntity(name="Другой"))
  val first=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=a,name="Быт",displayOrder=1))
  db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=a,name="Еда",displayOrder=2))
  val shared=db.shopDepartmentDao().getById(first)!!
  val stable=db.shopDepartmentDao().insert(shared.copy(id=0,shopId=b,displayOrder=5))
  db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=b,name="Одежда",displayOrder=6))
  val product=products.saveWithLinks(ProductEntity(name="Полотенце"),listOf(ProductShopLinkEntity(productId=0,shopId=b,departmentId=stable)))
  val purchase=basket.addProduct(product,choice=ShopChoice.EXPLICIT,shopId=b)
  shops.saveWithDepartments(db.shopDao().getById(a)!!,db.shopDepartmentDao().getByShopIdSync(a).reversed())
  fun names(rows:List<ShopDepartmentEntity>)=rows.sortedBy{it.displayOrder}.map{it.name}
  assertEquals(listOf("Еда","Быт","Одежда"),names(db.shopDepartmentDao().getByShopIdSync(a)))
  assertEquals(names(db.shopDepartmentDao().getByShopIdSync(a)),names(db.shopDepartmentDao().getByShopIdSync(b)))
  assertEquals(stable,db.productShopLinkDao().getLinksForProductSync(product).single().departmentId)
  assertEquals(stable,db.shoppingListEntryDao().getById(purchase)!!.assignedDepartmentId)
  assertTrue(db.shopDepartmentDao().getByShopIdSync(c).isEmpty())
  val removed=db.shopDepartmentDao().getByShopIdSync(a).single{it.name=="Одежда"}
  shops.deleteDepartment(removed)
  assertEquals(listOf("Еда","Быт"),names(db.shopDepartmentDao().getByShopIdSync(b)))
  val archive=BackupManager(app,db).exportFile();BackupManager(app,db).importFile(archive)
  assertEquals(stable,db.shoppingListEntryDao().getById(purchase)!!.assignedDepartmentId)
  assertEquals(names(db.shopDepartmentDao().getByShopIdSync(a)),names(db.shopDepartmentDao().getByShopIdSync(b)))
 }
 @Test fun legacyPointOrderCannotOverrideTheSharedOrderInMainShopOrWidget(){
  val data=runBlocking{
   val shop=db.shopDao().insert(ShopEntity(name="Фикс Прайс"))
   val point=db.shopBranchDao().insert(ShopBranchEntity(shopId=shop,address="Мира, 1"))
   val groups=listOf("Быт","Еда").mapIndexed{i,name->db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=shop,name=name,displayOrder=i))}
   groups.forEachIndexed{i,id->
    val group=db.shopDepartmentDao().getById(id)!!
    db.retailStructureDao().setDepartmentOrder(BranchDepartmentOrderEntity(point,group.typeId,1-i))
    val product=products.saveWithLinks(ProductEntity(name="Товар $i"),listOf(ProductShopLinkEntity(productId=0,shopId=shop,departmentId=id,restrictBranches=true)),mapOf(shop to setOf(point)))
    basket.addProduct(product,choice=ShopChoice.EXPLICIT,shopId=shop,branchId=point)
   }
   shop to groups
  }
  val classifiers=ClassifierRepository(db.productGroupDao(),db.recipientDao(),db.unitDao(),db)
  val main=com.spisokryadom.app.ui.shoppinglist.ShoppingListViewModel(basket,products,shops,classifiers,app.getSharedPreferences("241-main",0));models.put("main",main)
  val content=com.spisokryadom.app.ui.shopcontent.ShopContentViewModel(data.first,products,shops,basket,classifiers);models.put("content",content)
  // Entry counts can arrive before the independent shop/department Room flows.
  // Wait for both projections to contain their labels, without accepting any order.
  await{
   val mainGroup=main.uiState.value.groups.singleOrNull()
   val contentRows=content.uiState.value.sections.singleOrNull()?.products
   mainGroup!=null&&contentRows!=null&&main.uiState.value.totalCount==2&&mainGroup.groupId==data.first&&mainGroup.entries.size==2&&
    mainGroup.deptSubGroups.size==2&&mainGroup.deptSubGroups.all{it.deptName!=null}&&
    contentRows.size==2&&contentRows.all{it.departmentName!=null}
  }
  assertEquals(listOf("Быт","Еда"),main.uiState.value.groups.single().deptSubGroups.map{it.deptName})
  assertEquals(listOf("Быт","Еда"),content.uiState.value.sections.single().products.map{it.departmentName})
  val items=runBlocking{com.spisokryadom.app.widget.ShoppingWidgetData(db,basket,places).presentation(com.spisokryadom.app.widget.WidgetSettings())}
  assertEquals(listOf("Товар 0","Товар 1"),items.filterIsInstance<com.spisokryadom.app.widget.WidgetItem.Purchase>().map{it.row.title})
  assertEquals(2,runBlocking{db.retailStructureDao().departmentOrder().size})
 }
 @Test fun joiningNetworkAndCreatingProductGroupPreserveExistingGroupsAndRollbackOnInvalidPoint()=runBlocking{
  val network=shops.ensureNetwork("Сеть")
  val a=db.shopDao().insert(ShopEntity(name="Первый",networkId=network))
  val b=db.shopDao().insert(ShopEntity(name="Второй"))
  db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=a,name="Быт"))
  shops.saveWithDepartments(db.shopDao().getById(b)!!.copy(networkId=network),emptyList())
  assertEquals("Быт",db.shopDepartmentDao().getByShopIdSync(b).single().name)
  val pending=ShopDepartmentEntity(id=-1,shopId=b,name="Сладости")
  products.saveWithLinks(ProductEntity(name="Печенье"),listOf(ProductShopLinkEntity(productId=0,shopId=b,departmentId=-1)),departments=listOf(pending))
  assertEquals(listOf("Быт","Сладости"),db.shopDepartmentDao().getByShopIdSync(a).map{it.name})
  val before=db.shopDepartmentDao().getAllSync()
  try{
   shops.saveWithDepartments(db.shopDao().getById(a)!!,emptyList(),listOf(ShopBranchEntity(shopId=a,address="")))
   fail("Invalid point must roll back the entire network edit")
  }catch(expected:IllegalArgumentException){}
  assertEquals(before,db.shopDepartmentDao().getAllSync())
 }
}
