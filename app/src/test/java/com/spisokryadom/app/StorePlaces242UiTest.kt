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
class StorePlaces242UiTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 private val app get()=ApplicationProvider.getApplicationContext<SpisokRyadomApp>()
 private val db get()=app.container.database
 private var shop=0L;private var network=0L
 private fun waitFor(c:()->Boolean){ui.waitUntil(25_000){Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(20));ui.waitForIdle();c()}}
 private fun waitText(text:String)=waitFor{ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
 private fun waitTag(tag:String)=waitFor{ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}
 private fun tab(text:String){ui.onNode(hasText(text) and SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.Tab)).performClick()}
 private fun shot(name:String){ui.runOnIdle{val view=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf{it.isShowing}?.window?.decorView?:ui.activity.window.decorView;val b=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(b));val f=File("build/reports/refine242/screens/$name-api${android.os.Build.VERSION.SDK_INT}.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 @Before fun seed(){runBlocking{
  listOf("drafts","product_card_drafts","shopping_list_prefs","product_db_prefs").forEach{app.getSharedPreferences(it,0).edit().clear().commit()}
  db.shoppingListEntryDao().deleteAll();db.goalListDao().deleteAll();db.productDao().deleteAll();db.shopDao().deleteAll();db.retailStructureDao().clearVenues();db.retailStructureDao().clearNetworks();db.cityDao().deleteAll()
  db.cityDao().insert(CityEntity(1,"Красноярск"));db.cityDao().insert(CityEntity(2,"Томск"));db.cityDao().insert(CityEntity(3,"Пустой город"));app.container.locations.select(1)
  network=db.retailStructureDao().insertNetwork(RetailNetworkEntity(name="X5"))
  shop=db.shopDao().insert(ShopEntity(name="Красный Яр",networkId=network))
  db.shopBranchDao().insert(ShopBranchEntity(shopId=shop,address="Весны, 1 (Взлётка плаза)"))
  db.shopBranchDao().insert(ShopBranchEntity(shopId=shop,address="Молокова, 54",label="Сибирский городок",displayOrder=1))
  db.shopDao().insert(ShopEntity(name="Томский магазин",cityId=2))
  val product=app.container.productRepository.saveWithLinks(ProductEntity(name="Молоко"),listOf(ProductShopLinkEntity(productId=0,shopId=shop)))
  app.container.shoppingListRepository.addProduct(product,choice=ShopChoice.EXPLICIT,shopId=shop)
 };waitText("Молоко");waitFor{app.container.locations.activeCity.value.name=="Красноярск"}}
 @After fun close(){ui.activityRule.scenario.close();app.container.locations.closeForTest();db.close();org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null)}
 private fun card(){tab("Магазины");waitTag("shop-order-$shop-0");ui.onNodeWithText("Красный Яр").performTouchInput{longClick()};waitText("Название магазина");ui.onNodeWithText("Править").performScrollTo().performClick()}
 @Test fun cityNetworkAndPointsHaveOneOrderAndCityMenuHasNoDuplicateShortcut(){
  card()
  val city=ui.onNodeWithText("Город магазина").getUnclippedBoundsInRoot()
  val relation=ui.onNodeWithText("Сеть").getUnclippedBoundsInRoot()
  val points=ui.onNodeWithText("Точки магазина").getUnclippedBoundsInRoot()
  assertTrue(city.top<relation.top&&relation.top<points.top)
  ui.onNodeWithText("Добавить точку").assertDoesNotExist();ui.onNodeWithContentDescription("Добавить точку").performScrollTo().assertIsDisplayed()
  shot("store-place-order")
  ui.onNodeWithText("Город магазина").performScrollTo().performClick()
  ui.onAllNodes(hasText("Красноярск") and hasAnyAncestor(isPopup())).assertCountEquals(1)
  ui.onAllNodes(hasText("Томск") and hasAnyAncestor(isPopup())).assertCountEquals(1)
  ui.onNodeWithText("Текущий город").assertDoesNotExist()
  ui.onNode(hasText("Красноярск") and hasAnyAncestor(isPopup())).performClick()
 }
 @Test fun networkPencilStagesRenameAndCancelPreservesTheName(){
  card();ui.onNodeWithContentDescription("Изменить название сети").performScrollTo().performClick()
  ui.onNodeWithText("Название сети").performTextReplacement("Не сохранять")
  ui.onNodeWithText("Отмена").performClick();assertEquals("X5",runBlocking{db.retailStructureDao().networks().single().name})
  ui.onNodeWithContentDescription("Изменить название сети").performClick();ui.onNodeWithText("Название сети").performTextReplacement("X5 Обновлённая")
  ui.onAllNodesWithText("Сохранить").onLast().performClick()
  assertEquals("X5",runBlocking{db.retailStructureDao().networks().single().name})
  ui.onNodeWithText("Сохранить").performClick();waitText("Магазины")
  assertEquals("X5 Обновлённая",runBlocking{db.retailStructureDao().networks().single().name})
  assertEquals(network,runBlocking{db.shopDao().getById(shop)!!.networkId})
 }
 @Test fun newNetworkFromExistingSelectionDoesNotRenameTheOldNetwork(){
  card();ui.onNodeWithText("Сеть").performScrollTo().performClick();ui.onNodeWithText("Новая сеть…").performClick()
  ui.onNodeWithText("Название сети").performTextInput("Местная сеть");ui.onAllNodesWithText("Сохранить").onLast().performClick()
  assertEquals(1,runBlocking{db.retailStructureDao().networks().size})
  ui.onNodeWithText("Сохранить").performClick();waitText("Магазины")
  assertEquals("X5",runBlocking{db.retailStructureDao().networks().single{it.id==network}.name})
  val selected=runBlocking{db.shopDao().getById(shop)!!.networkId};assertNotEquals(network,selected)
  assertEquals("Местная сеть",runBlocking{db.retailStructureDao().networks().single{it.id==selected}.name})
 }
 @Test fun visibleShoppingCitySwitchesStoresEvenWhenTheCityHasNoStoresAndSurvivesRecreation(){
  tab("Магазины");waitTag("shop-city-selector");ui.onNodeWithContentDescription("Подсказка: Город покупок").performClick();waitText("Понятно");shot("shopping-city-help");ui.onNodeWithText("Понятно").performClick()
  ui.onNodeWithContentDescription("Изменить город").performClick();ui.onNodeWithText("Пустой город").performClick();waitText("Нет магазинов")
  ui.onNodeWithTag("shop-city-selector").assertIsDisplayed();ui.onNodeWithContentDescription("Изменить город").performClick();ui.onNodeWithText("Томск").performClick();waitText("Томский магазин")
  ui.onNodeWithText("Красный Яр").assertDoesNotExist();shot("stores-in-selected-city")
  ui.activityRule.scenario.recreate();waitText("Томский магазин");assertEquals(2L,runBlocking{app.container.locations.currentId()})
  ui.onNodeWithContentDescription("Изменить город").performClick();ui.onNodeWithText("Красноярск").performClick();waitTag("shop-order-$shop-0")
  assertEquals(1,runBlocking{db.shoppingListEntryDao().getAllSync().size});assertEquals(2,runBlocking{db.shopDao().getAllSync().size})
 }
 @Test fun creatingACardCityDoesNotSwitchTheAppOrSaveTheStoreOnBack(){
  card();ui.onNodeWithText("Город магазина").performScrollTo().performClick();ui.onNodeWithText("Новый город…").performClick()
  ui.onNodeWithText("Название города").performTextInput("Дивногорск");ui.onNodeWithContentDescription("Создать: Название города").performClick()
  waitFor{ui.onAllNodesWithText("Название города").fetchSemanticsNodes().isEmpty()&&ui.onAllNodesWithText("Дивногорск").fetchSemanticsNodes().isNotEmpty()}
  assertTrue(runBlocking{db.cityDao().getAllSync().any{it.name=="Дивногорск"}});assertEquals(1L,runBlocking{app.container.locations.currentId()})
  ui.onNodeWithContentDescription("Назад").performClick();waitTag("shop-city-selector")
  assertEquals(1L,runBlocking{db.shopDao().getById(shop)!!.cityId});assertEquals(1L,runBlocking{app.container.locations.currentId()})
 }
 @Test fun venueHasExplicitTypeAndCityAndMovingItRequiresConfirmation(){
  val venue=runBlocking{db.retailStructureDao().insertVenue(VenueEntity(cityId=1,name="Енисейский привоз",address="Общий адрес",kind=VenueKinds.MARKET))}
  tab("Магазины");waitTag("venue-group-$venue");ui.onNodeWithContentDescription("Изменить рынок или ТЦ: Енисейский привоз").performClick()
  ui.onNodeWithText("Город места").assertExists();ui.onNodeWithText("Тип места").performClick();ui.onNodeWithText("Торговый центр").performClick()
  ui.onNodeWithText("Город места").performClick();ui.onNodeWithText("Томск").performClick();shot("venue-type-city")
  ui.onNodeWithText("Сохранить").performClick();waitText("Изменить город места?");ui.onNodeWithText("Отмена").performClick();assertEquals(1L,runBlocking{db.retailStructureDao().venue(venue)!!.cityId})
  ui.onNodeWithText("Сохранить").performClick();ui.onNodeWithText("Да").performClick();waitFor{runBlocking{db.retailStructureDao().venue(venue)!!.cityId==2L}}
  assertEquals(VenueKinds.MALL,runBlocking{db.retailStructureDao().venue(venue)!!.kind});assertEquals(1L,runBlocking{app.container.locations.currentId()})
 }

 @Test fun logoDeletionIsExplicitConfirmedAndStagedUntilStoreSave(){
  val file=File(app.filesDir,"shop_logos/synthetic-242.png");file.parentFile!!.mkdirs();val bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);bitmap.eraseColor(0xff996699.toInt());file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
  runBlocking{db.shopDao().update(db.shopDao().getById(shop)!!.copy(logoUri=file.absolutePath))}
  card();ui.onNodeWithContentDescription("Оформление магазина").performClick();ui.onNodeWithTag("shop-symbol-edit").performScrollTo().performClick()
  ui.onNodeWithContentDescription("Заменить логотип магазина").assertExists();ui.onNodeWithContentDescription("Удалить логотип магазина").performClick();waitText("Удалить логотип?");shot("logo-delete-confirmation")
  ui.onAllNodesWithText("Отмена").onLast().performClick();ui.onNodeWithContentDescription("Удалить логотип магазина").assertExists()
  ui.onNodeWithContentDescription("Удалить логотип магазина").performClick();ui.onNodeWithText("Да").performClick();ui.onNodeWithContentDescription("Удалить логотип магазина").assertDoesNotExist()
  ui.onNodeWithTag("shop-symbol-apply").performClick();ui.onNodeWithTag("shop-appearance-apply").performClick()
  assertEquals(file.absolutePath,runBlocking{db.shopDao().getById(shop)!!.logoUri});ui.onNodeWithText("Сохранить").performClick();waitText("Магазины");assertNull(runBlocking{db.shopDao().getById(shop)!!.logoUri})
 }

}
