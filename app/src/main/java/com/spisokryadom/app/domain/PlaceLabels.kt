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
    fun place(shopName:String,branch:ShopBranchEntity?):PlaceLabel = physicalPlaceLabel(shopName,branch?.address,branch?.venueId?.let{venues[it]?.name})
    fun branch(branch:ShopBranchEntity):String = branch.venueId?.let{venues[it]}?.let{
        if(same(it.name,branch.address))it.name else "${it.name} — ${branch.address}"
    }?:branch.address
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
