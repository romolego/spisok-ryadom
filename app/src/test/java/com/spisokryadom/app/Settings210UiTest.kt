package com.spisokryadom.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.ResetMode
import com.spisokryadom.app.ui.components.*
import com.spisokryadom.app.ui.settings.*
import com.spisokryadom.app.util.*
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
class Settings210UiTest {
 @get:Rule val ui=createComposeRule()
 private fun show(content:@Composable ()->Unit){ui.setContent{MaterialTheme{content()}}}
 private fun shot(name:String){ui.runOnIdle{val view=org.robolectric.shadows.ShadowDialog.getLatestDialog()!!.window!!.decorView;val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(image));val file=File("build/reports/refine210/screens/$name-api${android.os.Build.VERSION.SDK_INT}.png");file.parentFile!!.mkdirs();file.outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()}}
 @Test fun favoriteContextsUseIndependentStylesInActualLabelsAndDropdownCopies(){
  val appearance=FavoriteAppearance(block=FavoriteStyle(star=false),row=FavoriteStyle(star=true),purchaseRow=FavoriteStyle(star=false),dropdownBlock=FavoriteStyle(star=true),dropdownRow=FavoriteStyle(star=false))
  show{CompositionLocalProvider(LocalFavoriteAppearance provides appearance,LocalFavoriteSurface provides FavoriteSurface.PURCHASES){
   androidx.compose.foundation.layout.Column{
    FavoriteMarkedLabel("Покупка",favorite=true)
    CompositionLocalProvider(LocalFavoriteSurface provides FavoriteSurface.LISTS){FavoriteMarkedLabel("Каталог",favorite=true)}
    DropdownSelector("Группа",listOf("Продукты"),null,{it},{},itemFavorite={true})
   }
  }}
  ui.onNodeWithContentDescription("Избранное: Покупка").assertDoesNotExist();ui.onNodeWithContentDescription("Избранное: Каталог").assertExists()
  ui.onNodeWithText("Группа").performClick();ui.onAllNodesWithText("Продукты").assertCountEquals(2);ui.onAllNodesWithContentDescription("Избранное: Продукты").assertCountEquals(1)
 }
 @Test fun productUsageRowsOpenTheProductAndDismissTheDialog(){
  var opened=0L;var dismissed=false
  show{ProductUsageDialog("Получатель",listOf(ProductEntity(id=37,name="Банан",isFavorite=true)),{opened=it},{dismissed=true})}
  ui.onNodeWithText("Банан").performClick();assertEquals(37L,opened);assertTrue(dismissed)
 }
 @Test fun departmentUsageShowsStoresWithTheirProductsAndBothAreClickable(){
  var opened=0L;var shop=0L
  show{DepartmentUsageDialog(listOf(SettingsViewModel.DepartmentUsage(ShopEntity(id=3,name="Батон"),listOf(ProductEntity(id=17,name="Молоко"))),SettingsViewModel.DepartmentUsage(ShopEntity(id=4,name="Красный Яр"),emptyList())),{opened=it},{shop=it},{})}
  ui.onNodeWithText("Батон").assertExists();ui.onNodeWithText("Нет привязанных товаров").assertExists();shot("department-usage")
  ui.onNodeWithText("Молоко").performClick();assertEquals(17L,opened);ui.onNodeWithText("Красный Яр").performClick();assertEquals(4L,shop)
 }
 @Test fun resetRequiresChoosingScopeAndExplicitConfirmation(){
  var called:ResetMode?=null
  show{ResetSettingsCard(false){called=it}}
  ui.onNodeWithText("Сброс").performClick();ui.onNodeWithText("Сбросить всё приложение").performClick();shot("reset-confirmation")
  assertNull(called);ui.onNodeWithText("Отмена").performClick();assertNull(called)
  ui.onNodeWithText("Сброс").performClick();ui.onNodeWithText("Только исходные выпадающие списки").performClick();ui.onNodeWithText("Да").performClick();assertEquals(ResetMode.DICTIONARIES,called)
 }
 @Test fun optionalQuantityAndUnitAppearTogetherAndPurchaseTypeRemainsSeparate(){
  var state by mutableStateOf(AddToListDefaultsState())
  show{CreationDefaultsCard(state,listOf(UnitEntity(id=1,name="шт")),{state=state.copy(fillMissing=it)},{},{},{})}
  ui.onNodeWithText("Количество").assertDoesNotExist();ui.onNodeWithText("Единица").assertDoesNotExist();ui.onNodeWithText("Тип покупки по умолчанию").assertExists()
  ui.onNodeWithText("Подставлять, если не заполнено").performClick();ui.onNodeWithText("Количество").assertExists();ui.onNodeWithText("Единица").assertExists()
  ui.onNodeWithText("Подставлять, если не заполнено").performClick();ui.onNodeWithText("Количество").assertDoesNotExist()
 }
}
