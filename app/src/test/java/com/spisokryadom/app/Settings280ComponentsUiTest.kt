package com.spisokryadom.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.spisokryadom.app.data.entity.ShopBranchEntity
import com.spisokryadom.app.ui.productcard.ShopProductSettingsDialog
import com.spisokryadom.app.ui.settings.WidgetSettingsCard
import com.spisokryadom.app.widget.WidgetSettings
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
class Settings280ComponentsUiTest {
 @get:Rule val ui=createComposeRule()
 @Test fun oneOutletHasNoAvailabilityControlAndApplyClearsHistoricalRestrictions(){
  var confirmed=false;var points:Set<Long>?=setOf(8)
  ui.setContent{MaterialTheme{ShopProductSettingsDialog("Один магазин",emptyList(),null,listOf(ShopBranchEntity(8,4,"Адрес")),emptySet(),listOf(8),
   {_,p,priority->confirmed=true;points=p;assertTrue(priority.isEmpty())},{},{_,_->},false,emptyList())}}
  ui.onNodeWithTag("product-outlet-mode").assertDoesNotExist();ui.onNodeWithText("Выберите хотя бы одну точку").assertDoesNotExist()
  ui.onNodeWithText("Применить").assertIsEnabled().performClick();assertTrue(confirmed);assertNull(points)
 }
 @Test fun severalOutletsKeepAllSelectedAndPrioritizedModes(){
  var result:Set<Long>?=null
  ui.setContent{MaterialTheme{ShopProductSettingsDialog("Сеть",emptyList(),null,listOf(ShopBranchEntity(8,4,"Первый"),ShopBranchEntity(9,4,"Второй")),null,emptyList(),{_,p,_->result=p},{},{_,_->},false,emptyList())}}
  ui.onNodeWithTag("product-outlet-mode").performClick();ui.onNodeWithText("Только выбранные точки").performClick()
  ui.onNode(isToggleable() and hasText("Первый")).performClick();ui.onNodeWithText("Применить").performClick();assertEquals(setOf(9L),result)
 }
 @Test @Config(qualifiers="w720dp-h360dp-land-xhdpi") fun widgetSheetKeepsActionsVisibleInLowLandscapeAndSupportsFontDropdown(){
  var value by mutableStateOf(WidgetSettings())
  ui.setContent{MaterialTheme{WidgetSettingsCard(value){value=it}}}
  ui.onNodeWithTag("widget-settings").performClick();ui.onNodeWithTag("widget-font-size").performScrollTo().performClick();ui.onNodeWithText("22").performScrollTo().performClick();assertEquals(22,value.textSize)
  ui.onNodeWithTag("widget-product-background").performScrollTo().performClick();ui.onNodeWithText("Готово").assertIsDisplayed()
  val actions=ui.onNodeWithTag("editor-sheet-actions").getUnclippedBoundsInRoot();assertTrue(actions.bottom.value-actions.top.value>=40)
  ui.runOnIdle{val v=org.robolectric.shadows.ShadowDialog.getLatestDialog()!!.window!!.decorView;val bitmap=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(bitmap));val file=File("build/reports/refine280/screens/widget-low-landscape-api"+android.os.Build.VERSION.SDK_INT+".png");file.parentFile!!.mkdirs();file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
  assertTrue(value.showProductBackground)
 }
}
