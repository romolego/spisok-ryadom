package com.spisokryadom.app.data.repository
import androidx.room.withTransaction
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.dao.ShoppingListEntryDao
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.util.Quantities
import kotlinx.coroutines.flow.Flow

enum class ShopChoice { AUTO, NONE, EXPLICIT }
class ShoppingListRepository(private val entryDao: ShoppingListEntryDao, private val db: AppDatabase, private val locations: LocationRepository, private val catalogueEdits:CatalogueEdits?=null) {
    fun getAllEntries(): Flow<List<ShoppingListEntryEntity>> = entryDao.getAll()
    fun getEntriesForList(listId:Long)=entryDao.getForList(listId)
    suspend fun getEntryById(id: Long) = entryDao.getById(id)
    private suspend fun assignments(productId:Long?=null)=com.spisokryadom.app.domain.AssignmentCatalogue(
        if(productId==null)db.productDao().getAllSync()else listOfNotNull(db.productDao().getById(productId)),db.shopDao().getAllSync(),
        if(productId==null)db.productShopLinkDao().getAllSync()else db.productShopLinkDao().getLinksForProductSync(productId),
        db.shopDepartmentDao().getAllSync(),db.shopBranchDao().getAll(),
        if(productId==null)db.shopBranchDao().allAvailability()else db.shopBranchDao().availability(productId),
        db.retailStructureDao().productDepartments().filter{productId==null||it.productId==productId},db.shopBranchDao().priorities().filter{productId==null||it.productId==productId})
    private fun retailerChoice(choice:ShopChoice,id:Long?)=when(choice){
        ShopChoice.AUTO->com.spisokryadom.app.domain.RetailerChoice.Automatic
        ShopChoice.NONE->com.spisokryadom.app.domain.RetailerChoice.Unassigned
        ShopChoice.EXPLICIT->com.spisokryadom.app.domain.RetailerChoice.Exact(requireNotNull(id){"Выберите магазин"})
    }
    private fun outletChoice(id:Long?)=when(id){null,-1L->com.spisokryadom.app.domain.OutletChoice.Preferred;0L->com.spisokryadom.app.domain.OutletChoice.Unassigned;else->com.spisokryadom.app.domain.OutletChoice.Exact(id)}
    suspend fun addProduct(productId: Long, quantity: Double?=null, unit: String?=null, choice: ShopChoice=ShopChoice.AUTO, shopId: Long?=null, branchId:Long?=null,listId:Long?=null,plannedUnitPrice:Double?=null): Long {
        val cityId=locations.currentId()
        return db.withTransaction {
            validateListPrice(listId,plannedUnitPrice)
            val product=db.productDao().getById(productId) ?: error("Товар удалён")
            val resolved=assignments(productId).resolve(productId,cityId,retailerChoice(choice,shopId),outletChoice(branchId))
            val row=ShoppingListEntryEntity(productId=productId,listId=listId,plannedUnitPrice=plannedUnitPrice,
                quantity=Quantities.valid(quantity ?: product.defaultQuantity?.takeIf { it>0 && it.isFinite() } ?: 1.0),
                explicitNoShop=choice==ShopChoice.NONE,awaitingShopCityId=cityId.takeIf{choice==ShopChoice.AUTO&&resolved.shopId==null},unit=Quantities.unit(unit ?: product.defaultUnit.orEmpty()),assignedShopId=resolved.shopId,assignedDepartmentId=resolved.departmentId,assignedBranchId=resolved.branchId)
            validateTarget(row)
            entryDao.addOrMerge(row)
        }
    }
    suspend fun addNewProduct(name: String, quantity: Double, unit: String, permanent: Boolean, choice: ShopChoice=ShopChoice.AUTO, shopId: Long?=null, branchId:Long?=null,listId:Long?=null,plannedUnitPrice:Double?=null,purchaseType:String="offline"): Long {
        val cityId=locations.currentId(); require(name.isNotBlank()) { "Введите название товара" }
        return db.withTransaction {
            validateListPrice(listId,plannedUnitPrice)
            require(purchaseType in setOf("offline","online"))
            val product=ProductEntity(purchaseType=purchaseType,name=name.trim(), defaultUnit=Quantities.unit(unit).ifBlank { null },isTemporary=!permanent,preferCityShopOrder=true)
            val id=db.productDao().insert(product)
            if (choice==ShopChoice.EXPLICIT) {
                val shop=db.shopDao().getById(shopId ?: -1)
                require(shop?.cityId==cityId) { "Магазин недоступен в выбранном городе" }
                if(permanent) db.productShopLinkDao().insert(ProductShopLinkEntity(productId=id,shopId=shopId!!))
            }
            val row=ShoppingListEntryEntity(productId=id,listId=listId,plannedUnitPrice=plannedUnitPrice,explicitNoShop=choice==ShopChoice.NONE,awaitingShopCityId=cityId.takeIf{choice==ShopChoice.AUTO},quantity=Quantities.valid(quantity),unit=Quantities.unit(unit),assignedShopId=if(choice==ShopChoice.EXPLICIT)shopId else null,assignedBranchId=resolveBranch(id,if(choice==ShopChoice.EXPLICIT)shopId else null,branchId))
            validateTarget(row)
            entryDao.insert(row)
        }
    }
    internal suspend fun addOrMergeEntry(entry: ShoppingListEntryEntity): Long = db.withTransaction {
        validateTarget(entry)
        entryDao.addOrMerge(entry.copy(quantity=Quantities.valid(entry.quantity),unit=Quantities.unit(entry.unit)))
    }
    private suspend fun validateListPrice(listId:Long?,price:Double?) {
        listId?.let{require(db.goalListDao().getById(it)!=null){"Список удалён"}}
        GoalListRepository.validPrice(price)
    }
    fun observeGoalLists()=db.goalListDao().observeAll()
    private suspend fun validateTarget(entry: ShoppingListEntryEntity) {
        validateListPrice(entry.listId,entry.plannedUnitPrice)
        entry.plannedUnitPrice?.let{require((it*entry.quantity).isFinite()){"Слишком большая сумма"}}
        require(db.productDao().getById(entry.productId)!=null) { "Товар удалён" }
        if(entry.assignedShopId!=null) require(db.shopDao().getById(entry.assignedShopId)!=null) { "Магазин удалён" }
        if(entry.assignedBranchId!=null)require(db.shopBranchDao().get(entry.assignedBranchId)?.shopId==entry.assignedShopId){"Адрес относится к другому магазину"}
        if(entry.assignedDepartmentId!=null) require(db.shopDepartmentDao().getById(entry.assignedDepartmentId)?.shopId==entry.assignedShopId) { "Группа относится к другому магазину" }
    }
    suspend fun adjustQuantity(id: Long, delta: Double, strictVisible: Boolean = false) = db.withTransaction {
        requireVisibleEntry(id, strictVisible)
        require(delta.isFinite())
        adjustQuantityInTransaction(id, delta)
    }
    /** Read both the actual row and card under the same transaction as the increment. */
    suspend fun adjustByDefaultStep(id: Long, direction: Int, strictVisible: Boolean = false, regularOnly: Boolean = false) = db.withTransaction {
        requireVisibleEntry(id, strictVisible, regularOnly)
        require(direction == 1 || direction == -1)
        val entry = entryDao.getById(id) ?: return@withTransaction
        val product = db.productDao().getById(entry.productId) ?: return@withTransaction
        val step = Quantities.step(entry.unit, product.defaultUnit, product.defaultQuantity)
        adjustQuantityInTransaction(id, direction * step)
    }
    private suspend fun adjustQuantityInTransaction(id: Long, delta: Double) {
        val entry=entryDao.getById(id) ?: return
        if(entry.isBought)return
        val quantity=entry.quantity+delta
        if(quantity<=0.0000001) { if (entry.listId != null) return; entryDao.deleteById(id) }
        else entryDao.update(entry.copy(quantity=Quantities.valid(quantity),updatedAt=System.currentTimeMillis()))
        db.productDao().deleteUnusedTemporary()
    }
    suspend fun updateQuantity(id: Long, quantity: Double, regularOnly: Boolean = false)=db.withTransaction {
        requireVisibleEntry(id, false, regularOnly)
        val entry=entryDao.getById(id) ?: return@withTransaction
        entryDao.update(entry.copy(quantity=Quantities.valid(quantity),updatedAt=System.currentTimeMillis()))
    }
    suspend fun changeShop(id: Long, shopId: Long?, departmentId: Long?, strictVisible: Boolean = false) = db.withTransaction {
        requireVisibleEntry(id, strictVisible)
        val entry = entryDao.getById(id) ?: return@withTransaction
        val point=if(shopId==entry.assignedShopId)entry.assignedBranchId else resolveBranch(entry.productId,shopId)
        val department=resolveDepartment(entry,shopId,departmentId,false)
        val changed = entry.copy(explicitNoShop=shopId==null,awaitingShopCityId=null,assignedShopId = shopId, customShopName=null,customShopAddress=null,assignedBranchId=point,
            assignedDepartmentId = if(shopId==entry.assignedShopId)department else departmentAtPoint(entry.productId,shopId,point,department),
            updatedAt = System.currentTimeMillis())
        validateTarget(changed)
        entryDao.update(changed)
    }
    /** Editing purchase facts cannot accidentally restore a stale assignment from an open editor. */
    suspend fun updatePurchaseDetails(id:Long,quantity:Double,note:String?,comment:String?,strictVisible:Boolean=false)=db.withTransaction {
        requireVisibleEntry(id, strictVisible)
        val entry=entryDao.getById(id)?:error("Покупка уже удалена")
        entryDao.update(entry.copy(quantity=Quantities.valid(quantity),note=note?.trim()?.ifBlank{null},comment=comment?.trim()?.ifBlank{null},updatedAt=System.currentTimeMillis()))
    }
    /** Preferred product link, then a department with the old name in the target shop. */
    private suspend fun resolveDepartment(entry: ShoppingListEntryEntity, shopId: Long?, explicitId: Long?, keepOnSameShop: Boolean): Long? {
        if (shopId == null) {
            require(explicitId == null) { "Группа относится к другому магазину" }
            return null
        }
        require(db.shopDao().getById(shopId) != null) { "Магазин удалён" }
        if (explicitId != null) {
            require(db.shopDepartmentDao().getById(explicitId)?.shopId == shopId) { "Группа относится к другому магазину" }
            return explicitId
        }
        if (keepOnSameShop && shopId == entry.assignedShopId) return entry.assignedDepartmentId
        db.productShopLinkDao().getLinksForProductSync(entry.productId)
            .firstOrNull { it.shopId == shopId }?.departmentId?.let { id ->
                if (db.shopDepartmentDao().getById(id)?.shopId == shopId) return id
            }
        val oldType = entry.assignedDepartmentId?.let { db.shopDepartmentDao().getById(it)?.typeId } ?: return null
        return db.shopDepartmentDao().getByShopIdSync(shopId)
            .firstOrNull { it.typeId==oldType }?.id
    }
    /** Revalidate an aggregate-screen action in the same transaction as its write. */
    private suspend fun requireVisibleEntry(id: Long, strict: Boolean, regularOnly: Boolean = false) {
        if (!strict && !regularOnly) return
        val row = entryDao.getById(id) ?: error("Покупка удалена. Обновите список")
        require(!regularOnly || row.listId == null) { "Покупка перенесена в целевой список. Обновите каталог" }
        require(!regularOnly || !row.isBought) { "Покупка уже отмечена купленной. Обновите каталог" }
        require(!strict || row.listId == null || db.goalListDao().getById(row.listId)?.let { it.showInPurchases && !it.isTemplate } == true) {
            "Покупка больше не отображается в общей корзине. Откройте её целевой список"
        }
    }
    suspend fun removeEntry(id: Long, strictVisible: Boolean = false, regularOnly: Boolean = false)=db.withTransaction { requireVisibleEntry(id, strictVisible, regularOnly); entryDao.deleteById(id);db.productDao().deleteUnusedTemporary() }
    suspend fun markBought(id: Long, strictVisible: Boolean = false)=db.withTransaction { requireVisibleEntry(id, strictVisible); entryDao.markBought(id) }
    suspend fun markNotBought(id: Long, strictVisible: Boolean = false)=db.withTransaction { requireVisibleEntry(id, strictVisible); entryDao.markNotBought(id) }
    suspend fun toggleUrgent(id: Long,strictPending:Boolean=false)=db.withTransaction{requireVisibleEntry(id, strictPending);val row=entryDao.getById(id)?:error("Покупка удалена");require(!strictPending||!row.isBought){"Срочность доступна для некупленных"};entryDao.toggleUrgency(id)}
    suspend fun clearBoughtEntries()=db.withTransaction {entryDao.clearBought();db.productDao().deleteUnusedTemporary()}
    suspend fun clearBoughtByShop(shopId: Long)=db.withTransaction {entryDao.clearBoughtByShop(shopId);db.productDao().deleteUnusedTemporary()}
    suspend fun clearBoughtWithoutShop()=db.withTransaction {entryDao.clearBoughtWithoutShop();db.productDao().deleteUnusedTemporary()}
    suspend fun movePendingToCurrentCity() {
        val city=locations.currentId()
        db.withTransaction {
            val shops=db.shopDao().getAllSync().associateBy{it.id};val catalogue=assignments()
            entryDao.getVisibleSync().filter { !it.isBought && it.assignedShopId!=null && shops[it.assignedShopId]?.cityId!=city }.forEach { entry ->
                val target=catalogue.resolve(entry.productId,city,com.spisokryadom.app.domain.RetailerChoice.Automatic)
                entryDao.update(entry.copy(explicitNoShop=false,awaitingShopCityId=city.takeIf{target.shopId==null},assignedShopId=target.shopId,assignedDepartmentId=target.departmentId,assignedBranchId=target.branchId,updatedAt=System.currentTimeMillis()))
            }
        }
    }

