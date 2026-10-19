package com.spisokryadom.app

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.speech.RecognizerIntent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.ProductEditorTransactions
import com.spisokryadom.app.util.ProductEditingPreferences
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.*
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=SpisokRyadomApp::class,qualifiers="w360dp-h800dp-xhdpi")
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Refine160UiTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 private val app get()=ApplicationProvider.getApplicationContext<SpisokRyadomApp>()
 private val db get()=app.container.database
 private var shop=0L;private var type=0L;private var ids=emptyList<Long>()
 private fun waitFor(c:()->Boolean){ui.waitUntil(25_000){Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(25));ui.waitForIdle();c()}}
 private fun waitText(t:String)=waitFor{ui.onAllNodesWithText(t).fetchSemanticsNodes().isNotEmpty()}
 private fun tab(t:String){ui.onNode(hasText(t) and SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.Tab)).performClick()}
 private fun back(){ui.runOnIdle{ui.activity.onBackPressedDispatcher.onBackPressed()};ui.waitForIdle()}
 private fun shot(n:String){ui.waitForIdle();ui.runOnIdle{
  val v=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf{it.isShowing}?.window?.decorView?:ui.activity.window.decorView
  val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b));val f=File("../.local/refinement-0.16.0/$n-api${android.os.Build.VERSION.SDK_INT}.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
 }}
 @Before fun seed(){runBlocking{
  listOf("drafts","product_card_drafts","shopping_list_prefs","product_db_prefs").forEach{app.getSharedPreferences(it,0).edit().clear().commit()}
  db.shoppingListEntryDao().deleteAll();db.goalListDao().deleteAll();db.productDao().deleteAll();db.productGroupDao().deleteAll();db.shopDao().deleteAll();db.shopDepartmentDao().clearTypes();app.container.locations.select(1)
  shop=db.shopDao().insert(ShopEntity(name="Светофор (маяк)"));type=db.shopDepartmentDao().ensureType("Бытовая химия")
  db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=shop,name="Бытовая химия",typeId=type))
  ids=listOf("Жидкость для линз","Щелочь для труб","Салфетки").map{app.container.productRepository.saveWithLinks(ProductEntity(name=it),listOf(ProductShopLinkEntity(productId=0,shopId=shop)))}
 };waitText("Список пуст")}
 @After fun close(){ui.activityRule.scenario.close();val end=System.nanoTime()+20_000_000_000;while(ProductEditorTransactions.mutex.isLocked&&System.nanoTime()<end){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();Thread.sleep(5)};assertFalse(ProductEditorTransactions.mutex.isLocked);app.container.locations.closeForTest();db.close();org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null)}
 private fun catalogue(){tab("Товары");waitText("База товаров");waitFor{runCatching{ui.onNodeWithContentDescription("Выбрать товары").assertIsEnabled()}.isSuccess}}
 private fun batchDepartment(){catalogue();ui.onNodeWithContentDescription("Выбрать товары").performClick();ui.onNodeWithContentDescription("Выбрать все").performClick();waitText("Выбрано: 3");ui.onNodeWithContentDescription("Действия с выбранными").performClick();ui.onNodeWithText("Назначить группу магазина").performClick()}
 @Test fun oneCommonShopIsSelectedAutomaticallyAndApplyActuallyUpdatesAllThree(){
  batchDepartment();ui.onNodeWithText("Светофор (маяк)").assertIsSelected();ui.onNodeWithText("Бытовая химия").performClick();ui.onNodeWithText("Применить к 3").assertIsEnabled();shot("01-bulk-one-shop-enabled");ui.onNodeWithText("Применить к 3").performClick();waitText("База товаров")
  runBlocking{val links=db.productShopLinkDao().getAllSync();assertEquals(3,links.size);assertTrue(links.all{it.departmentId!=null});assertEquals("Бытовая химия",db.shopDepartmentDao().getById(links.first().departmentId!!)!!.name)}
 }
 @Test fun severalCommonShopsRequireExplicitChoiceAndThenEnableApply(){
  val other=runBlocking{val s=db.shopDao().insert(ShopEntity(name="Батон"));ids.forEach{db.productShopLinkDao().insert(ProductShopLinkEntity(productId=it,shopId=s,priority=2))};s}
  batchDepartment();waitText("Выберите магазин для назначения группы");ui.onNodeWithText("Бытовая химия").assertIsNotEnabled();ui.onNodeWithText("Применить к 3").assertIsNotEnabled()
  ui.onNodeWithText("Светофор (маяк)").performClick();ui.onNodeWithText("Бытовая химия").performClick();ui.onNodeWithText("Применить к 3").assertIsEnabled().performClick();waitText("База товаров")
  runBlocking{assertTrue(db.productShopLinkDao().getAllSync().filter{it.shopId==other}.all{it.departmentId==null})}
 }
 @Test fun nameSuggestionsDisappearOnOpeningGroupAndReturnOnlyOnNameFocus(){
  catalogue();ui.onNodeWithContentDescription("Новый товар").performClick();waitText("Название товара");ui.onNodeWithTag("product-name").performTextInput("Пинцет для линз");waitText("Уже в базе");shot("02-name-suggestions-focused")
  ui.onNodeWithText("Товарная группа").performScrollTo().performClick();ui.onNodeWithText("Уже в базе").assertDoesNotExist();shot("03-name-suggestions-hidden")
  ui.onNodeWithText("Новая группа…").performScrollTo().performClick();waitText("Новая товарная группа");ui.onNodeWithText("Отмена").performClick()
  ui.onNodeWithTag("product-name").performScrollTo().performClick();waitText("Уже в базе")
  ui.onNodeWithText("Заметка").performScrollTo().performTextInput("Проверка");ui.onNodeWithText("Уже в базе").assertDoesNotExist()
 }
 @Test fun searchCreatesPrefilledCardAndSaveRevealsProductWithoutAddingPurchase(){
  ProductEditingPreferences.preferences(app).edit().putBoolean(ProductEditingPreferences.KEY,false).commit();catalogue();val name="Говяжье лёгкое & №1 / тест"
  ui.onNodeWithText("Поиск по названию...").performTextInput(name);waitFor{ui.onAllNodesWithTag("create-product-from-search").fetchSemanticsNodes().isNotEmpty()};shot("04-create-from-search")
  ui.onNodeWithTag("create-product-from-search").performClick();waitText("Название товара");ui.onNodeWithTag("product-name").assertTextContains(name);assertEquals(3,runBlocking{db.productDao().getAllSync().size})
  ui.onNodeWithText("Сохранить").performClick();waitText("База товаров");val id=runBlocking{db.productDao().getAllSync().single{it.name==name}.id};waitFor{ui.onAllNodesWithTag("catalogue-all-$id").fetchSemanticsNodes().isNotEmpty()};ui.onNodeWithTag("catalogue-all-$id").assertIsDisplayed();shot("05-created-product-revealed");assertTrue(runBlocking{db.shoppingListEntryDao().getAllSync().isEmpty()})
 }
 @Test fun voiceActivityCancellationKeepsSearchAndSuccessfulResultOnlyChangesQuery(){
  catalogue();ui.onNodeWithText("Поиск по названию...").performTextInput("Жидкость");ui.onNodeWithContentDescription("Голосовой поиск").performClick()
  val shadow=Shadows.shadowOf(ui.activity);val first=shadow.nextStartedActivityForResult;assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH,first.intent.action)
  ui.runOnUiThread{shadow.receiveResult(first.intent,Activity.RESULT_CANCELED,null)};ui.onNode(hasSetTextAction()).assertTextContains("Жидкость")
  ui.onNodeWithContentDescription("Голосовой поиск").performClick();val second=shadow.nextStartedActivityForResult
  ui.runOnUiThread{shadow.receiveResult(second.intent,Activity.RESULT_OK,Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS,arrayListOf("Говяжье лёгкое")))}
  waitText("Создать «Говяжье лёгкое»");ui.onNode(hasSetTextAction()).assertTextContains("Говяжье лёгкое");shot("06-voice-search-result");assertEquals(3,runBlocking{db.productDao().getAllSync().size});assertTrue(runBlocking{db.shoppingListEntryDao().getAllSync().isEmpty()})
 }
 @Test fun settingsDisableAutosaveAndBackReallyDiscardsNewCard(){
  ui.onNodeWithContentDescription("Настройки").performClick();waitText("Поведение списка");ui.onNodeWithText("Автосохранение карточек товаров").performScrollTo().performClick()
  waitFor{!ProductEditingPreferences.enabled(ProductEditingPreferences.preferences(app))};shot("07-autosave-setting-off");back();catalogue();ui.onNodeWithContentDescription("Новый товар").performClick();waitText("Название товара");ui.onNodeWithTag("product-name").performTextInput("Не сохранять");ui.onNodeWithContentDescription("Назад").performClick();waitText("База товаров");assertEquals(3,runBlocking{db.productDao().getAllSync().size})
 }
}
