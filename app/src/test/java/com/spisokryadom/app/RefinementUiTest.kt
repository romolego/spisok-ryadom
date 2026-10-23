package com.spisokryadom.app
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.ShopChoice
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=SpisokRyadomApp::class,qualifiers="w360dp-h640dp-xhdpi")
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RefinementUiTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 private val app get()=ApplicationProvider.getApplicationContext<SpisokRyadomApp>();private val db get()=app.container.database
 private var a=0L;private var b=0L;private var fruit=0L;private var milk=0L;private var carrot=0L;private var tomato=0L;private var cheese=0L;private var food=0L;private var med=0L
 private fun waitText(t:String){ui.waitUntil(20_000){ui.onAllNodesWithText(t).fetchSemanticsNodes().isNotEmpty()}}
 private fun waitTag(t:String){ui.waitUntil(20_000){ui.onAllNodesWithTag(t).fetchSemanticsNodes().isNotEmpty()}}
 private fun scrollTo(m:SemanticsMatcher){ui.activeLazyList().performScrollToNode(m)}
 private fun shot(name:String,sheet:Boolean=false){ui.waitForIdle();ui.runOnIdle{val v=if(sheet)org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView else ui.activity.window.decorView;val bitmap=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(bitmap));val file=File("../.local/refinement-0.7.0/$name-api${android.os.Build.VERSION.SDK_INT}.png");file.parentFile!!.mkdirs();file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}}
 @Before fun setup()=runBlocking{
  listOf("drafts","shopping_list_prefs","product_db_prefs").forEach{app.getSharedPreferences(it,0).edit().clear().commit()}
  db.shoppingListEntryDao().deleteAll();db.productDao().deleteAll();db.shopDao().deleteAll();db.shopDepartmentDao().clearTypes();db.productGroupDao().deleteAll();db.recipientDao().deleteAll();db.unitDao().deleteAll();db.retailStructureDao().clearVenues();db.retailStructureDao().clearNetworks();db.cityDao().update(CityEntity(1,"Красноярск"));app.container.locations.select(1)
  a=db.shopDao().insert(ShopEntity(name="Батон"));b=db.shopDao().insert(ShopEntity(name="Красный Яр",displayOrder=1))
  food=db.productGroupDao().insert(ProductGroupEntity(name="Продукты",displayOrder=0,accent="green",iconKey="groceries"));med=db.productGroupDao().insert(ProductGroupEntity(name="Аптека",displayOrder=1))
  fruit=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=a,name="Овощи/фрукты",displayOrder=0));milk=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=a,name="Молочка",displayOrder=1))
  val type=db.shopDepartmentDao().getById(fruit)!!.typeId;app.container.shopRepository.styleDepartmentType(type,"green","vegetables")
  app.container.shopRepository.createDepartmentType("Гастроном (готовая еда)")
  fun product(name:String,dep:Long,fav:Boolean=false)=runBlocking{app.container.productRepository.saveWithLinks(ProductEntity(name=name,defaultUnit="кг",productGroupId=food,isFavorite=fav,accent=if(fav)"yellow"else ""),listOf(ProductShopLinkEntity(productId=0,shopId=a,departmentId=dep),ProductShopLinkEntity(productId=0,shopId=b,priority=2)))}
  carrot=product("Морковь",fruit);tomato=product("Помидоры",fruit);cheese=product("Сыр",milk,true)
  app.container.shoppingListRepository.addProduct(cheese,choice=ShopChoice.EXPLICIT,shopId=b);Unit
 }
 @After fun close(){ui.mainClock.autoAdvance=true;ui.activityRule.scenario.close();app.container.locations.closeForTest();db.close();org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null)}
 private fun catalog(){ui.onNodeWithText("Товары").performClick();waitText("Товаров: 3")}
 private fun newProduct(){catalog();ui.onNodeWithContentDescription("Новый товар").performClick();waitText("Название товара")}
 @Test fun catalogueUsesDepartmentsAndCartBelongsToTheVisibleStore(){
  catalog();ui.onAllNodesWithText("Магазины").onFirst().performClick();waitText("Избранное");ui.onNodeWithText("Избранное").performClick();waitTag("catalogue-shop_${a}-$carrot")
  val c=ui.onNodeWithTag("catalogue-shop_${a}-$carrot").fetchSemanticsNode().boundsInRoot.top;val t=ui.onNodeWithTag("catalogue-shop_${a}-$tomato").fetchSemanticsNode().boundsInRoot.top
  assertTrue(c<t);ui.onNodeWithText("Овощи/фрукты").assertIsDisplayed();shot("01-catalogue-departments")
  scrollTo(hasTestTag("catalogue-shop_${a}-$cheese"));ui.onNode(hasContentDescription("Добавить в список") and hasAnyAncestor(hasTestTag("catalogue-shop_${a}-$cheese"))).assertIsDisplayed().performClick()
  ui.waitUntil(20_000){ui.onAllNodes(hasContentDescription("В списке") and hasAnyAncestor(hasTestTag("catalogue-shop_${a}-$cheese")),useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
  runBlocking{val rows=db.shoppingListEntryDao().getAllSync().filter{it.productId==cheese};assertEquals(setOf(a,b),rows.map{it.assignedShopId}.toSet());assertEquals(2,rows.size)}
 }
 @Test fun favoriteFilterAndGroupOrderSurviveRecreation(){
  catalog();ui.onNodeWithText("Группы").performClick();ui.onNodeWithText("Продукты").assertIsDisplayed()
  ui.onNodeWithContentDescription("Только избранное").performClick();waitText("Товаров: 1");ui.onNodeWithText("Сыр").assertIsDisplayed();ui.onNodeWithText("Морковь").assertDoesNotExist();shot("02-favourites")
  ui.activityRule.scenario.recreate();waitText("Товаров: 1");ui.onNodeWithContentDescription("Показать все товары").performClick();waitText("Товаров: 3")
 }
 @Test fun addingUnknownProductCreatesMissingStoreAndClosesTheForm(){
  ui.onNodeWithContentDescription("Добавить в список").performClick();waitText("Добавить покупку");ui.onNodeWithText("Название товара").performTextReplacement("Таблетка для собаки")
  waitText("Новый товар: Таблетка для собаки");scrollTo(hasText("Магазин"));ui.onNodeWithText("Магазин").performClick();ui.onNodeWithText("Новый магазин…").performClick()
  scrollTo(hasText("Название магазина"));ui.onNodeWithText("Название магазина").performTextInput("Зоомагазин");ui.onNodeWithContentDescription("Создать: Название магазина").performClick()
  ui.waitUntil(20_000){ui.onAllNodesWithContentDescription("Отмена: Название магазина").fetchSemanticsNodes().isEmpty()};waitText("Зоомагазин")
  ui.onNodeWithText("Добавить в список").performScrollTo().performClick();waitText("Список покупок");ui.onNodeWithText("Добавить покупку").assertDoesNotExist();waitText("Таблетка для собаки");shot("03-add-finished")
  runBlocking{val product=db.productDao().getAllSync().single{it.name=="Таблетка для собаки"};val entry=db.shoppingListEntryDao().getAllSync().single{it.productId==product.id};assertEquals("Зоомагазин",db.shopDao().getById(entry.assignedShopId!!)!!.name)}
 }
 @Test fun productCardCanChooseNoStoreAndReuseGlobalDepartmentWithoutBroadcast(){
  newProduct();ui.onNodeWithContentDescription("Порядок товарных групп").assertDoesNotExist();ui.onNodeWithText("Название товара").performTextInput("Готовый салат")
  ui.onNodeWithText("Добавить магазин").performScrollTo().performClick();waitText("Без магазина");ui.onNodeWithText("Магазин",useUnmergedTree=true).performClick();ui.onNodeWithText("Батон").performClick()
  waitText("Группа магазина");ui.onNodeWithText("Группа магазина").performClick();ui.onNodeWithText("Гастроном (готовая еда)").assertDoesNotExist();ui.onNodeWithText("Добавить группу…").performClick();ui.onNodeWithText("Общая группа").performClick();ui.onNodeWithText("Гастроном (готовая еда)").performClick();shot("04-global-group-choice",true)
  ui.onNodeWithText("Добавить",useUnmergedTree=true).performClick();ui.onNodeWithText("Сохранить").performClick();waitText("База товаров")
  runBlocking{val product=db.productDao().getAllSync().single{it.name=="Готовый салат"};val link=db.productShopLinkDao().getLinksForProductSync(product.id).single();assertEquals(a,link.shopId);assertEquals("Гастроном (готовая еда)",db.shopDepartmentDao().getById(link.departmentId!!)!!.name);assertTrue(db.shopDepartmentDao().getByShopIdSync(b).isEmpty())}
  ui.onNodeWithContentDescription("Новый товар").performClick();waitText("Название товара");ui.onNodeWithText("Название товара").performTextInput("Без привязки")
  ui.onNodeWithText("Добавить магазин").performScrollTo().performClick();ui.onNodeWithText("Магазин",useUnmergedTree=true).performClick();ui.onNodeWithText("Батон").performClick();ui.onNodeWithText("Магазин",useUnmergedTree=true).performClick();ui.onNodeWithText("Без магазина").performClick();ui.onNodeWithText("Добавить",useUnmergedTree=true).performClick();ui.onNodeWithText("Сохранить").performClick();waitText("База товаров")
  runBlocking{val product=db.productDao().getAllSync().single{it.name=="Без привязки"};assertTrue(db.productShopLinkDao().getLinksForProductSync(product.id).isEmpty())}
 }
 @Test fun settingsHideMutationsUntilEditAndConfirmAlphabeticReset(){
  ui.onNodeWithContentDescription("Настройки").performClick();waitText("Настройки");ui.openSettingsDictionary("group");ui.onNodeWithText("Добавить группу товаров").assertDoesNotExist()
  ui.onNodeWithTag("manager-edit-Товарные группы").performClick();scrollTo(hasText("Добавить группу товаров"));ui.onNodeWithText("Добавить группу товаров").assertIsDisplayed()
  ui.onNodeWithContentDescription("По алфавиту: Товарные группы").assertDoesNotExist();ui.onNodeWithContentDescription("Вверх: Продукты").assertDoesNotExist()
  runBlocking{assertEquals(food,db.productGroupDao().getAllSync().first().id);assertEquals(listOf(med,food),app.container.classifierRepository.getAllGroups().first().map{it.id})}
  scrollTo(hasContentDescription("В избранное: Продукты"));ui.onNodeWithContentDescription("В избранное: Продукты").performClick()
  ui.waitUntil(20_000){runBlocking{db.productGroupDao().getById(food)!!.isFavorite}}
  ui.onAllNodesWithContentDescription("Убрать из избранного: Продукты").onFirst().performScrollTo().performClick()
  ui.waitUntil(20_000){runBlocking{!db.productGroupDao().getById(food)!!.isFavorite}}
  runBlocking{assertEquals(listOf(med,food),app.container.classifierRepository.getAllGroups().first().map{it.id});assertEquals(food,db.productGroupDao().getAllSync().first().id)}
  scrollTo(hasContentDescription("Оформление: Продукты"));ui.onNodeWithContentDescription("Оформление: Продукты").performClick();waitText("Оформление группы");ui.onNodeWithContentDescription("Жёлтый").performClick();ui.onNodeWithTag("appearance-icon-milk").performClick();ui.onNodeWithText("Применить оформление").performClick();ui.waitForIdle();shot("05-settings-appearance")
  runBlocking{val group=db.productGroupDao().getById(food)!!;assertEquals("yellow",group.accent);assertEquals("milk",group.iconKey)}
 }
 @Test fun departmentSettingsCanReorderAndCreateWithoutAttachingToStores(){
  ui.onNodeWithContentDescription("Настройки").performClick();waitText("Настройки");ui.openSettingsDictionary("dept");ui.onNodeWithText("Добавить группу магазина").assertDoesNotExist()
  ui.onNodeWithTag("manager-edit-Группы магазинов").performClick();scrollTo(hasContentDescription("В избранное: Молочка"));ui.onNodeWithContentDescription("Вверх: Молочка").assertDoesNotExist()
  val before=runBlocking{db.shopDepartmentDao().getAllSync()}
  scrollTo(hasContentDescription("В избранное: Овощи/фрукты"));ui.onNodeWithContentDescription("В избранное: Овощи/фрукты").performClick()
  scrollTo(hasContentDescription("В избранное: Молочка"));ui.onNodeWithContentDescription("В избранное: Молочка").performClick()
  ui.awaitDictionaryControl("Вверх: Молочка");ui.onNodeWithContentDescription("Вверх: Молочка").performClick()
  ui.waitUntil(20_000){runBlocking{db.shopDepartmentDao().types().filter{it.isFavorite}.minByOrNull{it.favoriteOrder}?.name=="Молочка"}}
  runBlocking{assertEquals(before,db.shopDepartmentDao().getAllSync());assertEquals(listOf("Гастроном (готовая еда)","Молочка","Овощи/фрукты"),app.container.shopRepository.departmentTypes().first().map{it.name})}
  scrollTo(hasText("Добавить группу магазина"));ui.onNodeWithText("Добавить группу магазина").performClick();ui.onNode(hasSetTextAction() and hasText("Название")).performTextInput("Орехи");ui.onNode(hasText("Сохранить") and hasAnyAncestor(isDialog())).performClick()
  scrollTo(hasText("Орехи"));waitText("Орехи");shot("08-department-settings")
  runBlocking{assertTrue(db.shopDepartmentDao().getByShopIdSync(b).isEmpty());assertTrue(db.shopDepartmentDao().getAllSync().none{it.name=="Орехи"})}
 }
 @Test @Config(sdk=[35],qualifiers="w640dp-h360dp-xhdpi") fun appearanceActionsRemainReachableInLandscape(){
  ui.onNodeWithContentDescription("Настройки").performClick();waitText("Настройки");ui.openSettingsDictionary("group");ui.onNodeWithTag("manager-edit-Товарные группы").performClick();scrollTo(hasContentDescription("Оформление: Продукты"));ui.onNodeWithContentDescription("Оформление: Продукты").performClick();waitText("Оформление группы")
  ui.onNodeWithContentDescription("Зелёный").assertIsDisplayed();ui.onNodeWithText("Применить оформление").assertIsDisplayed();shot("09-landscape-appearance",true);ui.onNodeWithText("Применить оформление").performClick()
 }
 @Test fun aMarketIsOneTripWithPavilionsAndCollapsesAsAWhole(){
  runBlocking{
   db.shoppingListEntryDao().deleteAll();val market=app.container.shopRepository.ensureVenue(1,"Енисейский привоз")
   val first=db.shopBranchDao().insert(ShopBranchEntity(shopId=a,address="29 павильон (сыры)",venueId=market));val second=db.shopBranchDao().insert(ShopBranchEntity(shopId=b,address="68 павильон (мясо)",venueId=market))
   app.container.shoppingListRepository.addProduct(cheese,choice=ShopChoice.EXPLICIT,shopId=a,branchId=first);app.container.shoppingListRepository.addProduct(carrot,choice=ShopChoice.EXPLICIT,shopId=b,branchId=second)
  }
  waitText("Енисейский привоз");waitText("29 павильон (сыры)");waitText("68 павильон (мясо)");ui.onAllNodesWithText("Енисейский привоз").assertCountEquals(1);shot("06-market-trip")
  ui.onNodeWithContentDescription("Рынок или ТЦ: Енисейский привоз").performClick();ui.waitUntil(20_000){ui.onAllNodesWithText("Сыр").fetchSemanticsNodes().isEmpty()};ui.onNodeWithText("Морковь").assertDoesNotExist();shot("07-market-collapsed")
  ui.activityRule.scenario.recreate();waitText("Енисейский привоз");ui.onNodeWithText("Сыр").assertDoesNotExist();ui.onNodeWithContentDescription("Рынок или ТЦ: Енисейский привоз").performClick();waitText("Сыр")
 }
}
