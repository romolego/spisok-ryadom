package com.spisokryadom.app.domain

import com.spisokryadom.app.data.entity.*

sealed interface RetailerChoice {
 data object Automatic:RetailerChoice
 data object Unassigned:RetailerChoice
 data class Exact(val id:Long):RetailerChoice
}
sealed interface OutletChoice {
 data object Unassigned:OutletChoice
 data object Preferred:OutletChoice
 data class Exact(val id:Long):OutletChoice
}
data class PurchaseAssignment(val shopId:Long?,val departmentId:Long?,val branchId:Long?)

/** Immutable indexed catalogue, shared by a whole text/transfer command.
 * Existing purchases are facts: editing only quantity/note does not reroute them.
 * New assignment and explicit transfer always resolve current availability. */
class AssignmentCatalogue(products:List<ProductEntity>,shops:List<ShopEntity>,links:List<ProductShopLinkEntity>,departments:List<ShopDepartmentEntity>,branches:List<ShopBranchEntity>,availability:List<ProductBranchAvailabilityEntity>,overrides:List<ProductBranchDepartmentEntity>,priorities:List<ProductBranchPriorityEntity> = emptyList()) {
 private val priorityByPoint=priorities.associate{(it.productId to it.branchId) to it.displayOrder}
 private val productById=products.associateBy{it.id}
 private val shopById=shops.associateBy{it.id}
 private val linksByProduct=links.groupBy{it.productId}
 private val branchesByShop=branches.groupBy{it.shopId}.mapValues{(_,v)->v.sortedWith(compareBy<ShopBranchEntity>{it.displayOrder}.thenBy{it.id})}
 private val availableByProduct=availability.groupBy{it.productId}.mapValues{(_,v)->v.map{it.branchId}.toSet()}
 private val departmentsById=departments.associateBy{it.id}
 private val departmentByType=departments.associateBy{it.shopId to it.typeId}
 private val overrideByPoint=overrides.associateBy{it.productId to it.branchId}

 fun retailer(productId:Long,cityId:Long,choice:RetailerChoice):Pair<Long?,Long?> {
  if(choice==RetailerChoice.Unassigned)return null to null
  val links=linksByProduct[productId].orEmpty()
  val shopId=when(choice){
   is RetailerChoice.Exact->{require(shopById[choice.id]?.cityId==cityId){"Магазин недоступен в выбранном городе"};choice.id}
   else->{
    val candidates=links.filter{shopById[it.shopId]?.cityId==cityId}
    val ordering=if(productById[productId]?.preferCityShopOrder==true)compareBy<ProductShopLinkEntity>{shopById[it.shopId]?.displayOrder?:Int.MAX_VALUE}.thenBy{it.priority}.thenBy{it.shopId}
     else compareBy<ProductShopLinkEntity>{it.priority}.thenBy{shopById[it.shopId]?.displayOrder?:Int.MAX_VALUE}.thenBy{it.shopId}
    candidates.minWithOrNull(ordering)?.shopId
   }
  }
  return shopId to links.find{it.shopId==shopId}?.departmentId?.takeIf{departmentsById[it]?.shopId==shopId}
 }
 fun outlet(productId:Long,shopId:Long?,choice:OutletChoice):Long? {
  if(shopId==null){require(choice==OutletChoice.Preferred||choice==OutletChoice.Unassigned){"Сначала выберите магазин"};return null}
  require(shopById.containsKey(shopId)){"Магазин удалён"}
  if(choice==OutletChoice.Unassigned)return null
  val link=linksByProduct[productId].orEmpty().find{it.shopId==shopId}
  val available=availableByProduct[productId].orEmpty()
  val points=branchesByShop[shopId].orEmpty().filter{link?.restrictBranches!=true||it.id in available}
  if(choice is OutletChoice.Exact){require(points.any{it.id==choice.id}){"Этот товар не отмечен в выбранной точке. Проверьте точки продажи в карточке товара"};return choice.id}
  require(link?.restrictBranches!=true||points.isNotEmpty()){"Для товара не осталось доступных точек"}
  return points.minWithOrNull(compareBy<ShopBranchEntity>{priorityByPoint[productId to it.id]?:Int.MAX_VALUE}.thenBy{it.displayOrder}.thenBy{it.id})?.id
 }
 fun department(productId:Long,shopId:Long?,branchId:Long?,fallback:Long?):Long? {
  val type=overrideByPoint[productId to branchId]?.typeId?:return fallback
  return departmentByType[shopId to type]?.id?:fallback
 }
 fun resolve(productId:Long,cityId:Long,retailer:RetailerChoice,outlet:OutletChoice=OutletChoice.Preferred):PurchaseAssignment {
  val (shop,dept)=retailer(productId,cityId,retailer);val point=outlet(productId,shop,outlet)
  return PurchaseAssignment(shop,department(productId,shop,point,dept),point)
 }
}
