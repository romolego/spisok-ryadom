package com.spisokryadom.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule

/** Navigate the actual on-demand manager rather than finding hidden rows on Settings. */
fun ComposeContentTestRule.openSettingsDictionary(kind:String){
 closeSettingsDictionary()
 onNodeWithTag("settings-list").performScrollToNode(hasTestTag("dictionary-open-"+kind))
 onNodeWithTag("dictionary-open-"+kind).performClick()
 onNodeWithTag("dictionary-manager").assertExists()
}
fun ComposeContentTestRule.closeSettingsDictionary(){
 if(onAllNodesWithTag("dictionary-manager").fetchSemanticsNodes().isNotEmpty())onNodeWithText("Закрыть").performClick()
}
fun ComposeContentTestRule.activeLazyList():SemanticsNodeInteraction=
 if(onAllNodesWithTag("dictionary-manager").fetchSemanticsNodes().isNotEmpty())onNodeWithTag("dictionary-manager")else onNode(hasScrollToIndexAction())

fun ComposeContentTestRule.awaitDictionaryControl(description:String){
 waitUntil(20_000){
  org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(20))
  waitForIdle()
  runCatching{activeLazyList().performScrollToNode(hasContentDescription(description))}.isSuccess
 }
}
