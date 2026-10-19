package com.spisokryadom.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
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
class Refine090UiTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 private val app get()=ApplicationProvider.getApplicationContext<SpisokRyadomApp>()
 private val db get()=app.container.database
 private var baton=0L;private var other=0L;private var favorite=0L
 private fun waitText(text:String){ui.waitUntil(20_000){ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
 private fun waitDatabase(condition:()->Boolean){ui.waitUntil(20_000){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();condition()}}
 private fun waitTag(tag:String){ui.waitUntil(20_000){ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}}
 private fun shot(name:String,sheet:Boolean=false){ui.waitForIdle();ui.runOnIdle{
  val v=if(sheet)org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView else ui.activity.window.decorView
  val bitmap=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(bitmap));val f=File("../.local/refinement-0.9.0/$name-api${android.os.Build.VERSION.SDK_INT}.png");f.parentFile!!.mkdirs();f.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
 }}
 @Before fun setup()=runBlocking{
  listOf("drafts","product_card_drafts","shopping_list_prefs","product_db_prefs").forEach{app.getSharedPreferences(it,0).edit().clear().commit()}
  db.withTransaction{
   db.shoppingListEntryDao().deleteAll();db.productDao().deleteAll();db.productGroupDao().deleteAll();db.shopDao().deleteAll();db.shopDepartmentDao().clearTypes();db.retailStructureDao().clearVenues();db.retailStructureDao().clearNetworks()
   db.cityDao().update(CityEntity(1,"Красноярск"));app.container.locations.select(1)
   baton=db.shopDao().insert(ShopEntity(name="Батон"));other=db.shopDao().insert(ShopEntity(name="Другой",displayOrder=5))
   db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=baton,name="Молочка"))
   app.container.shopRepository.createDepartmentType("Готовая еда")
   favorite=db.productDao().insert(ProductEntity(name="Ааа любимое",isFavorite=true,defaultUnit="шт"))
   db.productShopLinkDao().insert(ProductShopLinkEntity(productId=favorite,shopId=baton))
   repeat(45){i->val id=db.productDao().insert(ProductEntity(name="Товар %02d".format(i),defaultUnit="шт"));db.productShopLinkDao().insert(ProductShopLinkEntity(productId=id,shopId=baton))}
  };Unit
 }
 @After fun close(){ui.activityRule.scenario.close();app.container.locations.closeForTest();db.close();org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null)}
 private fun catalogue(){ui.onNodeWithText("Товары").performClick();waitText("Товаров: 46")}
 private fun create(name:String){ui.onNodeWithContentDescription("Новый товар").performClick();waitText("Название товара");ui.onNodeWithText("Название товара").performTextInput(name)}
 @Test fun newDepartmentFromCardIsVisibleCreatedAndAssignedOnlyToChosenShop(){
  catalogue();create("Газировка")
  ui.onNodeWithText("Добавить магазин").performScrollTo().performClick();ui.onNodeWithText("Без магазина").performClick();waitText("Батон");ui.onNodeWithText("Батон").performClick()
  ui.onNodeWithText("Группа магазина").performClick();ui.onNodeWithText("Добавить группу…").performClick()
  ui.onNodeWithText("Новая группа").performScrollTo().assertIsDisplayed().performTextInput("Напитки")
  shot("01-create-department",true);ui.onNodeWithContentDescription("Создать группу магазина").performClick()
  ui.onNodeWithText("Напитки").assertIsDisplayed();ui.onNodeWithText("Добавить",useUnmergedTree=true).performClick();ui.onNodeWithText("Сохранить").performClick()
  waitText("База товаров")
  runBlocking{val p=db.productDao().getAllSync().single{it.name=="Газировка"};val l=db.productShopLinkDao().getLinksForProductSync(p.id).single();assertEquals(baton,l.shopId);assertEquals("Напитки",db.shopDepartmentDao().getById(l.departmentId!!)!!.name);assertTrue(db.shopDepartmentDao().getByShopIdSync(other).isEmpty())}
 }
 @Test fun newProductIsRevealedAfterClearingSearchAndFavoriteFilter(){
  catalogue();ui.onNodeWithContentDescription("Только избранное").performClick();waitText("Товаров: 1")
  ui.onNode(hasSetTextAction()).performTextInput("Ааа")
  create("Я новая газировка");ui.onNodeWithText("Сохранить").performClick();waitText("База товаров")
  val id=runBlocking{db.productDao().getAllSync().single{it.name=="Я новая газировка"}.id}
  waitTag("catalogue-all-$id");ui.onNodeWithTag("catalogue-all-$id").assertIsDisplayed();shot("02-created-product-reveal")
  ui.onNode(hasContentDescription("Добавить в список") and hasAnyAncestor(hasTestTag("catalogue-all-$id"))).performClick()
  waitDatabase{runBlocking{db.shoppingListEntryDao().getAllSync().any{it.productId==id}}}
 }
 @Test fun favoriteShortcutAndOriginalRowBothRemainAndShareCartState(){
  catalogue();waitTag("catalogue-favorites-$favorite");waitTag("catalogue-all-$favorite")
  ui.onNodeWithTag("catalogue-favorites-$favorite").assertIsDisplayed();ui.onNodeWithTag("catalogue-all-$favorite").assertIsDisplayed();shot("03-favorite-two-places")
  ui.onNode(hasContentDescription("Добавить в список") and hasAnyAncestor(hasTestTag("catalogue-favorites-$favorite"))).performClick()
  waitDatabase{runBlocking{db.shoppingListEntryDao().getAllSync().count{it.productId==favorite}==1}}
  ui.waitUntil(20_000){ui.onAllNodes(hasContentDescription("В списке") and hasAnyAncestor(hasTestTag("catalogue-all-$favorite")),useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
 }
 @Test fun backSavesNewAndExistingCardAndReturnsToVisibleRow(){
  catalogue();create("Я сохранён назад")
  ui.onNodeWithText("Заметка").performScrollTo().performTextInput("Не потерять")
  ui.runOnUiThread{ui.activity.onBackPressedDispatcher.onBackPressed()};waitText("База товаров")
  val p=runBlocking{db.productDao().getAllSync().single{it.name=="Я сохранён назад"}}
  assertEquals("Не потерять",p.note);waitTag("catalogue-all-${p.id}");ui.onNodeWithTag("catalogue-all-${p.id}").assertIsDisplayed().performClick();waitText("Название товара")
  ui.onNodeWithText("Заметка").performScrollTo().performTextReplacement("Правка без кнопки")
  ui.onNodeWithContentDescription("Назад").performClick();waitText("База товаров")
  assertEquals("Правка без кнопки",runBlocking{db.productDao().getById(p.id)!!.note});shot("04-back-autosave")
 }
 @Test fun createdProductIsAlsoRevealedInsideItsShop(){
  ui.onNodeWithText("Магазины").performClick();waitText("Батон");ui.onNodeWithText("Батон").performClick();waitText("Ааа любимое")
  ui.onNodeWithContentDescription("Добавить товар").performClick();ui.onNodeWithText("Создать новый товар").performClick();waitText("Название товара")
  ui.onNodeWithText("Название товара").performTextInput("Я товар магазина");ui.onNodeWithText("Сохранить").performClick();waitText("Батон")
  val id=runBlocking{db.productDao().getAllSync().single{it.name=="Я товар магазина"}.id}
  waitTag("shop-product-0-$id");ui.onNodeWithTag("shop-product-0-$id").assertIsDisplayed();shot("05-shop-reveal")
 }
 @Test fun marketPavilionsAndOrdinaryShopsAreVisibleTogetherWithoutSelector(){
  val venue=runBlocking{db.withTransaction{
   val v=db.retailStructureDao().insertVenue(VenueEntity(cityId=1,name="Енисейский привоз",address="Рынок"))
   val a=db.shopDao().insert(ShopEntity(name="29 павильон",displayOrder=1));val b=db.shopDao().insert(ShopEntity(name="68 павильон",displayOrder=2))
   db.shopBranchDao().insert(ShopBranchEntity(shopId=a,address="Сыры",venueId=v));db.shopBranchDao().insert(ShopBranchEntity(shopId=b,address="Мясной двор",venueId=v));v
  }}
  ui.onNodeWithText("Магазины").performClick();waitTag("venue-group-$venue")
  ui.onNodeWithText("Батон").assertIsDisplayed();ui.onNodeWithText("29 павильон").assertIsDisplayed();ui.onNodeWithText("68 павильон").assertIsDisplayed()
  ui.onNodeWithText("Рынок или ТЦ").assertDoesNotExist();ui.onNodeWithText("Другой").assertIsDisplayed();shot("06-unified-market")
 }
}
