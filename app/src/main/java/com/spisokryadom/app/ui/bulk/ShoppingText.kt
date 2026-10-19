package com.spisokryadom.app.ui.bulk

import com.spisokryadom.app.data.repository.ShoppingListRepository

fun renderShoppingText(s:ShoppingListRepository.TextSnapshot):String {
        val products=s.products.associateBy{it.id};val shops=s.shops.associateBy{it.id};val branches=s.branches.associateBy{it.id}
        val listNames=s.goalLists.associate{it.id to it.name}
        val labels=com.spisokryadom.app.domain.PlaceLabels(s.branches,s.venues)
        return s.entries.groupBy{listOf(it.assignedShopId,it.assignedBranchId,it.customShopName,it.customShopAddress)}.entries.joinToString("\n\n"){(target,rows)->
            val title=shops[target[0] as? Long]?.let{labels.place(it.name,branches[target[1] as? Long]).text}?:listOfNotNull((target[2] as? String),(target[3] as? String)).distinct().joinToString(" · ").ifBlank{"Без магазина"}
            title+":\n"+rows.joinToString("\n"){row->(products[row.productId]?.name?:"Товар").replace(Regex("\\s+")," ")+" — "+java.math.BigDecimal.valueOf(row.quantity).stripTrailingZeros().toPlainString().replace('.',',')+row.unit.takeIf{it.isNotBlank()}?.let{" $it"}.orEmpty()+row.listId?.let{listNames[it]?.let{name->" [${name.replace(Regex("\\s+")," ")}]"}}.orEmpty()}
        }
    }
