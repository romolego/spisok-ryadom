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
@Config(sdk=[28,35],application=SpisokRyadomApp::class,qualifiers="w360dp-h640dp-xhdpi")
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Favorites200SettingsTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 private val app get()=ApplicationProvider.getApplicationContext<SpisokRyadomApp>()
 private val db get()=app.container.database
 private var food=0L;private var health=0L
 private fun waitFor(c:()->Boolean){ui.waitUntil(25_000){Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(25));ui.waitForIdle();c()}}
 private fun text(t:String)=waitFor{ui.onAllNodesWithText(t).fetchSemanticsNodes().isNotEmpty()}
 private fun scroll(m:SemanticsMatcher){ui.onNodeWithTag("settings-list").performScrollToNode(m)}
 private fun shot(name:String){ui.waitForIdle();ui.runOnIdle{val v=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf{it.isShowing}?.window?.decorView?:ui.activity.window.decorView;val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b));val f=File("../.local/refine200/screens/$name-api${android.os.Build.VERSION.SDK_INT}.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 @Before fun seed(){runBlocking{
  app.getSharedPreferences("shopping_list_prefs",0).edit().clear().commit();db.shoppingListEntryDao().deleteAll();db.productDao().deleteAll();db.productGroupDao().deleteAll()
  health=db.productGroupDao().insert(ProductGroupEntity(name="Аптека",displayOrder=1));food=db.productGroupDao().insert(ProductGroupEntity(name="Продукты",displayOrder=0))
 };text("Список покупок");ui.onNodeWithContentDescription("Настройки").performClick();text("Настройки")}
 @After fun close(){ui.activityRule.scenario.close();val end=System.nanoTime()+20_000_000_000;while(ProductEditorTransactions.mutex.isLocked&&System.nanoTime()<end){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();Thread.sleep(5)};app.container.locations.closeForTest();db.close();org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null)}
 @Test fun settingsSeparateHelpDefaultsAndDictionaryLibrary(){
  scroll(hasText("Автосохранение карточек товаров"));ui.onNodeWithText("Если выключено, изменения карточки сохраняются только кнопкой «Сохранить».").assertDoesNotExist();ui.onNodeWithContentDescription("Подсказка: Автосохранение карточек товаров").performClick();ui.onNodeWithText("Если выключено",substring=true).assertIsDisplayed();shot("01-setting-help");ui.onNodeWithText("Понятно").performClick()
  scroll(hasText("Количество, если не заполнено"));ui.onNodeWithText("Количество, если не заполнено").performTextReplacement("2,5");waitFor{app.getSharedPreferences("shopping_list_prefs",0).getString("new_product_missing_quantity","")=="2,5"};shot("02-creation-defaults")
  scroll(hasText("Выпадающие списки"));ui.onNodeWithText("Добавить недостающие группы").assertExists();shot("03-dictionaries-entry")
 }
 @Test fun fullGroupListCannotBeMovedButPinnedCopiesCan(){
  scroll(hasText("Товарные группы"));ui.onNodeWithTag("manager-edit-Товарные группы").performClick();scroll(hasContentDescription("В избранное: Аптека"));ui.onNodeWithContentDescription("Вверх: Аптека").assertDoesNotExist();ui.onNodeWithContentDescription("В избранное: Аптека").performClick()
  scroll(hasContentDescription("В избранное: Продукты"));ui.onNodeWithContentDescription("В избранное: Продукты").performClick();scroll(hasContentDescription("Вверх: Продукты"));ui.onNodeWithContentDescription("Вверх: Продукты").performClick();waitFor{runBlocking{db.productGroupDao().getAllSync().filter{it.isFavorite}.minByOrNull{it.favoriteOrder}?.id==food}}
  shot("04-favorite-groups");assertEquals(2,runBlocking{db.productGroupDao().getAllSync().size});assertEquals(0,runBlocking{db.productGroupDao().getById(food)!!.displayOrder})
 }
 @Test fun appearanceHasIndependentBlockAndFullListControls(){
  scroll(hasText("Оформление избранного"));shot("05-favorite-appearance");ui.onNodeWithContentDescription("Подсказка: Оформление избранного").performClick();ui.onNodeWithText("Верхний блок",substring=true).assertIsDisplayed();ui.onNodeWithText("Понятно").performClick()
  scroll(hasText("В полном списке"));ui.onNodeWithText("В полном списке").assertExists();shot("06-full-list-appearance")
  val stars=ui.onAllNodes(isToggleable() and hasText("Показывать звёздочку"))
  stars[0].performScrollTo().performClick();waitFor{!app.getSharedPreferences("shopping_list_prefs",0).getBoolean("favorite_block_star",true)}
  assertTrue(app.getSharedPreferences("shopping_list_prefs",0).getBoolean("favorite_row_star",true));stars[1].performScrollTo().performClick();waitFor{!app.getSharedPreferences("shopping_list_prefs",0).getBoolean("favorite_row_star",true)}
 }
 @Test fun catalogueShowsSameFavoriteInBothZonesAndUpdatesStyleWithoutChangingRows(){
  runBlocking{db.productDao().insert(ProductEntity(name="Банан",isFavorite=true));db.productDao().insert(ProductEntity(name="Апельсин"))}
  app.getSharedPreferences("product_db_prefs",0).edit().putString("product_db_view_mode","ALPHABETICAL").commit()
  ui.onNodeWithContentDescription("Назад").performClick();ui.onNodeWithText("Товары").performClick();text("База товаров");waitFor{ui.onAllNodesWithText("Банан").fetchSemanticsNodes().size==2};shot("07-catalogue-favorites")
  ui.runOnIdle{com.spisokryadom.app.util.FavoriteAppearance(block=com.spisokryadom.app.util.FavoriteStyle(star=false,bold=false),row=com.spisokryadom.app.util.FavoriteStyle(star=true,bold=true)).save(app.getSharedPreferences("shopping_list_prefs",0))}
  waitFor{ui.onAllNodesWithContentDescription("Избранный товар",substring=false).fetchSemanticsNodes().size==1};assertEquals(2,runBlocking{db.productDao().getAllSync().size});shot("08-independent-stars")
 }
}
