package com.spisokryadom.app.domain

import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.util.FuzzySearch

/** Presentation only: never writes a contextual name back to the catalogue. */
data class PlaceLabel(val title:String,val subtitle:String?=null,val separator:String=" · ") {
    val text:String get()=listOfNotNull(title,subtitle).joinToString(separator)
}

class PlaceLabels(branches:List<ShopBranchEntity> = emptyList(),venues:List<VenueEntity> = emptyList()) {
    private val points=branches.groupBy{it.shopId}
    private val venues=venues.associateBy{it.id}
    private fun same(a:String,b:String)=FuzzySearch.normalize(a)==FuzzySearch.normalize(b)
    fun address(branch:ShopBranchEntity):String = branch.venueId?.let{venues[it]?.address}.orEmpty().ifBlank{if(branch.venueId==null)branch.address else ""}
    fun detail(branch:ShopBranchEntity):String {
        if(branch.venueId==null)return listOf(branch.label,branch.address).filter{it.isNotBlank()}.distinctBy(FuzzySearch::normalize).joinToString(" · ")
        val venue=venues[branch.venueId]
        return branch.label.ifBlank{branch.address.takeUnless{same(it,venue?.address.orEmpty())||same(it,venue?.name.orEmpty())}.orEmpty()}
    }
    fun details(shopName:String,branch:ShopBranchEntity):String? {
        val venue=branch.venueId?.let{venues[it]}
        val values=if(branch.venueId==null)listOf(branch.label,branch.address)else listOf(detail(branch),address(branch))
        return values.filter{it.isNotBlank()&&!same(it,shopName)&&!same(it,venue?.name.orEmpty())}
            .distinctBy(FuzzySearch::normalize).joinToString(" · ").ifBlank{null}
    }
    fun place(shopName:String,branch:ShopBranchEntity?):PlaceLabel =
        physicalPlaceLabel(shopName,branch?.let{details(shopName,it)},branch?.venueId?.let{venues[it]?.name})
    fun branch(branch:ShopBranchEntity):String {
        if(branch.venueId==null)return detail(branch).ifBlank{"Точка магазина"}
        return listOfNotNull(branch.venueId.let{venues[it]?.name},detail(branch).takeIf{it.isNotBlank()},address(branch).takeIf{it.isNotBlank()})
            .distinctBy(FuzzySearch::normalize).joinToString(" — ").ifBlank{"Точка магазина"}
    }
    fun shop(shopId:Long,name:String):String {
        val branches=points[shopId].orEmpty()
        val venueIds=branches.map{it.venueId}.distinct()
        // A retailer with street outlets and mall outlets is not owned by one venue.
        val venue=venueIds.singleOrNull()?.let{venues[it]}
        return if(venue==null||same(name,venue.name))name else "${venue.name} — $name"
    }
}

fun physicalPlaceLabel(shopName:String,address:String?,venueName:String?):PlaceLabel {
    fun same(a:String,b:String)=FuzzySearch.normalize(a)==FuzzySearch.normalize(b)
    val address=address?.trim()?.takeIf{it.isNotBlank()}
    if(!venueName.isNullOrBlank()) {
        val details=listOfNotNull(shopName.trim().takeIf{it.isNotEmpty()&&!same(it,venueName)},address)
            .distinctBy{FuzzySearch.normalize(it)}.filter{!same(it,venueName)}
        return PlaceLabel(venueName,details.takeIf{it.isNotEmpty()}?.joinToString(" · ")," — ")
    }
    return PlaceLabel(shopName,address?.takeUnless{same(it,shopName)})
}
