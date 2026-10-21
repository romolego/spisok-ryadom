package com.spisokryadom.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.ShopChoice
import com.spisokryadom.app.widget.ShoppingWidgetData
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
class QuickAdd241UiTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 @get:Rule val case=org.junit.rules.TestName()
 private val app get()=ApplicationProvider.getApplicationContext<SpisokRyadomApp>()
 private val db get()=app.container.database
 private val goals get()=app.container.goalListRepository
 private val basket get()=app.container.shoppingListRepository
 private var banana=0L;private var apple=0L
 private fun waitFor(c:()->Boolean){try{ui.waitUntil(25_000){Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(20));ui.waitForIdle();c()}}
 catch(e:Throwable){val dir=File("build/reports/refine241/failures");dir.mkdirs();runCatching{File(dir,"${case.methodName}.txt").writeText(ui.onRoot(useUnmergedTree=true).printToString())};runCatching{shot("failure-${case.methodName}")};throw e}}
 private fun waitTag(tag:String)=waitFor{ui.onAllNodesWithTag(tag,useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
 private fun waitText(text:String)=waitFor{ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
 private fun tab(text:String){ui.onNode(hasText(text) and SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.Tab)).performClick()}
 private fun shot(name:String){ui.runOnIdle{val view=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf{it.isShowing}?.window?.decorView?:ui.activity.window.decorView;val b=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(b));val f=File("build/reports/refine241/screens/$name-api${android.os.Build.VERSION.SDK_INT}.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 @Before fun seed(){runBlocking{
  listOf("drafts","shopping_list_prefs","product_db_prefs").forEach{app.getSharedPreferences(it,0).edit().clear().commit()}
  db.shoppingListEntryDao().deleteAll();db.goalListDao().deleteAll();db.productDao().deleteAll();db.productGroupDao().deleteAll();db.shopDao().deleteAll();db.shopDepartmentDao().clearTypes();app.container.locations.select(1)
  banana=db.productDao().insert(ProductEntity(name="Банан",isFavorite=true,defaultQuantity=.5,defaultUnit="кг"))
  apple=db.productDao().insert(ProductEntity(name="Яблоко",defaultUnit="шт"))
 };waitText("Список пуст")}
 @After fun close(){ui.activityRule.scenario.close();app.container.locations.closeForTest();db.close();org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null)}
 @Test fun newPurchaseOffersUnitsWithoutErasingTheTypedValue(){
  runBlocking{db.unitDao().deleteAll();db.unitDao().insert(UnitEntity(name="шт."));db.unitDao().insert(UnitEntity(name="кг"));db.unitDao().insert(UnitEntity(name="мешок"))}
  ui.onNodeWithContentDescription("Добавить в список").performClick();waitText("Название товара")
  ui.onNodeWithText("Название товара").performTextInput("Помада гигиеническая")
  waitFor{runCatching{ui.onNodeWithText("Добавить в список").assertIsEnabled()}.isSuccess}
  ui.onNodeWithText("Единица").performScrollTo().performTextInput("шт")
  waitText("кг");ui.onNodeWithText("шт.").assertIsDisplayed();shot("quick-unit-options")
  ui.onNodeWithText("кг").performClick();ui.onNodeWithText("Единица").assertTextContains("кг")
  ui.onNodeWithText("Добавить в список").performScrollTo().performClick()
  waitText("Помада гигиеническая")
  assertEquals("кг",runBlocking{db.shoppingListEntryDao().getAllSync().single().unit})
 }
 @Test fun typingDoesNotRemoveNewProductControlsOrInsertLoadingRows(){
  ui.onNodeWithContentDescription("Добавить в список").performClick();waitText("Название товара")
  ui.onNodeWithText("Название товара").performTextInput("Помада")
  waitText("Сохранить в базе товаров")
  val name=ui.onNodeWithText("Название товара")
  val before=ui.onNodeWithText("Сохранить в базе товаров").fetchSemanticsNode().boundsInRoot.top
  for(value in listOf("Помада г","Помада ги","Помада гиг")){
   name.performTextReplacement(value)
   ui.onNodeWithText("Сохранить в базе товаров").assertExists()
   val after=ui.onNodeWithText("Сохранить в базе товаров").fetchSemanticsNode().boundsInRoot.top
   assertEquals(before,after,1f)
  }
  waitFor{runCatching{ui.onNodeWithText("Добавить в список").assertIsEnabled()}.isSuccess}
  shot("quick-stable-form")
 }
 @Test fun appearanceLivesBehindOneIconAndCancelDoesNotChangeTheStore(){
  val id=runBlocking{db.shopDao().insert(ShopEntity(name="Фикс Прайс",shoppingBackground="rose"))}
  tab("Магазины");waitText("Фикс Прайс");ui.onNodeWithText("Фикс Прайс").performTouchInput{longClick()};waitText("Название магазина")
  ui.onNodeWithTag("shop-background-switch").assertDoesNotExist()
  ui.onNodeWithContentDescription("Оформление магазина").performClick();waitText("Подложка")
  ui.onNodeWithTag("shop-appearance-apply").assertIsDisplayed();shot("shop-appearance-compact")
  ui.onNodeWithText("Фон товаров").performScrollTo().performClick();ui.onNodeWithText("В тон подложки").performClick()
  ui.onNodeWithText("Отмена").performClick();ui.onNodeWithText("Сохранить").performClick();waitText("Магазины")
  assertEquals("",runBlocking{db.shopDao().getById(id)!!.purchaseBackground})
  ui.onNodeWithText("Фикс Прайс").performTouchInput{longClick()};waitText("Название магазина")
  ui.onNodeWithContentDescription("Оформление магазина").performClick();ui.onNodeWithText("Фон товаров").performScrollTo().performClick();ui.onNodeWithText("В тон подложки").performClick()
  ui.onNodeWithTag("shop-appearance-apply").performClick();ui.onNodeWithText("Сохранить").performClick();waitText("Магазины")
  assertEquals("auto",runBlocking{db.shopDao().getById(id)!!.purchaseBackground})
 }
}
