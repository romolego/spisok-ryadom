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

/** Real navigation must carry an exact physical block, including its absent address. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=SpisokRyadomApp::class,qualifiers="w360dp-h640dp-xhdpi")
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AuditUiNavigation071Test {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 private val app get()=ApplicationProvider.getApplicationContext<SpisokRyadomApp>()
 private val db get()=app.container.database
 private var shop=0L
 private var product=0L
 private var entry=0L
 private fun waitText(text:String){ui.waitUntil(20_000){ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
 @Before fun setup()=runBlocking{
  listOf("drafts","shopping_list_prefs","product_db_prefs").forEach{app.getSharedPreferences(it,0).edit().clear().commit()}
  db.shoppingListEntryDao().deleteAll();db.productDao().deleteAll();db.productGroupDao().deleteAll();db.shopDao().deleteAll();db.shopDepartmentDao().clearTypes()
  db.retailStructureDao().clearVenues();db.retailStructureDao().clearNetworks();app.container.locations.select(1)
  shop=db.shopDao().insert(ShopEntity(name="Батон"));db.shopBranchDao().insert(ShopBranchEntity(shopId=shop,address="Весны, 7"))
  product=app.container.productRepository.saveWithLinks(ProductEntity(name="Чай",defaultUnit="уп",defaultQuantity=1.0),listOf(ProductShopLinkEntity(productId=0,shopId=shop)))
  entry=app.container.shoppingListRepository.addProduct(product,choice=ShopChoice.EXPLICIT,shopId=shop,branchId=0)
  Unit
 }
 @After fun close(){ui.activityRule.scenario.close();app.container.locations.closeForTest();db.close();org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null)}
 private fun shot(){ui.waitForIdle();ui.runOnIdle{
  val view=ui.activity.window.decorView;val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))
  val file=File("../.local/audit-0.7.1/no-address-add-api${android.os.Build.VERSION.SDK_INT}.png");file.parentFile!!.mkdirs();file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
 }}
 @Test fun addingFromNoAddressBlockKeepsThatExactBlockAcrossNavigation(){
  waitText("Батон");waitText("Чай")
  ui.onNodeWithContentDescription("Действия магазина: Батон").performClick()
  ui.onNodeWithContentDescription("Добавить товар в Батон").performClick();waitText("Добавить покупку")
  ui.onNodeWithText("Название товара").performTextReplacement("Чай")
  // Browsed rows stay visible while the debounced query runs, but cannot be selected
  // until the new result is ready. Mere existence does not mean this row is actionable.
  ui.waitUntil(20_000){runCatching{ui.onNodeWithTag("candidate-all-$product").assertIsEnabled()}.isSuccess}
  ui.onNodeWithTag("candidate-all-$product").performClick();waitText("Выбран")
  ui.onNodeWithText("Добавить в список").performScrollTo().assertIsEnabled().performClick();waitText("Список покупок")
  ui.waitUntil(20_000){runBlocking{db.shoppingListEntryDao().getAllSync().filter{it.productId==product}.sumOf{it.quantity}==2.0}}
  runBlocking{
   val purchases=db.shoppingListEntryDao().getAllSync().filter{it.productId==product}
   assertEquals("The header action must not create a purchase at the preferred address",1,purchases.size)
   assertEquals(entry,purchases.single().id);assertEquals(shop,purchases.single().assignedShopId);assertNull(purchases.single().assignedBranchId)
  }
  shot()
 }
}
