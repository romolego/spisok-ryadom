package com.spisokryadom.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.*
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.ui.productcard.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class Refine120DataTest {
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 private lateinit var db:AppDatabase;private lateinit var products:ProductRepository;private lateinit var basket:ShoppingListRepository;private lateinit var locations:LocationRepository;private lateinit var batch:BatchRepository
 private var a=0L;private var b=0L;private var c=0L;private var first=0L;private var second=0L;private var recipient=0L;private var group=0L;private var type=0L
 private var ids=emptySet<Long>()
 @Before fun setup()=runBlocking{
  ProductDraftStore.preferences(app).edit().clear().commit()
  db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build();RetailConstraints.install(db.openHelper.writableDatabase)
  db.cityDao().insert(CityEntity(1,"Красноярск"));locations=LocationRepository(db.cityDao(),db.shopDao(),app.getSharedPreferences("120locations",0));products=ProductRepository(db.productDao(),db.productShopLinkDao(),db);basket=ShoppingListRepository(db.shoppingListEntryDao(),db,locations);batch=BatchRepository(db,products,basket)
  a=db.shopDao().insert(ShopEntity(name="Батон"));b=db.shopDao().insert(ShopEntity(name="Красный Яр"));c=db.shopDao().insert(ShopEntity(name="Аптека"));first=db.shopBranchDao().insert(ShopBranchEntity(shopId=a,address="Весны"));second=db.shopBranchDao().insert(ShopBranchEntity(shopId=a,address="Молокова",displayOrder=1))
  recipient=db.recipientDao().insert(RecipientEntity(name="Рома"));group=db.productGroupDao().insert(ProductGroupEntity(name="Продукты"));type=db.shopDepartmentDao().ensureType("Напитки")
  ids=(1..60).map{n->products.saveWithLinks(ProductEntity(name="Товар $n",defaultUnit="шт",note="Заметка $n",isFavorite=n==1),listOf(ProductShopLinkEntity(productId=0,shopId=a)))}.toSet();Unit
 }
 @After fun close(){locations.closeForTest();db.close()}
 @Test fun sixtyRecipientsAndGroupsChangeOnlyRequestedFreshFields()=runBlocking{
  val id=ids.first();db.productDao().update(db.productDao().getById(id)!!.copy(note="Свежая заметка",priceValue=12.0))
  val links=db.productShopLinkDao().getAllSync();batch.changeProducts(ids,ProductBatchChange.Recipient(recipient));batch.changeProducts(ids,ProductBatchChange.Group(group))
  assertEquals(60,db.productDao().getAllSync().count{it.recipientId==recipient&&it.productGroupId==group});assertEquals("Свежая заметка",db.productDao().getById(id)!!.note);assertEquals(12.0,db.productDao().getById(id)!!.priceValue!!,0.0);assertEquals(links,db.productShopLinkDao().getAllSync())
  batch.changeProducts(ids,ProductBatchChange.Recipient(null));assertTrue(db.productDao().getAllSync().all{it.recipientId==null&&it.productGroupId==group})
 }
 @Test fun invalidProductOrClassifierRollsBackWholeBatch()=runBlocking{
  val before=db.productDao().getAllSync();assertTrue(runCatching{batch.changeProducts(ids+99999,ProductBatchChange.Recipient(recipient))}.isFailure);assertEquals(before,db.productDao().getAllSync())
  assertTrue(runCatching{batch.changeProducts(ids,ProductBatchChange.Group(99999))}.isFailure);assertEquals(before,db.productDao().getAllSync())
 }
 @Test fun addingSeveralShopsIsIdempotentAndPreservesExistingRestrictionsAndPurchase()=runBlocking{
  val p=ids.first();products.saveWithLinks(db.productDao().getById(p)!!,products.getShopLinksForProductSync(p),mapOf(a to setOf(second)),branchPriorities=mapOf(a to listOf(second,first)))
  val entry=basket.addProduct(p);val old=db.shoppingListEntryDao().getById(entry)!!;val link=products.getShopLinksForProductSync(p).single()
  repeat(2){batch.changeProducts(ids,ProductBatchChange.Shops(setOf(a,b)))}
  assertEquals(120,db.productShopLinkDao().getAllSync().size);assertEquals(link,products.getShopLinksForProductSync(p).first{it.shopId==a});assertEquals(setOf(second),products.branchAvailability(p).map{it.branchId}.toSet());assertEquals(second,basket.resolveBranch(p,a));assertEquals(old,db.shoppingListEntryDao().getById(entry));assertTrue(db.shopDepartmentDao().getByShopIdSync(c).isEmpty())
 }
 @Test fun departmentsAttachOnlyToSelectedStoresWithoutDuplicates()=runBlocking{
  batch.addDepartments(setOf(a,b),setOf(type));batch.addDepartments(setOf(a,b),setOf(type));assertEquals(2,db.shopDepartmentDao().getAllSync().size);assertTrue(db.shopDepartmentDao().getByShopIdSync(c).isEmpty());assertTrue(db.productShopLinkDao().getAllSync().all{it.departmentId==null})
  val before=db.shopDepartmentDao().getAllSync();assertTrue(runCatching{batch.addDepartments(setOf(a,c,9999),setOf(type))}.isFailure);assertEquals(before,db.shopDepartmentDao().getAllSync())
 }
 @Test fun departmentAssignmentRefreshesPendingOnlyAndClearsPointOverrideInWholeStoreScope()=runBlocking{
  val p=ids.first();val oldType=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=a,name="Выпечка"));products.saveWithLinks(db.productDao().getById(p)!!,products.getShopLinksForProductSync(p),branchDepartments=mapOf(second to oldType))
  val active=basket.addProduct(p,choice=ShopChoice.EXPLICIT,shopId=a,branchId=second);val bought=basket.addProduct(p,quantity=2.0,choice=ShopChoice.EXPLICIT,shopId=a,branchId=first);basket.markBought(bought);val history=db.shoppingListEntryDao().getById(bought);val before=db.shoppingListEntryDao().getById(active)!!
  batch.changeProducts(ids,ProductBatchChange.Department(a,type))
  val dep=db.shopDepartmentDao().getByShopIdSync(a).first{it.typeId==type};assertEquals(dep.id,db.shoppingListEntryDao().getById(active)!!.assignedDepartmentId);assertEquals(history,db.shoppingListEntryDao().getById(bought));assertEquals(before.quantity,db.shoppingListEntryDao().getById(active)!!.quantity,0.0);assertTrue(products.branchDepartments(p).isEmpty())
  batch.changeProducts(ids,ProductBatchChange.Department(a,null));assertNull(db.shoppingListEntryDao().getById(active)!!.assignedDepartmentId)
 }
 @Test fun shopAssignmentUpdatesAllPointsAndLeavesOtherProductsAlone()=runBlocking{
  val p=ids.first();val q=ids.last();val default=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=a,name="Бакалея"));products.saveWithLinks(db.productDao().getById(p)!!,products.getShopLinksForProductSync(p).map{it.copy(departmentId=default)})
  val x=basket.addProduct(p,choice=ShopChoice.EXPLICIT,shopId=a,branchId=first);val y=basket.addProduct(p,choice=ShopChoice.EXPLICIT,shopId=a,branchId=second)
  batch.changeProducts(setOf(p),ProductBatchChange.Department(a,type));val selected=products.getShopLinksForProductSync(p).single().departmentId;assertNotEquals(default,selected);assertEquals(selected,db.shoppingListEntryDao().getById(x)!!.assignedDepartmentId);assertEquals(selected,db.shoppingListEntryDao().getById(y)!!.assignedDepartmentId);assertTrue(products.branchDepartments(q).isEmpty());assertNull(products.getShopLinksForProductSync(q).single().departmentId)
 }
 @Test fun unlinkedShopRejectsEntireSelectionAndRestrictedAvailabilityIsPreserved()=runBlocking{
  val p=ids.last();products.saveWithLinks(db.productDao().getById(p)!!,products.getShopLinksForProductSync(p),mapOf(a to setOf(first)))
  assertTrue(runCatching{batch.changeProducts(ids,ProductBatchChange.Department(b,type))}.isFailure);assertTrue(db.shopDepartmentDao().getAllSync().isEmpty())
  batch.changeProducts(ids,ProductBatchChange.Department(a,type));assertEquals(setOf(first),products.branchAvailability(p).map{it.branchId}.toSet());assertTrue(products.branchDepartments(p).isEmpty())
 }
 @Test fun mixedPurchaseStatusCanBuyReturnDeleteWithFactsPreserved()=runBlocking{
  val entries=ids.take(3).map{basket.addProduct(it,quantity=3.0)}.toSet();val old=db.shoppingListEntryDao().getAllSync();basket.markBought(entries.first())
  batch.changePurchases(entries,PurchaseBatchChange.BOUGHT);assertTrue(db.shoppingListEntryDao().getAllSync().all{it.isBought});batch.changePurchases(entries,PurchaseBatchChange.RETURN);batch.changePurchases(entries,PurchaseBatchChange.URGENT);assertTrue(db.shoppingListEntryDao().getAllSync().all{!it.isBought&&it.isUrgent})
  old.forEach{o->val row=db.shoppingListEntryDao().getById(o.id)!!;assertEquals(o.createdAt,row.createdAt);assertEquals(o.quantity,row.quantity,0.0);assertEquals(o.assignedBranchId,row.assignedBranchId)}
  assertTrue(runCatching{batch.changePurchases(entries+99999,PurchaseBatchChange.DELETE)}.isFailure);assertEquals(3,db.shoppingListEntryDao().getAllSync().size);batch.changePurchases(entries,PurchaseBatchChange.DELETE);assertTrue(db.shoppingListEntryDao().getAllSync().isEmpty());assertEquals(60,db.productDao().getAllSync().size)
 }
 @Test fun boughtUrgencyAndInvalidBasketBatchLeaveEverythingIntact()=runBlocking{
  val entry=basket.addProduct(ids.first());basket.markBought(entry);val old=db.shoppingListEntryDao().getAllSync();assertTrue(runCatching{batch.changePurchases(setOf(entry),PurchaseBatchChange.URGENT)}.isFailure);assertEquals(old,db.shoppingListEntryDao().getAllSync());assertTrue(runCatching{batch.addToBasket(ids+99999)}.isFailure);assertEquals(old,db.shoppingListEntryDao().getAllSync())
 }
 @Test fun draftReconciliationPreservesRawInputButCannotUndoBatchOnRecovery()=runBlocking{
  val p=ids.first();val product=db.productDao().getById(p)!!
  val state=ProductCardUiState(isNew=false,isLoading=false,name=product.name,defaultQuantity="1,",note="Незавершённая заметка",shopLinks=products.getShopLinksForProductSync(p).map{ShopLinkUi(it,"Батон",null)})
  val prefs=ProductDraftStore.preferences(app);ProductDraftStore.write(prefs,"draft",ProductEditDraft(product.stableId,p,false,false,state))
  val changes=listOf(ProductBatchChange.Recipient(recipient),ProductBatchChange.Group(group),ProductBatchChange.Shops(setOf(b)),ProductBatchChange.Department(a,type))
  changes.forEach{batch.changeProducts(setOf(p),it);ProductDraftStore.reconcileBatch(app,products,setOf(p),it)}
  val draft=ProductDraftStore.find(prefs,product.stableId)!!;assertEquals("1,",draft.state.defaultQuantity);assertEquals("Незавершённая заметка",draft.state.note);assertEquals(recipient,draft.state.recipientId);assertEquals(group,draft.state.productGroupId);assertEquals(setOf(a,b),draft.state.shopLinks.map{it.link.shopId}.toSet());assertTrue(draft.state.shopLinks.all{it.shopName.isNotBlank()})
  draft.copy(state=draft.state.copy(defaultQuantity="1")).persist(products);assertEquals(recipient,db.productDao().getById(p)!!.recipientId);assertTrue(products.branchDepartments(p).isEmpty());assertEquals(type,db.shopDepartmentDao().getById(products.getShopLinksForProductSync(p).single{it.shopId==a}.departmentId!!)!!.typeId);assertEquals(setOf(a,b),products.getShopLinksForProductSync(p).map{it.shopId}.toSet())
 }
 @Test fun addingStoreResolvesAutomaticUnassignedPurchaseButPreservesExplicitNoShop()=runBlocking{
  val x=db.productDao().insert(ProductEntity(name="Без магазина",defaultUnit="шт"));val auto=basket.addProduct(x);val explicit=basket.addProduct(x,choice=ShopChoice.NONE)
  assertNull(db.shoppingListEntryDao().getById(auto)!!.assignedShopId);assertTrue(db.shoppingListEntryDao().getById(explicit)!!.explicitNoShop)
  batch.changeProducts(setOf(x),ProductBatchChange.Shops(setOf(a)))
  assertEquals(a,db.shoppingListEntryDao().getById(auto)!!.assignedShopId);assertNull(db.shoppingListEntryDao().getById(explicit)!!.assignedShopId);assertTrue(db.shoppingListEntryDao().getById(explicit)!!.explicitNoShop)
  val before=db.productShopLinkDao().getAllSync();assertTrue(runCatching{batch.changeProducts(ids,ProductBatchChange.Shops(setOf(b,99999)))}.isFailure);assertEquals(before,db.productShopLinkDao().getAllSync())
 }

 @Test fun closingCallerAfterCommitStillReconcilesRecoverableDraft()=runBlocking{
  val id=ids.first();val p=db.productDao().getById(id)!!;val prefs=ProductDraftStore.preferences(app)
  ProductDraftStore.write(prefs,"draft",ProductEditDraft(p.stableId,id,false,false,ProductCardUiState(isNew=false,isLoading=false,name=p.name,defaultQuantity="1,")))
  val committed=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();val change=ProductBatchChange.Recipient(recipient)
  val job=launch{ProductDraftStore.applyBatch(app,products,setOf(id),change){batch.changeProducts(setOf(id),change);committed.complete(Unit);release.await()}}
  committed.await();job.cancel();release.complete(Unit);job.join()
  assertEquals(recipient,db.productDao().getById(id)!!.recipientId);assertEquals(recipient,ProductDraftStore.find(prefs,p.stableId)!!.state.recipientId);assertEquals("1,",ProductDraftStore.find(prefs,p.stableId)!!.state.defaultQuantity)
 }

 @Test fun deletingPurchasesCleansOnlyUnusedTemporaryCardsLikeSingleDelete()=runBlocking{
  val temporary=db.productDao().insert(ProductEntity(name="Разовая покупка",isTemporary=true));val retained=db.productDao().insert(ProductEntity(name="Разовая с историей",isTemporary=true))
  val x=basket.addProduct(temporary);val history=basket.addProduct(retained,quantity=2.0);basket.markBought(history);val y=basket.addProduct(retained)
  batch.changePurchases(setOf(x,y),PurchaseBatchChange.DELETE)
  assertNull(db.productDao().getById(temporary));assertNotNull(db.productDao().getById(retained));assertNotNull(db.shoppingListEntryDao().getById(history));assertEquals(60,db.productDao().getAllSync().count{!it.isTemporary})
 }

 @Test fun linkAdditionAndShopGroupPreserveOtherUnfinishedDraftChoices()=runBlocking{
  val id=ids.first();val p=db.productDao().getById(id)!!;val local=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=a,name="Мой отдел"));val base=products.getShopLinksForProductSync(id).single()
  val state=ProductCardUiState(isNew=false,isLoading=false,name=p.name,defaultQuantity="1,",shopLinks=listOf(ShopLinkUi(base.copy(departmentId=local),"Батон","Мой отдел")),branchSelections=mapOf(a to setOf(first,second)),branchDepartments=mapOf(first to local))
  val prefs=ProductDraftStore.preferences(app);ProductDraftStore.write(prefs,"draft",ProductEditDraft(p.stableId,id,false,false,state))
  listOf(ProductBatchChange.Shops(setOf(a,b)),ProductBatchChange.Department(a,type)).forEach{change->ProductDraftStore.applyBatch(app,products,setOf(id),change){batch.changeProducts(setOf(id),change)}}
  val draft=ProductDraftStore.find(prefs,p.stableId)!!;assertNotEquals(local,draft.state.shopLinks.single{it.link.shopId==a}.link.departmentId);assertEquals(setOf(first,second),draft.state.branchSelections[a]);assertTrue(draft.state.branchDepartments.isEmpty());assertEquals("1,",draft.state.defaultQuantity)
  draft.copy(state=draft.state.copy(defaultQuantity="1")).persist(products);assertEquals(setOf(first,second),products.branchAvailability(id).map{it.branchId}.toSet());assertTrue(products.branchDepartments(id).isEmpty())
 }

}
