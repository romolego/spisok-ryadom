package com.spisokryadom.app
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.domain.*
import com.spisokryadom.app.ui.components.ProductContextIndex
import org.junit.Test
import org.junit.Assert.*

class ArchitecturePolicyTest {
 @Test fun routeOrderAndProductPreferenceHaveExplicitDifferentSemantics(){
  val shops=listOf(ShopEntity(1,"Рядом",displayOrder=0),ShopEntity(2,"Дальше",displayOrder=1),ShopEntity(3,"Иркутск",cityId=2))
  val products=listOf(ProductEntity(1,"Яблоки",preferCityShopOrder=true),ProductEntity(2,"Сыр"))
  val links=listOf(1L,2L).flatMap{p->listOf(ProductShopLinkEntity(productId=p,shopId=1,priority=2),ProductShopLinkEntity(productId=p,shopId=2,priority=1),ProductShopLinkEntity(productId=p,shopId=3,priority=0))}
  val catalogue=AssignmentCatalogue(products,shops,links,emptyList(),emptyList(),emptyList(),emptyList())
  assertEquals(1L,catalogue.resolve(1,1,RetailerChoice.Automatic).shopId)
  assertEquals(2L,catalogue.resolve(2,1,RetailerChoice.Automatic).shopId)
  assertEquals(3L,catalogue.resolve(1,2,RetailerChoice.Automatic).shopId)
  assertNull(catalogue.resolve(1,1,RetailerChoice.Unassigned).shopId)
 }
 @Test fun restrictedPointAndItsOwnGroupAreResolvedWithoutRecreatingProduct(){
  val p=ProductEntity(1,"Сыр");val shop=ShopEntity(1,"Командор",networkId=7)
  val points=listOf(ShopBranchEntity(11,1,"Дом"),ShopBranchEntity(12,1,"ТЦ",venueId=9))
  val c=AssignmentCatalogue(listOf(p),listOf(shop),listOf(ProductShopLinkEntity(productId=1,shopId=1,departmentId=21,restrictBranches=true)),listOf(ShopDepartmentEntity(21,1,"Молочка",typeId=1),ShopDepartmentEntity(22,1,"Сыры",typeId=2)),points,listOf(ProductBranchAvailabilityEntity(1,12,1)),listOf(ProductBranchDepartmentEntity(1,12,1,2)))
  val target=c.resolve(1,1,RetailerChoice.Automatic);assertEquals(12L,target.branchId);assertEquals(22L,target.departmentId)
  try{c.resolve(1,1,RetailerChoice.Exact(1),OutletChoice.Exact(11));fail("Unavailable point")}catch(expected:IllegalArgumentException){}
 }
 @Test fun catalogueContextIndexHandlesTenThousandProductsWithThirtyThousandLinks(){
  val products=(1L..10_000L).map{ProductEntity(it,"Товар $it",note="Заметка $it")}
  val shops=(1L..30L).map{ShopEntity(it,"Магазин $it")}
  val links=products.flatMap{p->(1L..3L).map{ProductShopLinkEntity(productId=p.id,shopId=(p.id+it)%30+1)}}
  val index=ProductContextIndex(links,shops)
  val contexts=products.map{index.context(it)}
  assertEquals(10_000,contexts.size);assertTrue(contexts.last().contains("Заметка 10000"));assertTrue(contexts.first().contains("Магазин 3"))
 }
}
