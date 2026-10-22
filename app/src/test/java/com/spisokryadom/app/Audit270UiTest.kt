package com.spisokryadom.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.spisokryadom.app.data.entity.ProductEntity
import com.spisokryadom.app.data.entity.ShopEntity
import com.spisokryadom.app.ui.components.*
import com.spisokryadom.app.ui.productcard.ShopProductSettingsDialog
import com.spisokryadom.app.ui.settings.ProductUsageDialog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=Application::class,qualifiers="w360dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class Audit270UiTest {
    @get:Rule val ui=createComposeRule()
    private fun show(content:@Composable ()->Unit)=ui.setContent { MaterialTheme { content() } }
    private fun shot(name:String) { ui.runOnIdle {
        val view=org.robolectric.shadows.ShadowDialog.getLatestDialog()!!.window!!.decorView
        val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        val file=File("build/reports/audit270/screens/$name-api${android.os.Build.VERSION.SDK_INT}.png")
        file.parentFile!!.mkdirs();file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) };image.recycle()
    } }

    @Test fun rawQuantitySurvivesInvalidInputUnitSelectionAndUnrelatedRerender() {
        var quantity by mutableStateOf("2");var unit by mutableStateOf("шт");var revision by mutableStateOf(0)
        show { Column {
            Text("Обновление $revision")
            QuantityUnitInputWithSuggestions(quantity,unit,{quantity=it},{unit=it},listOf("шт","кг"),
                quantityModifier=Modifier.testTag("raw-quantity"),unitModifier=Modifier.testTag("raw-unit"))
        } }
        ui.onNodeWithTag("raw-quantity").performTextReplacement("1,,")
        ui.onNodeWithTag("raw-quantity").assertTextContains("1,,")
        assertEquals("1,,",quantity);assertNull(com.spisokryadom.app.util.Quantities.parse(quantity))
        ui.runOnIdle{revision++}
        ui.onNodeWithText("Обновление 1").assertExists();ui.onNodeWithTag("raw-quantity").assertTextContains("1,,")
        ui.onNodeWithTag("raw-unit").performClick();ui.onNodeWithText("кг").performClick()
        ui.onNodeWithTag("raw-unit").assertTextContains("кг");ui.onNodeWithTag("raw-quantity").assertTextContains("1,,")
        ui.onNodeWithTag("raw-quantity").performTextReplacement("ошибка")
        ui.onNodeWithTag("raw-quantity").assertTextContains("ошибка")
        assertNull(com.spisokryadom.app.util.Quantities.parse(quantity))
        ui.onNodeWithTag("raw-quantity").performTextReplacement("3,5")
        assertEquals(3.5,com.spisokryadom.app.util.Quantities.parse(quantity)!!,0.0)
    }

    @Test fun selectionBodyScrollsWithoutLosingActions() {
        var selected=false;var applied=false
        show { SelectionDialog("Выберите группы",{},confirm={TextButton(onClick={applied=selected}){Text("Применить")}}) {
            repeat(35) { index -> SelectionRow("Группа $index",index==34&&selected,{if(index==34)selected=it}) }
        } }
        ui.onNodeWithTag("editor-sheet").assertExists()
        ui.onNode(isToggleable() and hasText("Группа 34")).performScrollTo().performClick()
        ui.onNodeWithText("Применить").assertIsDisplayed().performClick()
        assertTrue(applied);shot("selection-fixed-actions")
    }

    @Test fun nameEditorCancellationDoesNotCommitDraft() {
        var open by mutableStateOf(true);var saved:String?=null
        show { if(open)InputDialog("Переименовать","Молочка","Название",{saved=it;open=false},{open=false}) }
        ui.onNodeWithText("Название").performTextReplacement("Отменённое название")
        ui.onNodeWithText("Отмена").performClick()
        assertNull(saved);ui.onNodeWithTag("editor-sheet").assertDoesNotExist()
    }

    @Test fun purchaseTypeDropdownKeepsUnsupportedChoiceVisibleAndDisabled() {
        var value by mutableStateOf("online")
        show { PurchaseTypeSelector(value,{value=it},allowedTypes=setOf("online")) }
        ui.onNodeWithText("Тип покупки").performClick()
        ui.onNodeWithText("Офлайн").assertIsNotEnabled()
        ui.onAllNodesWithText("Онлайн").onLast().assertIsEnabled().performClick()
        assertEquals("online",value)
    }

    @Test fun usageSheetCanOpenTheLastProductInLongCatalogue() {
        var opened=0L;var dismissed=false
        show { ProductUsageDialog("Товары группы",(1L..50L).map { ProductEntity(id=it,name="Товар %02d".format(it)) },{opened=it},{dismissed=true}) }
        ui.onNodeWithTag("product-usage-list").performScrollToNode(hasText("Товар 50"))
        ui.onNodeWithText("Товар 50").performClick()
        assertEquals(50L,opened);assertTrue(dismissed)
        ui.onNodeWithText("Закрыть").assertIsDisplayed()
    }

    @Test fun busyEditorRejectsBackUntilTheWriteFinishes() {
        var busy by mutableStateOf(true);var open by mutableStateOf(true);var dismissed=0
        show { if(open)EditorSheet("Сохранение",{dismissed++;open=false},busy=busy,actions={
            TextButton(onClick={busy=false}){Text("Завершить запись")}
        }) { Text("Изменение данных") } }
        fun back()=ui.runOnIdle {
            val dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog()!!
            when(dialog) {
                is androidx.activity.ComponentDialog -> dialog.onBackPressedDispatcher.onBackPressed()
                else -> @Suppress("DEPRECATION") dialog.onBackPressed()
            }
        }
        back();ui.waitForIdle();assertEquals(0,dismissed)
        ui.onNodeWithText("Завершить запись").performClick();back();ui.waitForIdle()
        assertEquals(1,dismissed);ui.onNodeWithTag("editor-sheet").assertDoesNotExist()
    }

    @Test fun inlineCreationCannotBeDismissedDuringAnOutstandingWrite() {
        var completed:(()->Unit)?=null;var cancelled=false
        show { InlineCreateValue("Получатель",{_,done->completed=done},{cancelled=true}) }
        ui.onNodeWithText("Получатель").performTextInput("Злата")
        ui.onNodeWithContentDescription("Создать: Получатель").performClick()
        ui.onNodeWithContentDescription("Отмена: Получатель").assertIsNotEnabled()
        ui.onNodeWithContentDescription("Создать: Получатель").assertIsNotEnabled()
        ui.runOnIdle { completed!!.invoke() }
        ui.onNodeWithContentDescription("Отмена: Получатель").assertIsEnabled().performClick()
        assertTrue(cancelled)
    }

    @Test fun shopCopySelectorPinsFavoritesAndKeepsTheirFullListCopies() {
        var copied=0L
        show { CopyProductShopSheet(1,{}, {
            listOf(ShopEntity(id=1,name="Аптека"),ShopEntity(id=2,name="Ярмарка",isFavorite=true))
        },{id,done->copied=id;done(true)}) }
        ui.waitUntil(20_000){ui.onAllNodesWithText("Ярмарка").fetchSemanticsNodes().size==2}
        val favorites=ui.onAllNodesWithText("Ярмарка").fetchSemanticsNodes()
        val other=ui.onNodeWithText("Аптека").fetchSemanticsNode()
        assertTrue(favorites.first().boundsInRoot.top<other.boundsInRoot.top)
        assertTrue(other.boundsInRoot.top<favorites.last().boundsInRoot.top)
        ui.onAllNodesWithText("Ярмарка").onLast().performClick();assertEquals(2L,copied)
    }

    @Test fun shopAppearanceReportsClearedLogoEvenWhenIconCallbackHasNoSideEffects() {
        val draftFile=File(androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>().cacheDir,"synthetic-draft-logo.png")
        val bitmap=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888).apply{eraseColor(android.graphics.Color.GREEN)}
        draftFile.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        val draftPath=draftFile.absolutePath
        var logoCallback=false;var logo:String?=draftPath;var selectedIcon:String?=null
        show { ShopAppearance("","Магазин","",draftPath,{},
            {selectedIcon=it},{logoCallback=true;logo=it},{}) }
        ui.onNodeWithContentDescription("Оформление магазина").performClick()
        ui.onNodeWithTag("shop-symbol-edit").performClick()
        ui.onNodeWithText("Готовый значок").performClick()
        ui.onNodeWithText("Обычный значок магазина").performClick()
        ui.onNodeWithTag("shop-symbol-apply").performClick()
        assertFalse(logoCallback);assertEquals(draftPath,logo)
        ui.onNodeWithTag("shop-appearance-apply").performClick()
        assertTrue(logoCallback);assertNull(logo);assertEquals("",selectedIcon)
    }

    @Test @Config(qualifiers="w640dp-h360dp-xhdpi")
    fun largeFontShopEditorKeepsConfirmationVisibleInLandscape() {
        show {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.3f)) {
                ShopProductSettingsDialog("Сеть с длинным названием",emptyList(),null,
                    (1L..8L).map { com.spisokryadom.app.data.entity.ShopBranchEntity(id=it,shopId=1,address="Длинный адрес точки $it") },
                    null,emptyList(),{_,_,_->},{},{_,_->},false,emptyList())
            }
        }
        ui.onNodeWithText("Доступность товара").performScrollTo().performClick()
        ui.onNodeWithText("Только выбранные точки").performClick()
        ui.onNode(isToggleable() and hasText("Длинный адрес точки 8")).performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Применить").assertIsDisplayed().assertIsEnabled()
        ui.onNodeWithText("Отмена").assertIsDisplayed();shot("landscape-large-font-shop")
    }

    @Test @Config(qualifiers="w640dp-h360dp-xhdpi")
    fun colorPreviewScrollsWithOptionsAndDoesNotPushApplyOffScreen() {
        var saved:String?=null
        show { AppearanceSheet("Оформление товара","blue",withIcons=false,onSave={color,_->saved=color},onDismiss={},preview={_,_->
            Column(Modifier.fillMaxWidth().height(240.dp)){Text("Предпросмотр магазина и товаров")}
        }) }
        ui.onNodeWithText("Применить оформление").assertIsDisplayed()
        ui.onNodeWithTag("appearance-options").performScrollToNode(hasTestTag("appearance-custom-toggle"))
        ui.onNodeWithTag("appearance-custom-toggle").performClick()
        ui.onNodeWithTag("appearance-options").performScrollToNode(hasTestTag("appearance-hex"))
        ui.onNodeWithTag("appearance-hex").performTextReplacement("#CCAA66")
        ui.onNodeWithText("Применить оформление").assertIsDisplayed().performClick()
        assertEquals("#CCAA66",saved);shot("landscape-color-fixed-actions")
    }
}
