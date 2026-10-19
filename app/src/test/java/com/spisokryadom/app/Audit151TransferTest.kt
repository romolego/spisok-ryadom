package com.spisokryadom.app

import com.spisokryadom.app.domain.*
import org.junit.Assert.*
import org.junit.Test

/** Regressions from an independent audit; portable inputs are validated before Double rounding. */
class Audit151TransferTest {
    private val source = GoalListDocument("На ужин", listOf(GoalListItemDraft("Огурцы", 1.0, "кг", 10.0)))
    private fun reject(input: String) {
        val preview = GoalListTransfer.parse(input)
        assertFalse("Unexpectedly accepted: $input", preview.canImport)
        assertNull(preview.document)
        assertTrue(preview.errors.isNotEmpty())
    }
    @Test fun bareUncheckedMessengerChecklistIsPlainTextRatherThanJson() {
        val result = GoalListTransfer.parse("[ ] Огурцы — 2 кг\n[x] Соль — 1 уп.")
        assertTrue(result.errors.joinToString(), result.canImport)
        assertEquals(GoalListTransferFormat.PLAIN_TEXT, result.format)
        assertEquals(listOf("Огурцы", "Соль"), result.document!!.items.map { it.name })
        assertEquals(2.0, result.document!!.items[0].quantity, 0.0)
        assertFalse(result.document!!.items[0].isBought)
        assertTrue(result.document!!.items[1].isBought)
    }
    @Test fun bareCheckedMessengerChecklistKeepsUrgencyAndCheckedFlag() {
        val result = GoalListTransfer.parse("[X] ! Чай\n[ ] Хлеб")
        assertTrue(result.errors.joinToString(), result.canImport)
        assertTrue(result.document!!.items[0].isBought)
        assertTrue(result.document!!.items[0].isUrgent)
        assertEquals("Чай", result.document!!.items[0].name)
    }
    @Test fun realJsonArrayStillFailsInsteadOfBecomingAProduct() {
        listOf("[]", "[{}]", "[1,2]", "[null]").forEach(::reject)
    }
    @Test fun jsonQuantityPrecisionCannotBeHiddenByDoubleRounding() {
        val json = GoalListTransfer.exportJson(source)
        listOf("1.00000000000000001", "0.99999999999999999", "1.0000001").forEach { quantity ->
            reject(json.replace("\"quantity\": 1", "\"quantity\": $quantity"))
        }
    }
    @Test fun textQuantityPrecisionCannotBeHiddenByDoubleRounding() {
        val text = GoalListTransfer.exportText(source)
        reject(text.replace(" | 1 \"кг\"", " | 1.00000000000000001 \"кг\""))
        reject("Огурцы — 1.00000000000000001 кг")
    }
    @Test fun exactQuantityBoundsApplyBeforeDoubleConversion() {
        val json = GoalListTransfer.exportJson(source)
        listOf("1000000000.00000001", "0.00000099999999999999999").forEach { quantity ->
            reject(json.replace("\"quantity\": 1", "\"quantity\": $quantity"))
        }
    }
    @Test fun exactPriceUpperBoundAppliesBeforeDoubleConversion() {
        val json = GoalListTransfer.exportJson(source)
        reject(json.replace("\"unitPrice\": 10", "\"unitPrice\": 1000000000000.00001"))
        reject("Огурцы — 1 кг — 1000000000000.00001 ₽")
    }
    @Test fun trailingZeroesAndScientificSixDecimalQuantityRemainValid() {
        val json = GoalListTransfer.exportJson(source)
        for (token in listOf("1.000000000000000000", "0.00000100000000", "1e-6", "1000000000.000000")) {
            val result = GoalListTransfer.parse(json.replace("\"quantity\": 1", "\"quantity\": $token"))
            assertTrue("$token: ${result.errors}", result.canImport)
            assertEquals(token.toBigDecimal().toDouble(), result.document!!.items.single().quantity, 0.0)
        }
    }
    @Test fun separateLongNoteAndCommentRoundTripWithoutMergingOrTruncation() {
        val item = GoalListItemDraft("Сыр | \"редкий\"", .123456, "кг", 3.125,
            note = "З".repeat(10_000), shopName = "Рынок | павильон", shopAddress = "Весны, 1",
            isBought = true, isUrgent = true, comment = "К".repeat(9_995) + "\n|\"🌻")
        val value = GoalListDocument("Ужин", listOf(item, source.items.single()))
        val text = GoalListTransfer.exportText(value)
        val json = GoalListTransfer.exportJson(value)
        assertTrue(text.startsWith("Список рядом — целевой список v2"))
        assertTrue(json.contains("\"version\": 2"))
        for (input in listOf(text, json)) {
            val preview = GoalListTransfer.parse(input)
            assertTrue(preview.errors.joinToString(), preview.canImport)
            assertEquals(value, preview.document)
            assertTrue(preview.warnings.isEmpty())
        }
    }
    @Test fun noCommentKeepsOriginalV1WireFormat() {
        val text = GoalListTransfer.exportText(source)
        val json = GoalListTransfer.exportJson(source)
        assertTrue(text.startsWith("Список рядом — целевой список v1"))
        assertTrue(json.contains("\"version\": 1"))
        assertFalse(json.contains("\"comment\""))
        assertEquals(source, GoalListTransfer.parse(text).document)
        assertEquals(source, GoalListTransfer.parse(json).document)
    }
    @Test fun v2CannotBeDowngradedToSilentlyLoseComments() {
        val value = source.copy(items = listOf(source.items.single().copy(comment = "Важное")))
        val text = GoalListTransfer.exportText(value)
        val json = GoalListTransfer.exportJson(value)
        reject(text.replace("список v2", "список v1"))
        reject(json.replace("\"version\": 2", "\"version\": 1"))
        reject(text.replace("список v2", "список v3"))
        reject(json.replace("\"version\": 2", "\"version\": 3"))
    }
    @Test fun malformedAndOversizedCommentsFailWholeImport() {
        val value = source.copy(items = listOf(source.items.single().copy(comment = "Важное")))
        val json = GoalListTransfer.exportJson(value)
        reject(json.replace("\"comment\": \"Важное\"", "\"comment\": null"))
        reject(json.replace("\"comment\": \"Важное\"", "\"comment\": 123"))
        reject(json.replace("Важное", "К".repeat(10_001)))
        reject(json.replace("Важное", "\\u0000"))
        val dto = value.copy(items = listOf(value.items.single().copy(comment = "К".repeat(10_001))))
        assertTrue(GoalListTransfer.validate(dto).any { it.contains("комментарий") })
    }
}
