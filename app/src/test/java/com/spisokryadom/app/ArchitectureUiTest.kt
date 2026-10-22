package com.spisokryadom.app
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
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
class ArchitectureUiTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 private val app get()=ApplicationProvider.getApplicationContext<SpisokRyadomApp>()
 private val db get()=app.container.database
 private var shop=0L;private var other=0L;private var first=0L;private var second=0L;private var product=0L;private var cheese=0L
 private var entryA=0L;private var entryB=0L
 private fun waitText(text:String){ui.waitUntil(20_000){ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
 private val shopRow=SemanticsMatcher("shop row"){it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("shop-order-")==true}
 private fun reveal(matcher:SemanticsMatcher){ui.waitUntil(20_000){runCatching{ui.onNode(hasScrollToIndexAction()).performScrollToNode(matcher);ui.onNode(matcher).assertIsDisplayed();true}.getOrDefault(false)}}
 private fun shot(name:String){ui.runOnIdle{val view=ui.activity.window.decorView;val b=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(b));val f=File("../.local/architecture-0.6.0/$name-api${android.os.Build.VERSION.SDK_INT}.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 @Before fun setup(){runBlocking{
  listOf("drafts","shopping_list_prefs","product_db_prefs").forEach{app.getSharedPreferences(it,0).edit().clear().commit()}
  db.shoppingListEntryDao().deleteAll();db.productDao().deleteAll();db.productGroupDao().deleteAll();db.shopDao().deleteAll();db.shopDepartmentDao().clearTypes();db.retailStructureDao().clearVenues();db.retailStructureDao().clearNetworks()
  db.cityDao().update(CityEntity(1,"Красноярск"));app.container.locations.select(1)
  shop=db.shopDao().insert(ShopEntity(name="Командор"));other=db.shopDao().insert(ShopEntity(name="Батон",displayOrder=1))
  first=db.shopBranchDao().insert(ShopBranchEntity(shopId=shop,address="Около дома"));second=db.shopBranchDao().insert(ShopBranchEntity(shopId=shop,address="В торговом центре",displayOrder=1))
  val milk=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=shop,name="Молочка",displayOrder=0));cheese=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=shop,name="Сыры",displayOrder=1))
  product=app.container.productRepository.saveWithLinks(ProductEntity(name="Сыр",defaultUnit="кг"),listOf(ProductShopLinkEntity(productId=0,shopId=shop,departmentId=milk)))
  entryA=app.container.shoppingListRepository.addProduct(product,quantity=1.0,unit="кг",choice=ShopChoice.EXPLICIT,shopId=shop,branchId=first)
  entryB=app.container.shoppingListRepository.addProduct(product,quantity=2.0,unit="шт",choice=ShopChoice.EXPLICIT,shopId=shop,branchId=second)
 };waitText("Список покупок")}
 @After fun close(){ui.activityRule.scenario.close();app.container.locations.closeForTest();db.close();org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null)}
 @Test fun multiplePurchasesRemainSeparateAndControlsChangeOnlyDisplayedEntry(){
  ui.onNodeWithText("Товары").performClick();waitText("Покупок: 2");ui.onNodeWithText("Покупок: 2").performClick()
  ui.onNodeWithText("Командор · Около дома").assertIsDisplayed();ui.onNodeWithText("Командор · В торговом центре").assertIsDisplayed();shot("01-multiple-purchases")
  ui.onAllNodesWithContentDescription("Увеличить")[1].performClick()
  ui.waitUntil(20_000){runBlocking{db.shoppingListEntryDao().getById(entryB)!!.quantity==3.0}}
  assertEquals(1.0,runBlocking{db.shoppingListEntryDao().getById(entryA)!!.quantity},0.0)
 }
 @Test fun networkAndVenueUsePointEditorAndSaveOnTheSamePhysicalPoint(){
  ui.onAllNodesWithText("Магазины").onLast().performClick();waitText("Командор");ui.onNodeWithText("Командор").performTouchInput{longClick()};waitText("Название магазина")
  ui.onNodeWithText("Править").performScrollTo().performClick()
  ui.onNodeWithText("Сеть").performScrollTo().performClick();ui.onNodeWithText("Новая сеть…").performClick();ui.onNodeWithText("Название сети").assertIsDisplayed().performTextReplacement("Командор");ui.onAllNodesWithText("Сохранить").onLast().performClick()
  ui.onNodeWithContentDescription("Изменить точку: В торговом центре").performScrollTo().performClick()
  ui.onNodeWithText("Торговое место").performClick();ui.onNodeWithText("Новое торговое место…").performClick();ui.onNodeWithTag("venue-name").performTextReplacement("Планета")
  ui.onNodeWithTag("venue-address").performTextReplacement("Адрес ТЦ, 1");ui.onAllNodesWithText("Сохранить").onLast().performClick()
  ui.onNodeWithTag("branch-label-$second").performTextReplacement("В торговом центре")
  ui.onNodeWithTag("branch-note-$second").performScrollTo().performTextReplacement("Вход с парковки");shot("02-inline-physical-point");ui.onAllNodesWithText("Сохранить").onLast().performClick()
  ui.onNodeWithText("Сохранить").performClick();waitText("Магазины")
  ui.waitUntil(20_000){runBlocking{db.shopDao().getById(shop)!!.networkId!=null}}
  runBlocking{val point=db.shopBranchDao().get(second)!!;assertEquals(shop,point.shopId);assertNotNull(point.venueId);assertEquals("Планета",db.retailStructureDao().venue(point.venueId!!)!!.name);assertEquals("Вход с парковки",point.note);assertEquals(2,db.shopBranchDao().forShop(shop).size)}
  reveal(hasText("Планета"));reveal(hasText("Командор") and hasText("В торговом центре"));reveal(hasText("Батон"));reveal(hasText("Командор") and hasText("Около дома"));ui.onNodeWithText("Рынок или ТЦ").assertDoesNotExist();shot("03-venue-browse")
 }
 @Test fun commonShopDepartmentAppliesToBothPointsWithoutASecondProduct(){
  ui.onNodeWithText("Товары").performClick();waitText("База товаров");waitText("Сыр");ui.onNodeWithText("Сыр").performClick();waitText("Название товара")
  ui.onNodeWithText("Группы в отдельных точках").assertDoesNotExist()
  ui.onNodeWithText("1. Командор").performScrollTo().performClick();waitText("Товар в магазине")
  ui.onNodeWithText("Группа магазина").performClick();ui.onNodeWithText("Сыры").performClick();shot("04-shop-group");ui.onNodeWithText("Применить").performClick()
  ui.onNodeWithText("Сохранить").performClick();waitText("База товаров")
  runBlocking{val fresh=app.container.shoppingListRepository.addProduct(product,unit="кг",choice=ShopChoice.EXPLICIT,shopId=shop,branchId=second);assertEquals(cheese,db.shoppingListEntryDao().getById(fresh)!!.assignedDepartmentId);assertEquals(cheese,db.shoppingListEntryDao().getById(entryA)!!.assignedDepartmentId);assertEquals(cheese,db.shoppingListEntryDao().getById(entryB)!!.assignedDepartmentId);assertEquals(1,db.productDao().getAllSync().size)}
 }
 @Test fun marketConversionAndUnifiedListKeepPavilionsAndPurchases(){
  runBlocking{db.shopDao().update(db.shopDao().getById(shop)!!.copy(name="Енисейский привоз"))}
  ui.onAllNodesWithText("Магазины").onLast().performClick();waitText("Енисейский привоз");ui.onNodeWithText("Енисейский привоз").performTouchInput{longClick()};waitText("Название магазина")
  ui.onNodeWithText("Править").performScrollTo().performClick()
  ui.onNodeWithText("Преобразовать в торговое место").performScrollTo().performClick();waitText("Преобразовать в торговое место")
  ui.onNodeWithText("Преобразовать",substring=false).performClick();waitText("Магазины");waitText("Около дома")
  reveal(hasText("Енисейский привоз"));reveal(hasText("Около дома") and hasAnyAncestor(shopRow));reveal(hasText("В торговом центре") and hasAnyAncestor(shopRow));reveal(hasText("Батон"));ui.onNodeWithText("Рынок или ТЦ").assertDoesNotExist();shot("05-market-pavilions")
  runBlocking{assertNotNull(db.shoppingListEntryDao().getById(entryA));assertEquals(first,db.shoppingListEntryDao().getById(entryA)!!.assignedBranchId);assertEquals(second,db.shoppingListEntryDao().getById(entryB)!!.assignedBranchId);assertEquals(1,db.productDao().getAllSync().size)}
 }
}
