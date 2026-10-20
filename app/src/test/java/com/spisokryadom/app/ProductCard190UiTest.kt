package com.spisokryadom.app

import android.graphics.Bitmap
import android.graphics.Canvas
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
@Config(sdk=[28,35],application=SpisokRyadomApp::class,qualifiers="w360dp-h640dp-xhdpi",shadows=[WindowsCaptureProvider::class])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProductCard190UiTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 private val app get()=ApplicationProvider.getApplicationContext<SpisokRyadomApp>()
 private val db get()=app.container.database
 private var shop=0L;private var first=0L;private var second=0L;private var product=0L;private var entry=0L;private var group=0L
 private fun waitFor(c:()->Boolean){ui.waitUntil(25_000){Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(25));ui.waitForIdle();c()}}
 private fun waitText(t:String)=waitFor{ui.onAllNodesWithText(t).fetchSemanticsNodes().isNotEmpty()}
 private fun shot(n:String){ui.waitForIdle();ui.runOnIdle{
  val v=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf{it.isShowing}?.window?.decorView?:ui.activity.window.decorView
  val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b));val f=File("../.local/refine190/screens/$n-api${android.os.Build.VERSION.SDK_INT}.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
 }}
 @Before fun seed(){runBlocking{
  listOf("drafts","product_card_drafts","shopping_list_prefs","product_db_prefs").forEach{app.getSharedPreferences(it,0).edit().clear().commit()}
  db.shoppingListEntryDao().deleteAll();db.goalListDao().deleteAll();db.productDao().deleteAll();db.shopDao().deleteAll();db.retailStructureDao().clearVenues();db.retailStructureDao().clearNetworks();app.container.locations.select(1)
  shop=db.shopDao().insert(ShopEntity(name="Красный Яр"));first=db.shopBranchDao().insert(ShopBranchEntity(shopId=shop,address="Весны, 1"));second=db.shopBranchDao().insert(ShopBranchEntity(shopId=shop,address="Молокова, 54",displayOrder=1))
  group=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=shop,name="Молочка"))
  product=app.container.productRepository.saveWithLinks(ProductEntity(name="Сыр косичка"),listOf(ProductShopLinkEntity(productId=0,shopId=shop)))
  entry=app.container.shoppingListRepository.addProduct(product,quantity=2.0)
 }
  waitText("Список покупок");ui.onNodeWithText("Товары").performClick();waitText("База товаров");waitText("Сыр косичка");ui.onNodeWithText("Сыр косичка").performClick();waitText("Название товара")
 }

 @After fun close(){ui.activityRule.scenario.close();val end=System.nanoTime()+20_000_000_000;while(ProductEditorTransactions.mutex.isLocked&&System.nanoTime()<end){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();Thread.sleep(5)};app.container.locations.closeForTest();db.close();org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null)}
 private fun settings(){ui.onNodeWithText("1. Красный Яр").performScrollTo().performClick();waitText("Товар в магазине")}
 private fun restrict(){ui.onNodeWithText("Доступность товара").performScrollTo().performClick();ui.onNodeWithText("Только выбранные точки").performClick()}
 private fun synthetic():File {val f=File(app.cacheDir,"synthetic-product.png");val b=Bitmap.createBitmap(240,160,Bitmap.Config.ARGB_8888);b.eraseColor(0xFFE9DCA5.toInt());val p=android.graphics.Paint().apply{color=0xFF3E6B62.toInt();isAntiAlias=true};Canvas(b).drawRoundRect(40f,35f,200f,125f,12f,12f,p);f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle();return f}
 private fun galleryPhoto(){ui.onNodeWithContentDescription("Выбрать фото").performScrollTo().performClick();val shadow=Shadows.shadowOf(ui.activity);val request=shadow.nextStartedActivityForResult;assertEquals(android.content.Intent.ACTION_GET_CONTENT,request.intent.action);val file=synthetic();ui.runOnUiThread{shadow.receiveResult(request.intent,android.app.Activity.RESULT_OK,android.content.Intent().setData(android.net.Uri.fromFile(file)))};waitFor{ui.onAllNodesWithContentDescription("Изменить фото").fetchSemanticsNodes().isNotEmpty()}}
 @Test fun cardHidesRareBlocksAndShopDialogCombinesGroupWithAvailability(){
  ui.onNodeWithText("Точки продажи товара").assertDoesNotExist();ui.onNodeWithText("Группы в отдельных точках").assertDoesNotExist();ui.onNodeWithTag("product-photo").performScrollTo();shot("01-card-compact")
  settings();ui.onNodeWithText("Во всех точках").assertExists();ui.onNode(isToggleable() and hasText("Весны, 1")).assertDoesNotExist();ui.onNodeWithText("Приоритет точек").assertExists();shot("02-shop-settings-all")
  ui.onNodeWithText("Группа магазина").performClick();ui.onNodeWithText("Молочка").performClick();ui.onNodeWithText("Применить").performClick();ui.onNodeWithText("Сохранить").performClick();waitText("База товаров")
  assertEquals(group,runBlocking{db.shoppingListEntryDao().getById(entry)!!.assignedDepartmentId});assertFalse(runBlocking{db.productShopLinkDao().getLinksForProductSync(product).single().restrictBranches})
 }
 @Test fun restrictedPointsRequireSelectionAndUpdateSamePurchase(){
  settings();restrict();ui.onNode(isToggleable() and hasText("Весны, 1")).performScrollTo().performClick();ui.onNode(isToggleable() and hasText("Молокова, 54")).performScrollTo().performClick();ui.onNodeWithText("Применить").assertIsNotEnabled();shot("03-no-outlets")
  ui.onNode(isToggleable() and hasText("Молокова, 54")).performScrollTo().performClick();ui.onNodeWithText("Применить").assertIsEnabled().performClick();ui.onNodeWithText("Сохранить").performClick();waitText("База товаров")
  val row=runBlocking{db.shoppingListEntryDao().getById(entry)!!};assertEquals(second,row.assignedBranchId);assertEquals(2.0,row.quantity,0.0);assertEquals(1,runBlocking{db.shoppingListEntryDao().getAllSync().size})
 }
 @Test fun priorityIsOptionalAndWorksInAllOutletsMode(){
  settings();ui.onNodeWithContentDescription("Выше приоритет точки: Молокова, 54").assertDoesNotExist();ui.onNodeWithText("Приоритет точек").performScrollTo().performClick();ui.onNodeWithContentDescription("Выше приоритет точки: Молокова, 54").performScrollTo().performClick();shot("04-outlet-priority")
  ui.onNodeWithText("Применить").performClick();waitFor{runBlocking{db.shoppingListEntryDao().getById(entry)!!.assignedBranchId==second}};assertFalse(runBlocking{db.productShopLinkDao().getLinksForProductSync(product).single().restrictBranches})
 }
 @Test fun cancelledShopDialogDoesNotChangeCardOrPurchases(){
  settings();restrict();ui.onNode(isToggleable() and hasText("Весны, 1")).performScrollTo().performClick();ui.onNodeWithText("Отмена").performClick();ui.onNodeWithText("Сохранить").performClick();waitText("База товаров");assertFalse(runBlocking{db.productShopLinkDao().getLinksForProductSync(product).single().restrictBranches});assertEquals(first,runBlocking{db.shoppingListEntryDao().getById(entry)!!.assignedBranchId})
 }
 @Test fun galleryPhotoHasPencilAndDeleteConfirmationProtectsIt(){
  galleryPhoto();ui.onNodeWithTag("product-photo").performScrollTo();ui.onNodeWithContentDescription("Изменить фото").assertIsDisplayed();ui.onNodeWithText("Заменить фото").assertDoesNotExist();ui.onNodeWithText("Удалить фото").assertDoesNotExist();shot("05-photo-pencil")
  ui.onNodeWithContentDescription("Изменить фото").performClick();ui.onNodeWithText("Удалить").performClick();waitText("Удалить фото?");shot("06-photo-confirmation");ui.onNodeWithText("Отмена").performClick();ui.onNodeWithContentDescription("Изменить фото").assertExists()
  ui.onNodeWithText("Сохранить").performClick();waitText("База товаров");val path=runBlocking{db.productDao().getById(product)!!.photoUri!!};assertTrue(File(path).isFile)
  ui.onNodeWithText("Сыр косичка").performClick();waitText("Название товара");ui.onNodeWithContentDescription("Изменить фото").performScrollTo().performClick();ui.onNodeWithText("Удалить").performClick();ui.onNodeWithText("Удалить",substring=false).performClick();ui.onNodeWithContentDescription("Выбрать фото").assertExists();ui.onNodeWithText("Сохранить").performClick();waitText("База товаров");assertNull(runBlocking{db.productDao().getById(product)!!.photoUri});assertTrue("Removal does not immediately erase immutable media",File(path).isFile)
 }
 @Test fun cameraCancellationKeepsExistingPhoto(){
  galleryPhoto();ui.onNodeWithContentDescription("Изменить фото").performScrollTo().performClick();ui.onNodeWithText("Камера").performClick();val shadow=Shadows.shadowOf(ui.activity);val request=shadow.nextStartedActivityForResult;assertEquals(android.provider.MediaStore.ACTION_IMAGE_CAPTURE,request.intent.action)
  ui.runOnUiThread{shadow.receiveResult(request.intent,android.app.Activity.RESULT_CANCELED,null)};ui.onNodeWithContentDescription("Изменить фото").assertExists();ui.onNodeWithText("Сохранить").performClick();waitText("База товаров");assertNotNull(runBlocking{db.productDao().getById(product)!!.photoUri})
 }
 @Test fun productUsesSharedColorFieldAndPersistsCustomHex(){
  ui.onNodeWithContentDescription("Цвет товара").performScrollTo().performClick();ui.onNodeWithTag("appearance-custom-toggle").performScrollTo().performClick();ui.onNodeWithTag("appearance-options").performScrollToNode(hasTestTag("appearance-color-field"));ui.onNodeWithTag("appearance-color-field").performTouchInput{click(percentOffset(.25f,.2f))};shot("07-product-color-field")
  ui.onNodeWithTag("appearance-hex").performScrollTo().performTextReplacement("#BADHEX");ui.onNodeWithText("Применить оформление").assertIsNotEnabled();ui.onNodeWithTag("appearance-hex").performTextReplacement("#D6C67A");ui.onNodeWithText("Применить оформление").performClick();ui.onNodeWithText("Сохранить").performClick();waitText("База товаров");assertEquals("#D6C67A",runBlocking{db.productDao().getById(product)!!.accent})
 }
 @Test fun personalStoreOrderOnlyAppearsForMultipleLinksAndDisappearsAfterRemoval(){
  runBlocking{db.shopDao().insert(ShopEntity(name="Батон"))}
  ui.onNodeWithContentDescription("Редактировать").performScrollTo().performClick();ui.onNodeWithText("Свой порядок магазинов").assertDoesNotExist()
  ui.onNodeWithText("Добавить магазин").performScrollTo().performClick();ui.onNodeWithText("Магазин").performClick();ui.onNodeWithText("Батон").performClick();ui.onNodeWithText("Добавить").performClick()
  waitFor{ui.onAllNodesWithText("Свой порядок магазинов").fetchSemanticsNodes().isNotEmpty()};ui.onNodeWithText("Свой порядок магазинов").performScrollTo().assertIsDisplayed()
  ui.onAllNodesWithContentDescription("Удалить связь").onLast().performScrollTo().performClick();waitFor{ui.onAllNodesWithText("Свой порядок магазинов").fetchSemanticsNodes().isEmpty()}
  ui.onNodeWithContentDescription("Удалить связь").performScrollTo().performClick();ui.onNodeWithText("Свой порядок магазинов").assertDoesNotExist()
 }

}
