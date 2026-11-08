package com.spisokryadom.app

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.IntentFilter
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.backup.BackupManager
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.ProductEntity
import com.spisokryadom.app.util.DatabaseRevision
import com.spisokryadom.app.widget.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowBroadcastPendingResult
import java.time.Duration

/** Old launcher frames must not operate on, or disclose, a restored database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=SpisokRyadomApp::class,qualifiers="w360dp-h800dp-xhdpi")
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class Audit311WidgetTest {
    private val app get()=ApplicationProvider.getApplicationContext<SpisokRyadomApp>()
    private val db get()=app.container.database
    private val data get()=ShoppingWidgetData(app.container)
    private val receiver=ShoppingWidgetProvider()
    private val factories=mutableListOf<ShoppingWidgetFactory>()
    private val broadcasts=mutableListOf<android.content.BroadcastReceiver.PendingResult>()
    private var purchase=0L
    private val widgetId=731
    private fun await(test:()->Boolean){
        val end=System.nanoTime()+10_000_000_000
        while(!test()&&System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20));Thread.sleep(5)}
        assertTrue(test())
    }
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag") // Test-only pre-33 registration of the private manifest receiver.
    @Before fun seed():Unit=runBlocking {
        WidgetInteractionState.clear()
        app.getSharedPreferences(WidgetSettings.PREFS,0).edit().clear().commit()
        app.getSharedPreferences("product_card_drafts",0).edit().clear().commit()
        db.shoppingListEntryDao().deleteAll();db.goalListDao().deleteAll();db.productDao().deleteAll();db.shopDao().deleteAll()
        WidgetSettings(mode=com.spisokryadom.app.ui.shoppinglist.ViewMode.FLAT).save(app.getSharedPreferences(WidgetSettings.PREFS,0))
        val product=app.container.productRepository.saveWithLinks(ProductEntity(name="Личный товар",isWidgetPrivate=true),emptyList())
        purchase=app.container.shoppingListRepository.addProduct(product)
        val filter=IntentFilter(ShoppingWidgetProvider.ACTION)
        if(android.os.Build.VERSION.SDK_INT>=33)app.registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED)
        else {@Suppress("DEPRECATION") app.registerReceiver(receiver,filter)}
    }
    @After fun close(){
        awaitFinished()
        app.unregisterReceiver(receiver);factories.forEach{it.onDestroy()};WidgetInteractionState.clear()
        app.container.locations.closeForTest();db.close()
        org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null)
    }
    private fun factory(id:Int=widgetId)=ShoppingWidgetFactory(app,id).also{it.onCreate();factories.add(it)}
    private fun position(f:ShoppingWidgetFactory)=(0 until f.count).single{f.getItemId(it)==purchase}
    private fun host(f:ShoppingWidgetFactory,id:Int=widgetId):View {
        val outer=FrameLayout(app)
        val root=ShoppingWidgetProvider.views(app,id).apply(app,outer);outer.addView(root)
        val list=root.findViewById<ListView>(R.id.widget_list)
        list.adapter=object:BaseAdapter(){
            override fun getCount()=f.count
            override fun getItem(p:Int)=p
            override fun getItemId(p:Int)=f.getItemId(p)
            override fun getView(p:Int,v:View?,parent:ViewGroup):View {
                val child=f.getViewAt(p)!!
                if(android.os.Build.VERSION.SDK_INT<=28)org.robolectric.util.ReflectionHelpers.callInstanceMethod<Void>(child,"setIsWidgetCollectionChild",org.robolectric.util.ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType,true))
                else org.robolectric.util.ReflectionHelpers.callInstanceMethod<Void>(child,"addFlags",org.robolectric.util.ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType,2))
                return child.apply(app,parent)
            }
        }
        outer.measure(View.MeasureSpec.makeMeasureSpec(660,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(850,View.MeasureSpec.EXACTLY));outer.layout(0,0,660,850)
        return list.getChildAt(position(f))
    }
    private fun click(view:View,id:Int){
        val previous=Shadows.shadowOf(receiver).originalPendingResult
        assertTrue(view.findViewById<View>(id).performClick())
        await{Shadows.shadowOf(receiver).originalPendingResult?.let{it!==previous}==true}
        broadcasts.add(Shadows.shadowOf(receiver).originalPendingResult!!)
    }
    private fun finished():Boolean=Shadows.shadowOf(receiver).originalPendingResult?.let{
        org.robolectric.shadow.api.Shadow.extract<ShadowBroadcastPendingResult>(it).future.isDone
    }?:true
    private fun awaitFinished(){await{broadcasts.all{org.robolectric.shadow.api.Shadow.extract<ShadowBroadcastPendingResult>(it).future.isDone}}}

    @Test fun cachedCheckboxCannotChangeAnIdenticalPurchaseAfterFullRestore():Unit=runBlocking {
        val f=factory();val cached=host(f)
        val before=db.shoppingListEntryDao().getById(purchase)!!
        val manager=BackupManager(app,db);val backup=manager.exportFile()
        val oldRevision=DatabaseRevision.current(app);manager.importFile(backup)
        assertNotEquals(oldRevision,DatabaseRevision.current(app))
        assertEquals(before,db.shoppingListEntryDao().getById(purchase))
        click(cached,R.id.widget_check);awaitFinished()
        assertEquals("A cached click belongs to the pre-restore database",before,db.shoppingListEntryDao().getById(purchase))
        f.onDataSetChanged();click(host(f),R.id.widget_check);awaitFinished()
        assertTrue(db.shoppingListEntryDao().getById(purchase)!!.isBought)
    }

    @Test fun staleFactoryCannotStampItsOldRowsWithTheRestoredDatabaseIdentity():Unit=runBlocking {
        val f=factory();val manager=BackupManager(app,db);val backup=manager.exportFile()
        manager.importFile(backup)
        assertNull("Do not render a stale frame as if it came from the restored database",f.getViewAt(position(f)))
        f.onDataSetChanged()
        val fresh=f.getViewAt(position(f))!!.apply(app,FrameLayout(app))
        assertEquals("● ● ● ● ● ●",fresh.findViewById<TextView>(R.id.widget_item_title).text.toString())
    }

    @Test fun cachedVisibilityAndLostProcessStateRespectDatabaseIdentity():Unit=runBlocking {
        val f=factory();val cached=host(f);val manager=BackupManager(app,db);val backup=manager.exportFile()
        manager.importFile(backup)
        val id=db.shoppingListEntryDao().getById(purchase)!!.productId
        click(cached,R.id.widget_privacy);awaitFinished()
        assertTrue(db.productDao().getById(id)!!.isWidgetPrivate)
        f.onDataSetChanged();val fresh=host(f)
        WidgetInteractionState.clear()
        click(fresh,R.id.widget_privacy);awaitFinished()
        assertTrue(db.productDao().getById(id)!!.isWidgetPrivate)
        assertFalse(db.shoppingListEntryDao().getById(purchase)!!.widgetHidden)
        assertFalse(db.shoppingListEntryDao().getById(purchase)!!.isBought)
    }

    @Test fun rapidRepeatsAndAnotherWidgetDoNotUndoTheRecordedPurchase():Unit=runBlocking {
        val first=factory();val second=factory(widgetId+1)
        val cachedFirst=host(first);val cachedSecond=host(second,widgetId+1)
        val before=db.shoppingListEntryDao().getById(purchase)!!
        click(cachedFirst,R.id.widget_check)
        await{runBlocking{db.shoppingListEntryDao().getById(purchase)!!.isBought}}
        click(cachedFirst,R.id.widget_check);click(cachedSecond,R.id.widget_check)
        awaitFinished()
        val after=db.shoppingListEntryDao().getById(purchase)!!
        assertTrue(after.isBought);assertEquals(before.quantity,after.quantity,0.0)
        assertEquals(before.productId,after.productId);assertEquals(1,db.shoppingListEntryDao().getAllSync().size)
        first.onDataSetChanged();second.onDataSetChanged()
        for(f in listOf(first,second)){
            val view=f.getViewAt(position(f))!!.apply(app,FrameLayout(app))
            assertEquals("Вернуть: скрытый товар",view.findViewById<View>(R.id.widget_check).contentDescription)
            assertEquals("● ● ● ● ● ●",view.findViewById<TextView>(R.id.widget_item_title).text.toString())
        }
    }
}
