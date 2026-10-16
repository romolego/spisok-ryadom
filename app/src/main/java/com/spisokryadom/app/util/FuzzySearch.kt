package com.spisokryadom.app.util
import kotlin.math.min
import java.text.Normalizer

/** Shared deterministic ranking. Lower scores are better; exact matches always win. */
object FuzzySearch {
    fun normalize(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFKC)
        .lowercase(java.util.Locale.ROOT).replace('ё', 'е').trim().replace(Regex("\\s+"), " ")
    fun score(candidate: String, query: String): Double {
        val c = normalize(candidate); val q = normalize(query)
        if (q.isEmpty() || c == q) return 0.0
        if (c.startsWith(q)) return 0.02
        if (c.split(' ', '-', '/').any { it.startsWith(q) }) return 0.04
        if (c.contains(q)) return 0.06
        if (q.length < 3) return 1.0
        val words = c.split(' ', '-', '/').filter { it.isNotEmpty() }
        val distance = minOf(distance(c, q).toDouble() / maxOf(c.length, q.length),
            words.minOfOrNull { distance(it, q).toDouble() / maxOf(it.length, q.length) } ?: 1.0)
        return 0.15 + distance * 0.8
    }
    fun filterAndSort(items: List<String>, query: String, threshold: Double = 0.50): List<String> =
        rank(items, query, { it }, threshold)
    fun <T> rank(items: List<T>, query: String, name: (T) -> String, threshold: Double = 0.50): List<T> {
        if (query.isBlank()) return items
        return items.asSequence().map { it to score(name(it), query) }
            .filter { it.second < threshold }.sortedWith(compareBy<Pair<T, Double>> { it.second }
                .thenBy { normalize(name(it.first)) }).map { it.first }.toList()
    }
    private fun distance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }; var curr = IntArray(b.length + 1)
        for (i in a.indices) {
            curr[0] = i + 1
            for (j in b.indices) curr[j + 1] = min(min(curr[j] + 1, prev[j + 1] + 1), prev[j] + if (a[i] == b[j]) 0 else 1)
            val swap = prev; prev = curr; curr = swap
        }
        return prev[b.length]
    }
}
