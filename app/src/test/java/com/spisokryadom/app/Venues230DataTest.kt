package com.spisokryadom.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.backup.*
import com.spisokryadom.app.data.db.*
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.domain.*
import com.spisokryadom.app.widget.ShoppingWidgetData
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import org.json.JSONObject
import java.io.File
import java.util.zip.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class Venues230DataTest {
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 private lateinit var db:AppDatabase;private lateinit var locations:LocationRepository;private lateinit var shops:ShopRepository;private lateinit var products:ProductRepository;private lateinit var basket:ShoppingListRepository
 private var venue=0L;private var shop=0L
 @Before fun setup():Unit=runBlocking{
  db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build();RetailConstraints.install(db.openHelper.writableDatabase);db.cityDao().insert(CityEntity(1,"Город"))
  locations=LocationRepository(db.cityDao(),db.shopDao(),app.getSharedPreferences("230-location",0));shops=ShopRepository(db.shopDao(),db.shopDepartmentDao(),locations,db);products=ProductRepository(db.productDao(),db.productShopLinkDao(),db);basket=ShoppingListRepository(db.shoppingListEntryDao(),db,locations)
  venue=db.retailStructureDao().insertVenue(VenueEntity(cityId=1,name="ТЦ Аврора",address="Парковая, 10"))
  shop=shops.saveWithDepartments(ShopEntity(name="Fix Price",networkId=shops.ensureNetwork("Fix Price")),emptyList(),listOf(ShopBranchEntity(-1,0,"Мира, 1"),ShopBranchEntity(-2,0,"",venueId=venue)))
 }
 @After fun close(){locations.closeForTest();db.close()}
 private fun manifest(f:File)=ZipFile(f).use{z->JSONObject(z.getInputStream(z.getEntry("manifest.json")).reader().readText())}
 private fun archive(root:JSONObject)=File(app.cacheDir,"230-input.zip").also{f->ZipOutputStream(f.outputStream()).use{z->z.putNextEntry(ZipEntry("manifest.json"));z.write(root.toString().toByteArray());z.closeEntry()}}
 private suspend fun save(points:List<ShopBranchEntity>,edits:Map<Long,VenueEntity> = emptyMap())=shops.saveWithDepartments(db.shopDao().getById(shop)!!,emptyList(),points,venueEdits=edits)
 @Test fun secondPointCanBecomeFirstAndNewPurchasesUseItAfterReload():Unit=runBlocking{
  val before=db.shopBranchDao().forShop(shop);val mall=before[1]
  save(OutletLocations.move(before,mall.id,-1));val after=db.shopBranchDao().forShop(shop)
  assertEquals(listOf(mall.id,before[0].id),after.map{it.id});assertEquals(listOf(0,1),after.map{it.displayOrder});assertEquals(venue,after[0].venueId)
  val p=products.saveWithLinks(ProductEntity(name="Батарейки"),listOf(ProductShopLinkEntity(productId=0,shopId=shop)))
  basket.addProduct(p,choice=ShopChoice.EXPLICIT,shopId=shop);assertEquals(mall.id,db.shoppingListEntryDao().getAllSync().single().assignedBranchId)
  save(OutletLocations.move(after,mall.id,1));assertEquals(before.map{it.id},db.shopBranchDao().forShop(shop).map{it.id})
 }
 @Test fun newUnsavedPointCanBeFirstWithoutChangingExistingIdentity():Unit=runBlocking{
  val before=db.shopBranchDao().forShop(shop);val added=ShopBranchEntity(-7,shop,"Северная, 3")
  save(OutletLocations.move(OutletLocations.move(before+added,-7,-1),-7,-1));val after=db.shopBranchDao().forShop(shop)
  assertEquals("Северная, 3",after[0].address);assertEquals(before.map{it.id},after.drop(1).map{it.id});assertEquals(listOf(0,1,2),after.map{it.displayOrder})
 }
 @Test fun sharedAddressUpdatesAllRetailersAndTransferGoalAndWidgetPresentations():Unit=runBlocking{
  val other=shops.saveWithDepartments(ShopEntity(name="Галамарт"),emptyList(),listOf(ShopBranchEntity(-1,0,"",venueId=venue)))
  val point=db.shopBranchDao().forShop(shop).single{it.venueId==venue};val p=products.saveWithLinks(ProductEntity(name="Батарейки"),listOf(ProductShopLinkEntity(productId=0,shopId=shop)))
  basket.addProduct(p,choice=ShopChoice.EXPLICIT,shopId=shop,branchId=point.id)
  shops.updateVenue(db.retailStructureDao().venue(venue)!!.copy(address="Новый адрес, 8"))
  val labels=PlaceLabels(db.shopBranchDao().getAll(),db.retailStructureDao().venues())
  for(s in listOf(shop,other))assertEquals("Новый адрес, 8",labels.address(db.shopBranchDao().forShop(s).single{it.venueId==venue}))
  val entries=db.shoppingListEntryDao().getAllSync();val place=basket.purchasePlaces(entries.map{it.id}.toSet()).single{it.branchId==point.id}.label
  assertTrue(place.contains("Новый адрес, 8"));assertFalse(place.contains("Парковая, 10"))
  val goal=GoalListSnapshot(GoalListEntity(name="План"),entries,db.productDao().getAllSync(),db.shopDao().getAllSync(),db.shopBranchDao().getAll(),db.retailStructureDao().venues())
  assertTrue(goal.place(entries.single())!!.text.contains("Новый адрес, 8"))
  assertTrue(ShoppingWidgetData(db,basket,locations).rows().single().detail.contains("Новый адрес, 8"));assertEquals(1,db.shoppingListEntryDao().getAllSync().size)
 }
 @Test fun severalPointsInOneMallHaveOptionalOwnLabelsAndOneAddress():Unit=runBlocking{
  val current=db.shopBranchDao().forShop(shop)
  save(current+ShopBranchEntity(-6,0,"",venueId=venue,label="Вход 2"))
  assertEquals(3,db.shopBranchDao().forShop(shop).size)
  assertTrue(runCatching{save(db.shopBranchDao().forShop(shop)+ShopBranchEntity(-8,0,"",venueId=venue))}.isFailure)
  assertEquals(3,db.shopBranchDao().forShop(shop).size)
 }
 @Test fun noPostalAddressIsRequiredForPavilionAndNewVenueIsCreatedAtomically():Unit=runBlocking{
  val draft=VenueEntity(-9,1,"Рынок",address=null)
  save(db.shopBranchDao().forShop(shop)+ShopBranchEntity(-8,0,"",venueId=-9,label="29 павильон"),mapOf(-9L to draft))
  val point=db.shopBranchDao().forShop(shop).single{it.label=="29 павильон"};assertTrue(point.venueId!!>0)
  assertEquals("Рынок",db.retailStructureDao().venue(point.venueId)!!.name);assertEquals("",point.address)
 }
 @Test fun sharedVenueChangesRollbackWhenOutletValidationFails():Unit=runBlocking{
  val old=db.retailStructureDao().venue(venue)!!;val points=db.shopBranchDao().forShop(shop)
  assertTrue(runCatching{save(points+points.first().copy(id=-10),mapOf(venue to old.copy(address="Не сохранять")))}.isFailure)
  assertEquals(old,db.retailStructureDao().venue(venue));assertEquals(points,db.shopBranchDao().forShop(shop))
 }
 @Test fun venueAndOutletPhotosRoundTripIndependentlyAndCleanupKeepsBoth():Unit=runBlocking{
  fun photo(name:String,bytes:ByteArray)=File(app.filesDir,name).also{it.parentFile!!.mkdirs();it.writeBytes(bytes)}.absolutePath
  val venuePhoto=photo("venue_photos/venue.jpg",byteArrayOf(1,2,3));val pointPhoto=photo("branch_photos/point.jpg",byteArrayOf(4,5,6))
  shops.updateVenue(db.retailStructureDao().venue(venue)!!.copy(photoUri=venuePhoto));val points=db.shopBranchDao().forShop(shop)
  save(points.map{if(it.venueId==venue)it.copy(photoUri=pointPhoto)else it});MediaLifecycle.cleanup(app,db)
  assertTrue(File(venuePhoto).isFile);assertTrue(File(pointPhoto).isFile)
  val manager=BackupManager(app,db);val file=manager.exportFile();assertEquals(23,manifest(file).getInt("version"))
  ZipFile(file).use{assertNotNull(it.getEntry("venue_photos/$venue.jpg"));assertNotNull(it.getEntry("branch_photos/${points.single{p->p.venueId==venue}.id}.jpg"))}
  manager.importFile(file);val restoredVenue=db.retailStructureDao().venue(venue)!!.photoUri!!;val restoredPoint=db.shopBranchDao().forShop(shop).single{it.venueId==venue}.photoUri!!
  assertNotEquals(restoredVenue,restoredPoint);assertArrayEquals(byteArrayOf(1,2,3),File(restoredVenue).readBytes());assertArrayEquals(byteArrayOf(4,5,6),File(restoredPoint).readBytes());manager.discard(file)
 }
 @Test fun old16BackupSeparatesRepeatedPostalAddressFromPavilionDescription():Unit=runBlocking{
  val manager=BackupManager(app,db);val root=manifest(manager.exportFile()).put("version",16);val rows=root.getJSONObject("data").getJSONArray("shopBranches")
  for(i in 0 until rows.length()){val p=rows.getJSONObject(i);p.remove("label");if(!p.isNull("venueId"))p.put("address","Парковая, 10")}
  manager.importFile(archive(root));val mall=db.shopBranchDao().forShop(shop).single{it.venueId==venue};assertEquals("",mall.address);assertEquals("",mall.label)
  for(i in 0 until rows.length()){val p=rows.getJSONObject(i);if(!p.isNull("venueId"))p.put("address","Павильон 29")}
  manager.importFile(archive(root));assertEquals("Павильон 29",db.shopBranchDao().forShop(shop).single{it.venueId==venue}.label)
 }
 @Test fun deletingVenueKeepsItsPostalAddressWithOutletAndDoesNotDeleteNetwork():Unit=runBlocking{
  val id=db.shopBranchDao().forShop(shop).single{it.venueId==venue}.id;shops.deleteVenue(venue)
  val point=db.shopBranchDao().get(id)!!;assertNull(point.venueId);assertEquals("Парковая, 10",point.address);assertNotNull(db.shopDao().getById(shop)!!.networkId)
 }
 @Test fun malformedPointLabelOrPhotoCannotPartiallyReplaceCatalogue():Unit=runBlocking{
  val manager=BackupManager(app,db);val file=manager.exportFile();val before=db.shopDao().getAllSync()
  val root=manifest(file);root.getJSONObject("data").getJSONArray("shopBranches").getJSONObject(0).put("label",45)
  assertTrue(runCatching{manager.importFile(archive(root))}.isFailure);assertEquals(before,db.shopDao().getAllSync())
  val bad=manifest(file);bad.getJSONObject("data").getJSONArray("venues").getJSONObject(0).put("photo","venue_photos/999.jpg")
  assertTrue(runCatching{manager.importFile(archive(bad))}.isFailure);assertEquals(before,db.shopDao().getAllSync());manager.discard(file)
 }
 @Test fun migration19PreservesIdsOrderMembershipAndSeparatesCommonAddress():Unit=runBlocking{
  val name="230-migration.db";app.deleteDatabase(name)
  val schema=File("schemas/com.spisokryadom.app.data.db.AppDatabase/19.json").takeIf{it.isFile}?:File("app/schemas/com.spisokryadom.app.data.db.AppDatabase/19.json")
  app.openOrCreateDatabase(name,0,null).use{old->val entities=JSONObject(schema.readText()).getJSONObject("database").getJSONArray("entities");for(i in 0 until entities.length()){val e=entities.getJSONObject(i);old.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName")));val indices=e.getJSONArray("indices");for(j in 0 until indices.length())old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName")))}
   old.execSQL("INSERT INTO cities(id,name) VALUES(1,'Город')");old.execSQL("INSERT INTO venues(id,cityId,name,address) VALUES(5,1,'ТЦ','Мира, 1')");old.execSQL("INSERT INTO shops(id,name,displayOrder,cityId,manualOrderOverride) VALUES(7,'Магазин',0,1,0)")
   old.execSQL("INSERT INTO shop_branches(id,shopId,address,displayOrder,venueId) VALUES(10,7,'Мира, 1',1,5),(11,7,'Павильон 29',0,5),(12,7,'Улица, 3',2,NULL)");old.version=19
  }
  val migrated=Room.databaseBuilder(app,AppDatabase::class.java,name).allowMainThreadQueries().addMigrations(AppDatabase.MIGRATION_19_20,AppDatabase.MIGRATION_20_21,AppDatabase.MIGRATION_21_22,AppDatabase.MIGRATION_22_23,AppDatabase.MIGRATION_23_24,AppDatabase.MIGRATION_24_25).build()
  try{val points=migrated.shopBranchDao().forShop(7);assertEquals(listOf(11L,10L,12L),points.map{it.id});assertEquals("Павильон 29",points[0].label);assertEquals("",points[0].address);assertEquals("",points[1].label);assertEquals("Улица, 3",points[2].address);assertNull(migrated.retailStructureDao().venue(5)!!.photoUri);assertEquals(25,migrated.openHelper.writableDatabase.version)}finally{migrated.close();app.deleteDatabase(name)}
 }
 @Test fun ambiguousLegacyOutletsKeepBothIdsAndDistinctDescriptions(){
  val points=listOf(ShopBranchEntity(10,7,"Магазин",venueId=5),ShopBranchEntity(11,7,"Общий адрес",venueId=5))
  val result=OutletLocations.migrate(points,listOf(VenueEntity(5,1,"ТЦ","Общий адрес")),mapOf(7L to "Магазин"))
  assertEquals(listOf(10L,11L),result.map{it.id});assertEquals(setOf("Магазин","Общий адрес"),result.map{it.label}.toSet());assertTrue(OutletLocations.valid(result));assertTrue(result.all{it.address.isEmpty()})
 }

 @Test fun globalPointReorderPreservesExplicitProductPriorityAvailabilityAndExistingPurchase():Unit=runBlocking{
  val before=db.shopBranchDao().forShop(shop);val street=before.first().id;val mall=before.last().id
  val p=products.saveWithLinks(ProductEntity(name="Товар с собственным порядком"),listOf(ProductShopLinkEntity(productId=0,shopId=shop)),availability=mapOf(shop to before.map{it.id}.toSet()),branchPriorities=mapOf(shop to listOf(street,mall)))
  val entry=basket.addProduct(p,choice=ShopChoice.EXPLICIT,shopId=shop);val fact=db.shoppingListEntryDao().getById(entry)!!
  save(OutletLocations.move(before,mall,-1))
  assertEquals(mall,db.shopBranchDao().forShop(shop).first().id);assertEquals(street,basket.resolveBranch(p,shop));assertEquals(fact,db.shoppingListEntryDao().getById(entry))
  assertEquals(before.map{it.id}.toSet(),db.shopBranchDao().availability(p).map{it.branchId}.toSet())
 }

 @Test fun namedStreetOutletAndRepeatedRetailerLabelDoNotRepeatAddressOrName(){
  val street=ShopBranchEntity(1,1,"Мира, 1",label="У дома")
  assertEquals("У дома · Мира, 1",PlaceLabels().branch(street));assertEquals("Магазин · У дома · Мира, 1",PlaceLabels().place("Магазин",street).text)
  val mall=ShopBranchEntity(2,1,"",venueId=5,label="Магазин")
  val labels=PlaceLabels(venues=listOf(VenueEntity(5,1,"ТЦ","Общий адрес")))
  assertEquals("ТЦ — Магазин · Общий адрес",labels.place("Магазин",mall).text)
 }

 @Test fun deletingVenueWithoutPostalAddressKeepsNamedPointAndBackupRemainsValid():Unit=runBlocking{
  shops.updateVenue(db.retailStructureDao().venue(venue)!!.copy(address=null))
  val id=db.shopBranchDao().forShop(shop).single{it.venueId==venue}.id
  shops.deleteVenue(venue)
  val point=db.shopBranchDao().get(id)!!;assertNull(point.venueId);assertEquals("",point.address);assertEquals("ТЦ Аврора",point.label)
  val manager=BackupManager(app,db);val file=manager.exportFile();manager.importFile(file)
  assertEquals(point,db.shopBranchDao().get(id));assertTrue(OutletLocations.valid(db.shopBranchDao().forShop(shop)));manager.discard(file)
 }

 @Test fun movingMixedNetworkToAnotherCityKeepsDistinctOutletsAndRollsBackInvalidVenue():Unit=runBlocking{
  val second=db.retailStructureDao().insertVenue(VenueEntity(cityId=1,name="ТЦ Второй",address="Северная, 2"))
  save(db.shopBranchDao().forShop(shop)+ShopBranchEntity(-9,0,"",venueId=second))
  val before=db.shopBranchDao().forShop(shop);val retailer=db.shopDao().getById(shop)!!
  db.cityDao().insert(CityEntity(2,"Другой город"))
  val nextA=db.retailStructureDao().insertVenue(VenueEntity(cityId=2,name="ТЦ Новый А",address="Новая, 1"))
  val nextB=db.retailStructureDao().insertVenue(VenueEntity(cityId=2,name="ТЦ Новый Б",address="Новая, 2"))
  val next=before.map{p->p.copy(venueId=when(p.venueId){venue->nextA;second->nextB;else->null})}
  assertTrue(runCatching{shops.saveWithDepartments(retailer.copy(cityId=2),emptyList(),next.map{if(it.venueId==nextA)it.copy(venueId=venue)else it})}.isFailure)
  assertEquals(before,db.shopBranchDao().forShop(shop));assertEquals(retailer,db.shopDao().getById(shop))
  shops.saveWithDepartments(retailer.copy(cityId=2),emptyList(),next)
  val after=db.shopBranchDao().forShop(shop);assertEquals(before.map{it.id},after.map{it.id});assertEquals(setOf(nextA,nextB,null),after.map{it.venueId}.toSet());assertEquals(2L,db.shopDao().getById(shop)!!.cityId)
  assertTrue(after.none{it.label.startsWith("relocating:")});db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
 }

}
