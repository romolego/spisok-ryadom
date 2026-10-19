package com.spisokryadom.app.domain

import java.math.BigDecimal

/** Portable values only: a shared list never contains IDs from somebody else's catalogue. */
data class GoalListItemDraft(
    val name: String,
    val quantity: Double = 1.0,
    val unit: String = "шт",
    val unitPrice: Double? = null,
    val note: String = "",
    val shopName: String = "",
    val shopAddress: String = "",
    val isBought: Boolean = false,
    val isUrgent: Boolean = false,
    val comment: String = "",
)

data class GoalListDocument(val name: String, val items: List<GoalListItemDraft>)
enum class GoalListTransferFormat { TEXT, JSON, PLAIN_TEXT }
data class GoalListTransferPreview(
    val document: GoalListDocument?,
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
    val format: GoalListTransferFormat = GoalListTransferFormat.PLAIN_TEXT,
) {
    val canImport: Boolean get() = document != null && errors.isEmpty()
}

/**
 * Text v1/v2 is a readable, editable message; v2 keeps note and comment separate. Quotes use JSON escaping, so pipes, quotes and
 * multiline names/notes round-trip without a hidden payload. Prices always mean price per unit.
 * The codec has no database access. A caller must show this preview before creating a new list;
 * imported shop captions are advisory and must not silently resolve/create catalogue records.
 */
object GoalListTransfer {
    const val MAX_INPUT_BYTES = 1_048_576
    const val MAX_ITEMS = 1000
    const val MAX_LIST_NAME = 200
    const val MAX_ITEM_NAME = 500
    const val MAX_UNIT = 80
    const val MAX_NOTE = 10_000
    const val MAX_SHOP_NAME = 500
    const val MAX_SHOP_ADDRESS = 1000
    const val MAX_UNIT_PRICE = 1_000_000_000_000.0
    private const val HEADER = "Список рядом — целевой список v1"
    private const val HEADER_V2 = "Список рядом — целевой список v2"
    private const val JSON_FORMAT = "spisok-ryadom-goal-list"
    private const val NUMBER = "[+-]?(?:[0-9]+(?:[.,][0-9]+)?|[.,][0-9]+)(?:[eE][+-]?[0-9]+)?"

    /** Also used by the repository for direct DTO imports, not just parsed messages. */
    fun validate(document: GoalListDocument): List<String> = buildList {
        fun field(value: String, label: String, max: Int, required: Boolean = false) {
            if (required && value.isBlank()) add("$label: укажите название")
            if (value.length > max) add("$label: не более $max символов")
            if ('\u0000' in value || !validUnicode(value)) add("$label: недопустимые символы")
        }
        field(document.name, "Название списка", MAX_LIST_NAME, true)
        if (document.items.size > MAX_ITEMS) add("В списке не может быть более $MAX_ITEMS позиций")
        document.items.take(MAX_ITEMS + 1).forEachIndexed { index, item ->
            val prefix = "Позиция ${index + 1}"
            field(item.name, "$prefix, название", MAX_ITEM_NAME, true)
            field(item.unit, "$prefix, единица", MAX_UNIT)
            field(item.note, "$prefix, заметка", MAX_NOTE)
            field(item.comment, "$prefix, комментарий", MAX_NOTE)
            field(item.shopName, "$prefix, магазин", MAX_SHOP_NAME)
            field(item.shopAddress, "$prefix, адрес", MAX_SHOP_ADDRESS)
            if (!item.quantity.isFinite() || item.quantity < 0.000001 || item.quantity > 1_000_000_000.0)
                add("$prefix: количество должно быть от 0,000001 до 1 000 000 000")
            else if (BigDecimal.valueOf(item.quantity).stripTrailingZeros().scale() > 6)
                add("$prefix: количество поддерживает не более 6 знаков после запятой")
            if (item.unitPrice != null && (!item.unitPrice.isFinite() || item.unitPrice < 0 || item.unitPrice > MAX_UNIT_PRICE))
                add("$prefix: цена за единицу должна быть от 0 до 1 000 000 000 000")
            if (item.unitPrice != null && !(item.quantity * item.unitPrice).isFinite())
                add("$prefix: итоговая стоимость выходит за допустимый диапазон")
        }
    }

