package com.spisokryadom.app

import com.spisokryadom.app.domain.*
import org.junit.Assert.*
import org.junit.Test

/** Pure JVM checks: no Room, catalogue, preferences or Android JSON stubs can affect imports. */
class GoalListTransferTest {
    private val document = GoalListDocument("Ужин \"Лето\" | 🌻\nна двоих", listOf(
        GoalListItemDraft("Помидоры | черри", 0.75, "кг", 195.5, "Спелые\nбез \\" + "пятен\"", "Рынок — 29 павильон", "Адрес: 1 | 2", isUrgent = true),
        GoalListItemDraft("Помидоры | черри", 2.0, "уп.\tмаленькая", 0.0, "", "Батон", "Весны, 16а", isBought = true),
        GoalListItemDraft("Видеокарта RTX 4070", unit = "", note = "магазин пока неизвестен"),
        GoalListItemDraft("Помидоры | черри", 0.75, "кг", 195.5, "после другой покупки", "Рынок — 29 павильон", "Адрес: 1 | 2"),
    ))
    private fun imported(text: String): GoalListDocument {
        val preview = GoalListTransfer.parse(text)
        assertTrue(preview.errors.joinToString(), preview.canImport)
        return preview.document!!
    }
    private fun rejected(text: String): GoalListTransferPreview = GoalListTransfer.parse(text).also {
        assertFalse("Не должен приниматься: $text", it.canImport)
        assertNull(it.document)
        assertTrue(it.errors.isNotEmpty())
    }

