package com.spisokryadom.app

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.backup.BackupManager
import com.spisokryadom.app.data.db.*
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.ui.shoplist.ShopListViewModel
import com.spisokryadom.app.ui.shopcontent.ShopContentViewModel
import com.spisokryadom.app.ui.shoppinglist.ShoppingListViewModel
import com.spisokryadom.app.ui.components.*
import com.spisokryadom.app.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.*
import org.json.JSONObject
import java.io.File
import java.time.Duration
import java.util.zip.*
import androidx.compose.ui.graphics.Color

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class Stores220DataTest {
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 private lateinit var db:AppDatabase
 private lateinit var locations:LocationRepository
 private lateinit var shops:ShopRepository
 private lateinit var products:ProductRepository
 private lateinit var basket:ShoppingListRepository
 private val store=ViewModelStore()
 private var shop=0L;private var network=0L;private var first=0L;private var second=0L;private var street=0L
 @Before fun setup():Unit=runBlocking{
  db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build();RetailConstraints.install(db.openHelper.writableDatabase);db.cityDao().insert(CityEntity(1,"Город"))
  locations=LocationRepository(db.cityDao(),db.shopDao(),app.getSharedPreferences("220-location",0));shops=ShopRepository(db.shopDao(),db.shopDepartmentDao(),locations,db)
  products=ProductRepository(db.productDao(),db.productShopLinkDao(),db);basket=ShoppingListRepository(db.shoppingListEntryDao(),db,locations)
  network=shops.ensureNetwork("Лента")
  first=shops.ensureVenue(1,"ТЦ Зелёный");second=shops.ensureVenue(1,"ТЦ Северный")
  shop=shops.saveWithDepartments(ShopEntity(name="Лента",networkId=network,shoppingBackground="rose",purchaseBackground="auto"),emptyList(),listOf(ShopBranchEntity(id=-1,shopId=0,address="Парковая, 1",venueId=first),ShopBranchEntity(id=-2,shopId=0,address="Северная, 2",venueId=second),ShopBranchEntity(id=-3,shopId=0,address="Мира, 3")))
  street=db.shopBranchDao().forShop(shop).single{it.venueId==null}.id
 }
 private suspend fun waitFor(condition:()->Boolean){val end=System.nanoTime()+20_000_000_000;while(!condition()&&System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20));yield();Thread.sleep(5)};assertTrue(condition())}
 @After fun close():Unit=runBlocking{store.clear();waitFor{!ProductEditorTransactions.mutex.isLocked};locations.closeForTest();db.close()}
 private fun manifest(f:File)=ZipFile(f).use{z->JSONObject(z.getInputStream(z.getEntry("manifest.json")).reader().readText())}
 private fun archive(j:JSONObject)=File(app.cacheDir,"altered220.zip").also{f->ZipOutputStream(f.outputStream()).use{z->z.putNextEntry(ZipEntry("manifest.json"));z.write(j.toString().toByteArray());z.closeEntry()}}
 @Test fun mixedNetworkAppearsUnderEachMallAndAsStreetStoreWithoutDuplicatingIdentity():Unit=runBlocking{
  val vm=ShopListViewModel(shops);store.put("shops",vm);waitFor{!vm.uiState.value.isLoading}
  val groups=vm.uiState.value.groups.filter{it.shops.any{s->s.id==shop}}
  assertEquals(3,groups.size);assertEquals(setOf(first,second,null),groups.map{it.venue?.id}.toSet());assertEquals(1,vm.uiState.value.shops.size)
  assertEquals("Мира, 3",groups.single{it.venue==null}.shops.single().address)
  assertTrue(groups.all{it.shops.single().networkId==network});assertEquals("Лента",shops.getShopsForDisplay().first().single().name)
 }
 @Test fun shopContentLabelsEveryPointAndPurchaseGroupsKeepAllThreeAssignments():Unit=runBlocking{
  val points=db.shopBranchDao().forShop(shop)
  val product=products.saveWithLinks(ProductEntity(name="Молоко"),listOf(ProductShopLinkEntity(productId=0,shopId=shop)),availability=mapOf(shop to points.map{it.id}.toSet()))
  points.forEach{basket.addProduct(product,1.0,choice=ShopChoice.EXPLICIT,shopId=shop,branchId=it.id)}
  val content=ShopContentViewModel(shop,products,shops,basket,ClassifierRepository(db.productGroupDao(),db.recipientDao(),db.unitDao(),db));store.put("content",content)
  waitFor{content.uiState.value.sections.count{it.id!=null}==3}
  val names=content.uiState.value.sections.filter{it.id!=null}.map{it.name}
  assertTrue(names.contains("ТЦ Зелёный — Парковая, 1"));assertTrue(names.contains("ТЦ Северный — Северная, 2"));assertTrue(names.contains("Мира, 3"))
  val shopping=ShoppingListViewModel(basket,products,shops,ClassifierRepository(db.productGroupDao(),db.recipientDao(),db.unitDao(),db),app.getSharedPreferences("220-shopping",0));store.put("shopping",shopping)
  waitFor{shopping.uiState.value.groups.filter{it.groupId==shop}.let{rows->rows.size==3&&rows.map{it.venueId}.toSet()==setOf(first,second,null)}}
  val groups=shopping.uiState.value.groups.filter{it.groupId==shop};assertEquals(setOf(first,second,null),groups.map{it.venueId}.toSet());assertTrue(groups.all{it.purchaseBackground=="auto"})
  db.shopDao().update(db.shopDao().getById(shop)!!.copy(purchaseBackground="#DDEEAA"));waitFor{shopping.uiState.value.groups.filter{it.groupId==shop}.all{it.purchaseBackground=="#DDEEAA"}}
  val entries=db.shoppingListEntryDao().getAllSync();assertEquals(3,entries.size);assertEquals(points.map{it.id}.toSet(),entries.map{it.assignedBranchId}.toSet());assertTrue(entries.all{it.assignedShopId==shop})
  val labels=com.spisokryadom.app.domain.PlaceLabels(points,db.retailStructureDao().venues());assertEquals("Лента",labels.shop(shop,"Лента"))
 }
 @Test fun removingOneMallDetachesOnlyItsPointAndPreservesNetworkAndPurchases():Unit=runBlocking{
  val points=db.shopBranchDao().forShop(shop);val product=products.saveWithLinks(ProductEntity(name="Товар"),listOf(ProductShopLinkEntity(productId=0,shopId=shop)))
  points.forEach{basket.addProduct(product,1.0,choice=ShopChoice.EXPLICIT,shopId=shop,branchId=it.id)}
  shops.deleteVenue(first)
  assertEquals(network,db.shopDao().getById(shop)!!.networkId);assertEquals(3,db.shoppingListEntryDao().getAllSync().size)
  assertNull(db.shopBranchDao().get(points.single{it.venueId==first}.id)!!.venueId);assertEquals(second,db.shopBranchDao().get(points.single{it.venueId==second}.id)!!.venueId)
  db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
 }
 @Test fun backup16PreservesMixedMembershipAndAllBackgroundModesAndReadsOld15():Unit=runBlocking{
  val manager=BackupManager(app,db)
  for(background in listOf("","auto","#B2D8E1")){
   db.shopDao().update(db.shopDao().getById(shop)!!.copy(purchaseBackground=background));val file=manager.exportFile();assertEquals(20,manifest(file).getInt("version"));db.shopDao().update(db.shopDao().getById(shop)!!.copy(purchaseBackground=""));manager.importFile(file)
   assertEquals(background,db.shopDao().getById(shop)!!.purchaseBackground);assertEquals(network,db.shopDao().getById(shop)!!.networkId);assertEquals(setOf(first,second,null),db.shopBranchDao().forShop(shop).map{it.venueId}.toSet());manager.discard(file)
  }
  val file=manager.exportFile();val old=manifest(file).put("version",15);old.getJSONObject("data").getJSONArray("shops").getJSONObject(0).remove("purchaseBackground");manager.importFile(archive(old));assertEquals("",db.shopDao().getById(shop)!!.purchaseBackground);manager.discard(file)
 }
 @Test fun malformedBackgroundAndCrossCityMallFailWithoutPartialWrites():Unit=runBlocking{
  val manager=BackupManager(app,db);val file=manager.exportFile();val before=db.shopDao().getAllSync()
  for(bad in listOf<Any>("#oops","custom",true,47,JSONObject.NULL)){
   val root=manifest(file);root.getJSONObject("data").getJSONArray("shops").getJSONObject(0).put("purchaseBackground",bad)
   assertTrue(runCatching{manager.importFile(archive(root))}.isFailure);assertEquals(before,db.shopDao().getAllSync())
  }
  db.cityDao().insert(CityEntity(2,"Другой город"));val foreign=shops.ensureVenue(2,"Чужой ТЦ");val oldPoints=db.shopBranchDao().forShop(shop)
  assertTrue(runCatching{shops.saveWithDepartments(before.single().copy(name="Не записывать"),emptyList(),oldPoints.map{it.copy(venueId=foreign)})}.isFailure)
  assertEquals(before,db.shopDao().getAllSync());assertEquals(oldPoints,db.shopBranchDao().forShop(shop));manager.discard(file)
 }
 @Test fun backgroundAndUnitRankingPreserveDefaultsAndAllOptions(){
  val surface=Color.White;assertEquals(surface,purchaseCardColor("rose","",surface));assertEquals(surface,purchaseCardColor("","auto",surface))
  val block=shoppingBlockColor("rose",surface)!!;val card=purchaseCardColor("rose","auto",surface);assertTrue(card.green<block.green);assertTrue(card.blue<=block.blue)
  assertNotEquals(card,purchaseCardColor("rose","#3366AA",surface))
  val choices=listOf("кг","л","шт.","уп.")
  assertEquals(listOf("шт.","кг","л","уп."),UnitChoices.ranked(choices,"шт"));assertEquals(choices.toSet(),UnitChoices.ranked(choices,"неизвестное").toSet());assertEquals(choices,UnitChoices.ranked(choices,""))
 }
 @Test fun migration18AddsOrdinaryBackgroundWithoutChangingIdentityOrRelations():Unit=runBlocking{
  val name="220-migration.db";app.deleteDatabase(name)
  val schema=File("schemas/com.spisokryadom.app.data.db.AppDatabase/18.json").takeIf{it.isFile}?:File("app/schemas/com.spisokryadom.app.data.db.AppDatabase/18.json")
  app.openOrCreateDatabase(name,0,null).use{old->val entities=JSONObject(schema.readText()).getJSONObject("database").getJSONArray("entities");for(i in 0 until entities.length()){val e=entities.getJSONObject(i);old.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName")));val indices=e.getJSONArray("indices");for(j in 0 until indices.length())old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName")))}
   old.execSQL("INSERT INTO cities(id,name) VALUES(1,'Город')");old.execSQL("INSERT INTO retail_networks(id,name) VALUES(4,'Сеть')");old.execSQL("INSERT INTO shops(id,name,cityId,networkId,displayOrder,manualOrderOverride,shoppingBackground,iconKey) VALUES(7,'Магазин',1,4,9,1,'rose','store')");old.version=18
  }
  val migrated=Room.databaseBuilder(app,AppDatabase::class.java,name).allowMainThreadQueries().addMigrations(AppDatabase.MIGRATION_18_19,AppDatabase.MIGRATION_19_20,AppDatabase.MIGRATION_20_21,AppDatabase.MIGRATION_21_22,AppDatabase.MIGRATION_22_23).build()
  try{val s=migrated.shopDao().getById(7)!!;assertEquals("",s.purchaseBackground);assertEquals(4L,s.networkId);assertEquals("rose",s.shoppingBackground);assertEquals(9,s.displayOrder);assertEquals("store",s.iconKey);assertTrue(s.manualOrderOverride);assertEquals(23,migrated.openHelper.writableDatabase.version)}finally{migrated.close();app.deleteDatabase(name)}
 }
}
