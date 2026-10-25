package com.spisokryadom.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.ShopChoice
import com.spisokryadom.app.ui.shoppinglist.ViewMode
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
@Config(sdk=[28,35],application=SpisokRyadomApp::class,qualifiers="w360dp-h800dp-xhdpi")
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Refine130UiTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 private val app get()=ApplicationProvider.getApplicationContext<SpisokRyadomApp>()
 private val db get()=app.container.database
 private val prefs get()=app.getSharedPreferences(WidgetSettings.PREFS,0)
 private var a=0L;private var b=0L;private var branch=0L;private var bakery=0L
 private fun waitFor(predicate:()->Boolean){ui.waitUntil(25_000){Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(20));ui.waitForIdle();predicate()}}
 private fun waitText(text:String)=waitFor{ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
 private fun click(text:String){waitText(text);if(text=="Магазины"||text=="Главная")ui.onNode(hasText(text) and SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role,androidx.compose.ui.semantics.Role.Tab)).performClick()else ui.onNodeWithText(text).performClick()}
 private fun shot(name:String,view:View=ui.activity.window.decorView){ui.waitForIdle();ui.runOnIdle{val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap));val file=File("../.local/refinement-0.13.0/$name-api${android.os.Build.VERSION.SDK_INT}.png");file.parentFile!!.mkdirs();file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}}
 @Before fun setup(){runBlocking{
  listOf("drafts","product_card_drafts",WidgetSettings.PREFS,"product_db_prefs").forEach{app.getSharedPreferences(it,0).edit().clear().commit()}
  db.shoppingListEntryDao().deleteAll();db.productDao().deleteAll();db.productGroupDao().deleteAll();db.shopDao().deleteAll();db.shopDepartmentDao().clearTypes();db.retailStructureDao().clearVenues();app.container.locations.select(1)
  a=db.shopDao().insert(ShopEntity(name="Красный Яр",shoppingBackground="rose"));b=db.shopDao().insert(ShopEntity(name="Батон",displayOrder=1));branch=db.shopBranchDao().insert(ShopBranchEntity(shopId=a,address="Адрес А"))
  bakery=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=a,name="Выпечка"));val dairy=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=a,name="Молочка",displayOrder=1))
  suspend fun add(name:String,shop:Long,dept:Long?=null){val p=app.container.productRepository.saveWithLinks(ProductEntity(name=name,defaultUnit="шт"),listOf(ProductShopLinkEntity(productId=0,shopId=shop,departmentId=dept)));app.container.shoppingListRepository.addProduct(p,choice=ShopChoice.EXPLICIT,shopId=shop,branchId=if(shop==a)branch else null)}
  add("Хлеб с изюмом",a,bakery);add("Молоко",a,dairy);add("Банан",b);Unit
 };waitFor{ui.onAllNodesWithTag("purchase-place-$a:$branch").fetchSemanticsNodes().isNotEmpty()}}
 @After fun close(){ui.activityRule.scenario.close();app.container.locations.closeForTest();db.close();org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null)}
 private fun settings(){ui.onNodeWithContentDescription("Настройки").performClick();waitText("Настройки");ui.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("widget-settings"));ui.onNodeWithTag("widget-settings").performClick();waitFor{ui.onAllNodesWithTag("widget-grouping").fetchSemanticsNodes().isNotEmpty()}}
 private fun font(size:Int){ui.onNodeWithTag("widget-font-size").performScrollTo().performClick();ui.onAllNodesWithText(size.toString()).onLast().performScrollTo().performClick();waitFor{WidgetSettings.load(prefs).textSize==size}}

 private fun grouping(mode:ViewMode){ui.onNodeWithText("Группировка в виджете").performScrollTo().performClick();val label=when(mode){ViewMode.BY_SHOPS->"По магазинам";ViewMode.BY_GROUPS->"По товарным группам";ViewMode.BY_RECIPIENTS->"По получателям";ViewMode.FLAT->"Общий список"};ui.onAllNodesWithText(label).onLast().performClick();waitFor{WidgetSettings.load(prefs).mode==mode}}
 @Test fun widgetSettingsAreIndependentAndSurviveActivityRecreation(){
  val original=prefs.all["shopping_list_view_mode"];settings();grouping(ViewMode.BY_RECIPIENTS);font(12);shot("01-widget-settings-small")
  assertEquals(original,prefs.all["shopping_list_view_mode"])
  ui.activityRule.scenario.recreate();waitFor{ui.onAllNodesWithTag("widget-grouping").fetchSemanticsNodes().isNotEmpty()};ui.onNode(hasText("По получателям") and hasAnyAncestor(hasTestTag("widget-grouping"))).assertExists();ui.onNodeWithTag("widget-font-size").performScrollTo();ui.onNode(hasText("12") and hasAnyAncestor(hasTestTag("widget-font-size"))).assertExists()
  assertEquals(WidgetSettings(ViewMode.BY_RECIPIENTS,true,12),WidgetSettings.load(prefs))
 }
 @Test fun allWidgetGroupingsAreReachableWithoutAnUnassignedMode(){
  settings()
  for(mode in listOf(ViewMode.FLAT,ViewMode.BY_GROUPS,ViewMode.BY_RECIPIENTS,ViewMode.BY_SHOPS)){
   grouping(mode)
   if(mode==ViewMode.BY_SHOPS)ui.onNodeWithTag("widget-departments").assertExists()else ui.onNodeWithTag("widget-departments").assertDoesNotExist()
  }
  ui.onNodeWithTag("widget-departments").performScrollTo().performClick();waitFor{!WidgetSettings.load(prefs).showDepartments};font(22);shot("02-widget-settings-large")
  ui.onNodeWithText("Обычный размер").assertDoesNotExist();ui.onNodeWithTag("widget-font-preview").assertDoesNotExist();font(16)
 }
 @Test fun compactDepartmentsKeepCollapseAndSelectionAndShopSeparation(){
  val deptTag="purchase-department-department_${a}_${branch}_${bakery}"
  waitFor{ui.onAllNodesWithTag(deptTag).fetchSemanticsNodes().isNotEmpty()}
  val shopHeight=ui.onNodeWithTag("purchase-place-$a:$branch").fetchSemanticsNode().boundsInRoot.height
  val deptHeight=ui.onNodeWithTag(deptTag).fetchSemanticsNode().boundsInRoot.height
  assertTrue("Shop heading must dominate the department",shopHeight>deptHeight*1.4f);shot("03-shopping-hierarchy")
  ui.onNodeWithContentDescription("Свернуть отдел: Выпечка").performClick();ui.onNodeWithText("Хлеб с изюмом").assertDoesNotExist()
  ui.onNodeWithContentDescription("Выбрать покупки").performClick();ui.onNodeWithContentDescription("Выбрать раздел: Выпечка").performClick();waitText("Выбрано: 1")
  ui.onNodeWithContentDescription("Развернуть отдел: Выпечка").performClick();ui.onNodeWithContentDescription("Выбрать: Хлеб с изюмом").assertIsOn();shot("04-shopping-selection")
 }
 @Test fun shopBackgroundSupportsCustomColorPersistsAndCanBeDisabled(){
  click("Магазины");waitText("Батон");ui.onNodeWithText("Батон").performTouchInput{longClick()};waitText("Название магазина")
  ui.onNodeWithContentDescription("Оформление магазина").performClick();ui.onNodeWithTag("shop-background-switch").performScrollTo().performClick();ui.onNodeWithTag("shop-background-color").performScrollTo().performClick()
  ui.onNodeWithTag("appearance-custom-toggle").performClick();ui.onNodeWithTag("appearance-hex").performTextReplacement("#12ABEF");click("Применить оформление");ui.onNodeWithTag("shop-appearance-apply").performClick();shot("05-shop-color")
  click("Сохранить");waitText("Магазины");assertEquals("#12ABEF",runBlocking{db.shopDao().getById(b)!!.shoppingBackground})
  click("Главная");waitText("Хлеб с изюмом");shot("06-shopping-colored-blocks")
  click("Магазины");waitText("Батон");ui.onNodeWithText("Батон").performTouchInput{longClick()};waitText("Название магазина");ui.onNodeWithContentDescription("Оформление магазина").performClick();ui.onNodeWithTag("shop-background-switch").performScrollTo().performClick();ui.onNodeWithTag("shop-appearance-apply").performClick();click("Сохранить");waitText("Магазины")
  assertEquals("",runBlocking{db.shopDao().getById(b)!!.shoppingBackground})
 }
 @Test fun nativeWidgetRendersSectionsAndBothFontExtremes(){
  runBlocking{repeat(10){i->val p=app.container.productRepository.saveWithLinks(ProductEntity(name="Покупка ${i+1}",defaultUnit="упаковка"),listOf(ProductShopLinkEntity(productId=0,shopId=b)));app.container.shoppingListRepository.addProduct(p,choice=ShopChoice.EXPLICIT,shopId=b)}}
  for(size in listOf(12,22)){
   WidgetSettings(textSize=size,showDepartments=size==12).save(prefs)
   val factory=ShoppingWidgetFactory(app);factory.onCreate();assertEquals(2,factory.viewTypeCount)
   val host=FrameLayout(ui.activity);val root=ShoppingWidgetProvider.views(app,130).apply(ui.activity,host);host.addView(root)
   val list=root.findViewById<ListView>(R.id.widget_list)
   list.adapter=object:BaseAdapter(){override fun getCount()=factory.count;override fun getItem(p:Int)=p;override fun getItemId(p:Int)=factory.getItemId(p);override fun getView(p:Int,v:View?,parent:ViewGroup)=factory.getViewAt(p)!!.apply(ui.activity,parent)}
   host.measure(View.MeasureSpec.makeMeasureSpec(660,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(900,View.MeasureSpec.EXACTLY));host.layout(0,0,660,900)
   assertEquals("Красный Яр",list.getChildAt(0).findViewById<TextView>(R.id.widget_section_title).text)
   assertNull(list.getChildAt(0).findViewById<View>(R.id.widget_check));assertTrue(list.canScrollVertically(1))
   val positions=(0 until factory.count).filter{factory.getItemId(it)>0};assertEquals(13,positions.size)
   val rendered=factory.getViewAt(positions.first())!!.apply(ui.activity,host)
   val title=rendered.findViewById<TextView>(R.id.widget_item_title);val detail=rendered.findViewById<TextView>(R.id.widget_item_detail)
   assertEquals(size*app.resources.displayMetrics.scaledDensity,title.textSize,.1f);assertEquals("1 шт",detail.text.toString())
   assertEquals((size+4)*app.resources.displayMetrics.scaledDensity,root.findViewById<TextView>(R.id.widget_heading).textSize,.1f)
   shot("07-widget-font-$size",host);factory.onDestroy()
  }
 }
 @Test fun restoredWidgetPreferencesRefreshAlreadyOpenSettings(){
  WidgetSettings(ViewMode.BY_GROUPS,false,12).save(prefs);val file=runBlocking{app.container.backupManager.exportFile()}
  WidgetSettings(ViewMode.BY_RECIPIENTS,true,22).save(prefs);settings();ui.onNodeWithTag("widget-font-size").performScrollTo();ui.onNode(hasText("22") and hasAnyAncestor(hasTestTag("widget-font-size"))).assertExists()
  runBlocking{app.container.backupManager.importFile(file);app.container.backupManager.discard(file)}
  ui.onNodeWithTag("widget-font-size").performScrollTo();ui.onNode(hasText("12") and hasAnyAncestor(hasTestTag("widget-font-size"))).assertExists();assertEquals(WidgetSettings(ViewMode.BY_GROUPS,false,12),WidgetSettings.load(prefs))
  ui.onNodeWithText("По товарным группам").assertExists()
 }
}
