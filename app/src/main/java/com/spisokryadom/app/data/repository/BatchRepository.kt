package com.spisokryadom.app.data.repository

import androidx.room.withTransaction
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*

sealed interface ProductBatchChange {
 data class Recipient(val id:Long?):ProductBatchChange
 data class Group(val id:Long?):ProductBatchChange
 data class Favorite(val value:Boolean):ProductBatchChange
 data class Shops(val ids:Set<Long>):ProductBatchChange
 data class Department(val shopId:Long,val typeId:Long?):ProductBatchChange
}
enum class PurchaseBatchChange { BOUGHT, RETURN, URGENT, NOT_URGENT, DELETE }

/** Every batch reads fresh rows, validates the entire set and commits once. */
class BatchRepository(private val db:AppDatabase,private val products:ProductRepository,private val basket:ShoppingListRepository, private val catalogueEdits:CatalogueEdits?=null) {
 private suspend fun products(ids:Set<Long>):List<ProductEntity> {
  require(ids.isNotEmpty()) { "Выберите товары" }
  return ids.sorted().map{db.productDao().getById(it)?:error("Один из товаров удалён. Обновите выбор")}
 }
 suspend fun changeProducts(ids:Set<Long>,change:ProductBatchChange)= withCatalogueEdits(db,catalogueEdits) { db.withTransaction {
  val selected=products(ids)
  val all=db.productDao().getAllSync().associateBy{it.id}
  val families=db.productVariantDao().variants()
  val expanded=selected.flatMap{p->val family=families.find{it.productId==p.id}?.familyId?:p.id;families.filter{it.familyId==family}.mapNotNull{all[it.productId]}.ifEmpty{listOf(p)}}.distinctBy{it.id}
  val rows=if(change is ProductBatchChange.Department){
   selected.forEach{p->val family=families.find{it.productId==p.id}?.familyId?:p.id;require(expanded.any{member->(families.find{it.productId==member.id}?.familyId?:member.id)==family&&db.productShopLinkDao().getLinksForProductSync(member.id).any{it.shopId==change.shopId}}){"Не все товары привязаны к этому магазину"}}
   expanded.filter{p->db.productShopLinkDao().getLinksForProductSync(p.id).any{it.shopId==change.shopId}}
  }else expanded
  when(change){
   is ProductBatchChange.Recipient->change.id?.let{require(db.recipientDao().getById(it)!=null){"Получатель удалён"}}
   is ProductBatchChange.Group->change.id?.let{require(db.productGroupDao().getById(it)!=null){"Товарная группа удалена"}}
   is ProductBatchChange.Shops->{require(change.ids.isNotEmpty());change.ids.forEach{require(db.shopDao().getById(it)!=null){"Магазин удалён"}}}
   is ProductBatchChange.Department->{
    require(db.shopDao().getById(change.shopId)!=null){"Магазин удалён"}
    change.typeId?.let{require(db.shopDepartmentDao().type(it)!=null){"Группа магазина удалена"}}
    rows.forEach{p->
     require(db.productShopLinkDao().getLinksForProductSync(p.id).any{it.shopId==change.shopId}){"Не все товары привязаны к этому магазину"}
    }
   }
   else->Unit
  }
  val placement=if(change is ProductBatchChange.Department&&change.typeId!=null)ensurePlacement(change.shopId,change.typeId)else null
  rows.forEach{p->
   val links=db.productShopLinkDao().getLinksForProductSync(p.id)
   when(change){
    is ProductBatchChange.Recipient->db.productDao().update(p.copy(recipientId=change.id))
    is ProductBatchChange.Group->db.productDao().update(p.copy(productGroupId=change.id))
    is ProductBatchChange.Favorite->db.productDao().update(p.copy(isFavorite=change.value))
    is ProductBatchChange.Shops->{
     products.ensureShopLinks(p.id,db.shopDao().getAllSync().filter{it.id in change.ids}.map{it.id})
     // Also resolves a formerly unassigned automatic purchase, without moving a valid one.
     products.saveWithLinks(p,db.productShopLinkDao().getLinksForProductSync(p.id))
    }
    is ProductBatchChange.Department->{
     val overrides=products.branchDepartments(p.id).associate{it.branchId to it.typeId}.toMutableMap()
     // saveWithLinks accepts placement IDs, while persisted overrides contain type IDs.
     val byType=db.shopDepartmentDao().getAllSync().associateBy{it.shopId to it.typeId}
     val branches=db.shopBranchDao().getAll().associateBy{it.id}
     val choices=overrides.mapNotNull{(branch,type)->byType[branches[branch]?.shopId to type]?.let{branch to it.id}}.toMap().toMutableMap()
     choices.keys.filter{branches[it]?.shopId==change.shopId}.forEach{choices.remove(it)}
     val nextLinks=links.map{if(it.shopId==change.shopId)it.copy(departmentId=placement)else it}
     products.saveWithLinks(p,nextLinks,branchDepartments=choices)
    }
   }
  }
 } }
 private suspend fun ensurePlacement(shopId:Long,typeId:Long):Long {
  val dao=db.shopDepartmentDao();val rows=dao.getByShopIdSync(shopId)
  val id=rows.find{it.typeId==typeId}?.id?:dao.insert(ShopDepartmentEntity(shopId=shopId,typeId=typeId,name=dao.type(typeId)!!.name,displayOrder=nextDepartmentDisplayOrder(db,shopId)))
  // The same network contract applies to a batch and to the shop/product cards.
  // Synchronization updates placements in place, retaining product/purchase IDs.
  synchronizeNetworkDepartments(db,shopId)
  return id
 }
 suspend fun addDepartments(shopIds:Set<Long>,typeIds:Set<Long>,cityId:Long?=null)=withCatalogueEdits(db,catalogueEdits){db.withTransaction {
  require(shopIds.isNotEmpty()&&typeIds.isNotEmpty()) { "Выберите магазины и группы" }
  shopIds.forEach{val shop=db.shopDao().getById(it)?:error("Магазин удалён");require(cityId==null||shop.cityId==cityId){"Город изменился. Обновите выбор магазинов"}}
  typeIds.forEach{require(db.shopDepartmentDao().type(it)!=null){"Группа удалена"}}
  val ordered=db.shopDepartmentDao().types().filter{it.id in typeIds}
  shopIds.sorted().forEach{s->ordered.forEach{ensurePlacement(s,it.id)}}
 } }
 suspend fun changePurchases(ids:Set<Long>,change:PurchaseBatchChange)=db.withTransaction {
  require(ids.isNotEmpty()) { "Выберите покупки" }
  val rows=ids.sorted().map{db.shoppingListEntryDao().getById(it)?:error("Одна из покупок удалена. Обновите выбор")}
  val visible=db.shoppingListEntryDao().getVisibleSync().map{it.id}.toSet()
  require(ids.all{it in visible}) { "Состав покупок изменился. Обновите выбор" }
  if(change==PurchaseBatchChange.URGENT||change==PurchaseBatchChange.NOT_URGENT)require(rows.none{it.isBought}){"Срочность доступна для некупленных"}
  val now=System.currentTimeMillis()
  rows.forEach{row->
   if(change==PurchaseBatchChange.DELETE)db.shoppingListEntryDao().delete(row)
   else db.shoppingListEntryDao().update(when(change){
    PurchaseBatchChange.BOUGHT->row.copy(isBought=true,updatedAt=now)
    PurchaseBatchChange.RETURN->row.copy(isBought=false,updatedAt=now)
    PurchaseBatchChange.URGENT->row.copy(isUrgent=true,updatedAt=now)
    else->row.copy(isUrgent=false,updatedAt=now)
   })
  }
  if(change==PurchaseBatchChange.DELETE)db.productDao().deleteUnusedTemporary()
 }
 suspend fun addToBasket(ids:Set<Long>,shopId:Long?=null,branchId:Long?=null)=db.withTransaction {
  products(ids).forEach{basket.addProduct(it.id,choice=if(shopId==null)ShopChoice.AUTO else ShopChoice.EXPLICIT,shopId=shopId,branchId=branchId)}
 }
}