    fun exportText(document: GoalListDocument): String {
        requireValid(document)
        val version = if (document.items.any { it.comment.isNotEmpty() }) 2 else 1
        val result = buildString {
            append(if (version == 2) HEADER_V2 else HEADER).append('\n').append("Название: ").append(quote(document.name)).append('\n')
            var previousPlace: Pair<String, String>? = null
            document.items.forEach { item ->
                val place = item.shopName to item.shopAddress
                if (place != previousPlace) {
                    append('\n').append("Магазин: ").append(quote(item.shopName)).append(" | ").append(quote(item.shopAddress)).append('\n')
                    previousPlace = place
                }
                append(if (item.isBought) "- [x] " else "- [ ] ")
                if (item.isUrgent) append("! ")
                append(quote(item.name)).append(" | ").append(decimal(item.quantity)).append(' ').append(quote(item.unit))
                append(" | ").append(item.unitPrice?.let { "${decimal(it)} ₽/ед." } ?: "—")
                append(" | ").append(quote(item.note))
                if (version == 2) append(" | ").append(quote(item.comment))
                append('\n')
            }
        }.trimEnd('\n')
        require(result.toByteArray(Charsets.UTF_8).size <= MAX_INPUT_BYTES) { "Экспорт больше 1 МиБ; разделите список" }
        return result
    }

    fun exportJson(document: GoalListDocument): String {
        requireValid(document)
        val version = if (document.items.any { it.comment.isNotEmpty() }) 2 else 1
        val result = buildString {
            append("{\n  \"format\": ").append(quote(JSON_FORMAT)).append(",\n  \"version\": ").append(version).append(",\n  \"name\": ").append(quote(document.name))
            append(",\n  \"items\": [")
            document.items.forEachIndexed { index, item ->
                if (index > 0) append(',')
                append("\n    {\"name\": ").append(quote(item.name)).append(", \"quantity\": ").append(decimal(item.quantity))
                append(", \"unit\": ").append(quote(item.unit)).append(", \"unitPrice\": ").append(item.unitPrice?.let(::decimal) ?: "null")
                append(", \"note\": ").append(quote(item.note))
                if (version == 2) append(", \"comment\": ").append(quote(item.comment))
                append(", \"shopName\": ").append(quote(item.shopName))
                append(", \"shopAddress\": ").append(quote(item.shopAddress)).append(", \"isBought\": ").append(item.isBought)
                append(", \"isUrgent\": ").append(item.isUrgent).append('}')
            }
            append("\n  ]\n}")
        }
        require(result.toByteArray(Charsets.UTF_8).size <= MAX_INPUT_BYTES) { "Экспорт больше 1 МиБ; разделите список" }
        return result
    }

    /** Entirely invalid input yields no partial document; no rows are silently dropped or merged. */
    fun parse(input: String): GoalListTransferPreview {
        if (input.length > MAX_INPUT_BYTES || input.toByteArray(Charsets.UTF_8).size > MAX_INPUT_BYTES)
            return failed("Размер сообщения больше 1 МиБ")
        if ('\u0000' in input || !validUnicode(input)) return failed("Сообщение содержит недопустимые символы")
        var text = input.removePrefix("\uFEFF").trim().replace("\r\n", "\n").replace('\r', '\n')
        if (text.isBlank()) return failed("Вставьте текст списка или выберите файл")
        val warnings = mutableListOf<String>()
        if (text.startsWith("```")) {
            val lines = text.lines()
            if (lines.size < 3 || lines.last().trim() != "```") return failed("Не закрыт блок текста ```")
            text = lines.drop(1).dropLast(1).joinToString("\n").trim()
            warnings += "Снято оформление блока кода из сообщения"
        }
        val format = when {
            text.startsWith('{') || (text.startsWith('[') && !Regex("^\\[[ xX]\\](?:\\s|$)").containsMatchIn(text)) -> GoalListTransferFormat.JSON
            text.lineSequence().first().startsWith("Список рядом") -> GoalListTransferFormat.TEXT
            else -> GoalListTransferFormat.PLAIN_TEXT
        }
        return try {
            val document = when (format) {
                GoalListTransferFormat.JSON -> parseJson(text, warnings)
                GoalListTransferFormat.TEXT -> parseExactText(text)
                GoalListTransferFormat.PLAIN_TEXT -> parsePlainText(text, warnings)
            }
            val errors = validate(document)
            if (document.items.isEmpty()) warnings += "В списке пока нет позиций"
            GoalListTransferPreview(document.takeIf { errors.isEmpty() }, warnings.distinct(), errors, format)
        } catch (error: TransferFailure) {
            GoalListTransferPreview(null, warnings.distinct(), listOf(error.message.orEmpty()), format)
        } catch (_: NumberFormatException) {
            GoalListTransferPreview(null, warnings.distinct(), listOf("Проверьте числовые значения списка"), format)
        }
    }

