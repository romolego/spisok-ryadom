package com.spisokryadom.app.data.repository
import kotlinx.coroutines.flow.map

import com.spisokryadom.app.data.dao.ShopDao
import com.spisokryadom.app.data.dao.ShopDepartmentDao
import com.spisokryadom.app.data.entity.ShopDepartmentEntity
import com.spisokryadom.app.data.entity.ShopEntity
import kotlinx.coroutines.flow.Flow
import androidx.room.withTransaction
import com.spisokryadom.app.data.db.AppDatabase

class ShopRepository(
    private val shopDao: ShopDao,
    private val departmentDao: ShopDepartmentDao,
    val locations: LocationRepository,
    private val db: AppDatabase, private val catalogueEdits:CatalogueEdits?=null
) {
    suspend fun saveWithDepartments(shop:ShopEntity, departments:List<ShopDepartmentEntity>, branches:List<com.spisokryadom.app.data.entity.ShopBranchEntity>?=null,networkName:String?=null,venueNames:Map<Long,String> = emptyMap(),branchOrders:Map<Long,List<Long>> = emptyMap(),convertToVenue:Boolean=false,purchaseMode:String?=null):Long = withCatalogueEdits(db,catalogueEdits) { db.withTransaction {
        require(shop.name.isNotBlank()) { "Укажите название магазина" }
        require(com.spisokryadom.app.util.AppearanceColors.validOptional(shop.shoppingBackground)){"Выберите цвет фона магазина"}
        require(db.cityDao().getById(shop.cityId)!=null) { "Город удалён" }
        // Detach old venue memberships before changing the retailer's city. Both
        // assignments are saved in this transaction; failure rolls everything back.
        val oldShop=shop.id.takeIf{it>0}?.let{shopDao.getById(it)}
        if(oldShop!=null&&oldShop.cityId!=shop.cityId&&branches!=null){
            db.shopBranchDao().forShop(shop.id).filter{it.venueId!=null}.forEach{db.shopBranchDao().update(it.copy(venueId=null))}
        }
        val resolved=if(networkName.isNullOrBlank())shop else if(shop.networkId==null)shop.copy(networkId=ensureNetwork(networkName))else{
            val network=db.retailStructureDao().networks().find{it.id==shop.networkId}?:error("Сеть удалена")
            require(db.retailStructureDao().networks().none{it.id!=network.id&&com.spisokryadom.app.util.FuzzySearch.normalize(it.name)==com.spisokryadom.app.util.FuzzySearch.normalize(networkName)}){"Сеть с таким названием уже есть"}
            db.retailStructureDao().updateNetwork(network.copy(name=networkName.trim()));shop
        }
        val id=if(shop.id==0L)shopDao.insert(resolved) else { require(shopDao.getById(shop.id)!=null);shopDao.update(resolved);shop.id }
        purchaseMode?.let{require(it in com.spisokryadom.app.util.PurchaseModes.labels);db.creationPresetDao().setShop(com.spisokryadom.app.data.entity.ShopCreationPreset(id,it))}
        val pendingBefore=db.shoppingListEntryDao().getAllSync().filter{!it.isBought&&it.assignedShopId==id}
        val retained=departments.filter{it.id>0}.map{it.id}.toSet()
        departmentDao.getByShopIdSync(id).filter{it.id !in retained}.forEach{departmentDao.delete(it)}
        departments.forEachIndexed{index,dept->
            require(dept.name.isNotBlank())
            val value=dept.copy(id=dept.id.coerceAtLeast(0),shopId=id,displayOrder=index+1,name=dept.name.trim())
            if(value.id==0L)departmentDao.insert(value) else { require(departmentDao.getById(value.id)?.shopId==id);departmentDao.update(value) }
        }
        if(branches!=null)saveBranches(id,branches.map{branch->
            val venueId=venueNames[branch.id]?.takeIf{it.isNotBlank()}?.let{ensureVenue(shop.cityId,it)}?:branch.venueId
            venueId?.let{require(db.retailStructureDao().venue(it)?.cityId==shop.cityId){"Торговый комплекс относится к другому городу"}}
            branch.copy(venueId=venueId)
        })
        else if(db.shopBranchDao().forShop(id).isEmpty()&&!shop.address.isNullOrBlank())db.shopBranchDao().insert(com.spisokryadom.app.data.entity.ShopBranchEntity(shopId=id,address=shop.address.trim()))
        branchOrders.forEach{(branchId,typeIds)->
            require(db.shopBranchDao().get(branchId)?.shopId==id){"Точка относится к другому магазину"}
            require(typeIds.distinct().size==typeIds.size&&typeIds.all{type->departmentDao.getByShopIdSync(id).any{it.typeId==type}}){"Проверьте группы точки"}
            db.retailStructureDao().clearDepartmentOrder(branchId)
            typeIds.forEachIndexed{order,type->db.retailStructureDao().setDepartmentOrder(com.spisokryadom.app.data.entity.BranchDepartmentOrderEntity(branchId,type,order))}
        }
        if(branches!=null)shopDao.update((shopDao.getById(id)!!).copy(address=db.shopBranchDao().forShop(id).firstOrNull()?.address))
        refreshPendingDepartments(id,pendingBefore)
        if(convertToVenue)this@ShopRepository.convertToVenue(id)
        id
    } }
    private suspend fun refreshPendingDepartments(shopId:Long,before:List<com.spisokryadom.app.data.entity.ShoppingListEntryEntity>) {
        val previous=before.associateBy{it.id}
        val placements=departmentDao.getByShopIdSync(shopId).associateBy{it.typeId}
        val links=db.productShopLinkDao().getAllSync().filter{it.shopId==shopId}.associateBy{it.productId}
        val overrides=db.retailStructureDao().productDepartments().filter{it.shopId==shopId}.associateBy{it.productId to it.branchId}
        db.shoppingListEntryDao().getAllSync().filter{!it.isBought&&it.assignedShopId==shopId}.forEach{row->
            val old=previous[row.id]?:return@forEach
            if(old.assignedBranchId==row.assignedBranchId&&old.assignedDepartmentId==row.assignedDepartmentId)return@forEach
            val type=overrides[row.productId to row.assignedBranchId]?.typeId
            val department=type?.let{placements[it]?.id}?:links[row.productId]?.departmentId?:row.assignedDepartmentId.takeIf{links[row.productId]==null}
            if(row.assignedDepartmentId!=department)db.shoppingListEntryDao().update(row.copy(assignedDepartmentId=department,updatedAt=System.currentTimeMillis()))
        }
    }
    suspend fun createPoint(shopId:Long,address:String,productId:Long?=null):Long=withCatalogueEdits(db,catalogueEdits){db.withTransaction {
        require(shopDao.getById(shopId)?.cityId==locations.currentId()){"Магазин недоступен в этом городе"};require(address.isNotBlank()){"Укажите адрес"}
        val points=db.shopBranchDao().forShop(shopId)
        val id=points.find{com.spisokryadom.app.util.FuzzySearch.normalize(it.address)==com.spisokryadom.app.util.FuzzySearch.normalize(address)}?.id?:db.shopBranchDao().insert(com.spisokryadom.app.data.entity.ShopBranchEntity(shopId=shopId,address=address.trim(),displayOrder=(points.maxOfOrNull{it.displayOrder}?:-1)+1))
        if(productId!=null&&db.productShopLinkDao().getLinksForProductSync(productId).any{it.shopId==shopId&&it.restrictBranches}&&db.shopBranchDao().availability(productId).none{it.branchId==id})db.shopBranchDao().insertAvailability(com.spisokryadom.app.data.entity.ProductBranchAvailabilityEntity(productId,id))
        id
    }
    }
    suspend fun createInCurrentCity(name:String):Long=db.withTransaction {
        val value=name.trim();require(value.isNotBlank()){"Укажите название магазина"};val city=locations.currentId()
        val current=shopDao.getAllSync().filter{it.cityId==city}
        current.find{com.spisokryadom.app.util.FuzzySearch.normalize(it.name)==com.spisokryadom.app.util.FuzzySearch.normalize(value)}?.id
            ?:shopDao.insert(ShopEntity(name=value,cityId=city,displayOrder=(current.maxOfOrNull{it.displayOrder}?:-1)+1))
    }
    // Compatibility API: a dictionary addition never attaches a department to stores.
    suspend fun addDepartmentInCity(name:String,city:Long)=db.withTransaction {
        require(db.cityDao().getById(city)!=null){"Город удалён"};departmentDao.ensureType(name)
    }
    suspend fun moveShop(id:Long,delta:Int)=db.withTransaction{
        val shop=shopDao.getById(id)?:return@withTransaction
        val list=shopDao.getAllSync().filter{it.cityId==shop.cityId}.sortedWith(compareBy<ShopEntity>{it.displayOrder}.thenBy{it.name}).toMutableList()
        val i=list.indexOfFirst{it.id==id};val j=i+delta
        if(i>=0&&j in list.indices){list.add(j,list.removeAt(i));shopDao.updateAll(list.mapIndexed{idx,s->s.copy(displayOrder=idx,manualOrderOverride=if(s.id==id||s.id==list[i].id)true else s.manualOrderOverride)})}
    }
    suspend fun movePurchasePlace(shopId:Long,branchId:Long?,targetShopId:Long,targetBranchId:Long?)=db.withTransaction {
        if(shopId==targetShopId) {
            if(branchId==null||targetBranchId==null)return@withTransaction
            val points=db.shopBranchDao().forShop(shopId).toMutableList()
            val from=points.indexOfFirst{it.id==branchId};val to=points.indexOfFirst{it.id==targetBranchId}
            if(from>=0&&to>=0){points.add(to,points.removeAt(from));points.forEachIndexed{i,p->db.shopBranchDao().update(p.copy(displayOrder=i))};shopDao.markManualOrderOverride(shopId)}
        } else {
            val shop=shopDao.getById(shopId)?:return@withTransaction
            val list=shopDao.getAllSync().filter{it.cityId==shop.cityId}.sortedWith(compareBy<ShopEntity>{it.displayOrder}.thenBy{it.name}.thenBy{it.id}).toMutableList()
            val from=list.indexOfFirst{it.id==shopId};val to=list.indexOfFirst{it.id==targetShopId}
            if(from>=0&&to>=0){list.add(to,list.removeAt(from));shopDao.updateAll(list.mapIndexed{i,p->p.copy(displayOrder=i,manualOrderOverride=if(p.id==shopId||p.id==targetShopId)true else p.manualOrderOverride)})}
        }
    }
    suspend fun markManualOrderOverride(id: Long)=shopDao.markManualOrderOverride(id)
    fun creationPresets()=db.creationPresetDao().observeShops()
    suspend fun purchaseMode(shopId:Long)=db.creationPresetDao().shopMode(shopId)?:"inherit"
    fun getCityShops(): Flow<List<ShopEntity>> = locations.cityShops
    fun getAllShops(): Flow<List<ShopEntity>> = shopDao.getAllOrdered()
    fun getShopsForDisplay():Flow<List<ShopEntity>> = kotlinx.coroutines.flow.combine(getAllShops(),observeBranches(),venues()){shops,b,v->
        val labels=com.spisokryadom.app.domain.PlaceLabels(b,v)
        shops.map{it.copy(name=labels.shop(it.id,it.name))}
    }
    suspend fun getAllShopsSync(): List<ShopEntity> = shopDao.getAllSync()
    suspend fun getShopById(id: Long): ShopEntity? = shopDao.getById(id)
    suspend fun insertShop(shop:ShopEntity):Long=db.withTransaction{require(shop.name.isNotBlank());require(db.cityDao().getById(shop.cityId)!=null);shopDao.insert(shop)}
    suspend fun updateShop(shop:ShopEntity)=db.withTransaction{require(shop.name.isNotBlank());require(db.cityDao().getById(shop.cityId)!=null);shopDao.update(shop)}
    suspend fun deleteShop(shop: ShopEntity) = withCatalogueEdits(db,catalogueEdits){db.withTransaction{shopDao.delete(shop)}}

    fun getDepartments(shopId: Long): Flow<List<ShopDepartmentEntity>> =
        departmentDao.getByShopId(shopId)

    fun getAllDepartments(): Flow<List<ShopDepartmentEntity>> = departmentDao.getAll()

    suspend fun getDepartmentById(id: Long): ShopDepartmentEntity? = departmentDao.getById(id)
    suspend fun getDepartmentsByShopIdSync(shopId: Long): List<ShopDepartmentEntity> =
        departmentDao.getByShopIdSync(shopId)
    suspend fun insertDepartment(dept: ShopDepartmentEntity): Long = departmentDao.insert(dept)
    suspend fun updateDepartment(dept: ShopDepartmentEntity) = departmentDao.update(dept)
    suspend fun deleteDepartment(dept: ShopDepartmentEntity) = withCatalogueEdits(db,catalogueEdits) {  db.withTransaction {
        val current=departmentDao.getById(dept.id)?:return@withTransaction
        val before=db.shoppingListEntryDao().getAllSync().filter{!it.isBought&&it.assignedShopId==current.shopId}
        departmentDao.delete(current)
        refreshPendingDepartments(current.shopId,before)
    } }
    fun getDistinctDepartmentNames(): Flow<List<String>> = departmentDao.getDistinctNames()

    suspend fun updateDepartments(departments: List<ShopDepartmentEntity>) =
        departmentDao.updateAll(departments)

    suspend fun updateShops(shops: List<ShopEntity>) = shopDao.updateAll(shops)

    fun departmentTypes()=departmentDao.observeTypes().map{com.spisokryadom.app.util.alphabetical(it){v->v.name}}
    suspend fun createDepartmentType(name:String)=departmentDao.ensureType(name)
    suspend fun moveDepartmentType(id:Long,delta:Int)=db.withTransaction {
        require(delta in -1..1)
        val list=departmentDao.types().filter{it.isFavorite}.sortedWith(compareBy<com.spisokryadom.app.data.entity.DepartmentTypeEntity>{it.favoriteOrder}.thenBy{com.spisokryadom.app.util.FuzzySearch.normalize(it.name)}.thenBy{it.id}).toMutableList();val i=list.indexOfFirst{it.id==id};val j=i+delta
        if(i>=0&&j in list.indices){list.add(j,list.removeAt(i));list.forEachIndexed{n,v->departmentDao.updateType(v.copy(favoriteOrder=n))}}
    }
    suspend fun favoriteDepartmentType(id:Long,value:Boolean)=db.withTransaction {
        val current=departmentDao.type(id)?:error("Группа магазина удалена")
        val order=if(value)(departmentDao.types().filter{it.isFavorite}.maxOfOrNull{it.favoriteOrder}?:-1).let{require(it<Int.MAX_VALUE){"Слишком большой порядок избранного"};it+1} else 0
        departmentDao.updateType(current.copy(isFavorite=value,favoriteOrder=if(current.isFavorite==value)current.favoriteOrder else order))
    }
    suspend fun alphabetizeDepartmentTypes()=db.withTransaction {
        departmentDao.types().sortedBy{com.spisokryadom.app.util.FuzzySearch.normalize(it.name)}.forEachIndexed{i,v->departmentDao.updateType(v.copy(displayOrder=i))}
    }
    suspend fun styleDepartmentType(id:Long,accent:String,icon:String)=db.withTransaction {departmentDao.type(id)?.let{departmentDao.updateType(it.copy(accent=accent,iconKey=icon))}}
    suspend fun renameDepartmentGlobally(typeId:Long,newName:String)=withCatalogueEdits(db,catalogueEdits){db.withTransaction{departmentDao.renameType(typeId,newName)}}
    suspend fun deleteDepartmentGlobally(typeId:Long)= withCatalogueEdits(db,catalogueEdits) { db.withTransaction{
        val affected=departmentDao.getAllSync().filter{it.typeId==typeId}.map{it.shopId}.toSet()
        val before=db.shoppingListEntryDao().getAllSync().filter{!it.isBought&&it.assignedShopId in affected}
        departmentDao.deleteType(typeId)
        affected.forEach{refreshPendingDepartments(it,before)}
    } }

    fun observeBranches()=db.shopBranchDao().observeAll()
    suspend fun branches(shopId:Long)=db.shopBranchDao().forShop(shopId)
    private suspend fun saveBranches(shopId:Long,branches:List<com.spisokryadom.app.data.entity.ShopBranchEntity>) {
        val dao=db.shopBranchDao()
        val old=dao.forShop(shopId)
        val retained=branches.filter{it.id>0}.map{it.id}.toSet()
        require(branches.all{it.address.isNotBlank()}){"Укажите адрес точки"}
        require(branches.map{it.address.trim().lowercase()}.distinct().size==branches.size){"Адрес добавлен дважды"}
        val removed=old.filter{it.id !in retained}.map{it.id}.toSet()
        val availability=dao.allAvailability()
        db.productShopLinkDao().getAllSync().filter{it.shopId==shopId&&it.restrictBranches}.forEach{link->
            require(availability.any{it.productId==link.productId&&it.branchId in retained}){"В удаляемой точке есть уникальные товары. Сначала измените их точки продажи в карточке товара"}
        }
        removed.forEach{dao.delete(it)}
        branches.forEachIndexed{i,b->
            val value=b.copy(shopId=shopId,address=b.address.trim(),displayOrder=i)
            if(b.id<=0)dao.insert(value.copy(id=0))else{require(old.any{it.id==b.id}){"Точка относится к другому магазину"};dao.update(value)}
        }
    }

    fun observeBranchOrders()=db.retailStructureDao().observeDepartmentOrder()
    suspend fun branchOrders()=db.retailStructureDao().departmentOrder()
    suspend fun updateVenue(venue:com.spisokryadom.app.data.entity.VenueEntity)=db.withTransaction{
        require(venue.name.isNotBlank());require(db.retailStructureDao().venue(venue.id)!=null)
        db.retailStructureDao().updateVenue(venue.copy(name=venue.name.trim(),address=venue.address?.trim()?.ifBlank{null},note=venue.note?.trim()?.ifBlank{null}))
    }
    suspend fun deleteVenue(id:Long)=db.withTransaction{db.retailStructureDao().deleteVenue(id)}
    fun networks()=db.retailStructureDao().observeNetworks()
    fun venues()=db.retailStructureDao().observeVenues()
    suspend fun ensureNetwork(name:String):Long=db.withTransaction{
        require(name.isNotBlank());val key=com.spisokryadom.app.util.FuzzySearch.normalize(name)
        db.retailStructureDao().networks().find{com.spisokryadom.app.util.FuzzySearch.normalize(it.name)==key}?.id
            ?:db.retailStructureDao().insertNetwork(com.spisokryadom.app.data.entity.RetailNetworkEntity(name=name.trim()))
    }
    suspend fun ensureVenue(cityId:Long,name:String):Long=db.withTransaction{
        require(name.isNotBlank());require(db.cityDao().getById(cityId)!=null)
        val key=com.spisokryadom.app.util.FuzzySearch.normalize(name)
        db.retailStructureDao().venues().find{it.cityId==cityId&&com.spisokryadom.app.util.FuzzySearch.normalize(it.name)==key}?.id
            ?:db.retailStructureDao().insertVenue(com.spisokryadom.app.data.entity.VenueEntity(cityId=cityId,name=name.trim()))
    }
    /** Explicit conversion, never a heuristic based on a market-looking name.
     * Each pavilion becomes a retailer with the SAME physical outlet ID.
     * Products and purchase IDs, notes, photos, and quantities are preserved atomically. */
    suspend fun convertToVenue(shopId:Long):Long= withCatalogueEdits(db,catalogueEdits) { db.withTransaction{
        val shop=shopDao.getById(shopId)?:error("Магазин удалён")
        val purchaseMode=db.creationPresetDao().shopMode(shopId)
        val points=db.shopBranchDao().forShop(shopId)
        require(points.isNotEmpty()){ "Добавьте хотя бы одну точку" }
        val venueId=db.retailStructureDao().insertVenue(com.spisokryadom.app.data.entity.VenueEntity(cityId=shop.cityId,name=shop.name,address=shop.address,note=shop.note))
        val departments=departmentDao.getByShopIdSync(shopId)
        val allLinks=db.productShopLinkDao().getAllSync()
        val links=allLinks.filter{it.shopId==shopId}
        val oldShops=shopDao.getAllSync().associateBy{it.id}
        val products=db.productDao().getAllSync().filter{p->links.any{it.productId==p.id}}
        val pointPriorities=db.shopBranchDao().priorities().filter{it.shopId==shopId}.associate{(it.productId to it.branchId) to it.displayOrder}
        val availability=db.shopBranchDao().allAvailability().groupBy{it.productId}
        fun preferredPoints(link:com.spisokryadom.app.data.entity.ProductShopLinkEntity?)=points
            .filter{link==null||!link.restrictBranches||availability[link.productId].orEmpty().any{a->a.branchId==it.id}}
            .sortedWith(compareBy<com.spisokryadom.app.data.entity.ShopBranchEntity>{point->link?.let{pointPriorities[it.productId to point.id]}?:Int.MAX_VALUE}.thenBy{it.displayOrder}.thenBy{it.id})
        val pointDepartments=db.retailStructureDao().productDepartments().filter{it.shopId==shopId}
        val purchases=db.shoppingListEntryDao().getAllSync().filter{it.assignedShopId==shopId}
        // Detach assignments while changing the physical outlet's owner.
        purchases.forEach{db.shoppingListEntryDao().update(it.copy(assignedBranchId=null,assignedDepartmentId=null))}
        links.forEach{db.productShopLinkDao().delete(it)}
        val owners=mutableMapOf<Long,Long>();val deptMap=mutableMapOf<Pair<Long,Long>,Long>()
        points.forEachIndexed{index,point->
            val owner=shopDao.insert(ShopEntity(name=point.address,cityId=shop.cityId,displayOrder=shop.displayOrder+index,note=point.note,showUrgentInCollapsed=shop.showUrgentInCollapsed,boostUrgentShop=shop.boostUrgentShop,shoppingBackground=shop.shoppingBackground,iconKey=shop.iconKey,logoUri=shop.logoUri))
            purchaseMode?.let{db.creationPresetDao().setShop(com.spisokryadom.app.data.entity.ShopCreationPreset(owner,it))}
            owners[point.id]=owner
            db.shopBranchDao().update(point.copy(shopId=owner,venueId=venueId,displayOrder=0))
            shopDao.update(shopDao.getById(owner)!!.copy(address=point.address))
            departments.forEach{dept->deptMap[point.id to dept.id]=departmentDao.insert(dept.copy(id=0,shopId=owner))}
            links.filter{!it.restrictBranches||availability[it.productId].orEmpty().any{a->a.branchId==point.id}}.forEach{link->
                db.productShopLinkDao().insert(link.copy(id=0,shopId=owner,departmentId=link.departmentId?.let{deptMap[point.id to it]},restrictBranches=false))
            }
        }
        // A point preference becomes a retailer preference when points are split
        // into independent sellers. Expand the old retailer in each product's
        // effective route instead of giving every new link the same priority.
        val newShops=shopDao.getAllSync().associateBy{it.id}
        products.forEach{product->
            val oldOrdering=if(product.preferCityShopOrder)
                compareBy<com.spisokryadom.app.data.entity.ProductShopLinkEntity>{oldShops[it.shopId]?.displayOrder?:Int.MAX_VALUE}.thenBy{it.priority}.thenBy{it.shopId}
            else compareBy<com.spisokryadom.app.data.entity.ProductShopLinkEntity>{it.priority}.thenBy{oldShops[it.shopId]?.displayOrder?:Int.MAX_VALUE}.thenBy{it.shopId}
            val current=db.productShopLinkDao().getLinksForProductSync(product.id).associateBy{it.shopId}
            val expanded=allLinks.filter{it.productId==product.id}.sortedWith(oldOrdering).flatMap{link->
                if(link.shopId==shopId)preferredPoints(link).map{current.getValue(owners.getValue(it.id))}
                else listOf(current.getValue(link.shopId))
            }.mapIndexed{index,link->link.copy(priority=index+1)}
            expanded.forEach{db.productShopLinkDao().update(it)}
            if(product.preferCityShopOrder){
                fun routeByCity(values:List<com.spisokryadom.app.data.entity.ProductShopLinkEntity>)=values.groupBy{newShops[it.shopId]?.cityId}.mapValues{(_,links)->links.map{it.shopId}}
                val inherited=expanded.sortedWith(compareBy<com.spisokryadom.app.data.entity.ProductShopLinkEntity>{newShops[it.shopId]?.displayOrder?:Int.MAX_VALUE}.thenBy{it.priority}.thenBy{it.shopId})
                // Keep inheritance whenever it still expresses the same route.
                // A conflicting individual pavilion preference needs an explicit
                // product route; materialize all its cities to preserve their order.
                if(routeByCity(inherited)!=routeByCity(expanded))db.productDao().update(product.copy(preferCityShopOrder=false))
            }
        }
        pointDepartments.forEach{d->
            val owner=owners.getValue(d.branchId)
            if(db.productShopLinkDao().getLinksForProductSync(d.productId).any{it.shopId==owner})db.retailStructureDao().setProductDepartment(d.copy(shopId=owner))
        }
        purchases.forEach{purchase->
            val link=links.find{it.productId==purchase.productId}
            val available=preferredPoints(link)
            val point=purchase.assignedBranchId?.takeIf{it in owners}?:available.firstOrNull()?.id?:points.first().id
            val department=if(purchase.isBought)purchase.assignedDepartmentId else
                pointDepartments.find{it.productId==purchase.productId&&it.branchId==point}?.typeId?.let{type->departments.find{it.typeId==type}?.id}
                    ?:if(link!=null)link.departmentId else purchase.assignedDepartmentId
            db.shoppingListEntryDao().update(purchase.copy(assignedShopId=owners.getValue(point),assignedBranchId=point,assignedDepartmentId=department?.let{deptMap[point to it]}))
        }
        shopDao.delete(shop)
        venueId
    } }

}
