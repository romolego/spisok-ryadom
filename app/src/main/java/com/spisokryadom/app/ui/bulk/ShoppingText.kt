package com.spisokryadom.app.ui.bulk

import com.spisokryadom.app.data.repository.ShoppingListRepository

fun renderShoppingText(s:ShoppingListRepository.TextSnapshot):String {
        val products=s.products.associateBy{it.id};val shops=s.shops.associateBy{it.id};val branches=s.branches.associateBy{it.id}
        val labels=com.spisokryadom.app.domain.PlaceLabels(s.branches,s.venues)
        return s.entries.groupBy{it.assignedShopId to it.assignedBranchId}.entries.joinToString("\n\n"){(target,rows)->
            val title=shops[target.first]?.let{labels.place(it.name,branches[target.second]).text}?:"Без магазина"
            title+":\n"+rows.joinToString("\n"){row->(products[row.productId]?.name?:"Товар").replace(Regex("\\s+")," ")+" — "+java.math.BigDecimal.valueOf(row.quantity).stripTrailingZeros().toPlainString().replace('.',',')+row.unit.takeIf{it.isNotBlank()}?.let{" $it"}.orEmpty()}
        }
    }
