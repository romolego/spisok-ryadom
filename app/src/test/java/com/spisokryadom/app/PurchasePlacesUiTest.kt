package com.spisokryadom.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.ShopChoice
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=SpisokRyadomApp::class,qualifiers="w360dp-h640dp-xhdpi")
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PurchasePlacesUiTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 private val app get()=ApplicationProvider.getApplicationContext<SpisokRyadomApp>()
 private val db get()=app.container.database
 private var yar=0L;private var baton=0L;private var near=0L;private var far=0L
 private var cheese=0L;private var tea=0L;private var first=0L;private var second=0L
 private fun waitText(t:String){ui.waitUntil(20_000){ui.onAllNodesWithText(t).fetchSemanticsNodes().isNotEmpty()}}
 private fun waitDescription(t:String){ui.waitUntil(20_000){ui.onAllNodesWithContentDescription(t).fetchSemanticsNodes().isNotEmpty()}}
 private fun waitPlacesReady(){
  waitDescription("Свернуть: Красный Яр · Весны, 1 (Взлётка плаза)")
  waitDescription("Свернуть: Красный Яр · Молокова, 54")
  waitText("Зелёный чай");ui.waitForIdle()
 }
 private fun shot(name:String,sheet:Boolean=false){ui.runOnIdle{
  val view=if(sheet)org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView else ui.activity.window.decorView
  val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))
  val file=File("../.local/ux-0.6.1/$name-api${android.os.Build.VERSION.SDK_INT}.png");file.parentFile!!.mkdirs();file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
 }}
 @Before fun setup(){runBlocking{
  listOf("drafts","shopping_list_prefs","product_db_prefs").forEach{app.getSharedPreferences(it,0).edit().clear().commit()}
  db.shoppingListEntryDao().deleteAll();db.productDao().deleteAll();db.productGroupDao().deleteAll();db.shopDao().deleteAll();db.shopDepartmentDao().clearTypes();db.retailStructureDao().clearVenues();db.retailStructureDao().clearNetworks()
  db.cityDao().update(CityEntity(1,"Красноярск"));app.container.locations.select(1)
  yar=db.shopDao().insert(ShopEntity(name="Красный Яр"));baton=db.shopDao().insert(ShopEntity(name="Батон",displayOrder=1))
  near=db.shopBranchDao().insert(ShopBranchEntity(shopId=yar,address="Весны, 1 (Взлётка плаза)"))
  far=db.shopBranchDao().insert(ShopBranchEntity(shopId=yar,address="Молокова, 54",displayOrder=1))
  cheese=app.container.productRepository.saveWithLinks(ProductEntity(name="Сыр адыгейский",defaultUnit="кг",note="Карточка сыра"),listOf(ProductShopLinkEntity(productId=0,shopId=yar)))
  tea=app.container.productRepository.saveWithLinks(ProductEntity(name="Зелёный чай",defaultUnit="уп"),listOf(ProductShopLinkEntity(productId=0,shopId=yar)))
  first=app.container.shoppingListRepository.addProduct(cheese,2.0,choice=ShopChoice.EXPLICIT,shopId=yar,branchId=near)
  second=app.container.shoppingListRepository.addProduct(tea,1.0,choice=ShopChoice.EXPLICIT,shopId=yar,branchId=far)
 }
  if(ui.activity.resources.configuration.screenHeightDp<400){
   ui.waitUntil(20_000){runCatching{ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Сыр адыгейский"));true}.getOrDefault(false)}
  }
  waitText("Сыр адыгейский");ui.waitForIdle()}
 @After fun close(){ui.mainClock.autoAdvance=true;ui.activityRule.scenario.close();app.container.locations.closeForTest();db.close();org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null)}

 @Test fun eachPhysicalPlaceHasItsOwnHeaderAndAddressNeverLeaksIntoProductRows(){
  waitPlacesReady()
  ui.onAllNodesWithText("Красный Яр").assertCountEquals(2)
  ui.onNodeWithText("Весны, 1 (Взлётка плаза)").assertIsDisplayed();ui.onNodeWithText("Молокова, 54").assertIsDisplayed()
  ui.onAllNodesWithText("Весны, 1 (Взлётка плаза)").assertCountEquals(1)
  ui.onNode(hasText("Весны",substring=true) and hasAnyAncestor(hasTestTag("purchase-row-$first"))).assertDoesNotExist()
  ui.onNodeWithContentDescription("Переместить: Сыр адыгейский").assertDoesNotExist();shot("01-physical-shops")
  ui.onNodeWithContentDescription("Действия магазина: Красный Яр · Молокова, 54").performClick()
  ui.onNodeWithContentDescription("Выбрать некупленные: Красный Яр · Молокова, 54").performClick()
  ui.onNodeWithText("Выбрано: 1").assertIsDisplayed()
 }

 @Test fun changePlaceUsesAvailabilityAndCopyOnlyAddsACatalogueLink(){
  val before=runBlocking{db.shoppingListEntryDao().getById(first)!!}
  ui.onNodeWithText("Сыр адыгейский").performClick();waitText("Сменить место покупки");shot("02-purchase-actions",true)
  ui.onNodeWithText("Сменить место покупки").performClick();ui.waitUntil(20_000){ui.onAllNodesWithTag("place-option-$yar:$far").fetchSemanticsNodes().isNotEmpty()}
  ui.onNode(hasText("Батон") and hasAnyAncestor(hasTestTag("purchase-place-picker"))).assertDoesNotExist();ui.onNodeWithTag("place-option-$yar:$near").assertDoesNotExist()
  shot("03-available-purchase-places",true)
  ui.onNodeWithTag("place-option-$yar:$far").performClick();ui.onNodeWithText("Сменить").performClick()
  ui.waitUntil(20_000){runBlocking{db.shoppingListEntryDao().getById(first)!!.assignedBranchId==far}}
  runBlocking { val actual=db.shoppingListEntryDao().getById(first)!!;assertEquals(before.copy(assignedBranchId=far,updatedAt=actual.updatedAt),actual) }
  waitText("Сыр адыгейский");ui.onNodeWithText("Сыр адыгейский").performClick();waitText("Добавить магазин к товару")
  ui.onNodeWithText("Добавить магазин к товару").performClick();waitText("Батон");shot("04-copy-catalogue-link",true)
  val entries=runBlocking{db.shoppingListEntryDao().getAllSync()};val product=runBlocking{db.productDao().getById(cheese)!!}
  ui.onNodeWithText("Батон").performClick()
  ui.waitUntil(20_000){runBlocking{db.productShopLinkDao().getLinksForProductSync(cheese).size==2}}
  runBlocking{assertEquals(entries,db.shoppingListEntryDao().getAllSync());assertEquals(product,db.productDao().getById(cheese));assertEquals(2,db.productDao().getAllSync().size)}
 }

 @Test fun collapseMovesNeighbourGraduallyAndRemembersEachAddressIndependently(){
  waitPlacesReady()
  fun y()=ui.onNodeWithTag("purchase-place-$yar:$far").fetchSemanticsNode().boundsInRoot.top
  val before=y();ui.mainClock.autoAdvance=false
  try {
   ui.onNodeWithContentDescription("Свернуть: Красный Яр · Весны, 1 (Взлётка плаза)").performClick()
   ui.waitUntil(20_000){ui.mainClock.advanceTimeByFrame();ui.onAllNodesWithContentDescription("Развернуть: Красный Яр · Весны, 1 (Взлётка плаза)").fetchSemanticsNodes().isNotEmpty()}
   ui.mainClock.advanceTimeByFrame();ui.waitForIdle();ui.mainClock.advanceTimeBy(112);ui.waitForIdle();val middle=y()
   ui.mainClock.advanceTimeBy(400);ui.waitForIdle();val after=y()
   assertTrue("Neighbour must slide, before=$before middle=$middle after=$after",middle<before&&middle>after)
   File("../.local/ux-0.6.1/collapse-api${android.os.Build.VERSION.SDK_INT}.json").writeText("{\"beforeY\":$before,\"middleY\":$middle,\"afterY\":$after}")
   ui.onNodeWithText("Сыр адыгейский").assertDoesNotExist();ui.onNodeWithText("Зелёный чай").assertIsDisplayed();shot("06-collapse-settled")
  } finally {ui.mainClock.autoAdvance=true}
  ui.activityRule.scenario.recreate();waitDescription("Развернуть: Красный Яр · Весны, 1 (Взлётка плаза)")
  ui.onNodeWithText("Сыр адыгейский").assertDoesNotExist();ui.onNodeWithText("Зелёный чай").assertIsDisplayed()
  ui.onNodeWithContentDescription("Развернуть: Красный Яр · Весны, 1 (Взлётка плаза)").performClick();waitText("Сыр адыгейский")
 }

 @Test fun availabilityCheckboxHasAnExplicitGapAndOneWholeRowTouchTarget(){
  runBlocking {val link=db.productShopLinkDao().getLinksForProductSync(cheese).single();db.productShopLinkDao().update(link.copy(restrictBranches=true));db.shopBranchDao().insertAvailability(ProductBranchAvailabilityEntity(cheese,near))}
  ui.onNodeWithText("Товары").performClick();waitText("База товаров");waitText("Сыр адыгейский")
  ui.onNodeWithText("Сыр адыгейский").performTouchInput{longClick()};waitText("Название товара")
  ui.onNodeWithTag("branch-choice-$far-label",useUnmergedTree=true).performScrollTo()
  val control=ui.onNodeWithTag("branch-choice-$far-control",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
  val label=ui.onNodeWithTag("branch-choice-$far-label",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
  assertTrue("12dp gap must separate the control slot and text",label.left-control.right>=23f)
  ui.onNodeWithText("Молокова, 54").performClick();shot("07-checkbox-spacing")
  ui.onNodeWithText("Сохранить").performClick();waitText("База товаров")
  runBlocking{assertEquals(setOf(near,far),db.shopBranchDao().availability(cheese).map{it.branchId}.toSet())}
 }

 @Test fun missingNetworkCreatesInlineWithoutAnEmptyPopupAndCancelDoesNotWrite(){
  ui.onAllNodesWithText("Магазины").onLast().performClick();waitText("Красный Яр")
  ui.onNodeWithText("Красный Яр").performTouchInput{longClick()};waitText("Название магазина")
  ui.onNodeWithText("Сеть").assertDoesNotExist()
  ui.onNodeWithText("Править").performScrollTo().performClick();ui.onNodeWithText("Сеть").performScrollTo().performClick();ui.onNodeWithText("Новая сеть…").performClick();waitText("Название сети")
  ui.onNodeWithText("Название сети").performTextReplacement("Красный Яр")
  ui.onAllNodes(isPopup()).assertCountEquals(0);shot("08-optional-network-inline")
  ui.onNodeWithText("Отмена нового названия").performScrollTo().performClick();ui.onNodeWithText("Название сети").assertDoesNotExist()
  runBlocking{assertTrue(db.retailStructureDao().networks().isEmpty());assertNull(db.shopDao().getById(yar)!!.networkId)}
  ui.onNodeWithText("Сеть").performScrollTo().performClick();ui.onNodeWithText("Новая сеть…").performClick();ui.onNodeWithText("Название сети").performTextReplacement("Красный Яр")
  ui.onNodeWithText("Готово").performScrollTo().performClick();shot("09-optional-network-summary")
  ui.onNodeWithText("Сохранить").performClick();waitText("Магазины")
  runBlocking{assertEquals(1,db.retailStructureDao().networks().size);assertNotNull(db.shopDao().getById(yar)!!.networkId);assertEquals(2,db.shopBranchDao().forShop(yar).size)}
 }

 @Test fun addFromAPlaceHeaderRetainsThatExactPhysicalPoint(){
  waitPlacesReady()
  ui.onNodeWithContentDescription("Действия магазина: Красный Яр · Молокова, 54").performClick()
  ui.onNodeWithContentDescription("Добавить товар в Красный Яр · Молокова, 54").performClick();waitText("Добавить покупку")
  ui.onNodeWithText("Название товара").performTextReplacement("Сыр адыгейский");waitText("Сыр адыгейский")
  val candidate=hasText("Сыр адыгейский") and hasClickAction() and !hasSetTextAction()
  ui.waitUntil(20_000){ui.onAllNodes(candidate).fetchSemanticsNodes().isNotEmpty()}
  ui.onNode(candidate).performClick();waitText("Выбран");shot("10-add-at-exact-place")
  ui.onNodeWithText("Добавить в список").performScrollTo().performClick()
  waitText("Список покупок")
  runBlocking{assertTrue(db.shoppingListEntryDao().getAllSync().any{it.productId==cheese&&it.assignedBranchId==far})}
  runBlocking{assertEquals(near,db.shoppingListEntryDao().getById(first)!!.assignedBranchId);assertEquals(3,db.shoppingListEntryDao().getAllSync().size)}
 }
 @Test fun legacyCollapsedRetailerPreferenceCanBeOverriddenForOneAddressOnly(){
  app.getSharedPreferences("shopping_list_prefs",0).edit().putStringSet("shopping_list_collapsed_shops",setOf(yar.toString())).commit()
  waitDescription("Развернуть: Красный Яр · Весны, 1 (Взлётка плаза)")
  waitDescription("Развернуть: Красный Яр · Молокова, 54")
  ui.onNodeWithText("Сыр адыгейский").assertDoesNotExist();ui.onNodeWithText("Зелёный чай").assertDoesNotExist()
  ui.onNodeWithContentDescription("Развернуть: Красный Яр · Молокова, 54").performClick();waitText("Зелёный чай")
  ui.activityRule.scenario.recreate();waitText("Зелёный чай")
  ui.onNodeWithText("Сыр адыгейский").assertDoesNotExist();ui.onNodeWithContentDescription("Развернуть: Красный Яр · Весны, 1 (Взлётка плаза)").assertExists()
 }
 @Test @Config(sdk=[35],qualifiers="w640dp-h360dp-xhdpi")
 fun aLongPlaceListInAShortWindowKeepsTheConfirmationVisible(){
  runBlocking{repeat(12){i->
   val shop=db.shopDao().insert(ShopEntity(name="Магазин $i"))
   db.shopBranchDao().insert(ShopBranchEntity(shopId=shop,address="Адрес $i"))
   app.container.productRepository.ensureShopLink(cheese,shop)
  }}
  ui.onNodeWithText("Сыр адыгейский").performScrollTo().performClick();waitText("Сменить место покупки")
  ui.onNodeWithText("Сменить место покупки").performScrollTo().performClick()
  ui.waitUntil(20_000){ui.onAllNodesWithTag("place-option-$yar:$far").fetchSemanticsNodes().isNotEmpty()}
  ui.onNodeWithTag("place-option-$yar:$far").performClick()
  ui.onNodeWithText("Сменить").assertIsDisplayed();shot("12-short-place-picker",true)
  ui.onNodeWithText("Сменить").performClick()
  ui.waitUntil(20_000){ui.onAllNodesWithTag("purchase-place-picker").fetchSemanticsNodes().isEmpty()}
  runBlocking{assertEquals(far,db.shoppingListEntryDao().getById(first)!!.assignedBranchId)}
 }
 @Test @Config(sdk=[35]) fun movingAnUrgentPointDownKeepsTheExplicitOrder(){
  waitPlacesReady()
  runBlocking{app.container.shoppingListRepository.toggleUrgent(first)}
  waitDescription("Срочно")
  ui.onNodeWithContentDescription("Действия магазина: Красный Яр · Весны, 1 (Взлётка плаза)").performClick()
  ui.onNodeWithContentDescription("Место ниже: Красный Яр · Весны, 1 (Взлётка плаза)").performClick()
  ui.waitUntil(20_000){
   val a=ui.onNodeWithTag("purchase-place-$yar:$near").fetchSemanticsNode().boundsInRoot.top
   val b=ui.onNodeWithTag("purchase-place-$yar:$far").fetchSemanticsNode().boundsInRoot.top
   b<a
  }
  runBlocking{assertTrue(db.shopDao().getById(yar)!!.manualOrderOverride);assertEquals(near,db.shoppingListEntryDao().getById(first)!!.assignedBranchId)}
 }
}
