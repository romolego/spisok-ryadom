package com.spisokryadom.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.navigation.AppNavGraph
import com.spisokryadom.app.ui.theme.SpisokRyadomTheme
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
class UxLayoutTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val app get()=ApplicationProvider.getApplicationContext<SpisokRyadomApp>()
    private val db get()=app.container.database
    private fun waitText(text:String) { ui.waitUntil(20_000){ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()} }
    private fun shot(name:String) { ui.runOnIdle {
        val view=when {
            name=="05-city-dialog"->org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView
            name=="03-plus-menu"->runCatching {
                val clazz=Class.forName("android.view.WindowManagerGlobal")
                val global=clazz.getDeclaredMethod("getInstance").invoke(null)
                val field=clazz.getDeclaredField("mViews").apply{isAccessible=true}
                @Suppress("UNCHECKED_CAST") val views=field.get(global) as List<android.view.View>
                views.last{it.javaClass.simpleName.contains("Popup")&&it.width>0}
            }.getOrElse{ui.activity.window.decorView}
            else->ui.activity.window.decorView
        }
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val target=File("../.local/ux-0.2.2/${name}-api${android.os.Build.VERSION.SDK_INT}.png")
        target.parentFile!!.mkdirs()
        target.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    } }
    private fun bulk() {
        ui.onNodeWithContentDescription("Добавить в список").performTouchInput{longClick()}
        ui.onNodeWithText("Текстовый список").performClick()
        waitText("Проверить изменения")
    }
    @Before fun setup() {
        app.getSharedPreferences("drafts",0).edit().clear().commit()
        runBlocking {
            db.shoppingListEntryDao().deleteAll();db.productDao().deleteAll();db.shopDao().deleteAll()
            db.cityDao().update(CityEntity(1,"Петропавловск-Камчатский"));app.container.locations.select(1)
        }
        waitText("Список пуст")
    }
    @After fun close() {
        ui.activityRule.scenario.close();app.container.locations.closeForTest();db.close()
        org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null)
    }

    @Test fun cityIsOnlyInSettingsAndPlusHasTwoDistinctGestures() {
        ui.onNodeWithText("Город:",substring=true).assertDoesNotExist()
        ui.onNodeWithContentDescription("Изменить город").assertDoesNotExist()
        ui.onNodeWithText("Текстовый список").assertDoesNotExist()
        shot("01-empty")
        ui.onNodeWithContentDescription("Добавить в список").performClick()
        waitText("Добавить покупку")
        ui.onNodeWithText("Город:",substring=true).assertDoesNotExist()
        ui.onNodeWithContentDescription("Изменить город").assertDoesNotExist()
        shot("02-single-add")
        ui.onNodeWithContentDescription("Назад").performClick()
        ui.onNodeWithContentDescription("Добавить в список").performTouchInput{longClick()}
        ui.onNodeWithText("Текстовый список").assertIsDisplayed()
        ui.onNodeWithText("Добавить покупку").assertDoesNotExist()
        shot("03-plus-menu")
        ui.onNodeWithText("Текстовый список").performClick()
        ui.onNodeWithTag("bulk-editor").assertIsDisplayed()
        ui.onNodeWithText("Город:",substring=true).assertDoesNotExist()
        ui.onNodeWithContentDescription("Изменить город").assertDoesNotExist()
        ui.onNodeWithContentDescription("Назад").performClick()
        ui.onNodeWithContentDescription("Настройки").performClick()
        waitText("Петропавловск-Камчатский")
        ui.onNodeWithText("Петропавловск-Камчатский").assertIsDisplayed()
        shot("04-settings")
        ui.onNodeWithContentDescription("Изменить город").performClick()
        ui.onNodeWithText("Добавить город").assertIsDisplayed()
        shot("05-city-dialog")
    }

    @Test fun compactKeyboardSizedViewportKeepsMultilineEditorAndActionVisible() {
        // A 300dp keyboard leaves 340dp from this 640dp window. Use the complete
        // navigation tree, not an isolated field. This is a geometry test, not a real IME.
        ui.activity.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.15f)) {
                SpisokRyadomTheme {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().height(340.dp)){AppNavGraph()}
                        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Box(contentAlignment=Alignment.Center){Text("Область клавиатуры · тест геометрии",style=MaterialTheme.typography.bodySmall)}
                        }
                    }
                }
            }
        }
        waitText("Список пуст");bulk()
        val raw="Красный Яр:\nБананы 4\nЯблоки 0,5 кг\nОгурцы 2 кг\nБатон:\nМолоко 2 л"
        ui.onNodeWithTag("bulk-editor").performTextReplacement(raw)
        ui.onNodeWithTag("bulk-editor").assertHeightIsAtLeast(130.dp).assertTextContains(raw)
        ui.onNodeWithText("Проверить изменения").assertIsDisplayed()
        shot("06-compact-editor")
    }

    @Test @Config(sdk=[35]) fun imeInsetsAreConsumedOnceInTheActualNavigationTree() {
        bulk()
        ui.onNodeWithTag("bulk-editor").performTextReplacement("Бананы 4\nЯблоки 0,5 кг\nМолоко 2 л\nОгурцы 2")
        ui.runOnIdle {
            fun findCompose(view:android.view.View):android.view.View? {
                if(view.javaClass.simpleName=="AndroidComposeView")return view
                if(view is android.view.ViewGroup)for(i in 0 until view.childCount) {
                    findCompose(view.getChildAt(i))?.let{return it}
                }
                return null
            }
            val compose=findCompose(ui.activity.window.decorView)!!
            val density=ui.activity.resources.displayMetrics.density
            val inset=androidx.core.view.WindowInsetsCompat.Builder()
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars(),androidx.core.graphics.Insets.of(0,(24*density).toInt(),0,(24*density).toInt()))
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.ime(),androidx.core.graphics.Insets.of(0,0,0,(300*density).toInt()))
                .setVisible(androidx.core.view.WindowInsetsCompat.Type.ime(),true).build()
            androidx.core.view.ViewCompat.dispatchApplyWindowInsets(compose,inset)
        }
        ui.waitForIdle()
        ui.onNodeWithTag("bulk-editor").assertHeightIsAtLeast(130.dp)
        val action=ui.onNodeWithText("Проверить изменения").getUnclippedBoundsInRoot()
        println("IME300 geometry: editor=${ui.onNodeWithTag("bulk-editor").getUnclippedBoundsInRoot()}, action=$action")
        assertTrue("Action must stay above injected 300dp IME; actual=$action",action.bottom<=341.dp)
        shot("06b-ime-insets")
    }

    @Test fun perLineStoreCorrectionIsInteractiveAndHeadersAreNotPurchases() {
        val ids=runBlocking {
            val shop=db.shopDao().insert(ShopEntity(name="Красный Яр",cityId=1))
            val other=db.shopDao().insert(ShopEntity(name="Батон",cityId=1))
            val apples=db.productDao().insert(ProductEntity(name="Яблоки Гала",defaultUnit="кг"))
            val milk=db.productDao().insert(ProductEntity(name="Молоко",defaultUnit="л"))
            listOf(shop,other,apples,milk)
        }
        bulk()
        ui.onNodeWithTag("bulk-editor").performTextReplacement("Красный Йар:\nЯблоки Гала 0,5 кг\nБатон:\nМолоко 2 л")
        ui.onNodeWithText("Проверить изменения").performClick();waitText("Сохранить изменения")
        ui.onNodeWithText("Выберите магазин").performScrollTo().performClick()
        ui.onNodeWithText("Красный Яр").performClick()
        shot("07-review")
        ui.onNodeWithText("Сохранить изменения").performClick();waitText("Изменения сохранены")
        runBlocking {
            val rows=db.shoppingListEntryDao().getAllSync()
            assertEquals(2,rows.size)
            assertEquals(ids[0],rows.single{it.productId==ids[2]}.assignedShopId)
            assertEquals(ids[1],rows.single{it.productId==ids[3]}.assignedShopId)
            assertEquals(2,db.productDao().getAllSync().size)
        }
        ui.onNodeWithContentDescription("Назад").performClick();waitText("Яблоки Гала")
        shot("08-basket")
    }

    @Test fun catalogAndShopCardsRemainReadableWithAddedAndLongNamedProducts() {
        runBlocking {
            val shop=db.shopDao().insert(ShopEntity(name="Красный Яр",address="Проспект Мира, 10",cityId=1))
            val apples=db.productDao().insert(ProductEntity(name="Яблоки Гала",defaultUnit="кг"))
            val other=db.productDao().insert(ProductEntity(name="Йогурт натуральный без сахара с очень длинным названием",defaultUnit="упаковка"))
            db.productShopLinkDao().insert(ProductShopLinkEntity(productId=apples,shopId=shop))
            db.productShopLinkDao().insert(ProductShopLinkEntity(productId=other,shopId=shop))
            app.container.shoppingListRepository.addProduct(apples,.5,"кг")
        }
        ui.onNodeWithText("Товары").performClick();waitText("Товаров: 2")
        ui.onNodeWithText("Город:",substring=true).assertDoesNotExist()
        ui.onNodeWithContentDescription("Изменить город").assertDoesNotExist()
        ui.onNodeWithContentDescription("Увеличить").assertIsDisplayed()
        ui.onNodeWithText("Йогурт натуральный без сахара с очень длинным названием").assertIsDisplayed()
        shot("09-catalog")
        ui.onNode(hasText("Магазины") and SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role,androidx.compose.ui.semantics.Role.Tab)).performClick();waitText("Красный Яр")
        ui.onNodeWithText("Город:",substring=true).assertDoesNotExist()
        ui.onNodeWithContentDescription("Изменить город").assertDoesNotExist()
        shot("10-shops")
        ui.onNodeWithText("Красный Яр").performClick();waitText("Яблоки Гала")
        shot("11-shop-content")
        ui.onNodeWithText("Яблоки Гала").performTouchInput{longClick()}
        ui.waitUntil(20_000){ui.onAllNodes(hasSetTextAction() and hasText("Яблоки Гала")).fetchSemanticsNodes().isNotEmpty()}
        shot("12-product-card")
        ui.onNodeWithContentDescription("Назад").performClick()
        ui.waitUntil(20_000){ui.onAllNodes(hasSetTextAction() and hasText("Яблоки Гала")).fetchSemanticsNodes().isEmpty()}
        ui.onNodeWithContentDescription("Назад").performClick()
        waitText("Красный Яр")
        ui.onNodeWithText("Красный Яр").performTouchInput{longClick()}
        ui.waitUntil(20_000){ui.onAllNodes(hasSetTextAction() and hasText("Красный Яр")).fetchSemanticsNodes().isNotEmpty()}
        shot("13-shop-card")
    }
}
