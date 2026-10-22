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
class Templates240UiTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 @get:Rule val case=org.junit.rules.TestName()
 private val app get()=ApplicationProvider.getApplicationContext<SpisokRyadomApp>()
 private val db get()=app.container.database
 private val goals get()=app.container.goalListRepository
 private val basket get()=app.container.shoppingListRepository
 private var banana=0L;private var apple=0L
 private fun waitFor(c:()->Boolean){try{ui.waitUntil(25_000){Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(20));ui.waitForIdle();c()}}
 catch(e:Throwable){val dir=File("build/reports/refine240/failures");dir.mkdirs();runCatching{File(dir,"${case.methodName}.txt").writeText(ui.onRoot(useUnmergedTree=true).printToString())};runCatching{shot("failure-${case.methodName}")};throw e}}
 private fun waitTag(tag:String)=waitFor{ui.onAllNodesWithTag(tag,useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
 private fun waitText(text:String)=waitFor{ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
 private fun tab(text:String){ui.onNode(hasText(text) and SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.Tab)).performClick()}
 private fun shot(name:String){ui.runOnIdle{val view=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf{it.isShowing}?.window?.decorView?:ui.activity.window.decorView;val b=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(b));val f=File("build/reports/refine240/screens/$name-api${android.os.Build.VERSION.SDK_INT}.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 @Before fun seed(){runBlocking{
  listOf("drafts","shopping_list_prefs","product_db_prefs").forEach{app.getSharedPreferences(it,0).edit().clear().commit()}
  db.shoppingListEntryDao().deleteAll();db.goalListDao().deleteAll();db.productDao().deleteAll();db.productGroupDao().deleteAll();db.shopDao().deleteAll();db.shopDepartmentDao().clearTypes();app.container.locations.select(1)
  banana=db.productDao().insert(ProductEntity(name="Банан",isFavorite=true,defaultQuantity=.5,defaultUnit="кг"))
  apple=db.productDao().insert(ProductEntity(name="Яблоко",defaultUnit="шт"))
 };waitText("Список пуст")}
 @After fun close(){ui.activityRule.scenario.close();app.container.locations.closeForTest();db.close();org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null)}
 private fun openApply(id:Long){tab("Списки");waitTag("goal-lists");ui.onNodeWithTag("goal-lists").performScrollToNode(hasTestTag("goal-list-$id"));ui.onNodeWithContentDescription("Добавить позиции списка: На неделю").performClick();waitTag("list-copy-confirm")}
 @Test fun favoritesFilterHasExplicitBannerAndOneTapToShowAll(){
  app.getSharedPreferences("product_db_prefs",0).edit().putBoolean("favorites_only",true).commit();tab("Товары");waitTag("favorites-filter-active")
  ui.onNodeWithTag("favorites-filter-active").assertIsDisplayed();ui.onNodeWithText("Яблоко").assertDoesNotExist();shot("favorites-active")
  ui.onNodeWithText("Показать все").performClick();waitText("Яблоко");ui.onNodeWithTag("favorites-filter-active").assertDoesNotExist()
  assertFalse(app.getSharedPreferences("product_db_prefs",0).getBoolean("favorites_only",true))
 }
 @Test fun quickFavoritesShowAddedQuantityInBothCopiesAndCanAddMore(){
  ui.onNodeWithContentDescription("Добавить в список").performClick();waitTag("candidate-favorite-$banana")
  ui.onAllNodesWithContentDescription("Добавить: Банан")[0].performClick();waitTag("candidate-added-favorite-$banana")
  ui.onNodeWithTag("candidate-added-favorite-$banana",useUnmergedTree=true).assertIsDisplayed();waitTag("candidate-added-all-$banana")
  ui.onAllNodesWithContentDescription("Добавить ещё: Банан")[0].performClick()
  waitFor{runBlocking{db.shoppingListEntryDao().getVisibleSync().singleOrNull()?.quantity==1.0}}
  waitFor{ui.onAllNodesWithText("В списке · 1 кг").fetchSemanticsNodes().size==2};shot("favorites-added")
  assertTrue(runBlocking{db.productDao().getById(banana)!!.isFavorite})
 }
 @Test fun addedMarkerComesFromRealPendingPurchasesAndClearsAfterBuying(){
  val entry=runBlocking{basket.addProduct(banana,.5,"кг",choice=ShopChoice.NONE)}
  ui.onNodeWithContentDescription("Добавить в список").performClick();waitTag("candidate-added-favorite-$banana")
  runBlocking{basket.markBought(entry)};waitFor{ui.onAllNodesWithTag("candidate-added-favorite-$banana",useUnmergedTree=true).fetchSemanticsNodes().isEmpty()}
  runBlocking{basket.markNotBought(entry)};waitTag("candidate-added-favorite-$banana")
 }
 @Test fun createsOneListFormatAndAddsWithTheSharedPicker(){
  tab("Списки");ui.onNodeWithContentDescription("Создать список").performClick();ui.onNodeWithText("Название списка").performTextInput("Рецепт")
  ui.onNodeWithText("Тип").assertDoesNotExist();ui.onNodeWithText("Сохранить").performClick();waitTag("goal-add-item")
  assertFalse(runBlocking{db.goalListDao().getAllSync().single().isTemplate})
  ui.onNodeWithTag("goal-add-item").performClick();waitTag("add-screen-list");ui.onNodeWithTag("add-list-contents").assertExists()
 }
 @Test fun applicationRequiresConfirmationThenOpensMainAndWidgetSeesCopies(){
  val id=runBlocking{goals.create("На неделю").also{goals.addCatalogue(it,banana,.5,"кг");goals.addQuick(it,"Хлеб",2.0,"шт")}}
  openApply(id);ui.onNodeWithText("Все позиции списка (2) будут добавлены на «Главную» и появятся в виджете. Исходный список сохранится.").assertExists()
  assertTrue(runBlocking{db.shoppingListEntryDao().getVisibleSync().isEmpty()});shot("template-confirm-main")
  ui.onNodeWithTag("list-copy-confirm").performClick();waitFor{runBlocking{db.shoppingListEntryDao().getVisibleSync().size==2}}
  waitText("Список покупок");ui.onNode(hasText("Главная") and SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.Tab)).assertIsSelected()
  assertEquals(2,runBlocking{goals.snapshot(id).entries.size});assertEquals(2,runBlocking{ShoppingWidgetData(app.container).rows().size})
 }
 @Test fun templateCanCreateAnOrdinaryListAndOpenIt(){
  val id=runBlocking{goals.create("На неделю").also{goals.addQuick(it,"Грибы")}}
  openApply(id);ui.onNodeWithText("Новый список").performClick();ui.onNodeWithTag("list-copy-target-name").performTextInput("К выходным")
  ui.onNodeWithTag("list-copy-confirm").performClick();waitText("К выходным");waitTag("goal-open-catalogue")
  assertFalse(runBlocking{db.goalListDao().getAllSync().single{it.name=="К выходным"}.isTemplate});assertTrue(runBlocking{db.shoppingListEntryDao().getVisibleSync().isEmpty()})
 }
 @Test fun currentPurchasesCanBeCapturedAsReusableTemplate(){
  runBlocking{basket.addProduct(banana,1.0,"кг",choice=ShopChoice.NONE)};tab("Списки")
  ui.onNodeWithContentDescription("Сохранить текущие покупки как список").performClick();ui.onNodeWithText("Название списка").performTextInput("Повторить покупки");ui.onNodeWithText("Сохранить").performClick()
  waitTag("list-open-copy");assertEquals(1,runBlocking{db.shoppingListEntryDao().getVisibleSync().size});assertEquals(1,runBlocking{goals.snapshot(db.goalListDao().getAllSync().single().id).entries.size})
 }
 @Test fun cancellingDoesNotAddAnythingAndExistingDestinationKeepsItsOwner(){
  val (id,target)=runBlocking{val id=goals.create("На неделю");goals.addQuick(id,"Хлеб");id to goals.create("В поездку")}
  openApply(id);ui.onNodeWithText("Отмена").performClick();waitFor{ui.onAllNodesWithTag("list-copy-confirm").fetchSemanticsNodes().isEmpty()}
  assertTrue(runBlocking{goals.snapshot(target).entries.isEmpty()})
  ui.onNodeWithContentDescription("Добавить позиции списка: На неделю").performClick();waitTag("list-copy-confirm");ui.onNode(hasText("В поездку") and hasAnyAncestor(hasTestTag("list-copy-sheet"))).performClick();shot("template-confirm-list")
  ui.onNodeWithTag("list-copy-confirm").performClick();waitTag("goal-open-catalogue")
  assertEquals(1,runBlocking{goals.snapshot(target).entries.size});assertEquals(1,runBlocking{goals.snapshot(id).entries.size});assertTrue(runBlocking{db.shoppingListEntryDao().getVisibleSync().isEmpty()})
 }
 @Test fun templateUrgencyEditsAffectFutureCopiesAndKeepExistingPurchases(){
  val (id,row)=runBlocking{val id=goals.create("Срочность шаблона");id to goals.addQuick(id,"Хлеб")}
  tab("Списки");waitTag("goal-list-$id");ui.onNodeWithTag("goal-list-$id").performClick();waitTag("goal-entry-$row")
  ui.onNodeWithContentDescription("Действия: Хлеб").performClick();ui.onNodeWithText("Сделать срочным").performClick()
  waitFor{runBlocking{db.shoppingListEntryDao().getById(row)!!.isUrgent}}
  val target=runBlocking{goals.copyList(id,newListName="Первая копия")!!}
  assertTrue(runBlocking{goals.snapshot(target).entries.single().isUrgent})
  ui.onNodeWithContentDescription("Действия: Хлеб").performClick();ui.onNodeWithText("Убрать срочность").performClick()
  waitFor{runBlocking{!db.shoppingListEntryDao().getById(row)!!.isUrgent}}
  runBlocking{goals.copyList(id)}
  assertFalse(runBlocking{db.shoppingListEntryDao().getVisibleSync().single().isUrgent})
  assertTrue(runBlocking{goals.snapshot(target).entries.single().isUrgent})
  assertFalse(runBlocking{goals.snapshot(id).entries.single().isBought})
  ui.onNodeWithContentDescription("Куплено: Хлеб").assertExists()
 }

}
