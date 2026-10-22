package com.spisokryadom.app

import android.content.Intent
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
import com.spisokryadom.app.ui.shoppinglist.ViewMode
import com.spisokryadom.app.widget.*
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
class Audit270FlowsUiTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 @get:Rule val case=org.junit.rules.TestName()
 private val app get()=ApplicationProvider.getApplicationContext<SpisokRyadomApp>()
 private val db get()=app.container.database
 private val goals get()=app.container.goalListRepository
 private val basket get()=app.container.shoppingListRepository
 private var product=0L;private var shop=0L;private var list=0L
 private var launchIntent:Intent?=null
 private fun waitFor(c:()->Boolean){try{ui.waitUntil(25_000){Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(20));ui.waitForIdle();c()}}
 catch(e:Throwable){val f=File("build/reports/audit270/flows-failures/${case.methodName}.txt");f.parentFile!!.mkdirs();runCatching{f.writeText(ui.onRoot(useUnmergedTree=true).printToString())};throw e}}
 private fun waitTag(tag:String)=waitFor{ui.onAllNodesWithTag(tag,useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
 private fun waitText(text:String)=waitFor{ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
 private fun tab(text:String){ui.onNode(hasText(text) and SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.Tab)).performClick()}
 private fun widgetAdd(){ui.runOnUiThread{org.robolectric.util.ReflectionHelpers.callInstanceMethod<Unit>(ui.activity,"onNewIntent",org.robolectric.util.ReflectionHelpers.ClassParameter.from(Intent::class.java,WidgetIntents.open(app,true)))}}
 private fun openList(){tab("Списки");waitTag("goal-list-$list");ui.onNodeWithTag("goal-list-$list").performClick();waitTag("goal-add-item")}
 private fun shot(name:String){ui.runOnIdle{val view=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf{it.isShowing}?.window?.decorView?:ui.activity.window.decorView;val b=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(b));val f=File("build/reports/audit270/screens/$name-api${android.os.Build.VERSION.SDK_INT}.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 @Before fun seed(){launchIntent=Intent(ui.activity.intent);runBlocking{
  listOf("drafts","product_card_drafts","shopping_list_prefs","product_db_prefs").forEach{app.getSharedPreferences(it,0).edit().clear().commit()}
  app.getSharedPreferences("shopping_list_prefs",0).edit().putBoolean("autosave_product_cards",false).commit()
  db.shoppingListEntryDao().deleteAll();db.goalListDao().deleteAll();db.productDao().deleteAll();db.shopDao().deleteAll();db.productGroupDao().deleteAll();app.container.locations.select(1)
  db.unitDao().deleteAll();listOf("шт","кг","мешок").forEachIndexed{index,name->db.unitDao().insert(UnitEntity(name=name,displayOrder=index))}
  shop=db.shopDao().insert(ShopEntity(name="Батон",shoppingBackground="#DC7090",purchaseBackground="auto",iconKey="bread"))
  product=app.container.productRepository.saveWithLinks(ProductEntity(name="Банан",defaultUnit="шт"),listOf(ProductShopLinkEntity(productId=0,shopId=shop)))
  list=goals.create("Рецепт")
 };waitText("Список пуст")}
 @After fun close(){
  // The synthetic onNewIntent calls bypass ActivityScenario's own launcher. Restore
  // its launch identity before lifecycle matching (same convention as Refine110UiTest).
  ui.runOnUiThread{launchIntent?.let{ui.activity.intent=it}}
  ui.activityRule.scenario.close()
  app.container.locations.closeForTest();db.close();org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null)
 }

 @Test fun widgetAddLeavesTheGoalPickerAndWritesToMainOnly(){
  openList();ui.onNodeWithTag("goal-add-item").performClick();waitTag("add-list-contents")
  widgetAdd();waitText("Добавить покупку");ui.onNodeWithTag("add-list-contents").assertDoesNotExist()
  waitTag("candidate-all-$product");ui.onNodeWithContentDescription("Добавить: Банан").performClick()
  waitFor{runBlocking{db.shoppingListEntryDao().getForListSync(null).size==1}}
  assertTrue(runBlocking{goals.snapshot(list).entries.isEmpty()})
  // Repeated widget '+' in the unscoped Main picker must preserve the user's input.
  ui.onNodeWithText("Название товара").performTextInput("Новая позиция")
  widgetAdd();ui.waitForIdle();ui.onNodeWithText("Название товара").assertTextContains("Новая позиция")
  shot("widget-main-context")
 }
 @Test fun widgetAddAlsoLeavesALockedShopPicker(){
  runBlocking{basket.addProduct(product,choice=ShopChoice.EXPLICIT,shopId=shop)}
  waitTag("purchase-row-${runBlocking{db.shoppingListEntryDao().getAllSync().single().id}}")
  ui.onNodeWithContentDescription("Действия магазина: Батон").performClick();ui.onNodeWithText("Добавить покупку").performClick();waitText("Название товара")
  ui.onNodeWithText("Магазин").assertIsNotEnabled()
  widgetAdd();waitText("Подобрать автоматически");ui.onNodeWithText("Магазин").assertIsEnabled()
  assertEquals(1,runBlocking{db.shoppingListEntryDao().getAllSync().size})
 }
 @Test fun shopFavoritesRepeatAtTheTopAndCanSelectFromEitherProjection(){
  val other=runBlocking{db.shopDao().insert(ShopEntity(name="Аллея",isFavorite=true,favoriteOrder=1))}
  runBlocking{db.shopDao().update(db.shopDao().getById(shop)!!.copy(isFavorite=true,favoriteOrder=0))}
  widgetAdd();waitText("Название товара");ui.onNodeWithText("Магазин").performClick();waitTag("shop-choice-favorite-$shop")
  val top=ui.onNodeWithTag("shop-choice-favorite-$shop").fetchSemanticsNode().boundsInRoot.top
  assertTrue(top<ui.onNodeWithTag("shop-choice-favorite-$other").fetchSemanticsNode().boundsInRoot.top)
  ui.onNodeWithTag("shop-choice-all-$shop").assertExists();shot("favorite-shop-picker")
  ui.onNodeWithTag("shop-choice-favorite-$shop").performClick();ui.onNodeWithText("Батон").assertExists()
  ui.onNodeWithText("Магазин").performClick();ui.onNodeWithTag("shop-choice-all-$other").performClick();ui.onNodeWithText("Аллея").assertExists()
 }
 @Test fun listMetadataCancelDoesNotWriteAndGoalUnitsUseTheSharedUnfilteredSelector(){
  openList();ui.onNodeWithContentDescription("Действия со списком").performClick();ui.onNodeWithText("Настройки списка").performClick();waitTag("editor-sheet")
  ui.onNodeWithText("Название списка").performTextReplacement("Не сохранять")
  ui.onNodeWithText("Отображать на Главной").performClick();ui.onNodeWithText("Отмена").performClick()
  assertEquals("Рецепт",runBlocking{goals.getList(list)!!.name});assertFalse(runBlocking{goals.getList(list)!!.showInPurchases})
  ui.onNodeWithContentDescription("Действия со списком").performClick();ui.onNodeWithText("Быстрая позиция с ценой").performClick();waitTag("goal-item-unit")
  ui.onNodeWithTag("goal-item-name").performTextInput("Грибы");ui.onNodeWithTag("goal-item-unit").performTextInput("шт")
  waitFor{ui.onAllNodesWithText("кг").fetchSemanticsNodes().isNotEmpty()};ui.onNodeWithText("кг").performClick()
  ui.onNodeWithTag("goal-item-unit").assertTextContains("кг");shot("goal-unit-picker")
  ui.onNodeWithTag("goal-item-done").performClick();waitFor{runBlocking{goals.snapshot(list).entries.singleOrNull()?.unit=="кг"}}
 }
 @Test fun mainAndWidgetProductGroupsShareTheAlphabetInsteadOfLegacyManualOrder(){
  runBlocking{
   val last=db.productGroupDao().insert(ProductGroupEntity(name="Ягоды",displayOrder=-100,isFavorite=true,favoriteOrder=0))
   val first=db.productGroupDao().insert(ProductGroupEntity(name="Ёлочные украшения",displayOrder=100))
   val a=db.productDao().insert(ProductEntity(name="Яблоко",productGroupId=last))
   val b=db.productDao().insert(ProductEntity(name="Игрушка",productGroupId=first))
   basket.addProduct(a,choice=ShopChoice.NONE);basket.addProduct(b,choice=ShopChoice.NONE)
   app.getSharedPreferences("shopping_list_prefs",0).edit().putString("shopping_list_view_mode",ViewMode.BY_GROUPS.name).commit()
  }
  waitText("Ягоды");waitText("Ёлочные украшения")
  assertTrue(ui.onNodeWithText("Ёлочные украшения").fetchSemanticsNode().boundsInRoot.top<ui.onNodeWithText("Ягоды").fetchSemanticsNode().boundsInRoot.top)
  assertEquals(listOf("Ёлочные украшения","Ягоды"),runBlocking{ShoppingWidgetData(app.container).presentation(WidgetSettings(mode=ViewMode.BY_GROUPS)).filterIsInstance<WidgetItem.Header>().map{it.title}})
 }
 @Test fun goalListUsesShopZonesAndDepartmentPriorityWithoutSplittingBoughtRowsIntoAnotherShop(){
  val rows=runBlocking{
   val dairy=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=shop,name="Молочка",displayOrder=0))
   val fruit=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=shop,name="Фрукты",displayOrder=1))
   db.productShopLinkDao().update(db.productShopLinkDao().getLinksForProductSync(product).single().copy(departmentId=fruit))
   val milk=app.container.productRepository.saveWithLinks(ProductEntity(name="Молоко",defaultUnit="л"),listOf(ProductShopLinkEntity(productId=0,shopId=shop,departmentId=dairy)))
   val bananaRow=app.container.shoppingListRepository.addGoalCatalogueFixture(list,product,choice=ShopChoice.EXPLICIT,shopId=shop)
   val milkRow=app.container.shoppingListRepository.addGoalCatalogueFixture(list,milk,choice=ShopChoice.EXPLICIT,shopId=shop)
   goals.setBought(bananaRow,true,expectedListId=list)
   bananaRow to milkRow
  }
  openList();waitTag("goal-entry-${rows.first}");waitText("Молочка");waitText("Фрукты")
  ui.onNodeWithText("Батон").assertExists();assertEquals(1,ui.onAllNodesWithText("Батон").fetchSemanticsNodes().size)
  assertTrue(ui.onNodeWithText("Молочка").fetchSemanticsNode().boundsInRoot.top<ui.onNodeWithText("Фрукты").fetchSemanticsNode().boundsInRoot.top)
  shot("goal-shop-zone")
  runBlocking{db.shopDepartmentDao().update(db.shopDepartmentDao().getByShopIdSync(shop).first().copy(name="Новая молочка"))}
  waitText("Новая молочка")
 }
 @Test fun successfulFullRestoreReturnsToMainAndRecreatesEditorsInSavedTabs(){
  val backup=app.container.backupManager
  val archive=runBlocking{backup.exportFile()}
  try{
   // Keep a list-tab model in Navigation's saved back stack before the full replacement.
   tab("Списки");waitTag("goal-list-$list");tab("Главная");waitText("Список пуст")
   runBlocking{goals.updateList(list,"До восстановления",false)}
   ui.onNodeWithContentDescription("Настройки").performClick();waitTag("settings-list")
   ui.onNodeWithText("Импорт / Восстановление").performScrollTo().performClick()
   val shadow=Shadows.shadowOf(ui.activity);val request=shadow.nextStartedActivityForResult
   assertEquals(Intent.ACTION_OPEN_DOCUMENT,request.intent.action)
   ui.runOnUiThread{shadow.receiveResult(request.intent,android.app.Activity.RESULT_OK,Intent().setData(android.net.Uri.fromFile(archive)))}
   waitText("Восстановление");ui.onNodeWithText("Да").performClick()
   waitText("Список покупок");ui.onNodeWithTag("settings-list").assertDoesNotExist()
   assertEquals("Рецепт",runBlocking{goals.getList(list)!!.name})
   // A restored tab must have a fresh sessionGeneration, so it can create a new list.
   tab("Списки");waitTag("goal-list-$list");ui.onNodeWithContentDescription("Создать список").performClick()
   ui.onNodeWithText("Название списка").performTextInput("После восстановления");ui.onNodeWithText("Сохранить").performClick()
   waitText("После восстановления");waitTag("goal-add-item")
   assertTrue(runBlocking{db.goalListDao().getAllSync().any{it.name=="После восстановления"}})
   shot("restore-fresh-list-session")
  }finally{backup.discard(archive)}
 }
 @Test fun widgetRespectsIndependentFavoriteStarAndBoldAndResetsReusedRows(){
  val rowId=runBlocking{db.productDao().update(db.productDao().getById(product)!!.copy(isFavorite=true));basket.addProduct(product)}
  val prefs=app.getSharedPreferences(WidgetSettings.PREFS,0)
  WidgetSettings(mode=ViewMode.FLAT,showProductBackground=false).save(prefs)
  com.spisokryadom.app.util.FavoriteAppearance(purchaseRow=com.spisokryadom.app.util.FavoriteStyle(star=true,bold=true)).save(prefs)
  val factory=ShoppingWidgetFactory(app);factory.onCreate()
  try{
   fun row()=factory.getViewAt((0 until factory.count).single{factory.getItemId(it)==rowId})!!
   val host=android.widget.FrameLayout(ui.activity)
   val rendered=row().apply(ui.activity,host)
   val title=rendered.findViewById<android.widget.TextView>(R.id.widget_item_title)
   assertEquals("★ Банан",title.text.toString());assertTrue(title.paintFlags and android.graphics.Paint.FAKE_BOLD_TEXT_FLAG!=0)
   // Star and weight are separate switches, both independent of the widget background.
   com.spisokryadom.app.util.FavoriteAppearance(purchaseRow=com.spisokryadom.app.util.FavoriteStyle(star=false,bold=true)).save(prefs)
   factory.onDataSetChanged();row().reapply(ui.activity,rendered)
   assertEquals("Банан",title.text.toString());assertTrue(title.paintFlags and android.graphics.Paint.FAKE_BOLD_TEXT_FLAG!=0)
   runBlocking{basket.markBought(rowId)}
   factory.onDataSetChanged();row().reapply(ui.activity,rendered)
   assertTrue(title.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG!=0)
   runBlocking{db.productDao().update(db.productDao().getById(product)!!.copy(isFavorite=false));basket.markNotBought(rowId)}
   factory.onDataSetChanged();row().reapply(ui.activity,rendered)
   assertEquals("Банан",title.text.toString());assertEquals(0,title.paintFlags and (android.graphics.Paint.FAKE_BOLD_TEXT_FLAG or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG))
   assertEquals(listOf(rowId),runBlocking{db.shoppingListEntryDao().getAllSync().map{it.id}})
  }finally{factory.onDestroy()}
 }
 @Test fun goalListShowsOneVenueParentAndDistinctRetailersAndPavilions(){
  runBlocking{
   val venue=db.retailStructureDao().insertVenue(VenueEntity(cityId=1,name="ТЦ Зелёный",address="Мира, 10",kind=VenueKinds.MALL))
   val point=db.shopBranchDao().insert(ShopBranchEntity(shopId=shop,address="",venueId=venue,label="Павильон 1"))
   val second=db.shopDao().insert(ShopEntity(name="Фикс Прайс",displayOrder=1))
   val otherPoint=db.shopBranchDao().insert(ShopBranchEntity(shopId=second,address="",venueId=venue,label="Павильон 2"))
   val p=app.container.productRepository.saveWithLinks(ProductEntity(name="Пакет"),listOf(ProductShopLinkEntity(productId=0,shopId=second)))
   app.container.shoppingListRepository.addGoalCatalogueFixture(list,product,choice=ShopChoice.EXPLICIT,shopId=shop,branchId=point)
   app.container.shoppingListRepository.addGoalCatalogueFixture(list,p,choice=ShopChoice.EXPLICIT,shopId=second,branchId=otherPoint)
  }
  openList();waitText("ТЦ Зелёный");waitText("Фикс Прайс");waitText("Павильон 2")
  assertEquals(1,ui.onAllNodesWithText("ТЦ Зелёный").fetchSemanticsNodes().size)
  assertEquals(1,ui.onAllNodesWithText("Мира, 10").fetchSemanticsNodes().size)
  ui.onNodeWithText("Батон").assertExists();ui.onNodeWithText("Павильон 1").assertExists();shot("goal-venue-hierarchy")
 }
 @Test fun failedInlineShopCreationKeepsTextAndReleasesEditAndCancelAfterStaleSession(){
  widgetAdd();waitText("Название товара");ui.onNodeWithText("Магазин").performClick();ui.onNodeWithText("Новый магазин…").performClick()
  ui.onNodeWithText("Название магазина").performTextInput("Не создавать")
  // Full replacement advances this same guard; keep the open picker to test rejection recovery.
  com.spisokryadom.app.data.repository.ProductEditorTransactions.generation.incrementAndGet()
  ui.onNodeWithContentDescription("Создать: Название магазина").performClick();waitTag("shop-create-error")
  ui.onNodeWithTag("shop-create-error").assertTextContains("База восстановлена",substring=true)
  assertEquals(1,ui.onAllNodesWithText("База восстановлена",substring=true).fetchSemanticsNodes().size)
  ui.onNodeWithText("Название магазина").assertTextContains("Не создавать").assertIsEnabled()
  ui.onNodeWithContentDescription("Отмена: Название магазина").assertIsEnabled()
  ui.onNodeWithText("Название магазина").performTextReplacement("Текст сохранён")
  assertEquals(listOf(shop),runBlocking{db.shopDao().getAllSync().map{it.id}})
  shot("inline-create-error-recovery")
  ui.onNodeWithContentDescription("Отмена: Название магазина").performClick();ui.onNodeWithText("Название магазина").assertDoesNotExist()
  assertEquals(1,ui.onAllNodesWithText("База восстановлена",substring=true).fetchSemanticsNodes().size)
 }
 @Test fun successfulInlineShopCreationSelectsTheNewShopAndClosesOnlyItsEditor(){
  widgetAdd();waitText("Название товара");ui.onNodeWithText("Название товара").performTextInput("Будущая покупка")
  ui.onNodeWithText("Магазин").performClick();ui.onNodeWithText("Новый магазин…").performClick()
  ui.onNodeWithText("Название магазина").performTextInput("Новый магазин")
  ui.onNodeWithContentDescription("Создать: Название магазина").performClick()
  waitFor{ui.onAllNodesWithText("Название магазина").fetchSemanticsNodes().isEmpty()}
  ui.onNodeWithText("Магазин").assertTextContains("Новый магазин");ui.onNodeWithText("Название товара").assertTextContains("Будущая покупка")
  assertEquals(2,runBlocking{db.shopDao().getAllSync().size});assertTrue(runBlocking{db.shoppingListEntryDao().getAllSync().isEmpty()})
 }
}
