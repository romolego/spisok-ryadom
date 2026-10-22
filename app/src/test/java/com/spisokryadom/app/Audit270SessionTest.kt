package com.spisokryadom.app

import android.app.Application
import android.net.Uri
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.backup.BackupManager
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.ui.batch.BatchViewModel
import com.spisokryadom.app.ui.settings.SettingsViewModel
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
class Audit270SessionTest {
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:AppDatabase
    private lateinit var locations:LocationRepository
    private lateinit var products:ProductRepository
    private lateinit var shops:ShopRepository
    private lateinit var basket:ShoppingListRepository
    private lateinit var classifiers:ClassifierRepository
    private lateinit var manager:BackupManager
    private val store=ViewModelStore()
    private val prefs get()=app.getSharedPreferences("shopping_list_prefs",0)
    @Before fun setup():Unit=runBlocking {
        prefs.edit().clear().commit();app.getSharedPreferences("product_card_drafts",0).edit().clear().commit()
        db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build()
        db.cityDao().insert(CityEntity(1,"Город"))
        locations=LocationRepository(db.cityDao(),db.shopDao(),app.getSharedPreferences("270-session-location",0))
        products=ProductRepository(db.productDao(),db.productShopLinkDao(),db)
        shops=ShopRepository(db.shopDao(),db.shopDepartmentDao(),locations,db)
        basket=ShoppingListRepository(db.shoppingListEntryDao(),db,locations)
        classifiers=ClassifierRepository(db.productGroupDao(),db.recipientDao(),db.unitDao(),db)
        manager=BackupManager(app,db)
    }
    private suspend fun pump(condition:()->Boolean) {
        val until=System.nanoTime()+20_000_000_000
        while(!condition()&&System.nanoTime()<until){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20));yield();Thread.sleep(5)}
        assertTrue("Async operation did not finish",condition())
    }
    @After fun close():Unit=runBlocking {store.clear();pump{!ProductEditorTransactions.mutex.isLocked};locations.closeForTest();db.close()}
    private fun settings(key:String)=SettingsViewModel(manager,classifiers,products,shops,prefs,app,ResetRepository(app,db)).also{store.put(key,it)}
    private fun batch(key:String)=BatchViewModel(app,products,shops,basket,classifiers,BatchRepository(db,products,basket)).also{store.put(key,it)}
    private suspend fun restore(){val file=manager.exportFile();try{manager.importFile(file)}finally{manager.discard(file)}}

    @Test fun oldBatchCannotApplySelectionToRestoredIdsAndFreshWindowWorks():Unit=runBlocking {
        val id=db.productDao().insert(ProductEntity(name="Товар"));val old=batch("old")
        restore();var completed=false
        old.products(setOf(id),ProductBatchChange.Favorite(true)){completed=true}
        pump{!old.busy.value&&old.error.value!=null}
        assertTrue(old.error.value!!.contains("База восстановлена"));assertFalse(completed);assertNull(old.notice.value)
        assertFalse(db.productDao().getById(id)!!.isFavorite)
        val fresh=batch("fresh");fresh.products(setOf(id),ProductBatchChange.Favorite(true)){completed=true}
        pump{completed};assertTrue(db.productDao().getById(id)!!.isFavorite);assertNull(fresh.error.value)
    }

    @Test fun oldSettingsCannotRenameDeleteStyleOrSavePresetIntoRestoredDictionary():Unit=runBlocking {
        val group=db.productGroupDao().insert(ProductGroupEntity(name="Группа"))
        val recipient=db.recipientDao().insert(RecipientEntity(name="Получатель"))
        val unit=db.unitDao().insert(UnitEntity(name="шт"))
        val old=settings("old");val savedUnit=db.unitDao().getById(unit)!!
        restore()
        old.renameProductGroup(db.productGroupDao().getById(group)!!,"Не записывать")
        pump{old.uiState.value.message!=null};assertTrue(old.uiState.value.message!!.contains("База восстановлена"))
        old.clearMessage();old.deleteUnit(savedUnit);pump{old.uiState.value.message!=null}
        old.clearMessage();old.style("group",group,"rose","food");pump{old.uiState.value.message!=null}
        var presetResult:Boolean?=null;old.setGroupDefaultRecipient(group,recipient){presetResult=it};pump{presetResult!=null}
        assertEquals(false,presetResult);assertEquals("Группа",db.productGroupDao().getById(group)!!.name)
        assertEquals("",db.productGroupDao().getById(group)!!.accent);assertNotNull(db.unitDao().getById(unit))
        assertTrue(db.creationPresetDao().groups().isEmpty())
        val fresh=settings("fresh");fresh.renameProductGroup(db.productGroupDao().getById(group)!!,"После открытия")
        pump{runBlocking{db.productGroupDao().getById(group)!!.name=="После открытия"}};assertNull(fresh.uiState.value.message)
    }

    @Test fun fullRestoreThroughSettingsRequestsFreshNavigation():Unit=runBlocking {
        db.productDao().insert(ProductEntity(name="Товар"));val file=manager.exportFile();val vm=settings("settings")
        try {
            vm.receiveImport(Uri.fromFile(file));pump{vm.uiState.value.pendingImportPath!=null}
            vm.importSelected();pump{!vm.uiState.value.isLoading&&vm.uiState.value.resetCompleted}
            assertEquals("Полная копия восстановлена",vm.uiState.value.message)
            assertEquals("Товар",db.productDao().getAllSync().single().name)
        }finally{manager.discard(file)}
    }

    @Test fun dictionaryResetRequestsFreshNavigationButBuiltInRestoreKeepsCurrentSession():Unit=runBlocking {
        val vm=settings("settings");val generation=ProductEditorTransactions.generation.get()
        vm.reset(ResetMode.BUILT_INS);pump{!vm.uiState.value.isLoading&&vm.uiState.value.message!=null}
        assertFalse(vm.uiState.value.resetCompleted);assertEquals(generation,ProductEditorTransactions.generation.get())
        vm.clearMessage();vm.reset(ResetMode.DICTIONARIES)
        pump{!vm.uiState.value.isLoading&&vm.uiState.value.resetCompleted}
        assertEquals(generation+1,ProductEditorTransactions.generation.get());assertEquals("Сброс выполнен",vm.uiState.value.message)
    }
}
