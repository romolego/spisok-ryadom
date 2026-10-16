package com.spisokryadom.app.util
import kotlin.math.round
object Quantities {
    fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
        ?.takeIf { it.isFinite() && it >= 0.000001 && it <= 1_000_000_000.0 }
    fun valid(value: Double): Double {
        require(value.isFinite() && value >= 0.000001 && value <= 1_000_000_000.0) { "Укажите положительное количество" }
        return round(value * 1_000_000.0) / 1_000_000.0
    }
    fun unit(text: String) = FuzzySearch.normalize(text).let { when (it) {
        "штук", "штука", "штуки", "шт." -> "шт"
        "килограмм", "килограмма", "килограммов", "кг." -> "кг"
        "литр", "литра", "литров", "л." -> "л"
        "г.", "грамм", "грамма", "граммов", "гр", "гр." -> "г"
        else -> it
    } }
}
