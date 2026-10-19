package com.spisokryadom.app

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.spisokryadom.app.ui.components.InlineQuantityControl
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

/** Inline editor regression checks, isolated from Activity navigation and Room. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35], application=Application::class, qualifiers="w360dp-h640dp-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IndependentInlineFocusAuditTest {
    @get:Rule val ui=createComposeRule()

    @Test fun tappingQuantityMustOpenEditorWithoutCommitting() {
        val writes=mutableListOf<Double>()
        ui.setContent {
            MaterialTheme {
                InlineQuantityControl(
                    quantity=3.0, unit="кг", onIncrement={}, onDecrement={},
                    onSetQuantity={writes.add(it)}
                )
            }
        }
        ui.onNodeWithText("3").performClick()
        ui.waitForIdle()
        val editors=ui.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size
        println("After tap: SetText nodes=$editors; unsolicited writes=$writes")
        println(ui.onRoot().printToString())
        ui.runOnIdle {
            assertTrue("Opening the quantity editor must not commit: $writes",writes.isEmpty())
        }
        ui.onNode(hasSetTextAction()).assertExists()
        ui.onNode(hasSetTextAction()).assertIsFocused()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(0,1)))
    }

    @Test fun doneAndSubsequentFocusLossCommitOnlyOnce() {
        val writes=mutableListOf<Double>()
        editorWithAnotherFocusTarget(writes)
        ui.onNodeWithText("3").performClick()
        quantityEditor().performTextReplacement("0,5")
        quantityEditor().performImeAction()
        ui.onNodeWithTag("other").performClick()
        ui.runOnIdle { assertEquals(listOf(.5),writes) }
        quantityEditor().assertDoesNotExist()
    }

    @Test fun actualFocusLossCommitsTheDecimalExactlyOnce() {
        val writes=mutableListOf<Double>()
        editorWithAnotherFocusTarget(writes)
        ui.onNodeWithText("3").performClick()
        quantityEditor().performTextReplacement("0,5")
        ui.onNodeWithTag("other").performClick()
        ui.runOnIdle { assertEquals(listOf(.5),writes) }
        quantityEditor().assertDoesNotExist()
    }

    @Test fun invalidInputStaysVisibleAndCannotSilentlyChangeDigits() {
        val writes=mutableListOf<Double>()
        editorWithAnotherFocusTarget(writes)
        ui.onNodeWithText("3").performClick()
        for (invalid in listOf("-5","0","1,2,3","0.0000001","1000000001")) {
            quantityEditor().performTextReplacement(invalid)
            quantityEditor().performImeAction()
            quantityEditor().assertTextEquals(invalid).assertIsFocused()
            ui.onNodeWithText("Некорректное количество").assertIsDisplayed()
            ui.runOnIdle { assertTrue("Invalid input must not commit: $invalid, $writes",writes.isEmpty()) }
        }
        // Invalid blur also keeps the draft instead of applying a transformed value.
        ui.onNodeWithTag("other").performClick()
        quantityEditor().assertTextEquals("1000000001")
        ui.runOnIdle { assertTrue(writes.isEmpty()) }
        quantityEditor().performClick().performTextReplacement("0,5")
        quantityEditor().performImeAction()
        ui.runOnIdle { assertEquals(listOf(.5),writes) }
    }

    private fun quantityEditor() = ui.onNode(hasSetTextAction() and !hasTestTag("other"))

    private fun editorWithAnotherFocusTarget(writes:MutableList<Double>) {
        ui.setContent {
            MaterialTheme {
                Column {
                    InlineQuantityControl(quantity=3.0,unit="кг",onIncrement={},onDecrement={},onSetQuantity={writes.add(it)})
                    var other by remember { mutableStateOf("Другое поле") }
                    BasicTextField(value=other,onValueChange={other=it},modifier=Modifier.testTag("other"))
                }
            }
        }
    }

    @Test fun harnessCanRequestFocusAndEnterDecimalComma() {
        val events=mutableListOf<String>()
        var value="3"
        ui.setContent {
            var input by remember { mutableStateOf("3") }
            val requester=remember { FocusRequester() }
            LaunchedEffect(Unit) {
                events.add("requestFocus")
                requester.requestFocus()
            }
            MaterialTheme {
                BasicTextField(
                    value=input,
                    onValueChange={input=it;value=it},
                    modifier=Modifier.focusRequester(requester)
                        .onFocusChanged { events.add("focus=${it.isFocused}") }
                )
            }
        }
        ui.waitForIdle()
        ui.onNode(hasSetTextAction()).assertIsFocused().performTextReplacement("0,5")
        ui.runOnIdle {
            println("Focus harness events=$events")
            assertEquals("0,5",value)
        }
    }
}

