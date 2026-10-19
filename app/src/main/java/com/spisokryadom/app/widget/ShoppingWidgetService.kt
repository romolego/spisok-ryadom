package com.spisokryadom.app.widget

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.spisokryadom.app.R
import com.spisokryadom.app.SpisokRyadomApp
import kotlinx.coroutines.runBlocking

class ShoppingWidgetService:RemoteViewsService() {
    override fun onGetViewFactory(intent:Intent):RemoteViewsFactory=ShoppingWidgetFactory(applicationContext)
}

class ShoppingWidgetFactory(private val context:Context):RemoteViewsService.RemoteViewsFactory {
    @Volatile private var rows:List<WidgetPurchase> = emptyList()
    override fun onCreate()=onDataSetChanged()
    override fun onDataSetChanged(){rows=runBlocking{ShoppingWidgetData((context.applicationContext as SpisokRyadomApp).container).rows()}}
    override fun onDestroy(){rows=emptyList()}
    override fun getCount()=rows.size
    override fun getViewAt(position:Int):RemoteViews? {
        val row=rows.getOrNull(position)?:return null
        return RemoteViews(context.packageName,R.layout.widget_purchase).apply {
            setTextViewText(R.id.widget_item_title,(if(row.urgent&&!row.bought)"! "else "")+row.title)
            setTextViewText(R.id.widget_item_detail,row.detail)
            setInt(R.id.widget_item_title,"setPaintFlags",Paint.ANTI_ALIAS_FLAG or if(row.bought)Paint.STRIKE_THRU_TEXT_FLAG else 0)
            setImageViewResource(R.id.widget_check,if(row.bought)R.drawable.widget_checked else R.drawable.widget_unchecked)
            setContentDescription(R.id.widget_check,(if(row.bought)"Вернуть: "else "Отметить купленным: ")+row.title)
            setOnClickFillInIntent(R.id.widget_check,ShoppingWidgetProvider.checkIntent(row))
            setOnClickFillInIntent(R.id.widget_item_text,Intent().putExtra("open",true))
        }
    }
    override fun getLoadingView():RemoteViews?=null
    override fun getViewTypeCount()=1
    override fun getItemId(position:Int)=rows.getOrNull(position)?.id?:-1L
    override fun hasStableIds()=true
}
