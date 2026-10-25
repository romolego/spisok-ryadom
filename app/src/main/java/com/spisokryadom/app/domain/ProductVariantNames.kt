package com.spisokryadom.app.domain

import com.spisokryadom.app.data.entity.*

/** Presentation only; canonical product names are never rewritten to include the variant. */
fun variantNames(products:List<ProductEntity>,variants:List<ProductVariantEntity>):List<ProductEntity>{
 val families=variants.groupBy{it.familyId};val byId=variants.associateBy{it.productId}
 return products.map{p->val v=byId[p.id];if(v!=null&&families[v.familyId].orEmpty().size>1)p.copy(name="${p.name} · ${v.label}")else p}
}
