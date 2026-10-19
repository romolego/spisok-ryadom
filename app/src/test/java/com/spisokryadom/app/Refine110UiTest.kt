package com.spisokryadom.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.ShopChoice
import com.spisokryadom.app.widget.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.*
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=SpisokRyadomApp::class,qualifiers="w360dp-h640dp-xhdpi")
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Refine110UiTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 private val app get()=ApplicationProvider.getApplicationContext<SpisokRyadomApp>()
 private val db get()=app.container.database
 private var p=0L;private var shop=0L;private var branch=0L;private var entry=0L;private var baton=0L
 private fun waitText(t:String){ui.waitUntil(20_000){Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(20));ui.waitForIdle();ui.onAllNodesWithText(t).fetchSemanticsNodes().isNotEmpty()}}
 private fun shot(name:String,view:View=ui.activity.window.decorView){ui.waitForIdle();ui.runOnIdle{val b=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(b));val f=File("../.local/refinement-0.11.0/$name-api${android.os.Build.VERSION.SDK_INT}.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 @Before fun setup(){runBlocking{
  listOf("drafts","product_card_drafts","shopping_list_prefs","product_db_prefs").forEach{app.getSharedPreferences(it,0).edit().clear().commit()}
  db.shoppingListEntryDao().deleteAll();db.productDao().deleteAll();db.shopDao().deleteAll();db.retailStructureDao().clearVenues();db.cityDao().update(CityEntity(1,"Красноярск"));app.container.locations.select(1)
  val venue=db.retailStructureDao().insertVenue(VenueEntity(cityId=1,name="Енисейский привоз"));shop=db.shopDao().insert(ShopEntity(name="29 павильон (сыры)"));branch=db.shopBranchDao().insert(ShopBranchEntity(shopId=shop,address="29 павильон (сыры)",venueId=venue));baton=db.shopDao().insert(ShopEntity(name="Батон"))
  p=app.container.productRepository.saveWithLinks(ProductEntity(name="Сыр косичка",defaultUnit="шт"),listOf(ProductShopLinkEntity(productId=0,shopId=shop),ProductShopLinkEntity(productId=0,shopId=baton,priority=2)))
  entry=app.container.shoppingListRepository.addProduct(p,choice=ShopChoice.EXPLICIT,shopId=baton);Unit
 };waitText("Сыр косичка")}
 @After fun close(){ui.activityRule.scenario.close();app.container.locations.closeForTest();db.close();org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null)}
 @Test fun shareSendsVisibleEditedTextWithoutSavingBasket(){
  ui.onNodeWithContentDescription("Текстовый список").performClick();waitText("Ваш список");ui.onNodeWithText("Ваш список").performTextReplacement("Газировка — 1 бутылка\nОгурцы — 2 банки")
  ui.onNodeWithContentDescription("Поделиться списком").performClick();val chooser=Shadows.shadowOf(ui.activity).nextStartedActivity;assertEquals(Intent.ACTION_CHOOSER,chooser.action)
  @Suppress("DEPRECATION") val send=chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
  assertEquals("Газировка — 1 бутылка\nОгурцы — 2 банки",send.getStringExtra(Intent.EXTRA_TEXT));assertEquals("text/plain",send.type);assertEquals(1,runBlocking{db.shoppingListEntryDao().getAllSync().size});shot("01-share-text")
 }
 @Test fun productCardShowsMarketBeforePavilion(){
  ui.onNodeWithText("Сыр косичка").performClick();waitText("Карточка товара");ui.onNodeWithText("Карточка товара").performClick();waitText("Название товара")
  ui.onNodeWithText("1. Енисейский привоз — 29 павильон (сыры)").performScrollTo().assertIsDisplayed();shot("02-card-place")
 }
 @Test fun movePickerSeparatesMarketAndPavilion(){
  ui.onNodeWithText("Сыр косичка").performClick();waitText("Сменить место покупки");ui.onNodeWithText("Сменить место покупки").performClick();waitText("Енисейский привоз")
  ui.onNodeWithText("Енисейский привоз").assertIsDisplayed();ui.onNodeWithText("29 павильон (сыры)").assertIsDisplayed();assertEquals(1,ui.onAllNodesWithText("29 павильон (сыры)").fetchSemanticsNodes().size)
  shot("03-move-place",org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView)
  ui.onNodeWithText("Добавить место покупки").performClick();waitText("Новый магазин…");ui.onNodeWithText("Новый магазин…").performClick()
  ui.onNodeWithText("Название магазина").performTextInput("29 павильон (сыры)");ui.onNodeWithContentDescription("Создать: Название магазина").performClick()
  ui.waitUntil(20_000){Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(20));ui.waitForIdle();ui.onAllNodesWithContentDescription("Отмена: Название магазина").fetchSemanticsNodes().isEmpty()}
  waitText("Енисейский привоз");ui.onNodeWithText("Енисейский привоз").assertIsDisplayed();ui.onNodeWithText("29 павильон (сыры)").assertIsDisplayed()
  assertEquals(2,runBlocking{db.shopDao().getAllSync().size})
 }
 @Test fun widgetAddIntentOpensAddPurchaseEvenFromAnotherScreen(){
  ui.onNodeWithText("Товары").performClick();waitText("База товаров")
  val scenarioIntent=ui.activity.intent
  ui.runOnUiThread{org.robolectric.util.ReflectionHelpers.callInstanceMethod<Unit>(ui.activity,"onNewIntent",org.robolectric.util.ReflectionHelpers.ClassParameter.from(Intent::class.java,WidgetIntents.open(app,true)))}
  try {
   waitText("Название товара");ui.onNodeWithText("Название товара").performTextInput("Газировка")
   ui.runOnUiThread{org.robolectric.util.ReflectionHelpers.callInstanceMethod<Unit>(ui.activity,"onNewIntent",org.robolectric.util.ReflectionHelpers.ClassParameter.from(Intent::class.java,WidgetIntents.open(app,true)))}
   ui.waitForIdle();ui.onNodeWithText("Название товара").assertTextContains("Газировка");shot("04-widget-add")
  }
  finally {ui.runOnUiThread{ui.activity.intent=scenarioIntent}}
 }
 @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag") // SDK<33 test branch; production receiver is private in the manifest.
 @Test fun widgetRemoteViewsRenderScrollableRowsAndCheckedState(){
  runBlocking{repeat(12){i->val id=db.productDao().insert(ProductEntity(name=if(i==0)"Огурцы солёные в большой стеклянной банке"else "Покупка ${i+1}"));app.container.shoppingListRepository.addProduct(id)}}
  val factory=ShoppingWidgetFactory(app);factory.onCreate();assertEquals(13,factory.count)
  val host=FrameLayout(ui.activity);val root=ShoppingWidgetProvider.views(app,71).apply(ui.activity,host);host.addView(root)
  val list=root.findViewById<ListView>(R.id.widget_list)
  list.adapter=object:BaseAdapter(){override fun getCount()=factory.count;override fun getItem(p:Int)=p;override fun getItemId(p:Int)=factory.getItemId(p);override fun getView(p:Int,v:View?,parent:ViewGroup)=factory.getViewAt(p)!!.apply(ui.activity,parent)}
  host.measure(View.MeasureSpec.makeMeasureSpec(660,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(900,View.MeasureSpec.EXACTLY));host.layout(0,0,660,900)
  assertEquals("Список покупок",root.findViewById<TextView>(R.id.widget_heading).text);assertEquals(13,list.count);assertTrue(list.getChildAt(0).findViewById<TextView>(R.id.widget_item_detail).text.contains("Батон"));assertTrue(list.canScrollVertically(1));shot("05-widget-remoteviews",host)
  val token=runBlocking{ShoppingWidgetData(app.container).rows().first{it.id==entry}}
  val receiverInfo=app.packageManager.getReceiverInfo(android.content.ComponentName(app,ShoppingWidgetProvider::class.java),android.content.pm.PackageManager.GET_META_DATA)
  assertFalse(receiverInfo.exported);assertTrue(receiverInfo.metaData.containsKey("android.appwidget.provider"))
  assertEquals("android.permission.BIND_REMOTEVIEWS",app.packageManager.getServiceInfo(android.content.ComponentName(app,ShoppingWidgetService::class.java),0).permission)
  // Robolectric delivers runtime registrations; the production receiver is manifest-declared.
  val receiver=ShoppingWidgetProvider()
  if(android.os.Build.VERSION.SDK_INT>=33)app.registerReceiver(receiver,android.content.IntentFilter(ShoppingWidgetProvider.ACTION),android.content.Context.RECEIVER_NOT_EXPORTED)
  else { @Suppress("DEPRECATION") app.registerReceiver(receiver,android.content.IntentFilter(ShoppingWidgetProvider.ACTION)) }
  try {
   app.sendBroadcast(Intent(ShoppingWidgetProvider.ACTION).setPackage(app.packageName).putExtras(ShoppingWidgetProvider.checkIntent(token)))
   ui.waitUntil(20_000){Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(20));runBlocking{db.shoppingListEntryDao().getById(entry)!!.isBought}}
  } finally {app.unregisterReceiver(receiver)}
  factory.onDataSetChanged();val position=(0 until factory.count).single{factory.getItemId(it)==entry}
  val checked=factory.getViewAt(position)!!.apply(ui.activity,host);assertEquals("Вернуть: Сыр косичка",checked.findViewById<View>(R.id.widget_check).contentDescription);factory.onDestroy()
 }
 @Test fun textReviewRoundTripUnderstandsContextualPavilionHeading(){
  runBlocking{app.container.shoppingListRepository.movePending(setOf(entry),shop,branch)}
  ui.onNodeWithContentDescription("Текстовый список").performClick();waitText("Ваш список")
  ui.onNodeWithText("Ваш список").assertTextContains("Енисейский привоз — 29 павильон (сыры):",substring=true)
  ui.onNodeWithText("Проверить изменения").performClick();waitText("Проверьте список");ui.onNodeWithText("Сохранить изменения").performClick();waitText("Изменения сохранены")
  runBlocking{val rows=db.shoppingListEntryDao().getAllSync();assertEquals(1,rows.size);assertEquals(entry,rows.single().id);assertEquals(branch,rows.single().assignedBranchId)}
 }
}
