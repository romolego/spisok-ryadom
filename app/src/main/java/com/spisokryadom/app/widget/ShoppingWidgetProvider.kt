package com.spisokryadom.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import com.spisokryadom.app.R
import com.spisokryadom.app.SpisokRyadomApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ShoppingWidgetProvider:AppWidgetProvider() {
    override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray){ids.forEach{manager.updateAppWidget(it,views(context,it))};refresh(context)}
    override fun onReceive(context:Context,intent:Intent) {
        super.onReceive(context,intent)
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
        private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        fun checkIntent(row:WidgetPurchase)=Intent().putExtra("id",row.id).putExtra("product",row.productStableId)
            .putExtra("created",row.createdAt).putExtra("updated",row.updatedAt).putExtra("bought",row.bought).putExtra("revision",row.revision)
        fun decodeCheck(intent:Intent):WidgetPurchase? {
            if(!listOf("id","product","created","updated","bought","revision").all{intent.hasExtra(it)})return null
            return WidgetPurchase(intent.getLongExtra("id",-1),intent.getStringExtra("product")?:return null,
                intent.getLongExtra("created",-1),intent.getLongExtra("updated",-1),intent.getBooleanExtra("bought",false),"","",false,intent.getStringExtra("revision")?:return null)
        }
        fun views(context:Context,id:Int)=RemoteViews(context.packageName,R.layout.shopping_widget).apply {
            val settings=WidgetSettings.load(context.getSharedPreferences(WidgetSettings.PREFS,Context.MODE_PRIVATE))
            setTextViewTextSize(R.id.widget_heading,android.util.TypedValue.COMPLEX_UNIT_SP,(settings.textSize+4).toFloat())
            setTextViewTextSize(R.id.widget_empty,android.util.TypedValue.COMPLEX_UNIT_SP,settings.textSize.toFloat())
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
