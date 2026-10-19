package com.spisokryadom.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.ShopBranchEntity
import com.spisokryadom.app.data.entity.ShopEntity
import com.spisokryadom.app.ui.components.*
import com.spisokryadom.app.ui.addtolist.ShopChoiceField
import com.spisokryadom.app.ui.addtolist.AddToListViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import com.spisokryadom.app.ui.theme.SpisokRyadomTheme
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
class Refine080UiTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 private val app get()=ApplicationProvider.getApplicationContext<SpisokRyadomApp>()
 private fun content(block:@Composable ()->Unit){ui.runOnUiThread{ui.activity.setContent{SpisokRyadomTheme{block()}}};ui.waitForIdle()}
 private fun paletteScroll(tag:String){ui.onNodeWithTag("appearance-options").performScrollToNode(hasTestTag(tag))}
 private fun popup(text:String)=ui.onNode(hasText(text)and hasAnyAncestor(isPopup()))
 private fun waitModel(condition:()->Boolean){ui.waitUntil(20_000){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(20));condition()}}
 private fun shot(name:String){ui.waitForIdle();ui.runOnIdle{
  val v=org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView
  val bitmap=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(bitmap));val f=File("../.local/refinement-0.8.0/$name-api${android.os.Build.VERSION.SDK_INT}.png");f.parentFile!!.mkdirs();f.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
 }}
 @After fun close(){ui.activityRule.scenario.close();app.container.locations.closeForTest();app.container.database.close();org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null)}
 @Test fun applicationLabelIsRealRussianResource(){assertEquals("Список рядом",app.packageManager.getApplicationLabel(app.applicationInfo).toString())}
 @Test fun realDropdownValuesPrecedeNoneAndCreateWithoutChangingTheirMeaning(){
  var selected:String?="Рома";var create=false
  content{Column(Modifier.padding(16.dp)){DropdownSelector("Получатель",listOf("Рома","Злата"),selected,{it},{selected=it},allowCreate=true,onCreateNew={create=true},createLabel="Новый получатель…")}}
  ui.onNodeWithText("Получатель").performClick()
  val positions=listOf("Рома","Злата","Не задано","Новый получатель…").map{popup(it).fetchSemanticsNode().boundsInRoot.top}
  assertEquals(positions.sorted(),positions);popup("Новый получатель…").performClick();assertTrue(create);assertEquals("Рома",selected)
  ui.onNodeWithText("Получатель").performClick();popup("Не задано").performClick();assertNull(selected)
 }
 @Test fun storeChoiceAlsoPutsExistingStoresBeforeNoneAndCreate(){
  var selected:Long?=1
  content{Column(Modifier.padding(16.dp)){ShopChoiceField(listOf(ShopEntity(id=1,name="Батон"),ShopEntity(id=2,name="Аптека.ру")),selected,{selected=it},onCreate={_,_->})}}
  ui.onNodeWithText("Магазин").performClick()
  val positions=listOf("Батон","Аптека.ру","Подобрать автоматически","Без магазина","Новый магазин…").map{popup(it).fetchSemanticsNode().boundsInRoot.top}
  assertEquals(positions.sorted(),positions);popup("Без магазина").performClick();assertNull(selected)
  content{Column(Modifier.padding(16.dp)){BranchChoiceField(listOf(ShopBranchEntity(id=11,shopId=1,address="Весны, 7"),ShopBranchEntity(id=12,shopId=1,address="Молокова, 19")),selected,{selected=it},onCreate={_,_->})}}
  ui.onNodeWithText("Точка / адрес").performClick()
  val branchPositions=listOf("Весны, 7","Молокова, 19","По приоритету для каждого товара","Без конкретного адреса","Новый адрес…").map{popup(it).fetchSemanticsNode().boundsInRoot.top}
  assertEquals(branchPositions.sorted(),branchPositions);popup("Без конкретного адреса").performClick();assertEquals(0L,selected)
  ui.onNodeWithText("Точка / адрес").performClick();popup("Молокова, 19").performClick();assertEquals(12L,selected)
 }
 @Test fun customHexAndCannedIconCanBeSelectedTogether(){
  var saved:Pair<String,String>?=null
  content{AppearanceSheet("Оформление группы","green",onSave={a,i->saved=a to i},onDismiss={})}
  ui.onNodeWithTag("appearance-custom-toggle").performClick();paletteScroll("appearance-hex");ui.onNodeWithTag("appearance-hex").performTextReplacement("#12ABEF")
  ui.onNodeWithText("Применить оформление").assertIsEnabled().assertIsDisplayed();shot("01-custom-color")
  paletteScroll("appearance-icon-search");ui.onNodeWithTag("appearance-icon-search").performTextInput("Консервы")
  paletteScroll("appearance-icon-canned");ui.onNodeWithTag("appearance-icon-canned").performClick();shot("02-canned-icon")
  ui.onNodeWithText("Применить оформление").performClick();assertEquals("#12ABEF" to "canned",saved)
 }
 @Test fun creatingStoreBlocksPurchaseUntilTheStoreIsSelected(){
  val c=app.container
  lateinit var vm:AddToListViewModel
  var created=0L
  ui.runOnUiThread{vm=AddToListViewModel(c.productRepository,c.shopRepository,c.shoppingListRepository,app.getSharedPreferences("creation-test",0));vm.onSearchQueryChanged("Покупка после создания")}
  try{
   waitModel{vm.uiState.value.isNewProduct}
   ui.runOnUiThread{vm.createShop("Созданный магазин"){created=it};assertTrue(vm.uiState.value.isLoading);vm.add()}
   waitModel{created>0&&!vm.uiState.value.isLoading&&vm.uiState.value.shops.any{it.id==created}}
   assertEquals(created,vm.uiState.value.selectedShopId)
   runBlocking{assertTrue(c.database.shoppingListEntryDao().getAllSync().isEmpty())}
   ui.runOnUiThread{vm.add()}
   waitModel{vm.uiState.value.addedMessage!=null}
   runBlocking{assertEquals(created,c.database.shoppingListEntryDao().getAllSync().single().assignedShopId)}
  }finally{ui.runOnUiThread{vm.viewModelScope.cancel()}}
 }
 @Test fun invalidHexCannotSaveAndNoColorClearsTheCustomChoice(){
  var saved:Pair<String,String>?=null
  content{AppearanceSheet("Цвет товара","#12ABEF",withIcons=false,onSave={a,i->saved=a to i},onDismiss={})}
  ui.onNodeWithTag("appearance-custom-toggle").performClick();paletteScroll("appearance-hex");ui.onNodeWithTag("appearance-hex").performTextReplacement("#ZZ1122")
  ui.onNodeWithText("Применить оформление").assertIsNotEnabled()
  ui.onNodeWithTag("appearance-options").performScrollToIndex(0);ui.onNodeWithContentDescription("Без цвета").performClick();ui.onNodeWithText("Применить оформление").assertIsEnabled().performClick();assertEquals("" to "",saved)
 }
 @Test @Config(sdk=[35],qualifiers="w640dp-h360dp-xhdpi") fun customColorControlsAndConfirmationWorkInLandscape(){
  var saved:Pair<String,String>?=null
  content{AppearanceSheet("Оформление группы","",onSave={a,i->saved=a to i},onDismiss={})}
  ui.onNodeWithTag("appearance-custom-toggle").performClick();paletteScroll("appearance-hex");ui.onNodeWithTag("appearance-hex").performTextReplacement("#FF0000")
  paletteScroll("appearance-hue");ui.onNodeWithTag("appearance-hue").performSemanticsAction(SemanticsActions.SetProgress){it(120f)}
  ui.onNodeWithText("Применить оформление").assertIsDisplayed().assertIsEnabled();shot("03-landscape-color");ui.onNodeWithText("Применить оформление").performClick();assertEquals("#00FF00" to "",saved)
 }
}
