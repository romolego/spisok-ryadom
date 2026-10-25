package com.spisokryadom.app

import android.app.Application
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.speech.RecognizerIntent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.spisokryadom.app.data.entity.ProductPhotoEntity
import com.spisokryadom.app.ui.components.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=Application::class,qualifiers="w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ProductVariants290UiTest{
 @get:Rule val ui=createComposeRule()
 private fun shot(name:String){ui.waitForIdle();ui.runOnIdle{val v=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf{it.isShowing}?.window?.decorView?:return@runOnIdle;val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b));val f=File("build/reports/refine290/screens/$name-api${android.os.Build.VERSION.SDK_INT}.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 @Test fun inputMicBecomesClearAndClearReturnsToEmptyDraft(){
  var value by mutableStateOf("")
  ui.setContent{MaterialTheme{AppTextField(value,{value=it},label={Text("Название")})}}
  ui.onNodeWithContentDescription("Голосовой ввод").assertIsDisplayed();ui.onNodeWithText("Название").performTextInput("Салфетки")
  ui.onNodeWithContentDescription("Голосовой ввод").assertDoesNotExist();ui.onNodeWithContentDescription("Очистить").performClick();assertEquals("",value)
  ui.onNodeWithContentDescription("Голосовой ввод").assertIsDisplayed();ui.onNodeWithText("Название").performTextInput("Бананы");assertEquals("Бананы",value)
 }
 @Test fun voiceResultOnlyFillsThisFieldAndCancellationPreservesDraft(){
  var value by mutableStateOf("")
  lateinit var activity:Activity
  ui.setContent{var context=androidx.compose.ui.platform.LocalContext.current;while(context is android.content.ContextWrapper&&context !is Activity)context=context.baseContext;activity=context as Activity;MaterialTheme{AppTextField(value,{value=it},label={Text("Заметка")})}}
  ui.onNodeWithContentDescription("Голосовой ввод").performClick()
  val shadow=Shadows.shadowOf(activity);val first=shadow.nextStartedActivityForResult
  assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH,first.intent.action)
  ui.runOnUiThread{shadow.receiveResult(first.intent,Activity.RESULT_CANCELED,Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS,arrayListOf("Не применять")))}
  assertEquals("",value)
  ui.onNodeWithContentDescription("Голосовой ввод").performClick();val second=shadow.nextStartedActivityForResult
  ui.runOnUiThread{shadow.receiveResult(second.intent,Activity.RESULT_OK,Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS,arrayListOf(" Огурцы ")))}
  ui.waitUntil{value=="Огурцы"};ui.onNodeWithContentDescription("Очистить").assertIsDisplayed()
 }
 @Test fun readonlyChoiceHasNoDestructiveInputActions(){
  ui.setContent{MaterialTheme{DropdownSelector("Магазин",listOf("Батон"),"Батон",{it},{})}}
  ui.onNodeWithContentDescription("Очистить").assertDoesNotExist();ui.onNodeWithContentDescription("Голосовой ввод").assertDoesNotExist()
 }
 @Test fun unitClearKeepsDropdownChoicesUsable(){
  var quantity by mutableStateOf("1");var unit by mutableStateOf("шт")
  ui.setContent{MaterialTheme{QuantityUnitInputWithSuggestions(quantity,unit,{quantity=it},{unit=it},listOf("шт","кг","уп"),quantityModifier=Modifier.testTag("pair-quantity"),unitModifier=Modifier.testTag("pair-unit"))}}
  ui.onNodeWithText("Ед. изм.").performClick();ui.onNodeWithText("кг",useUnmergedTree=true).performClick();assertEquals("кг",unit)
  ui.onAllNodesWithContentDescription("Очистить").onLast().performClick();assertEquals("",unit)
  ui.onNodeWithTag("pair-quantity").performTextClearance()
  assertTrue(ui.onNodeWithTag("pair-quantity").getUnclippedBoundsInRoot().let{it.bottom-it.top}<=64.dp);assertTrue(ui.onNodeWithTag("pair-unit").getUnclippedBoundsInRoot().let{it.bottom-it.top}<=64.dp)
 }
 @Test fun photoStripKeepsCaptionAndFirstPositionWhenEditing(){
  val context=androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>();val paths=(0..1).map{index->File(context.filesDir,"290-ui-$index.png").also{f->val b=Bitmap.createBitmap(80+index*40,100,Bitmap.Config.ARGB_8888);b.eraseColor(android.graphics.Color.rgb(60,140,130));f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}.absolutePath}
  var photos by mutableStateOf(paths.mapIndexed{i,path->ProductPhotoEntity(1,i,path,"Вариант фото $i")})
  ui.setContent{MaterialTheme{Column(Modifier.fillMaxWidth()){ProductPhotos(photos,true,{photos=it},{},toolbar={_,_->})}}}
  ui.waitUntil(20_000){ui.onAllNodesWithContentDescription("Вариант фото 0").fetchSemanticsNodes().isNotEmpty()&&ui.onAllNodesWithContentDescription("Вариант фото 1").fetchSemanticsNodes().isNotEmpty()}
  assertTrue(ui.onNodeWithTag("product-photo-0").getUnclippedBoundsInRoot().let{it.right-it.left}<ui.onNodeWithTag("product-photo-1").getUnclippedBoundsInRoot().let{it.right-it.left})
  ui.onNodeWithTag("product-photo-0").performClick();ui.onNodeWithText("Примечание к фото").performTextReplacement("Первая булка")
  ui.onNodeWithContentDescription("Фото позже").performClick();shot("photo-caption-order")
  ui.onNodeWithText("Применить",substring=false).performClick();assertEquals(paths[1],photos.first().uri);assertEquals("Первая булка",photos.last().caption)
 }
 @Test fun shopColorControlsUseSameSwatchesAndAutomaticColorIsLocalUntilApply(){
  var background="rose";var items="auto"
  ui.setContent{MaterialTheme{ShopAppearance(background,"Красный Яр","",null,{background=it},{},{},{},purchaseBackground=items,onPurchaseBackground={items=it})}}
  ui.onNodeWithTag("shop-appearance-open").performClick();ui.onNodeWithTag("shop-items-color").performClick()
  ui.onNodeWithTag("appearance-match-background").assertIsOn();ui.onNodeWithTag("appearance-custom-toggle").assertDoesNotExist()
  ui.onNodeWithTag("appearance-match-background").performClick();ui.onNodeWithContentDescription("Синий").performClick();shot("matching-color-picker")
  ui.onNodeWithText("Применить оформление").performClick();assertEquals("auto",items)
  ui.onNodeWithTag("shop-appearance-apply").performClick();assertEquals("blue",items);assertEquals("rose",background)
 }
}
