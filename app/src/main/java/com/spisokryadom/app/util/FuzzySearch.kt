package com.spisokryadom.app.util

import kotlin.math.min

/**
 * Единый механизм приблизительного поиска по похожим названиям.
 * Используется во всех местах приложения, где нужны подсказки или фильтрация по названиям:
 * — группы магазина, единицы измерения, товары и т.д.
 */
object FuzzySearch {

    /**
     * Фильтрует и сортирует [items] по степени приблизительного совпадения с [query].
     * Возвращает список, отсортированный от лучшего совпадения к худшему.
     *
     * @param items список строк-кандидатов
     * @param query пользовательский ввод
     * @param threshold максимальное нормализованное расстояние (0.0–1.0).
     *                  По умолчанию 0.45 — достаточно для опечаток и близких названий.
     */
    fun filterAndSort(
        items: List<String>,
        query: String,
        threshold: Double = 0.45
    ): List<String> {
        if (query.isBlank()) return items

        val normalizedQuery = normalize(query)
        if (normalizedQuery.isEmpty()) return items

        return items
            .map { item -> item to matchScore(normalize(item), normalizedQuery) }
            .filter { (_, score) -> score < threshold }
            .sortedBy { (_, score) -> score }
            .map { (item, _) -> item }
    }

    /**
     * Вычисляет оценку совпадения (меньше = лучше, 0.0 = идеальное совпадение).
     * Комбинирует несколько стратегий:
     * 1. Точное вхождение подстроки → 0.0
     * 2. Совпадение по префиксу слов → 0.05
     * 3. Нормализованное расстояние Левенштейна
     */
    private fun matchScore(candidate: String, query: String): Double {
        // Точное вхождение подстроки
        if (candidate.contains(query)) return 0.0

        // Совпадение по началу слов кандидата
        val candidateWords = candidate.split(" ", "-", "/")
        if (candidateWords.any { it.startsWith(query) }) return 0.05

        // Совпадение по началу запроса
        if (query.startsWith(candidate)) return 0.05

        // Расстояние Левенштейна, нормализованное по длине большей строки
        val maxLen = maxOf(candidate.length, query.length)
        if (maxLen == 0) return 0.0

        // Для коротких запросов: проверяем расстояние до каждого слова кандидата
        val wordDistances = candidateWords.map { word ->
            val wordMaxLen = maxOf(word.length, query.length)
            if (wordMaxLen == 0) 0.0
            else levenshtein(word, query).toDouble() / wordMaxLen
        }
        val bestWordDistance = wordDistances.minOrNull() ?: 1.0

        // Расстояние до полной строки
        val fullDistance = levenshtein(candidate, query).toDouble() / maxLen

        return minOf(bestWordDistance, fullDistance)
    }

    /**
     * Расстояние Левенштейна — количество операций вставки, удаления и замены,
     * необходимых для преобразования одной строки в другую.
     */
    private fun levenshtein(a: String, b: String): Int {
        val m = a.length
        val n = b.length

        if (m == 0) return n
        if (n == 0) return m

        // Используем одномерный массив для экономии памяти
        var prev = IntArray(n + 1) { it }
        var curr = IntArray(n + 1)

        for (i in 1..m) {
            curr[0] = i
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = min(
                    min(curr[j - 1] + 1, prev[j] + 1),
                    prev[j - 1] + cost
                )
            }
            val temp = prev
            prev = curr
            curr = temp
        }
        return prev[n]
    }

    private fun normalize(s: String): String = s.trim().lowercase()
}
