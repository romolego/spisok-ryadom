package com.spisokryadom.app.data.repository

import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.util.appendOrder

/** Caller holds the containing write transaction. Existing equal positions stay equal. */
internal suspend fun nextShopDisplayOrder(db:AppDatabase,cityId:Long,reserve:Int=1):Int {
    val rows=db.shopDao().getAllSync().filter{it.cityId==cityId}
    val order=appendOrder(rows.map{it.displayOrder},reserve)
    if(order.ranks.isNotEmpty())db.shopDao().updateAll(rows.map{it.copy(displayOrder=order.ranks.getValue(it.displayOrder))})
    return order.next
}

internal suspend fun nextDepartmentDisplayOrder(db:AppDatabase,shopId:Long):Int {
    val dao=db.shopDepartmentDao();val rows=dao.getByShopIdSync(shopId)
    val order=appendOrder(rows.map{it.displayOrder})
    if(order.ranks.isNotEmpty())dao.updateAll(rows.map{it.copy(displayOrder=order.ranks.getValue(it.displayOrder))})
    return order.next
}
