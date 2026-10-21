package com.spisokryadom.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.ProductEditorTransactions
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
class StoreCard180UiTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 private val app get()=ApplicationProvider.getApplicationContext<SpisokRyadomApp>()
 private val db get()=app.container.database
 private var shop=0L;private var branch=0L
 private fun waitFor(c:()->Boolean){ui.waitUntil(25_000){Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(25));ui.waitForIdle();c()}}
 private fun waitText(t:String)=waitFor{ui.onAllNodesWithText(t).fetchSemanticsNodes().isNotEmpty()}
 private fun shot(n:String){ui.waitForIdle();ui.runOnIdle{
  val v=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf{it.isShowing}?.window?.decorView?:ui.activity.window.decorView
  val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b));val f=File("../.local/refine180/screens/$n-api${android.os.Build.VERSION.SDK_INT}.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
 }}
 @Before fun seed(){runBlocking{
  listOf("drafts","product_card_drafts","shopping_list_prefs","product_db_prefs").forEach{app.getSharedPreferences(it,0).edit().clear().commit()}
  db.shoppingListEntryDao().deleteAll();db.goalListDao().deleteAll();db.productDao().deleteAll();db.shopDao().deleteAll();db.retailStructureDao().clearVenues();db.retailStructureDao().clearNetworks();app.container.locations.select(1);db.cityDao().update(CityEntity(1,"Красноярск"))
  shop=db.shopDao().insert(ShopEntity(name="Красный Яр",shoppingBackground="yellow"));branch=db.shopBranchDao().insert(ShopBranchEntity(shopId=shop,address="Весны, 1"))
 };waitText("Список пуст");ui.onNodeWithText("Магазины").performClick();waitText("Красный Яр");ui.onNodeWithText("Красный Яр").performTouchInput{longClick()};waitText("Название магазина")}
 @After fun close(){ui.activityRule.scenario.close();val end=System.nanoTime()+20_000_000_000;while(ProductEditorTransactions.mutex.isLocked&&System.nanoTime()<end){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();Thread.sleep(5)};app.container.locations.closeForTest();db.close();org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null)}
 @Test fun placeIsReadOnlyUntilEditingAndKeepsIdentityAfterSave(){
  ui.onNodeWithTag("shop-place-summary").assertExists();ui.onNodeWithText("Город магазина").assertDoesNotExist();ui.onNodeWithText("Весны, 1").assertExists();shot("01-card-summary")
  ui.onNodeWithText("Править").performClick();ui.onNodeWithText("Город магазина").assertExists();ui.onNodeWithContentDescription("Изменить точку: Весны, 1").performScrollTo().performClick();ui.onNodeWithTag("branch-address-$branch").performScrollTo().performTextReplacement("Весны, 2");shot("02-place-editor");ui.onAllNodesWithText("Сохранить").onLast().performClick()
  ui.onNodeWithText("Готово").performScrollTo().performClick();ui.onNodeWithTag("branch-address-$branch").assertDoesNotExist();ui.onNodeWithText("Весны, 2").assertExists();ui.onNodeWithText("Сохранить").performClick();waitText("Магазины");assertEquals("Весны, 2",runBlocking{db.shopBranchDao().get(branch)!!.address})
 }
 @Test fun purchaseAndUrgencyUseDropdownsAndPersistAllStates(){
  ui.onNodeWithText("Тип покупок в магазине").performScrollTo().performClick();shot("03-purchase-menu");ui.onNodeWithText("Только онлайн").performClick()
  ui.onNodeWithText("Короткая заметка").performScrollTo().performTextInput("Доставка по будням")
  ui.onNodeWithText("Срочные в свёрнутом магазине").performScrollTo().performClick();ui.onNodeWithText("Нет").performClick()
  ui.onNodeWithText("Поднимать магазин со срочными").performScrollTo().performClick();ui.onNodeWithText("Да").performClick();shot("04-compact-urgency")
  ui.onNodeWithText("Сохранить").performClick();waitText("Магазины");runBlocking{val s=db.shopDao().getById(shop)!!;assertEquals(false,s.showUrgentInCollapsed);assertEquals(true,s.boostUrgentShop);assertEquals("Доставка по будням",s.note);assertEquals("online",db.creationPresetDao().shopMode(shop))}
 }
 @Test fun duplicateAddressCannotBeHiddenOrSaved(){
  ui.onNodeWithText("Править").performClick();ui.onNodeWithText("Добавить точку").performScrollTo().performClick()
  ui.onNodeWithTag("branch-address--1").performScrollTo().performTextReplacement("весны, 1 ")
  ui.onAllNodesWithText("Сохранить").onLast().assertIsNotEnabled()
  ui.onNodeWithTag("branch-address--1").performTextReplacement("Весны, 2")
  ui.onAllNodesWithText("Сохранить").onLast().assertIsEnabled().performClick()
  ui.onNodeWithText("Готово").performScrollTo().assertIsEnabled().performClick()
  ui.onNodeWithTag("branch-address--1").assertDoesNotExist();assertEquals(1,runBlocking{db.shopBranchDao().forShop(shop).size})
 }
 @Test fun twoDimensionalPickerIsLiveAndCancelDoesNotCommit(){
  ui.onNodeWithContentDescription("Оформление магазина").performClick();ui.onNodeWithTag("shop-background-color").performScrollTo().performClick();ui.onNodeWithTag("appearance-custom-toggle").performScrollTo().performClick()
  ui.onNodeWithTag("appearance-options").performScrollToNode(hasTestTag("appearance-color-field"));ui.onNodeWithTag("appearance-color-field").performTouchInput{click(percentOffset(.5f,.25f))};shot("05-color-field")
  ui.onNodeWithTag("appearance-hex").performScrollTo().performTextReplacement("#E0C76F");shot("06-color-hex-preview")
  ui.onNodeWithText("Применить оформление").performClick();ui.onNodeWithTag("shop-appearance-preview").assertIsDisplayed();shot("07-card-preview")
  ui.onNodeWithTag("shop-appearance-apply").performClick();ui.onNodeWithText("Сохранить").performClick();waitText("Магазины");assertEquals("#E0C76F",runBlocking{db.shopDao().getById(shop)!!.shoppingBackground})
 }
 @Test fun iconOnlyPickerAndVenueSummaryAreCoherent(){
  ui.onNodeWithContentDescription("Оформление магазина").performClick();ui.onNodeWithContentDescription("Выбрать значок магазина").performScrollTo().performClick();ui.onNodeWithTag("appearance-custom-toggle").assertDoesNotExist();ui.onNodeWithTag("appearance-icon-search").performTextInput("Покупки");ui.onNodeWithTag("appearance-icon-bag").performScrollTo().performClick();shot("08-icon-picker");ui.onNodeWithText("Применить оформление").performClick()
  ui.onNodeWithTag("shop-appearance-apply").performClick();ui.onNodeWithText("Сохранить").performClick();waitText("Магазины");assertEquals("bag",runBlocking{db.shopDao().getById(shop)!!.iconKey});shot("09-store-list-icon")
 }
 @Test fun galleryLogoCanBeImportedSavedAndDisplayedInPurchases(){
  val f=File(app.cacheDir,"synthetic-logo.png");val b=Bitmap.createBitmap(80,80,Bitmap.Config.ARGB_8888);b.eraseColor(0xFF2E7D6B.toInt());val c=Canvas(b);val p=android.graphics.Paint().apply{color=android.graphics.Color.WHITE;textSize=48f;isAntiAlias=true};c.drawText("Я",21f,57f,p);f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
  ui.onNodeWithContentDescription("Оформление магазина").performClick();ui.onNodeWithContentDescription("Загрузить логотип магазина").performScrollTo().performClick();val shadow=Shadows.shadowOf(ui.activity);val request=shadow.nextStartedActivityForResult;assertEquals(android.content.Intent.ACTION_GET_CONTENT,request.intent.action)
  ui.runOnUiThread{shadow.receiveResult(request.intent,android.app.Activity.RESULT_OK,android.content.Intent().setData(android.net.Uri.fromFile(f)))}
  waitFor{runCatching{ui.onNodeWithTag("shop-appearance-apply").assertIsEnabled()}.isSuccess};ui.onNodeWithTag("shop-appearance-preview");shot("10-logo-preview");ui.onNodeWithTag("shop-appearance-apply").performClick();ui.onNodeWithText("Сохранить").performClick();waitText("Магазины")
  assertNotNull(runBlocking{db.shopDao().getById(shop)!!.logoUri});shot("11-logo-store-list")
  runBlocking{val id=app.container.productRepository.saveWithLinks(ProductEntity(name="Пример покупки"),listOf(ProductShopLinkEntity(productId=0,shopId=shop)));app.container.shoppingListRepository.addProduct(id,choice=com.spisokryadom.app.data.repository.ShopChoice.EXPLICIT,shopId=shop)}
  ui.onNodeWithText("Главная").performClick();waitText("Пример покупки");shot("12-logo-purchases")
 }
 @Test fun busyPhotoEditorsCannotStartAnotherImport(){
  ui.runOnIdle{ui.activity.setContent{com.spisokryadom.app.ui.theme.SpisokRyadomTheme{
   androidx.compose.foundation.layout.Column{
    com.spisokryadom.app.ui.components.BranchPhotoEditor("",{},enabled=false)
    com.spisokryadom.app.ui.components.ShopAppearance("","Магазин","",null,{},{},{},{},externalPhotoBusy=true)
   }
  }}}
  ui.onNodeWithText("Выбрать фото").assertIsNotEnabled();ui.onNodeWithText("Камера").assertIsNotEnabled();ui.onNodeWithContentDescription("Оформление магазина").assertIsNotEnabled()
 }
 @Test @Config(sdk=[35],qualifiers="w640dp-h360dp-xhdpi") fun storeColorPreviewKeepsConfirmationVisibleInLandscape(){
  ui.onNodeWithContentDescription("Оформление магазина").performClick();ui.onNodeWithTag("shop-background-color").performScrollTo().performClick();ui.onNodeWithTag("appearance-custom-toggle").performScrollTo().performClick()
  ui.onNodeWithTag("appearance-options").performScrollToNode(hasTestTag("appearance-color-field"));ui.onNodeWithTag("appearance-color-field").performTouchInput{click(percentOffset(.6f,.3f))}
  ui.onNodeWithText("Применить оформление").assertIsDisplayed().assertIsEnabled();shot("13-landscape-color")
 }
}
