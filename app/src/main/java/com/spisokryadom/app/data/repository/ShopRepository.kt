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
    suspend fun saveWithDepartments(shop:ShopEntity, departments:List<ShopDepartmentEntity>, branches:List<com.spisokryadom.app.data.entity.ShopBranchEntity>?=null,networkName:String?=null,convertToVenue:Boolean=false,purchaseMode:String?=null,venueEdits:Map<Long,com.spisokryadom.app.data.entity.VenueEntity> = emptyMap(),venueKind:String=com.spisokryadom.app.data.entity.VenueKinds.UNSPECIFIED,appendToRoute:Boolean=false):Long = withCatalogueEdits(db,catalogueEdits) { db.withTransaction {
        require(com.spisokryadom.app.util.PurchaseBackground.valid(shop.purchaseBackground)){"Выберите фон товаров магазина"}
        require(shop.name.isNotBlank()) { "Укажите название магазина" }
        require(com.spisokryadom.app.util.AppearanceColors.validOptional(shop.shoppingBackground)){"Выберите цвет фона магазина"}
        require(db.cityDao().getById(shop.cityId)!=null) { "Город удалён" }
        // Detach old venue memberships before changing the retailer's city. Both
        // assignments are saved in this transaction; failure rolls everything back.
        val oldShop=shop.id.takeIf{it>0}?.let{shopDao.getById(it)}
        if(oldShop!=null&&oldShop.cityId!=shop.cityId&&branches!=null){
            // Venue points can share an empty street address/label. Give the
            // detached rows distinct transaction-local keys until the incoming
            // point drafts replace them; rollback restores every original field.
            val relocationKey="relocating:"+java.util.UUID.randomUUID().toString()
            db.shopBranchDao().forShop(shop.id).filter{it.venueId!=null}.forEach{point->
                db.shopBranchDao().update(point.copy(venueId=null,label="$relocationKey:${point.id}"))
            }
        }
        val ordered=if(appendToRoute&&shop.id==0L)shop.copy(displayOrder=nextShopDisplayOrder(db,shop.cityId))else shop
        val favorite=when {
            !shop.isFavorite -> ordered.copy(favoriteOrder=0)
            oldShop?.isFavorite==true -> ordered.copy(favoriteOrder=oldShop.favoriteOrder)
            else -> ordered.copy(favoriteOrder=nextShopFavoriteOrder())
        }
        val resolved=if(networkName.isNullOrBlank())favorite else if(shop.networkId==null)favorite.copy(networkId=ensureNetwork(networkName))else{
            val network=db.retailStructureDao().networks().find{it.id==shop.networkId}?:error("Сеть удалена")
            require(db.retailStructureDao().networks().none{it.id!=network.id&&com.spisokryadom.app.util.FuzzySearch.normalize(it.name)==com.spisokryadom.app.util.FuzzySearch.normalize(networkName)}){"Сеть с таким названием уже есть"}
            db.retailStructureDao().updateNetwork(network.copy(name=networkName.trim()));favorite
        }
        val id=if(shop.id==0L)shopDao.insert(resolved) else { require(shopDao.getById(shop.id)!=null);shopDao.update(resolved);shop.id }
        purchaseMode?.let{require(it in com.spisokryadom.app.util.PurchaseModes.labels);db.creationPresetDao().setShop(com.spisokryadom.app.data.entity.ShopCreationPreset(id,it))}
        val pendingBefore=db.shoppingListEntryDao().getAllSync().filter{!it.isBought&&it.assignedShopId==id}
        val retained=departments.filter{it.id>0}.map{it.id}.toSet()
        val removedTypes=departmentDao.getByShopIdSync(id).filter{it.id !in retained}.map{it.typeId}.toSet()
        departmentDao.getByShopIdSync(id).filter{it.id !in retained}.forEach{departmentDao.delete(it)}
        departments.forEachIndexed{index,dept->
            require(dept.name.isNotBlank())
            val value=dept.copy(id=dept.id.coerceAtLeast(0),shopId=id,displayOrder=index+1,name=dept.name.trim())
            if(value.id==0L)departmentDao.insert(value) else { require(departmentDao.getById(value.id)?.shopId==id);departmentDao.update(value) }
        }
        synchronizeNetworkDepartments(db,id,removedTypes-departmentDao.getByShopIdSync(id).map{it.typeId}.toSet())
        val venueIds=venueEdits.mapValues{(key,value)->
            require(key!=0L&&key==value.id){"Проверьте ссылку на рынок или ТЦ"}
            require(value.cityId==shop.cityId){"Торговый комплекс относится к другому городу"}
            if(key>0){updateVenueInTransaction(value);key}else{
                require(value.name.isNotBlank()){"Укажите название рынка или ТЦ"}
                require(db.retailStructureDao().venues().none{it.cityId==value.cityId&&com.spisokryadom.app.util.FuzzySearch.normalize(it.name)==com.spisokryadom.app.util.FuzzySearch.normalize(value.name)}){"Рынок или ТЦ с таким названием уже есть"}
                ensureVenue(value.cityId,value.name).also{newId->updateVenueInTransaction(value.copy(id=newId))}
            }
        }
        if(branches!=null)saveBranches(id,branches.map{branch->
            val venueId=venueIds[branch.venueId]?:branch.venueId
            venueId?.let{require(db.retailStructureDao().venue(it)?.cityId==shop.cityId){"Торговый комплекс относится к другому городу"}}
            com.spisokryadom.app.domain.OutletLocations.legacy(branch.copy(venueId=venueId),venueId?.let{db.retailStructureDao().venue(it)},shop.name)
        })
        else if(db.shopBranchDao().forShop(id).isEmpty()&&!shop.address.isNullOrBlank())db.shopBranchDao().insert(com.spisokryadom.app.data.entity.ShopBranchEntity(shopId=id,address=shop.address.trim()))
        if(branches!=null)syncPrimaryAddress(id)
        refreshPendingDepartments(id,pendingBefore)
        if(convertToVenue)this@ShopRepository.convertToVenue(id,venueKind)
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
    suspend fun createInCurrentCity(name:String):Long=db.withTransaction {
        val value=name.trim();require(value.isNotBlank()){"Укажите название магазина"};val city=locations.currentId()
        val current=shopDao.getAllSync().filter{it.cityId==city}
        current.find{com.spisokryadom.app.util.FuzzySearch.normalize(it.name)==com.spisokryadom.app.util.FuzzySearch.normalize(value)}?.id
            ?:shopDao.insert(ShopEntity(name=value,cityId=city,displayOrder=nextShopDisplayOrder(db,city)))
    }
    private fun favoriteShopOrder()=compareBy<ShopEntity>{it.favoriteOrder}
        .thenBy{com.spisokryadom.app.util.FuzzySearch.normalize(it.name)}.thenBy{it.id}
    private suspend fun nextShopFavoriteOrder():Int {
        val favorites=shopDao.getAllSync().filter{it.isFavorite}.sortedWith(favoriteShopOrder())
        val order=com.spisokryadom.app.util.nextFavoriteOrder(favorites.map{it.favoriteOrder})
        if(order.compact)shopDao.updateAll(favorites.mapIndexed{i,value->value.copy(favoriteOrder=i)})
        return order.next
    }
    suspend fun moveFavoriteShop(id:Long,delta:Int)=db.withTransaction {
        require(delta==1||delta== -1)
        val current=shopDao.getById(id)?:error("Магазин удалён")
        val favorites=shopDao.getAllSync().filter{it.isFavorite&&it.cityId==current.cityId}
            .sortedWith(favoriteShopOrder()).toMutableList()
        val from=favorites.indexOfFirst{it.id==id};val to=from+delta
        if(from>=0&&to in favorites.indices){
            favorites.add(to,favorites.removeAt(from))
            shopDao.updateAll(favorites.mapIndexed{i,value->value.copy(favoriteOrder=i)})
        }
    }
    // Compatibility API: a dictionary addition never attaches a department to stores.
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
        shops.map{shop->shop.copy(name=labels.shop(shop.id,shop.name),address=b.filter{it.shopId==shop.id}.minWithOrNull(compareBy<com.spisokryadom.app.data.entity.ShopBranchEntity>{it.displayOrder}.thenBy{it.id})?.let{labels.address(it)}?:shop.address)}
    }
    suspend fun getAllShopsSync(): List<ShopEntity> = shopDao.getAllSync()
    suspend fun getShopById(id: Long): ShopEntity? = shopDao.getById(id)
    suspend fun deleteShop(shop: ShopEntity) = withCatalogueEdits(db,catalogueEdits){db.withTransaction{shopDao.delete(shop)}}


    fun getAllDepartments(): Flow<List<ShopDepartmentEntity>> = departmentDao.getAll()

    suspend fun getDepartmentById(id: Long): ShopDepartmentEntity? = departmentDao.getById(id)
    suspend fun getDepartmentsByShopIdSync(shopId: Long): List<ShopDepartmentEntity> =
        departmentDao.getByShopIdSync(shopId)
    suspend fun updateDepartment(dept: ShopDepartmentEntity) = withCatalogueEdits(db,catalogueEdits) { db.withTransaction {
        departmentDao.update(dept)
        synchronizeNetworkDepartments(db,dept.shopId)
    } }
    suspend fun deleteDepartment(dept: ShopDepartmentEntity) = withCatalogueEdits(db,catalogueEdits) {  db.withTransaction {
        val current=departmentDao.getById(dept.id)?:return@withTransaction
        val before=db.shoppingListEntryDao().getAllSync().filter{!it.isBought&&it.assignedShopId==current.shopId}
        departmentDao.delete(current)
        synchronizeNetworkDepartments(db,current.shopId,setOf(current.typeId))
        refreshPendingDepartments(current.shopId,before)
    } }
    fun getDistinctDepartmentNames(): Flow<List<String>> = departmentDao.getDistinctNames()



    fun departmentTypes()=departmentDao.observeTypes().map{com.spisokryadom.app.util.alphabetical(it){v->v.name}}
    suspend fun createDepartmentType(name:String)=departmentDao.ensureType(name)
    suspend fun moveDepartmentType(id:Long,delta:Int)=db.withTransaction {
        require(delta in -1..1)
        val list=departmentDao.types().filter{it.isFavorite}.sortedWith(compareBy<com.spisokryadom.app.data.entity.DepartmentTypeEntity>{it.favoriteOrder}.thenBy{com.spisokryadom.app.util.FuzzySearch.normalize(it.name)}.thenBy{it.id}).toMutableList();val i=list.indexOfFirst{it.id==id};val j=i+delta
        if(i>=0&&j in list.indices){list.add(j,list.removeAt(i));list.forEachIndexed{n,v->departmentDao.updateType(v.copy(favoriteOrder=n))}}
    }
    suspend fun favoriteDepartmentType(id:Long,value:Boolean)=db.withTransaction {
        val current=departmentDao.type(id)?:error("Группа магазина удалена")
        if(current.isFavorite==value)return@withTransaction
        val favorites=departmentDao.types().filter{it.isFavorite}
            .sortedWith(compareBy<com.spisokryadom.app.data.entity.DepartmentTypeEntity>{it.favoriteOrder}.thenBy{com.spisokryadom.app.util.FuzzySearch.normalize(it.name)}.thenBy{it.id})
        val order=com.spisokryadom.app.util.nextFavoriteOrder(favorites.map{it.favoriteOrder})
        if(value&&order.compact)favorites.forEachIndexed{i,v->departmentDao.updateType(v.copy(favoriteOrder=i))}
        departmentDao.updateType(current.copy(isFavorite=value,favoriteOrder=if(value)order.next else 0))
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
    private suspend fun syncPrimaryAddress(shopId:Long){
        val shop=shopDao.getById(shopId)?:return
        val point=db.shopBranchDao().forShop(shopId).firstOrNull()
        val address=point?.let{com.spisokryadom.app.domain.PlaceLabels(venues=db.retailStructureDao().venues()).address(it)}?.ifBlank{null}
        shopDao.update(shop.copy(address=address))
    }
    private suspend fun saveBranches(shopId:Long,branches:List<com.spisokryadom.app.data.entity.ShopBranchEntity>) {
        val dao=db.shopBranchDao()
        val old=dao.forShop(shopId)
        val retained=branches.filter{it.id>0}.map{it.id}.toSet()
        require(com.spisokryadom.app.domain.OutletLocations.valid(branches)){"Укажите адрес или название точки; одинаковые точки не должны повторяться"}
        val removed=old.filter{it.id !in retained}.map{it.id}.toSet()
        val availability=dao.allAvailability()
        db.productShopLinkDao().getAllSync().filter{it.shopId==shopId&&it.restrictBranches}.forEach{link->
            require(availability.any{it.productId==link.productId&&it.branchId in retained}){"В удаляемой точке есть уникальные товары. Сначала измените их точки продажи в карточке товара"}
        }
        removed.forEach{dao.delete(it)}
        branches.forEachIndexed{i,b->
            val value=b.copy(shopId=shopId,address=b.address.trim(),label=b.label.trim(),displayOrder=i)
            if(b.id<=0)dao.insert(value.copy(id=0))else{require(old.any{it.id==b.id}){"Точка относится к другому магазину"};dao.update(value)}
        }
    }

    suspend fun updateVenue(venue:com.spisokryadom.app.data.entity.VenueEntity)=withCatalogueEdits(db,catalogueEdits){db.withTransaction{updateVenueInTransaction(venue)}}
    private suspend fun updateVenueInTransaction(venue:com.spisokryadom.app.data.entity.VenueEntity){
        require(venue.name.isNotBlank()){"Укажите название рынка или ТЦ"}
        val old=db.retailStructureDao().venue(venue.id)?:error("Рынок или ТЦ удалён")
        require(com.spisokryadom.app.data.entity.VenueKinds.valid(venue.kind)){"Выберите тип места"}
        require(db.cityDao().getById(venue.cityId)!=null){"Город удалён"}
        require(db.retailStructureDao().venues().none{it.id!=venue.id&&it.cityId==venue.cityId&&com.spisokryadom.app.util.FuzzySearch.normalize(it.name)==com.spisokryadom.app.util.FuzzySearch.normalize(venue.name)}){"Рынок или ТЦ с таким названием уже есть"}
        if(old.cityId!=venue.cityId)relocateVenue(db,old,venue.cityId)
        db.retailStructureDao().updateVenue(venue.copy(name=venue.name.trim(),address=venue.address?.trim()?.ifBlank{null},note=venue.note?.trim()?.ifBlank{null}))
        db.shopBranchDao().getAll().filter{it.venueId==venue.id}.map{it.shopId}.distinct().forEach{syncPrimaryAddress(it)}
    }
    suspend fun deleteVenue(id:Long)=db.withTransaction{
        val venue=db.retailStructureDao().venue(id)?:return@withTransaction
        val points=db.shopBranchDao().getAll().filter{it.venueId==id}
        points.forEach{point->
            db.shopBranchDao().update(point.copy(address=venue.address.orEmpty(),label=point.label.ifBlank{if(venue.address.isNullOrBlank())venue.name else ""},venueId=null))
        }
        db.retailStructureDao().deleteVenue(id)
        points.map{it.shopId}.distinct().forEach{syncPrimaryAddress(it)}
    }
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
    suspend fun convertToVenue(shopId:Long,kind:String=com.spisokryadom.app.data.entity.VenueKinds.UNSPECIFIED):Long= withCatalogueEdits(db,catalogueEdits) { db.withTransaction{
        var shop=shopDao.getById(shopId)?:error("Магазин удалён")
        val purchaseMode=db.creationPresetDao().shopMode(shopId)
        val points=db.shopBranchDao().forShop(shopId)
        require(points.isNotEmpty()){ "Добавьте хотя бы одну точку" }
        if(shop.displayOrder.toLong()+points.size>Int.MAX_VALUE){
            nextShopDisplayOrder(db,shop.cityId,points.size+1)
            shop=shopDao.getById(shopId)?:error("Магазин удалён")
        }
        require(points.all{it.venueId==null}){"Магазин уже содержит точки в рынке или ТЦ"}
        require(com.spisokryadom.app.data.entity.VenueKinds.valid(kind)){"Выберите тип места"}
        require(db.retailStructureDao().venues().none{it.cityId==shop.cityId&&com.spisokryadom.app.util.FuzzySearch.normalize(it.name)==com.spisokryadom.app.util.FuzzySearch.normalize(shop.name)}){"Торговое место с таким названием уже есть"}
        val venueId=db.retailStructureDao().insertVenue(com.spisokryadom.app.data.entity.VenueEntity(cityId=shop.cityId,name=shop.name,address=null,note=shop.note,kind=kind))
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
            val owner=shopDao.insert(ShopEntity(name=point.label.ifBlank{point.address},cityId=shop.cityId,displayOrder=shop.displayOrder+index,note=point.note,showUrgentInCollapsed=shop.showUrgentInCollapsed,boostUrgentShop=shop.boostUrgentShop,shoppingBackground=shop.shoppingBackground,purchaseBackground=shop.purchaseBackground,iconKey=shop.iconKey,logoUri=shop.logoUri,isFavorite=shop.isFavorite,favoriteOrder=if(shop.isFavorite)nextShopFavoriteOrder()else 0))
            purchaseMode?.let{db.creationPresetDao().setShop(com.spisokryadom.app.data.entity.ShopCreationPreset(owner,it))}
            owners[point.id]=owner
            db.shopBranchDao().update(point.copy(shopId=owner,venueId=venueId,address="",label="",displayOrder=0))
            shopDao.update(shopDao.getById(owner)!!.copy(address=null))
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