    private fun parseExactText(text: String): GoalListDocument {
        val lines = text.lines()
        val version = when (lines.first()) { HEADER -> 1; HEADER_V2 -> 2; else -> fail("Неизвестная версия текстового списка. Поддерживаются v1 и v2") }
        var name: String? = null
        var shopName = ""
        var shopAddress = ""
        val items = mutableListOf<GoalListItemDraft>()
        lines.drop(1).forEachIndexed { index, raw ->
            val line = raw.trim()
            val prefix = "Строка ${index + 2}"
            when {
                line.isBlank() -> Unit
                line.startsWith("Название: ") -> {
                    ensure(name == null && items.isEmpty(), "$prefix: название списка повторено или стоит после товаров")
                    name = stringToken(line.removePrefix("Название: "), prefix)
                }
                line.startsWith("Магазин: ") -> {
                    val parts = splitQuoted(line.removePrefix("Магазин: "), prefix)
                    ensure(parts.size == 2, "$prefix: после магазина нужен адрес в кавычках")
                    shopName = stringToken(parts[0], prefix)
                    shopAddress = stringToken(parts[1], prefix)
                }
                line.startsWith("- [ ] ") || line.startsWith("- [x] ") || line.startsWith("- [X] ") -> {
                    ensure(name != null, "$prefix: сначала укажите название списка")
                    val bought = line[3].lowercaseChar() == 'x'
                    val body = line.substring(6)
                    val urgent = body.startsWith("! ")
                    val parts = splitQuoted(if (urgent) body.substring(2) else body, prefix)
                    ensure(parts.size == (if (version == 2) 5 else 4), "$prefix: нужны название, количество/единица, цена, заметка" + if (version == 2) " и комментарий" else "")
                    val quantityUnit = Regex("^($NUMBER)\\s+(\".*\")$").matchEntire(parts[1])
                        ?: fail("$prefix: количество и единица должны выглядеть как 2 \"кг\"")
                    val price = if (parts[2] == "—") null else {
                        val match = Regex("^($NUMBER)\\s+₽/ед\\.$").matchEntire(parts[2])
                            ?: fail("$prefix: цена должна выглядеть как 120 ₽/ед. или —")
                        number(match.groupValues[1], prefix, price = true)
                    }
                    items += GoalListItemDraft(stringToken(parts[0], prefix), number(quantityUnit.groupValues[1], prefix, quantity = true),
                        stringToken(quantityUnit.groupValues[2], prefix), price, stringToken(parts[3], prefix), shopName, shopAddress, bought, urgent, if (version == 2) stringToken(parts[4], prefix) else "")
                    ensure(items.size <= MAX_ITEMS, "В списке не может быть более $MAX_ITEMS позиций")
                }
                else -> fail("$prefix: непонятная строка; проверьте формат перед импортом")
            }
        }
        return GoalListDocument(name ?: fail("Не указано название списка"), items)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseJson(text: String, warnings: MutableList<String>): GoalListDocument {
        val root = JsonReader(text).read() as? Map<String, Any?> ?: fail("JSON списка должен быть объектом")
        ensure(root["format"] == JSON_FORMAT, "Это не файл целевого списка «Список рядом»")
        val rawVersion = root["version"] as? BigDecimal
        val version = when { rawVersion?.compareTo(BigDecimal.ONE) == 0 -> 1; rawVersion?.compareTo(BigDecimal(2)) == 0 -> 2; else -> fail("Неизвестная версия JSON списка") }
        unknownKeys(root, setOf("format", "version", "name", "items"), "списка", warnings)
        val name = requiredString(root, "name", "Название списка")
        val rows = root["items"] as? List<*> ?: fail("JSON: items должен быть массивом")
        ensure(rows.size <= MAX_ITEMS, "В списке не может быть более $MAX_ITEMS позиций")
        val items = rows.mapIndexed { index, row ->
            val obj = row as? Map<String, Any?> ?: fail("Позиция ${index + 1}: требуется объект")
            val where = "Позиция ${index + 1}"
            unknownKeys(obj, setOf("name", "quantity", "unit", "unitPrice", "note", "comment", "shopName", "shopAddress", "isBought", "isUrgent"), where, warnings)
            val price = obj["unitPrice"]?.let { numericValue(it, "$where, цена", price = true) }
            val comment = optionalString(obj, "comment", where)
            ensure(version == 2 || comment.isEmpty(), "$where: комментарий требует JSON списка версии 2")
            GoalListItemDraft(requiredString(obj, "name", "$where, название"),
                numericValue(obj["quantity"], "$where, количество", quantity = true), requiredString(obj, "unit", "$where, единица"), price,
                optionalString(obj, "note", where), optionalString(obj, "shopName", where), optionalString(obj, "shopAddress", where),
                optionalBoolean(obj, "isBought", where), optionalBoolean(obj, "isUrgent", where), comment)
        }
        return GoalListDocument(name, items)
    }

    private fun parsePlainText(text: String, warnings: MutableList<String>): GoalListDocument {
        warnings += "Обычный текст: проверьте названия, места, количества и цены в предпросмотре. Неуказанное количество — 1, единица — шт."
        var name = "Импортированный список"
        var hasTitle = false
        var shopName = ""
        var shopAddress = ""
        val items = mutableListOf<GoalListItemDraft>()
        text.lines().forEachIndexed { index, raw ->
            var line = raw.trim()
            if (line.isBlank()) return@forEachIndexed
            val where = "Строка ${index + 1}"
            val titlePrefix = listOf("Список:", "Название:").firstOrNull { line.startsWith(it, ignoreCase = true) }
            when {
                titlePrefix != null || line.startsWith("# ") -> {
                    ensure(!hasTitle && items.isEmpty(), "$where: повторное название или заголовок после товаров — уточните текст")
                    name = if (titlePrefix != null) line.substring(titlePrefix.length).trim() else line.substring(2).trim()
                    hasTitle = true
                    return@forEachIndexed
                }
                line.startsWith("Магазин:", true) || line.endsWith(':') -> {
                    val caption = if (line.startsWith("Магазин:", true)) line.substringAfter(':').trim() else line.dropLast(1).trim()
                    val parts = caption.split(" | ")
                    ensure(parts.size <= 2, "$where: неоднозначное название/адрес магазина")
                    shopName = parts[0].takeUnless { it.equals("Без магазина", true) }.orEmpty()
                    shopAddress = parts.getOrNull(1).orEmpty()
                    return@forEachIndexed
                }
            }
            line = line.replace(Regex("^(?:[-*•–—]\\s+|[0-9]+[.)]\\s+)"), "")
            val bought = line.startsWith("[x]", true) || line.startsWith("☑") || line.startsWith("✅")
            line = line.replace(Regex("^(?:\\[[ xX]\\]|[☐☑✅])\\s*"), "")
            val urgent = line.startsWith("! ")
            if (urgent) line = line.substring(2)
            ensure(line.isNotBlank(), "$where: пустая позиция")
            var quantity = 1.0
            var unit = "шт"
            var price: Double? = null
            var note = ""
            var itemName = line
            val parts = line.split(Regex("\\s+[—–-]\\s+"))
            if (parts.size in 2..3) {
                val amount = Regex("^($NUMBER)(?:\\s+([^₽]+))?$").matchEntire(parts[1])
                val onlyPrice = friendlyPrice(parts[1])
                if (onlyPrice != null) {
                    itemName = parts[0]; price = onlyPrice
                    ensure(parts.size == 2, "$where: после цены непонятный текст; выделите заметку явно")
                } else if (amount != null) {
                    itemName = parts[0]
                    quantity = number(amount.groupValues[1], where, quantity = true)
                    unit = amount.groupValues[2].trim().ifEmpty { "шт" }
                    if (parts.size == 3) {
                        price = friendlyPrice(parts[2])
                        if (price == null) { note = parts[2]; warnings += "$where: последняя часть принята за заметку" }
                    }
                } else {
                    ensure(!Regex("^(?:[-+]?(?:NaN|Infinity)|∞)(?:\\s|$)", RegexOption.IGNORE_CASE).containsMatchIn(parts[1]), "$where: некорректное количество")
                    warnings += "$where: разделители оставлены в названии; количество не определено"
                }
            } else if (parts.size > 3) warnings += "$where: неоднозначные разделители сохранены целиком в названии"
            else if (line.any(Char::isDigit)) warnings += "$where: числа без разделителя оставлены в названии; количество — 1"
            items += GoalListItemDraft(itemName, quantity, unit, price, note, shopName, shopAddress, bought, urgent)
            ensure(items.size <= MAX_ITEMS, "В списке не может быть более $MAX_ITEMS позиций")
        }
        ensure(items.isNotEmpty(), "В тексте не найдено ни одной позиции")
        if (!hasTitle) warnings += "Название списка не указано; использовано «Импортированный список»"
        return GoalListDocument(name, items)
    }

    private fun friendlyPrice(text: String): Double? = Regex("^($NUMBER)\\s*(?:₽|руб\\.?)(?:\\s*/\\s*(?:ед\\.?|шт\\.?))?$", RegexOption.IGNORE_CASE)
        .matchEntire(text.trim())?.let { number(it.groupValues[1], "Цена", price = true) }
    private fun decimal(value: Double) = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
    private fun number(value: String, where: String, quantity: Boolean = false, price: Boolean = false): Double {
        val decimal = value.replace(',', '.').toBigDecimalOrNull() ?: fail("$where: некорректное число")
        return numericValue(decimal, where, quantity, price)
    }
    private fun numericValue(value: Any?, label: String, quantity: Boolean = false, price: Boolean = false): Double {
        val decimal = value as? BigDecimal ?: fail("$label: требуется конечное число")
        // Validate the input decimal before converting to Double: otherwise a value such as
        // 1.00000000000000001 silently becomes 1 and bypasses the six-decimal contract.
        if (quantity) {
            ensure(decimal >= BigDecimal("0.000001") && decimal <= BigDecimal("1000000000"), "$label: количество должно быть от 0,000001 до 1 000 000 000")
            ensure(decimal.stripTrailingZeros().scale() <= 6, "$label: количество поддерживает не более 6 знаков после запятой")
        }
        if (price) ensure(decimal >= BigDecimal.ZERO && decimal <= BigDecimal("1000000000000"), "$label: цена за единицу должна быть от 0 до 1 000 000 000 000")
        val number = decimal.toDouble()
        ensure(number.isFinite() && (number != 0.0 || decimal.signum() == 0), "$label: число выходит за допустимый диапазон")
        return number
    }
    private fun requiredString(obj: Map<String, Any?>, key: String, label: String) = obj[key] as? String ?: fail("$label: требуется строка")
    private fun optionalString(obj: Map<String, Any?>, key: String, label: String): String = if (key !in obj) "" else obj[key] as? String ?: fail("$label, $key: требуется строка")
    private fun optionalBoolean(obj: Map<String, Any?>, key: String, label: String): Boolean = if (key !in obj) false else obj[key] as? Boolean ?: fail("$label, $key: требуется true или false")
    private fun unknownKeys(obj: Map<String, Any?>, allowed: Set<String>, where: String, warnings: MutableList<String>) {
        val unknown = obj.keys - allowed
        if (unknown.isNotEmpty()) warnings += "$where: неизвестные поля ${unknown.joinToString().take(200)} не будут импортированы"
    }
    private fun requireValid(document: GoalListDocument) { val errors = validate(document); require(errors.isEmpty()) { errors.joinToString("\n") } }
    private fun failed(message: String) = GoalListTransferPreview(null, errors = listOf(message))
    private class TransferFailure(message: String) : IllegalArgumentException(message)
    private fun fail(message: String): Nothing = throw TransferFailure(message)
    private fun ensure(condition: Boolean, message: String) { if (!condition) fail(message) }
    private fun stringToken(token: String, where: String): String = JsonReader(token.trim()).read() as? String ?: fail("$where: текст должен быть в кавычках")
    private fun splitQuoted(value: String, where: String): List<String> {
        val result = mutableListOf<String>(); var quoted = false; var escaped = false; var start = 0
        value.forEachIndexed { index, char ->
            when {
                escaped -> escaped = false
                quoted && char == '\\' -> escaped = true
                char == '"' -> quoted = !quoted
                char == '|' && !quoted -> { result += value.substring(start, index).trim(); start = index + 1 }
            }
        }
        ensure(!quoted && !escaped, "$where: не закрыты кавычки")
        result += value.substring(start).trim()
        return result
    }
    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { char -> when (char) {
            '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
            else -> if (char.code < 32) append("\\u${char.code.toString(16).padStart(4, '0')}") else append(char)
        } }
        append('"')
    }
    private fun validUnicode(value: String): Boolean {
        var index = 0
        while (index < value.length) {
            val char = value[index++]
            if (Character.isHighSurrogate(char)) {
                if (index >= value.length || !Character.isLowSurrogate(value[index++])) return false
            } else if (Character.isLowSurrogate(char)) return false
        }
        return true
    }

