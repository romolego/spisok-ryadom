package com.spisokryadom.app.widget

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.room.withTransaction
import com.spisokryadom.app.R
import com.spisokryadom.app.SpisokRyadomApp
import kotlinx.coroutines.runBlocking

class ShoppingWidgetService:RemoteViewsService() {
    override fun onGetViewFactory(intent:Intent):RemoteViewsFactory=ShoppingWidgetFactory(applicationContext,intent.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID,android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID))
}

class ShoppingWidgetFactory(private val context:Context,private val widgetId:Int=android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID):RemoteViewsService.RemoteViewsFactory {
    @Volatile private var frame=WidgetFrame()
    override fun onCreate()=onDataSetChanged()
    override fun onDataSetChanged(){
        val prefs=context.getSharedPreferences(WidgetSettings.PREFS,Context.MODE_PRIVATE)
        val settings=WidgetSettings.load(prefs)
        val (items,departing)=runBlocking{
            val container=(context.applicationContext as SpisokRyadomApp).container
            val data=ShoppingWidgetData(container)
            val boost=prefs.all["global_default_boost_urgent_shop"] as? Boolean?:true
            container.database.withTransaction {
            val canonical=data.presentation(settings,boost)
            val all=if(!settings.showBought&&WidgetInteractionState.moving(widgetId))data.presentation(settings.copy(showBought=true),boost)else canonical
            WidgetInteractionState.present(widgetId,com.spisokryadom.app.util.DatabaseRevision.current(context),settings,canonical,all)
            }
        }
        frame=WidgetFrame(settings,items,departing,com.spisokryadom.app.util.FavoriteAppearance.read(prefs).purchaseRow)
    }
    override fun onDestroy(){frame=WidgetFrame()}
    override fun getCount()=frame.items.size
    override fun getViewAt(position:Int):RemoteViews? {
        val snapshot=frame
        val item=snapshot.items.getOrNull(position)?:return null
        val size=snapshot.settings.textSize
        if(item is WidgetItem.Header)return RemoteViews(context.packageName,R.layout.widget_section).apply {
            setInt(R.id.widget_section,"setBackgroundColor",WidgetAppearance.shopBackground(item,snapshot.settings))
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
        val purchase=item as WidgetItem.Purchase
        val row=purchase.row
        val peek=WidgetInteractionState.peekToken(widgetId,row)
        val hidden=row.isPrivate&&peek==null
        return RemoteViews(context.packageName,R.layout.widget_purchase).apply {
            setInt(R.id.widget_purchase_root,"setBackgroundColor",WidgetAppearance.shopBackground(purchase,snapshot.settings))
            // Tint the rounded drawable itself: XML clipToOutline is unavailable below API31.
            // Assign even white on every bind so reused rows cannot retain another color.
            setInt(R.id.widget_purchase_background,"setColorFilter",WidgetAppearance.productBackground(purchase,snapshot.settings,snapshot.favoriteStyle))
            setTextViewText(R.id.widget_item_title,if(hidden)"● ● ● ● ● ●" else (if(row.urgent&&!row.bought)"! "else "")+
                (if(purchase.isFavorite&&snapshot.favoriteStyle.star)"★ "else "")+row.title)
            setTextViewText(R.id.widget_item_detail,if(hidden)"Нажмите, чтобы показать" else row.detail)
            setTextViewTextSize(R.id.widget_item_title,TypedValue.COMPLEX_UNIT_SP,size.toFloat())
            setTextViewTextSize(R.id.widget_item_detail,TypedValue.COMPLEX_UNIT_SP,(size-3).coerceAtLeast(10).toFloat())
            val bold=purchase.isFavorite&&snapshot.favoriteStyle.bold||row.urgent&&!row.bought
            setInt(R.id.widget_item_title,"setPaintFlags",Paint.ANTI_ALIAS_FLAG or
                (if(row.bought)Paint.STRIKE_THRU_TEXT_FLAG else 0) or (if(bold)Paint.FAKE_BOLD_TEXT_FLAG else 0))
            setImageViewResource(R.id.widget_check,if(row.bought)R.drawable.widget_checked else R.drawable.widget_unchecked)
            setContentDescription(R.id.widget_check,(if(row.bought)"Вернуть: "else "Отметить купленным: ")+if(hidden)"скрытый товар"else row.title)
            setOnClickFillInIntent(R.id.widget_check,ShoppingWidgetProvider.checkIntent(row))
            setContentDescription(R.id.widget_item_text,if(hidden)"Скрытый товар. Нажмите, чтобы показать на 5 секунд"else if(row.isPrivate)"${row.title}. ${row.detail}. Нажмите, чтобы скрыть"else null)
            setOnClickFillInIntent(R.id.widget_item_text,if(row.isPrivate)ShoppingWidgetProvider.peekIntent(row,peek.orEmpty())
                .putExtra("database",com.spisokryadom.app.util.DatabaseRevision.current(context))else Intent().putExtra("open",true))
            // ViewFlipper is supported by RemoteViews, including Android 8. No per-frame IPC.
            setInt(R.id.widget_purchase_motion,"setDisplayedChild",if(row.id in snapshot.departing)1 else 0)
        }
    }
    override fun getLoadingView():RemoteViews?=null
    override fun getViewTypeCount()=2
    override fun getItemId(position:Int)=frame.items.getOrNull(position)?.stableId?:-1L
    override fun hasStableIds()=true

}
