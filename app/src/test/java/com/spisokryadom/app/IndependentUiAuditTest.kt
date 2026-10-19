package com.spisokryadom.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ApplicationProvider
import androidx.room.withTransaction
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

/** Regression expectations from the independent audit, exercised through real app navigation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35], application=SpisokRyadomApp::class, qualifiers="w411dp-h891dp-xhdpi")
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IndependentUiAuditTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 @Before fun reset()=runBlocking {
  val c=ApplicationProvider.getApplicationContext<SpisokRyadomApp>().container
  c.database.withTransaction {
   c.database.shoppingListEntryDao().deleteAll();c.database.productShopLinkDao().deleteAll();c.database.shopDepartmentDao().deleteAll()
   c.database.productDao().deleteAll();c.database.shopDao().deleteAll();c.database.productGroupDao().deleteAll();c.database.recipientDao().deleteAll();c.database.unitDao().deleteAll();c.database.cityDao().deleteAll()
   c.database.cityDao().insert(CityEntity(1,"Мой город"))
  }
  c.locations.select(1)
 }
 private fun waitText(text:String){ui.waitUntil(20_000){ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
 private fun shot(name:String){ui.runOnIdle{
  val v=ui.activity.window.decorView
  val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b))
  val f=File("../.local/independent-audit/$name.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
 }}
 @After fun cleanup(){val app=ApplicationProvider.getApplicationContext<SpisokRyadomApp>();ui.activityRule.scenario.close();app.container.locations.closeForTest();app.container.database.close();org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null)}
 @Test fun inlineQuantityEditorMustRemainOpenForDecimalInput() {
  val app=ApplicationProvider.getApplicationContext<SpisokRyadomApp>();val db=app.container.database
  runBlocking{val p=db.productDao().insert(ProductEntity(name="Яблоки Гала",defaultUnit="кг"));app.container.shoppingListRepository.addProduct(p,3.0,"кг")}
  ui.onNodeWithText("Товары").performClick();waitText("Яблоки Гала");waitText("3")
  ui.onNodeWithText("3").performClick()
  shot("inline-editor-after-open")
  println(ui.onRoot(useUnmergedTree=true).printToString())
  ui.onNode(hasSetTextAction() and hasText("3")).assertExists("Quantity editor must remain open after tapping its number")
  ui.onNode(hasSetTextAction() and hasText("3")).performTextReplacement("0,5")
  shot("comma-before-done")
  ui.onNode(hasSetTextAction() and hasText("0,5")).performImeAction()
  ui.waitForIdle()
  ui.waitUntil(5_000){runBlocking{db.shoppingListEntryDao().getAllSync().single().quantity!=3.0}}
  val actual=runBlocking{db.shoppingListEntryDao().getAllSync().single().quantity}
  println("AUDIT inline quantity input=0,5 actual=$actual expected=0.5")
  assertEquals("Decimal comma must preserve fractional quantity",.5,actual,0.0)
 }
 @Test fun changingDisplayedPurchaseDiscardsThePreviousInlineDraft() {
  val db=ApplicationProvider.getApplicationContext<SpisokRyadomApp>().container.database
  val ids=runBlocking {
   val p=db.productDao().insert(ProductEntity(name="Разные покупки",defaultUnit="кг"))
   val first=db.shoppingListEntryDao().insert(ShoppingListEntryEntity(productId=p,quantity=3.0,unit="кг"))
   val second=db.shoppingListEntryDao().insert(ShoppingListEntryEntity(productId=p,quantity=2.0,unit="шт"))
   first to second
  }
  ui.onNodeWithText("Товары").performClick();waitText("Разные покупки");waitText("Покупок: 2")
  ui.onNodeWithText("Покупок: 2").performClick();waitText("3")
  ui.onNodeWithText("3").performClick()
  ui.onNode(hasSetTextAction() and hasText("3")).performTextReplacement("7")
  runBlocking { db.shoppingListEntryDao().deleteById(ids.first) }
  // The second row was already visible in the expanded representation. Wait for
  // the actual two-to-one UI transition before checking disposal of the first editor.
  ui.waitUntil(20_000){ui.onAllNodesWithText("Покупок: 2").fetchSemanticsNodes().isEmpty()}
  waitText("2")
  ui.onNode(hasSetTextAction() and hasText("7")).assertDoesNotExist()
  assertEquals("Removing the edited purchase must not transfer its draft to another one",2.0,
   runBlocking { db.shoppingListEntryDao().getById(ids.second)!! }.quantity,0.0)
  // Also verify the replacement editor starts from the replacement purchase's own value.
  ui.onNodeWithText("2").performClick()
  ui.onNode(hasSetTextAction() and hasText("2")).assertExists()
  ui.onNode(hasSetTextAction() and hasText("2")).performImeAction()
  ui.waitForIdle()
  assertEquals(2.0,runBlocking { db.shoppingListEntryDao().getById(ids.second)!! }.quantity,0.0)
 }
}
