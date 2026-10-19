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
class Refine100UiTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 private val app get()=ApplicationProvider.getApplicationContext<SpisokRyadomApp>()
 private val db get()=app.container.database
 private var a=0L;private var b=0L;private var p=0L;private var entry=0L;private var first=0L;private var second=0L
 private fun waitText(t:String){ui.waitUntil(20_000){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(20));ui.waitForIdle();ui.onAllNodesWithText(t).fetchSemanticsNodes().isNotEmpty()}}
 private fun waitTag(t:String){ui.waitUntil(20_000){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(20));ui.onAllNodesWithTag(t).fetchSemanticsNodes().isNotEmpty()}}
 private fun waitDb(c:()->Boolean){ui.waitUntil(20_000){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(20));c()}}
 private fun shot(name:String,sheet:Boolean=false){ui.waitForIdle();ui.runOnIdle{val v=if(sheet)org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView else ui.activity.window.decorView;val bitmap=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(bitmap));val f=File("../.local/refinement-0.10.0/$name-api${android.os.Build.VERSION.SDK_INT}.png");f.parentFile!!.mkdirs();f.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}}
 @Before fun seed(){runBlocking{
  listOf("drafts","product_card_drafts","shopping_list_prefs","product_db_prefs").forEach{app.getSharedPreferences(it,0).edit().clear().commit()}
  db.shoppingListEntryDao().deleteAll();db.productDao().deleteAll();db.productGroupDao().deleteAll();db.shopDao().deleteAll();db.shopDepartmentDao().clearTypes();db.retailStructureDao().clearVenues();db.retailStructureDao().clearNetworks();db.cityDao().update(CityEntity(1,"Красноярск"));app.container.locations.select(1)
  a=db.shopDao().insert(ShopEntity(name="Батон"));b=db.shopDao().insert(ShopEntity(name="Красный Яр",displayOrder=1));first=db.shopBranchDao().insert(ShopBranchEntity(shopId=b,address="Весны, 1"));second=db.shopBranchDao().insert(ShopBranchEntity(shopId=b,address="Молокова, 54",displayOrder=1))
  p=app.container.productRepository.saveWithLinks(ProductEntity(name="Спагетти",defaultUnit="упаковка",preferCityShopOrder=true),listOf(ProductShopLinkEntity(productId=0,shopId=a)))
  db.productDao().insert(ProductEntity(name="Банан"));db.productDao().insert(ProductEntity(name="Банан спелый",isFavorite=true))
  entry=app.container.shoppingListRepository.addProduct(p,quantity=3.0);Unit
 };waitTag("purchase-row-$entry");waitTag("purchase-place-$a:0")}
 @After fun close(){ui.activityRule.scenario.close();app.container.locations.closeForTest();db.close();org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null)}
 private fun move(){waitText("Спагетти");ui.onNodeWithText("Спагетти").performClick();waitText("Сменить место покупки");ui.onNodeWithText("Сменить место покупки").performClick();waitTag("purchase-place-picker")}
 private fun productCard(){waitText("Спагетти");ui.onNodeWithText("Спагетти").performClick();waitText("Карточка товара");ui.onNodeWithText("Карточка товара").performClick();waitText("Название товара")}
 @Test fun emptyDestinationListCanAttachExactOutletAndMoveWithoutLeaving(){
  move();try{waitText("Других мест покупки нет")}catch(e:Throwable){println("PICKER="+ui.onNodeWithTag("purchase-place-picker").printToString());println("DB="+runBlocking{db.shoppingListEntryDao().getAllSync().toString()+db.productShopLinkDao().getAllSync().toString()});throw e};ui.onNodeWithText("Добавить место покупки").performClick();waitTag("place-option-$b:$second");ui.onNodeWithTag("place-option-$b:$second").performClick();shot("01-add-and-move",true);ui.onNodeWithText("Добавить и перенести").performClick()
  waitDb{runBlocking{db.shoppingListEntryDao().getById(entry)!!.assignedBranchId==second}}
  runBlocking{val row=db.shoppingListEntryDao().getById(entry)!!;assertEquals(3.0,row.quantity,0.0);assertEquals(b,row.assignedShopId);assertEquals(2,db.productShopLinkDao().getLinksForProductSync(p).size);assertEquals(3,db.productDao().getAllSync().size)}
 }
 @Test fun newShopCanBeCreatedAndLinkedInsideMove(){
  move();waitText("Других мест покупки нет");ui.onNodeWithText("Добавить место покупки").performClick();waitText("Новый магазин…");ui.onNodeWithText("Новый магазин…").performClick();ui.onNodeWithText("Название магазина").performTextInput("Аллея");ui.onNodeWithContentDescription("Создать: Название магазина").performClick()
  ui.waitUntil(20_000){ui.onAllNodesWithContentDescription("Отмена: Название магазина").fetchSemanticsNodes().isEmpty()};waitText("Аллея");shot("02-new-shop-transfer",true);ui.onNodeWithText("Добавить и перенести").performClick()
  waitDb{runBlocking{val shop=db.shopDao().getAllSync().find{it.name=="Аллея"};shop!=null&&db.shoppingListEntryDao().getById(entry)!!.assignedShopId==shop.id}}
 }
 @Test fun settingRemembersSelectedStoreAndOutletForFuturePurchases(){
  ui.onNodeWithContentDescription("Настройки").performClick();waitText("Настройки");ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Сохранять приоритет магазина при переносе товара"));ui.onNodeWithText("Сохранять приоритет магазина при переносе товара").performClick();shot("03-transfer-setting")
  assertTrue(app.getSharedPreferences("shopping_list_prefs",0).getBoolean("remember_shop_priority_on_move",false));ui.onNodeWithContentDescription("Назад").performClick();move();ui.onNodeWithText("Добавить место покупки").performClick();waitTag("place-option-$b:$second");ui.onNodeWithTag("place-option-$b:$second").performClick();ui.onNodeWithText("Добавить и перенести").performClick()
  waitDb{runBlocking{db.shoppingListEntryDao().getById(entry)!!.assignedBranchId==second}};runBlocking{app.container.shoppingListRepository.removeEntry(entry);val next=app.container.shoppingListRepository.addProduct(p);assertEquals(b,db.shoppingListEntryDao().getById(next)!!.assignedShopId);assertEquals(second,db.shoppingListEntryDao().getById(next)!!.assignedBranchId)}
 }
 @Test fun cardOutletPriorityAutosavesAndUpdatesTheSamePendingPurchase(){
  runBlocking{app.container.productRepository.ensureShopLink(p,b);app.container.shoppingListRepository.movePending(setOf(entry),b,first)}
  productCard();ui.onNodeWithText("Приоритет точек").performScrollTo().performClick();ui.onNodeWithContentDescription("Выше приоритет точки: Молокова, 54").performScrollTo().performClick();shot("04-product-outlet-priority")
  try{waitDb{runBlocking{db.shoppingListEntryDao().getById(entry)!!.assignedBranchId==second}}}catch(e:Throwable){println("DIAGNOSTIC="+runBlocking{db.shopBranchDao().priorities().toString()+db.shoppingListEntryDao().getAllSync().toString()+app.getSharedPreferences("product_card_drafts",0).all.toString()});println(ui.onRoot().printToString());throw e};ui.onNodeWithContentDescription("Назад").performClick();waitText("Список покупок");waitText("Молокова, 54")
  runBlocking{assertEquals(3.0,db.shoppingListEntryDao().getById(entry)!!.quantity,0.0);assertEquals(1,db.shoppingListEntryDao().getAllSync().size)}
 }
 @Test fun addPurchaseSearchShowsStarsWithoutPromotingFavorite(){
  ui.onNodeWithContentDescription("Добавить в список").performClick();waitText("Название товара");ui.onNodeWithText("Название товара").performTextInput("Банан");waitText("Банан спелый");ui.onNodeWithContentDescription("Избранный товар: Банан спелый").assertIsDisplayed();ui.onNodeWithContentDescription("Избранный товар: Банан").assertDoesNotExist()
  assertTrue(ui.onNode(hasText("Банан") and !hasSetTextAction()).getUnclippedBoundsInRoot().top<ui.onNodeWithText("Банан спелый").getUnclippedBoundsInRoot().top);shot("05-search-stars-add")
 }
 @Test fun newProductExistingMatchesShowFavoriteStar(){
  ui.onNodeWithText("Товары").performClick();waitText("База товаров");ui.onNodeWithContentDescription("Новый товар").performClick();waitText("Название товара");ui.onNodeWithText("Название товара").performTextInput("Банан");waitText("Уже в базе");waitText("Банан спелый");ui.onNodeWithContentDescription("Избранный товар: Банан спелый").assertIsDisplayed();shot("06-search-stars-card")
 }
 @Test fun storeDatabasePickerShowsStarOnSearchResult(){
  ui.onAllNodesWithText("Магазины").onLast().performClick();waitText("Батон");ui.onNodeWithText("Батон").performClick();waitText("Спагетти");ui.onNodeWithContentDescription("Добавить товар").performClick();ui.onNodeWithText("Добавить из базы").performClick();waitText("Добавить товары")
  ui.onNode(hasSetTextAction()).performTextInput("Банан");waitText("Банан спелый");ui.onNodeWithContentDescription("Избранный товар: Банан спелый").assertIsDisplayed();shot("07-search-stars-store")
 }
 @Test fun addSearchShowsFavoriteStarInsteadOfRetiredTextMatching(){
  ui.onNodeWithContentDescription("Добавить в список").performClick();waitText("Название товара")
  ui.onNodeWithText("Название товара").performTextInput("Банан");waitText("Банан спелый")
  ui.onNodeWithContentDescription("Избранный товар: Банан спелый").assertIsDisplayed()
 }
}
