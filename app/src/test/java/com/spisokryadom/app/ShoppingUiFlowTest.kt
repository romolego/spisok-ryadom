package com.spisokryadom.app
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.entity.*
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.After
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=SpisokRyadomApp::class,qualifiers="w411dp-h891dp-xhdpi")
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShoppingUiFlowTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 private fun waitText(text:String){ui.waitUntil(20_000){ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
 private fun capture(name:String){ui.runOnIdle{val view=ui.activity.window.decorView;val b=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(b));val f=File("../.local/ui-verification/$name.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 @After fun cleanup(){val app=ApplicationProvider.getApplicationContext<SpisokRyadomApp>();ui.activityRule.scenario.close();app.container.locations.closeForTest();app.container.database.close();org.robolectric.util.ReflectionHelpers.setStaticField(com.spisokryadom.app.data.db.AppDatabase::class.java,"INSTANCE",null)}
 @Test fun listTextReviewCitySwitchAndBuying() {
  val app=ApplicationProvider.getApplicationContext<SpisokRyadomApp>();val c=app.container
  runBlocking {
   c.database.shoppingListEntryDao().deleteAll();c.database.productDao().deleteAll();c.database.shopDao().deleteAll()
   c.database.cityDao().update(CityEntity(1,"Красноярск"));c.database.cityDao().insert(CityEntity(2,"Иркутск"));c.locations.select(1)
   val shop=c.database.shopDao().insert(ShopEntity(name="Батон",cityId=1));val irkutsk=c.database.shopDao().insert(ShopEntity(name="Слата",cityId=2))
   listOf("Бананы","Яблоки Гала").forEach{name->val id=c.database.productDao().insert(ProductEntity(name=name,defaultUnit="кг"));c.database.productShopLinkDao().insert(ProductShopLinkEntity(productId=id,shopId=shop));c.database.productShopLinkDao().insert(ProductShopLinkEntity(productId=id,shopId=irkutsk,priority=2))}
  }
  waitText("Список пуст");capture("01-empty")
  runBlocking{
   val ps=c.database.productDao().getAllSync()
   c.shoppingListRepository.addProduct(ps.single{it.name=="Бананы"}.id,4.0)
   c.shoppingListRepository.addProduct(ps.single{it.name=="Яблоки Гала"}.id,.5)
   c.shoppingListRepository.addNewProduct("Молоко",2.0,"л",true)
  }
  waitText("Яблоки Гала")
  ui.onNodeWithContentDescription("Текстовый список").performClick();waitText("Ваш список")
  ui.onNodeWithTag("bulk-editor").performTextReplacement("Текст только для отправки")
  ui.activityRule.scenario.recreate();waitText("Ваш список")
  ui.onNodeWithTag("bulk-editor").assertTextContains("Текст только для отправки")
  ui.onNodeWithText("Сохранить изменения").assertDoesNotExist()
  ui.onNodeWithContentDescription("Назад").performClick();waitText("Яблоки Гала");capture("03-basket")
  runBlocking{assertEquals(3,c.database.shoppingListEntryDao().getAllSync().size);val apples=c.database.productDao().getAllSync().single{it.name=="Яблоки Гала"};assertEquals(.5,c.database.shoppingListEntryDao().getAllSync().single{it.productId==apples.id}.quantity,0.0)}
  ui.onNodeWithContentDescription("Купить: Бананы").performClick();ui.waitUntil(20_000){ui.onAllNodesWithContentDescription("Вернуть в покупки: Бананы").fetchSemanticsNodes().isNotEmpty()}
  ui.onNodeWithText("Город: Красноярск").assertDoesNotExist()
  ui.onNodeWithContentDescription("Настройки").performClick();waitText("Красноярск")
  ui.onNodeWithContentDescription("Изменить город").performClick();ui.onNodeWithText("Иркутск",substring=true).performClick()
  waitText("Иркутск");ui.onNodeWithContentDescription("Назад").performClick()
  // Changing the settings city reassigns pending automatic rows; bought facts stay unchanged.
  waitText("Слата")
  runBlocking{assertTrue(c.database.shoppingListEntryDao().getAllSync().filter{it.assignedShopId!=null&&!it.isBought}.all{e->c.database.shopDao().getById(e.assignedShopId!!)!!.cityId==2L})}
  runBlocking{val bought=c.database.shoppingListEntryDao().getAllSync().single{it.isBought};assertEquals(1L,c.database.shopDao().getById(bought.assignedShopId!!)!!.cityId)}
  ui.onNodeWithContentDescription("Настройки").performClick();waitText("Сохранить полную копию");capture("04-settings")
 }
}
