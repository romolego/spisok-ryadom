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
class Venues230UiTest {
 @get:Rule val ui=createComposeRule()
 private var root:android.view.View?=null
 private val venue=VenueEntity(5,1,"ТЦ Аврора","Парковая, 10")
 private fun show(content:@Composable ()->Unit){ui.setContent{val view=androidx.compose.ui.platform.LocalView.current;SideEffect{root=view.rootView};MaterialTheme{content()}}}
 private fun shot(name:String){ui.runOnIdle{val v=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf{it.isShowing}?.window?.decorView?:root;requireNotNull(v);val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b));val file=File("build/reports/refine230/screens/$name-api${android.os.Build.VERSION.SDK_INT}.png");file.parentFile!!.mkdirs();file.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 @Test fun priorityControlsMoveMallBeforeStreetAndCanMoveItBack(){
  var points by mutableStateOf(listOf(ShopBranchEntity(1,1,"Мира, 1"),ShopBranchEntity(2,1,"",venueId=5)))
  show{BranchesEditor(points,{points=it},venues=listOf(venue))}
  ui.onNodeWithContentDescription("Точка выше: ТЦ Аврора — Парковая, 10").assertIsDisplayed().assertIsEnabled().performClick()
  assertEquals(listOf(2L,1L),points.map{it.id});ui.onNodeWithContentDescription("Точка выше: ТЦ Аврора — Парковая, 10").assertIsNotEnabled();shot("point-priority")
  ui.onNodeWithContentDescription("Точка ниже: ТЦ Аврора — Парковая, 10").performClick();assertEquals(listOf(1L,2L),points.map{it.id})
 }
 @Test fun selectingMallUsesItsAddressAndDoesNotRequireAnotherAddress(){
  var points by mutableStateOf(listOf(ShopBranchEntity(1,1,"Мира, 1")))
  show{BranchesEditor(points,{points=it},venues=listOf(venue))}
  ui.onNodeWithText("Добавить точку").performClick();ui.onNodeWithText("Рынок или ТЦ").performClick();ui.onNodeWithText("ТЦ Аврора").performClick()
  ui.onNodeWithText("Парковая, 10").assertIsDisplayed();ui.onNodeWithTag("branch-address--1").assertDoesNotExist()
  ui.onNodeWithTag("branch-label--1").performTextInput("2 этаж")
  ui.onNodeWithText("Выбрать фото").assertDoesNotExist();ui.onNodeWithContentDescription("Выбрать фото").assertExists();ui.onAllNodesWithText("Сохранить").onLast().assertIsDisplayed().assertIsEnabled();shot("point-in-mall")
  ui.onAllNodesWithText("Сохранить").onLast().performClick();assertEquals(2,points.size);assertEquals(5L,points.last().venueId);assertEquals("",points.last().address);assertEquals("2 этаж",points.last().label)
 }
 @Test fun newVenueHasOwnEditorAndIsReusableForTheNextPoint(){
  var points by mutableStateOf(emptyList<ShopBranchEntity>());var venues by mutableStateOf(emptyList<VenueEntity>())
  show{BranchesEditor(points,{points=it},venues=venues,onVenueChange={venues=venues.filter{v->v.id!=it.id}+it},cityId=1)}
  ui.onNodeWithText("Добавить точку").performClick();ui.onNodeWithText("Рынок или ТЦ").performClick();ui.onNodeWithText("Новый рынок или ТЦ…").performClick()
  ui.onNodeWithTag("venue-name").performTextInput("Рынок");ui.onNodeWithTag("venue-address").performTextInput("Общий адрес, 7");ui.onAllNodesWithText("Сохранить").onLast().performClick()
  ui.onNodeWithText("Общий адрес, 7").assertIsDisplayed();ui.onAllNodesWithText("Сохранить").onLast().performClick();assertEquals(1,venues.size);assertEquals(1,points.size);assertEquals(venues.single().id,points.single().venueId)
 }
 @Test fun venueSaveAndBackHaveConsistentTopActionsAndBackDoesNotCommit(){
  var open by mutableStateOf(true);var saved:VenueEntity?=null
  show{if(open)VenueEditorDialog(venue,{open=false},{saved=it})}
  ui.onNodeWithTag("venue-address").performTextReplacement("Другой адрес, 2");ui.onAllNodesWithText("Сохранить").onLast().assertIsDisplayed();shot("venue-editor")
  ui.onNodeWithContentDescription("Назад").performClick();assertFalse(open);assertNull(saved)
 }
 @Test fun venueEditorCommitsCommonAddressOnlyOnSave(){
  var open by mutableStateOf(true);var saved:VenueEntity?=null
  show{if(open)VenueEditorDialog(venue,{open=false},{saved=it})}
  ui.onNodeWithTag("venue-address").performTextReplacement("Новый адрес, 9");assertNull(saved);ui.onAllNodesWithText("Сохранить").onLast().performClick();ui.waitForIdle();assertEquals("Новый адрес, 9",saved!!.address);assertFalse(open)
 }
 @Test fun compactOutletPhotoDeletionRequiresConfirmation(){
  val file=File.createTempFile("synthetic-230-",".png");val bitmap=Bitmap.createBitmap(40,40,Bitmap.Config.ARGB_8888);bitmap.eraseColor(0xffaaccdd.toInt());file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
  var path:String? by mutableStateOf(file.path)
  show{BranchPhotoEditor(path.orEmpty(),{path=it},compact=true,showTitle=false)}
  ui.onNodeWithContentDescription("Изменить фото").performClick();ui.onNodeWithText("Удалить").performClick();ui.onNodeWithText("Удалить фото?").assertIsDisplayed();assertNotNull(path);shot("photo-delete-confirmation")
  ui.onNodeWithText("Отмена").performClick();assertNotNull(path)
  ui.onNodeWithContentDescription("Изменить фото").performClick();ui.onNodeWithText("Удалить").performClick();ui.onNodeWithText("Удалить").performClick();assertNull(path);file.delete()
 }
 @Test @Config(sdk=[35],qualifiers="w640dp-h360dp-xhdpi") fun shortScreenKeepsVenueSaveVisibleWhileBodyScrolls(){
  show{VenueEditorDialog(venue,{},{})}
  ui.onNodeWithTag("venue-address").performScrollTo().performTextInput(" новый")
  ui.onAllNodesWithText("Сохранить").onLast().assertIsDisplayed().assertIsEnabled();shot("venue-editor-landscape")
 }
 @Test @Config(sdk=[35]) fun outletDraftSurvivesStateRestorationBeforeSave(){
  val tester=androidx.compose.ui.test.junit4.StateRestorationTester(ui)
  val points=listOf(ShopBranchEntity(1,1,"Мира, 1"))
  tester.setContent{MaterialTheme{BranchesEditor(points,{},venues=listOf(venue))}}
  ui.onNodeWithContentDescription("Изменить точку: Мира, 1").performClick()
  ui.onNodeWithTag("branch-address-1").performTextReplacement("Другой адрес, 5")
  tester.emulateSavedInstanceStateRestore()
  ui.onNodeWithTag("branch-address-1").assertTextContains("Другой адрес, 5")
  ui.onAllNodesWithText("Сохранить").onLast().assertIsDisplayed()
 }

}
