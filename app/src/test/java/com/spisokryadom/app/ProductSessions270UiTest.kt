package com.spisokryadom.app

import android.os.Looper
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.backup.BackupManager
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.ProductEditorTransactions
import com.spisokryadom.app.ui.components.*
import com.spisokryadom.app.ui.productcard.ProductCardViewModel
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
@Config(sdk=[28,35],application=SpisokRyadomApp::class,qualifiers="w360dp-h640dp-xhdpi")
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ProductSessions270UiTest {
 @get:Rule val ui=createComposeRule()
 private val app get()=ApplicationProvider.getApplicationContext<SpisokRyadomApp>()
 private val db get()=app.container.database
 private val models=ViewModelStore()
 private var product=0L;private var secondCity=0L
 private fun await(c:()->Boolean){ui.waitUntil(25_000){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20));ui.waitForIdle();c()}}
 @Before fun setup():Unit=runBlocking{
  app.getSharedPreferences("shopping_list_prefs",0).edit().clear().putBoolean(ProductEditingPreferences.KEY,false).commit()
  db.shoppingListEntryDao().deleteAll();db.goalListDao().deleteAll();db.productDao().deleteAll();db.shopDao().deleteAll()
  db.cityDao().update(CityEntity(1,"Первый город"));secondCity=db.cityDao().insert(CityEntity(name="Второй город"));app.container.locations.select(1)
  product=db.productDao().insert(ProductEntity(name="Товар"))
 }
 @After fun close(){models.clear();await{!ProductEditorTransactions.mutex.isLocked};app.container.locations.closeForTest();db.close();org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null)}
 private fun model()=ProductCardViewModel(product,-1,app.container.productRepository,app.container.shopRepository,app.container.classifierRepository,app.getSharedPreferences("270-ui-drafts",0),app.getSharedPreferences("shopping_list_prefs",0)).also{models.put("card",it);await{!it.uiState.value.isLoading}}
 private fun restore()=runBlocking{coroutineScope{
  val task=async(Dispatchers.IO){val manager=BackupManager(app,db);val file=manager.exportFile();try{manager.importFile(file)}finally{manager.discard(file)}}
  await{task.isCompleted};task.await()
 }}
 private fun show(content:@Composable ()->Unit)=ui.setContent{MaterialTheme{content()}}

 @Test fun failedShopCreationKeepsInputAndReenablesCancellationWithoutFalseSuccess(){
  val vm=model();var open by mutableStateOf(true);var success=false
  show{val state by vm.uiState.collectAsState();Column{if(open)InlineCreateValue("Название магазина",{name,done->vm.createShop(name,{success=true;open=false},done)},{open=false});state.error?.let{Text(it)}}}
  ui.onNodeWithText("Название магазина").performTextInput("Сохранить мой ввод");restore()
  ui.onNodeWithContentDescription("Создать: Название магазина").performClick();await{vm.uiState.value.error!=null}
  ui.onNodeWithText("Сохранить мой ввод").assertExists();ui.onNodeWithContentDescription("Отмена: Название магазина").assertIsEnabled().performClick()
  ui.onNodeWithText("Название магазина").assertDoesNotExist();assertFalse(success);assertTrue(runBlocking{db.shopDao().getAllSync().isEmpty()})
 }
 @Test fun failedRecipientCreationKeepsInputAndReenablesCancellation(){
  val vm=model();var open by mutableStateOf(true);var success=false
  val before=runBlocking{db.recipientDao().getAllSync()}
  show{val state by vm.uiState.collectAsState();Column{if(open)InlineCreateValue("Имя получателя",{name,done->vm.createRecipient(name,{success=true;open=false},done)},{open=false});state.error?.let{Text(it)}}}
  ui.onNodeWithText("Имя получателя").performTextInput("Злата");restore()
  ui.onNodeWithContentDescription("Создать: Имя получателя").performClick();await{vm.uiState.value.error!=null}
  ui.onNodeWithText("Злата").assertExists();ui.onNodeWithContentDescription("Отмена: Имя получателя").assertIsEnabled().performClick()
  assertFalse(success);assertEquals(before,runBlocking{db.recipientDao().getAllSync()})
 }
 @Test fun staleGlobalCityPickerCannotSelectRenameOrCreateRestoredCities(){
  show{CityPicker()};await{runBlocking{app.container.locations.cities.value.any{it.id==secondCity}}}
  ui.onNodeWithContentDescription("Изменить город").performClick();restore()
  ui.onNodeWithText("Второй город").performClick();await{ui.onAllNodesWithText("База восстановлена",substring=true).fetchSemanticsNodes().isNotEmpty()}
  assertEquals(1L,runBlocking{app.container.locations.currentId()})
  ui.onNodeWithContentDescription("Изменить Первый город").performClick();ui.onNodeWithText("Город").performTextReplacement("Старое переименование");ui.onNodeWithText("Сохранить").performClick()
  await{ui.onAllNodesWithText("Название города").fetchSemanticsNodes().isEmpty()}
  assertEquals("Первый город",runBlocking{db.cityDao().getById(1)!!.name})
  ui.onNodeWithText("Добавить город").performClick();ui.onNodeWithText("Название").performTextInput("Старый новый город");ui.onNodeWithText("Сохранить").performClick()
  await{ui.onAllNodesWithText("Новый город",substring=false).fetchSemanticsNodes().isEmpty()}
  assertFalse(runBlocking{db.cityDao().getAllSync().any{it.name=="Старый новый город"}})
 }
 @Test fun staleScopedCityCreationReleasesInlineAndDoesNotSelectOrInsert(){
  var selected=1L
  show{CityField(1,{selected=it})};await{app.container.locations.cities.value.any{it.id==secondCity}}
  ui.onNodeWithText("Город магазина").performClick();ui.onNodeWithText("Новый город…").performClick()
  ui.onNodeWithText("Название города").performTextInput("Не создавать");restore()
  ui.onNodeWithContentDescription("Создать: Название города").performClick()
  await{ui.onAllNodesWithText("База восстановлена",substring=true).fetchSemanticsNodes().isNotEmpty()}
  ui.onNodeWithText("Не создавать").assertExists();ui.onNodeWithContentDescription("Отмена: Название города").assertIsEnabled().performClick()
  assertEquals(1L,selected);assertFalse(runBlocking{db.cityDao().getAllSync().any{it.name=="Не создавать"}})
 }
}
