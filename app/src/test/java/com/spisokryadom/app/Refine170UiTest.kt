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
class Refine170UiTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 private val app get()=ApplicationProvider.getApplicationContext<SpisokRyadomApp>()
 private val db get()=app.container.database
 private var shop=0L;private var group=0L;private var pet=0L;private var person=0L
 private fun waitFor(c:()->Boolean){ui.waitUntil(25_000){Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(25));ui.waitForIdle();c()}}
 private fun waitText(t:String)=waitFor{ui.onAllNodesWithText(t).fetchSemanticsNodes().isNotEmpty()}
 private fun tab(t:String){ui.onNode(hasText(t) and SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.Tab)).performClick()}
 private fun back(){ui.runOnIdle{ui.activity.onBackPressedDispatcher.onBackPressed()};ui.waitForIdle()}
 private fun shot(n:String){ui.waitForIdle();ui.runOnIdle{
  val v=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf{it.isShowing}?.window?.decorView?:ui.activity.window.decorView
  val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b));val f=File("../.local/refinement-0.17.0/$n-api${android.os.Build.VERSION.SDK_INT}.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
 }}
 @Before fun seed(){runBlocking{
  listOf("drafts","product_card_drafts","shopping_list_prefs","product_db_prefs").forEach{app.getSharedPreferences(it,0).edit().clear().commit()}
  ProductEditingPreferences.preferences(app).edit().putBoolean(ProductEditingPreferences.KEY,false).commit()
  db.shoppingListEntryDao().deleteAll();db.goalListDao().deleteAll();db.productDao().deleteAll();db.productGroupDao().deleteAll();db.shopDao().deleteAll();db.shopDepartmentDao().clearTypes();db.recipientDao().getAllSync().forEach{db.recipientDao().delete(it)};app.container.locations.select(1)
  shop=db.shopDao().insert(ShopEntity(name="Батон"));group=db.productGroupDao().insert(ProductGroupEntity(name="Собачье",iconKey="dog"));pet=db.recipientDao().insert(RecipientEntity(name="Злата",iconKey="dog"));person=db.recipientDao().insert(RecipientEntity(name="Рома",iconKey="man"));db.unitDao().deleteAll();db.unitDao().insert(UnitEntity(name="шт"));db.unitDao().insert(UnitEntity(name="ед.",displayOrder=1))
 };waitText("Список пуст")}
 @After fun close(){ui.activityRule.scenario.close();val end=System.nanoTime()+20_000_000_000;while(ProductEditorTransactions.mutex.isLocked&&System.nanoTime()<end){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();Thread.sleep(5)};assertFalse(ProductEditorTransactions.mutex.isLocked);app.container.locations.closeForTest();db.close();org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null)}
 private fun settings(){ui.onNodeWithContentDescription("Настройки").performClick();waitText("Настройки")}
 private fun newProduct(name:String){tab("Товары");waitText("База товаров");ui.onNodeWithContentDescription("Новый товар").performClick();waitText("Название товара");ui.onNodeWithTag("product-name").performTextInput(name)}
 @Test fun shopRuleIsSavedAndUsedInProductCreatedInsideThatShop(){
  tab("Магазины");ui.onNodeWithContentDescription("Новый магазин").performClick();waitText("Название магазина");ui.onNodeWithText("Название магазина").performTextInput("Онлайн-витрина")
  ui.onNodeWithText("Тип покупок в магазине").performScrollTo().performClick();ui.onNodeWithText("Только онлайн").performClick();shot("01-shop-online-preset");ui.onNodeWithText("Сохранить").performClick();waitText("Магазины")
  val id=runBlocking{db.shopDao().getAllSync().single{it.name=="Онлайн-витрина"}.id};assertEquals("online",runBlocking{db.creationPresetDao().shopMode(id)})
  ui.onNodeWithText("Онлайн-витрина").performClick();ui.onNodeWithText("Создать новый товар").performClick();waitText("Название товара");ui.onNodeWithTag("product-name").performTextInput("Кабель");ui.onNodeWithText("Оффлайн").performScrollTo().assertIsNotEnabled();shot("02-product-online-default");ui.onNodeWithText("Сохранить").performClick();waitFor{runBlocking{db.productDao().getAllSync().any{it.name=="Кабель"}}};assertEquals("online",runBlocking{db.productDao().getAllSync().single().purchaseType})
 }
 @Test fun groupDefaultRecipientAppliesAndManualChoiceOverridesIt(){
  settings();ui.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("manager-edit-Товарные группы"));ui.onNodeWithTag("manager-edit-Товарные группы").performClick();ui.onNodeWithContentDescription("Преднастройка: Собачье").performScrollTo().performClick();waitText("Преднастройка группы")
  ui.onNodeWithText("Получатель по умолчанию").performClick();ui.onAllNodesWithText("Злата").onLast().performClick();shot("03-group-recipient-preset");ui.onNodeWithText("Сохранить").performClick();waitFor{runBlocking{db.creationPresetDao().groups().singleOrNull()?.recipientId==pet}};waitFor{ui.onAllNodesWithText("Преднастройка группы").fetchSemanticsNodes().isEmpty()};back()
  newProduct("Корм");ui.onNodeWithText("Товарная группа").performClick();ui.onNodeWithText("Собачье").performClick();waitText("Злата");shot("04-recipient-filled");ui.onNodeWithText("Получатель").performClick();ui.onAllNodesWithText("Рома").onLast().performClick();ui.onNodeWithText("Сохранить").performClick();waitText("База товаров");assertEquals(person,runBlocking{db.productDao().getAllSync().single().recipientId})
 }
 @Test fun globalDefaultsStayOutOfUnitFieldUntilSaving(){
  settings();ui.onNodeWithText("Тип покупки по умолчанию").performScrollTo().performClick();ui.onNodeWithText("Онлайн").performClick();ui.onNodeWithText("Если единица не заполнена").performScrollTo().performClick();ui.onNodeWithText("ед").performClick();shot("05-global-creation-defaults");back()
  newProduct("Новая позиция");ui.onNodeWithText("Ед. изм.").performScrollTo().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,androidx.compose.ui.text.AnnotatedString("")));ui.onNodeWithText("Сохранить").performClick();waitText("База товаров")
  val p=runBlocking{db.productDao().getAllSync().single()};assertEquals("ед",p.defaultUnit);assertEquals(1.0,p.defaultQuantity!!,0.0);assertEquals("online",p.purchaseType)
 }
 @Test fun recipientIconPackAndDifferentBottomTabsAreAvailable(){
  shot("06-distinct-bottom-tabs");settings();ui.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("manager-edit-Получатели"));ui.onNodeWithTag("manager-edit-Получатели").performClick();ui.onNodeWithContentDescription("Оформление: Злата").performScrollTo().performClick();waitText("Оформление группы")
  ui.onNodeWithTag("appearance-icon-search").performScrollTo().performTextInput("Кошка");ui.onNodeWithTag("appearance-icon-cat").performScrollTo().performClick();shot("07-recipient-cat-icon");ui.onNodeWithText("Применить оформление").performClick();waitFor{runBlocking{db.recipientDao().getById(pet)?.iconKey=="cat"}}
 }
 @Test fun standardCategoriesButtonIsIdempotentAndDoesNotPopulateShops(){
  settings();ui.onNodeWithTag("settings-list").performScrollToNode(hasText("Добавить недостающие группы"));ui.onNodeWithText("Добавить недостающие группы").performClick();waitFor{runBlocking{db.shopDepartmentDao().types().size==30}};ui.onNodeWithText("Добавить недостающие группы").performClick();waitFor{runBlocking{db.productGroupDao().getAllSync().size==31}};assertTrue(runBlocking{db.shopDepartmentDao().getAllSync().isEmpty()});shot("08-standard-library-control")
 }
 @Test fun quickAddFromSearchUsesStorePresetAndFallbackUnit(){
  runBlocking{db.creationPresetDao().setShop(ShopCreationPreset(shop,"online"))};ProductEditingPreferences.preferences(app).edit().putString(com.spisokryadom.app.util.ProductCreationDefaults.UNIT,"ед").commit()
  ui.onNodeWithContentDescription("Добавить в список").performClick();waitText("Добавить покупку");ui.onNodeWithText("Магазин").performClick();ui.onNodeWithText("Батон").performClick();ui.onNodeWithText("Название товара").performTextInput("Быстрая позиция");waitFor{ui.onAllNodesWithText("Новый товар: Быстрая позиция").fetchSemanticsNodes().isNotEmpty()};ui.onNodeWithTag("add-screen-list").performScrollToNode(hasText("Добавить в список"));ui.onNodeWithText("Добавить в список").performClick();waitText("Список покупок");waitFor{runBlocking{db.shoppingListEntryDao().getAllSync().size==1}};val p=runBlocking{db.productDao().getAllSync().single()};assertEquals("online",p.purchaseType);assertEquals("ед",p.defaultUnit);shot("09-quick-purchase-defaults")
 }
}
