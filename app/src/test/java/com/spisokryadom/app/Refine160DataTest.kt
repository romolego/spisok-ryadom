package com.spisokryadom.app

import android.app.Application
import android.app.Activity
import android.content.Intent
import android.os.Looper
import android.speech.RecognizerIntent
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.*
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.data.backup.BackupManager
import com.spisokryadom.app.ui.productcard.*
import com.spisokryadom.app.ui.productdb.*
import com.spisokryadom.app.ui.components.*
import com.spisokryadom.app.util.ProductEditingPreferences
import com.spisokryadom.app.navigation.Routes
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
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
class Refine160DataTest {
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 private val drafts get()=ProductDraftStore.preferences(app)
 private val prefs get()=ProductEditingPreferences.preferences(app)
 private lateinit var db:AppDatabase
 private lateinit var locations:LocationRepository
 private lateinit var products:ProductRepository
 private lateinit var shops:ShopRepository
 private lateinit var classifiers:ClassifierRepository
 private val store=ViewModelStore();private var serial=0
 private var detail:()->String={""}
 private fun await(c:()->Boolean){val end=System.nanoTime()+20_000_000_000;while(!c()&&System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(25));Thread.sleep(5)};assertTrue("Timed out: ${detail()}",c())}
 private fun settle(){repeat(50){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(25));Thread.sleep(5)}}
 private fun card(id:Long=-1,seed:String="")=ProductCardViewModel(id,productRepository=products,shopRepository=shops,classifierRepository=classifiers,draftPreferences=drafts,editingPreferences=prefs,initialName=seed).also{vm->store.put("card${serial++}",vm);detail={vm.uiState.value.toString()};await{!vm.uiState.value.isLoading}}
 private fun clear(){store.clear();await{!ProductEditorTransactions.mutex.isLocked};settle()}
 private fun recover(){val j=CoroutineScope(Dispatchers.IO).async{ProductDraftStore.recover(app,products,shops)};await{j.isCompleted};runBlocking{j.await()}}
 @Before fun setup():Unit=runBlocking{
  ProductEditorTransactions.generation.incrementAndGet();drafts.edit().clear().commit();prefs.edit().clear().commit()
  db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build();RetailConstraints.install(db.openHelper.writableDatabase)
  db.cityDao().insert(CityEntity(1,"Тестовый город"));locations=LocationRepository(db.cityDao(),db.shopDao(),app.getSharedPreferences("160-location",0))
  products=ProductRepository(db.productDao(),db.productShopLinkDao(),db);shops=ShopRepository(db.shopDao(),db.shopDepartmentDao(),locations,db);classifiers=ClassifierRepository(db.productGroupDao(),db.recipientDao(),db.unitDao(),db)
 }
 @After fun close(){clear();locations.closeForTest();db.close()}
 @Test fun manualNewCardBackDiscardsAndDoesNotRecover(){
  prefs.edit().putBoolean(ProductEditingPreferences.KEY,false).commit();val vm=card();vm.updateName("Яблоко с опечаткой");vm.updateNote("Не сохранять");settle()
  assertTrue(runBlocking{db.productDao().getAllSync().isEmpty()});assertTrue(drafts.all.isNotEmpty())
  vm.finishEditing();await{vm.uiState.value.isSaved};assertTrue(drafts.all.isEmpty());clear();recover();assertTrue(runBlocking{db.productDao().getAllSync().isEmpty()})
 }
 @Test fun manualExistingBackDiscardsButExplicitSaveWritesAllFields():Unit=runBlocking{
  val id=db.productDao().insert(ProductEntity(name="Сок",note="Прежняя заметка"));prefs.edit().putBoolean(ProductEditingPreferences.KEY,false).commit()
  val vm=card(id);vm.updateName("Сок без сахара");vm.updateNote("Несохранённое");settle();assertEquals("Сок",db.productDao().getById(id)!!.name)
  vm.finishEditing();await{vm.uiState.value.isSaved};assertEquals("Прежняя заметка",db.productDao().getById(id)!!.note)
  val next=card(id);next.updateName("Сок без сахара");next.updateNote("Сохранённое");next.save();await{next.uiState.value.isSaved}
  assertEquals("Сок без сахара",db.productDao().getById(id)!!.name);assertEquals("Сохранённое",db.productDao().getById(id)!!.note);assertTrue(drafts.all.isEmpty())
 }
 @Test fun manualSaveCreatesOnceAndKeepsLinks():Unit=runBlocking{
  prefs.edit().putBoolean(ProductEditingPreferences.KEY,false).commit();val shop=db.shopDao().insert(ShopEntity(name="Батон"));val vm=card();await{vm.uiState.value.shops.any{it.id==shop}}
  vm.updateName("Газировка");vm.updateDefaultQuantity("2");vm.updateDefaultUnit("бутылка");vm.addPendingShopLink(shop,null);vm.save();await{vm.uiState.value.isSaved};settle()
  val p=db.productDao().getAllSync().single();assertEquals(2.0,p.defaultQuantity!!,0.0);assertEquals(shop,db.productShopLinkDao().getLinksForProductSync(p.id).single().shopId);assertTrue(drafts.all.isEmpty())
 }
 @Test fun manualDirectNavigationAndStartupDoNotResurrectRawCard(){
  prefs.edit().putBoolean(ProductEditingPreferences.KEY,false).commit();val vm=card();vm.updateName("Не сохранять при удалении VM");settle();clear();assertTrue(drafts.all.isEmpty());recover();assertTrue(runBlocking{db.productDao().getAllSync().isEmpty()})
 }
 @Test fun startupSkipsManualRawEvenWhenAutoSaveWasReenabled(){
  ProductDraftStore.write(drafts,"new_-1",ProductEditDraft("manual",-1,true,false,ProductCardUiState(name="Нельзя восстановить",isLoading=false),autoPersist=false))
  recover();assertTrue(drafts.all.isEmpty());assertTrue(runBlocking{db.productDao().getAllSync().isEmpty()})
 }
 @Test fun disabledAutosaveDoesNotRecoverOldEnabledDraft(){
  ProductDraftStore.write(drafts,"new_-1",ProductEditDraft("old",-1,true,false,ProductCardUiState(name="Старый черновик",isLoading=false)))
  prefs.edit().putBoolean(ProductEditingPreferences.KEY,false).commit();recover();assertTrue(drafts.all.isEmpty());assertTrue(runBlocking{db.productDao().getAllSync().isEmpty()})
 }
 @Test fun livePreferenceDisablesQueuedCommit(){
  val vm=card();vm.updateName("Не успело сохраниться");prefs.edit().putBoolean(ProductEditingPreferences.KEY,false).commit();settle();assertTrue(runBlocking{db.productDao().getAllSync().isEmpty()});vm.finishEditing();await{vm.uiState.value.isSaved};assertTrue(drafts.all.isEmpty())
 }
 @Test fun clearedNewNameWithdrawsOldRowAndBackRetainsOnlyUnnamedRaw(){
  val vm=card();vm.updateName("Яьооко");vm.updateNote("Оставшаяся заметка");await{vm.uiState.value.savedProductId!=null};vm.updateName("");await{runBlocking{db.productDao().getAllSync().isEmpty()}}
  assertEquals("",vm.uiState.value.name);assertFalse(vm.uiState.value.isDeleted);vm.finishEditing();await{vm.uiState.value.isSaved};clear();recover()
  assertTrue(runBlocking{db.productDao().getAllSync().isEmpty()});val resumed=card();assertEquals("",resumed.uiState.value.name);assertEquals("Оставшаяся заметка",resumed.uiState.value.note)
  resumed.updateName("Яблоко");resumed.save();await{resumed.uiState.value.isSaved};assertEquals("Яблоко",runBlocking{db.productDao().getAllSync().single().name})
 }
 @Test fun clearingNameAndImmediatelyGoingBackRemovesAutoCreatedRow(){
  val vm=card();vm.updateName("Опечатка");await{vm.uiState.value.savedProductId!=null};vm.updateName("");vm.finishEditing();await{vm.uiState.value.isSaved};assertTrue(runBlocking{db.productDao().getAllSync().isEmpty()});assertNull(vm.uiState.value.savedProductId)
 }
 @Test fun clearingNameAndImmediatelyClearingVmRemovesAutoCreatedRow(){
  val vm=card();vm.updateName("Опечатка");await{vm.uiState.value.savedProductId!=null};vm.updateName("");clear();recover();assertTrue(runBlocking{db.productDao().getAllSync().isEmpty()})
 }
 @Test fun existingBlankNameDoesNotSilentlyCloseOrDeletePurchases():Unit=runBlocking{
  val id=db.productDao().insert(ProductEntity(name="Яблоко"));val row=db.shoppingListEntryDao().insert(ShoppingListEntryEntity(productId=id,quantity=2.0,unit="шт"));val vm=card(id);vm.updateName("");vm.finishEditing();await{vm.uiState.value.error!=null}
  assertFalse(vm.uiState.value.isSaved);assertEquals("",vm.uiState.value.name);assertEquals("Введите название товара",vm.uiState.value.error);assertNotNull(db.shoppingListEntryDao().getById(row));assertEquals("Яблоко",db.productDao().getById(id)!!.name)
  vm.clearError();vm.updateName("Яблоко зелёное");vm.finishEditing();await{vm.uiState.value.isSaved};assertEquals("Яблоко зелёное",db.productDao().getById(id)!!.name)
 }
 @Test fun newBlankNameCannotDeleteItsAlreadyUsedProduct():Unit=runBlocking{
  val vm=card();vm.updateName("Сок");await{vm.uiState.value.savedProductId!=null};val id=vm.uiState.value.savedProductId!!;val row=db.shoppingListEntryDao().insert(ShoppingListEntryEntity(productId=id,quantity=1.0,unit="л"))
  vm.updateName("");vm.finishEditing();await{vm.uiState.value.error!=null};assertFalse(vm.uiState.value.isSaved);assertNotNull(db.productDao().getById(id));assertNotNull(db.shoppingListEntryDao().getById(row))
 }
 @Test fun searchSeedIsIndependentOfUnrelatedUnfinishedNewCard(){
  val old=card();old.updateName("Первый черновик");old.updateNote("Не перезаписывать");await{old.uiState.value.savedProductId!=null};clear()
  val seeded=card(seed=" Говяжье лёгкое ");assertEquals("Говяжье лёгкое",seeded.uiState.value.name);seeded.save();await{seeded.uiState.value.isSaved};clear()
  val resumed=card();assertEquals("Первый черновик",resumed.uiState.value.name);assertEquals("Не перезаписывать",resumed.uiState.value.note)
 }
 @Test fun seededRouteEncodesReservedCharactersWithoutChangingTitle(){
  val name="Чай & кофе / №1? 100% + #";val uri=android.net.Uri.parse(Routes.ProductCard.createRoute(initialName=name));assertEquals(name,uri.getQueryParameter("initialName"));assertNull(uri.fragment)
 }
 @Test fun searchExactnessIgnoresFavoritesFilterAndFuzzyMatches():Unit=runBlocking{
  db.productDao().insert(ProductEntity(name="Жидкость для линз"));val basket=ShoppingListRepository(db.shoppingListEntryDao(),db,locations)
  val vm=ProductDatabaseViewModel(products,basket,shops,classifiers,app.getSharedPreferences("160-catalogue",0));store.put("search",vm);val job=CoroutineScope(Dispatchers.Main).launch{vm.uiState.collect{}}
  try{vm.setSearchQuery("Пинцет для линз");await{vm.uiState.value.searchedQuery=="Пинцет для линз"};assertFalse(vm.uiState.value.exactNameExists)
   vm.toggleFavorites();vm.setSearchQuery("жидкость для линз");await{vm.uiState.value.searchedQuery=="жидкость для линз"&&vm.uiState.value.favoritesOnly};assertTrue(vm.uiState.value.exactNameExists);assertTrue(vm.uiState.value.products.isEmpty())
  }finally{job.cancel()}
 }
 @Test fun autosavePreferenceRoundTripsThroughFullBackup():Unit=runBlocking{
  prefs.edit().putBoolean(ProductEditingPreferences.KEY,false).commit();val manager=BackupManager(app,db);val file=manager.exportFile()
  try{prefs.edit().putBoolean(ProductEditingPreferences.KEY,true).commit();manager.importFile(file);assertFalse(ProductEditingPreferences.enabled(prefs))}finally{manager.discard(file)}
 }
 @Test fun voiceIntentAndResultAreSearchOnlyAndCancellationKeepsQuery(){
  val intent=voiceSearchIntent();assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH,intent.action);assertEquals(RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL))
  val result=Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS,arrayListOf("  Банан ","Бананы"));assertEquals("Банан",voiceSearchResult(Activity.RESULT_OK,result));assertNull(voiceSearchResult(Activity.RESULT_CANCELED,result));assertNull(voiceSearchResult(Activity.RESULT_OK,null));assertNull(voiceSearchResult(Activity.RESULT_OK,Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS,arrayListOf(" "))))
  assertTrue(runBlocking{db.productDao().getAllSync().isEmpty()});assertTrue(runBlocking{db.shoppingListEntryDao().getAllSync().isEmpty()})
 }
}
