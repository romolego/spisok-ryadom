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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.spisokryadom.app.data.entity.ShopBranchEntity
import com.spisokryadom.app.ui.components.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=Application::class,qualifiers="w320dp-h700dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class StorePlaces242LayoutTest {
 @get:Rule val ui=createComposeRule()
 private var root:android.view.View?=null
 private fun show(content:@Composable ()->Unit){ui.setContent{val view=androidx.compose.ui.platform.LocalView.current;SideEffect{root=view.rootView};MaterialTheme{Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)){content()}}}}
 private fun shot(name:String){ui.runOnIdle{val v=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf{it.isShowing}?.window?.decorView?:root;requireNotNull(v);val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b));val f=File("build/reports/refine242/screens/$name-api${android.os.Build.VERSION.SDK_INT}.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 @Test fun longAddressGetsTheWholeCardWidthAndKeepsPriorityControlsWithLargeText(){
  var points by mutableStateOf(listOf(ShopBranchEntity(1,1,"Весны, 1 (Взлётка плаза)"),ShopBranchEntity(-2,1,"Молокова, 54, вход со стороны большой парковки",label="Сибирский городок",displayOrder=1)))
  show{val density=LocalDensity.current;CompositionLocalProvider(LocalDensity provides Density(density.density,1.5f)){BranchesEditor(points,{points=it})}}
  ui.onNodeWithTag("branch-summary-text--2").performScrollTo().assertIsDisplayed()
  val card=ui.onNodeWithTag("branch-summary--2").getUnclippedBoundsInRoot()
  val text=ui.onNodeWithTag("branch-summary-text--2").getUnclippedBoundsInRoot()
  val edit=ui.onNodeWithContentDescription("Изменить точку: Сибирский городок · Молокова, 54, вход со стороны большой парковки").getUnclippedBoundsInRoot()
  assertTrue(text.right-text.left>=card.right-card.left-28.dp);assertTrue(text.bottom-text.top>30.dp)
  assertTrue(edit.bottom<=text.top);assertTrue(card.bottom-text.bottom>=8.dp)
  shot("long-address-large-text")
  ui.onNodeWithContentDescription("Точка выше: Сибирский городок · Молокова, 54, вход со стороны большой парковки").performScrollTo().performClick()
  assertEquals(-2L,points.first().id)
  ui.onNodeWithText("Добавить точку").assertDoesNotExist();ui.onNodeWithContentDescription("Добавить точку").performScrollTo().performClick()
  ui.onNodeWithText("Новая точка").assertIsDisplayed()
 }
 @Test fun networkPencilIsNextToTheDropdownAndCancelKeepsTheDraft(){
  var id:Long? by mutableStateOf(1L);var name by mutableStateOf("")
  show{RetailRelationSelector("Сеть",listOf(1L to "X5"),id,name,{id=it},{name=it})}
  val field=ui.onNodeWithText("Сеть").getUnclippedBoundsInRoot();val pencil=ui.onNodeWithContentDescription("Изменить название сети").getUnclippedBoundsInRoot()
  assertTrue(pencil.left>=field.right);assertTrue(pencil.top<field.bottom)
  ui.onNodeWithText("Изменить название сети").assertDoesNotExist()
  ui.onNodeWithContentDescription("Изменить название сети").performClick();ui.onNodeWithText("Название сети").performTextInput("Другое")
  ui.onNodeWithText("Отмена").performClick();assertEquals("",name);assertEquals(1L,id)
 }
 @Test @Config(sdk=[35],qualifiers="w640dp-h360dp-xhdpi") fun networkNameDialogKeepsBothActionsVisibleOnAShortScreen(){
  var name by mutableStateOf("")
  show{RetailRelationSelector("Сеть",listOf(1L to "X5"),1,name,{},{name=it})}
  ui.onNodeWithContentDescription("Изменить название сети").performClick()
  ui.onNodeWithText("Название сети").performTextReplacement("Новое название сети")
  ui.onNodeWithText("Отмена").assertIsDisplayed();ui.onNodeWithText("Сохранить").assertIsDisplayed().assertIsEnabled();shot("network-name-landscape")
  ui.onNodeWithText("Сохранить").performClick();assertEquals("Новое название сети",name)
 }
 @Test fun appearanceSwitchesHideInactiveControlsAndCancelKeepsBothBackgrounds(){
  var block by mutableStateOf("");var items by mutableStateOf("")
  show{ShopAppearance(block,"Магазин","",null,{block=it},{},{},{},purchaseBackground=items,onPurchaseBackground={items=it})}
  ui.onNodeWithContentDescription("Оформление магазина").performClick()
  ui.onNodeWithTag("shop-background-color").assertDoesNotExist();ui.onNodeWithText("Фон товаров").assertDoesNotExist()
  ui.onNodeWithTag("shop-background-switch").performClick();ui.onNodeWithTag("shop-background-color").assertExists()
  ui.onNodeWithTag("shop-items-switch").performScrollTo().performClick();ui.onNodeWithText("В тон подложки").assertExists()
  ui.onNodeWithText("Отмена").performClick();assertEquals("",block);assertEquals("",items)
  ui.onNodeWithContentDescription("Оформление магазина").performClick();ui.onNodeWithTag("shop-items-switch").performScrollTo().performClick();ui.onNodeWithTag("shop-appearance-apply").performClick();assertEquals("auto",items)
  ui.onNodeWithContentDescription("Оформление магазина").performClick();ui.onNodeWithTag("shop-items-switch").performScrollTo().performClick();ui.onNodeWithText("Фон товаров").assertDoesNotExist();shot("appearance-disabled-controls");ui.onNodeWithTag("shop-appearance-apply").performClick();assertEquals("",items)
 }
 @Test fun symbolItselfOpensOneChooserAndCancelKeepsTheOriginalSymbol(){
  var icon by mutableStateOf("bag")
  show{ShopAppearance("rose","Магазин",icon,null,{},{icon=it},{},{})}
  ui.onNodeWithContentDescription("Оформление магазина").performClick();ui.onNodeWithTag("shop-symbol-edit").performScrollTo().performClick()
  ui.onNodeWithContentDescription("Загрузить логотип магазина").assertExists();ui.onNodeWithContentDescription("Удалить логотип магазина").assertDoesNotExist()
  ui.onNodeWithText("Готовый значок").performClick();ui.onNodeWithText("Обычный значок магазина").performClick()
  ui.onAllNodesWithText("Отмена").onLast().performClick();assertEquals("bag",icon)
  ui.onNodeWithTag("shop-symbol-edit").performScrollTo().performClick();ui.onNodeWithText("Готовый значок").performClick();ui.onNodeWithText("Обычный значок магазина").performClick();ui.onNodeWithTag("shop-symbol-apply").performClick();assertEquals("bag",icon)
  ui.onNodeWithTag("shop-appearance-apply").performClick();assertEquals("",icon)
 }
 @Test @Config(sdk=[35],qualifiers="w640dp-h360dp-xhdpi") fun symbolChooserActionsStayVisibleInLandscape(){
  show{ShopAppearance("rose","Магазин","bag",null,{},{},{},{})}
  ui.onNodeWithContentDescription("Оформление магазина").performClick();ui.onNodeWithTag("shop-symbol-edit").performScrollTo().performClick()
  ui.onNodeWithTag("shop-symbol-apply").assertIsDisplayed();ui.onAllNodesWithText("Отмена").onLast().assertIsDisplayed();shot("symbol-chooser-landscape")
 }

}
