package com.spisokryadom.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.ProductEditorTransactions
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
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
class ProductVariants290FlowsUiTest{
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 private val app get()=ApplicationProvider.getApplicationContext<SpisokRyadomApp>()
 private val db get()=app.container.database
 private var root=0L;private var second=0L
 private fun waitFor(c:()->Boolean){try{ui.waitUntil(25_000){ui.mainClock.advanceTimeByFrame();Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(20));ui.waitForIdle();c()}}catch(e:Throwable){runCatching{File("../.local/refine290/flow-failure-api${android.os.Build.VERSION.SDK_INT}.txt").writeText(ui.onAllNodes(isRoot(),useUnmergedTree=true).printToString(maxDepth=100))};throw e}}
 private fun text(t:String)=waitFor{ui.onAllNodesWithText(t).fetchSemanticsNodes().isNotEmpty()}
 private fun tab(t:String){ui.onNode(hasText(t) and SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.Tab)).performClick()}
 private fun shot(n:String){ui.runOnIdle{val v=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf{it.isShowing}?.window?.decorView?:ui.activity.window.decorView;val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b));val f=File("build/reports/refine290/screens/$n-api${android.os.Build.VERSION.SDK_INT}.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 @Before fun seed(){runBlocking{
  listOf("product_card_drafts","shopping_list_prefs","product_db_prefs").forEach{app.getSharedPreferences(it,0).edit().clear().commit()}
  app.getSharedPreferences("shopping_list_prefs",0).edit().putBoolean("autosave_product_cards",false).commit()
  db.shoppingListEntryDao().deleteAll();db.goalListDao().deleteAll();db.productDao().deleteAll();db.shopDao().deleteAll();db.productGroupDao().deleteAll();db.recipientDao().deleteAll();db.unitDao().deleteAll();app.container.locations.select(1)
  db.cityDao().update(CityEntity(1,"Красноярск"));if(db.cityDao().getById(2)==null)db.cityDao().insert(CityEntity(2,"Иркутск"))
  db.unitDao().insert(UnitEntity(name="шт"));db.unitDao().insert(UnitEntity(name="кг"))
  root=db.productDao().insert(ProductEntity(name="Огурцы",isFavorite=true,defaultQuantity=1.0,defaultUnit="шт"))
  second=app.container.productRepository.variants.create(root,"Марка Б");db.productDao().update(db.productDao().getById(second)!!.copy(defaultQuantity=2.0,defaultUnit="кг"))
  app.container.productRepository.variants.configureCity(root,1,listOf(second to true,root to true))
  val photos=(0..1).map{i->val file=File(app.filesDir,"variant290-$i.png");val b=Bitmap.createBitmap(90+i*50,100,Bitmap.Config.ARGB_8888);b.eraseColor(android.graphics.Color.rgb(80+i*40,140,150));file.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle();ProductPhotoEntity(second,i,file.absolutePath,"Булка $i")}
  app.container.productRepository.variants.savePhotos(second,photos);db.productDao().update(db.productDao().getById(second)!!.copy(photoUri=photos.first().uri))
 };text("Список пуст")}
 @After fun close(){ui.activityRule.scenario.close();val end=System.nanoTime()+20_000_000_000;while(ProductEditorTransactions.mutex.isLocked&&System.nanoTime()<end){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();Thread.sleep(5)};assertFalse(ProductEditorTransactions.mutex.isLocked);app.container.locations.closeForTest();db.close();org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null)}
 private fun card(){tab("Товары");text("База товаров");text("Огурцы");ui.onAllNodesWithText("Огурцы").onLast().performClick();text("Название товара")}
 @Test fun pickerShowsAddedQuantityAndMinusRemovesLastWithoutLeaving(){
  ui.onNodeWithContentDescription("Добавить в список").performClick();text("Добавить покупку")
  ui.onNode(hasContentDescription("Добавить: Огурцы") and hasAnyAncestor(hasTestTag("candidate-favorite-$root"))).performClick()
  waitFor{runBlocking{db.shoppingListEntryDao().getAllSync().singleOrNull()?.productId==second}}
  waitFor{ui.onAllNodesWithTag("candidate-added-favorite-$root",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()};shot("picker-added-minus")
  ui.onNode(hasContentDescription("Уменьшить: Огурцы") and hasAnyAncestor(hasTestTag("candidate-favorite-$root"))).performClick()
  waitFor{runBlocking{db.shoppingListEntryDao().getAllSync().isEmpty()}}
  ui.onNodeWithText("Название товара").performTextInput("Салфетки");text("Новый товар: Салфетки");shot("input-clear");ui.onNodeWithContentDescription("Очистить").performClick();ui.onNodeWithText("Название товара").assertTextContains("")
  assertTrue(runBlocking{db.productDao().getAllSync().size==2})
 }
 @Test fun cardHasOneFamilyAndSwitchesVariantsWithTheirOwnPhotos(){
  card();waitFor{runCatching{ui.onNodeWithContentDescription("Предыдущий вариант").assertIsEnabled()}.isSuccess};ui.onNodeWithTag("product-variants").performScrollTo();ui.onNodeWithTag("product-variants").performTouchInput{swipeRight()}
  // City ordering puts the preferred second brand first; the catalogue still opens the stable original page.
  waitFor{ui.onAllNodesWithText("Марка Б").fetchSemanticsNodes().isNotEmpty()}
  ui.onNodeWithTag("product-photo-strip").performScrollTo();waitFor{ui.onAllNodesWithContentDescription("Булка 0").fetchSemanticsNodes().isNotEmpty()};shot("variant-card-photos")
  ui.onNodeWithContentDescription("Настроить варианты").performScrollTo().performClick();text("Варианты товара")
  waitFor{ui.onAllNodesWithTag("variant-city-row-$root").fetchSemanticsNodes().isNotEmpty()}
  ui.onAllNodesWithContentDescription("Вариант выше").onLast().performClick()
  ui.onNodeWithText("Город",substring=false).performClick();ui.onNodeWithText("Иркутск").performClick()
  waitFor{ui.onAllNodesWithTag("variant-city-row-$root").fetchSemanticsNodes().isNotEmpty()}
  ui.onAllNodesWithContentDescription("Вариант выше").onLast().performClick()
  ui.onNodeWithText("Город",substring=false).performClick();ui.onNodeWithText("Красноярск").performClick()
  waitFor{runCatching{ui.onNodeWithTag("variant-city-row-$root").getUnclippedBoundsInRoot().top<ui.onNodeWithTag("variant-city-row-$second").getUnclippedBoundsInRoot().top}.getOrDefault(false)}
  shot("variant-city-drafts");ui.onNodeWithText("Применить",substring=false).performClick()
  waitFor{runBlocking{val rules=db.productVariantDao().cities();rules.find{it.productId==root&&it.cityId==1L}?.displayOrder==0&&rules.find{it.productId==second&&it.cityId==2L}?.displayOrder==0}}
  ui.onNodeWithText("Кол-во по умолч.").performScrollTo().performTextReplacement("3")
  ui.onNodeWithText("Сохранить").performClick();text("База товаров")
  assertEquals(3.0,runBlocking{db.productDao().getById(second)!!.defaultQuantity!!},0.0);assertEquals(1.0,runBlocking{db.productDao().getById(root)!!.defaultQuantity!!},0.0)
 }
 @Test fun createVariantInsideCardKeepsOneCatalogueProduct(){
  card();ui.onNodeWithContentDescription("Новый вариант товара").performScrollTo().performClick();text("Марка или название варианта")
  ui.onNodeWithText("Марка или название варианта").performTextInput("Марка В");ui.onAllNodesWithText("Сохранить").onLast().performClick()
  text("Марка В");assertEquals(3,runBlocking{db.productDao().getAllSync().size});assertEquals(1,runBlocking{app.container.productRepository.getAllProducts().first().size})
  ui.onNodeWithText("Сохранить").performClick();text("База товаров")
 }
 @Test fun purchaseVariantCanBeReplacedAndOptionalRememberAffectsOnlyCurrentCity(){
  app.getSharedPreferences("shopping_list_prefs",0).edit().putBoolean("remember_variant_choice",true).commit()
  val id=runBlocking{val variants=app.container.productRepository.variants;variants.configureCities(root,mapOf(1L to listOf(root to true,second to true),2L to listOf(root to true,second to true)));app.container.shoppingListRepository.addProduct(root,4.0,"шт",exactVariant=true)}
  text("Огурцы · Основной вариант");ui.onNodeWithText("Огурцы · Основной вариант").performClick();text("Заменить вариант");ui.onNodeWithText("Заменить вариант").performClick();text("Вариант покупки")
  text("Вариант");ui.onNodeWithText("Вариант",substring=false).performClick();ui.onNodeWithText("Марка Б").performClick();shot("purchase-variant-choice");ui.onNodeWithText("Применить").performClick()
  waitFor{runBlocking{db.shoppingListEntryDao().getById(id)!!.productId==second}}
  assertEquals(4.0,runBlocking{db.shoppingListEntryDao().getById(id)!!.quantity},0.0);assertEquals("шт",runBlocking{db.shoppingListEntryDao().getById(id)!!.unit})
  assertEquals(second,runBlocking{app.container.productRepository.variants.resolve(root,1).id});assertEquals(root,runBlocking{app.container.productRepository.variants.resolve(root,2).id})
 }
}
