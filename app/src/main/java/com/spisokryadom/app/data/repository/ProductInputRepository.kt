package com.spisokryadom.app.data.repository

import androidx.room.withTransaction
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.db.StandardCategories
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.util.FuzzySearch
import com.spisokryadom.app.domain.ProductHints
import kotlinx.coroutines.flow.map

class ProductInputRepository(private val db:AppDatabase) {
    private fun active(value:ProductInputSettings)=value.copy(recipientEnabled=value.recipientEnabled&&value.recipientId!=null)
    fun observeSettings()=db.productInputDao().observeSettings().map{active(it?:ProductInputSettings())}
    suspend fun settings()=active(db.productInputDao().settings()?:ProductInputSettings())
    suspend fun updateSettings(change:(ProductInputSettings)->ProductInputSettings)=db.withTransaction {
        val next=change(settings()).copy(id=1)
        require(next.recipientId==null||db.recipientDao().getById(next.recipientId)!=null){"Получатель удалён"}
        db.productInputDao().settings(next.copy(recipientEnabled=next.recipientEnabled&&next.recipientId!=null))
    }
    suspend fun recipient(groupId:Long?,manual:Boolean,value:Long?):Long? {
        if(manual)return value
        val group=db.creationPresetDao().groups().find{it.groupId==groupId}
        if(group!=null)return group.recipientId
        val defaults=settings()
        return defaults.recipientId.takeIf{defaults.recipientEnabled}
    }
    suspend fun department(productId:Long):ProductDefaultDepartment? {
        db.productInputDao().department(productId)?.let{return it}
        // Older products keep all placements. Their first saved choice supplies future links only.
        val types=db.productShopLinkDao().getLinksForProductSync(productId).mapNotNull{it.departmentId?.let{d->db.shopDepartmentDao().getById(d)?.typeId}}.distinct()
        val type=types.singleOrNull()?:return null
        return ProductDefaultDepartment(productId,type)
    }
    suspend fun setDepartment(productId:Long,typeId:Long?) {
        require(db.productDao().getById(productId)!=null){"Товар удалён"}
        require(typeId==null||db.shopDepartmentDao().types().any{it.id==typeId}){"Группа магазина удалена"}
        db.productInputDao().department(ProductDefaultDepartment(productId,typeId))
    }
    suspend fun resolveDraftType(typeId:Long?,drafts:List<ShopDepartmentEntity>):Long? =
        if(typeId!=null&&typeId<0){
            val draft=drafts.find{it.id==typeId}?:error("Группа магазина больше недоступна")
            db.shopDepartmentDao().ensureType(draft.name)
        }else typeId
    suspend fun copyDepartment(sourceId:Long,targetId:Long) {
        department(sourceId)?.let{db.productInputDao().department(it.copy(productId=targetId))}
    }
    suspend fun placement(shopId:Long,typeId:Long):Long {
        val type=db.shopDepartmentDao().types().find{it.id==typeId}?:error("Группа магазина удалена")
        require(db.shopDao().getById(shopId)!=null){"Магазин удалён"}
        db.shopDepartmentDao().getByShopIdSync(shopId).find{it.typeId==typeId}?.let{return it.id}
        val id=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=shopId,name=type.name,typeId=typeId,displayOrder=nextDepartmentDisplayOrder(db,shopId)))
        synchronizeNetworkDepartments(db,shopId)
        return id
    }
    suspend fun inheritedPlacement(productId:Long,shopId:Long):Long? {
        if(!settings().inheritDepartment)return null
        val rule=department(productId)?:return null
        return rule.typeId?.let{placement(shopId,it)}
    }
    data class Suggestion(val categoryId:Long?,val unit:String?,val departmentTypeId:Long?)
    private fun classifierId(name:String,standards:List<StandardCategories.Category>,rows:List<Pair<Long,String>>):Long? {
        val key=FuzzySearch.normalize(name)
        rows.firstOrNull{FuzzySearch.normalize(it.second)==key}?.let{return it.first}
        // Installation preserves these historical names instead of adding a canonical duplicate.
        val aliases=standards.find{FuzzySearch.normalize(it.name)==key}?.aliases.orEmpty().map(FuzzySearch::normalize).toSet()
        return rows.filter{FuzzySearch.normalize(it.second) in aliases}.singleOrNull()?.first
    }
    suspend fun suggest(name:String):Suggestion {
        val policy=settings()
        val hint=if(policy.prefillEnabled)ProductHints.match(name)else null
        val category=hint?.takeIf{policy.prefillCategory}?.let{h->classifierId(h.category,StandardCategories.groups,db.productGroupDao().getAllSync().map{it.id to it.name})}
        val department=hint?.takeIf{policy.prefillDepartment}?.let{h->classifierId(h.department,StandardCategories.departments,db.shopDepartmentDao().types().map{it.id to it.name})}
        return Suggestion(category,hint?.unit?.takeIf{policy.prefillUnit},department)
    }
}
