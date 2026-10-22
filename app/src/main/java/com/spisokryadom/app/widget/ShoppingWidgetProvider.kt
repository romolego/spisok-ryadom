package com.spisokryadom.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.spisokryadom.app.R
import com.spisokryadom.app.SpisokRyadomApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ShoppingWidgetProvider:AppWidgetProvider() {
    override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray){ids.forEach{manager.updateAppWidget(it,views(context,it))};refresh(context)}
    override fun onAppWidgetOptionsChanged(context:Context,manager:AppWidgetManager,id:Int,options:Bundle) {
        manager.updateAppWidget(id,views(context,id))
    }
    override fun onReceive(context:Context,intent:Intent) {
        super.onReceive(context,intent)
        if(intent.action==ACTION_SET_BOUGHT_VISIBILITY){
            // Explicit target state makes a repeated/stale broadcast idempotent. This changes
            // only the widget projection; purchases and their CAS tokens are untouched.
            val show=intent.extras?.get(EXTRA_SHOW_BOUGHT) as? Boolean?:return
            val prefs=context.getSharedPreferences(WidgetSettings.PREFS,Context.MODE_PRIVATE)
            if(!WidgetSettings.load(prefs).showBoughtToggle)return
            prefs.edit().putBoolean(WidgetSettings.SHOW_BOUGHT,show).apply()
            refresh(context,updateLayout=true)
            return
        }
        if(intent.action!=ACTION)return
        if(intent.getBooleanExtra("open",false)){context.startActivity(WidgetIntents.open(context));return}
        val row=decodeCheck(intent)?:return
        val pending=goAsync()
        scope.launch {
            try { ShoppingWidgetData((context.applicationContext as SpisokRyadomApp).container).check(row);refresh(context) }
            catch(e:Exception){android.util.Log.w("ShoppingWidget","Cannot update purchase",e);refresh(context)}
            finally{pending.finish()}
        }
    }
    companion object {
        const val ACTION="com.spisokryadom.app.widget.CHECK"
        const val ACTION_SET_BOUGHT_VISIBILITY="com.spisokryadom.app.widget.SET_BOUGHT_VISIBILITY"
        private const val EXTRA_SHOW_BOUGHT="show"
        private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        fun visibilityIntent(context:Context,show:Boolean):Intent=Intent(context,ShoppingWidgetProvider::class.java)
            .setAction(ACTION_SET_BOUGHT_VISIBILITY).setData(Uri.parse("spisok-widget://bought/$show"))
            .putExtra(EXTRA_SHOW_BOUGHT,show)
        fun checkIntent(row:WidgetPurchase)=Intent().putExtra("id",row.id).putExtra("product",row.productStableId)
            .putExtra("created",row.createdAt).putExtra("updated",row.updatedAt).putExtra("bought",row.bought).putExtra("revision",row.revision)
        fun decodeCheck(intent:Intent):WidgetPurchase? {
            if(!listOf("id","product","created","updated","bought","revision").all{intent.hasExtra(it)})return null
            return WidgetPurchase(intent.getLongExtra("id",-1),intent.getStringExtra("product")?:return null,
                intent.getLongExtra("created",-1),intent.getLongExtra("updated",-1),intent.getBooleanExtra("bought",false),"","",false,intent.getStringExtra("revision")?:return null)
        }
        fun views(context:Context,id:Int)=RemoteViews(context.packageName,
            if(AppWidgetManager.getInstance(context).getAppWidgetOptions(id)
                    .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,0) in 1..259)R.layout.shopping_widget_narrow else R.layout.shopping_widget).apply {
            val settings=WidgetSettings.load(context.getSharedPreferences(WidgetSettings.PREFS,Context.MODE_PRIVATE))
            setTextViewTextSize(R.id.widget_heading,android.util.TypedValue.COMPLEX_UNIT_SP,(settings.textSize+4).toFloat())
            setTextViewTextSize(R.id.widget_empty,android.util.TypedValue.COMPLEX_UNIT_SP,settings.textSize.toFloat())
            setTextViewText(R.id.widget_empty,context.getString(if(settings.showBought)R.string.widget_empty else R.string.widget_pending_empty))
            setViewVisibility(R.id.widget_bought_toggle,if(settings.showBoughtToggle)View.VISIBLE else View.GONE)
            setImageViewResource(R.id.widget_bought_toggle,if(settings.showBought)R.drawable.widget_visibility else R.drawable.widget_visibility_off)
            setContentDescription(R.id.widget_bought_toggle,context.getString(if(settings.showBought)R.string.widget_hide_bought else R.string.widget_show_bought))
            setInt(R.id.widget_bought_toggle,"setBackgroundResource",if(settings.showBought)R.drawable.widget_add_background else R.drawable.widget_active_background)
            setViewVisibility(R.id.widget_bought_status,if(settings.showBought)View.GONE else View.VISIBLE)
            val adapter=Intent(context,ShoppingWidgetService::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id)
                .setData(Uri.parse("spisok-widget://list/$id"))
            setRemoteAdapter(R.id.widget_list,adapter)
            setEmptyView(R.id.widget_list,R.id.widget_empty)
            val immutable=PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val open=PendingIntent.getActivity(context,0,WidgetIntents.open(context),immutable)
            setOnClickPendingIntent(R.id.widget_root,open)
            setOnClickPendingIntent(R.id.widget_heading,open)
            setOnClickPendingIntent(R.id.widget_empty,open)
            setOnClickPendingIntent(R.id.widget_add,PendingIntent.getActivity(context,1,WidgetIntents.open(context,true),immutable))
            setOnClickPendingIntent(R.id.widget_bought_toggle,PendingIntent.getBroadcast(context,
                if(settings.showBought)2 else 3,visibilityIntent(context,!settings.showBought),immutable))
            val mutable=PendingIntent.FLAG_UPDATE_CURRENT or if(Build.VERSION.SDK_INT>=31)PendingIntent.FLAG_MUTABLE else 0
            setPendingIntentTemplate(R.id.widget_list,PendingIntent.getBroadcast(context,id,Intent(context,ShoppingWidgetProvider::class.java).setAction(ACTION).setData(Uri.parse("spisok-widget://click/$id")),mutable))
        }
        fun refresh(context:Context,updateLayout:Boolean=false) {
            val manager=AppWidgetManager.getInstance(context)
            val ids=manager.getAppWidgetIds(ComponentName(context,ShoppingWidgetProvider::class.java))
            if(ids.isNotEmpty()){
                if(updateLayout)ids.forEach{manager.updateAppWidget(it,views(context,it))}
                manager.notifyAppWidgetViewDataChanged(ids,R.id.widget_list)
            }
        }
    }
}