    @Test fun readableMessageRoundTripsDelimitersUnicodeMultilineDuplicatesAndPlaceChanges() {
        val text = GoalListTransfer.exportText(document)
        assertTrue(text.contains("Помидоры"))
        assertTrue(text.contains("195.5 ₽/ед."))
        assertTrue(text.contains("- [x]"))
        assertEquals(document, imported(text))
        assertEquals(GoalListTransferFormat.TEXT, GoalListTransfer.parse(text).format)
        assertTrue(GoalListTransfer.parse(text).warnings.isEmpty())
    }
    @Test fun jsonRoundTripsSamePortableDocumentWithoutDatabaseIdentifiers() {
        val json = GoalListTransfer.exportJson(document)
        assertEquals(document, imported(json))
        assertEquals(GoalListTransferFormat.JSON, GoalListTransfer.parse(json).format)
        assertFalse(json.contains("productId"))
        assertFalse(json.contains("listId"))
    }
    @Test fun bomWindowsNewlinesAndTelegramCodeBlockAreAcceptedWithVisibleWarning() {
        val text = "\uFEFF```text\r\n" + GoalListTransfer.exportText(document).replace("\n", "\r\n") + "\r\n```"
        val preview = GoalListTransfer.parse(text)
        assertTrue(preview.canImport)
        assertEquals(document, preview.document)
        assertTrue(preview.warnings.single().contains("блока кода"))
    }
    @Test fun friendlyBulletsKeepShopContextAmountsPriceNotesCheckboxAndUrgency() {
        val preview = GoalListTransfer.parse("""
            Список: Салат
            Магазин: Батон | Весны, 16а
            • Огурцы — 2 кг — 150 ₽/ед.
            - [x] ! Соль — 1 уп. — без добавок
            Без магазина:
            1. Миска
        """.trimIndent())
        assertTrue(preview.errors.joinToString(), preview.canImport)
        assertEquals("Салат", preview.document!!.name)
        val items = preview.document!!.items
        assertEquals(3, items.size)
        assertEquals(2.0, items[0].quantity, 0.0)
        assertEquals("кг", items[0].unit)
        assertEquals(150.0, items[0].unitPrice!!, 0.0)
        assertEquals("Весны, 16а", items[0].shopAddress)
        assertEquals("без добавок", items[1].note)
        assertTrue(items[1].isBought && items[1].isUrgent)
        assertEquals("", items[2].shopName)
        assertTrue(preview.warnings.any { it.contains("Обычный текст") })
        assertTrue(preview.warnings.any { it.contains("за заметку") })
    }
    @Test fun ordinaryShoppingShareHeadingsAreRecognizedWithoutGuessingStoreIds() {
        val result = imported("Батон:\nМолоко — 2 бутылка\nХлеб — 1 шт\n\nЕнисейский привоз — 29 павильон:\nСыр — 0,5 кг")
        assertEquals("Батон", result.items[0].shopName)
        assertEquals("Енисейский привоз — 29 павильон", result.items[2].shopName)
        assertEquals(0.5, result.items[2].quantity, 0.0)
        assertEquals(2.0, imported("— Молоко - 2 л").items.single().quantity, 0.0)
    }
    @Test fun productModelNumbersRemainInNameAndAmbiguityIsReported() {
        val preview = GoalListTransfer.parse("Видеокарта RTX 4070\nЧай — зелёный — с жасмином — упаковка")
        assertTrue(preview.canImport)
        assertEquals("Видеокарта RTX 4070", preview.document!!.items[0].name)
        assertEquals("Чай — зелёный — с жасмином — упаковка", preview.document!!.items[1].name)
        assertEquals(1.0, preview.document!!.items[0].quantity, 0.0)
        assertTrue(preview.warnings.any { it.contains("числа", true) })
        assertTrue(preview.warnings.any { it.contains("неоднозначные", true) })
    }
    @Test fun blankInputTitlesOnlyAndRepeatedTitlesCannotCreatePartialImport() {
        rejected("  \n ")
        rejected("Список: Салат\nМагазин: Батон")
        rejected("Список: Салат\nОгурцы\nСписок: Другой")
    }
    @Test fun malformedQuotedTextUnknownVersionAndUnknownLinesAreFatal() {
        val text = GoalListTransfer.exportText(document)
        rejected(text.replace("список v1", "список v3"))
        rejected(text + "\nКомментарий без формата")
        rejected(text.replace("\"Помидоры | черри\"", "\"Помидоры | черри"))
        rejected(text.replace("0.75 \"кг\"", "не число \"кг\""))
    }
    @Test fun jsonRejectsUnknownFormatVersionWrongTypesAndTrailingObjects() {
        val json = GoalListTransfer.exportJson(document)
        rejected(json.replace("spisok-ryadom-goal-list", "foreign-app"))
        rejected(json.replace("\"version\": 1", "\"version\": 3"))
        rejected(json.replace("\"version\": 1", "\"version\": \"1\""))
        rejected(json.replace("\"quantity\": 0.75", "\"quantity\": \"0.75\""))
        rejected(json.replace("\"isBought\": false", "\"isBought\": \"false\""))
        rejected(json + "{}")
        rejected("[]")
    }
    @Test fun jsonRejectsDuplicateKeysEvenWhenOneKeyUsesEscaping() {
        val json = GoalListTransfer.exportJson(GoalListDocument("Рецепт", emptyList()))
        rejected(json.replace("\"name\": \"Рецепт\"", "\"name\": \"Рецепт\", \"name\": \"Подмена\""))
        rejected(json.replace("\"name\": \"Рецепт\"", "\"name\": \"Рецепт\", \"na\\u006de\": \"Подмена\""))
    }
    @Test fun unknownJsonFieldsAreReportedAndDoNotBecomeForeignCatalogueLinks() {
        val json = GoalListTransfer.exportJson(document).replace("\"quantity\": 0.75", "\"productId\": 42, \"quantity\": 0.75")
        val preview = GoalListTransfer.parse(json)
        assertTrue(preview.canImport)
        assertEquals(document, preview.document)
        assertTrue(preview.warnings.any { it.contains("productId") && it.contains("не будут") })
    }
    @Test fun negativeZeroNonfiniteOutOfRangeAndUnderflowNumbersAreRejected() {
        val json = GoalListTransfer.exportJson(document)
        listOf("-1", "0", "NaN", "Infinity", "1e999", "1e-999", "1000000001").forEach {
            rejected(json.replace("\"quantity\": 0.75", "\"quantity\": $it"))
        }
        listOf("-1", "NaN", "Infinity", "1e999", "1e-999", "1000000000001").forEach {
            rejected(json.replace("\"unitPrice\": 195.5", "\"unitPrice\": $it"))
        }
        rejected("Огурцы — -2 кг")
        rejected("Огурцы — NaN кг")
        rejected("Огурцы — 1 кг — 1e-999 ₽")
    }
    @Test fun invalidDirectDtoCannotBeExportedOrImportedThroughRepositoryGuard() {
        val invalid = document.copy(items = listOf(GoalListItemDraft("", Double.NaN, unitPrice = -1.0)))
        assertTrue(GoalListTransfer.validate(invalid).size >= 3)
        try { GoalListTransfer.exportText(invalid); fail("Нельзя экспортировать некорректные данные") } catch (_: IllegalArgumentException) { }
        try { GoalListTransfer.exportJson(invalid); fail("Нельзя экспортировать некорректные данные") } catch (_: IllegalArgumentException) { }
    }
    @Test fun extraQuantityPrecisionIsRejectedBeforeRepositoryCouldRoundIt() {
        val text = GoalListTransfer.exportText(document)
        rejected(text.replace("0.75 \"кг\"", "0.7500001 \"кг\""))
        val json = GoalListTransfer.exportJson(document)
        rejected(json.replace("\"quantity\": 0.75", "\"quantity\": 0.7500001"))
        assertTrue(GoalListTransfer.validate(document.copy(items = listOf(GoalListItemDraft("Товар", 1.0000001)))).any { it.contains("6 знаков") })
    }
    @Test fun emptyPortableListRoundTripsAndIsExplicitInPreview() {
        val empty = GoalListDocument("Пока только идея", emptyList())
        listOf(GoalListTransfer.exportJson(empty), GoalListTransfer.exportText(empty)).forEach { text ->
            val preview = GoalListTransfer.parse(text)
            assertTrue(preview.canImport)
            assertEquals(empty, preview.document)
            assertTrue(preview.warnings.any { it.contains("нет позиций") })
        }
    }
    @Test fun itemAndUtf8ByteLimitsFailEntireImportRatherThanTruncatingIt() {
        val max = GoalListDocument("Много", List(GoalListTransfer.MAX_ITEMS) { GoalListItemDraft("Повтор") })
        assertEquals(1000, imported(GoalListTransfer.exportText(max)).items.size)
        rejected(List(1001) { "- Повтор" }.joinToString("\n"))
        rejected("Я".repeat(GoalListTransfer.MAX_INPUT_BYTES / 2 + 1))
        assertTrue(GoalListTransfer.validate(max.copy(items = max.items + GoalListItemDraft("Лишняя"))).isNotEmpty())
    }
    @Test fun fieldLengthControlCharactersMalformedUnicodeAndDeepJsonAreRejected() {
        rejected("Список: " + "А".repeat(201) + "\nОгурцы")
        rejected("Огурцы\u0000")
        rejected("Огурцы\uD800")
        val json = GoalListTransfer.exportJson(document)
        rejected(json.replace("\"name\": \"Помидоры | черри\"", "\"name\": \"\\uD800\""))
        rejected("[".repeat(20) + "0" + "]".repeat(20))
    }
    @Test fun strictJsonRejectsJavascriptShortcutsAndMalformedEscapes() {
        val json = GoalListTransfer.exportJson(GoalListDocument("Рецепт", emptyList()))
        rejected(json.replace("\"version\": 1", "\"version\": 01"))
        rejected(json.replace("\"version\": 1", "\"version\": +1"))
        rejected(json.replace("\"version\": 1", "\"version\": 1,"))
        rejected(json.replace("Рецепт", "\\q"))
        rejected(json.replace("Рецепт", "\\u+041"))
        rejected(json.replace("Рецепт", "\\u-001"))
        rejected(json.replace("\"name\"", "name"))
    }
    @Test fun importedFlagsRemainInPreviewSoCallerCanExplicitlyResetBoughtOnNewList() {
        val result = imported(GoalListTransfer.exportText(document))
        assertTrue(result.items[1].isBought)
        assertTrue(result.items[0].isUrgent)
        assertEquals(4, result.items.size)
        assertEquals(3, result.items.count { it.name == "Помидоры | черри" })
    }
}
