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
class UnifiedLists250UiTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 @get:Rule val case=org.junit.rules.TestName()
 private val app get()=ApplicationProvider.getApplicationContext<SpisokRyadomApp>()
 private val db get()=app.container.database
 private val goals get()=app.container.goalListRepository
 private val basket get()=app.container.shoppingListRepository
 private var banana=0L;private var apple=0L
 private fun waitFor(c:()->Boolean){try{ui.waitUntil(25_000){Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(20));ui.waitForIdle();c()}}
 catch(e:Throwable){val dir=File("build/reports/lists250/failures");dir.mkdirs();runCatching{File(dir,"${case.methodName}.txt").writeText(ui.onRoot(useUnmergedTree=true).printToString())};runCatching{shot("failure-${case.methodName}")};throw e}}
 private fun waitTag(tag:String)=waitFor{ui.onAllNodesWithTag(tag,useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
 private fun waitText(text:String)=waitFor{ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
 private fun tab(text:String){ui.onNode(hasText(text) and SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.Tab)).performClick()}
 private fun shot(name:String){ui.runOnIdle{val view=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf{it.isShowing}?.window?.decorView?:ui.activity.window.decorView;val b=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(b));val f=File("build/reports/lists250/screens/$name-api${android.os.Build.VERSION.SDK_INT}.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 private var list=0L;private var shop=0L
 @Before fun seed(){runBlocking{
  listOf("drafts","product_card_drafts","shopping_list_prefs","product_db_prefs").forEach{app.getSharedPreferences(it,0).edit().clear().commit()}
  app.getSharedPreferences("shopping_list_prefs",0).edit().putBoolean("autosave_product_cards",false).commit()
  db.shoppingListEntryDao().deleteAll();db.goalListDao().deleteAll();db.productDao().deleteAll();db.shopDao().deleteAll();app.container.locations.select(1)
  banana=db.productDao().insert(ProductEntity(name="Банан",isFavorite=true,defaultQuantity=.5,defaultUnit="кг"))
  list=goals.create("Рецепт");val other=goals.create("Другой список");app.container.shoppingListRepository.addGoalCatalogueFixture(other,banana,9.0)
  shop=db.shopDao().insert(ShopEntity(name="Батон"))
 };waitText("Список пуст")}
 @After fun close(){ui.activityRule.scenario.close();app.container.locations.closeForTest();db.close();org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null)}
 private fun open(){tab("Списки");waitTag("goal-list-$list");ui.onNodeWithText("Шаблоны").assertDoesNotExist();ui.onNodeWithTag("goal-list-$list").performClick();waitTag("goal-add-item")}
 private fun add(){open();ui.onNodeWithTag("goal-add-item").performClick();waitTag("add-list-contents")}
 @Test fun sharedPickerShowsRealAddedItemsAndBothFavoriteMarkersImmediatelyAndAfterRecreation(){
  add();waitTag("candidate-favorite-$banana");ui.onNodeWithTag("candidate-added-favorite-$banana",useUnmergedTree=true).assertDoesNotExist()
  ui.onAllNodesWithContentDescription("Добавить: Банан").onFirst().performClick();waitTag("candidate-added-favorite-$banana")
  ui.onNodeWithTag("add-list-current-items").assertExists();ui.onNodeWithTag("candidate-added-all-$banana",useUnmergedTree=true).assertExists();shot("shared-picker-added")
  ui.activityRule.scenario.recreate();waitTag("candidate-added-favorite-$banana")
  ui.onNodeWithTag("add-list-contents").assertExists();assertEquals(.5,runBlocking{goals.snapshot(list).entries.single().quantity},0.0)
  ui.onNodeWithTag("add-screen-list").performScrollToNode(hasText("Открыть"));ui.onNodeWithText("Открыть").performClick();waitTag("goal-entry-${runBlocking{goals.snapshot(list).entries.single().id}}")
  assertTrue(runBlocking{db.shoppingListEntryDao().getVisibleSync().isEmpty()});shot("unified-list-contents")
 }
 @Test fun searchCanCreateFullProductCardWithShopAndReturnToTheSameListBeforeAdding(){
  add();ui.onNodeWithText("Магазин").performClick();ui.onNodeWithText("Батон").performClick()
  ui.onNodeWithText("Название товара").performTextInput("Шампиньоны");waitFor{runCatching{ui.onNodeWithTag("add-create-product").assertIsEnabled()}.isSuccess}
  ui.onNodeWithTag("add-create-product").performScrollTo().performClick();waitText("Новый товар");ui.onNodeWithText("Название товара").assertTextContains("Шампиньоны")
  ui.onNodeWithText("Сохранить").performClick();waitTag("add-list-contents");waitText("Выбран")
  assertTrue(runBlocking{goals.snapshot(list).entries.isEmpty()})
  val product=runBlocking{db.productDao().getAllSync().single{it.name=="Шампиньоны"}}
  assertEquals(shop,runBlocking{db.productShopLinkDao().getLinksForProductSync(product.id).single().shopId})
  ui.onNodeWithText("Добавить в список").performScrollTo().performClick();waitFor{runBlocking{goals.snapshot(list).entries.size==1}};waitTag("goal-entry-${runBlocking{goals.snapshot(list).entries.single().id}}")
  val row=runBlocking{goals.snapshot(list).entries.single()};assertEquals(product.id,row.productId);assertEquals(shop,row.assignedShopId);assertTrue(runBlocking{db.shoppingListEntryDao().getVisibleSync().isEmpty()})
  ui.onNodeWithTag("goal-entry-${row.id}").performClick();waitText("Название товара");ui.onNodeWithText("Привязанные магазины").performScrollTo().assertExists();shot("normal-product-card-from-list")
 }
 @Test fun cancellingNewProductCardDoesNotAddAnItemOrLoseTheListContext(){
  add();ui.onNodeWithText("Название товара").performTextInput("Новый несохранённый");waitFor{runCatching{ui.onNodeWithTag("add-create-product").assertIsEnabled()}.isSuccess}
  ui.onNodeWithTag("add-create-product").performScrollTo().performClick();waitText("Новый товар");ui.onNodeWithContentDescription("Назад").performClick();waitTag("add-list-contents")
  assertTrue(runBlocking{goals.snapshot(list).entries.isEmpty()});assertEquals(1,runBlocking{db.productDao().getAllSync().size})
 }
 @Test fun quickPositionOpensTheNormalCardAndCanBeKeptInTheCatalogueWithoutDuplicatingPurchase(){
  val row=runBlocking{goals.addQuick(list,"Видеокарта",2.0,"шт",1000.0)};open();ui.onNodeWithTag("goal-entry-$row").performClick();waitText("Название товара")
  ui.onNodeWithText("Сохранить разовую позицию в базе").performScrollTo().performClick();ui.onNodeWithText("Сохранить").performClick();waitTag("goal-entry-$row")
  runBlocking{val saved=goals.snapshot(list);assertEquals(row,saved.entries.single().id);assertEquals(2.0,saved.entries.single().quantity,0.0);assertFalse(saved.products.single().isTemporary);assertEquals(1000.0,saved.entries.single().plannedUnitPrice!!,0.0)}
 }
}