    /** Strict bounded JSON: rejects duplicate keys, trailing data and permissive JS syntax. */
    private class JsonReader(private val text: String) {
        private var offset = 0
        fun read(): Any? { val value = value(0); spaces(); ensure(offset == text.length, "JSON: лишние данные после значения"); return value }
        private fun spaces() { while (offset < text.length && text[offset] in " \t\r\n") offset++ }
        private fun value(depth: Int): Any? {
            ensure(depth < 16, "JSON: слишком глубокая вложенность")
            spaces(); ensure(offset < text.length, "JSON: неожиданное окончание")
            return when (text[offset]) {
                '"' -> string()
                '{' -> obj(depth + 1)
                '[' -> array(depth + 1)
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                '-', in '0'..'9' -> numeric()
                else -> fail("JSON: недопустимое значение около символа ${offset + 1}")
            }
        }
        private fun literal(token: String, result: Any?): Any? { ensure(text.startsWith(token, offset), "JSON: неверное значение"); offset += token.length; return result }
        private fun obj(depth: Int): Map<String, Any?> {
            offset++; spaces(); val result = linkedMapOf<String, Any?>()
            if (consume('}')) return result
            while (true) {
                spaces(); ensure(offset < text.length && text[offset] == '"', "JSON: ключ должен быть в кавычках")
                val key = string(); ensure(key !in result, "JSON: поле «${key.take(80)}» указано дважды")
                spaces(); expect(':'); result[key] = value(depth); spaces()
                if (consume('}')) return result
                expect(',')
            }
        }
        private fun array(depth: Int): List<Any?> {
            offset++; spaces(); val result = mutableListOf<Any?>()
            if (consume(']')) return result
            while (true) {
                result += value(depth)
                ensure(result.size <= MAX_ITEMS, "JSON: массив содержит более $MAX_ITEMS элементов")
                spaces(); if (consume(']')) return result
                expect(',')
            }
        }
        private fun numeric(): BigDecimal {
            val start = offset
            while (offset < text.length && text[offset] in "-+0123456789.eE") offset++
            val token = text.substring(start, offset)
            ensure(token.length <= 128 && Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?").matches(token), "JSON: некорректное число")
            return token.toBigDecimalOrNull() ?: fail("JSON: некорректное число")
        }
        private fun string(): String {
            expect('"'); val result = StringBuilder()
            while (offset < text.length) {
                val char = text[offset++]
                if (char == '"') { val value = result.toString(); ensure(validUnicode(value), "JSON: некорректный Unicode"); return value }
                ensure(char.code >= 32, "JSON: перевод строки в строке должен быть записан как \\n")
                if (char != '\\') result.append(char) else {
                    ensure(offset < text.length, "JSON: незавершённая escape-последовательность")
                    when (val escaped = text[offset++]) {
                        '"', '\\', '/' -> result.append(escaped)
                        'b' -> result.append('\b'); 'f' -> result.append('\u000C'); 'n' -> result.append('\n'); 'r' -> result.append('\r'); 't' -> result.append('\t')
                        'u' -> {
                            ensure(offset + 4 <= text.length, "JSON: неполный Unicode-код")
                            val digits = text.substring(offset, offset + 4)
                            ensure(digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }, "JSON: неверный Unicode-код")
                            val code = digits.toInt(16)
                            result.append(code.toChar()); offset += 4
                        }
                        else -> fail("JSON: неизвестная escape-последовательность")
                    }
                }
            }
            fail("JSON: не закрыты кавычки")
        }
        private fun expect(char: Char) { spaces(); ensure(consume(char), "JSON: ожидался символ $char около ${offset + 1}") }
        private fun consume(char: Char): Boolean = if (offset < text.length && text[offset] == char) { offset++; true } else false
    }
}
