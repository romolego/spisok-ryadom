package com.spisokryadom.app.util

data class AppendOrder(val next:Int,val ranks:Map<Int,Int> = emptyMap())

/** Compact equal positions to equal ranks, preserving every existing ordering tie-break. */
fun appendOrder(orders:List<Int>,reserve:Int=1):AppendOrder {
    require(reserve>0)
    val maximum=orders.maxOrNull()?:-1
    if(maximum.toLong()+reserve<=Int.MAX_VALUE)return AppendOrder(maximum+1)
    val ranks=orders.distinct().sorted().mapIndexed{index,order->order to index}.toMap()
    return AppendOrder(ranks.size,ranks)
}
