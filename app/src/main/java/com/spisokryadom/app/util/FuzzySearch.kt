package com.spisokryadom.app.util

import java.text.Normalizer
import java.util.Locale
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

/** Deterministic local ranking. Lower scores are better; exact matches always win. */
object FuzzySearch {
    private val whitespace = Regex("\\s+")
    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace('ё', 'е').trim().replace(whitespace, " ")

    private data class Indexed<T>(val value: T, val name: String, val words: List<String>, val ordinal: Int)
    private data class Ranked<T>(val item: Indexed<T>, val score: Double)

    /** Reuse this immutable snapshot when matching several lines against the same catalogue. */
    class Index<T> internal constructor(items: List<T>, name: (T) -> String) {
        private val entries = items.mapIndexed { ordinal, item ->
            val normalized = normalize(name(item))
            Indexed(item, normalized, words(normalized), ordinal)
        }

        fun search(query: String, threshold: Double = 0.50, limit: Int = Int.MAX_VALUE, checkCancelled: () -> Unit = {}): List<T> {
            require(limit >= 0)
            checkCancelled()
            if (limit == 0) return emptyList()
            val normalizedQuery = normalize(query)
            if (normalizedQuery.isEmpty()) return entries.take(limit).map { it.value }
            val order = compareBy<Ranked<T>> { it.score }.thenBy { it.item.name }.thenBy { it.item.ordinal }
            val all = if (limit == Int.MAX_VALUE) ArrayList<Ranked<T>>() else null
            val best = if (all == null) PriorityQueue<Ranked<T>>(limit.coerceAtMost(entries.size).coerceAtLeast(1), order.reversed()) else null
            for (entry in entries) {
                checkCancelled()
                val score = scoreNormalized(entry.name, entry.words, normalizedQuery, threshold, checkCancelled)
                if (score >= threshold) continue
                val ranked = Ranked(entry, score)
                if (all != null) all.add(ranked)
                else if (best!!.size < limit) best.add(ranked)
                else if (order.compare(ranked, best.peek()) < 0) { best.poll(); best.add(ranked) }
            }
            checkCancelled()
            return (all ?: best!!.toList()).sortedWith(order).map { it.item.value }
        }
    }

    fun <T> index(items: List<T>, name: (T) -> String): Index<T> = Index(items, name)
    fun score(candidate: String, query: String): Double {
        val normalized = normalize(candidate)
        return scoreNormalized(normalized, words(normalized), normalize(query), Double.POSITIVE_INFINITY) {}
    }
    fun filterAndSort(items: List<String>, query: String, threshold: Double = 0.50): List<String> = rank(items, query, { it }, threshold)
    fun <T> rank(items: List<T>, query: String, name: (T) -> String, threshold: Double = 0.50): List<T> =
        if (query.isBlank()) items else index(items, name).search(query, threshold)

    private fun words(value: String) = value.split(' ', '-', '/').filter { it.isNotEmpty() }
    private fun scoreNormalized(candidate: String, words: List<String>, query: String, threshold: Double, checkCancelled: () -> Unit): Double {
        if (query.isEmpty() || candidate == query) return 0.0
        if (candidate.startsWith(query)) return 0.02
        if (words.any { it.startsWith(query) }) return 0.04
        if (candidate.contains(query)) return 0.06
        if (query.length < 3) return 1.0
        val allowedRatio = (threshold - 0.15) / 0.8
        if (allowedRatio <= 0) return Double.POSITIVE_INFINITY
        var best = Double.POSITIVE_INFINITY
        fun compare(value: String) {
            val length = maxOf(value.length, query.length)
            val maximum = if (allowedRatio >= 1.0) length else floor(allowedRatio * length).toInt()
            if (abs(value.length - query.length) > maximum) return
            val distance = boundedDistance(value, query, maximum, checkCancelled)
            if (distance <= maximum) best = minOf(best, distance.toDouble() / length)
        }
        compare(candidate)
        for (word in words) if (word != candidate) compare(word)
        return 0.15 + best * 0.8
    }

    /** Returns maximum+1 when the distance cannot pass the caller's score threshold. */
    private fun boundedDistance(a: String, b: String, maximum: Int, checkCancelled: () -> Unit): Int {
        if (abs(a.length - b.length) > maximum) return maximum + 1
        val rows = if (a.length >= b.length) a else b
        val columns = if (a.length >= b.length) b else a
        val infinity = maximum + 1
        var previous = IntArray(columns.length + 1) { if (it <= maximum) it else infinity }
        var current = IntArray(columns.length + 1) { infinity }
        for (i in 1..rows.length) {
            if (i % 64 == 0) checkCancelled()
            current[0] = if (i <= maximum) i else infinity
            val start = maxOf(1, i - maximum)
            val end = minOf(columns.length, i + maximum)
            if (start > 1) current[start - 1] = infinity
            var rowMinimum = current[0]
            for (j in start..end) {
                current[j] = min(min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + if (rows[i - 1] == columns[j - 1]) 0 else 1)
                rowMinimum = minOf(rowMinimum, current[j])
            }
            if (end < columns.length) current[end + 1] = infinity
            if (rowMinimum > maximum) return infinity
            val swap = previous; previous = current; current = swap
        }
        return previous[columns.length]
    }
}
