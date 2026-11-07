package com.spisokryadom.app.widget

import android.appwidget.AppWidgetManager
import android.os.SystemClock
import java.util.UUID

/** Short lived host presentation only. Purchase writes still use the repository's CAS. */
internal object WidgetInteractionState {
    const val PEEK_MS=5_000L
    const val MOTION_MS=600L
    private data class Peek(val identity:WidgetPurchase,val token:String,val until:Long)
    private data class Motion(val token:String,val row:WidgetPurchase,val until:Long)
    private data class Host(val revision:String,val settings:WidgetSettings,var frame:List<WidgetItem>,
        val peeks:MutableMap<Long,Peek> = mutableMapOf(),val motions:MutableMap<Long,Motion> = mutableMapOf())
    private val hosts=mutableMapOf<Int,Host>()
    private fun same(a:WidgetPurchase,b:WidgetPurchase)=a.id==b.id&&a.productStableId==b.productStableId&&a.createdAt==b.createdAt&&a.revision==b.revision
    @Synchronized fun clear(id:Int?=null){if(id==null)hosts.clear()else hosts.remove(id)}
    @Synchronized fun ready(id:Int,revision:String,settings:WidgetSettings)=hosts[id]?.let{it.revision==revision&&it.settings==settings}==true
    @Synchronized fun present(id:Int,revision:String,settings:WidgetSettings,canonical:List<WidgetItem>,current:List<WidgetItem> = canonical,now:Long=SystemClock.elapsedRealtime()):Pair<List<WidgetItem>,Set<Long>> {
        if(id==AppWidgetManager.INVALID_APPWIDGET_ID)return canonical to emptySet()
        var host=hosts[id]
        if(host==null||host.revision!=revision||host.settings!=settings){host=Host(revision,settings,emptyList());hosts[id]=host}
        val actual=current.filterIsInstance<WidgetItem.Purchase>().associateBy{it.row.id}
        host.peeks.entries.removeAll{(_,peek)->peek.until<=now||actual[peek.identity.id]?.row?.let{!it.isPrivate||!same(peek.identity,it)}!=false}
        host.motions.entries.removeAll{(_,motion)->motion.until<=now||actual[motion.row.id]?.row?.let{it.productStableId!=motion.row.productStableId||it.createdAt!=motion.row.createdAt}!=false}
        val baseIds=host.frame.filterIsInstance<WidgetItem.Purchase>().map{it.row.id}.toSet()
        val visible=canonical
        // Structural changes use the canonical snapshot immediately. No deleted or newly
        // visible purchase may be hidden by a cached transition frame.
        if(visible.filterIsInstance<WidgetItem.Purchase>().any{it.row.id !in baseIds}||baseIds.any{it !in actual})host.motions.clear()
        val departing=host.motions.values.filter{actual[it.row.id]?.row?.bought!=it.row.bought}.map{it.row.id}.toSet()
        val result=if(host.motions.isEmpty())visible else host.frame.map{item->if(item is WidgetItem.Purchase)actual.getValue(item.row.id)else item}
        host.frame=result
        return result to departing
    }
    @Synchronized fun moving(id:Int)=hosts[id]?.motions?.isNotEmpty()==true
    @Synchronized fun begin(id:Int,row:WidgetPurchase,revision:String,now:Long=SystemClock.elapsedRealtime()):String? {
        val host=hosts[id]?:return null
        if(host.revision!=revision)return null
        if(host.motions.containsKey(row.id)||host.frame.filterIsInstance<WidgetItem.Purchase>().none{same(it.row,row)})return null
        val token=UUID.randomUUID().toString()
        host.peeks.remove(row.id)
        host.motions[row.id]=Motion(token,row,now+2_000)
        return token
    }
    @Synchronized fun settle(id:Int,rowId:Long,token:String){val host=hosts[id]?:return;if(host.motions[rowId]?.token==token)host.motions.remove(rowId)}
    @Synchronized fun peekToken(id:Int,row:WidgetPurchase,now:Long=SystemClock.elapsedRealtime()):String? =
        hosts[id]?.peeks?.get(row.id)?.takeIf{it.until>now&&same(it.identity,row)&&row.isPrivate}?.token
    /** Empty expected token reveals once. A repeated stale tap cannot prolong disclosure. */
    @Synchronized fun reveal(id:Int,row:WidgetPurchase,expected:String,now:Long=SystemClock.elapsedRealtime()):String? {
        val host=hosts[id]?:return null
        if(!row.isPrivate||host.frame.filterIsInstance<WidgetItem.Purchase>().none{same(it.row,row)})return null
        val existing=peekToken(id,row,now)
        if(expected.isNotEmpty()){if(existing==expected)host.peeks.remove(row.id);return null}
        if(existing!=null)return null
        val token=UUID.randomUUID().toString()
        host.peeks[row.id]=Peek(row,token,now+PEEK_MS)
        return token
    }
    @Synchronized fun conceal(id:Int,rowId:Long,token:String){val host=hosts[id]?:return;if(host.peeks[rowId]?.token==token)host.peeks.remove(rowId)}
}
