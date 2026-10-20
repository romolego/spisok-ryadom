package com.spisokryadom.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.spisokryadom.app.data.entity.ShopBranchEntity
import com.spisokryadom.app.ui.productcard.ShopProductSettingsDialog
import com.spisokryadom.app.ui.theme.SpisokRyadomTheme
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="w640dp-h360dp-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProductCard190LandscapeTest {
 @get:Rule val ui=createComposeRule()
 @Test fun shopDialogCanScrollWhileConfirmationStaysVisibleInLandscape(){
  ui.setContent{SpisokRyadomTheme{Box(Modifier.fillMaxSize()){
   ShopProductSettingsDialog("Красный Яр",emptyList(),null,
    listOf(ShopBranchEntity(id=1,shopId=1,address="Весны, 1"),ShopBranchEntity(id=2,shopId=1,address="Молокова, 54",displayOrder=1)),
    null,emptyList(),{_,_,_->},{},{_,_->},false,emptyList())
  }}}
  ui.onNodeWithText("Доступность товара").performScrollTo().performClick();ui.onNodeWithText("Только выбранные точки").performClick()
  ui.onNode(isToggleable() and hasText("Молокова, 54")).performScrollTo().assertIsDisplayed()
  ui.onNodeWithText("Применить").assertIsDisplayed().assertIsEnabled()
  ui.runOnIdle{val v=org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView;val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b));val f=File("../.local/refine190/screens/08-landscape-shop-dialog-api35.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
 }
}
