package com.spisokryadom.app.ui.components
import com.spisokryadom.app.data.entity.*

/** Build once per catalogue snapshot; rendering each candidate only walks its own links. */
class ProductContextIndex(links:List<ProductShopLinkEntity>,shops:List<ShopEntity>) {
 private val linksByProduct=links.groupBy{it.productId}
 private val shopById=shops.associateBy{it.id}
 fun context(product:ProductEntity):String {
  val names=linksByProduct[product.id].orEmpty().mapNotNull{shopById[it.shopId]?.name}.distinct()
  return listOfNotNull(product.defaultQuantity?.let{q->"${if(q%1.0==0.0)q.toLong().toString()else q.toString()} ${product.defaultUnit.orEmpty()}"}?:product.defaultUnit,
   names.takeIf{it.isNotEmpty()}?.joinToString(", "),product.note?.takeIf{it.isNotBlank()}).joinToString(" · ")
 }
}
fun productContext(product:ProductEntity,links:List<ProductShopLinkEntity>,shops:List<ShopEntity>)=ProductContextIndex(links,shops).context(product)
fun productContexts(products:List<ProductEntity>,links:List<ProductShopLinkEntity>,shops:List<ShopEntity>):Map<Long,String>{
 val index=ProductContextIndex(links,shops);return products.associate{it.id to index.context(it)}
}
