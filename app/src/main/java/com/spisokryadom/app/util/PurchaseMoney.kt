package com.spisokryadom.app.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** The same decimal arithmetic and kopeck rounding in the plan and the basket. */
object PurchaseMoney {
    fun amount(unitPrice: Double, quantity: Double): BigDecimal =
        BigDecimal.valueOf(unitPrice).multiply(BigDecimal.valueOf(quantity))

    fun total(unitPrice: Double, quantity: Double): Double = amount(unitPrice, quantity).toDouble()

    fun format(value: Double): String = format(BigDecimal.valueOf(value))

    fun format(value: BigDecimal): String = DecimalFormat(
        "#,##0.##", DecimalFormatSymbols(Locale.forLanguageTag("ru"))
    ).apply { roundingMode = RoundingMode.HALF_UP }.format(value.setScale(2, RoundingMode.HALF_UP)) + " ₽"
}
