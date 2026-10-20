package com.spisokryadom.app

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.entity.ProductGroupEntity
import com.spisokryadom.app.data.entity.ProductEntity
import com.spisokryadom.app.ui.components.*
import com.spisokryadom.app.ui.settings.*
import com.spisokryadom.app.ui.theme.SpisokRyadomTheme
import com.spisokryadom.app.util.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=Application::class,qualifiers="w360dp-h640dp-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Favorites200UiTest {
 @get:Rule val ui=createComposeRule()
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 private val prefs get()=app.getSharedPreferences("shopping_list_prefs",0)
 @Before fun setup(){prefs.edit().clear().commit()}
 @Test fun groupMenuDuplicatesPinsAboveFullAlphabetAndKeepsNoneCreateLast(){
  val list=listOf(ProductGroupEntity(1,"Продукты",isFavorite=true,favoriteOrder=0),ProductGroupEntity(2,"Аптека"),ProductGroupEntity(3,"Гигиена",isFavorite=true,favoriteOrder=1))
  var chosen:Long?=null
  ui.setContent{SpisokRyadomTheme{Column(Modifier.fillMaxSize().padding(16.dp)){DropdownSelector("Группа",list,null,{it.name},{chosen=it?.id},itemFavorite={it.isFavorite},favoriteOrder={it.favoriteOrder},alphabetical=true,allowCreate=true,onCreateNew={})}}}
  ui.onNodeWithText("Группа").performClick()
  assertEquals(2,ui.onAllNodesWithText("Продукты").fetchSemanticsNodes().size);assertEquals(2,ui.onAllNodesWithText("Гигиена").fetchSemanticsNodes().size)
  val food=ui.onAllNodesWithText("Продукты");val pharmacy=ui.onNodeWithText("Аптека")
  assertTrue(food[0].getUnclippedBoundsInRoot().top<pharmacy.getUnclippedBoundsInRoot().top)
  assertTrue(pharmacy.getUnclippedBoundsInRoot().top<food[1].getUnclippedBoundsInRoot().top)
  val none=ui.onNode(hasText("Не задано") and hasAnyAncestor(isPopup()))
  none.performScrollTo();assertTrue(none.getUnclippedBoundsInRoot().top<ui.onNodeWithText("Создать...").getUnclippedBoundsInRoot().top)
  food[1].performScrollTo().performClick();assertEquals(1L,chosen)
 }
 @Test fun blockAndFullListStarAndBoldAreIndependentAndReactToPreferences(){
  val product=ProductEntity(name="Банан",isFavorite=true)
  var blockStyle:FavoriteStyle?=null;var rowStyle:FavoriteStyle?=null
  ui.setContent{SpisokRyadomTheme{Column{
   FavoriteBlock{ProductCandidateTitle(product);val s=favoriteStyle();SideEffect{blockStyle=s}}
   ProductCandidateTitle(product);val s=favoriteStyle();SideEffect{rowStyle=s}
  }}}
  assertEquals(2,ui.onAllNodesWithContentDescription("Избранный товар: Банан").fetchSemanticsNodes().size)
  ui.runOnIdle{FavoriteAppearance(block=FavoriteStyle(star=false,bold=false),row=FavoriteStyle(star=true,bold=true)).save(prefs)}
  ui.waitUntil{ui.onAllNodesWithContentDescription("Избранный товар: Банан").fetchSemanticsNodes().size==1}
  ui.runOnIdle{assertFalse(blockStyle!!.bold);assertTrue(rowStyle!!.bold)}
  ui.runOnIdle{FavoriteAppearance(block=FavoriteStyle(star=false),row=FavoriteStyle(star=false)).save(prefs)}
  ui.waitUntil{ui.onAllNodesWithContentDescription("Избранный товар: Банан").fetchSemanticsNodes().isEmpty()}
  assertEquals(2,ui.onAllNodesWithText("Банан").fetchSemanticsNodes().size)
 }
 @Test fun helpStaysHiddenUntilQuestionIconIsTapped(){
  ui.setContent{SpisokRyadomTheme{SettingSwitch("Автосохранение",false,{},help="Сохранять только кнопкой")}}
  ui.onNodeWithText("Сохранять только кнопкой").assertDoesNotExist();ui.onNodeWithContentDescription("Подсказка: Автосохранение").performClick();ui.onNodeWithText("Сохранять только кнопкой").assertIsDisplayed();ui.onNodeWithText("Понятно").performClick();ui.onNodeWithText("Сохранять только кнопкой").assertDoesNotExist()
 }
 @Test fun multiSelectKeepsSingleSelectionWhenFavoriteAlsoOccursInFullList(){
  var result=listOf<String>()
  ui.setContent{SpisokRyadomTheme{MultiSelectDialog("Группы",listOf("Продукты","Аптека"),emptySet(),onConfirm={result=it},onDismiss={},favorites=listOf("Продукты"),alphabetical=true)}}
  ui.onAllNodesWithText("Продукты")[0].performClick();ui.onNodeWithText("Добавить").performClick();assertEquals(listOf("Продукты"),result)
 }
}
