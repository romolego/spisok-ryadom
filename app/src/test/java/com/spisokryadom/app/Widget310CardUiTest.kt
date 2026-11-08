package com.spisokryadom.app

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.ProductEntity
import com.spisokryadom.app.data.repository.ProductEditorTransactions
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=SpisokRyadomApp::class,qualifiers="w360dp-h800dp-xhdpi")
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class Widget310CardUiTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val app get()=ApplicationProvider.getApplicationContext<SpisokRyadomApp>()
    private val db get()=app.container.database
    private var product=0L
    private fun text(s:String){ui.waitUntil(25_000){ui.onAllNodesWithText(s).fetchSemanticsNodes().isNotEmpty()}}
    @Before fun seed(){runBlocking{
        app.getSharedPreferences("shopping_list_prefs",0).edit().clear().putBoolean("autosave_product_cards",false).commit()
        app.getSharedPreferences("product_card_drafts",0).edit().clear().commit()
        db.shoppingListEntryDao().deleteAll();db.goalListDao().deleteAll();db.productDao().deleteAll()
        product=app.container.productRepository.saveWithLinks(ProductEntity(name="Личный товар"),emptyList())
    };text("Список пуст")}
    @After fun close(){ui.activityRule.scenario.close();val end=System.nanoTime()+20_000_000_000;while(ProductEditorTransactions.mutex.isLocked&&System.nanoTime()<end){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();Thread.sleep(5)};assertFalse(ProductEditorTransactions.mutex.isLocked);app.container.locations.closeForTest();db.close();org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null)}
    @Test fun privacyUsesCommonToolbarAndManualSaveAcrossActivityRecreation(){
        ui.onNode(hasText("Товары") and SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.Tab)).performClick()
        text("База товаров");text("Личный товар");ui.onAllNodesWithText("Личный товар").onLast().performClick();text("Название товара")
        for(label in listOf("Цена, ₽","Дата цены")){
            ui.onNodeWithText(label).performScrollTo()
            ui.onAllNodes(hasContentDescription("Голосовой ввод") and hasAnyAncestor(hasText(label)),useUnmergedTree=true).assertCountEquals(0)
        }
        ui.onNodeWithTag("product-price-date").performTextReplacement("5 ноября")
        val priceBounds=ui.onNodeWithTag("product-price").fetchSemanticsNode().boundsInRoot
        val dateBounds=ui.onNodeWithTag("product-price-date").fetchSemanticsNode().boundsInRoot
        assertEquals(priceBounds.height,dateBounds.height,1f)
        ui.onNodeWithContentDescription("Подсказка: Дата цены").assertDoesNotExist()
        ui.onNodeWithText("В виджете нажмите на скрытое название, чтобы показать его на 5 секунд.").assertDoesNotExist()
        ui.onNodeWithTag("widget_privacy").performScrollTo().assertIsOff().performClick().assertIsOn()
        ui.runOnIdle{
            val view=ui.activity.window.decorView
            val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888);view.draw(android.graphics.Canvas(bitmap))
            val file=java.io.File("build/reports/widget310/screens/card-api${android.os.Build.VERSION.SDK_INT}.png");file.parentFile!!.mkdirs();file.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }
        assertFalse(runBlocking{db.productDao().getById(product)!!.isWidgetPrivate})
        ui.activityRule.scenario.recreate();text("Название товара")
        ui.onNodeWithTag("widget_privacy").performScrollTo().assertIsOn()
        ui.onNodeWithText("Сохранить",substring=false).performClick();text("База товаров")
        assertTrue(runBlocking{db.productDao().getById(product)!!.isWidgetPrivate})
        assertEquals("5 ноября",runBlocking{db.productDao().getById(product)!!.priceDate})
        // Catalogue content is still readable; privacy changes only the widget projection.
        text("Личный товар")
    }
}
