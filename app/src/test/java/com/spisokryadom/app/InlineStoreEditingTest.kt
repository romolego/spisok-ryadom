package com.spisokryadom.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
class InlineStoreEditingTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 private val app get()=ApplicationProvider.getApplicationContext<SpisokRyadomApp>()
 private val db get()=app.container.database
 private var shop=0L;private var first=0L;private var second=0L
 private fun waitText(text:String){ui.waitUntil(20_000){ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
 private fun shot(name:String){ui.runOnIdle{val v=ui.activity.window.decorView;val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b));val f=File("../.local/ux-0.3.1/$name-api${android.os.Build.VERSION.SDK_INT}.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 private fun openStore(){waitText("Магазины");ui.onNodeWithText("Магазины").performClick();waitText("Командор");ui.onNodeWithText("Командор").performTouchInput{longClick()};waitText("Название магазина")}
 @Before fun setup(){
  listOf("drafts","shopping_list_prefs","product_db_prefs").forEach{app.getSharedPreferences(it,0).edit().clear().commit()}
  runBlocking{
   db.shoppingListEntryDao().deleteAll();db.productDao().deleteAll();db.shopDao().deleteAll()
   db.cityDao().update(CityEntity(1,"Красноярск"));app.container.locations.select(1)
   shop=db.shopDao().insert(ShopEntity(name="Командор",address="Весны, 7",displayOrder=0))
   db.shopDao().insert(ShopEntity(name="Батон",displayOrder=1))
   first=db.shopBranchDao().insert(ShopBranchEntity(shopId=shop,address="Весны, 7",displayOrder=0))
   second=db.shopBranchDao().insert(ShopBranchEntity(shopId=shop,address="Молокова, 19",displayOrder=1))
   db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=shop,name="Продукты",displayOrder=0))
  };waitText("Список пуст")
 }
 @After fun close(){ui.activityRule.scenario.close();app.container.locations.closeForTest();db.close();org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null)}
 @Test fun addressEditingIsInlineGatedAndKeepsIdentityWhileReordering(){
  openStore()
  ui.onNodeWithText("Адреса и точки магазина").performScrollTo()
  ui.onNodeWithText("Для сети",substring=true).assertDoesNotExist()
  ui.onNodeWithContentDescription("Точка выше: Молокова, 19").assertDoesNotExist()
  ui.onNodeWithContentDescription("Удалить точку: Весны, 7").assertDoesNotExist()
  shot("01-addresses-view")
  ui.onNodeWithContentDescription("Изменить адрес: Молокова, 19").performScrollTo().performClick()
  ui.onAllNodes(isDialog()).assertCountEquals(0)
  ui.onNodeWithTag("branch-address-$second").performTextReplacement("Молокова, 19 (около старой квартиры)")
  ui.onNodeWithContentDescription("Точка выше: Молокова, 19 (около старой квартиры)").performScrollTo().performClick()
  ui.onNodeWithTag("branch-address-$second").performScrollTo().assertTextContains("Молокова, 19 (около старой квартиры)")
  ui.onNodeWithContentDescription("Точка ниже: Весны, 7").assertDoesNotExist()
  assertEquals(first,runBlocking{db.shopBranchDao().forShop(shop)}.first().id)
  shot("02-address-inline")
  ui.onNodeWithContentDescription("Завершить редактирование адреса").performScrollTo().performClick()
  ui.onNodeWithContentDescription("Удалить точку: Молокова, 19 (около старой квартиры)").assertDoesNotExist()
  ui.onNodeWithText("Сохранить").performClick();waitText("Магазины")
  runBlocking{val rows=db.shopBranchDao().forShop(shop);assertEquals(listOf(second,first),rows.map{it.id});assertEquals("Молокова, 19 (около старой квартиры)",rows.first().address);assertEquals(rows.first().address,db.shopDao().getById(shop)!!.address)}
  ui.onNodeWithContentDescription("Изменить порядок").performClick()
  ui.onAllNodesWithContentDescription("Переместить вверх").onFirst().assertIsNotEnabled()
  ui.onAllNodesWithContentDescription("Переместить вниз").onFirst().performClick()
  ui.waitUntil(20_000){runBlocking{db.shopDao().getAllSync().sortedBy{it.displayOrder}.first().name=="Батон"}}
  shot("03-store-reorder")
 }
 @Test fun addingAndDeletingAddressesNeverOpensAnInputDialogAndBackDiscardsDraft(){
  openStore()
  ui.onNodeWithText("Добавить адрес").performScrollTo().performClick()
  ui.onAllNodes(isDialog()).assertCountEquals(0)
  ui.onNodeWithText("Сохранить").assertIsNotEnabled()
  val newField=ui.onNode(hasSetTextAction() and hasText("Адрес"))
  newField.performTextReplacement("Ленина, 20")
  ui.onNodeWithText("Сохранить").assertIsEnabled()
  ui.onNodeWithContentDescription("Удалить точку: Ленина, 20").performScrollTo().performClick()
  ui.onNodeWithText("Ленина, 20").assertDoesNotExist()
  ui.onNodeWithContentDescription("Изменить адрес: Весны, 7").performScrollTo().performClick()
  ui.onNodeWithTag("branch-address-$first").performTextReplacement("Другой адрес")
  ui.onNodeWithContentDescription("Назад").performClick();waitText("Магазины")
  assertEquals("Весны, 7",runBlocking{db.shopBranchDao().get(first)}!!.address)
 }
 @Test fun groupRenameUsesTheFormAndInstructionalCopyIsAbsentAcrossScreens(){
  ui.onNodeWithText("Нажмите +",substring=true).assertDoesNotExist()
  ui.onNodeWithContentDescription("Настройки").performClick();waitText("Настройки")
  ui.onNodeWithText("Сохраните покупки",substring=true).assertDoesNotExist()
  ui.onNodeWithText("Магазины подбираются",substring=true).assertDoesNotExist()
  shot("04-settings")
  ui.onNodeWithContentDescription("Назад").performClick()
  ui.onNodeWithContentDescription("Текстовый список").performClick();waitText("Проверить изменения")
  ui.onNodeWithText("Только некупленные позиции.",substring=true).assertDoesNotExist()
  assertEquals("",ui.onNodeWithTag("bulk-editor").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text)
  shot("05-text-view")
  ui.onNodeWithContentDescription("Назад").performClick()
  openStore()
  ui.onNodeWithText("Группы магазина").performScrollTo()
  ui.onNodeWithContentDescription("Редактировать").performScrollTo().performClick()
  ui.onNode(hasSetTextAction() and hasText("Название группы")).performScrollTo().performTextReplacement("Еда")
  ui.onAllNodes(isDialog()).assertCountEquals(0)
  assertEquals("Продукты",runBlocking{db.shopDepartmentDao().getByShopIdSync(shop)}.single().name)
  shot("06-group-inline")
  ui.onNodeWithText("Сохранить").performClick();waitText("Магазины")
  assertEquals("Еда",runBlocking{db.shopDepartmentDao().getByShopIdSync(shop)}.single().name)
 }
}

