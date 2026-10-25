package com.spisokryadom.app.data.repository

import com.spisokryadom.app.data.dao.ProductDao
import com.spisokryadom.app.data.dao.ProductShopLinkDao
import com.spisokryadom.app.data.entity.ProductEntity
import com.spisokryadom.app.data.entity.ProductShopLinkEntity
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.spisokryadom.app.util.FuzzySearch

object ProductEditorTransactions {
    val mutex=kotlinx.coroutines.sync.Mutex()
    val generation=java.util.concurrent.atomic.AtomicLong()
    private object Key:kotlin.coroutines.CoroutineContext.Key<Owner>
    private class Owner:kotlin.coroutines.AbstractCoroutineContextElement(Key)
    suspend fun <T> inGeneration(expected: Long, block: suspend () -> T): T = locked {
        require(generation.get() == expected) { "База восстановлена. Откройте экран заново" }
        block()
    }
    suspend fun <T> locked(block:suspend()->T):T {
        if(kotlinx.coroutines.currentCoroutineContext()[Key]!=null)return block()
        mutex.lock()
        try{return kotlinx.coroutines.withContext(Owner()){block()}}finally{mutex.unlock()}
    }
}

class ProductRepository(
    private val productDao: ProductDao,
    private val linkDao: ProductShopLinkDao,
    private val db: com.spisokryadom.app.data.db.AppDatabase, private val catalogueEdits:CatalogueEdits?=null
) {
    val variants=ProductVariantsRepository(db,catalogueEdits)
    class DuplicateName:IllegalArgumentException("Товар с таким названием уже есть")
    suspend fun getProductByStableId(stableId:String)=productDao.getByStableId(stableId)
    suspend fun editorShops()=db.shopDao().getAllSync()
    suspend fun editorDepartments()=db.shopDepartmentDao().getAllSync()
    suspend fun editorBranches()=db.shopBranchDao().getAll()
    suspend fun saveEditorProduct(product:ProductEntity,links:List<ProductShopLinkEntity>,availability:Map<Long,Set<Long>>,departments:List<com.spisokryadom.app.data.entity.ShopDepartmentEntity>,branchDepartments:Map<Long,Long>,newSession:Boolean,allowDuplicate:Boolean,branchPriorities:Map<Long,List<Long>>?=null):Long=db.withTransaction {
        val existing=if(newSession)productDao.getByStableId(product.stableId)else productDao.getById(product.id)
        if(!newSession)require(existing?.stableId==product.stableId){"Товар удалён или заменён"}
        if(newSession&&!allowDuplicate&&productDao.getAllSync().any{!it.isTemporary&&it.stableId!=product.stableId&&FuzzySearch.normalize(it.name)==FuzzySearch.normalize(product.name)})throw DuplicateName()
        saveWithLinks(product.copy(id=existing?.id?:0),links,availability,departments,branchDepartments,branchPriorities)
    }
    suspend fun discardNewEditorProduct(stableId:String)= withCatalogueEdits(db,catalogueEdits) { db.withTransaction {
        productDao.getByStableId(stableId)?.let{p->
            require(db.shoppingListEntryDao().getAllSync().none{it.productId==p.id}){"У созданного товара уже есть покупки"}
            productDao.delete(p)
        }
    } }
    suspend fun adoptEditorProduct(productId:Long,editorStableId:String,shopIds:List<Long>,draftLinks:List<ProductShopLinkEntity>,availability:Map<Long,Set<Long>>,departments:List<com.spisokryadom.app.data.entity.ShopDepartmentEntity>,branchDepartments:Map<Long,Long>)= withCatalogueEdits(db,catalogueEdits) { db.withTransaction {
        val target=productDao.getById(productId)?:error("Товар удалён")
        if(target.stableId!=editorStableId)discardNewEditorProduct(editorStableId)
        ensureShopLinks(productId,shopIds,draftLinks,availability,departments,branchDepartments)
    } }
    fun getAllProducts(): Flow<List<ProductEntity>> = productDao.getAll()
    fun getAllProductsIncludingTemporary(): Flow<List<ProductEntity>> = kotlinx.coroutines.flow.combine(productDao.getAllIncludingTemporary(),db.productVariantDao().observeVariants()){ps,vs->com.spisokryadom.app.domain.variantNames(ps,vs)}
    fun getProductsByRecipientId(recipientId: Long): Flow<List<ProductEntity>> =
        productDao.getByRecipientId(recipientId)
    fun getProductsByGroupId(groupId: Long): Flow<List<ProductEntity>> =
        productDao.getByGroupId(groupId)
    fun getProductsByUnit(unit: String): Flow<List<ProductEntity>> = getAllProductsIncludingTemporary().map { products ->
        val canonical=com.spisokryadom.app.util.Quantities.unit(unit)
        products.filter{com.spisokryadom.app.util.Quantities.unit(it.defaultUnit.orEmpty())==canonical}
    }
    suspend fun getProductById(id: Long): ProductEntity? = productDao.getById(id)
    suspend fun deleteProduct(product: ProductEntity) = withCatalogueEdits(db,catalogueEdits){db.withTransaction{
        val actual=productDao.getById(product.id)?:return@withTransaction
        val familyIds=variants.options(actual.id,db.cityDao().getAllSync().firstOrNull()?.id?:1).map{it.product.id}.toSet().takeIf{variants.familyId(actual.id)==actual.id}?:setOf(actual.id)
        familyIds.forEach{member->
            val p=productDao.getById(member)?:return@forEach
            val goalRows=db.shoppingListEntryDao().getAllSync().filter{it.productId==member&&it.listId!=null}
            if(goalRows.isNotEmpty()){
                val name=com.spisokryadom.app.domain.variantNames(listOf(p),db.productVariantDao().variants()).single().name
                val copy=productDao.insert(p.copy(id=0,stableId=java.util.UUID.randomUUID().toString(),isTemporary=true,name=name))
                variants.savePhotos(copy,variants.photos(member))
                goalRows.forEach{db.shoppingListEntryDao().update(it.copy(productId=copy,updatedAt=System.currentTimeMillis()))}
            }
        }
        productDao.delete(actual)
    }}

    fun observeAvailability() = db.shopBranchDao().observeAvailability()

    fun getCatalogueShopLinks()=kotlinx.coroutines.flow.combine(linkDao.getAll(),variants.observeMetadata(),db.shopDao().getAllOrdered()){links,metadata,shops->
        val families=metadata.first.associate{it.productId to it.familyId}
        val cities=shops.associate{it.id to it.cityId}
        val disabled=metadata.second.filter{!it.enabled}.map{it.productId to it.cityId}.toSet()
        val visible=links.filter{link->cities[link.shopId]?.let{(link.productId to it) !in disabled}==true}
        visible.groupBy{(families[it.productId]?:it.productId) to it.shopId}.map{(key,rows)->rows.minBy{it.priority}.copy(productId=key.first,restrictBranches=rows.all{it.restrictBranches})}
    }
    fun getCatalogueLinksForShop(shopId:Long)=getCatalogueShopLinks().map{links->links.filter{it.shopId==shopId}}
    suspend fun toggleFamilyShopLink(productId:Long,shopId:Long)=withCatalogueEdits(db,catalogueEdits){db.withTransaction{
        val members=variants.options(productId,db.shopDao().getById(shopId)?.cityId?:error("Магазин удалён"))
        val linked=members.filter{linkDao.getLinksForProductSync(it.product.id).any{l->l.shopId==shopId}}
        if(linked.isEmpty())members.forEach{ensureShopLink(it.product.id,shopId)}
        else linked.forEach{toggleShopLink(it.product.id,shopId)}
    }}
    fun observeCatalogueAvailability()=kotlinx.coroutines.flow.combine(db.shopBranchDao().observeAvailability(),variants.observeMetadata(),db.shopDao().getAllOrdered()){rows,metadata,shops->
        val families=metadata.first.associate{it.productId to it.familyId}
        val cities=shops.associate{it.id to it.cityId}
        val disabled=metadata.second.filter{!it.enabled}.map{it.productId to it.cityId}.toSet()
        rows.filter{row->cities[row.shopId]?.let{(row.productId to it) !in disabled}==true}
            .map{it.copy(productId=families[it.productId]?:it.productId)}.distinctBy{it.productId to it.branchId}
    }
    fun getAllShopLinks(): Flow<List<ProductShopLinkEntity>> = linkDao.getAll()

    fun getShopLinksForProduct(productId: Long): Flow<List<ProductShopLinkEntity>> =
        linkDao.getLinksForProduct(productId)

    fun getShopLinksForShop(shopId: Long): Flow<List<ProductShopLinkEntity>> =
        linkDao.getLinksForShop(shopId)

    suspend fun getShopLinksForProductSync(productId: Long): List<ProductShopLinkEntity> =
        linkDao.getLinksForProductSync(productId)

    suspend fun toggleShopLink(productId:Long,shopId:Long)= withCatalogueEdits(db,catalogueEdits) { db.withTransaction {
        val links=linkDao.getLinksForProductSync(productId)
        val old=links.find{it.shopId==shopId}
        if(old!=null){
            val product=productDao.getById(productId)?:error("Товар удалён")
            linkDao.delete(old)
            refreshPending(product,product,links)
        } else {
            ensureShopLink(productId,shopId)
        }
    } }

    suspend fun saveWithLinks(product: ProductEntity, links: List<ProductShopLinkEntity>, availability:Map<Long,Set<Long>>?=null,departments:List<com.spisokryadom.app.data.entity.ShopDepartmentEntity> = emptyList(),branchDepartments:Map<Long,Long>?=null,branchPriorities:Map<Long,List<Long>>?=null): Long = db.withTransaction {
        require(product.name.isNotBlank()) { "Введите название товара" }
        product.defaultQuantity?.let { com.spisokryadom.app.util.Quantities.valid(it) }
        product.priceValue?.let { require(it.isFinite() && it>=0) { "Проверьте цену" } }
        val previous=if(product.id>0)productDao.getById(product.id)else null
        val oldPriorities=db.shopBranchDao().priorities().filter{it.productId==product.id}
        val oldAvailability=db.shopBranchDao().availability(product.id).map{it.branchId}.toSet()
        val previousLinks=if(product.id>0)linkDao.getLinksForProductSync(product.id)else emptyList()
        val id=if(product.id==0L)productDao.insert(product) else { require(productDao.getById(product.id)!=null) { "Товар удалён" };productDao.update(product);product.id }
        require(links.map{it.shopId}.distinct().size==links.size) { "Магазин добавлен дважды" }
        val deptIds=resolveDraftDepartments(links,departments,branchDepartments.orEmpty().values.toSet())
        val resolvedLinks=links.map{it.copy(departmentId=deptIds[it.departmentId]?:it.departmentId)}
        resolvedLinks.forEach { link ->
            require(db.shopDao().getById(link.shopId)!=null) { "Магазин удалён" }
            if(link.departmentId!=null)require(db.shopDepartmentDao().getById(link.departmentId)?.shopId==link.shopId) { "Группа относится к другому магазину" }
        }
        val branches=db.shopBranchDao().getAll().associateBy{it.id}
        val oldAvailable=db.shopBranchDao().availability(id).map{it.branchId}.toSet()
        val selected=availability ?: links.filter{it.restrictBranches}.associate{link->link.shopId to oldAvailable.filter{branches[it]?.shopId==link.shopId}.toSet()}
        selected.forEach{(shop,ids)->require(links.any{it.shopId==shop}){"Нет привязки к магазину"};require(ids.isNotEmpty()&&ids.all{branches[it]?.shopId==shop}){"Выберите хотя бы одну точку этого магазина"}}
        // Stable relationship IDs: only removed links cascade their owned availability.
        val existing=linkDao.getLinksForProductSync(id).associateBy{it.shopId}
        existing.values.filter{old->resolvedLinks.none{it.shopId==old.shopId}}.forEach{linkDao.delete(it)}
        db.shopBranchDao().clearAvailability(id)
        resolvedLinks.sortedBy{it.priority}.forEachIndexed{i,link->
            val old=existing[link.shopId]
            val value=link.copy(id=old?.id?:0,productId=id,priority=i+1,restrictBranches=link.shopId in selected)
            if(old==null)linkDao.insert(value)else linkDao.update(value)
        }
        selected.values.flatten().distinct().forEach{db.shopBranchDao().insertAvailability(com.spisokryadom.app.data.entity.ProductBranchAvailabilityEntity(id,it))}
        if(branchDepartments==null){
            db.retailStructureDao().productDepartments().filter{it.productId==id&&it.shopId in selected&&it.branchId !in selected[it.shopId].orEmpty()}.forEach{db.retailStructureDao().removeProductDepartment(id,it.branchId)}
        }
        if(branchDepartments!=null){
            db.retailStructureDao().clearProductDepartments(id)
            saveBranchDepartments(id,branchDepartments.mapValues{deptIds[it.value]?:it.value},resolvedLinks,selected)
        }
        if(branchPriorities!=null){
            branchPriorities.forEach{(shop,ids)->require(resolvedLinks.any{it.shopId==shop}&&ids.distinct().size==ids.size&&ids.all{branches[it]?.shopId==shop}){"Проверьте приоритеты точек"}}
            db.shopBranchDao().clearPriorities(id)
            branchPriorities.forEach{(shop,ids)->ids.forEachIndexed{order,branch->db.shopBranchDao().setPriority(com.spisokryadom.app.data.entity.ProductBranchPriorityEntity(id,branch,shop,order))}}
        }
        if(previous!=null){
            val retained=previousLinks.map{it.shopId}.intersect(resolvedLinks.map{it.shopId}.toSet())
            val priorityChanged=product.preferCityShopOrder!=previous.preferCityShopOrder||previousLinks.filter{it.shopId in retained}.sortedBy{it.priority}.map{it.shopId}!=resolvedLinks.filter{it.shopId in retained}.sortedBy{it.priority}.map{it.shopId}
            val now=db.shopBranchDao().priorities().filter{it.productId==id}
            val pointChanged=(oldPriorities.map{it.shopId}+now.map{it.shopId}).toSet().filter{shop->oldPriorities.filter{it.shopId==shop}.sortedBy{it.displayOrder}.map{it.branchId}!=now.filter{it.shopId==shop}.sortedBy{it.displayOrder}.map{it.branchId}}.toSet()
            refreshPending(product.copy(id=id),previous,previousLinks,priorityChanged,pointChanged,oldAvailability!=selected.values.flatten().toSet())
        }
        id
    }

    private suspend fun refreshPending(product:ProductEntity,previous:ProductEntity,previousLinks:List<ProductShopLinkEntity>,priorityChanged:Boolean=false,pointOrderChanged:Set<Long> = emptySet(),availabilityChanged:Boolean=false,waitingOnly:Boolean=false) {
        val links=linkDao.getLinksForProductSync(product.id).associateBy{it.shopId}
        val overrides=db.retailStructureDao().productDepartments().filter{it.productId==product.id}.associateBy{it.branchId}
        val placements=db.shopDepartmentDao().getAllSync().associateBy{it.shopId to it.typeId}
        val allowed=db.shopBranchDao().availability(product.id).map{it.branchId}.toSet()
        db.shoppingListEntryDao().getAllSync().filter{it.productId==product.id&&!it.isBought&&(!waitingOnly||(it.assignedShopId==null&&it.awaitingShopCityId!=null&&!it.explicitNoShop))}.forEach { row ->
            val link=links[row.assignedShopId]
            val department=overrides[row.assignedBranchId]?.let{placements[it.shopId to it.typeId]?.id}?:link?.departmentId
            val branches=db.shopBranchDao().getAll()
            val pointValid=link!=null&&(!link.restrictBranches||row.assignedBranchId in allowed)&&(row.assignedBranchId==null||branches.any{it.id==row.assignedBranchId&&it.shopId==row.assignedShopId})
            // A target item owns its planned quantity/unit, including the unit its price refers to.
            val unit=if(row.listId==null&&(row.unit.isBlank()||com.spisokryadom.app.util.Quantities.unit(row.unit)==com.spisokryadom.app.util.Quantities.unit(previous.defaultUnit.orEmpty())))product.defaultUnit.orEmpty()else row.unit
            val changed=row.copy(assignedDepartmentId=if(pointValid)department else null,unit=unit)
            val wasLinked=previousLinks.any{it.shopId==row.assignedShopId}
            if((wasLinked&&!pointValid)||(!pointValid&&(availabilityChanged||previousLinks!=links.values.toList())&&row.assignedShopId!=null)||priorityChanged||(row.assignedShopId in pointOrderChanged)||(row.assignedShopId==null&&row.awaitingShopCityId!=null&&!row.explicitNoShop)){
                val shops=db.shopDao().getAllSync();val city=row.awaitingShopCityId?:shops.find{it.id==row.assignedShopId}?.cityId?:return@forEach
                val catalogue=com.spisokryadom.app.domain.AssignmentCatalogue(products=listOf(product),shops=shops,links=links.values.toList(),departments=db.shopDepartmentDao().getAllSync(),branches=db.shopBranchDao().getAll(),availability=db.shopBranchDao().availability(product.id),overrides=overrides.values.toList(),priorities=db.shopBranchDao().priorities())
                val sameRetailerAvailable=link!=null&&(!link.restrictBranches||branches.any{it.shopId==link.shopId&&it.id in allowed})
                val choice=if(!priorityChanged&&sameRetailerAvailable)com.spisokryadom.app.domain.RetailerChoice.Exact(row.assignedShopId!!)else com.spisokryadom.app.domain.RetailerChoice.Automatic
                val next=catalogue.resolve(product.id,city,choice)
                val resolved=changed.copy(assignedShopId=next.shopId,assignedBranchId=next.branchId,assignedDepartmentId=next.departmentId,explicitNoShop=false,awaitingShopCityId=city.takeIf{next.shopId==null})
                if(resolved!=row)db.shoppingListEntryDao().update(resolved.copy(updatedAt=System.currentTimeMillis()))
            }else if(changed!=row)db.shoppingListEntryDao().update(changed.copy(updatedAt=System.currentTimeMillis()))
        }
    }

    suspend fun branchPriorities(productId:Long)=db.shopBranchDao().priorities().filter{it.productId==productId}
    fun observeBranchDepartments()=db.retailStructureDao().observeProductDepartments()
    suspend fun branchDepartments(productId:Long)=db.retailStructureDao().productDepartments().filter{it.productId==productId}
    private suspend fun saveBranchDepartments(productId:Long,choices:Map<Long,Long>,links:List<ProductShopLinkEntity>,availability:Map<Long,Set<Long>>) {
        choices.forEach{(branchId,departmentId)->
            val branch=db.shopBranchDao().get(branchId)?:error("Точка удалена")
            require(links.any{it.shopId==branch.shopId}){"Нет привязки к магазину"}
            require(branch.shopId !in availability||branchId in availability.getValue(branch.shopId)){"Товар недоступен в этой точке"}
            val department=db.shopDepartmentDao().getById(departmentId)?:error("Группа удалена")
            require(department.shopId==branch.shopId){"Группа относится к другому магазину"}
            db.retailStructureDao().setProductDepartment(com.spisokryadom.app.data.entity.ProductBranchDepartmentEntity(productId,branchId,branch.shopId,department.typeId))
        }
    }
    fun observeBranchAvailability()=db.shopBranchDao().observeAvailability()
    suspend fun ensureShopLink(productId:Long,shopId:Long)=ensureShopLinks(productId,listOf(shopId))
    suspend fun ensureShopLinks(productId:Long,shopIds:List<Long>,draftLinks:List<ProductShopLinkEntity> = emptyList(),availability:Map<Long,Set<Long>> = emptyMap(),departments:List<com.spisokryadom.app.data.entity.ShopDepartmentEntity> = emptyList(),branchDepartments:Map<Long,Long> = emptyMap())=withCatalogueEdits(db,catalogueEdits) { db.withTransaction {
        require(productDao.getById(productId)!=null){"Товар удалён"}
        shopIds.forEach{require(db.shopDao().getById(it)!=null){"Магазин удалён"}}
        val old=linkDao.getLinksForProductSync(productId)
        val added=shopIds.distinct().filter{id->old.none{it.shopId==id}}.map{id->draftLinks.find{it.shopId==id}?:ProductShopLinkEntity(productId=productId,shopId=id)}
        val deptIds=resolveDraftDepartments(added,departments,branchDepartments.values.toSet())
        added.forEach{draft->
            val dept=deptIds[draft.departmentId]?:draft.departmentId
            if(dept!=null)require(db.shopDepartmentDao().getById(dept)?.shopId==draft.shopId){"Группа относится к другому магазину"}
            val points=availability[draft.shopId]
            if(points!=null)require(points.isNotEmpty()&&points.all{db.shopBranchDao().get(it)?.shopId==draft.shopId}){"Выберите хотя бы одну точку этого магазина"}
            linkDao.insert(draft.copy(id=0,productId=productId,departmentId=dept,priority=nextProductShopPriority(db,productId),restrictBranches=points!=null))
            points?.forEach{db.shopBranchDao().insertAvailability(com.spisokryadom.app.data.entity.ProductBranchAvailabilityEntity(productId,it))}
        }
        val newShopIds=added.map{it.shopId}.toSet()
        val newChoices=branchDepartments.filterKeys{db.shopBranchDao().get(it)?.shopId in newShopIds}.mapValues{deptIds[it.value]?:it.value}
        saveBranchDepartments(productId,newChoices,added,availability)
        // Adding availability resolves waiting automatic purchases only. An explicit
        // purchase is a fact even when its retailer has no catalogue link yet.
        val product=productDao.getById(productId)!!
        refreshPending(product,product,linkDao.getLinksForProductSync(productId),waitingOnly=true)
    } }
    private suspend fun resolveDraftDepartments(links:List<ProductShopLinkEntity>,departments:List<com.spisokryadom.app.data.entity.ShopDepartmentEntity>,extraIds:Set<Long> = emptySet()):Map<Long,Long>{
        val deptIds=mutableMapOf<Long,Long>()
        departments.filter{dept->links.any{it.shopId==dept.shopId&&(it.departmentId==dept.id||dept.id in extraIds)}}.forEach{dept->
            require(dept.id<0&&dept.name.isNotBlank()){ "Проверьте новую группу" }
            require(db.shopDao().getById(dept.shopId)!=null){ "Магазин удалён" }
            val all=db.shopDepartmentDao().getByShopIdSync(dept.shopId)
            val found=all.find{(dept.typeId>0&&it.typeId==dept.typeId)||FuzzySearch.normalize(it.name)==FuzzySearch.normalize(dept.name)}
            deptIds[dept.id]=found?.id ?: db.shopDepartmentDao().insert(dept.copy(id=0,name=dept.name.trim(),displayOrder=nextDepartmentDisplayOrder(db,dept.shopId)))
            synchronizeNetworkDepartments(db,dept.shopId)
        }
        return deptIds
    }
    suspend fun branchAvailability(productId:Long)=db.shopBranchDao().availability(productId)

}
