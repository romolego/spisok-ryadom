package com.spisokryadom.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.ui.components.*
import com.spisokryadom.app.ui.shopcard.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=Application::class,qualifiers="w360dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class Stores220UiTest {
 @get:Rule val ui=createComposeRule()
 private var root:android.view.View?=null
 private fun show(content:@Composable ()->Unit){ui.setContent{val view=androidx.compose.ui.platform.LocalView.current;SideEffect{root=view.rootView};MaterialTheme{content()}}}
 private fun shot(name:String){ui.runOnIdle{val v=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf{it.isShowing}?.window?.decorView?:root;requireNotNull(v);val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b));val file=File("build/reports/refine220/screens/$name-api${android.os.Build.VERSION.SDK_INT}.png");file.parentFile!!.mkdirs();file.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 @Test fun typedUnitRanksMatchesButAllowsChoosingAnUnrelatedUnit(){
  var unit by mutableStateOf("шт")
  show{QuantityUnitInputWithSuggestions("1",unit,{}, {unit=it},listOf("кг","л","шт.","уп."))}
  ui.onNodeWithText("Ед. изм.").performClick();ui.onNodeWithText("кг").assertIsDisplayed();ui.onNodeWithText("уп.").assertExists()
  val top=ui.onNodeWithText("шт.").fetchSemanticsNode().boundsInRoot.top;assertTrue(top<ui.onNodeWithText("кг").fetchSemanticsNode().boundsInRoot.top)
  ui.onNodeWithText("кг").performClick();assertEquals("кг",unit)
 }
 @Test fun longDepartmentPickerKeepsBothActionsVisibleAndAppliesSelection(){
  var chosen=emptyList<String>();var dismissed=false
  show{MultiSelectDialog("Добавить группы магазина",(1..40).map{"Группа $it"},emptySet(),onConfirm={chosen=it},onDismiss={dismissed=true})}
  ui.onNodeWithText("Отмена").assertIsDisplayed();ui.onNodeWithText("Добавить").assertIsDisplayed().assertIsNotEnabled()
  ui.onNodeWithText("Группа 1").performClick();ui.onNodeWithText("Добавить").assertIsEnabled();shot("department-picker-portrait")
  ui.onNodeWithText("Добавить").performClick();assertEquals(listOf("Группа 1"),chosen);assertFalse(dismissed)
 }
 @Test @Config(sdk=[35],qualifiers="w640dp-h360dp-xhdpi") fun shortScreenStillKeepsPickerActionsVisible(){
  var dismissed=false
  show{MultiSelectDialog("Добавить группы магазина",(1..60).map{"Отдел $it"},emptySet(),onConfirm={},onDismiss={dismissed=true})}
  ui.onNodeWithText("Отмена").assertIsDisplayed();ui.onNodeWithText("Добавить").assertIsDisplayed();shot("department-picker-landscape");ui.onNodeWithText("Отмена").performClick();assertTrue(dismissed)
 }
 @Test fun defaultStoreIconIsCalledDefaultAndPurchaseBackgroundCanFollowTheShop(){
  var background by mutableStateOf("")
  show{Column(Modifier.verticalScroll(rememberScrollState())){ShopAppearance("rose","Лента","",null,{},{},{},{},purchaseBackground=background,onPurchaseBackground={background=it})}}
  ui.onNodeWithText("Значок").performClick();ui.onNodeWithText("Обычный значок магазина").assertExists();ui.onNodeWithText("Без значка").assertDoesNotExist();ui.onNodeWithText("Применить оформление").performClick()
  ui.onNodeWithText("Фон товаров").performScrollTo().performClick();ui.onNodeWithText("В тон подложки").performClick();assertEquals("auto",background);ui.onNodeWithTag("shop-appearance-preview").performScrollTo();shot("purchase-background-auto")
  ui.onNodeWithText("Фон товаров").performScrollTo().performClick();ui.onNodeWithText("Обычный фон").performClick();assertEquals("",background)
 }
 @Test fun shopSummaryKeepsNetworkAndSeparateMallMembershipForEachPoint(){
  val state=ShopCardUiState(name="Лента",isLoading=false,networkId=8,networks=listOf(RetailNetworkEntity(8,"Лента")),cities=listOf(CityEntity(1,"Город")),venues=listOf(VenueEntity(1,1,"ТЦ Зелёный"),VenueEntity(2,1,"ТЦ Северный")),branches=listOf(ShopBranchEntity(1,9,"Парковая, 1",venueId=1),ShopBranchEntity(2,9,"Северная, 2",venueId=2),ShopBranchEntity(3,9,"Мира, 3")))
  show{ShopPlaceSummary(state)}
  listOf("Сеть: Лента","ТЦ Зелёный","ТЦ Северный","Отдельный адрес","Мира, 3").forEach{ui.onNodeWithText(it).assertExists()};shot("mixed-network-summary")
 }
}
