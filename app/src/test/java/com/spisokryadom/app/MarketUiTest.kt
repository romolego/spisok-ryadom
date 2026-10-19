package com.spisokryadom.app
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
class MarketUiTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 private val app get()=ApplicationProvider.getApplicationContext<SpisokRyadomApp>()
 private val db get()=app.container.database
 private var market=0L;private var baton=0L;private var cheesePoint=0L;private var meatPoint=0L;private var cheese=0L;private var meat=0L;private var candied=0L
 private fun waitText(t:String){ui.waitUntil(20_000){ui.onAllNodesWithText(t).fetchSemanticsNodes().isNotEmpty()}}
 private fun shot(name:String,dialog:Boolean=false){ui.runOnIdle{val v=if(dialog)org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView else if(name=="02-actions"){
 val c=Class.forName("android.view.WindowManagerGlobal");val g=c.getDeclaredMethod("getInstance").invoke(null);val f=c.getDeclaredField("mViews").apply{isAccessible=true};@Suppress("UNCHECKED_CAST") val views=f.get(g) as List<android.view.View>;views.last{it.javaClass.simpleName.contains("Popup")&&it.width>0}
}else ui.activity.window.decorView;val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b));val f=File("../.local/market-0.4.0/$name-api${android.os.Build.VERSION.SDK_INT}.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 private fun openMarket(){ui.onNodeWithText("Магазины").performClick();waitText("Енисейский привоз");ui.onNodeWithText("Енисейский привоз").performClick();waitText("Сыр")}
 @Before fun seed(){
  listOf("drafts","shopping_list_prefs","product_db_prefs").forEach{app.getSharedPreferences(it,0).edit().clear().commit()}
  runBlocking{
   db.shoppingListEntryDao().deleteAll();db.productDao().deleteAll();db.productGroupDao().deleteAll();db.shopDao().deleteAll();db.shopDepartmentDao().clearTypes();db.cityDao().update(CityEntity(1,"Красноярск"));app.container.locations.select(1)
   market=db.shopDao().insert(ShopEntity(name="Енисейский привоз"));baton=db.shopDao().insert(ShopEntity(name="Батон"))
   cheesePoint=db.shopBranchDao().insert(ShopBranchEntity(shopId=market,address="29 павильон — сыры",note="Беру сыр и масло",displayOrder=0))
   meatPoint=db.shopBranchDao().insert(ShopBranchEntity(shopId=market,address="68 павильон — мясо",displayOrder=1))
   cheese=db.productDao().insert(ProductEntity(name="Сыр",defaultUnit="кг"));meat=db.productDao().insert(ProductEntity(name="Мясо",defaultUnit="кг"));candied=db.productDao().insert(ProductEntity(name="Цукаты",note="Из Батона",priceValue=90.0))
   for((p,b)in listOf(cheese to cheesePoint,meat to meatPoint)){db.productShopLinkDao().insert(ProductShopLinkEntity(productId=p,shopId=market,restrictBranches=true));db.shopBranchDao().insertAvailability(ProductBranchAvailabilityEntity(p,b))}
   db.productShopLinkDao().insert(ProductShopLinkEntity(productId=candied,shopId=baton,priority=1))
   db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=market,name="Молочка"));db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=baton,name="Мясо/колбаса"));db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=baton,name="Крупы"))
  };waitText("Список пуст")
 }
 @After fun close(){ui.activityRule.scenario.close();app.container.locations.closeForTest();db.close();org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null)}
 @Test fun pointsGroupAndFoldIndependentlyAndCartUsesThePoint(){
  openMarket();waitText("29 павильон — сыры");ui.onNodeWithText("Беру сыр и масло").assertIsDisplayed();shot("01-market-points")
  ui.onNodeWithContentDescription("Свернуть точку: 29 павильон — сыры").performClick();ui.onNodeWithText("Сыр").assertDoesNotExist();ui.onNodeWithText("Мясо").assertExists()
  ui.activityRule.scenario.recreate();waitText("68 павильон — мясо");ui.onNodeWithText("Сыр").assertDoesNotExist()
  ui.onNodeWithTag("shop-add-$meat").performScrollTo().performClick()
  ui.waitUntil(20_000){runBlocking{db.shoppingListEntryDao().getAllSync().any{it.productId==meat}}||ui.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty()}
  assertTrue(ui.onRoot().printToString(),runBlocking{db.shoppingListEntryDao().getAllSync().any{it.productId==meat}})
  assertEquals(meatPoint,runBlocking{db.shoppingListEntryDao().getAllSync()}.single().assignedBranchId)
 }
 @Test fun createProductSearchesOtherStoresAndReusesIdentityInsteadOfDuplicating(){
  runBlocking{db.productDao().insert(ProductEntity(name="Огурцы свежие"));db.productDao().insert(ProductEntity(name="Огурцы солёные"))}
  openMarket();ui.onNodeWithContentDescription("Добавить товар").performClick();ui.onNodeWithText("Создать новый товар").assertIsDisplayed();shot("02-actions")
  ui.onNodeWithText("Создать новый товар").performClick();waitText("Название товара")
  val name=ui.onNode(hasSetTextAction() and hasText("Название товара"));name.performTextReplacement("Огурцы");waitText("Огурцы свежие");ui.onNodeWithText("Огурцы солёные").assertExists();name.performTextReplacement("Цукты");waitText("Цукаты");ui.onNodeWithText("Цукаты").assertExists()
  name.performTextReplacement("Цукаты");ui.onNodeWithText("Сохранить").performClick();waitText("Товар с таким названием уже есть")
  assertEquals(5,runBlocking{db.productDao().getAllSync()}.size);shot("03-existing-product")
  ui.onNodeWithText("Использовать").performScrollTo().performClick();waitText("Редактирование");ui.waitUntil(20_000){ui.onAllNodes(hasSetTextAction() and hasText("Цукаты")).fetchSemanticsNodes().isNotEmpty()}
  runBlocking{assertEquals(5,db.productDao().getAllSync().size);assertEquals("Из Батона",db.productDao().getById(candied)!!.note);assertEquals(90.0,db.productDao().getById(candied)!!.priceValue!!,0.0);assertEquals(setOf(market,baton),db.productShopLinkDao().getLinksForProductSync(candied).map{it.shopId}.toSet())}
  ui.onNodeWithContentDescription("Назад").performClick();ui.waitUntil(20_000){ui.onAllNodes(hasSetTextAction() and hasText("Название товара")).fetchSemanticsNodes().isEmpty()};ui.waitUntil(20_000){ui.onAllNodesWithContentDescription("Добавить товар").fetchSemanticsNodes().isNotEmpty()};waitText("Цукаты")
  ui.onNodeWithContentDescription("Добавить товар").performClick();ui.onNodeWithText("Создать новый товар").performClick();waitText("Название товара")
  ui.onNode(hasSetTextAction() and hasText("Название товара")).performTextReplacement("Огурцы маринованные")
  ui.onNodeWithText("Сохранить").performClick();ui.waitUntil(20_000){ui.onAllNodes(hasSetTextAction() and hasText("Название товара")).fetchSemanticsNodes().isEmpty()};waitText("Огурцы маринованные")
  runBlocking{assertEquals(6,db.productDao().getAllSync().size);val added=db.productDao().getAllSync().single{it.name=="Огурцы маринованные"};assertEquals(market,db.productShopLinkDao().getLinksForProductSync(added.id).single().shopId)}

 }
 @Test fun groupRowsStayAlignedWhenExistingSelectedAndNewRowsAreMixed(){
  ui.onNodeWithText("Магазины").performClick();waitText("Енисейский привоз");ui.onNodeWithText("Енисейский привоз").performTouchInput{longClick()};waitText("Название магазина")
  ui.onNodeWithText("Группы магазина").performScrollTo();ui.onNodeWithContentDescription("Редактировать").performScrollTo().performClick();ui.onNodeWithText("Добавить группы").performScrollTo().performClick();waitText("Добавить группы магазина")
  waitText("Мясо/колбаса");ui.onNodeWithText("Мясо/колбаса").performScrollTo().performClick()
  ui.onNode(hasSetTextAction() and hasText("Новая группа")).performTextReplacement("орехи");ui.onNodeWithContentDescription("Добавить название").performScrollTo().performClick()
  val xs=listOf("Крупы","Молочка","Мясо/колбаса","орехи").map{ui.onNode(hasText(it) and !hasSetTextAction(),useUnmergedTree=true).performScrollTo().getUnclippedBoundsInRoot().left.value}
  assertTrue("Text columns must align: $xs",xs.max()-xs.min()<1f);shot("04-groups",true)
  ui.onNodeWithText("Добавить",useUnmergedTree=true).performClick();ui.waitUntil(20_000){ui.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty()};ui.onNodeWithText("Сохранить").performClick();waitText("Магазины")
  assertEquals(setOf("Молочка","Мясо/колбаса","орехи"),runBlocking{db.shopDepartmentDao().getByShopIdSync(market)}.map{it.name}.toSet())
 }
 @Test fun pointNoteIsADraftAndCameraUsesPrivateCaptureUri(){
  ui.onNodeWithText("Магазины").performClick();waitText("Енисейский привоз");ui.onNodeWithText("Енисейский привоз").performTouchInput{longClick()};waitText("Название магазина")
  ui.onNodeWithContentDescription("Изменить адрес: 29 павильон — сыры").performScrollTo().performClick()
  ui.onNodeWithTag("branch-note-$cheesePoint").performScrollTo().performTextReplacement("Беру адыгейский и масло")
  assertEquals("Беру сыр и масло",runBlocking{db.shopBranchDao().get(cheesePoint)!!}.note)
  org.robolectric.Shadows.shadowOf(app.packageManager).addResolveInfoForIntent(android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE),android.content.pm.ResolveInfo().apply{activityInfo=android.content.pm.ActivityInfo().apply{name="CameraActivity";packageName="test.camera"}})
  ui.onNodeWithText("Камера").performScrollTo().performClick()
  val started=org.robolectric.Shadows.shadowOf(ui.activity).nextStartedActivityForResult
  assertNotNull("Camera: "+org.robolectric.shadows.ShadowLog.getLogsForTag("BranchPhoto").map{it.throwable?.stackTraceToString()},started)
  assertEquals(android.provider.MediaStore.ACTION_IMAGE_CAPTURE,started.intent.action)
  val uri=started.intent.getParcelableExtra<android.net.Uri>(android.provider.MediaStore.EXTRA_OUTPUT)!!
  assertEquals("content",uri.scheme);assertTrue(uri.authority!!.endsWith(".files"))
  val bitmap=Bitmap.createBitmap(600,300,Bitmap.Config.ARGB_8888)
  bitmap.eraseColor(android.graphics.Color.rgb(40,90,110));Canvas(bitmap).drawText("29 — Сыр и масло",30f,160f,android.graphics.Paint().apply{color=android.graphics.Color.WHITE;textSize=44f;isAntiAlias=true})
  app.contentResolver.openOutputStream(uri)!!.use{bitmap.compress(Bitmap.CompressFormat.JPEG,90,it)};bitmap.recycle()
  org.robolectric.Shadows.shadowOf(ui.activity).receiveResult(started.intent,android.app.Activity.RESULT_OK,null)
  ui.waitUntil(20_000){ui.onAllNodesWithContentDescription("Фото точки").fetchSemanticsNodes().isNotEmpty()}
  ui.onAllNodes(isDialog()).assertCountEquals(0);ui.onNodeWithTag("branch-note-$cheesePoint").performScrollTo();shot("05-point-metadata")
  ui.onNodeWithText("Сохранить").performClick();waitText("Магазины");assertEquals("Беру адыгейский и масло",runBlocking{db.shopBranchDao().get(cheesePoint)!!}.note);assertTrue(File(runBlocking{db.shopBranchDao().get(cheesePoint)!!}.photoUri!!).isFile)
 }
}

// Android FileProvider compares canonical paths with '/'. The JVM hosting Robolectric
// uses Windows backslashes; emulate this framework boundary while keeping the app's
// camera intent, content-URI reads, import, compression and persistence real.
@org.robolectric.annotation.Implements(androidx.core.content.FileProvider::class)
class WindowsCaptureProvider {
 companion object {
  private val files=mutableMapOf<String,File>()
  @JvmStatic @org.robolectric.annotation.Implementation
  fun getUriForFile(context:android.content.Context,authority:String,file:File):android.net.Uri {
   val root=File(context.cacheDir,"camera_captures").canonicalFile
   require(file.canonicalPath.startsWith(root.path+File.separator))
   val uri=android.net.Uri.parse("content://$authority/camera/${file.name}");files[uri.toString()]=file
   return uri
  }
 }
 @org.robolectric.annotation.Implementation
 fun openFile(uri:android.net.Uri,mode:String):android.os.ParcelFileDescriptor {
  val file=files[uri.toString()]?:throw java.io.FileNotFoundException()
  return android.os.ParcelFileDescriptor.open(file,android.os.ParcelFileDescriptor.parseMode(mode))
 }
}
