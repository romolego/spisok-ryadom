package com.spisokryadom.app.widget

import androidx.room.withTransaction
import com.spisokryadom.app.AppContainer
import com.spisokryadom.app.domain.PlaceLabels
import java.math.BigDecimal

data class WidgetPurchase(val id:Long,val productStableId:String,val createdAt:Long,val updatedAt:Long,
    val bought:Boolean,val title:String,val detail:String,val urgent:Boolean,val revision:String="")

/** Read directly from Room on the service's binder worker, including after process death. */
class ShoppingWidgetData(private val db:com.spisokryadom.app.data.db.AppDatabase,private val basket:com.spisokryadom.app.data.repository.ShoppingListRepository,
    private val locations:com.spisokryadom.app.data.repository.LocationRepository?=null) {
    private data class PlaceKey(val shopId:Long?,val branchId:Long?,val name:String?=null,val address:String?=null) {
        val isHint: Boolean get() = name != null || address != null
        // Length-prefixed captions cannot collide when a name/address contains delimiters.
        val stableKey: String get() = if(!isHint)"place:$shopId:$branchId" else "hint:"+
            java.security.MessageDigest.getInstance("SHA-256").digest(
                ("${name.orEmpty().length}:${name.orEmpty()}${address.orEmpty().length}:${address.orEmpty()}").toByteArray(Charsets.UTF_8)
            ).joinToString(""){"%02x".format(it)}
    }
    private fun placeKey(entry:com.spisokryadom.app.data.entity.ShoppingListEntryEntity):PlaceKey = PlaceKey(
        entry.assignedShopId,entry.assignedBranchId,
        entry.customShopName?.trim()?.takeIf{entry.assignedShopId==null&&it.isNotEmpty()},
        entry.customShopAddress?.trim()?.takeIf{entry.assignedShopId==null&&it.isNotEmpty()})
    private fun hintLabel(key:PlaceKey):com.spisokryadom.app.domain.PlaceLabel? = if(!key.isHint)null else
        com.spisokryadom.app.domain.physicalPlaceLabel(key.name?:key.address.orEmpty(),key.address.takeIf{key.name!=null},null)
    private fun revision(entry:com.spisokryadom.app.data.entity.ShoppingListEntryEntity)=java.security.MessageDigest.getInstance("SHA-256").digest(entry.toString().toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
    constructor(container:AppContainer):this(container.database,container.shoppingListRepository,container.locations)
    suspend fun rows():List<WidgetPurchase> = db.withTransaction {
        val products=db.productDao().getAllSync().associateBy{it.id}
        val shops=db.shopDao().getAllSync().associateBy{it.id}
        val branches=db.shopBranchDao().getAll().associateBy{it.id}
        val labels=PlaceLabels(branches.values.toList(),db.retailStructureDao().venues())
        db.shoppingListEntryDao().getVisibleSync().sortedWith(compareBy<com.spisokryadom.app.data.entity.ShoppingListEntryEntity>{it.isBought}.thenByDescending{it.isUrgent}.thenBy{it.createdAt}.thenBy{it.id}).mapNotNull{entry->
            val p=products[entry.productId]?:return@mapNotNull null
            val quantity=BigDecimal.valueOf(entry.quantity).stripTrailingZeros().toPlainString().replace('.',',')
            val place=shops[entry.assignedShopId]?.let{labels.place(it.name,branches[entry.assignedBranchId]).text}?:hintLabel(placeKey(entry))?.text
            WidgetPurchase(entry.id,p.stableId,entry.createdAt,entry.updatedAt,entry.isBought,p.name,
                listOfNotNull((quantity+" "+entry.unit).trim(),place).joinToString(" · "),entry.isUrgent,revision(entry))
        }
    }
    /** One coherent Room snapshot; presentation does not depend on collapsed app sections. */
    suspend fun presentation(settings:WidgetSettings,boostUrgentShops:Boolean=true):List<WidgetItem> = db.withTransaction {
        val entries=db.shoppingListEntryDao().getAllSync().associateBy{it.id}
        val products=db.productDao().getAllSync().associateBy{it.id}
        val shops=db.shopDao().getAllSync().associateBy{it.id}
        val branches=db.shopBranchDao().getAll().associateBy{it.id}
        val departments=db.shopDepartmentDao().getAllSync().associateBy{it.id}
        val orders=db.retailStructureDao().departmentOrder().associateBy{it.branchId to it.typeId}
        val venues=db.retailStructureDao().venues().associateBy{it.id}
        val cities=db.cityDao().getAllSync().associateBy{it.id}
        val cityId=locations?.currentId()?:cities.keys.firstOrNull()
        val purchases=rows().map{row->
            val entry=entries.getValue(row.id)
            WidgetItem.Purchase(row.copy(detail=(BigDecimal.valueOf(entry.quantity).stripTrailingZeros().toPlainString().replace('.',',')+" "+entry.unit).trim()))
        }
        val result=mutableListOf<WidgetItem>()
        fun product(row:WidgetItem.Purchase)=products[entries.getValue(row.row.id).productId]
        fun entry(row:WidgetItem.Purchase)=entries.getValue(row.row.id)
        fun sorted(values:List<WidgetItem.Purchase>)=values.sortedWith(compareBy<WidgetItem.Purchase>{it.row.bought}
            .thenByDescending{it.row.urgent&&!it.row.bought}.thenBy{branches[entry(it).assignedBranchId]?.displayOrder?:0}
            .thenBy{it.row.title.lowercase()}.thenBy{it.row.id})
        when(settings.mode){
            com.spisokryadom.app.ui.shoppinglist.ViewMode.FLAT -> result.addAll(sorted(purchases))
            com.spisokryadom.app.ui.shoppinglist.ViewMode.BY_GROUPS,
            com.spisokryadom.app.ui.shoppinglist.ViewMode.BY_RECIPIENTS -> {
                val byGroups=settings.mode==com.spisokryadom.app.ui.shoppinglist.ViewMode.BY_GROUPS
                val groups=if(byGroups)db.productGroupDao().getAllSync().map{Triple(it.id,it.name,it.displayOrder)}
                    else db.recipientDao().getAllSync().map{Triple(it.id,it.name,it.displayOrder)}
                val classifiers=groups.associateBy{it.first}
                val grouped=purchases.groupBy{if(byGroups)product(it)?.productGroupId else product(it)?.recipientId}
                grouped.keys.sortedWith(compareBy<Long?>{it==null}.thenBy{classifiers[it]?.third?:Int.MAX_VALUE}.thenBy{it}).forEach{id->
                    result.add(WidgetItem.Header("${settings.mode}:$id",classifiers[id]?.second?:if(byGroups)"Без группы"else "Без получателя"))
                    result.addAll(sorted(grouped.getValue(id)))
                }
            }
            com.spisokryadom.app.ui.shoppinglist.ViewMode.BY_SHOPS -> {
                val places=purchases.groupBy{placeKey(entry(it))}.entries.sortedWith(
                    compareBy<Map.Entry<PlaceKey,List<WidgetItem.Purchase>>>{it.key.shopId==null}
                        .thenBy{shops[it.key.shopId]?.cityId!=cityId}
                        .thenByDescending{place->val shop=shops[place.key.shopId];shop!=null&&shop.cityId==cityId&&!shop.manualOrderOverride&&(shop.boostUrgentShop?:boostUrgentShops)&&place.value.any{it.row.urgent&&!it.row.bought}}
                        .thenBy{shops[it.key.shopId]?.displayOrder?:Int.MAX_VALUE}.thenBy{it.key.shopId}
                        .thenBy{branches[it.key.branchId]?.displayOrder?:0}.thenBy{it.key.branchId}
                        .thenBy{!it.key.isHint}.thenBy{it.key.name}.thenBy{it.key.address})
                val trips=places.groupBy{place->branches[place.key.branchId]?.venueId?.let{"venue:$it"}?:place.key.stableKey}
                trips.values.forEach{trip->
                    val venue=branches[trip.first().key.branchId]?.venueId?.let{venues[it]}
                    if(venue!=null)result.add(WidgetItem.Header("venue:${venue.id}",venue.name,venue.address))
                    trip.forEach{place->
                        val shopId=place.key.shopId;val branchId=place.key.branchId
                        val shop=shops[shopId]
                        val name=shop?.let{if(it.cityId==cityId)it.name else "${it.name} · ${cities[it.cityId]?.name?:"Другой город"}"}?:"Без магазина"
                        val label=hintLabel(place.key)?:com.spisokryadom.app.domain.physicalPlaceLabel(name,branches[branchId]?.address,null)
                        result.add(WidgetItem.Header(place.key.stableKey,label.title,label.subtitle,if(venue==null)0 else 1))
                        if(shop!=null){
                            val depts=place.value.groupBy{entry(it).assignedDepartmentId}
                            depts.keys.sortedWith(compareBy<Long?>{id->val dept=departments[id];orders[branchId to dept?.typeId]?.displayOrder?:dept?.displayOrder?:Int.MAX_VALUE}.thenBy{it}).forEach{id->
                                if(settings.showDepartments&&(id!=null||depts.size>1))result.add(WidgetItem.Header("department:$shopId:$branchId:$id",departments[id]?.name?:"Без группы магазина",depth=2))
                                result.addAll(sorted(depts.getValue(id)))
                            }
                        }else result.addAll(sorted(place.value))
                    }
                }
            }
        }
        result
    }
    /** Idempotent compare-and-set: repeated taps and stale widgets never undo newer changes. */
    suspend fun check(row:WidgetPurchase):Boolean = db.withTransaction {
        val entry=db.shoppingListEntryDao().getById(row.id)?:return@withTransaction false
        if(entry.listId!=null&&db.goalListDao().getById(entry.listId)?.showInPurchases!=true)return@withTransaction false
        val product=db.productDao().getById(entry.productId)?:return@withTransaction false
        if(product.stableId!=row.productStableId||entry.createdAt!=row.createdAt||entry.updatedAt!=row.updatedAt||entry.isBought!=row.bought||revision(entry)!=row.revision)return@withTransaction false
        if(row.bought)basket.markNotBought(row.id)else basket.markBought(row.id)
        true
    }
}
