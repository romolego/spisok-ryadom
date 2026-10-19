package com.spisokryadom.app
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.spisokryadom.app.ui.components.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],qualifiers="w360dp-h640dp-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OrderMotionTest {
 @get:Rule val ui=createComposeRule()
 @Test fun unequalRowsBothSlideAndRapidReverseKeepsCurrentPosition(){
  val order=mutableStateOf(listOf("Первый","Второй","Третий"))
  ui.setContent{MaterialTheme{AnimatedOrderColumn(order.value,{it}){index,name->
   Card(Modifier.fillMaxWidth().height(if(name=="Первый")80.dp else 112.dp).testTag(name)){
    Text(name);ReorderButtons({val a=order.value.toMutableList();a.add(index-1,a.removeAt(index));order.value=a},{val a=order.value.toMutableList();a.add(index+1,a.removeAt(index));order.value=a},index>0,index<2,"Выше $name","Ниже $name")
   }
  }}}
  ui.waitForIdle();fun y(name:String)=ui.onNodeWithTag(name).fetchSemanticsNode().boundsInRoot.top
  val a=y("Первый");val b=y("Второй");val c=y("Третий")
  ui.mainClock.autoAdvance=false
  ui.onNodeWithContentDescription("Ниже Первый").performClick();ui.mainClock.advanceTimeByFrame();ui.mainClock.advanceTimeBy(112)
  val amid=y("Первый");val bmid=y("Второй")
  assertTrue("Moved row must be between old and new positions: $a < $amid < $b",amid>a&&amid<b)
  assertTrue("Displaced neighbour slides in opposite direction",bmid>a&&bmid<b)
  assertEquals(c,y("Третий"),1f)
  ui.onNodeWithContentDescription("Выше Первый").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick);ui.mainClock.advanceTimeByFrame()
  val reverseStart=y("Первый")
  assertTrue("Reverse must remain between endpoints, not snap: before=$amid after=$reverseStart",reverseStart>a&&reverseStart<240f)
  ui.mainClock.advanceTimeBy(64)
  assertTrue("Reverse must move back towards the starting position",y("Первый")<reverseStart&&y("Первый")>a)
  ui.mainClock.advanceTimeBy(400)
  assertEquals(a,y("Первый"),1f);assertEquals(b,y("Второй"),1f)
  ui.onNodeWithContentDescription("Ниже Первый").performClick();ui.mainClock.advanceTimeBy(400)
  assertEquals(a,y("Второй"),1f)
  assertEquals(240f,y("Первый"),2f) // second row height112dp + 8dp gap, density2
  ui.mainClock.autoAdvance=true
 }
 @Test fun lazyRowsSlideInBothDirectionsWithoutMovingTheViewport(){
  val order=mutableStateOf(listOf("А","Б","В"))
  var viewport:ReorderViewport?=null
  ui.setContent{MaterialTheme{val state=androidx.compose.foundation.lazy.rememberLazyListState();viewport=rememberReorderViewport(state,order.value);androidx.compose.foundation.lazy.LazyColumn(state=state,modifier=Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)){
   items(items=order.value,key={it}){name->Card(Modifier.fillMaxWidth().height(80.dp).animateItem(fadeInSpec=null,fadeOutSpec=null,placementSpec=androidx.compose.animation.core.tween(300)).testTag(name)){Text(name)}}
  }}}
  ui.waitForIdle();fun y(n:String)=ui.onNodeWithTag(n).fetchSemanticsNode().boundsInRoot.top
  val a=y("А");val b=y("Б");val c=y("В");ui.mainClock.autoAdvance=false
  ui.runOnIdle{viewport!!.capture();order.value=listOf("Б","А","В")};ui.mainClock.advanceTimeByFrame();ui.waitForIdle();ui.mainClock.advanceTimeBy(112);ui.waitForIdle()
  val am=y("А");val bm=y("Б");assertTrue("Lazy moved row: $a < $am < $b",am>a&&am<b);assertTrue("Lazy neighbour: $a < $bm < $b",bm>a&&bm<b);assertEquals(c,y("В"),1f)
  ui.mainClock.advanceTimeBy(400);assertEquals(a,y("Б"),1f);assertEquals(b,y("А"),1f);ui.mainClock.autoAdvance=true
 }

}
