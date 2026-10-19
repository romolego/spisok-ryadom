package com.spisokryadom.app

import com.spisokryadom.app.util.PurchaseMoney
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class Audit151MoneyTest {
    @Test fun halfKopeckIsRoundedUpConsistently() {
        assertEquals("0,13 ₽", PurchaseMoney.format(0.125))
        assertEquals("0,13 ₽", PurchaseMoney.format(PurchaseMoney.amount(0.1, 1.25)))
        assertEquals("0,13 ₽", PurchaseMoney.format(PurchaseMoney.total(0.1, 1.25)))
    }

    @Test fun decimalMultiplicationDoesNotRoundBinaryArtifactDown() {
        assertEquals(BigDecimal("1.005"), PurchaseMoney.amount(0.1, 10.05))
        assertEquals("1,01 ₽", PurchaseMoney.format(PurchaseMoney.total(0.1, 10.05)))
        assertEquals("0 ₽", PurchaseMoney.format(0.0))
    }

    @Test fun sumIsRoundedOnlyAfterAddingUnroundedAmounts() {
        val total = PurchaseMoney.amount(0.125, 1.0).multiply(BigDecimal("2"))
        assertEquals("0,25 ₽", PurchaseMoney.format(total))
    }

    @Test fun smallPendingBudgetIsNotLostBesideLargeBoughtTotal() {
        val product = com.spisokryadom.app.data.entity.ProductEntity(id = 1, name = "План")
        val state = com.spisokryadom.app.ui.goallists.GoalListUiState(rows = listOf(
            com.spisokryadom.app.ui.goallists.GoalPurchaseRow(
                com.spisokryadom.app.data.entity.ShoppingListEntryEntity(productId = 1, quantity = 1_000_000_000.0,
                    plannedUnitPrice = 1_000_000_000_000.0, isBought = true), product, null),
            com.spisokryadom.app.ui.goallists.GoalPurchaseRow(
                com.spisokryadom.app.data.entity.ShoppingListEntryEntity(productId = 1, quantity = 1.25,
                    plannedUnitPrice = 0.1), product, null)
        ))
        assertEquals(0.125, state.remainingTotal, 0.0)
        assertEquals("0,13 ₽", com.spisokryadom.app.ui.goallists.goalMoney(state.remainingTotal))
    }
}
