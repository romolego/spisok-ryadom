package com.spisokryadom.app

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.backup.BackupManager
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.ui.productcard.*
import com.spisokryadom.app.util.ProductEditingPreferences
import kotlinx.coroutines.*
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
class ProductSessions270DataTest {
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 private lateinit var db:AppDatabase;private lateinit var locations:LocationRepository
 private lateinit var products:ProductRepository;private lateinit var shops:ShopRepository;private lateinit var classifiers:ClassifierRepository
 private val store=ViewModelStore()
 private val prefs get()=app.getSharedPreferences("270-product-settings",0)
 private val drafts get()=app.getSharedPreferences("270-product-drafts",0)
 private var product=0L
 private fun await(c:()->Boolean){val end=System.nanoTime()+20_000_000_000;while(!c()&&System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(25));Thread.sleep(5)};assertTrue("Operation did not finish",c())}
 @Before fun setup():Unit=runBlocking{
  prefs.edit().clear().putBoolean(ProductEditingPreferences.KEY,false).commit();drafts.edit().clear().commit()
  db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build();db.cityDao().insert(CityEntity(1,"Город"))
  locations=LocationRepository(db.cityDao(),db.shopDao(),app.getSharedPreferences("270-product-location",0))
  products=ProductRepository(db.productDao(),db.productShopLinkDao(),db);shops=ShopRepository(db.shopDao(),db.shopDepartmentDao(),locations,db);classifiers=ClassifierRepository(db.productGroupDao(),db.recipientDao(),db.unitDao(),db)
  product=db.productDao().insert(ProductEntity(name="Сохранённый товар"))
 }
 @After fun close(){store.clear();await{!ProductEditorTransactions.mutex.isLocked};locations.closeForTest();db.close()}
 private fun card(id:Long=product)=ProductCardViewModel(id,-1,products,shops,classifiers,drafts,prefs).also{store.put("card",it);await{!it.uiState.value.isLoading};Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(75))}
 private suspend fun restore()=coroutineScope{
  // The paused Android main looper must keep resuming an in-flight autosave while
  // backup waits for its editor lock; blocking runBlocking alone does not pump it.
  val task=async(Dispatchers.IO){val manager=BackupManager(app,db);val file=manager.exportFile();try{manager.importFile(file)}finally{manager.discard(file)}}
  await{task.isCompleted};task.await()
 }
 private fun restoredRaw(v:ProductCardViewModel,stable:String){drafts.edit().clear().putString("restored",ProductEditDraft(stable,-1,true,false,v.uiState.value).encode()).commit()}

 @Test fun staleStoreCreationRejectsTheWriteAndStillCompletesTheInlineRequest():Unit=runBlocking{
  val v=card();restore();val before=db.shopDao().getAllSync();var success=false;var finished=false
  v.createShop("Новый магазин",{success=true},{finished=true});await{finished&&v.uiState.value.error!=null}
  assertFalse(success);assertEquals(before,db.shopDao().getAllSync());assertFalse(v.uiState.value.isSaving)
 }
 @Test fun staleRecipientCreationCannotTouchRestoredClassifiersAndAlwaysFinishes():Unit=runBlocking{
  val v=card();restore();val before=db.recipientDao().getAllSync();var success=false;var finished=false
  v.createRecipient("Злата",{success=true},{finished=true});await{finished&&v.uiState.value.error!=null}
  assertFalse(success);assertEquals(before,db.recipientDao().getAllSync());assertNull(v.uiState.value.recipientId)
 }
 @Test fun staleGroupCreationDoesNotCreateOrSelectAClassifier():Unit=runBlocking{
  val v=card();restore();val before=db.productGroupDao().getAllSync();v.createGroup("Новая группа");await{v.uiState.value.error!=null}
  assertEquals(before,db.productGroupDao().getAllSync());assertNull(v.uiState.value.productGroupId)
 }
 @Test fun staleDeleteCannotDeleteMatchingStableProductOrRestoredRawDraft():Unit=runBlocking{
  val v=card();restore();val before=db.productDao().getById(product)!!;restoredRaw(v,before.stableId);val raw=drafts.all
  v.deleteProduct();await{v.uiState.value.error!=null};assertEquals(before,db.productDao().getById(product));assertEquals(raw,drafts.all);assertFalse(v.uiState.value.isDeleted)
 }
 @Test fun staleAdoptionCannotRemoveAutoCreatedProductOrChangeTargetLinks():Unit=runBlocking{
  prefs.edit().putBoolean(ProductEditingPreferences.KEY,true).commit();val v=card(-1);v.updateName("Автотовар")
  await{v.uiState.value.savedProductId!=null&&!ProductEditorTransactions.mutex.isLocked}
  val own=db.productDao().getAllSync().single{it.name=="Автотовар"};restore();restoredRaw(v,own.stableId)
  val before=db.productDao().getAllSync();val raw=drafts.all
  v.useExistingProduct(product);await{v.uiState.value.error!=null}
  assertEquals(before,db.productDao().getAllSync());assertTrue(db.productShopLinkDao().getAllSync().isEmpty());assertEquals(raw,drafts.all);assertNull(v.uiState.value.usedExistingId)
 }
 @Test fun staleBlankBackLeavesRestoredProductAndDraftIntact():Unit=runBlocking{
  prefs.edit().putBoolean(ProductEditingPreferences.KEY,true).commit();val v=card(-1);v.updateName("Автотовар")
  await{v.uiState.value.savedProductId!=null&&!ProductEditorTransactions.mutex.isLocked}
  val own=db.productDao().getAllSync().single{it.name=="Автотовар"};restore();restoredRaw(v,own.stableId);val raw=drafts.all
  v.updateName("");v.finishEditing();await{v.uiState.value.isSaved}
  assertEquals(own,db.productDao().getById(own.id));assertEquals(raw,drafts.all)
 }
 @Test fun staleManualSaveFailsButBackCanLeaveWithoutErasingRestoredDraft():Unit=runBlocking{
  val v=card();restore();val before=db.productDao().getById(product)!!;restoredRaw(v,before.stableId);val raw=drafts.all
  v.updateName("Старые правки");v.save();await{v.uiState.value.error!=null&&!v.uiState.value.isSaving};assertFalse(v.uiState.value.isSaved)
  v.finishEditing();await{v.uiState.value.isSaved};assertEquals(before,db.productDao().getById(product));assertEquals(raw,drafts.all)
 }
 @Test fun ordinaryFailedCreationReleasesBusyWithoutAFalseSuccessOrPartialWrite():Unit=runBlocking{
  val v=card();db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_new_shop BEFORE INSERT ON shops BEGIN SELECT RAISE(ABORT, 'Synthetic rejection'); END")
  var success=false;var finished=false;v.createShop("Проверка отказа",{success=true},{finished=true});await{finished&&v.uiState.value.error!=null}
  assertFalse(success);assertTrue(db.shopDao().getAllSync().isEmpty());assertFalse(v.uiState.value.isSaving)
 }
}
