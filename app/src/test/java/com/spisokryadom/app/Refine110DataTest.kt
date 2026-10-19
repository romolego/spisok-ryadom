package com.spisokryadom.app

import android.app.Application
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.*
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.domain.PlaceLabels
import com.spisokryadom.app.ui.bulk.*
import com.spisokryadom.app.widget.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class Refine110DataTest {
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 private lateinit var db:AppDatabase;private lateinit var locations:LocationRepository;private lateinit var basket:ShoppingListRepository;private lateinit var widget:ShoppingWidgetData
 private var shop=0L;private var branch=0L;private var venue=0L;private var product=0L;private var entry=0L
 @Before fun setup()=runBlocking {
  db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build();RetailConstraints.install(db.openHelper.writableDatabase)
  db.cityDao().insert(CityEntity(1,"Красноярск"));locations=LocationRepository(db.cityDao(),db.shopDao(),app.getSharedPreferences("110location",0))
  basket=ShoppingListRepository(db.shoppingListEntryDao(),db,locations);widget=ShoppingWidgetData(db,basket)
  venue=db.retailStructureDao().insertVenue(VenueEntity(cityId=1,name="Енисейский привоз"));shop=db.shopDao().insert(ShopEntity(name="29 павильон (сыры)"))
  branch=db.shopBranchDao().insert(ShopBranchEntity(shopId=shop,address="29 павильон (сыры)",venueId=venue))
  product=db.productDao().insert(ProductEntity(name="Сыр косичка",defaultUnit="шт",isFavorite=true));db.productShopLinkDao().insert(ProductShopLinkEntity(productId=product,shopId=shop))
  entry=basket.addProduct(product,quantity=2.0);Unit
 }
 @After fun close()=runBlocking{locations.closeAndJoin();db.close()}
 @Test fun physicalPlaceShowsVenueAndPavilionOnce()=runBlocking {
  val place=basket.purchasePlaces(setOf(entry)).single();assertEquals("Енисейский привоз",place.presentation.title);assertEquals("29 павильон (сыры)",place.presentation.subtitle);assertEquals("Енисейский привоз — 29 павильон (сыры)",place.label)
 }
 @Test fun additionalDestinationUsesSameContextWithoutChangingFacts()=runBlocking {
  val p=db.productDao().insert(ProductEntity(name="Молоко"));val other=basket.addProduct(p,choice=ShopChoice.NONE)
  val place=basket.additionalPurchasePlaces(setOf(other)).single();assertEquals("Енисейский привоз — 29 павильон (сыры)",place.label);assertEquals("29 павильон (сыры)",db.shopDao().getById(shop)!!.name)
 }
 @Test fun retailerContextDoesNotClaimMixedStreetAndMallOutlets()=runBlocking {
  val s=db.shopDao().getById(shop)!!;val b=db.shopBranchDao().get(branch)!!;val v=db.retailStructureDao().venues()
  assertEquals("Енисейский привоз — 29 павильон (сыры)",PlaceLabels(listOf(b),v).shop(shop,s.name))
  assertEquals(s.name,PlaceLabels(listOf(b,b.copy(id=999,venueId=null,address="Улица")),v).shop(shop,s.name))
  assertEquals("29 павильон (сыры)",PlaceLabels().place(s.name,b.copy(venueId=null)).text)
 }
 @Test fun textAndSharePreserveRussianAndExcludeBought()=runBlocking {
  val p=db.productDao().insert(ProductEntity(name="Уже куплено"));basket.markBought(basket.addProduct(p))
  val text=renderShoppingText(basket.textSnapshot());assertTrue(text.startsWith("Енисейский привоз — 29 павильон (сыры):"));assertTrue(text.contains("Сыр косичка — 2 шт"));assertFalse(text.contains("Уже куплено"))
  val chooser=shoppingListShareIntent(text+"\nГазировка — 1 бутылка");assertEquals(Intent.ACTION_CHOOSER,chooser.action)
  @Suppress("DEPRECATION") val send=chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
  assertEquals(Intent.ACTION_SEND,send.action);assertEquals("text/plain",send.type);assertEquals(text+"\nГазировка — 1 бутылка",send.getStringExtra(Intent.EXTRA_TEXT))
 }
 @Test fun widgetCheckboxChangesActualBasketAndIsIdempotent()=runBlocking {
  val original=widget.rows().single();assertFalse(original.bought);assertTrue(widget.check(original));assertTrue(db.shoppingListEntryDao().getById(entry)!!.isBought)
  assertFalse(widget.check(original));assertTrue(db.shoppingListEntryDao().getById(entry)!!.isBought)
  assertTrue(widget.check(widget.rows().single()));assertFalse(db.shoppingListEntryDao().getById(entry)!!.isBought)
 }
 @Test fun staleWidgetDoesNotOverwriteEditedMovedOrDeletedPurchase()=runBlocking {
  val old=widget.rows().single();basket.updateQuantity(entry,5.0);assertFalse(widget.check(old));assertFalse(db.shoppingListEntryDao().getById(entry)!!.isBought)
  val current=widget.rows().single();basket.removeEntry(entry);assertFalse(widget.check(current))
 }
 @Test fun widgetShowsFreshNamesPlaceQuantityAndBoughtState()=runBlocking {
  val row=widget.rows().single();assertEquals("Сыр косичка",row.title);assertEquals("2 шт · Енисейский привоз — 29 павильон (сыры)",row.detail)
  db.productDao().update(db.productDao().getById(product)!!.copy(name="Другой сыр"));basket.updateQuantity(entry,3.5);basket.markBought(entry)
  val fresh=widget.rows().single();assertEquals("Другой сыр",fresh.title);assertTrue(fresh.detail.startsWith("3,5 шт"));assertTrue(fresh.bought)
 }
 @Test fun actionTokensAreCompleteAndColdLaunchTargetsAreExplicit()=runBlocking {
  val row=widget.rows().single();val decoded=ShoppingWidgetProvider.decodeCheck(ShoppingWidgetProvider.checkIntent(row))!!
  assertEquals(row.id,decoded.id);assertEquals(row.productStableId,decoded.productStableId);assertEquals(row.updatedAt,decoded.updatedAt);assertNull(ShoppingWidgetProvider.decodeCheck(Intent()))
  val add=WidgetIntents.open(app,true);assertEquals(MainActivity::class.java.name,add.component!!.className);assertEquals("add",WidgetIntents.destination(add));assertEquals("list",WidgetIntents.destination(WidgetIntents.open(app)));assertNull(WidgetIntents.destination(Intent()))
 }
}
