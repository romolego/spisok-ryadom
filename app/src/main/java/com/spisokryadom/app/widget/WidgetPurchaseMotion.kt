package com.spisokryadom.app.widget

import android.appwidget.AppWidgetManager
import android.os.SystemClock

/** Presentation only: concurrent checks commit first; no input waits for a transition. */
internal object WidgetPurchaseMotion {
    const val SETTLE_MS=180L
    private data class Host(val revision:String,val settings:WidgetSettings,var frame:List<WidgetItem>,val leaving:MutableMap<Long,Long> = mutableMapOf())
    private val hosts=mutableMapOf<Int,Host>()
    @Synchronized fun clear(id:Int?=null){if(id==null)hosts.clear()else hosts.remove(id)}
    @Synchronized fun begin(row:WidgetPurchase,revision:String,now:Long=SystemClock.elapsedRealtime()){
        if(row.bought)return
        hosts.values.filter{it.revision==revision}.forEach{host->
            if(host.frame.filterIsInstance<WidgetItem.Purchase>().any{it.row.id==row.id&&it.row.revision==row.revision})host.leaving[row.id]=now+SETTLE_MS
        }
    }
    @Synchronized fun present(id:Int,revision:String,settings:WidgetSettings,canonical:List<WidgetItem>,current:List<WidgetItem>,now:Long=SystemClock.elapsedRealtime()):Pair<List<WidgetItem>,Set<Long>>{
        if(id==AppWidgetManager.INVALID_APPWIDGET_ID)return canonical to emptySet()
        val host=hosts[id]?.takeIf{it.revision==revision&&it.settings==settings}
            ?:Host(revision,settings,canonical).also{hosts[id]=it}
        val actual=current.filterIsInstance<WidgetItem.Purchase>().associateBy{it.row.id}
        val old=host.frame.filterIsInstance<WidgetItem.Purchase>()
        val oldIds=old.map{it.row.id}.toSet()
        // New/deleted purchases and replacement identities bypass cached presentation.
        if(canonical.filterIsInstance<WidgetItem.Purchase>().any{it.row.id !in oldIds}||
            old.any{item->actual[item.row.id]?.row?.let{it.productStableId!=item.row.productStableId||it.createdAt!=item.row.createdAt}!=false})host.leaving.clear()
        host.leaving.entries.removeAll{(id,_)->actual[id]?.row?.bought!=true}
        // Keep completed departures blank while a later row is still moving. Rebinding an
        // expired row as visible in the old frame would make it jump back before sorting.
        if(host.leaving.values.none{it>now})host.leaving.clear()
        val frame=if(host.leaving.isEmpty())canonical else host.frame.map{item->if(item is WidgetItem.Purchase)actual.getValue(item.row.id)else item}
        host.frame=frame
        return frame to host.leaving.keys.toSet()
    }
}
