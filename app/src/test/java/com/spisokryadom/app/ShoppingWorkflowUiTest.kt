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
class ShoppingWorkflowUiTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 private val app get()=ApplicationProvider.getApplicationContext<SpisokRyadomApp>()
 private val db get()=app.container.database
 private var baton=0L;private var yar=0L;private var near=0L;private var far=0L;private var apple=0L;private var pasta=0L
 private fun waitText(text:String){ui.waitUntil(20_000){ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
 private fun shot(name:String){ui.runOnIdle{val v=ui.activity.window.decorView;val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b));val f=File("../.local/workflows-0.3.0/$name-api${android.os.Build.VERSION.SDK_INT}.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 @Before fun setup() {
  listOf("drafts","shopping_list_prefs","product_db_prefs").forEach{app.getSharedPreferences(it,0).edit().clear().commit()}
  runBlocking {
   db.shoppingListEntryDao().deleteAll();db.productDao().deleteAll();db.productGroupDao().deleteAll();db.shopDao().deleteAll();db.shopDepartmentDao().clearTypes()
   db.cityDao().update(CityEntity(1,"Красноярск"));app.container.locations.select(1)
   baton=db.shopDao().insert(ShopEntity(name="Батон"))
   yar=db.shopDao().insert(ShopEntity(name="Красный Яр"))
   near=db.shopBranchDao().insert(ShopBranchEntity(shopId=yar,address="Мира, 10",displayOrder=0))
   far=db.shopBranchDao().insert(ShopBranchEntity(shopId=yar,address="Ленина, 20",displayOrder=1))
   apple=db.productDao().insert(ProductEntity(name="Яблоки Гала",defaultUnit="кг"))
   pasta=db.productDao().insert(ProductEntity(name="Спагетти",defaultUnit="уп"))
   db.productShopLinkDao().insert(ProductShopLinkEntity(productId=apple,shopId=yar))
   db.productShopLinkDao().insert(ProductShopLinkEntity(productId=pasta,shopId=yar))
  }
  waitText("Список пуст")
 }
 @After fun close(){ui.activityRule.scenario.close();app.container.locations.closeForTest();db.close();org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null)}

 @Test fun catalogueGroupAndBasketGroupRemainCollapsedAfterRecreate() {
  runBlocking {
   val pharma=db.productGroupDao().insert(ProductGroupEntity(name="Аптека",displayOrder=0))
   val food=db.productGroupDao().insert(ProductGroupEntity(name="Продукты",displayOrder=1))
   val medicine=db.productDao().insert(ProductEntity(name="Лекарство",productGroupId=pharma))
   val a=db.productDao().getById(apple)!!;db.productDao().update(a.copy(productGroupId=food))
   app.container.shoppingListRepository.addProduct(medicine,1.0)
   app.container.shoppingListRepository.addProduct(apple,2.0)
  }
  ui.onNodeWithText("Товары").performClick();waitText("Товаров: 3")
  ui.onNodeWithText("Группы").performClick();waitText("Аптека")
  ui.onNodeWithContentDescription("Свернуть: Аптека").performClick()
  ui.onNodeWithText("Лекарство").assertDoesNotExist();ui.onNodeWithText("Продукты").assertIsDisplayed()
  ui.activityRule.scenario.recreate();waitText("Аптека")
  ui.onNodeWithText("Лекарство").assertDoesNotExist();shot("01-catalog-collapsed")
  ui.onNodeWithText("Покупки").performClick();waitText("Яблоки Гала")
  ui.onNodeWithText("Группы").performClick();waitText("Аптека")
  ui.onNodeWithContentDescription("Свернуть: Аптека").performClick()
  ui.onNodeWithText("Лекарство").assertDoesNotExist()
  ui.activityRule.scenario.recreate();waitText("Аптека")
  ui.onNodeWithText("Лекарство").assertDoesNotExist();shot("02-basket-collapsed")
  ui.onNodeWithContentDescription("Развернуть: Аптека").performClick();ui.onNodeWithText("Лекарство").assertIsDisplayed()
 }

 @Test fun textEditsStayDuringRecreationButAreDiscardedOnClosing(){
  val ids=runBlocking {
   app.container.shoppingListRepository.addProduct(apple,2.0) to app.container.shoppingListRepository.addProduct(pasta,1.0).also{app.container.shoppingListRepository.markBought(it)}
  }
  waitText("Яблоки Гала");ui.onNodeWithContentDescription("Текстовый список").performClick()
  ui.waitUntil(20_000){ui.onAllNodes(hasSetTextAction() and hasText("Яблоки Гала",substring=true)).fetchSemanticsNodes().isNotEmpty()}
  val editor=ui.onNodeWithTag("bulk-editor")
  editor.assertTextContains("Яблоки Гала — 2 кг",substring=true)
  assertFalse(editor.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text.contains("Спагетти"))
  editor.performTextReplacement("Яблоки для друга — 3,5 кг")
  ui.activityRule.scenario.recreate();waitText("Ваш список")
  editor.assertTextContains("Яблоки для друга — 3,5 кг")
  ui.onNodeWithText("Проверить изменения").assertDoesNotExist()
  ui.onNodeWithContentDescription("Назад").performClick();waitText("Яблоки Гала")
  runBlocking {assertEquals(2.0,db.shoppingListEntryDao().getById(ids.first)!!.quantity,0.0);assertTrue(db.shoppingListEntryDao().getById(ids.second)!!.isBought);assertEquals(2,db.shoppingListEntryDao().getAllSync().size)}
  ui.onNodeWithContentDescription("Текстовый список").performClick()
  ui.waitUntil(20_000){ui.onAllNodes(hasSetTextAction() and hasText("Яблоки Гала — 2 кг",substring=true)).fetchSemanticsNodes().isNotEmpty()}
  editor.assertTextContains("Яблоки Гала — 2 кг",substring=true)
 }

 @Test fun groupSelectAndMoveTransferPendingToAChainAddressWithoutTouchingBought() {
  val ids=runBlocking {
   val a=app.container.shoppingListRepository.addProduct(apple,2.0,choice=ShopChoice.EXPLICIT,shopId=baton)
   val p=app.container.shoppingListRepository.addProduct(pasta,1.0,choice=ShopChoice.EXPLICIT,shopId=baton)
   val bought=db.shoppingListEntryDao().insert(ShoppingListEntryEntity(productId=apple,quantity=10.0,unit="кг",assignedShopId=baton,isBought=true))
   listOf(a,p,bought)
  }
  waitText("Яблоки Гала")
  ui.onNodeWithContentDescription("Действия магазина: Батон").performClick()
  ui.onNodeWithContentDescription("Выбрать некупленные: Батон").performClick()
  ui.onNodeWithText("Выбрано: 2").assertIsDisplayed()
  ui.onNodeWithContentDescription("Переместить выбранные").performClick()
  waitText("Сменить место покупки")
  ui.waitUntil(20_000){ui.onAllNodesWithTag("place-option-$yar:$far").fetchSemanticsNodes().isNotEmpty()}
  ui.onNodeWithTag("place-option-$yar:$far").performClick()
  // Dialogs are separate windows: screenshot in the dedicated capture below.
  ui.runOnIdle {val v=org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView;val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b));val f=File("../.local/workflows-0.3.0/04-move-dialog-api${android.os.Build.VERSION.SDK_INT}.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
  ui.onNodeWithText("Сменить").performClick()
  ui.waitUntil(20_000){runBlocking{db.shoppingListEntryDao().getById(ids[0])!!.assignedBranchId==far}}
  runBlocking {assertEquals(yar,db.shoppingListEntryDao().getById(ids[1])!!.assignedShopId);assertEquals(far,db.shoppingListEntryDao().getById(ids[1])!!.assignedBranchId);assertEquals(baton,db.shoppingListEntryDao().getById(ids[2])!!.assignedShopId);assertTrue(db.shoppingListEntryDao().getById(ids[2])!!.isBought)}
  waitText("Красный Яр");shot("05-moved-basket")
  ui.onNodeWithContentDescription("Переместить: Спагетти").assertDoesNotExist()
  ui.onNodeWithText("Спагетти").performTouchInput{longClick()}
  ui.onNodeWithText("Сделать срочным").performClick()
  ui.waitUntil(20_000){runBlocking{db.shoppingListEntryDao().getById(ids[1])!!.isUrgent}}

 }

 @Test fun storeEditorOffersMultipleRankedAddressesAndProductEditorCanRestrictPoints() {
  ui.onNodeWithText("Магазины").performClick();waitText("Красный Яр")
  ui.onNodeWithText("Красный Яр").performTouchInput{longClick()}
  waitText("Место магазина")
  ui.onNodeWithText("Место магазина").performScrollTo()
  waitText("Мира, 10");ui.onNodeWithText("Мира, 10").assertExists();shot("06-chain-addresses")
  ui.onNodeWithText("Править").performScrollTo().performClick()
  ui.onNodeWithContentDescription("Точка выше: Ленина, 20").performScrollTo().performClick()
  ui.onNodeWithText("Сохранить").performClick();waitText("Магазины")
  assertEquals(far,runBlocking{db.shopBranchDao().forShop(yar)}.first().id)
  ui.onNodeWithText("Товары").performClick();waitText("Товаров: 2")
  ui.onNodeWithText("Спагетти").performTouchInput{longClick()}
  waitText("Точки продажи товара");ui.onNodeWithText("Точки продажи товара").performScrollTo()
  ui.onNodeWithText("Только выбранные точки").performScrollTo().assertIsDisplayed()
  ui.onNode(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role,androidx.compose.ui.semantics.Role.Switch)).performClick()
  ui.onNodeWithText("Мира, 10").performScrollTo();shot("07-product-points")
  ui.onNodeWithText("Сохранить").performClick();waitText("Товаров: 2")
  runBlocking {assertTrue(db.productShopLinkDao().getLinksForProductSync(pasta).single().restrictBranches);assertEquals(far,db.shopBranchDao().availability(pasta).single().branchId)}
 }
}
