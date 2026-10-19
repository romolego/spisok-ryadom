package com.spisokryadom.app
import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.*
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.data.backup.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import org.json.JSONObject
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class AuditIntegrity071Test {
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 private lateinit var db:AppDatabase;private lateinit var locations:LocationRepository;private lateinit var shops:ShopRepository;private lateinit var products:ProductRepository;private lateinit var basket:ShoppingListRepository;private lateinit var classifiers:ClassifierRepository
 private var a=0L;private var b=0L;private var point=0L
 @Before fun setup()=runBlocking{
  db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build();RetailConstraints.install(db.openHelper.writableDatabase)
  db.cityDao().insert(CityEntity(1,"Красноярск"));locations=LocationRepository(db.cityDao(),db.shopDao(),app.getSharedPreferences("refinement",0));locations.select(1)
  shops=ShopRepository(db.shopDao(),db.shopDepartmentDao(),locations,db);products=ProductRepository(db.productDao(),db.productShopLinkDao(),db);basket=ShoppingListRepository(db.shoppingListEntryDao(),db,locations)
  classifiers=ClassifierRepository(db.productGroupDao(),db.recipientDao(),db.unitDao(),db)
  a=db.shopDao().insert(ShopEntity(name="Батон"));b=db.shopDao().insert(ShopEntity(name="Красный Яр",displayOrder=1));point=db.shopBranchDao().insert(ShopBranchEntity(shopId=a,address="Рядом"));Unit
 }
 @After fun close(){locations.closeForTest();db.close()}

 @Test fun newUnitCanonicalizesAliasesAndReusesSameIdentity()=runBlocking{
  val id=classifiers.insertUnit(UnitEntity(name=" КГ. "));assertEquals("кг",db.unitDao().getById(id)!!.name)
  assertEquals(id,classifiers.insertUnit(UnitEntity(name="килограмм")));assertEquals(1,db.unitDao().getAllSync().size)
 }
 @Test fun legacyUnitAliasesRenameAndStaleDeleteFollowCurrentValueButKeepBoughtFacts()=runBlocking{
  val id=db.unitDao().insert(UnitEntity(name="кг."));val old=db.unitDao().getById(id)!!
  val p=db.productDao().insert(ProductEntity(name="Мука",defaultUnit="кг"));val p2=db.productDao().insert(ProductEntity(name="Сахар",defaultUnit="КГ."))
  val pending=db.shoppingListEntryDao().insert(ShoppingListEntryEntity(productId=p,quantity=2.0,unit="килограмм"))
  val bought=db.shoppingListEntryDao().insert(ShoppingListEntryEntity(productId=p,quantity=3.0,unit="КГ.",isBought=true))
  val historical=db.shoppingListEntryDao().getById(bought)!!
  val temporary=db.productDao().insert(ProductEntity(name="Разовая мука",defaultUnit="КГ.",isTemporary=true))
  assertEquals(setOf(p,p2,temporary),products.getProductsByUnit("кг.").first().map{it.id}.toSet())
  classifiers.renameUnitAndProducts(old,"пакет")
  assertEquals("пакет",db.productDao().getById(p)!!.defaultUnit);assertEquals("пакет",db.productDao().getById(p2)!!.defaultUnit)
  assertEquals("пакет",db.shoppingListEntryDao().getById(pending)!!.unit);assertEquals(historical,db.shoppingListEntryDao().getById(bought))
  classifiers.deleteUnitAndProducts(old)
  assertNull(db.productDao().getById(p)!!.defaultUnit);assertEquals("",db.shoppingListEntryDao().getById(pending)!!.unit);assertEquals(historical,db.shoppingListEntryDao().getById(bought))
 }
 @Test fun deletingPointRecomputesPendingDepartmentWithoutReroutingOrRewritingHistory()=runBlocking{
  val milk=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=a,name="Молочка"));val cheese=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=a,name="Сыры"))
  val second=db.shopBranchDao().insert(ShopBranchEntity(shopId=a,address="Дальше",displayOrder=1))
  val p=products.saveWithLinks(ProductEntity(name="Сыр",defaultUnit="шт"),listOf(ProductShopLinkEntity(productId=0,shopId=a,departmentId=milk)),branchDepartments=mapOf(point to cheese))
  val pending=basket.addProduct(p,2.0,branchId=point);val bought=basket.addProduct(p,1.0,"пакет",branchId=point);basket.markBought(bought)
  val old=db.shoppingListEntryDao().getById(pending)!!;val historical=db.shoppingListEntryDao().getById(bought)!!
  shops.saveWithDepartments(db.shopDao().getById(a)!!,shops.getDepartmentsByShopIdSync(a),listOf(db.shopBranchDao().get(second)!!))
  val actual=db.shoppingListEntryDao().getById(pending)!!
  assertNull(actual.assignedBranchId);assertEquals(a,actual.assignedShopId);assertEquals(milk,actual.assignedDepartmentId);assertEquals(old.quantity,actual.quantity,0.0);assertEquals(old.createdAt,actual.createdAt)
  assertEquals(historical.copy(assignedBranchId=null),db.shoppingListEntryDao().getById(bought))
  val stable=actual;shops.saveWithDepartments(db.shopDao().getById(a)!!.copy(note="Заметка"),shops.getDepartmentsByShopIdSync(a),shops.branches(a));assertEquals(stable,db.shoppingListEntryDao().getById(pending))
 }
 @Test fun marketConversionRespectsRestrictedAvailabilityAndPointDepartmentForNoAddressPurchase()=runBlocking{
  val milk=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=a,name="Молочка"));val cheese=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=a,name="Сыры"))
  val second=db.shopBranchDao().insert(ShopBranchEntity(shopId=a,address="Сырный павильон",displayOrder=1))
  val p=products.saveWithLinks(ProductEntity(name="Сыр"),listOf(ProductShopLinkEntity(productId=0,shopId=a,departmentId=milk,restrictBranches=true)),availability=mapOf(a to setOf(second)),branchDepartments=mapOf(second to cheese))
  val row=basket.addProduct(p,shopId=a,branchId=0L,choice=ShopChoice.EXPLICIT)
  assertNull(db.shoppingListEntryDao().getById(row)!!.assignedBranchId)
  val venue=shops.convertToVenue(a);val actual=db.shoppingListEntryDao().getById(row)!!
  assertEquals(second,actual.assignedBranchId);assertEquals(venue,db.shopBranchDao().get(second)!!.venueId)
  assertTrue(db.productShopLinkDao().getLinksForProductSync(p).any{it.shopId==actual.assignedShopId})
  assertEquals("Сыры",db.shopDepartmentDao().getById(actual.assignedDepartmentId!!)!!.name)
  db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
 }
 @Test fun conversionUsesSavedDraftAndRollsBackWholeDraftIfConversionFails()=runBlocking{
  val old=db.shopDao().getById(a)!!
  assertTrue(runCatching{shops.saveWithDepartments(old.copy(name="Не должно сохраниться"),emptyList(),emptyList(),convertToVenue=true)}.isFailure)
  assertEquals(old,db.shopDao().getById(a));assertEquals(1,shops.branches(a).size);assertTrue(db.retailStructureDao().venues().isEmpty())
  shops.saveWithDepartments(old.copy(name="Новый рынок",note="Вход справа"),emptyList(),shops.branches(a)+ShopBranchEntity(id=-1,shopId=a,address="Новый павильон",note="Сыры"),convertToVenue=true)
  assertNull(db.shopDao().getById(a));val venue=db.retailStructureDao().venues().single();assertEquals("Новый рынок",venue.name);assertEquals("Вход справа",venue.note)
  assertEquals(2,db.shopBranchDao().getAll().count{it.venueId==venue.id});assertTrue(db.shopDao().getAllSync().any{it.name=="Новый павильон"&&it.note=="Сыры"})
 }

 @Test fun deletingLegacyAliasKeepsUnitWhileAnotherCanonicalClassifierRemains()=runBlocking{
  val canonical=db.unitDao().insert(UnitEntity(name="кг"));val alias=db.unitDao().insert(UnitEntity(name="КГ."))
  val p=db.productDao().insert(ProductEntity(name="Мука",defaultUnit="килограмм"));val row=basket.addProduct(p,unit="кг")
  classifiers.deleteUnitAndProducts(db.unitDao().getById(alias)!!)
  assertEquals(canonical,db.unitDao().getAllSync().single().id);assertEquals("кг",db.productDao().getById(p)!!.defaultUnit);assertEquals("кг",db.shoppingListEntryDao().getById(row)!!.unit)
 }
 @Test fun harmlessShopSavePreservesExplicitManualDepartmentsAndTimestamps()=runBlocking{
  val dept=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=a,name="Особый отдел"));val p=db.productDao().insert(ProductEntity(name="Свободный"))
  val row=basket.addProduct(p,choice=ShopChoice.EXPLICIT,shopId=a,branchId=0)
  val old=db.shoppingListEntryDao().getById(row)!!.copy(assignedDepartmentId=dept);db.shoppingListEntryDao().update(old)
  shops.saveWithDepartments(db.shopDao().getById(a)!!.copy(name="Новое название"),shops.getDepartmentsByShopIdSync(a),shops.branches(a))
  assertEquals(old,db.shoppingListEntryDao().getById(row))
 }
 @Test fun marketConversionKeepsBoughtDepartmentHistoryAndRefreshesOnlyPending()=runBlocking{
  val milk=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=a,name="Молочка"));val cheese=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=a,name="Сыры"))
  val p=products.saveWithLinks(ProductEntity(name="Сыр"),listOf(ProductShopLinkEntity(productId=0,shopId=a,departmentId=milk)))
  val bought=basket.addProduct(p,unit="пакет");basket.markBought(bought);val pending=basket.addProduct(p,unit="шт")
  products.saveWithLinks(db.productDao().getById(p)!!,db.productShopLinkDao().getLinksForProductSync(p),branchDepartments=mapOf(point to cheese))
  shops.convertToVenue(a)
  assertEquals("Молочка",db.shopDepartmentDao().getById(db.shoppingListEntryDao().getById(bought)!!.assignedDepartmentId!!)!!.name)
  assertEquals("Сыры",db.shopDepartmentDao().getById(db.shoppingListEntryDao().getById(pending)!!.assignedDepartmentId!!)!!.name)
 }
 @Test fun deletingLocalOrGlobalPointDepartmentFallsBackToRemainingLinkDepartment()=runBlocking{
  val milk=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=a,name="Молочка"))
  for(global in listOf(false,true)){
   val cheese=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=a,name="Сыры"))
   val p=products.saveWithLinks(ProductEntity(name="Сыр $global"),listOf(ProductShopLinkEntity(productId=0,shopId=a,departmentId=milk)),branchDepartments=mapOf(point to cheese))
   val pending=basket.addProduct(p,3.0,"шт");val bought=basket.addProduct(p,1.0,"пакет");basket.markBought(bought)
   val old=db.shoppingListEntryDao().getById(pending)!!.copy(note="Нежный",isUrgent=true);db.shoppingListEntryDao().update(old)
   val historical=db.shoppingListEntryDao().getById(bought)!!
   val department=db.shopDepartmentDao().getById(cheese)!!
   if(global)shops.deleteDepartmentGlobally(department.typeId)else shops.deleteDepartment(department)
   val actual=db.shoppingListEntryDao().getById(pending)!!
   assertEquals(old.copy(assignedDepartmentId=milk,updatedAt=actual.updatedAt),actual)
   assertEquals(historical.copy(assignedDepartmentId=null),db.shoppingListEntryDao().getById(bought))
   assertTrue(products.branchDepartments(p).isEmpty())
  }
 }
}
