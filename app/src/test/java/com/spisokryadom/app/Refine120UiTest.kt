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
import org.robolectric.Shadows
import org.robolectric.annotation.*
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=SpisokRyadomApp::class,qualifiers="w360dp-h640dp-xhdpi")
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Refine120UiTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 private val app get()=ApplicationProvider.getApplicationContext<SpisokRyadomApp>()
 private val db get()=app.container.database
 private var p=0L;private var q=0L;private var a=0L;private var b=0L;private var c=0L;private var roma=0L;private var type=0L;private var entry=0L
 private fun waitFor(condition:()->Boolean){ui.waitUntil(20_000){Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(20));ui.waitForIdle();condition()}}
 private fun waitText(t:String)=waitFor{ui.onAllNodesWithText(t).fetchSemanticsNodes().isNotEmpty()}
 private fun click(t:String){waitText(t);val node=if(t in listOf("Магазины","Товары","Главная"))ui.onNode(hasText(t) and SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role,androidx.compose.ui.semantics.Role.Tab))else ui.onNodeWithText(t);waitFor{runCatching{node.assertIsEnabled()}.isSuccess};if(runCatching{node.assertIsDisplayed()}.isFailure)node.performScrollTo();node.performClick()}
 private fun shot(name:String){ui.waitForIdle();ui.runOnIdle{val v=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf{it.isShowing}?.window?.decorView?:ui.activity.window.decorView;val bitmap=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(bitmap));val file=File("../.local/refinement-0.12.0/$name-api${android.os.Build.VERSION.SDK_INT}.png");file.parentFile!!.mkdirs();file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}}
 @Before fun setup(){runBlocking{
  listOf("drafts","product_card_drafts","shopping_list_prefs","product_db_prefs").forEach{app.getSharedPreferences(it,0).edit().clear().commit()}
  db.shoppingListEntryDao().deleteAll();db.productDao().deleteAll();db.productGroupDao().deleteAll();db.shopDao().deleteAll();db.shopDepartmentDao().clearTypes();db.retailStructureDao().clearVenues();app.container.locations.select(1)
  db.recipientDao().getAllSync().forEach{db.recipientDao().delete(it)};roma=db.recipientDao().insert(RecipientEntity(name="Рома"))
  a=db.shopDao().insert(ShopEntity(name="Батон"));b=db.shopDao().insert(ShopEntity(name="Красный Яр",displayOrder=1));c=db.shopDao().insert(ShopEntity(name="Аптека",displayOrder=2));type=db.shopDepartmentDao().ensureType("Напитки")
  p=app.container.productRepository.saveWithLinks(ProductEntity(name="Банан",defaultUnit="шт",isFavorite=true),listOf(ProductShopLinkEntity(productId=0,shopId=a),ProductShopLinkEntity(productId=0,shopId=b,priority=2)))
  q=app.container.productRepository.saveWithLinks(ProductEntity(name="Газировка",defaultUnit="бутылка"),listOf(ProductShopLinkEntity(productId=0,shopId=a)))
  entry=app.container.shoppingListRepository.addProduct(p,choice=ShopChoice.EXPLICIT,shopId=a);app.container.shoppingListRepository.addProduct(q,choice=ShopChoice.EXPLICIT,shopId=a);Unit
 };waitFor{ui.onAllNodesWithTag("purchase-place-$a:0").fetchSemanticsNodes().isNotEmpty()}}
 @After fun close(){ui.activityRule.scenario.close();app.container.locations.closeForTest();db.close();org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null)}
 private fun catalogue(){click("Товары");waitText("База товаров");waitFor{runCatching{ui.onNodeWithContentDescription("Выбрать товары").assertIsEnabled()}.isSuccess}}
 private fun actions(){ui.onNodeWithContentDescription("Действия с выбранными").performClick()}
 @Test fun wholeRecipientSectionAssignsSixtyItemsEvenWhenCollapsed(){
  runBlocking{repeat(58){db.productDao().insert(ProductEntity(name="Товар $it"))}}
  catalogue();click("Получатели");waitText("Без получателя")
  ui.onNodeWithContentDescription("Свернуть: Без получателя").performClick()
  ui.onNodeWithContentDescription("Выбрать товары").performClick();ui.onNodeWithContentDescription("Выбрать раздел: Без получателя").performClick();waitText("Выбрано: 60");shot("01-recipient-selection")
  actions();click("Назначить получателя");click("Рома");shot("02-recipient-action");click("Применить к 60");waitText("База товаров")
  runBlocking{assertEquals(60,db.productDao().getAllSync().count{it.recipientId==roma})}
 }
 @Test fun favoriteShortcutAndShopDuplicatesAreOneLogicalSelection(){
  catalogue();ui.onNodeWithContentDescription("Выбрать товары").performClick();ui.onNodeWithContentDescription("Выбрать раздел: Избранное").performClick();waitText("Выбрано: 1")
  ui.onNodeWithContentDescription("Выбрать все").performClick();waitText("Выбрано: 2");actions();click("Добавить магазины к товарам");click("Красный Яр");click("Применить к 2");waitText("База товаров")
  runBlocking{assertEquals(4,db.productShopLinkDao().getAllSync().size)}
 }
 @Test fun changingSearchPrunesHiddenSelectionAndBackCancelsMode(){
  catalogue();ui.onNodeWithContentDescription("Выбрать товары").performClick();ui.onNodeWithContentDescription("Выбрать все").performClick();waitText("Выбрано: 2")
  ui.onNodeWithText("Поиск по названию...").performTextInput("Газировка");waitText("Выбрано: 1");ui.onNodeWithContentDescription("Очистить").performClick();waitText("Выбрано: 1")
  ui.runOnUiThread{ui.activity.onBackPressedDispatcher.onBackPressed()};waitText("База товаров");ui.onAllNodesWithTag("batch-row-$p").assertCountEquals(0)
 }
 @Test fun shopCatalogueBatchAssignsDepartmentAndUpdatesBasket(){
  click("Магазины");click("Батон");ui.onNodeWithContentDescription("Выбрать товары").performClick();ui.onNodeWithContentDescription("Выбрать все").performClick();actions();click("Назначить группу магазина");click("Напитки");shot("03-store-department");click("Применить к 2");waitFor{runBlocking{db.productShopLinkDao().getAllSync().filter{it.shopId==a}.all{it.departmentId!=null}}}
  runBlocking{assertNotNull(db.shoppingListEntryDao().getById(entry)!!.assignedDepartmentId)}
 }
 @Test fun twoSelectedStoresGainGroupAndThirdDoesNot(){
  click("Магазины");ui.onNodeWithContentDescription("Выбрать магазины").performClick();ui.onNodeWithTag("batch-row-$a").performClick();ui.onNodeWithTag("batch-row-$b").performClick();waitText("Выбрано: 2");actions();click("Добавить группы магазина");click("Напитки");shot("04-stores-groups");click("Применить к 2");waitFor{runBlocking{db.shopDepartmentDao().getAllSync().size==2}}
  runBlocking{assertEquals(setOf(a,b),db.shopDepartmentDao().getAllSync().map{it.shopId}.toSet());assertTrue(db.shopDepartmentDao().getByShopIdSync(c).isEmpty())}
 }
 @Test fun shoppingBatchUrgencyBuyReturnAndConfirmedDelete(){
  ui.onNodeWithContentDescription("Выбрать покупки").performClick();ui.onNodeWithContentDescription("Выбрать все покупки").performClick();actions();click("Сделать срочными");waitText("Список покупок")
  runBlocking{assertTrue(db.shoppingListEntryDao().getAllSync().all{it.isUrgent})}
  ui.onNodeWithContentDescription("Выбрать покупки").performClick();ui.onNodeWithContentDescription("Выбрать все покупки").performClick();actions();shot("05-shopping-actions");click("Отметить купленными");waitText("Список покупок")
  runBlocking{assertTrue(db.shoppingListEntryDao().getAllSync().all{it.isBought})}
  ui.onNodeWithContentDescription("Выбрать покупки").performClick();ui.onNodeWithContentDescription("Выбрать все покупки").performClick();actions();click("Вернуть в покупки");waitText("Список покупок")
  runBlocking{assertTrue(db.shoppingListEntryDao().getAllSync().none{it.isBought})}
  ui.onNodeWithContentDescription("Выбрать покупки").performClick();ui.onNodeWithContentDescription("Выбрать все покупки").performClick();actions();click("Удалить из списка");waitText("Удалить из списка?");shot("06-delete-confirmation");click("Да");waitText("Список покупок")
  runBlocking{assertTrue(db.shoppingListEntryDao().getAllSync().isEmpty());assertEquals(2,db.productDao().getAllSync().size)}
 }
 @Test fun shoppingMultiMoveAddsMissingLinksWithoutChangingOtherPurchaseFacts(){
  ui.onNodeWithContentDescription("Выбрать покупки").performClick();ui.onNodeWithContentDescription("Выбрать все покупки").performClick();ui.onNodeWithContentDescription("Переместить выбранные").performClick();click("Добавить место покупки");click("Красный Яр");click("Добавить и перенести");waitText("Список покупок")
  runBlocking{val rows=db.shoppingListEntryDao().getAllSync();assertEquals(2,rows.size);assertTrue(rows.all{it.assignedShopId==b});assertEquals(1.0,rows.first().quantity,0.0);assertEquals(setOf(a,b),app.container.productRepository.getShopLinksForProductSync(q).map{it.shopId}.toSet())}
 }
 @Test fun recipientCanBeCreatedInlineAndAppliedToWholeSelection(){
  catalogue();ui.onNodeWithContentDescription("Выбрать товары").performClick();ui.onNodeWithContentDescription("Выбрать все").performClick();actions();click("Назначить получателя");click("Новый получатель…");ui.onNodeWithText("Название").performTextInput("Семья");click("Создать");waitText("Семья");click("Применить к 2");waitText("База товаров")
  runBlocking{val family=db.recipientDao().getAllSync().single{it.name=="Семья"};assertTrue(db.productDao().getAllSync().all{it.recipientId==family.id})}
 }
 @Test @Config(qualifiers="w640dp-h360dp-xhdpi") fun actionSheetKeepsApplyVisibleWithLongScrollableOptionsInLandscape(){
  runBlocking{repeat(25){db.recipientDao().insert(RecipientEntity(name="Получатель $it",displayOrder=it+1))}}
  catalogue();ui.onNodeWithContentDescription("Выбрать товары").performClick();ui.onNodeWithContentDescription("Выбрать все").performClick();actions();click("Назначить получателя");click("Рома");ui.onNodeWithText("Применить к 2").assertIsDisplayed();shot("07-landscape-sheet");click("Применить к 2");waitText("База товаров");runBlocking{assertTrue(db.productDao().getAllSync().all{it.recipientId==roma})}
 }
}
