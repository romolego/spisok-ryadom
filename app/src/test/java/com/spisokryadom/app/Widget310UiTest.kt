package com.spisokryadom.app

import android.appwidget.AppWidgetManager
import android.content.*
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.ProductEntity
import com.spisokryadom.app.widget.*
import com.spisokryadom.app.util.DatabaseRevision
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import java.io.File
import java.time.Duration

/** Inflate/reapply the real collection RemoteViews, including native animation resources. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=SpisokRyadomApp::class,qualifiers="w360dp-h800dp-xhdpi",shadows=[Widget310UiTest.PlatformViewAnimator::class])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class Widget310UiTest {
    /** Robolectric's default shadow only changes visibility and drops all animations.
     * Exercise Android's actual setDisplayedChild/showOnly implementation instead. */
    @Implements(ViewAnimator::class)
    class PlatformViewAnimator:org.robolectric.shadows.ShadowViewAnimator() {
        @RealObject private lateinit var animator:ViewAnimator
        @Implementation override fun setDisplayedChild(child:Int){
            org.robolectric.shadow.api.Shadow.directlyOn<Void,ViewAnimator>(animator,ViewAnimator::class.java,"setDisplayedChild",org.robolectric.util.ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType,child))
        }
        @Implementation override fun getDisplayedChild():Int=org.robolectric.shadow.api.Shadow.directlyOn(animator,ViewAnimator::class.java,"getDisplayedChild")
    }
    private val app get()=ApplicationProvider.getApplicationContext<SpisokRyadomApp>()
    private val db get()=app.container.database
    private val prefs get()=app.getSharedPreferences(WidgetSettings.PREFS,0)
    private val data get()=ShoppingWidgetData(app.container)
    private var secret=0L;private var plain=0L
    private val receiver=ShoppingWidgetProvider()
    private val factories=mutableListOf<ShoppingWidgetFactory>()
    private fun await(ms:Long=10_000,test:()->Boolean){val end=System.nanoTime()+ms*1_000_000;while(!test()&&System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20));Thread.sleep(10)};assertTrue(test())}
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag") // Pre-33 Robolectric registration only; production uses its private manifest receiver.
    @Before fun seed():Unit=runBlocking {
        prefs.edit().clear().commit();app.getSharedPreferences("product_card_drafts",0).edit().clear().commit();WidgetInteractionState.clear();WidgetPurchaseMotion.clear()
        db.shoppingListEntryDao().deleteAll();db.goalListDao().deleteAll();db.productDao().deleteAll();db.shopDao().deleteAll()
        WidgetSettings(mode=com.spisokryadom.app.ui.shoppinglist.ViewMode.FLAT).save(prefs)
        val a=app.container.productRepository.saveWithLinks(ProductEntity(name="Личный товар",defaultUnit="упак",isWidgetPrivate=true),emptyList())
        val b=app.container.productRepository.saveWithLinks(ProductEntity(name="Хлеб",defaultUnit="шт"),emptyList())
        secret=app.container.shoppingListRepository.addProduct(a,quantity=2.0);plain=app.container.shoppingListRepository.addProduct(b)
        val filter=IntentFilter(ShoppingWidgetProvider.ACTION)
        if(android.os.Build.VERSION.SDK_INT>=33)app.registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED)
        else {@Suppress("DEPRECATION") app.registerReceiver(receiver,filter)}
    }
    @After fun close(){app.unregisterReceiver(receiver);factories.forEach{it.onDestroy()};WidgetInteractionState.clear();WidgetPurchaseMotion.clear();app.container.locations.closeForTest();db.close();org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null)}
    private fun factory(id:Int=310)=ShoppingWidgetFactory(app,id).also{it.onCreate();factories.add(it)}
    private fun rv(f:ShoppingWidgetFactory,id:Long)=f.getViewAt((0 until f.count).single{f.getItemId(it)==id})!!
    private fun view(f:ShoppingWidgetFactory,id:Long)=rv(f,id).apply(app,FrameLayout(app))
    private fun title(v:View)=v.findViewById<TextView>(R.id.widget_item_title).text.toString()
    private fun allText(v:View):String {
        val own=(v as? TextView)?.text?.toString().orEmpty()+v.contentDescription?.toString().orEmpty()
        return own+if(v is android.view.ViewGroup)(0 until v.childCount).joinToString(""){allText(v.getChildAt(it))}else ""
    }
    private fun shot(name:String,v:View){
        val host=FrameLayout(app);host.addView(v)
        val controller=Robolectric.buildActivity(android.app.Activity::class.java).setup().visible()
        try {
            controller.get().setContentView(host)
            host.setLayerType(View.LAYER_TYPE_SOFTWARE,null)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            host.measure(View.MeasureSpec.makeMeasureSpec(660,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(500,View.MeasureSpec.AT_MOST));host.layout(0,0,host.measuredWidth,host.measuredHeight)
            val card=v.findViewById<ViewFlipper>(R.id.widget_purchase_motion)
            assertEquals(View.VISIBLE,card.getChildAt(0).visibility)
            val text=v.findViewById<TextView>(R.id.widget_item_title)
            assertTrue(text.height>0);assertTrue(text.top>=0)
            val bitmap=Bitmap.createBitmap(host.width,host.height,Bitmap.Config.ARGB_8888);host.draw(Canvas(bitmap))
            val file=File("build/reports/widget310/screens/$name-api${android.os.Build.VERSION.SDK_INT}.png");file.parentFile!!.mkdirs();file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }finally{controller.pause().stop().destroy()}
    }
    @Test fun concealedRowAndReusedRemoteViewsContainNoPrivateTextOrQuantity():Unit=runBlocking {
        val f=factory();val reused=view(f,plain);assertEquals("Хлеб",title(reused));assertEquals(View.GONE,reused.findViewById<View>(R.id.widget_privacy).visibility)
        rv(f,secret).reapply(app,reused)
        assertEquals(View.VISIBLE,reused.findViewById<View>(R.id.widget_privacy).visibility);assertEquals("● ● ● ● ● ●",title(reused));assertFalse(allText(reused).contains("Личный товар"));assertFalse(allText(reused).contains("2 упак"))
        assertTrue(allText(reused).contains("скрытый товар"));shot("masked",reused)
        rv(f,plain).reapply(app,reused);assertEquals("Хлеб",title(reused));assertFalse(allText(reused).contains("Скрытый товар"))
    }
    @Test fun visibilityIsDurableAndSharedAcrossWidgetHosts():Unit=runBlocking {
        val f=factory();val other=factory(311);val before=db.shoppingListEntryDao().getAllSync()
        val row=data.rows().single{it.id==secret}
        assertTrue(data.visibility(row,true))
        f.onDataSetChanged();other.onDataSetChanged()
        val revealed=view(f,secret);assertTrue(title(revealed).contains("Личный товар"))
        assertEquals("2 упак",revealed.findViewById<TextView>(R.id.widget_item_detail).text.toString())
        assertTrue(allText(view(other,secret)).contains("Личный товар"));shot("revealed",revealed)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6));WidgetInteractionState.clear();WidgetPurchaseMotion.clear()
        f.onDataSetChanged();assertTrue(title(view(f,secret)).contains("Личный товар"))
        assertTrue(data.visibility(data.rows().single{it.id==secret},false))
        f.onDataSetChanged();assertFalse(allText(view(f,secret)).contains("Личный товар"));assertEquals(before,db.shoppingListEntryDao().getAllSync())
    }
    @Test fun productionVisibilityReceiverPersistsWithoutOpeningAppOrMutatingPurchase():Unit=runBlocking {
        val f=factory();val row=data.rows().single{it.id==secret};val before=db.shoppingListEntryDao().getAllSync()
        val action=ShoppingWidgetProvider.privacyIntent(row).setComponent(ComponentName(app,ShoppingWidgetProvider::class.java)).setAction(ShoppingWidgetProvider.ACTION)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,310).putExtra("database",DatabaseRevision.current(app))
        assertFalse(action.extras!!.keySet().any{action.extras!!.get(it).toString().contains("Личный товар")})
        WidgetInteractionState.clear();WidgetPurchaseMotion.clear() // Simulate loss of the old process with a cached launcher row.
        val host=FrameLayout(app)
        val root=ShoppingWidgetProvider.views(app,310).apply(app,host);host.addView(root)
        val list=root.findViewById<ListView>(R.id.widget_list)
        list.adapter=object:BaseAdapter(){
            override fun getCount()=f.count
            override fun getItem(position:Int)=position
            override fun getItemId(position:Int)=f.getItemId(position)
            override fun getView(position:Int,convertView:View?,parent:android.view.ViewGroup):View {
                val child=f.getViewAt(position)!!
                // RemoteViewsService marks collection children on the binder boundary.
                if(android.os.Build.VERSION.SDK_INT<=28)org.robolectric.util.ReflectionHelpers.callInstanceMethod<Void>(child,"setIsWidgetCollectionChild",org.robolectric.util.ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType,true))
                else org.robolectric.util.ReflectionHelpers.callInstanceMethod<Void>(child,"addFlags",org.robolectric.util.ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType,2))
                return child.apply(app,parent)
            }
        }
        host.measure(View.MeasureSpec.makeMeasureSpec(660,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(850,View.MeasureSpec.EXACTLY));host.layout(0,0,660,850)
        val index=(0 until f.count).single{f.getItemId(it)==secret}
        assertTrue(list.getChildAt(index).findViewById<View>(R.id.widget_privacy).performClick())
        await{runBlocking{!db.shoppingListEntryDao().getById(secret)!!.widgetHidden}}
        f.onDataSetChanged();assertTrue(title(view(f,secret)).contains("Личный товар"))
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6));WidgetInteractionState.clear();WidgetPurchaseMotion.clear();f.onDataSetChanged();assertTrue(title(view(f,secret)).contains("Личный товар"))
        assertEquals(before.map{if(it.id==secret)it.copy(widgetHidden=false)else it},db.shoppingListEntryDao().getAllSync());assertTrue(db.productDao().getByStableId(row.productStableId)!!.isWidgetPrivate);assertNull(Shadows.shadowOf(app).nextStartedActivity)
    }
    @Test fun realRemoteViewsAnimateDownWithoutBlockingAnotherPurchase():Unit=runBlocking {
        val f=factory();val row=data.rows().single{it.id==secret};val first=view(f,secret)
        assertTrue(data.check(row));WidgetPurchaseMotion.begin(row,DatabaseRevision.current(app));assertTrue(db.shoppingListEntryDao().getById(secret)!!.isBought)
        f.onDataSetChanged();rv(f,secret).reapply(app,first)
        val flipper=first.findViewById<ViewFlipper>(R.id.widget_purchase_motion)
        assertEquals(1,flipper.displayedChild);assertNotNull(flipper.outAnimation);assertEquals(140L,flipper.outAnimation.duration)
        assertNotNull(flipper.getChildAt(0).animation)
        assertTrue(data.check(data.rows().single{it.id==plain})) // The next input never waits for motion.
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));f.onDataSetChanged();rv(f,secret).reapply(app,first)
        assertEquals(0,first.findViewById<ViewFlipper>(R.id.widget_purchase_motion).displayedChild)
        assertFalse(title(first).contains("Личный товар"));shot("bought-masked",first)
    }
    @Test fun hiddenBoughtFiltersImmediatelyWithoutDeletingEntry():Unit=runBlocking {
        WidgetSettings(mode=com.spisokryadom.app.ui.shoppinglist.ViewMode.FLAT,showBought=false).save(prefs)
        val f=factory();val row=data.rows().single{it.id==secret}
        assertTrue(data.check(row));f.onDataSetChanged();assertFalse((0 until f.count).map{f.getItemId(it)}.contains(secret))
        assertNotNull(db.shoppingListEntryDao().getById(secret));assertEquals(2,db.shoppingListEntryDao().getAllSync().size)
    }
}
