package com.spisokryadom.app.ui.addtolist

import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.util.FuzzySearch
import java.text.Collator
import java.util.Locale

/** The same physical-point restriction used by purchase assignment. */
fun addProductCandidates(products:List<ProductEntity>,links:List<ProductShopLinkEntity>,availability:List<ProductBranchAvailabilityEntity>,
    shopId:Long?,branchId:Long?,query:String,browse:Boolean):List<ProductEntity> {
    if(query.isBlank()&&!browse)return emptyList()
    val shopLinks=links.filter{it.shopId==shopId}.associateBy{it.productId}
    val available=availability.filter{it.shopId==shopId}.groupBy{it.productId}
    val candidates=products.filter { p ->
        !p.isTemporary && (shopId==null||shopId<=0||shopLinks[p.id]?.let{link->
            !link.restrictBranches||available[p.id].orEmpty().any{branchId==null||branchId<=0||it.branchId==branchId}
        }==true)
    }
    if(query.isNotBlank())return FuzzySearch.rank(candidates,query.trim(),{it.name})
    val collator=Collator.getInstance(Locale.forLanguageTag("ru")).apply{strength=Collator.PRIMARY}
    return candidates.sortedWith { a,b -> collator.compare(a.name,b.name).takeIf{it!=0}?:a.id.compareTo(b.id) }
}
