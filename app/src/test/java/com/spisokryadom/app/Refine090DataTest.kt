package com.spisokryadom.app

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.*
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.data.backup.BackupManager
import com.spisokryadom.app.ui.productcard.*
import com.spisokryadom.app.ui.productdb.*
import com.spisokryadom.app.ui.shoplist.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.*
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class Refine090DataTest {
 private val context get()=ApplicationProvider.getApplicationContext<Application>()
 private lateinit var db:AppDatabase;private lateinit var products:ProductRepository;private lateinit var shops:ShopRepository;private lateinit var classifiers:ClassifierRepository;private lateinit var locations:LocationRepository
 private val store=ViewModelStore();private var sequence=0
 private val prefs get()=ProductDraftStore.preferences(context)
 private var diagnostic:()->String={""}
 private suspend fun await(condition:()->Boolean){val end=System.nanoTime()+20_000_000_000;while(!condition()&&System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20));yield();Thread.sleep(5)};assertTrue("Editor did not finish: ${diagnostic()}",condition())}
 private suspend fun card(id:Long = -1):ProductCardViewModel=ProductCardViewModel(id,productRepository=products,shopRepository=shops,classifierRepository=classifiers,draftPreferences=prefs).also{store.put("card${sequence++}",it);diagnostic={it.uiState.value.toString()};await{!it.uiState.value.isLoading}}
 private suspend fun clearEditors(){store.clear();await{!ProductEditorTransactions.mutex.isLocked}}
 private suspend fun recoverDrafts(){val job=CoroutineScope(Dispatchers.IO).async{ProductDraftStore.recover(context,products,shops)};await{job.isCompleted};job.await()}
 @Before fun setup()=runBlocking{
  ProductEditorTransactions.generation.incrementAndGet();prefs.edit().clear().commit();db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).allowMainThreadQueries().build();RetailConstraints.install(db.openHelper.writableDatabase)
  db.cityDao().insert(CityEntity(1,"Красноярск"));locations=LocationRepository(db.cityDao(),db.shopDao(),context.getSharedPreferences("090locations",0))
  products=ProductRepository(db.productDao(),db.productShopLinkDao(),db);shops=ShopRepository(db.shopDao(),db.shopDepartmentDao(),locations,db);classifiers=ClassifierRepository(db.productGroupDao(),db.recipientDao(),db.unitDao(),db);Unit
 }
 @After fun close()=runBlocking{clearEditors();locations.closeForTest();db.close()}
 @Test fun autosaveUpdatesOneStableRowWithoutPressingSave()=runBlocking{
  val vm=card();vm.updateName("Газировка");vm.updateNote("Сначала");vm.updateDefaultQuantity("2");vm.updateDefaultUnit("бутылка")
  await{runBlocking{db.productDao().getAllSync().singleOrNull()?.note=="Сначала"}}
  val first=db.productDao().getAllSync().single();vm.updateName("Газировка без сахара");vm.updateNote("После правки");vm.favorite(true)
  await{runBlocking{db.productDao().getById(first.id)?.note=="После правки"}}
  val after=db.productDao().getAllSync().single();assertEquals(first.id,after.id);assertEquals(first.stableId,after.stableId);assertTrue(after.isFavorite);assertEquals(2.0,after.defaultQuantity!!,0.0);assertFalse(vm.uiState.value.isSaved)
 }
 @Test fun incompleteNumbersAndNotesSurviveNewViewModelAndRecoverWithoutDuplicates()=runBlocking{
  val vm=card();vm.updateName("Газировка");await{runBlocking{db.productDao().getAllSync().size==1}}
  vm.updateDefaultQuantity("0,");vm.updateNote("Не терять незавершённое")
  await{prefs.getString("new_-1","")!!.contains("Не терять")};clearEditors()
  val resumed=card();assertEquals("0,",resumed.uiState.value.defaultQuantity);assertEquals("Не терять незавершённое",resumed.uiState.value.note)
  resumed.updateDefaultQuantity("3");await{runBlocking{db.productDao().getAllSync().single().defaultQuantity==3.0}};clearEditors()
  recoverDrafts();recoverDrafts()
  assertEquals(1,db.productDao().getAllSync().size);assertEquals("Не терять незавершённое",db.productDao().getAllSync().single().note)
 }
 @Test fun immediateBackFlushesLatestFieldsAndClearsCompletedDraft()=runBlocking{
  val vm=card();vm.updateName("Сок");vm.updateNote("Последний символ");vm.updateDefaultUnit("л");vm.finishEditing()
  await{vm.uiState.value.isSaved};val p=db.productDao().getAllSync().single();assertEquals("Последний символ",p.note);assertEquals("л",p.defaultUnit);assertTrue(prefs.all.isEmpty())
 }
 @Test fun explicitDeleteDoesNotGetUndoneByQueuedAutosaveOrStartupRecovery()=runBlocking{
  val vm=card();vm.updateName("Удалить");await{vm.uiState.value.savedProductId!=null};vm.updateNote("Ещё ожидает записи");vm.deleteProduct();await{vm.uiState.value.isDeleted}
  Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000));recoverDrafts()
  assertTrue(db.productDao().getAllSync().isEmpty());assertTrue(prefs.all.isEmpty())
 }
 @Test fun exactDuplicateNeedsExplicitSeparateCreation()=runBlocking{
  db.productDao().insert(ProductEntity(name="Цукаты"));val vm=card();vm.updateName("Цукаты");await{vm.uiState.value.duplicateName}
  assertEquals(1,db.productDao().getAllSync().size);vm.save(true);await{vm.uiState.value.isSaved};assertEquals(2,db.productDao().getAllSync().size)
 }
 @Test fun recoveredDraftPreservesLocalDepartmentAndRestrictedPointAtomically()=runBlocking{
  val shop=db.shopDao().insert(ShopEntity(name="Батон"));val other=db.shopDao().insert(ShopEntity(name="Другой"));val point=db.shopBranchDao().insert(ShopBranchEntity(shopId=shop,address="Адрес"))
  val s=ProductCardUiState(name="Напиток",isLoading=false,pendingShopLinks=listOf(PendingShopLink(1,shop,"Батон",-2,"Напитки",1)),pendingDepartments=listOf(ShopDepartmentEntity(id=-2,shopId=shop,name="Напитки")),branchSelections=mapOf(shop to setOf(point)),branchDepartments=mapOf(point to -2))
  val draft=ProductEditDraft("draft-point-stable",-1,true,false,s);assertEquals(draft.encode(),ProductEditDraft.decode(draft.encode()).encode());prefs.edit().putString("new_-1",draft.encode()).commit()
  recoverDrafts();recoverDrafts()
  val p=db.productDao().getAllSync().single();val link=db.productShopLinkDao().getLinksForProductSync(p.id).single();assertTrue(link.restrictBranches);assertEquals("Напитки",db.shopDepartmentDao().getById(link.departmentId!!)!!.name);assertEquals(point,db.shopBranchDao().availability(p.id).single().branchId);assertTrue(db.shopDepartmentDao().getByShopIdSync(other).isEmpty());assertEquals(1,db.retailStructureDao().productDepartments().size)
 }
 @Test fun favoritesAreShortcutsAndOriginalRowsInEveryView()=runBlocking{
  val shop=db.shopDao().insert(ShopEntity(name="Батон"));val g=db.productGroupDao().insert(ProductGroupEntity(name="Продукты"));val rec=db.recipientDao().insert(RecipientEntity(name="Рома"))
  val f=products.saveWithLinks(ProductEntity(name="Любимое",isFavorite=true,productGroupId=g,recipientId=rec),listOf(ProductShopLinkEntity(productId=0,shopId=shop)));products.saveWithLinks(ProductEntity(name="Обычное",productGroupId=g,recipientId=rec),listOf(ProductShopLinkEntity(productId=0,shopId=shop)))
  val vm=ProductDatabaseViewModel(products,ShoppingListRepository(db.shoppingListEntryDao(),db,locations),shops,classifiers,context.getSharedPreferences("090catalog",0));store.put("catalogue",vm)
  val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined);val job=scope.launch{vm.uiState.collect{}}
  try{ProductViewMode.entries.forEach{mode->vm.setViewMode(mode);await{vm.uiState.value.viewMode==mode&&vm.uiState.value.products.size==2};val s=vm.uiState.value;assertEquals(1,s.sections.first{it.key=="favorites"}.products.size);assertTrue(s.sections.any{it.key!="favorites"&&it.products.any{p->p.id==f}});assertEquals(2,s.sections.flatMap{it.products}.count{it.id==f})}
   vm.toggleFavorites();await{vm.uiState.value.favoritesOnly&&vm.uiState.value.products.size==1};assertTrue(vm.uiState.value.sections.none{it.key=="favorites"})
  }finally{job.cancel();scope.cancel()}
 }
 @Test fun oneShopCanAppearInsideVenueAndOutsideWithoutHidingOtherShops()=runBlocking{
  val venue=db.retailStructureDao().insertVenue(VenueEntity(cityId=1,name="Рынок"));val shop=db.shopDao().insert(ShopEntity(name="Командор"));db.shopDao().insert(ShopEntity(name="Батон",displayOrder=1))
  db.shopBranchDao().insert(ShopBranchEntity(shopId=shop,address="Около дома"));db.shopBranchDao().insert(ShopBranchEntity(shopId=shop,address="В рынке",venueId=venue))
  val vm=ShopListViewModel(shops);diagnostic={vm.uiState.value.toString()};store.put("shops",vm);await{!vm.uiState.value.isLoading&&vm.uiState.value.groups.size==3}
  assertEquals(2,vm.uiState.value.shops.size);assertEquals("В рынке",vm.uiState.value.groups.single{it.venue!=null}.shops.single().address);assertEquals("Около дома",vm.uiState.value.groups.single{it.venue==null&&it.shops.single().id==shop}.shops.single().address)
 }
 @Test fun fullRestoreClearsStaleDraftOnlyAfterSuccessfulImport()=runBlocking{
  val manager=BackupManager(context,db);val good=manager.exportFile();prefs.edit().putString("new_-1","keep on failure").commit()
  val invalid=File(context.cacheDir,"invalid090.zip").also{it.writeText("invalid")};try{manager.importFile(invalid);fail()}catch(expected:Exception){}
  assertTrue(prefs.contains("new_-1"));manager.importFile(good);assertTrue(prefs.all.isEmpty());good.delete();invalid.delete();Unit
 }
 @Test fun deletedExistingProductCannotBeResurrectedFromItsEditorDraft()=runBlocking{
  val id=db.productDao().insert(ProductEntity(name="Существующий"));val vm=card(id);vm.updateNote("Сохранить");await{runBlocking{db.productDao().getById(id)?.note=="Сохранить"}}
  val raw=prefs.getString("product_$id",null)!!;clearEditors();db.productDao().delete(db.productDao().getById(id)!!);prefs.edit().putString("product_$id",raw).commit();recoverDrafts()
  assertTrue(db.productDao().getAllSync().isEmpty());assertTrue(prefs.contains("product_$id"))
 }
 @Test fun unfinishedNewDraftIsRecoveredFromItsCatalogueRowAndClearedOnDelete()=runBlocking{
  val shop=db.shopDao().insert(ShopEntity(name="Батон"));val fresh=card();await{fresh.uiState.value.shops.any{it.id==shop}}
  fresh.updateName("Сок");fresh.addPendingShopLink(shop,null);await{fresh.uiState.value.savedProductId!=null};val id=fresh.uiState.value.savedProductId!!
  fresh.updateDefaultQuantity("0,");fresh.updateNote("Незавершённое из новой карточки");fresh.finishEditing();await{fresh.uiState.value.isSaved};clearEditors()
  val resumed=card(id);await{resumed.uiState.value.note=="Незавершённое из новой карточки"};assertFalse(resumed.uiState.value.isNew);assertEquals("0,",resumed.uiState.value.defaultQuantity);assertEquals(shop,resumed.uiState.value.shopLinks.single().link.shopId)
  resumed.deleteProduct();await{resumed.uiState.value.isDeleted};assertTrue(prefs.all.isEmpty());recoverDrafts();assertTrue(db.productDao().getAllSync().isEmpty())
 }
 @Test fun draftPhotoSurvivesCleanupUntilDraftIsRemoved(){
  val photo=File(context.filesDir,"product_photos/draft-090.jpg");photo.parentFile!!.mkdirs();photo.writeBytes(byteArrayOf(1));photo.setLastModified(System.currentTimeMillis()-600_000)
  val draft=ProductEditDraft("photo-draft",-1,true,false,ProductCardUiState(photoUri=photo.absolutePath,isLoading=false))
  ProductDraftStore.write(prefs,"new_-1",draft);com.spisokryadom.app.util.PhotoStore.cleanup(context,emptySet());assertTrue(photo.isFile)
  ProductDraftStore.remove(prefs,"photo-draft");com.spisokryadom.app.util.PhotoStore.cleanup(context,emptySet());assertFalse(photo.exists())
 }
}
