package com.spisokryadom.app.domain

import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.util.FuzzySearch

/** Postal address is owned by a venue; an outlet label is never a second postal address. */
object OutletLocations {
    fun legacy(point:ShopBranchEntity,venue:VenueEntity?,shopName:String):ShopBranchEntity {
        if(venue==null)return point
        val old=point.address.trim()
        val redundant=listOf(venue.address.orEmpty(),venue.name,shopName).any{FuzzySearch.normalize(it)==FuzzySearch.normalize(old)}
        return point.copy(address="",label=point.label.ifBlank{if(redundant)""else old})
    }
    fun migrate(points:List<ShopBranchEntity>,venues:List<VenueEntity>,shopNames:Map<Long,String>):List<ShopBranchEntity> {
        val venuesById=venues.associateBy{it.id}
        val result=points.map{legacy(it,venuesById[it.venueId],shopNames[it.shopId].orEmpty())}
        val collisions=result.groupingBy{it.shopId to key(it)}.eachCount()
        val used=mutableSetOf<Pair<Long,Triple<Long?,String,String>>>()
        return result.mapIndexed{i,point->
            var next=if(collisions.getValue(point.shopId to key(point))>1)point.copy(label=points[i].address.ifBlank{point.label}.ifBlank{"Точка ${point.id}"})else point
            if(!used.add(next.shopId to key(next))){next=next.copy(label="${next.label} · ${next.id}");used.add(next.shopId to key(next))}
            next
        }
    }
    fun key(point:ShopBranchEntity):Triple<Long?,String,String> = Triple(point.venueId,FuzzySearch.normalize(point.address),FuzzySearch.normalize(point.label))
    fun valid(points:List<ShopBranchEntity>)=points.all{it.venueId!=null||it.address.isNotBlank()||it.label.isNotBlank()}&&points.map(::key).distinct().size==points.size
    fun move(points:List<ShopBranchEntity>,id:Long,delta:Int):List<ShopBranchEntity> {
        val next=points.toMutableList();val from=next.indexOfFirst{it.id==id};val to=from+delta
        if(from<0||to !in next.indices)return points
        next.add(to,next.removeAt(from))
        return next.mapIndexed{i,p->p.copy(displayOrder=i)}
    }
}