    fun observeBranches()=db.shopBranchDao().observeAll()
    suspend fun branches()=db.shopBranchDao().getAll()
    private suspend fun departmentAtPoint(productId:Long,shopId:Long?,branchId:Long?,fallback:Long?)=assignments(productId).department(productId,shopId,branchId,fallback)
    suspend fun resolveBranch(productId:Long,shopId:Long?,branchId:Long?=null):Long? = assignments(productId).outlet(productId,shopId,outletChoice(branchId))
    data class PurchasePlace(val shopId:Long,val branchId:Long?,val shopName:String,val address:String?,val venueName:String?=null) {
        val presentation:com.spisokryadom.app.domain.PlaceLabel get()=com.spisokryadom.app.domain.physicalPlaceLabel(shopName,address,venueName)
        val label:String get()=presentation.text
    }
    /** Only real catalogue availability, intersected for a multi-selection. No speculative destinations. */
    suspend fun purchasePlaces(ids:Set<Long>):List<PurchasePlace> = db.withTransaction {
        if(ids.isEmpty())return@withTransaction emptyList()
        val rows=entryDao.getAllSync().filter{it.id in ids && !it.isBought}
        if(rows.isEmpty())return@withTransaction emptyList()
        val products=rows.map{it.productId}.toSet()
        val links=db.productShopLinkDao().getAllSync().filter{it.productId in products}.groupBy{it.shopId}
        val points=db.shopBranchDao().getAll().groupBy{it.shopId}
        val venues=db.retailStructureDao().venues().associateBy{it.id}
        val labels=com.spisokryadom.app.domain.PlaceLabels(points.values.flatten(),venues.values.toList())
        val available=db.shopBranchDao().allAvailability().groupBy{it.productId}.mapValues{(_,v)->v.map{it.branchId}.toSet()}
        val city=locations.currentId()
        db.shopDao().getAllSync().filter{it.cityId==city}.sortedWith(compareBy<ShopEntity>{it.displayOrder}.thenBy{it.id}).flatMap{shop->
            val linked=links[shop.id].orEmpty().associateBy{it.productId}
            if(!linked.keys.containsAll(products))emptyList() else {
                val branches=points[shop.id].orEmpty().sortedWith(compareBy<ShopBranchEntity>{it.displayOrder}.thenBy{it.id})
                if(branches.isEmpty())listOf(PurchasePlace(shop.id,null,shop.name,null))
                else branches.filter{b->products.all{p->linked.getValue(p).restrictBranches.not() || b.id in available[p].orEmpty()}}
                    .map{PurchasePlace(shop.id,it.id,shop.name,labels.details(shop.name,it),it.venueId?.let{v->venues[v]?.name})}
            }
        }
    }
    suspend fun additionalPurchasePlaces(ids:Set<Long>):List<PurchasePlace> = db.withTransaction {
        val products=entryDao.getAllSync().filter{it.id in ids&&!it.isBought}.map{it.productId}.toSet()
        if(products.isEmpty())return@withTransaction emptyList()
        val existing=purchasePlaces(ids).map{it.shopId to it.branchId}.toSet()
        val points=db.shopBranchDao().getAll().groupBy{it.shopId}
        val venues=db.retailStructureDao().venues().associateBy{it.id}
        val labels=com.spisokryadom.app.domain.PlaceLabels(points.values.flatten(),venues.values.toList())
        db.shopDao().getAllSync().filter{it.cityId==locations.currentId()}.sortedWith(compareBy<ShopEntity>{it.displayOrder}.thenBy{it.id}).flatMap{shop->
            points[shop.id].orEmpty().ifEmpty{listOf(null)}.map{b->PurchasePlace(shop.id,b?.id,shop.name,b?.let{labels.details(shop.name,it)},b?.venueId?.let{venues[it]?.name})}
        }.filter{(it.shopId to it.branchId) !in existing}
    }
    suspend fun clearBoughtInPlace(shopId:Long?,branchId:Long?,customShopName:String?=null,customShopAddress:String?=null)=db.withTransaction {
        entryDao.getVisibleSync().filter{it.isBought&&it.assignedShopId==shopId&&it.assignedBranchId==branchId&&it.customShopName==customShopName&&it.customShopAddress==customShopAddress}.forEach{entryDao.deleteById(it.id)}
        db.productDao().deleteUnusedTemporary()
    }
    suspend fun movePending(ids:Set<Long>,shopId:Long?,branchId:Long? = -1L,rememberPriority:Boolean=false,extendAvailability:Boolean=false,strictPending:Boolean=false):Int = withCatalogueEdits(db,catalogueEdits) { db.withTransaction {
        require(ids.isNotEmpty()){"Выберите покупки"}
        val selected=ids.map{entryDao.getById(it)?:error("Одна из покупок уже удалена")}
        if(strictPending){
            val visible=entryDao.getVisibleSync().map{it.id}.toSet()
            require(ids.all{it in visible}){"Состав покупок изменился. Обновите выбор"}
        }
        // UI selections promise to move the complete pending set. A widget may
        // change a row while the destination picker is open; reject that stale set
        // before adding availability or changing any product preferences.
        require(!strictPending||selected.none{it.isBought}){"Одна из покупок уже отмечена купленной. Обновите выбор"}
        val rows=selected.filter{!it.isBought}
        if(rows.isEmpty())return@withTransaction 0
        val city=locations.currentId()
        if(shopId!=null)require(db.shopDao().getById(shopId)?.cityId==city){"Магазин недоступен в этом городе"}
        if(extendAvailability){
            require(shopId!=null){"Выберите магазин"}
            if(branchId!=null&&branchId>0)require(db.shopBranchDao().get(branchId)?.shopId==shopId){"Точка относится к другому магазину"}
            rows.map{it.productId}.distinct().forEach{product->
                val links=db.productShopLinkDao().getLinksForProductSync(product)
                val link=links.find{it.shopId==shopId}
                if(link==null){
                    val exact=branchId!=null&&branchId>0
                    db.productShopLinkDao().insert(ProductShopLinkEntity(productId=product,shopId=shopId,priority=(links.maxOfOrNull{it.priority}?:0)+1,restrictBranches=exact))
                    if(exact)db.shopBranchDao().insertAvailability(ProductBranchAvailabilityEntity(product,branchId!!))
                }
                else if(link.restrictBranches){
                    require(branchId!=null&&branchId>0){"Выберите конкретную точку"}
                    if(db.shopBranchDao().availability(product).none{it.branchId==branchId})db.shopBranchDao().insertAvailability(ProductBranchAvailabilityEntity(product,branchId))
                }
            }
        }
        val catalogue=assignments()
        if(shopId!=null)require(db.shopDao().getById(shopId)?.cityId==city){"Магазин недоступен в этом городе"}
        if(shopId!=null) {
            val places=purchasePlaces(ids)
            require(places.any{it.shopId==shopId && (branchId==null || branchId == -1L || it.branchId==branchId)}){"Товар недоступен в выбранном месте покупки"}
        }
        val changed=rows.map { row ->
            val point=catalogue.outlet(row.productId,shopId,outletChoice(branchId))
            row.copy(explicitNoShop=shopId==null,awaitingShopCityId=null,customShopName=null,customShopAddress=null,assignedShopId=shopId,assignedBranchId=point,assignedDepartmentId=catalogue.department(row.productId,shopId,point,resolveDepartment(row,shopId,null,false)),updatedAt=System.currentTimeMillis())
        }
        changed.forEach{validateTarget(it);entryDao.update(it)}
        if(rememberPriority&&shopId!=null)changed.distinctBy{it.productId}.forEach{row->
            val product=db.productDao().getById(row.productId)?:error("Товар удалён")
            val links=db.productShopLinkDao().getLinksForProductSync(product.id)
            val shopOrder=db.shopDao().getAllSync().associate{it.id to it.displayOrder}
            val ordered=links.sortedWith(compareBy<ProductShopLinkEntity>{if(it.shopId==shopId)0 else 1}.thenBy{if(product.preferCityShopOrder)shopOrder[it.shopId]?:Int.MAX_VALUE else it.priority})
            ordered.forEachIndexed{i,link->db.productShopLinkDao().update(link.copy(priority=i+1))}
            db.productDao().update(product.copy(preferCityShopOrder=false))
            row.assignedBranchId?.let{point->
                val old=db.shopBranchDao().priorities().filter{it.productId==product.id&&it.shopId==shopId}.associate{it.branchId to it.displayOrder}
                val points=db.shopBranchDao().forShop(shopId).sortedWith(compareBy<ShopBranchEntity>{if(it.id==point)0 else 1}.thenBy{old[it.id]?:Int.MAX_VALUE}.thenBy{it.displayOrder}.thenBy{it.id})
                db.shopBranchDao().clearShopPriorities(product.id,shopId)
                points.forEachIndexed{i,p->db.shopBranchDao().setPriority(ProductBranchPriorityEntity(product.id,p.id,shopId,i))}
            }
        }
        changed.size
    } }
    data class TextSnapshot(val entries:List<ShoppingListEntryEntity>,val products:List<ProductEntity>,val shops:List<ShopEntity>,val branches:List<ShopBranchEntity>,val venues:List<VenueEntity> = emptyList(),val goalLists:List<GoalListEntity> = emptyList())
    suspend fun textSnapshot():TextSnapshot=db.withTransaction {
        TextSnapshot(entryDao.getVisibleSync().filter{!it.isBought},db.productDao().getAllSync(),db.shopDao().getAllSync(),db.shopBranchDao().getAll(),db.retailStructureDao().venues(),db.goalListDao().getAllSync())
    }


    suspend fun moveOptions(ids:Set<Long>,shopId:Long):Set<Long> = db.withTransaction {
        var allowed=db.shopBranchDao().forShop(shopId).map{it.id}.toSet()
        for(id in ids) {
            val row=entryDao.getById(id)?:continue
            if(row.isBought)continue
            val link=db.productShopLinkDao().getLinksForProductSync(row.productId).find{it.shopId==shopId}
            if(link?.restrictBranches==true)allowed=allowed.intersect(db.shopBranchDao().availability(row.productId).map{it.branchId}.toSet())
        }
        allowed
    }
}
