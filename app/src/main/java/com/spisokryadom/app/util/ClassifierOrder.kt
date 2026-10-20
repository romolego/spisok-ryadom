package com.spisokryadom.app.util

/** Dictionaries have a stable full alphabet; pin order never changes that alphabet or local routes. */
fun <T> alphabetical(values:List<T>,name:(T)->String):List<T> = values.sortedWith(
    compareBy<T>{FuzzySearch.normalize(name(it))}.thenBy{name(it)}
)

data class FavoriteAppendOrder(val next:Int,val compact:Boolean)

/** Imported order values may span the full Int range. Preserve order without overflowing. */
fun nextFavoriteOrder(orders:List<Int>):FavoriteAppendOrder {
    val max=orders.maxOrNull()?:-1
    return if(max==Int.MAX_VALUE)FavoriteAppendOrder(orders.size,true)else FavoriteAppendOrder(max+1,false)
}
