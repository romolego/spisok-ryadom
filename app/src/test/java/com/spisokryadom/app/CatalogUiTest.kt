package com.spisokryadom.app
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.db.AppDatabase
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
class CatalogUiTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 private fun waitText(text:String){ui.waitUntil(20_000){ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
 private fun shot(name:String){ui.runOnIdle{val v=ui.activity.window.decorView;val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b));val f=File("../.local/ui-verification/$name.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 @After fun cleanup(){val app=ApplicationProvider.getApplicationContext<SpisokRyadomApp>();ui.activityRule.scenario.close();app.container.locations.closeForTest();app.container.database.close();org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null)}
 @Test fun largeCatalogFuzzySearchAndSmallScreenAdding() {
  val app=ApplicationProvider.getApplicationContext<SpisokRyadomApp>();val db=app.container.database
  runBlocking{db.withTransaction{
   db.shoppingListEntryDao().deleteAll();db.productDao().deleteAll()
   val a=db.productGroupDao().insert(ProductGroupEntity(name="Одинаковая группа"));val b=db.productGroupDao().insert(ProductGroupEntity(name="Одинаковая группа"))
   repeat(5000){i->db.productDao().insert(ProductEntity(name="Товар "+i.toString().padStart(4,'0'),productGroupId=if(i%2==0)a else b))}
   db.productDao().insert(ProductEntity(name="Яблоки Гала",defaultUnit="кг",defaultQuantity=.5))
  }}
  ui.onNodeWithText("Товары").performClick();waitText("Товаров: 5001")
  val composed=ui.onAllNodesWithText("Товар ",substring=true).fetchSemanticsNodes().size
  assertTrue("Lazy catalogue must compose a bounded viewport, actual=$composed",composed<35)
  ui.onNodeWithContentDescription("Свернуть: Все товары").performClick()
  assertEquals(0,ui.onAllNodesWithText("Товар ",substring=true).fetchSemanticsNodes().size)
  ui.onNodeWithContentDescription("Развернуть: Все товары").performClick()
  assertTrue("Expanding 5001 products must still compose only the viewport",ui.onAllNodesWithText("Товар ",substring=true).fetchSemanticsNodes().size<35)
  ui.onNode(hasScrollToIndexAction()).performScrollToIndex(5001);ui.onNodeWithText("Яблоки Гала").assertIsDisplayed()
  ui.onNode(hasSetTextAction()).performTextReplacement("яблыки");waitText("Товаров: 1");ui.onNodeWithText("Яблоки Гала").assertIsDisplayed();shot("05-fuzzy-small")
  ui.onNodeWithText("Группы").performClick();ui.onNode(hasSetTextAction()).performTextClearance();waitText("Товаров: 5001")
  ui.onNode(hasScrollToIndexAction()).performScrollToIndex(2600)
  ui.onNodeWithText("Покупки").performClick();ui.onNodeWithContentDescription("Добавить в список").performClick()
  ui.onNode(hasSetTextAction() and hasText("Название товара")).performTextReplacement("яблыки")
  waitText("Яблоки Гала");ui.onNodeWithText("Яблоки Гала").performScrollTo().performClick()
  ui.onNode(hasSetTextAction() and hasText("Количество")).performTextReplacement("0,75")
  ui.onNodeWithText("Добавить в список").performScrollTo().performClick()
  waitText("Список покупок")
  runBlocking{assertEquals(.75,db.shoppingListEntryDao().getAllSync().single().quantity,0.0);assertEquals(5001,db.productDao().getAllSync().size)}
  waitText("Список покупок");waitText("Яблоки Гала")
  ui.onNodeWithText("Яблоки Гала").performClick();waitText("Параметры покупки");ui.onNodeWithText("Параметры покупки").performClick()
  ui.onNode(hasSetTextAction() and hasText("Количество")).performTextReplacement("0,25")
  ui.runOnIdle{val v=org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView;val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b));val f=File("../.local/ui-verification/07-edit-dialog.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
  ui.onNodeWithText("Сохранить").performClick()
  ui.waitForIdle()
  ui.activityRule.scenario.recreate();waitText("Яблоки Гала")
  runBlocking{assertEquals(.25,db.shoppingListEntryDao().getAllSync().single().quantity,0.0)}
  shot("06-small-basket")
 }
}
