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
import com.spisokryadom.app.util.FavoriteAppearance
import com.spisokryadom.app.util.FavoriteStyle
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
class Audit270StoresUiTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 private val app get()=ApplicationProvider.getApplicationContext<SpisokRyadomApp>()
 private val db get()=app.container.database
 private var first=0L;private var second=0L
 private fun waitFor(c:()->Boolean){ui.waitUntil(25_000){Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(20));ui.waitForIdle();c()}}
 private fun tab(text:String){ui.onNode(hasText(text) and SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.Tab)).performClick()}
 private fun stores(){tab("Магазины");waitFor{ui.onAllNodesWithTag("shop-order-$first-0").fetchSemanticsNodes().isNotEmpty()}}
 private fun card(){stores();ui.onNodeWithText("Ближний магазин").performTouchInput{longClick()};waitFor{ui.onAllNodesWithText("Название магазина").fetchSemanticsNodes().isNotEmpty()}}
 private fun shot(name:String){ui.runOnIdle{
  val view=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf{it.isShowing}?.window?.decorView?:ui.activity.window.decorView
  val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))
  val file=File("build/reports/audit270/screens/$name-api${android.os.Build.VERSION.SDK_INT}.png");file.parentFile!!.mkdirs();file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
 }}
 @Before fun seed(){runBlocking{
  listOf("drafts","product_card_drafts","shopping_list_prefs","product_db_prefs").forEach{app.getSharedPreferences(it,0).edit().clear().commit()}
  db.shoppingListEntryDao().deleteAll();db.goalListDao().deleteAll();db.productDao().deleteAll();db.shopDao().deleteAll();db.retailStructureDao().clearVenues();db.retailStructureDao().clearNetworks()
  first=db.shopDao().insert(ShopEntity(name="Ближний магазин",displayOrder=0))
  second=db.shopDao().insert(ShopEntity(name="Другой магазин",displayOrder=1))
 }}
 @After fun close(){ui.activityRule.scenario.close();app.container.locations.closeForTest();db.close();org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null)}
 @Test fun storeFavoriteAndAppearanceShareOneRowAndOnlySaveCommitsTheFavorite(){
  card()
  val favorite=ui.onNodeWithTag("favorite-toggle").getUnclippedBoundsInRoot()
  val palette=ui.onNodeWithContentDescription("Оформление магазина").getUnclippedBoundsInRoot()
  val name=ui.onNodeWithText("Название магазина").getUnclippedBoundsInRoot()
  assertTrue(palette.left>=favorite.right);assertTrue(kotlin.math.abs((palette.top.value+palette.bottom.value-favorite.top.value-favorite.bottom.value)/2)<20)
  assertTrue(palette.top>name.top)
  ui.onNodeWithTag("favorite-toggle").performClick();assertFalse(runBlocking{db.shopDao().getById(first)!!.isFavorite})
  ui.onNodeWithContentDescription("Оформление магазина").performClick();ui.onNodeWithTag("shop-background-switch").performClick()
  ui.onNodeWithText("Отмена").performClick();assertEquals("",runBlocking{db.shopDao().getById(first)!!.shoppingBackground})
  shot("store-favorite-and-palette")
  ui.onNodeWithText("Сохранить").performClick();waitFor{ui.onAllNodesWithTag("favorite-shop-$first").fetchSemanticsNodes().isNotEmpty()}
  assertTrue(runBlocking{db.shopDao().getById(first)!!.isFavorite});assertEquals(listOf(first,second),runBlocking{db.shopDao().getAllSync().map{it.id}})
  assertEquals(2,ui.onAllNodesWithText("Ближний магазин").fetchSemanticsNodes().size)
  shot("store-favorite-pinned-and-full")
 }
 @Test fun pinnedOrderDoesNotChangeTheRouteAndUsesIndependentStarPolicy(){
  runBlocking{app.container.shopRepository.saveFavoriteFixture(first,true);app.container.shopRepository.saveFavoriteFixture(second,true)}
  FavoriteAppearance(block=FavoriteStyle(star=false),row=FavoriteStyle(star=true)).save(app.getSharedPreferences("shopping_list_prefs",0))
  stores();waitFor{ui.onAllNodesWithTag("favorite-shop-$second").fetchSemanticsNodes().isNotEmpty()}
  ui.onNode(hasContentDescription("Избранный магазин: Ближний магазин") and hasAnyAncestor(hasTestTag("favorite-shop-$first"))).assertDoesNotExist()
  ui.onNode(hasContentDescription("Избранный магазин: Ближний магазин") and hasAnyAncestor(hasTestTag("shop-order-$first-0"))).assertExists()
  ui.onNodeWithContentDescription("Управление магазинами").performClick();ui.onNodeWithText("Изменить порядок").performClick()
  ui.onNode(hasContentDescription("Переместить вверх") and hasAnyAncestor(hasTestTag("favorite-shop-$second"))).performClick()
  waitFor{runBlocking{db.shopDao().getById(second)!!.favoriteOrder<db.shopDao().getById(first)!!.favoriteOrder}}
  assertEquals(listOf(first,second),runBlocking{db.shopDao().getAllSync().map{it.id}})
  assertTrue(ui.onNodeWithTag("favorite-shop-$second").getUnclippedBoundsInRoot().top<ui.onNodeWithTag("favorite-shop-$first").getUnclippedBoundsInRoot().top)
  shot("store-favorites-independent-style-order")
 }
}
