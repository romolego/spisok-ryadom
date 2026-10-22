package com.spisokryadom.app.data.repository

import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.ProductShopLinkEntity

/** Called inside the relationship transaction. Imported priorities can reach Int.MAX_VALUE. */
internal suspend fun nextProductShopPriority(db:AppDatabase,productId:Long):Int {
    val dao=db.productShopLinkDao()
    val links=dao.getLinksForProductSync(productId)
    val max=links.maxOfOrNull{it.priority}?:0
    if(max<Int.MAX_VALUE)return max+1
    val shops=db.shopDao().getAllSync().associateBy{it.id}
    // This is the same tie-break as an explicit product route. It also retains
    // relative priorities within every equal city-route position for inheritance.
    val ordered=links.sortedWith(compareBy<ProductShopLinkEntity>{it.priority}
        .thenBy{shops[it.shopId]?.displayOrder?:Int.MAX_VALUE}.thenBy{it.shopId})
    ordered.forEachIndexed{index,link->dao.update(link.copy(priority=index+1))}
    return ordered.size+1
}
