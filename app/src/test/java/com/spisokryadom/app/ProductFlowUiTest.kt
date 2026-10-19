package com.spisokryadom.app
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=SpisokRyadomApp::class,qualifiers="w360dp-h640dp-xhdpi",shadows=[WindowsCaptureProvider::class])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProductFlowUiTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 private val app get()=ApplicationProvider.getApplicationContext<SpisokRyadomApp>()
 private val db get()=app.container.database
 private var shop=0L;private var other=0L;private var points=listOf<Long>();private var food=0L;private var med=0L
 private fun waitText(t:String){ui.waitUntil(20_000){ui.onAllNodesWithText(t).fetchSemanticsNodes().isNotEmpty()}}
 private fun shot(name:String,dialog:Boolean=false){ui.runOnIdle{val v=if(dialog)org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView else ui.activity.window.decorView;val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b));val f=File("../.local/flow-0.5.0/$name-api${android.os.Build.VERSION.SDK_INT}.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 private fun newDepartmentChoice(){
  ui.onNodeWithText("Группа магазина").performClick();ui.onNodeWithText("Добавить группу…").performClick()

 }
 private fun newProduct(){ui.onNodeWithText("Товары").performClick();waitText("База товаров");ui.onNodeWithContentDescription("Новый товар").performClick();waitText("Название товара")}
 @Before fun seed(){
  listOf("drafts","shopping_list_prefs","product_db_prefs").forEach{app.getSharedPreferences(it,0).edit().clear().commit()}
  runBlocking{db.shoppingListEntryDao().deleteAll();db.productDao().deleteAll();db.shopDao().deleteAll();db.shopDepartmentDao().clearTypes();db.productGroupDao().deleteAll();db.cityDao().update(CityEntity(1,"Красноярск"));app.container.locations.select(1)
   shop=db.shopDao().insert(ShopEntity(name="Командор",displayOrder=0));other=db.shopDao().insert(ShopEntity(name="Батон",displayOrder=1))
   points=(1..5).map{db.shopBranchDao().insert(ShopBranchEntity(shopId=shop,address="Адрес $it",displayOrder=it))}
   med=db.productGroupDao().insert(ProductGroupEntity(name="Аптека",displayOrder=0));food=db.productGroupDao().insert(ProductGroupEntity(name="Продукты",displayOrder=1))
   db.shopBranchDao().insert(ShopBranchEntity(shopId=other,address="Единственная точка"))
  };waitText("Список пуст")
 }
 @After fun close(){ui.activityRule.scenario.close();app.container.locations.closeForTest();db.close();org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null)}
 @Test fun newDepartmentAndNonPrimarySalePointsAreSavedTogether(){
  newProduct();ui.onNodeWithText("Название товара").performTextReplacement("Зелёный чай");ui.onNodeWithText("Заметка").performScrollTo().performTextReplacement("Листовой")
  ui.onNodeWithText("Добавить магазин").performScrollTo().performClick();waitText("Магазин");ui.onNodeWithText("Магазин",useUnmergedTree=true).performClick();ui.onNodeWithText("Командор").performClick();waitText("Группа магазина")
  newDepartmentChoice()
  ui.onNodeWithText("Новая группа").performTextInput("Чай и кофе");ui.onNodeWithContentDescription("Создать группу магазина").performClick()
  assertTrue(runBlocking{db.shopDepartmentDao().getByShopIdSync(shop)}.isEmpty())
  ui.onNode(isToggleable() and hasText("Только выбранные точки")).performScrollTo().performClick()
  ui.onNode(isToggleable() and hasText("Адрес 1")).performScrollTo().performClick()
  ui.onNode(isToggleable() and hasText("Адрес 3")).performScrollTo().performClick()
  ui.onNode(isToggleable() and hasText("Адрес 5")).performScrollTo().performClick();shot("01-shop-link-subset",true)
  ui.onNodeWithText("Добавить",useUnmergedTree=true).performClick();ui.waitUntil(20_000){ui.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty()}
  ui.onNodeWithText("Точки продажи товара").performScrollTo();shot("02-points-retained")
  ui.onNode(isToggleable() and hasText("Адрес 3")).assertIsOn();ui.onNode(isToggleable() and hasText("Адрес 5")).assertIsOn()
  ui.onNodeWithText("Сохранить").performClick();waitText("База товаров")
  runBlocking{val p=db.productDao().getAllSync().single();assertEquals("Листовой",p.note);val link=db.productShopLinkDao().getLinksForProductSync(p.id).single();assertTrue(link.restrictBranches);assertEquals("Чай и кофе",db.shopDepartmentDao().getById(link.departmentId!!)!!.name);assertEquals(setOf(points[2],points[4]),db.shopBranchDao().availability(p.id).map{it.branchId}.toSet());val id=app.container.shoppingListRepository.addProduct(p.id);assertEquals(points[2],db.shoppingListEntryDao().getAllSync().single().assignedBranchId)}
 }
 @Test fun cancellingShopSelectionDoesNotSaveDraftDepartmentAndSinglePointHasNoToggle(){
  newProduct();ui.onNodeWithText("Название товара").performTextReplacement("Какао")
  ui.onNodeWithText("Добавить магазин").performScrollTo().performClick();waitText("Магазин");ui.onNodeWithText("Магазин",useUnmergedTree=true).performClick();ui.onNodeWithText("Командор").performClick();waitText("Группа магазина");newDepartmentChoice()
  ui.onNodeWithText("Новая группа").performTextInput("Напитки");ui.onNodeWithContentDescription("Создать группу магазина").performClick()
  ui.onNodeWithText("Отмена").performClick();ui.waitUntil(20_000){ui.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty()}
  ui.onNodeWithText("Добавить магазин").performScrollTo().performClick();ui.onNodeWithText("Магазин",useUnmergedTree=true).performClick();ui.onNodeWithText("Батон").performClick()
  ui.onNodeWithText("Только выбранные точки").assertDoesNotExist();ui.onNodeWithText("Добавить",useUnmergedTree=true).performClick();ui.waitUntil(20_000){ui.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty()}
  ui.onNodeWithText("Сохранить").performClick();waitText("База товаров");assertTrue(runBlocking{db.shopDepartmentDao().getByShopIdSync(shop)}.isEmpty())
 }
 @Test fun groupOrderIsChangedInSettingsAndUsedByTheProductDropdown(){
  ui.onNodeWithContentDescription("Настройки").performClick();waitText("Настройки")
  ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Товарные группы"));ui.onNodeWithTag("manager-edit-Товарные группы").performClick()
  ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("Вверх: Продукты"));ui.onNodeWithContentDescription("Вверх: Продукты").performClick()
  ui.waitUntil(20_000){runBlocking{db.productGroupDao().getAllSync().first().id==food}}
  shot("05-group-priority");ui.onNodeWithContentDescription("Назад").performClick();newProduct()
  ui.onNodeWithContentDescription("Порядок товарных групп").assertDoesNotExist();ui.onNodeWithText("Товарная группа").performClick()
  assertTrue(ui.onNode(hasText("Продукты") and hasAnyAncestor(isPopup())).getUnclippedBoundsInRoot().top<ui.onNode(hasText("Аптека") and hasAnyAncestor(isPopup())).getUnclippedBoundsInRoot().top)
  ui.onNode(hasText("Продукты") and hasAnyAncestor(isPopup())).performClick();ui.onNodeWithContentDescription("Назад").performClick();ui.waitUntil(20_000){ui.onAllNodesWithContentDescription("Новый товар").fetchSemanticsNodes().isNotEmpty()};ui.onNodeWithContentDescription("Новый товар").performClick();waitText("Название товара")
  ui.onNodeWithText("Товарная группа").performClick();assertTrue(ui.onNode(hasText("Продукты") and hasAnyAncestor(isPopup())).getUnclippedBoundsInRoot().top<ui.onNode(hasText("Аптека") and hasAnyAncestor(isPopup())).getUnclippedBoundsInRoot().top)
 }
 @Test @Config(sdk=[35]) fun duplicateSearchResultsRemainAboveImeAndChooseCorrectIdentity(){
  val ids=runBlocking{listOf(shop,other).map{sid->val p=db.productDao().insert(ProductEntity(name="Цукаты",note=if(sid==shop)"Имбирные"else "Ананасовые"));db.productShopLinkDao().insert(ProductShopLinkEntity(productId=p,shopId=sid));p}}
  ui.onNodeWithContentDescription("Добавить в список").performClick();waitText("Добавить покупку");ui.onNodeWithText("Название товара").performTextInput("цукат")
  ui.waitUntil(20_000){ui.onAllNodesWithText("Цукаты").fetchSemanticsNodes().size==2}
  ui.runOnIdle{fun find(v:android.view.View):android.view.View?{if(v.javaClass.simpleName=="AndroidComposeView")return v;if(v is android.view.ViewGroup)for(i in 0 until v.childCount)find(v.getChildAt(i))?.let{return it};return null};val d=ui.activity.resources.displayMetrics.density
   val insets=androidx.core.view.WindowInsetsCompat.Builder().setInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars(),androidx.core.graphics.Insets.of(0,(24*d).toInt(),0,(24*d).toInt())).setInsets(androidx.core.view.WindowInsetsCompat.Type.ime(),androidx.core.graphics.Insets.of(0,0,0,(300*d).toInt())).setVisible(androidx.core.view.WindowInsetsCompat.Type.ime(),true).build()
   androidx.core.view.ViewCompat.dispatchApplyWindowInsets(find(ui.activity.window.decorView)!!,insets)
  };ui.waitForIdle()
  val candidate=ui.onNodeWithText("Командор · Имбирные");candidate.assertIsDisplayed();assertTrue(candidate.getUnclippedBoundsInRoot().bottom<341.dp);shot("03-search-ime")
  candidate.performClick();ui.onNodeWithText("Выбран").assertExists();ui.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Добавить в список"));ui.onNodeWithText("Добавить в список").assertIsEnabled();shot("03b-add-action");println("ADD bounds="+ui.onNodeWithText("Добавить в список").getUnclippedBoundsInRoot());ui.onNodeWithText("Добавить в список").performClick();shot("03c-after-add")
  ui.waitUntil(20_000){runBlocking{db.shoppingListEntryDao().getAllSync().isNotEmpty()}};assertEquals(ids[0],runBlocking{db.shoppingListEntryDao().getAllSync().single().productId})
 }
 @Test fun productPhotoCameraImportsAndPersistsAndCancelPreservesOldPhoto(){
  newProduct();ui.onNodeWithText("Название товара").performTextReplacement("Чай с фото")
  org.robolectric.Shadows.shadowOf(app.packageManager).addResolveInfoForIntent(android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE),android.content.pm.ResolveInfo().apply{activityInfo=android.content.pm.ActivityInfo().apply{name="CameraActivity";packageName="test.camera"}})
  ui.onNodeWithText("Камера").performScrollTo().performClick();val started=org.robolectric.Shadows.shadowOf(ui.activity).nextStartedActivityForResult;assertNotNull(started)
  val uri=started.intent.getParcelableExtra<android.net.Uri>(android.provider.MediaStore.EXTRA_OUTPUT)!!;assertEquals("content",uri.scheme)
  val bitmap=Bitmap.createBitmap(600,300,Bitmap.Config.ARGB_8888);bitmap.eraseColor(android.graphics.Color.rgb(34,110,95));Canvas(bitmap).drawText("Чай",30f,160f,android.graphics.Paint().apply{color=android.graphics.Color.WHITE;textSize=44f;isAntiAlias=true});app.contentResolver.openOutputStream(uri)!!.use{bitmap.compress(Bitmap.CompressFormat.JPEG,90,it)};bitmap.recycle()
  org.robolectric.Shadows.shadowOf(ui.activity).receiveResult(started.intent,android.app.Activity.RESULT_OK,null)
  ui.waitUntil(20_000){ui.onAllNodesWithContentDescription("Фото товара").fetchSemanticsNodes().isNotEmpty()};ui.onNodeWithContentDescription("Фото товара").performScrollTo();shot("04-product-camera")
  ui.onNodeWithText("Камера").performScrollTo().performClick();val cancelled=org.robolectric.Shadows.shadowOf(ui.activity).nextStartedActivityForResult;org.robolectric.Shadows.shadowOf(ui.activity).receiveResult(cancelled.intent,android.app.Activity.RESULT_CANCELED,null)
  ui.onNodeWithContentDescription("Фото товара").assertExists();ui.onNodeWithText("Сохранить").performClick();waitText("База товаров")
  val path=runBlocking{db.productDao().getAllSync().single().photoUri!!};assertTrue(path.contains("product_photos"));assertTrue(File(path).isFile)
 }
 @Test fun shopOrderMovesBothRowsAndKeepsTheViewport(){
  ui.onNodeWithText("Магазины").performClick();waitText("Командор");ui.onNodeWithContentDescription("Изменить порядок").performClick()
  fun y(n:String)=ui.onNodeWithText(n).fetchSemanticsNode().boundsInRoot.top
  val a=y("Командор");val b=y("Батон");ui.mainClock.autoAdvance=false
  try{
   ui.onAllNodesWithContentDescription("Переместить вниз").filter(isEnabled()).onFirst().performClick()
   ui.waitUntil(20_000){ui.onAllNodesWithTag("shop-order-$other-0").fetchSemanticsNodes().isNotEmpty()}
   ui.mainClock.advanceTimeByFrame();ui.waitForIdle();ui.mainClock.advanceTimeBy(112);ui.waitForIdle()
   val am=y("Командор");val bm=y("Батон")
   assertTrue("Actual store row must slide down: $a < $am < $b",am>a&&am<b)
   assertTrue("Actual neighbour must slide up: $a < $bm < $b",bm>a&&bm<b);shot("06-moving-shops")
   ui.mainClock.advanceTimeBy(400);ui.waitForIdle();assertEquals(a,y("Батон"),2f);assertEquals(b,y("Командор"),2f)
  }finally{ui.mainClock.autoAdvance=true}
 }

}
