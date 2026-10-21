package com.spisokryadom.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.ui.addtolist.AddToListViewModel
import com.spisokryadom.app.widget.WidgetIntents
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
class Refine140UiTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 private val app get()=ApplicationProvider.getApplicationContext<SpisokRyadomApp>()
 private val db get()=app.container.database
 private val prefs get()=app.getSharedPreferences("shopping_list_prefs",0)
 private var a=0L;private var b=0L;private var banana=0L;private var apple=0L;private var water=0L
 private fun waitFor(c:()->Boolean){ui.waitUntil(20_000){Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(20));ui.waitForIdle();c()}}
 private fun waitText(t:String)=waitFor{ui.onAllNodesWithText(t).fetchSemanticsNodes().isNotEmpty()}
 private fun shot(name:String){ui.waitForIdle();ui.runOnIdle{val v=ui.activity.window.decorView;val bitmap=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(bitmap));val file=File("../.local/refinement-0.14.0/$name-api${android.os.Build.VERSION.SDK_INT}.png");file.parentFile!!.mkdirs();file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}}
 @Before fun seed(){runBlocking{
  listOf("drafts","shopping_list_prefs","product_db_prefs").forEach{app.getSharedPreferences(it,0).edit().clear().commit()}
  db.shoppingListEntryDao().deleteAll();db.productDao().deleteAll();db.productGroupDao().deleteAll();db.shopDao().deleteAll();db.shopDepartmentDao().clearTypes();app.container.locations.select(1)
  a=db.shopDao().insert(ShopEntity(name="Батон"));b=db.shopDao().insert(ShopEntity(name="Красный Яр",displayOrder=1))
  banana=app.container.productRepository.saveWithLinks(ProductEntity(name="Банан",isFavorite=true,defaultUnit="кг",defaultQuantity=.5),listOf(ProductShopLinkEntity(productId=0,shopId=a)))
  apple=app.container.productRepository.saveWithLinks(ProductEntity(name="Яблоко",defaultUnit="шт"),listOf(ProductShopLinkEntity(productId=0,shopId=a)))
  water=app.container.productRepository.saveWithLinks(ProductEntity(name="Вода",defaultUnit="бутылка"),listOf(ProductShopLinkEntity(productId=0,shopId=b)))
 };waitText("Список пуст")}
 @After fun close(){ui.activityRule.scenario.close();app.container.locations.closeForTest();db.close();org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null)}
 private fun add(){ui.onNodeWithContentDescription("Добавить в список").performClick();waitText("Избранные")}
 @Test fun blankPickerRepeatsFavoritesAndQuickAddKeepsItOpen(){
  add();ui.onNodeWithTag("candidate-favorite-$banana").assertIsDisplayed()
  ui.onNodeWithTag("add-candidates").performScrollToNode(hasTestTag("candidate-all-$banana"));ui.onNodeWithTag("candidate-all-$banana").assertIsDisplayed()
  shot("01-browse-all")
  ui.onNodeWithTag("candidate-all-$banana").onChildren().filter(hasContentDescription("Добавить: Банан")).onFirst().performClick()
  waitFor{runBlocking{db.shoppingListEntryDao().getAllSync().size==1}}
  ui.onNodeWithText("Добавить покупку").assertIsDisplayed()
  ui.onNodeWithTag("add-candidates").performScrollToNode(hasTestTag("candidate-all-$apple"))
  ui.onNode(hasContentDescription("Добавить: Яблоко")).performClick()
  waitFor{runBlocking{db.shoppingListEntryDao().getAllSync().size==2}}
  ui.onNodeWithText("Добавить покупку").assertIsDisplayed()
  runBlocking{assertEquals(.5,db.shoppingListEntryDao().getAllSync().single{it.productId==banana}.quantity,0.0)}
 }
 @Test fun explicitShopPickerFiltersBeforeAnyTyping(){
  add();ui.onNodeWithText("Магазин").performScrollTo().performClick();ui.onAllNodesWithText("Красный Яр").onLast().performClick()
  waitText("Вода");ui.onNodeWithTag("candidate-all-$water").assertIsDisplayed()
  ui.onNodeWithTag("candidate-all-$banana").assertDoesNotExist();ui.onNodeWithText("Избранные").assertDoesNotExist();shot("02-shop-browse")
  ui.onNodeWithTag("candidate-all-$water").performClick();ui.onNodeWithText("Количество").performScrollTo().performTextReplacement("3")
  ui.onNodeWithText("Добавить в список").performScrollTo().performClick();waitText("Список покупок")
  waitFor{runBlocking{db.shoppingListEntryDao().getAllSync().singleOrNull()?.quantity==3.0}}
 }
 @Test fun settingsSpacingAndBrowseToggleSurviveRecreation(){
  ui.onNodeWithContentDescription("Настройки").performClick();waitText("Поведение списка")
  ui.onNodeWithText("Поднимать магазины со срочными покупками").performScrollTo()
  val first=ui.onNodeWithText("Показывать срочные в свёрнутых магазинах").getUnclippedBoundsInRoot()
  val second=ui.onNodeWithText("Поднимать магазины со срочными покупками").getUnclippedBoundsInRoot()
  assertTrue("Separate label blocks",second.top-first.bottom>=12.dp)
  ui.onNodeWithText("Показывать товары до ввода названия").performScrollTo();shot("03-settings-spacing")
  ui.onNodeWithText("Показывать товары до ввода названия").performClick();waitFor{!prefs.getBoolean(AddToListViewModel.PREF_BROWSE_PRODUCTS,true)}
  ui.activityRule.scenario.recreate();waitText("Настройки");ui.onNodeWithContentDescription("Назад").performClick()
  ui.onNodeWithContentDescription("Добавить в список").performClick();waitText("Название товара")
  ui.onNodeWithTag("add-candidates").assertDoesNotExist()
  ui.onNodeWithText("Название товара").performTextInput("Банан");waitFor{runCatching{ui.onNodeWithContentDescription("Избранный товар: Банан").assertIsDisplayed()}.isSuccess}
  ui.onNodeWithContentDescription("Избранный товар: Банан").assertIsDisplayed();shot("04-search-with-browse-off")
 }
 @Test fun shareUsesEditedTextAndReopeningRegeneratesFromUnchangedBasket(){
  runBlocking{app.container.shoppingListRepository.addProduct(banana,.5)};waitText("Банан")
  ui.onNodeWithContentDescription("Текстовый список").performClick();waitText("Ваш список")
  ui.onNodeWithTag("bulk-editor").performTextReplacement("Купи бананы, пожалуйста")
  ui.onNodeWithContentDescription("Поделиться списком").performClick()
  ui.runOnIdle {val chooser=Shadows.shadowOf(ui.activity).nextStartedActivity;assertEquals(Intent.ACTION_CHOOSER,chooser.action);val send=chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!;assertEquals("Купи бананы, пожалуйста",send.getStringExtra(Intent.EXTRA_TEXT))}
  ui.onNodeWithText("Проверить изменения").assertDoesNotExist();shot("05-share-message")
  ui.onNodeWithContentDescription("Назад").performClick();waitText("Список покупок")
  runBlocking{assertEquals(.5,db.shoppingListEntryDao().getAllSync().single().quantity,0.0)}
  ui.onNodeWithContentDescription("Текстовый список").performClick()
  waitFor{ui.onAllNodes(hasSetTextAction() and hasText("Банан — 0,5 кг",substring=true)).fetchSemanticsNodes().isNotEmpty()}
  shot("06-regenerated-message")
 }
 @Test fun longPlusDoesNothingAndWidgetAddUsesSameBrowser(){
  ui.onNodeWithContentDescription("Добавить в список").performTouchInput{longClick()}
  ui.onNodeWithText("Добавить покупку").assertDoesNotExist();ui.onNodeWithText("Текстовый список").assertDoesNotExist()
  val scenarioIntent=ui.activity.intent
  ui.runOnUiThread{org.robolectric.util.ReflectionHelpers.callInstanceMethod<Unit>(ui.activity,"onNewIntent",org.robolectric.util.ReflectionHelpers.ClassParameter.from(Intent::class.java,WidgetIntents.open(app,true)))}
  try {waitText("Избранные");ui.onNodeWithTag("candidate-favorite-$banana").assertIsDisplayed();shot("07-widget-entry-browser")}
  finally {ui.runOnUiThread{ui.activity.intent=scenarioIntent}}
 }
}
