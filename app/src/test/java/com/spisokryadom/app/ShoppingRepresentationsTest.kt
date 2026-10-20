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
import com.spisokryadom.app.ui.bulk.BulkViewModel
import com.spisokryadom.app.ui.components.GroupExpansion
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.*
import java.util.UUID
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ShoppingRepresentationsTest {
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 private lateinit var db:AppDatabase
 private lateinit var locations:LocationRepository
 private lateinit var basket:ShoppingListRepository
 private lateinit var shops:ShopRepository
 private lateinit var products:ProductRepository
 private val store=ViewModelStore()
 private val prefs by lazy{app.getSharedPreferences("text-test-${UUID.randomUUID()}",0)}
 private var baton=0L;private var yar=0L;private var near=0L;private var far=0L;private var pasta=0L;private var apple=0L
 @Before fun setup()=runBlocking {
  db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build()
  db.cityDao().insert(CityEntity(1,"Красноярск"))
  locations=LocationRepository(db.cityDao(),db.shopDao(),app.getSharedPreferences("loc-${UUID.randomUUID()}",0));locations.select(1)
  basket=ShoppingListRepository(db.shoppingListEntryDao(),db,locations)
  shops=ShopRepository(db.shopDao(),db.shopDepartmentDao(),locations,db)
  products=ProductRepository(db.productDao(),db.productShopLinkDao(),db)
  baton=db.shopDao().insert(ShopEntity(name="Батон"))
  yar=db.shopDao().insert(ShopEntity(name="Красный Яр"))
  near=db.shopBranchDao().insert(ShopBranchEntity(shopId=yar,address="Мира, 10",displayOrder=0))
  far=db.shopBranchDao().insert(ShopBranchEntity(shopId=yar,address="Ленина, 20",displayOrder=1))
  pasta=db.productDao().insert(ProductEntity(name="Спагетти",defaultUnit="уп"))
  apple=db.productDao().insert(ProductEntity(name="Яблоки Гала",defaultUnit="кг"))
  db.productShopLinkDao().insert(ProductShopLinkEntity(productId=pasta,shopId=yar,restrictBranches=true))
  db.productShopLinkDao().insert(ProductShopLinkEntity(productId=apple,shopId=yar))
  db.shopBranchDao().insertAvailability(ProductBranchAvailabilityEntity(pasta,far))
  Unit
 }
 @After fun close(){store.clear();Shadows.shadowOf(Looper.getMainLooper()).idle();locations.closeForTest();db.close()}
 private fun model(key:String)=BulkViewModel(products,basket,locations,prefs,textView=true).also{store.put(key,it);await{!it.uiState.value.busy}}
 private fun await(condition:()->Boolean){val until=System.nanoTime()+10_000_000_000;while(!condition()&&System.nanoTime()<until){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5)};assertTrue(condition())}

 @Test fun chainPriorityRespectsExclusiveProductAvailability()=runBlocking {
  val a=basket.addProduct(apple,2.0);val p=basket.addProduct(pasta,1.0)
  assertEquals(near,db.shoppingListEntryDao().getById(a)!!.assignedBranchId)
  assertEquals(far,db.shoppingListEntryDao().getById(p)!!.assignedBranchId)
  val chain=db.shopDao().getById(yar)!!
  shops.saveWithDepartments(chain,emptyList(),db.shopBranchDao().forShop(yar).reversed())
  assertEquals(far,db.shoppingListEntryDao().getById(basket.addProduct(apple,3.0))!!.assignedBranchId)
  assertEquals(near,db.shoppingListEntryDao().getById(a)!!.assignedBranchId) // Existing route is not silently rewritten.
  assertEquals(far,basket.resolveBranch(pasta,yar))
 }

 @Test fun quantitiesAtDifferentPointsNeverCollapseIntoOnePurchase()=runBlocking {
  basket.addOrMergeEntry(ShoppingListEntryEntity(productId=apple,quantity=1.0,unit="кг",assignedShopId=yar,assignedBranchId=near))
  basket.addOrMergeEntry(ShoppingListEntryEntity(productId=apple,quantity=2.0,unit="кг",assignedShopId=yar,assignedBranchId=far))
  assertEquals(2,db.shoppingListEntryDao().getAllSync().size)
 }

 @Test fun fifteenPurchasesMoveOnlyFourPendingAndPreserveAllTheirFields()=runBlocking {
  val entries=(0 until 15).map{i->db.shoppingListEntryDao().insert(ShoppingListEntryEntity(productId=apple,quantity=i+1.0,unit="кг",assignedShopId=baton,isBought=i<11,isUrgent=i%2==0,note="Заметка $i",comment="Комментарий $i",createdAt=i.toLong()))}
  val before=db.shoppingListEntryDao().getAllSync().associateBy{it.id}
  assertEquals(4,basket.movePending(entries.toSet(),yar,far))
  val after=db.shoppingListEntryDao().getAllSync().associateBy{it.id}
  before.forEach{(id,old)->val actual=after.getValue(id);if(old.isBought)assertEquals(old,actual)else{assertEquals(yar,actual.assignedShopId);assertEquals(far,actual.assignedBranchId);assertEquals(old.copy(assignedShopId=yar,assignedBranchId=far,updatedAt=actual.updatedAt),actual)}}
 }

 @Test fun invalidPointRollsBackTheWholeSelectionAndAutomaticMoveUsesIndividualPoints()=runBlocking {
  val a=basket.addProduct(apple,1.0,choice=ShopChoice.EXPLICIT,shopId=baton)
  val p=basket.addProduct(pasta,1.0,choice=ShopChoice.EXPLICIT,shopId=baton)
  val before=db.shoppingListEntryDao().getAllSync()
  try{basket.movePending(setOf(a,p),yar,near);fail("Restricted point must be rejected")}catch(expected:IllegalArgumentException){}
  assertEquals(before,db.shoppingListEntryDao().getAllSync())
  assertEquals(setOf(far),basket.moveOptions(setOf(a,p),yar))
  basket.movePending(setOf(a,p),yar)
  assertEquals(near,db.shoppingListEntryDao().getById(a)!!.assignedBranchId)
  assertEquals(far,db.shoppingListEntryDao().getById(p)!!.assignedBranchId)
 }

 @Test fun removingOnlyExclusivePointIsRejectedWithoutChangingTheChain()=runBlocking {
  val before=db.shopBranchDao().forShop(yar)
  try{shops.saveWithDepartments(db.shopDao().getById(yar)!!,emptyList(),before.filter{it.id!=far});fail("Must not erase unique availability")}catch(expected:IllegalArgumentException){}
  assertEquals(before,db.shopBranchDao().forShop(yar))
  assertEquals(listOf(ProductBranchAvailabilityEntity(pasta,far,yar)),db.shopBranchDao().availability(pasta))
 }

 @Test fun textIsPersistentRepresentationWithoutDoublingAndBoughtRowsAreProtected() {
  val ids=runBlocking {
   val active=db.shoppingListEntryDao().insert(ShoppingListEntryEntity(productId=apple,quantity=2.0,unit="кг",assignedShopId=yar,assignedBranchId=near,isUrgent=true,note="Зелёные",comment="Без пакета"))
   val bought=db.shoppingListEntryDao().insert(ShoppingListEntryEntity(productId=pasta,quantity=1.0,unit="уп",assignedShopId=yar,assignedBranchId=far,isBought=true))
   active to bought
  }
  val boughtBefore=runBlocking{db.shoppingListEntryDao().getById(ids.second)}
  val vm=model("first")
  assertTrue(vm.uiState.value.text.contains("Яблоки Гала — 2 кг"));assertFalse(vm.uiState.value.text.contains("Спагетти"))
  vm.text(vm.uiState.value.text.replace("— 2 кг","— 3,5 кг"));vm.review();await{vm.uiState.value.review&&!vm.uiState.value.busy}
  assertEquals(ids.first,vm.uiState.value.rows.single().entryId)
  vm.add();await{vm.uiState.value.saved&&!vm.uiState.value.busy}
  assertFalse(vm.uiState.value.review);assertTrue(vm.uiState.value.text.contains("3,5 кг"))
  runBlocking {val row=db.shoppingListEntryDao().getById(ids.first)!!;assertEquals(3.5,row.quantity,0.0);assertEquals("Зелёные",row.note);assertEquals("Без пакета",row.comment);assertTrue(row.isUrgent);assertEquals(boughtBefore,db.shoppingListEntryDao().getById(ids.second))}
  vm.review();await{vm.uiState.value.review&&!vm.uiState.value.busy};vm.add();await{vm.uiState.value.saved&&!vm.uiState.value.busy&&!vm.uiState.value.review}
  assertEquals(2,runBlocking{db.shoppingListEntryDao().getAllSync()}.size)
  assertTrue(model("reopened").uiState.value.text.contains("3,5 кг"))
 }

 @Test fun unsavedDraftSurvivesAndStaleTextCannotOverwriteNewBasketQuantity() {
  val id=runBlocking{basket.addProduct(apple,2.0)}
  val vm=model("draft");vm.text(vm.uiState.value.text.replace("— 2 кг","— 5 кг"))
  val restored=model("restored");assertTrue(restored.uiState.value.text.contains("5 кг"))
  runBlocking{basket.updateQuantity(id,7.0)}
  restored.review();await{restored.uiState.value.review&&!restored.uiState.value.busy};restored.add()
  await{!restored.uiState.value.busy&&restored.uiState.value.error!=null}
  assertEquals(7.0,runBlocking{db.shoppingListEntryDao().getById(id)}!!.quantity,0.0)
  assertTrue(restored.uiState.value.text.contains("5 кг"))
 }

 @Test fun deletingTextRemovesOnlyPendingAndUndoRestoresTemporaryProductAndMetadata() {
  val active=runBlocking{basket.addNewProduct("Разовый",.000001,"сложная единица 2",false)}
  val bought=runBlocking{basket.addProduct(pasta,1.0).also{basket.markBought(it)}}
  val before=runBlocking{db.shoppingListEntryDao().getAllSync()}.associateBy{it.id}
  val vm=model("delete")
  assertTrue(vm.uiState.value.text.contains("0,000001 сложная единица 2"))
  vm.text("");vm.review();await{vm.uiState.value.review&&!vm.uiState.value.busy}
  assertEquals(1,vm.uiState.value.removedNames.size)
  vm.add();await{vm.uiState.value.saved&&!vm.uiState.value.busy}
  assertEquals(listOf(bought),runBlocking{db.shoppingListEntryDao().getAllSync()}.map{it.id})
  vm.undo();await{!vm.uiState.value.busy&&!vm.uiState.value.canUndo}
  assertEquals(before,runBlocking{db.shoppingListEntryDao().getAllSync()}.associateBy{it.id})
  assertNotNull(runBlocking{db.productDao().getById(before.getValue(active).productId)})
 }

 @Test fun ambiguousSameProductRowsRequireIdentityChoiceInsteadOfDiscardingNotes() {
  runBlocking {
   db.shoppingListEntryDao().insert(ShoppingListEntryEntity(productId=apple,quantity=1.0,unit="кг",note="Одни"))
   db.shoppingListEntryDao().insert(ShoppingListEntryEntity(productId=apple,quantity=2.0,unit="кг",note="Другие"))
  }
  val vm=model("ambiguous");vm.text("Без магазина:\nЯблоки Гала 3 кг\nЯблоки Гала 4 кг");vm.review();await{vm.uiState.value.review&&!vm.uiState.value.busy}
  assertTrue(vm.uiState.value.rows.all{!it.entryResolved})
  vm.add();assertNotNull(vm.uiState.value.error)
  assertEquals(listOf(1.0,2.0),runBlocking{db.shoppingListEntryDao().getAllSync()}.map{it.quantity})
 }

 @Test fun zipFiveRoundTripsAddressesRestrictionsPurchasesAndCollapsedPreferences()=runBlocking {
  val id=basket.addProduct(pasta,3.0)
  app.getSharedPreferences("product_db_prefs",0).edit().putStringSet("collapsed_sections",setOf("BY_GROUPS_group_1")).commit()
  val backup=BackupManager(app,db);val file=backup.exportFile()
  assertEquals(14,ZipFile(file).use{JSONObject(it.getInputStream(it.getEntry("manifest.json")).reader().readText()).getInt("version")})
  db.shoppingListEntryDao().deleteAll();db.shopDao().deleteAll()
  backup.importFile(file)
  assertEquals(2,db.shopBranchDao().getAll().size)
  assertEquals(far,db.shoppingListEntryDao().getById(id)!!.assignedBranchId)
  assertEquals(far,basket.resolveBranch(pasta,yar))
  assertEquals(setOf("BY_GROUPS_group_1"),app.getSharedPreferences("product_db_prefs",0).getStringSet("collapsed_sections",emptySet()))
 }

 @Test fun collapsedGroupIdentityPersistsIndependentlyByViewAndStableId() {
  val first=GroupExpansion(prefs,"collapsed")
  first.toggle("GROUP_1");first.toggle("SHOP_1")
  val second=GroupExpansion(prefs,"collapsed")
  assertEquals(setOf("GROUP_1","SHOP_1"),second.collapsed)
  second.toggle("GROUP_1")
  assertEquals(setOf("SHOP_1"),GroupExpansion(prefs,"collapsed").collapsed)
 }

 private fun rewriteManifest(source:java.io.File,change:(JSONObject)->Unit):java.io.File {
  val target=java.io.File(app.cacheDir,"rewrite-${UUID.randomUUID()}.zip")
  ZipFile(source).use{input->ZipOutputStream(target.outputStream()).use{output->
   input.entries().asSequence().forEach{entry->output.putNextEntry(ZipEntry(entry.name));val bytes=input.getInputStream(entry).readBytes();if(entry.name=="manifest.json"){val root=JSONObject(bytes.toString(Charsets.UTF_8));change(root);output.write(root.toString().toByteArray())}else output.write(bytes);output.closeEntry()}
  }}
  return target
 }

 @Test fun olderFullZipFourGetsOnePointFromEachOldShopAddress()=runBlocking {
  val id=basket.addProduct(pasta,1.0)
  val backup=BackupManager(app,db)
  val old=rewriteManifest(backup.exportFile()){root->
   root.put("version",4);val data=root.getJSONObject("data")
   data.remove("shopBranches");data.remove("productBranchAvailability")
   val shops=data.getJSONArray("shops")
   for(i in 0 until shops.length())if(shops.getJSONObject(i).getLong("id")==yar)shops.getJSONObject(i).put("address","Старый адрес")
   val links=data.getJSONArray("productShopLinks");for(i in 0 until links.length())links.getJSONObject(i).remove("restrictBranches")
   val rows=data.getJSONArray("shoppingListEntries");for(i in 0 until rows.length())rows.getJSONObject(i).remove("assignedBranchId")
  }
  backup.importFile(old)
  val branch=db.shopBranchDao().forShop(yar).single()
  assertEquals("Старый адрес",branch.address)
  assertEquals(branch.id,db.shoppingListEntryDao().getById(id)!!.assignedBranchId)
  assertEquals(1.0,db.shoppingListEntryDao().getById(id)!!.quantity,0.0)
 }

 @Test fun malformedBranchInFullBackupCannotReplaceTheCurrentBasket()=runBlocking {
  val foreign=db.shopBranchDao().insert(ShopBranchEntity(shopId=baton,address="Другой магазин"))
  basket.addProduct(pasta,1.0)
  val before=db.shoppingListEntryDao().getAllSync()
  val backup=BackupManager(app,db)
  val invalid=rewriteManifest(backup.exportFile()){root->root.getJSONObject("data").getJSONArray("shoppingListEntries").getJSONObject(0).put("assignedBranchId",foreign)}
  try{backup.importFile(invalid);fail("Cross-shop point must be rejected")}catch(expected:IllegalArgumentException){}
  assertEquals(before,db.shoppingListEntryDao().getAllSync())
  assertEquals(far,basket.resolveBranch(pasta,yar))
 }

 @Test fun oldUnsavedBulkDraftIsKeptAlongsideExistingPendingPurchases() {
  runBlocking{basket.addProduct(apple,2.0)}
  prefs.edit().putString("bulk_text","Спагетти 3 уп").commit()
  val vm=model("legacy-draft")
  assertTrue(vm.uiState.value.text.contains("Яблоки Гала — 2 кг"))
  assertTrue(vm.uiState.value.text.contains("Спагетти 3 уп"))
  assertTrue(vm.uiState.value.dirty)
  assertFalse(prefs.contains("bulk_text"))
  assertTrue(prefs.contains("text_view_draft"))
  vm.review();await{vm.uiState.value.review&&!vm.uiState.value.busy}
  vm.add();await{vm.uiState.value.saved&&!vm.uiState.value.busy}
  assertEquals(2,runBlocking{db.shoppingListEntryDao().getAllSync()}.size)
 }

 @Test fun editingTextShopAndUnitRetainsPurchaseIdentityNotesAndUrgency() {
  val id=runBlocking{db.shoppingListEntryDao().insert(ShoppingListEntryEntity(productId=apple,quantity=2.0,unit="кг",assignedShopId=baton,note="Сохранить заметку",comment="Сохранить комментарий",isUrgent=true,createdAt=123))}
  val vm=model("changed-target")
  vm.text("Красный Яр · Ленина, 20:\nЯблоки Гала — 4 шт")
  vm.review();await{vm.uiState.value.review&&!vm.uiState.value.busy}
  assertEquals(id,vm.uiState.value.rows.single().entryId)
  vm.add();await{vm.uiState.value.saved&&!vm.uiState.value.busy}
  val row=runBlocking{db.shoppingListEntryDao().getById(id)}!!
  assertEquals(yar,row.assignedShopId);assertEquals(far,row.assignedBranchId);assertEquals("шт",row.unit)
  assertEquals("Сохранить заметку",row.note);assertEquals("Сохранить комментарий",row.comment);assertTrue(row.isUrgent)
  assertEquals(123L,row.createdAt);assertEquals(1,runBlocking{db.shoppingListEntryDao().getAllSync()}.size)
 }

 @Test fun newPermanentTextProductKeepsTheStoreForFutureAutomaticPurchases() {
  val vm=model("new-permanent");vm.text("Красный Яр:\nНовый товар — 2 шт")
  vm.review();await{vm.uiState.value.review&&!vm.uiState.value.busy}
  vm.update(0){it.copy(permanent=true)};vm.add();await{vm.uiState.value.saved&&!vm.uiState.value.busy}
  runBlocking {
   val product=db.productDao().getAllSync().single{it.name=="Новый товар"}
   assertFalse(product.isTemporary)
   assertEquals(yar,db.productShopLinkDao().getLinksForProductSync(product.id).single().shopId)
   val row=db.shoppingListEntryDao().getById(basket.addProduct(product.id,1.0))!!
   assertEquals(yar,row.assignedShopId);assertEquals(near,row.assignedBranchId)
  }
 }

 @Test fun textTransactionRollsBackEarlierEditsAndNewProductsWhenALaterRowFails()=runBlocking {
  val id=basket.addProduct(apple,2.0)
  val base=basket.textSnapshot().entries
  val beforeProducts=db.productDao().getAllSync()
  val requests=listOf(
   ShoppingListRepository.PurchaseRequest(apple,"Яблоки Гала",3.0,"кг",choice=ShopChoice.EXPLICIT,shopId=yar,entryId=id,branchId=far),
   ShoppingListRepository.PurchaseRequest(null,"Не должен остаться",-1.0,"шт",true,ShopChoice.EXPLICIT,yar)
  )
  try{basket.replacePendingText(base,requests);fail("Bad second row must roll back all edits")}catch(expected:IllegalArgumentException){}
  assertEquals(base,basket.textSnapshot().entries)
  assertEquals(beforeProducts,db.productDao().getAllSync())
 }

 @Test fun placeChoicesIntersectCatalogueAvailabilityAndExcludeOtherCitiesAndUnlinkedShops()=runBlocking {
  val a=basket.addProduct(apple,choice=ShopChoice.EXPLICIT,shopId=baton)
  val p=basket.addProduct(pasta,choice=ShopChoice.EXPLICIT,shopId=baton)
  assertEquals(listOf(yar to far),basket.purchasePlaces(setOf(a,p)).map{it.shopId to it.branchId})
  val otherCity=db.cityDao().insert(CityEntity(name="Иркутск"))
  val otherShop=db.shopDao().insert(ShopEntity(name="Красный Яр",cityId=otherCity))
  db.productShopLinkDao().insert(ProductShopLinkEntity(productId=apple,shopId=otherShop))
  assertEquals(setOf(near,far),basket.purchasePlaces(setOf(a)).mapNotNull{it.branchId}.toSet())
  val before=db.shoppingListEntryDao().getAllSync()
  try{basket.movePending(setOf(a,p),baton);fail("An unlinked destination is not product availability")}catch(expected:IllegalArgumentException){}
  assertEquals(before,db.shoppingListEntryDao().getAllSync())
  products.ensureShopLink(apple,baton)
  assertTrue(basket.purchasePlaces(setOf(a)).any{it.shopId==baton&&it.branchId==null})
  assertFalse(basket.purchasePlaces(setOf(a,p)).any{it.shopId==baton})
 }
 @Test fun copyingTheCatalogueLinkIsIdempotentAndNeverTouchesPurchasesOrProductMetadata()=runBlocking {
  val entry=basket.addProduct(pasta)
  val product=db.productDao().getById(pasta)!!
  val before=db.shoppingListEntryDao().getById(entry)!!
  val originalLink=db.productShopLinkDao().getLinksForProductSync(pasta).single()
  products.ensureShopLink(pasta,baton);products.ensureShopLink(pasta,baton)
  assertEquals(product,db.productDao().getById(pasta));assertEquals(before,db.shoppingListEntryDao().getById(entry))
  val links=db.productShopLinkDao().getLinksForProductSync(pasta)
  assertEquals(2,links.size);assertEquals(originalLink,links.find{it.shopId==yar})
  assertEquals(listOf(ProductBranchAvailabilityEntity(pasta,far,yar)),db.shopBranchDao().availability(pasta))
 }
 @Test fun stalePlaceSelectionIsRecheckedAndRollsBackTheWholeMove()=runBlocking {
  val a=basket.addProduct(apple,choice=ShopChoice.EXPLICIT,shopId=baton)
  val p=basket.addProduct(pasta,choice=ShopChoice.EXPLICIT,shopId=baton)
  assertEquals(listOf(far),basket.purchasePlaces(setOf(a,p)).map{it.branchId})
  products.toggleShopLink(pasta,yar)
  val before=db.shoppingListEntryDao().getAllSync()
  try{basket.movePending(setOf(a,p),yar,far);fail("Stale availability must not be accepted")}catch(expected:IllegalArgumentException){}
  assertEquals(before,db.shoppingListEntryDao().getAllSync())
 }
 @Test fun clearingBoughtInOnePlacePreservesTheOtherAddressAndPendingEntries()=runBlocking {
  val boughtNear=basket.addProduct(apple,1.0,branchId=near).also{basket.markBought(it)}
  val boughtFar=basket.addProduct(apple,2.0,branchId=far).also{basket.markBought(it)}
  val pending=basket.addProduct(apple,3.0,branchId=near)
  basket.clearBoughtInPlace(yar,near)
  assertNull(db.shoppingListEntryDao().getById(boughtNear));assertNotNull(db.shoppingListEntryDao().getById(boughtFar));assertNotNull(db.shoppingListEntryDao().getById(pending))
 }
 @Test fun editingPurchaseFactsCannotChangeItsPlaceOrIdentity()=runBlocking {
  val id=basket.addProduct(apple,2.0,choice=ShopChoice.EXPLICIT,shopId=baton)
  basket.movePending(setOf(id),yar,far);basket.markBought(id)
  val before=db.shoppingListEntryDao().getById(id)!!
  basket.updatePurchaseDetails(id,3.0," заметка "," комментарий ")
  val actual=db.shoppingListEntryDao().getById(id)!!
  assertEquals(before.copy(quantity=3.0,note="заметка",comment="комментарий",updatedAt=actual.updatedAt),actual)
 }
 @Test fun explicitPointReorderingPreservesPurchaseAssignmentsAndOverridesUrgencyBoost()=runBlocking {
  val id=basket.addProduct(apple,branchId=near);basket.toggleUrgent(id)
  val before=db.shoppingListEntryDao().getById(id)
  shops.movePurchasePlace(yar,near,yar,far)
  assertEquals(listOf(far,near),db.shopBranchDao().forShop(yar).map{it.id})
  assertTrue(db.shopDao().getById(yar)!!.manualOrderOverride)
  assertEquals(before,db.shoppingListEntryDao().getById(id))
 }
 @Test fun equalPriorityRetailersKeepTheirAlphabeticNeighboursDuringExplicitMove()=runBlocking {
  val alley=db.shopDao().insert(ShopEntity(name="Аллея"))
  assertEquals(listOf(alley,baton,yar),db.shopDao().getAllSync().map{it.id})
  shops.movePurchasePlace(yar,far,baton,null)
  assertEquals(listOf(alley,yar,baton),db.shopDao().getAllSync().map{it.id})
 }
}
