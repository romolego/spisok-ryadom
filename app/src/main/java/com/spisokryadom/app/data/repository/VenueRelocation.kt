package com.spisokryadom.app.data.repository

import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*

/** Called inside the catalogue transaction. Never relocates unrelated street/mall outlets.
 * A city-specific profile is split only when it also owns outlets outside this venue.
 * Physical outlet and purchase IDs stay unchanged; departments and product links are remapped.
 */
internal suspend fun relocateVenue(db:AppDatabase,venue:VenueEntity,cityId:Long){
    val points=db.shopBranchDao().getAll().filter{it.venueId==venue.id}
    val token="venue-move:"+java.util.UUID.randomUUID()
    // Temporarily detach memberships so both city ownership triggers remain enabled.
    points.forEach{db.shopBranchDao().update(it.copy(venueId=null,label="$token:${it.id}"))}
    db.retailStructureDao().updateVenue(venue.copy(cityId=cityId))
    for((shopId,moved) in points.groupBy{it.shopId}){
        val shop=db.shopDao().getById(shopId)?:error("Магазин удалён")
        val movedIds=moved.map{it.id}.toSet()
        val remaining=db.shopBranchDao().forShop(shopId).filter{it.id !in movedIds}
        if(remaining.isEmpty()){
            db.shopDao().update(shop.copy(cityId=cityId))
            moved.forEach{db.shopBranchDao().update(it)}
            continue
        }
        val order=nextShopDisplayOrder(db,cityId)
        val owner=db.shopDao().insert(shop.copy(id=0,cityId=cityId,displayOrder=order,address=venue.address))
        db.creationPresetDao().shopMode(shopId)?.let{db.creationPresetDao().setShop(ShopCreationPreset(owner,it))}
        val deptMap=db.shopDepartmentDao().getByShopIdSync(shopId).associate{it.id to db.shopDepartmentDao().insert(it.copy(id=0,shopId=owner))}
        val links=db.productShopLinkDao().getAllSync().filter{it.shopId==shopId}
        val availability=db.shopBranchDao().allAvailability().filter{it.shopId==shopId}
        val priorities=db.shopBranchDao().priorities().filter{it.shopId==shopId&&it.branchId in movedIds}
        val overrides=db.retailStructureDao().productDepartments().filter{it.shopId==shopId&&it.branchId in movedIds}
        val purchases=db.shoppingListEntryDao().getAllSync().filter{it.assignedShopId==shopId&&it.assignedBranchId in movedIds}
        val newLinks=links.filter{l->!l.restrictBranches||availability.any{it.productId==l.productId&&it.branchId in movedIds}}
        newLinks.forEach{db.productShopLinkDao().insert(it.copy(id=0,shopId=owner,departmentId=it.departmentId?.let{d->deptMap.getValue(d)}))}
        purchases.forEach{db.shoppingListEntryDao().update(it.copy(assignedBranchId=null,assignedDepartmentId=null))}
        // Composite FKs have NO ACTION on owner changes. Remove only captured point-owned rows.
        moved.forEach{p->
            db.openHelper.writableDatabase.execSQL("DELETE FROM product_branch_availability WHERE branchId=?",arrayOf(p.id))
            db.openHelper.writableDatabase.execSQL("DELETE FROM product_branch_priorities WHERE branchId=?",arrayOf(p.id))
            db.openHelper.writableDatabase.execSQL("DELETE FROM product_branch_departments WHERE branchId=?",arrayOf(p.id))
            db.shopBranchDao().update(p.copy(shopId=owner))
        }
        availability.filter{it.branchId in movedIds}.forEach{db.shopBranchDao().insertAvailability(it.copy(shopId=owner))}
        val linkedProducts=newLinks.map{it.productId}.toSet()
        // Ineligible legacy point hints have no effect once their retailer link stays behind.
        priorities.filter{it.productId in linkedProducts}.forEach{db.shopBranchDao().setPriority(it.copy(shopId=owner))}
        overrides.filter{it.productId in linkedProducts}.forEach{db.retailStructureDao().setProductDepartment(it.copy(shopId=owner))}
        purchases.forEach{db.shoppingListEntryDao().update(it.copy(assignedShopId=owner,assignedDepartmentId=it.assignedDepartmentId?.let{d->deptMap.getValue(d)}))}
        links.filter{l->l.restrictBranches&&availability.none{it.productId==l.productId&&it.branchId !in movedIds}}.forEach{db.productShopLinkDao().delete(it)}
        val first=remaining.minWith(compareBy<ShopBranchEntity>{it.displayOrder}.thenBy{it.id})
        db.shopDao().update(shop.copy(address=com.spisokryadom.app.domain.PlaceLabels(venues=db.retailStructureDao().venues()).address(first).ifBlank{null}))
    }
}
