package com.spisokryadom.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.backup.*
import com.spisokryadom.app.data.db.*
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File
import java.util.zip.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class Venues242DataTest {
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 private lateinit var db:AppDatabase;private lateinit var locations:LocationRepository;private lateinit var shops:ShopRepository
 @Before fun setup()=runBlocking{
  db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build();RetailConstraints.install(db.openHelper.writableDatabase)
  db.cityDao().insert(CityEntity(1,"Красноярск"));db.cityDao().insert(CityEntity(2,"Томск"))
  locations=LocationRepository(db.cityDao(),db.shopDao(),app.getSharedPreferences("venues242",0));shops=ShopRepository(db.shopDao(),db.shopDepartmentDao(),locations,db)
 }
 @After fun close(){locations.closeForTest();db.close()}
 private suspend fun venue(kind:String=VenueKinds.MARKET)=db.retailStructureDao().insertVenue(VenueEntity(cityId=1,name="Место",address="Общий адрес, 1",kind=kind))
 private fun fk(){db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}}
 @Test fun cityMoveKeepsSingleVenueProfilesAndPurchasesIdentical()=runBlocking{
  val v=venue();val s=shops.saveWithDepartments(ShopEntity(name="Павильон"),emptyList(),listOf(ShopBranchEntity(-1,0,"",venueId=v,label="29")))
  val p=ProductRepository(db.productDao(),db.productShopLinkDao(),db).saveWithLinks(ProductEntity(name="Сыр"),listOf(ProductShopLinkEntity(productId=0,shopId=s)))
  ShoppingListRepository(db.shoppingListEntryDao(),db,locations).addProduct(p,choice=ShopChoice.EXPLICIT,shopId=s)
  val points=db.shopBranchDao().getAll();val purchases=db.shoppingListEntryDao().getAllSync()
  shops.updateVenue(db.retailStructureDao().venue(v)!!.copy(cityId=2,kind=VenueKinds.MALL))
  assertEquals(2L,db.shopDao().getById(s)!!.cityId);assertEquals(points,db.shopBranchDao().getAll());assertEquals(purchases,db.shoppingListEntryDao().getAllSync())
  assertEquals(VenueKinds.MALL,db.retailStructureDao().venue(v)!!.kind);assertEquals(1L,locations.currentId());fk()
 }
 @Test fun movingMallSplitsMixedNetworkWithoutMovingStreetOrLosingRestrictedLinks()=runBlocking{
  val v=venue(VenueKinds.MALL);val network=shops.ensureNetwork("Сеть")
  val s=shops.saveWithDepartments(ShopEntity(name="Fix Price",networkId=network,shoppingBackground="rose",iconKey="bag"),listOf(ShopDepartmentEntity(shopId=0,name="Быт",displayOrder=1)),listOf(ShopBranchEntity(-1,0,"Мира, 1"),ShopBranchEntity(-2,0,"",venueId=v,label="2 этаж")),purchaseMode="online")
  val points=db.shopBranchDao().forShop(s);val street=points.first();val mall=points.last();val dept=db.shopDepartmentDao().getByShopIdSync(s).single()
  val products=ProductRepository(db.productDao(),db.productShopLinkDao(),db)
  val common=products.saveWithLinks(ProductEntity(name="Общий"),listOf(ProductShopLinkEntity(productId=0,shopId=s,departmentId=dept.id)))
  val restricted=db.productDao().insert(ProductEntity(name="Только в ТЦ"));db.productShopLinkDao().insert(ProductShopLinkEntity(productId=restricted,shopId=s,departmentId=dept.id,restrictBranches=true));db.shopBranchDao().insertAvailability(ProductBranchAvailabilityEntity(restricted,mall.id,s));db.shopBranchDao().setPriority(ProductBranchPriorityEntity(restricted,mall.id,s,3));db.retailStructureDao().setProductDepartment(ProductBranchDepartmentEntity(restricted,mall.id,s,dept.typeId))
  val basket=ShoppingListRepository(db.shoppingListEntryDao(),db,locations)
  val row=basket.addProduct(restricted,quantity=3.0,unit="уп",choice=ShopChoice.EXPLICIT,shopId=s,branchId=mall.id)
  val other=basket.addProduct(common,choice=ShopChoice.EXPLICIT,shopId=s,branchId=street.id)
  val before=db.shoppingListEntryDao().getById(row)!!;val outside=db.shoppingListEntryDao().getById(other)!!
  shops.updateVenue(db.retailStructureDao().venue(v)!!.copy(cityId=2))
  val moved=db.shopBranchDao().get(mall.id)!!;assertNotEquals(s,moved.shopId);assertEquals(2L,db.shopDao().getById(moved.shopId)!!.cityId)
  assertEquals(street,db.shopBranchDao().get(street.id));assertEquals(1L,db.shopDao().getById(s)!!.cityId)
  val target=db.shopDao().getById(moved.shopId)!!;assertEquals(network,target.networkId);assertEquals("bag",target.iconKey);assertEquals("rose",target.shoppingBackground);assertEquals("online",db.creationPresetDao().shopMode(target.id))
  val after=db.shoppingListEntryDao().getById(row)!!;assertEquals(before.copy(assignedShopId=target.id,assignedDepartmentId=after.assignedDepartmentId),after);assertEquals(outside,db.shoppingListEntryDao().getById(other))
  assertEquals(dept.typeId,db.shopDepartmentDao().getById(after.assignedDepartmentId!!)!!.typeId)
  assertEquals(target.id,db.shopBranchDao().availability(restricted).single().shopId);assertEquals(target.id,db.shopBranchDao().priorities().single().shopId);assertEquals(target.id,db.retailStructureDao().productDepartments().single().shopId)
  assertEquals(listOf(target.id),db.productShopLinkDao().getLinksForProductSync(restricted).map{it.shopId});assertEquals(setOf(s,target.id),db.productShopLinkDao().getLinksForProductSync(common).map{it.shopId}.toSet());fk()
 }
 @Test fun failedCityOrTypeChangeLeavesTheWholeGraphUntouched()=runBlocking{
  val id=venue();val old=db.retailStructureDao().venue(id)!!
  val s=shops.saveWithDepartments(ShopEntity(name="Точка"),emptyList(),listOf(ShopBranchEntity(-1,0,"",venueId=id)))
  val before=db.shopBranchDao().getAll()
  assertTrue(runCatching{shops.updateVenue(old.copy(cityId=999))}.isFailure)
  assertTrue(runCatching{shops.updateVenue(old.copy(kind="bad"))}.isFailure)
  db.retailStructureDao().insertVenue(VenueEntity(cityId=2,name="Место"))
  assertTrue(runCatching{shops.updateVenue(old.copy(cityId=2))}.isFailure)
  assertEquals(old,db.retailStructureDao().venue(id));assertEquals(before,db.shopBranchDao().getAll());assertEquals(1L,db.shopDao().getById(s)!!.cityId);fk()
 }
 @Test fun explicitConversionStoresChosenTypeAndPreservesPointIdentity()=runBlocking{
  for(kind in VenueKinds.choices){
   val s=shops.saveWithDepartments(ShopEntity(name=kind),emptyList(),listOf(ShopBranchEntity(-1,0,"Павильон")))
   val point=db.shopBranchDao().forShop(s).single();val v=shops.convertToVenue(s,kind)
   assertEquals(kind,db.retailStructureDao().venue(v)!!.kind);assertEquals(v,db.shopBranchDao().get(point.id)!!.venueId);assertNull(db.shopDao().getById(s))
  };fk()
 }

 @Test fun unavailableMovedPointDoesNotMakeAStreetOnlyProductAvailableInTheOtherCity()=runBlocking{
  val v=venue();val s=shops.saveWithDepartments(ShopEntity(name="Сеть"),emptyList(),listOf(ShopBranchEntity(-1,0,"Улица"),ShopBranchEntity(-2,0,"",venueId=v)))
  val points=db.shopBranchDao().forShop(s);val p=db.productDao().insert(ProductEntity(name="Только на улице"))
  db.productShopLinkDao().insert(ProductShopLinkEntity(productId=p,shopId=s,restrictBranches=true));db.shopBranchDao().insertAvailability(ProductBranchAvailabilityEntity(p,points.first().id,s))
  db.shopBranchDao().setPriority(ProductBranchPriorityEntity(p,points.last().id,s,1))
  shops.updateVenue(db.retailStructureDao().venue(v)!!.copy(cityId=2))
  assertEquals(listOf(s),db.productShopLinkDao().getLinksForProductSync(p).map{it.shopId});assertEquals(points.first().id,db.shopBranchDao().availability(p).single().branchId);assertTrue(db.shopBranchDao().priorities().isEmpty());fk()
 }
 private fun root(file:File)=ZipFile(file).use{JSONObject(it.getInputStream(it.getEntry("manifest.json")).reader().readText())}
 private fun archive(json:JSONObject)=File(app.cacheDir,"venues242.zip").also{f->ZipOutputStream(f.outputStream()).use{z->z.putNextEntry(ZipEntry("manifest.json"));z.write(json.toString().toByteArray());z.closeEntry()}}
 @Test fun backupKeepsTypesReadsLegacyAndRejectsMalformedTypeBeforeReplacingData()=runBlocking{
  val id=venue();val manager=BackupManager(app,db);val file=manager.exportFile();val json=root(file);assertEquals(21,json.getInt("version"));manager.importFile(file);assertEquals(VenueKinds.MARKET,db.retailStructureDao().venue(id)!!.kind)
  val row=json.getJSONObject("data").getJSONArray("venues").getJSONObject(0)
  row.put("kind","unsupported");assertTrue(runCatching{manager.importFile(archive(json))}.isFailure);assertEquals(VenueKinds.MARKET,db.retailStructureDao().venue(id)!!.kind)
  row.remove("kind");assertTrue(runCatching{manager.importFile(archive(json))}.isFailure)
  json.put("version",18);manager.importFile(archive(json));assertEquals(VenueKinds.UNSPECIFIED,db.retailStructureDao().venue(id)!!.kind);fk();manager.discard(file)
 }
 @Test fun migration21PreservesExistingCityNameAndAddressWithoutGuessingKind()=runBlocking{
  val name="venues242-migration.db";app.deleteDatabase(name)
  val schema=File("schemas/com.spisokryadom.app.data.db.AppDatabase/21.json").takeIf{it.isFile}?:File("app/schemas/com.spisokryadom.app.data.db.AppDatabase/21.json")
  app.openOrCreateDatabase(name,0,null).use{old->val entities=JSONObject(schema.readText()).getJSONObject("database").getJSONArray("entities");for(i in 0 until entities.length()){val e=entities.getJSONObject(i);old.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName")));val indices=e.getJSONArray("indices");for(j in 0 until indices.length())old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName")))}
   old.execSQL("INSERT INTO cities(id,name)VALUES(1,'Город')");old.execSQL("INSERT INTO venues(id,cityId,name,address,photoUri)VALUES(7,1,'Рынок','Общий адрес','photo.jpg')");old.version=21
  }
  val migrated=Room.databaseBuilder(app,AppDatabase::class.java,name).allowMainThreadQueries().addMigrations(AppDatabase.MIGRATION_21_22,AppDatabase.MIGRATION_22_23).build()
  try{assertEquals(23,migrated.openHelper.writableDatabase.version);assertEquals(VenueEntity(7,1,"Рынок","Общий адрес",photoUri="photo.jpg"),migrated.retailStructureDao().venue(7))}finally{migrated.close();app.deleteDatabase(name)}
 }
}
