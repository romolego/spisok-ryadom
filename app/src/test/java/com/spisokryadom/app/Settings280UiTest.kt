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
import com.spisokryadom.app.widget.WidgetSettings
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
class Settings280UiTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 private val app get()=ApplicationProvider.getApplicationContext<SpisokRyadomApp>()
 private val db get()=app.container.database
 private var shop=0L;private var second=0L;private var product=0L;private var photo=""
 private fun waitFor(c:()->Boolean){ui.waitUntil(25_000){Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(20));ui.waitForIdle();c()}}
 private fun text(value:String)=waitFor{ui.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()}
 private fun tag(value:String)=waitFor{ui.onAllNodesWithTag(value).fetchSemanticsNodes().isNotEmpty()}
 private fun tab(label:String){ui.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.Tab)).performClick()}
 private fun settings(){ui.onNodeWithContentDescription("Настройки").performClick();tag("settings-list")}
 private fun openWidget(){ui.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("widget-settings"));ui.onNodeWithTag("widget-settings").performClick();tag("widget-grouping")}
 private fun openDictionary(kind:String){ui.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("dictionary-open-"+kind));ui.onNodeWithTag("dictionary-open-"+kind).performClick();tag("dictionary-manager")}
 private fun productCard(){tab("Товары");text("База товаров");text("Проверяемый товар");ui.onNodeWithText("Проверяемый товар").performClick();text("Название товара")}
 private fun shopCard(){tab("Магазины");text("Местный");ui.onNodeWithText("Местный").performTouchInput{longClick()};text("Название магазина")}
 private fun selectShops(){
  waitFor{ui.onAllNodesWithContentDescription("Управление магазинами").fetchSemanticsNodes().isNotEmpty()}
  ui.onNodeWithContentDescription("Управление магазинами").performClick();ui.onNodeWithText("Выбрать магазины").performClick()
  ui.onNodeWithContentDescription("Выбрать все").performClick();ui.onNodeWithContentDescription("Действия с выбранными").performClick();text("Выбрано: 2")
 }
 private fun shot(name:String){ui.runOnIdle{
  val view=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf{it.isShowing}?.window?.decorView?:ui.activity.window.decorView
  val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(image))
  val f=File("build/reports/refine280/screens/"+name+"-api"+android.os.Build.VERSION.SDK_INT+".png");f.parentFile!!.mkdirs();f.outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()
 }}
 @Before fun seed(){runBlocking{
  listOf("drafts","product_card_drafts","shopping_list_prefs","product_db_prefs").forEach{app.getSharedPreferences(it,0).edit().clear().commit()}
  app.getSharedPreferences("shopping_list_prefs",0).edit().putBoolean("autosave_product_cards",false).commit()
  db.shoppingListEntryDao().deleteAll();db.goalListDao().deleteAll();db.productDao().deleteAll();db.shopDao().deleteAll();db.productGroupDao().deleteAll();db.recipientDao().deleteAll();db.unitDao().deleteAll();db.shopDepartmentDao().clearTypes();db.retailStructureDao().clearVenues()
  db.cityDao().update(CityEntity(1,"Красноярск"));if(db.cityDao().getById(2)==null)db.cityDao().insert(CityEntity(2,"Иркутск"));app.container.locations.select(1)
  val recipient=db.recipientDao().insert(RecipientEntity(name="Рома"));val group=db.productGroupDao().insert(ProductGroupEntity(name="Продукты"))
  db.unitDao().insert(UnitEntity(name="шт"));db.unitDao().insert(UnitEntity(name="кг"));app.container.shopRepository.createDepartmentType("Бытовая химия")
  shop=app.container.shopRepository.saveWithDepartments(ShopEntity(name="Местный"),emptyList(),listOf(ShopBranchEntity(shopId=0,address="Мира, 1")))
  second=app.container.shopRepository.saveWithDepartments(ShopEntity(name="Другой",isFavorite=true,displayOrder=1),emptyList(),listOf(ShopBranchEntity(shopId=0,address="Ленина, 2")))
  val file=File(app.filesDir,"photo280.png");val image=Bitmap.createBitmap(40,40,Bitmap.Config.ARGB_8888);image.eraseColor(android.graphics.Color.rgb(80,150,160));file.outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle();photo=file.absolutePath
  product=app.container.productRepository.saveWithLinks(ProductEntity(name="Проверяемый товар",recipientId=recipient,productGroupId=group,photoUri=photo),listOf(ProductShopLinkEntity(productId=0,shopId=shop)))
  app.container.productRepository.saveWithLinks(ProductEntity(name="Любимый товар",isFavorite=true),emptyList())
 };text("Список пуст")}
 @After fun close(){ui.activityRule.scenario.close();app.container.locations.closeForTest();db.close();org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null)}

 @Test fun allWidgetSettingsLiveInOneSheetAndFontUsesADropdown(){
  settings();ui.onNodeWithTag("widget-font-size").assertDoesNotExist();ui.onNodeWithTag("widget-grouping").assertDoesNotExist()
  openWidget();ui.onAllNodesWithTag("editor-sheet").assertCountEquals(1)
  ui.onNodeWithTag("widget-font-size").performScrollTo().performClick();ui.onNodeWithText("14").performClick()
  ui.onNodeWithTag("widget-shop-background").performScrollTo().performClick();ui.onNodeWithTag("widget-product-background").performScrollTo().performClick()
  assertEquals(14,WidgetSettings.load(app.getSharedPreferences(WidgetSettings.PREFS,0)).textSize)
  shot("all-widget-settings")
  ui.onNodeWithText("Готово").performClick();ui.onNodeWithTag("widget-font-size").assertDoesNotExist()
  openWidget();ui.onNodeWithTag("widget-shop-background").performScrollTo().assertIsOn();ui.onNodeWithTag("widget-product-background").performScrollTo().assertIsOn()
  ui.onNodeWithTag("widget-font-size").performScrollTo();ui.onNode(hasText("14") and hasAnyAncestor(hasTestTag("widget-font-size"))).assertExists()
 }
 @Test fun dictionariesOpenOnlyOnDemandAndResetFollowsAllFourEntries(){
  settings();ui.onNodeWithText("Рома").assertDoesNotExist()
  for(kind in listOf("recipient","group","unit","dept")){
   openDictionary(kind);ui.onAllNodesWithTag("dictionary-manager").assertCountEquals(1)
   val title=when(kind){"recipient"->"Получатели";"group"->"Товарные группы";"unit"->"Единицы измерения";else->"Группы магазинов"}
   ui.onNodeWithTag("manager-edit-"+title).performClick();shot("dictionary-"+kind)
   ui.onNodeWithText("Закрыть").performClick();ui.onNodeWithTag("dictionary-manager").assertDoesNotExist()
  }
  ui.onNodeWithTag("settings-list").performScrollToNode(hasContentDescription("Открыть сброс"))
  val reset=ui.onNodeWithContentDescription("Открыть сброс").getUnclippedBoundsInRoot()
  val last=ui.onNodeWithTag("dictionary-open-dept").getUnclippedBoundsInRoot();assertTrue(reset.top>=last.bottom)
  shot("settings-bottom-reset");ui.onNodeWithTag("settings-reset").performClick();text("Восстановить готовые группы");shot("reset-menu");ui.onNodeWithText("Закрыть").performClick()
 }
 @Test fun recipientUsageStillOpensTheRealProductFromItsManager(){
  settings();openDictionary("recipient");ui.onNodeWithText("Рома").performClick();text("Товары получателя")
  ui.onNodeWithText("Проверяемый товар").performClick();text("Название товара");ui.onNodeWithTag("product-name").assertTextContains("Проверяемый товар")
  ui.onNodeWithContentDescription("Назад").performClick();tag("settings-list");tag("dictionary-manager")
 }
 @Test fun productToolbarHasStarPhotoControlsAndPaletteWithPhotoImmediatelyBelow(){
  productCard();tag("product-appearance-row");waitFor{ui.onAllNodesWithContentDescription("Фото товара").fetchSemanticsNodes().isNotEmpty()}
  val star=ui.onNodeWithTag("favorite-toggle").getUnclippedBoundsInRoot();val gallery=ui.onNodeWithContentDescription("Выбрать фото").getUnclippedBoundsInRoot();val camera=ui.onNodeWithContentDescription("Камера").getUnclippedBoundsInRoot();val palette=ui.onNodeWithContentDescription("Цвет товара").getUnclippedBoundsInRoot()
  assertTrue(star.right<=gallery.left);assertTrue(gallery.right<=camera.left);assertTrue(camera.right<=palette.left)
  for(rect in listOf(gallery,camera,palette))assertTrue(kotlin.math.abs(rect.top.value-star.top.value)<3)
  val image=ui.onNodeWithContentDescription("Фото товара").getUnclippedBoundsInRoot();val quantity=ui.onNodeWithText("Кол-во по умолч.").getUnclippedBoundsInRoot();val format=ui.onNodeWithText("Тип покупки").getUnclippedBoundsInRoot()
  assertTrue(image.top>=star.bottom);assertTrue(format.top>=image.bottom);assertTrue(quantity.top>format.top)
  ui.onNodeWithTag("favorite-toggle").assertIsOff().performClick();ui.onNodeWithTag("favorite-toggle").assertIsOn();ui.onNodeWithText("Избранное").assertDoesNotExist();shot("product-photo-toolbar")
  ui.onNodeWithText("Тип покупки").performScrollTo().performClick();ui.onNodeWithText("Онлайн и офлайн").performClick()
  ui.onNodeWithText("Сохранить").performClick();text("База товаров")
  val stored=runBlocking{db.productDao().getById(product)!!};assertTrue(stored.isFavorite);assertEquals("both",stored.purchaseType);assertEquals(photo,stored.photoUri)
 }
 @Test fun shopUsesTheSameStarAndPaletteLayoutAndOnlySaveChangesTheFavorite(){
  shopCard();tag("favorite-toggle");ui.onNodeWithText("Избранное").assertDoesNotExist()
  val star=ui.onNodeWithTag("favorite-toggle").getUnclippedBoundsInRoot();val palette=ui.onNodeWithContentDescription("Оформление магазина").getUnclippedBoundsInRoot()
  assertTrue(star.right<palette.left);assertTrue(kotlin.math.abs(star.top.value-palette.top.value)<3)
  ui.onNodeWithTag("favorite-toggle").performClick();ui.onNodeWithTag("favorite-toggle").assertIsOn();assertFalse(runBlocking{db.shopDao().getById(shop)!!.isFavorite});shot("shop-star-toolbar")
  ui.onNodeWithText("Сохранить").performClick();text("Магазины");assertTrue(runBlocking{db.shopDao().getById(shop)!!.isFavorite})
 }
 @Test fun favoriteFilterLivesInHeadersAndShopBatchEntryLivesBehindThePencil(){
  tab("Товары");text("База товаров");ui.onNodeWithContentDescription("Только избранное").performClick();text("База товаров · избранные")
  ui.onNodeWithText("Только избранные").assertDoesNotExist();ui.onNodeWithTag("favorites-filter-active").assertDoesNotExist();ui.onNodeWithText("Проверяемый товар").assertDoesNotExist();shot("catalogue-favorite-title")
  tab("Магазины");text("Магазины");ui.onNodeWithContentDescription("Изменить город").assertDoesNotExist();ui.onNodeWithContentDescription("Выбрать магазины").assertDoesNotExist()
  ui.onNodeWithContentDescription("Только избранные магазины").performClick();text("Магазины · избранные");ui.onNodeWithText("Местный").assertDoesNotExist();ui.onAllNodesWithText("Другой").assertCountEquals(1);shot("shop-favorite-title")
  ui.onNodeWithContentDescription("Управление магазинами").performClick();ui.onNodeWithText("Выбрать магазины").assertIsDisplayed();ui.onNodeWithText("Выбрать магазины").performClick()
  ui.onNodeWithContentDescription("Выбрать все").performClick();ui.onNodeWithContentDescription("Действия с выбранными").performClick();ui.onNodeWithText("Убрать из избранного").performClick()
  text("Нет избранных магазинов");assertFalse(runBlocking{db.shopDao().getById(second)!!.isFavorite});assertFalse(runBlocking{db.shopDao().getById(shop)!!.isFavorite})
 }
 @Test fun cityEditorKeepsOldAddressesWhileAddingAndReopeningAnotherCity(){
  shopCard();ui.onAllNodesWithText("Править").onFirst().performClick()
  fun city(name:String){ui.onNodeWithText("Город магазина").performScrollTo().performClick();ui.onNode(hasText(name) and hasAnyAncestor(isPopup())).performClick();waitFor{ui.onAllNodesWithContentDescription("Добавить точку").fetchSemanticsNodes().isNotEmpty()}}
  city("Иркутск");ui.onNodeWithText("Мира, 1").assertDoesNotExist()
  ui.onNodeWithContentDescription("Добавить точку").performScrollTo().performClick();text("Новая точка")
  ui.onNodeWithText("Адрес").performTextInput("Советская, 12")
  ui.onNode(hasText("Сохранить") and hasAnyAncestor(isDialog())).performClick();waitFor{ui.onAllNodesWithTag("place-editor").fetchSemanticsNodes().isEmpty()}
  city("Красноярск");ui.onNodeWithText("Мира, 1").assertExists();ui.onNodeWithText("Советская, 12").assertDoesNotExist()
  city("Иркутск");ui.onNodeWithText("Советская, 12").assertExists();shot("city-specific-shop-addresses")
  ui.onNodeWithText("Сохранить").performClick();text("Магазины")
  val profiles=runBlocking{app.container.shopRepository.cityProfiles(shop)};assertEquals(setOf(1L,2L),profiles.map{it.cityId}.toSet())
  val local=profiles.single{it.cityId==2L};assertEquals("Мира, 1",runBlocking{app.container.shopRepository.branches(shop).single().address});assertEquals("Советская, 12",runBlocking{app.container.shopRepository.branches(local.id).single().address})
 }
 @Test fun shopBatchConfiguresModeAndBothUrgencyOverrides(){
  tab("Магазины");text("Местный");selectShops();ui.onNodeWithText("Тип покупок").performClick()
  ui.onNodeWithText("Тип покупок в магазине").performClick();ui.onNodeWithText("Только онлайн").performClick();ui.onNodeWithText("Применить к 2").performClick()
  waitFor{runBlocking{app.container.shopRepository.purchaseMode(shop)=="online"&&app.container.shopRepository.purchaseMode(second)=="online"}}
  selectShops();ui.onNodeWithText("Настройки срочности").performClick()
  ui.onNodeWithText("Срочные в свёрнутом магазине").performClick();ui.onNode(hasText("Да") and hasAnyAncestor(isPopup())).performClick()
  ui.onNodeWithText("Поднимать магазин со срочными").performClick();ui.onNode(hasText("Да") and hasAnyAncestor(isPopup())).performClick();shot("shop-batch-urgency")
  ui.onNodeWithText("Применить к 2").performClick()
  waitFor{runBlocking{listOf(shop,second).all{db.shopDao().getById(it)!!.let{s->s.showUrgentInCollapsed==true&&s.boostUrgentShop==true}}}}
 }
}
