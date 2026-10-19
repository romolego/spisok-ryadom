package com.spisokryadom.app.widget

import androidx.room.withTransaction
import com.spisokryadom.app.AppContainer
import com.spisokryadom.app.domain.PlaceLabels
import java.math.BigDecimal

data class WidgetPurchase(val id:Long,val productStableId:String,val createdAt:Long,val updatedAt:Long,
    val bought:Boolean,val title:String,val detail:String,val urgent:Boolean,val revision:String="")

/** Read directly from Room on the service's binder worker, including after process death. */
class ShoppingWidgetData(private val db:com.spisokryadom.app.data.db.AppDatabase,private val basket:com.spisokryadom.app.data.repository.ShoppingListRepository) {
    private fun revision(entry:com.spisokryadom.app.data.entity.ShoppingListEntryEntity)=java.security.MessageDigest.getInstance("SHA-256").digest(entry.toString().toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
    constructor(container:AppContainer):this(container.database,container.shoppingListRepository)
    suspend fun rows():List<WidgetPurchase> = db.withTransaction {
        val products=db.productDao().getAllSync().associateBy{it.id}
        val shops=db.shopDao().getAllSync().associateBy{it.id}
        val branches=db.shopBranchDao().getAll().associateBy{it.id}
        val labels=PlaceLabels(branches.values.toList(),db.retailStructureDao().venues())
        db.shoppingListEntryDao().getAllSync().sortedWith(compareBy<com.spisokryadom.app.data.entity.ShoppingListEntryEntity>{it.isBought}.thenByDescending{it.isUrgent}.thenBy{it.createdAt}.thenBy{it.id}).mapNotNull{entry->
            val p=products[entry.productId]?:return@mapNotNull null
            val quantity=BigDecimal.valueOf(entry.quantity).stripTrailingZeros().toPlainString().replace('.',',')
            val place=shops[entry.assignedShopId]?.let{labels.place(it.name,branches[entry.assignedBranchId]).text}
            WidgetPurchase(entry.id,p.stableId,entry.createdAt,entry.updatedAt,entry.isBought,p.name,
                listOfNotNull((quantity+" "+entry.unit).trim(),place).joinToString(" · "),entry.isUrgent,revision(entry))
        }
    }
    /** Idempotent compare-and-set: repeated taps and stale widgets never undo newer changes. */
    suspend fun check(row:WidgetPurchase):Boolean = db.withTransaction {
        val entry=db.shoppingListEntryDao().getById(row.id)?:return@withTransaction false
        val product=db.productDao().getById(entry.productId)?:return@withTransaction false
        if(product.stableId!=row.productStableId||entry.createdAt!=row.createdAt||entry.updatedAt!=row.updatedAt||entry.isBought!=row.bought||revision(entry)!=row.revision)return@withTransaction false
        if(row.bought)basket.markNotBought(row.id)else basket.markBought(row.id)
        true
    }
}
