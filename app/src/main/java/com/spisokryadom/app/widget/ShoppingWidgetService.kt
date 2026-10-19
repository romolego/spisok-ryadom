package com.spisokryadom.app.widget

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.spisokryadom.app.R
import com.spisokryadom.app.SpisokRyadomApp
import kotlinx.coroutines.runBlocking

class ShoppingWidgetService:RemoteViewsService() {
    override fun onGetViewFactory(intent:Intent):RemoteViewsFactory=ShoppingWidgetFactory(applicationContext)
}

class ShoppingWidgetFactory(private val context:Context):RemoteViewsService.RemoteViewsFactory {
    @Volatile private var frame=WidgetFrame()
    override fun onCreate()=onDataSetChanged()
    override fun onDataSetChanged(){
        val prefs=context.getSharedPreferences(WidgetSettings.PREFS,Context.MODE_PRIVATE)
        val settings=WidgetSettings.load(prefs)
        val items=runBlocking{ShoppingWidgetData((context.applicationContext as SpisokRyadomApp).container)
            .presentation(settings,prefs.all["global_default_boost_urgent_shop"] as? Boolean?:true)}
        frame=WidgetFrame(settings,items)
    }
    override fun onDestroy(){frame=WidgetFrame()}
    override fun getCount()=frame.items.size
    override fun getViewAt(position:Int):RemoteViews? {
        val snapshot=frame
        val item=snapshot.items.getOrNull(position)?:return null
        val size=snapshot.settings.textSize
        if(item is WidgetItem.Header)return RemoteViews(context.packageName,R.layout.widget_section).apply {
            setTextViewText(R.id.widget_section_title,item.title)
            setTextColor(R.id.widget_section_title,android.graphics.Color.parseColor(if(item.depth==2)"#66757B"else "#24667A"))
            setTextViewText(R.id.widget_section_detail,item.subtitle.orEmpty())
            setViewVisibility(R.id.widget_section_detail,if(item.subtitle.isNullOrBlank())View.GONE else View.VISIBLE)
            setTextViewTextSize(R.id.widget_section_title,TypedValue.COMPLEX_UNIT_SP,(size+if(item.depth==0)1 else if(item.depth==1)0 else -2).toFloat())
            setTextViewTextSize(R.id.widget_section_detail,TypedValue.COMPLEX_UNIT_SP,(size-3).coerceAtLeast(10).toFloat())
            setInt(R.id.widget_section_title,"setPaintFlags",Paint.ANTI_ALIAS_FLAG or if(item.depth<2)Paint.FAKE_BOLD_TEXT_FLAG else 0)
            val density=context.resources.displayMetrics.density
            setViewPadding(R.id.widget_section,((8+item.depth*8)*density).toInt(),((if(item.depth==0)12 else 4)*density).toInt(),(8*density).toInt(),(4*density).toInt())
            setOnClickFillInIntent(R.id.widget_section,Intent().putExtra("open",true))
        }
        val row=(item as WidgetItem.Purchase).row
        return RemoteViews(context.packageName,R.layout.widget_purchase).apply {
            setTextViewText(R.id.widget_item_title,(if(row.urgent&&!row.bought)"! "else "")+row.title)
            setTextViewText(R.id.widget_item_detail,row.detail)
            setTextViewTextSize(R.id.widget_item_title,TypedValue.COMPLEX_UNIT_SP,size.toFloat())
            setTextViewTextSize(R.id.widget_item_detail,TypedValue.COMPLEX_UNIT_SP,(size-3).coerceAtLeast(10).toFloat())
            setInt(R.id.widget_item_title,"setPaintFlags",Paint.ANTI_ALIAS_FLAG or if(row.bought)Paint.STRIKE_THRU_TEXT_FLAG else 0)
            setImageViewResource(R.id.widget_check,if(row.bought)R.drawable.widget_checked else R.drawable.widget_unchecked)
            setContentDescription(R.id.widget_check,(if(row.bought)"Вернуть: "else "Отметить купленным: ")+row.title)
            setOnClickFillInIntent(R.id.widget_check,ShoppingWidgetProvider.checkIntent(row))
            setOnClickFillInIntent(R.id.widget_item_text,Intent().putExtra("open",true))
        }
    }
    override fun getLoadingView():RemoteViews?=null
    override fun getViewTypeCount()=2
    override fun getItemId(position:Int)=frame.items.getOrNull(position)?.stableId?:-1L
    override fun hasStableIds()=true
}
