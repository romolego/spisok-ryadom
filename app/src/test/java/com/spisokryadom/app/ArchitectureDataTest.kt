package com.spisokryadom.app

import android.app.Application
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.backup.BackupManager
import com.spisokryadom.app.data.db.*
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArchitectureDataTest {
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 private lateinit var db:AppDatabase
 private lateinit var locations:LocationRepository
 private lateinit var products:ProductRepository
 private lateinit var shops:ShopRepository
 private lateinit var basket:ShoppingListRepository
 private var a=0L;private var b=0L;private var first=0L;private var second=0L
 @Before fun setup()=runBlocking{
  db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build()
  RetailConstraints.install(db.openHelper.writableDatabase)
  db.cityDao().insert(CityEntity(1,"Красноярск"));db.cityDao().insert(CityEntity(2,"Иркутск"))
  locations=LocationRepository(db.cityDao(),db.shopDao(),app.getSharedPreferences("architecture",0));locations.select(1)
  products=ProductRepository(db.productDao(),db.productShopLinkDao(),db);shops=ShopRepository(db.shopDao(),db.shopDepartmentDao(),locations,db);basket=ShoppingListRepository(db.shoppingListEntryDao(),db,locations)
  a=db.shopDao().insert(ShopEntity(name="Командор",displayOrder=0));b=db.shopDao().insert(ShopEntity(name="Батон",displayOrder=1))
  first=db.shopBranchDao().insert(ShopBranchEntity(shopId=a,address="Дом",displayOrder=0));second=db.shopBranchDao().insert(ShopBranchEntity(shopId=a,address="ТЦ",displayOrder=1));Unit
 }
 @After fun close(){locations.closeForTest();db.close()}
 private suspend fun product(restricted:Boolean=false)=products.saveWithLinks(ProductEntity(name="Цукаты"),listOf(ProductShopLinkEntity(productId=0,shopId=a)),if(restricted)mapOf(a to setOf(second))else emptyMap())
 private fun jpeg(name:String):File {
  val file=File(app.filesDir,"branch_photos/$name.jpg");file.parentFile!!.mkdirs()
  val bitmap=Bitmap.createBitmap(32,16,Bitmap.Config.ARGB_8888);bitmap.eraseColor(android.graphics.Color.CYAN)
  file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.JPEG,85,it)};bitmap.recycle();return file
 }
 @Test fun unlinkCascadesOnlyOwnedAvailabilityAndBackupStillWorks()=runBlocking{
  val p=product(true);val purchase=basket.addProduct(p)
  products.toggleShopLink(p,a)
  assertTrue(db.shopBranchDao().availability(p).isEmpty());assertNotNull(db.shoppingListEntryDao().getById(purchase))
  val manager=BackupManager(app,db);assertTrue(manager.exportFile().isFile)
  products.toggleShopLink(p,a);assertFalse(db.productShopLinkDao().getLinksForProductSync(p).single().restrictBranches)
  assertTrue(db.shopBranchDao().availability(p).isEmpty());assertTrue(manager.exportFile().isFile)
 }
 @Test fun savingProductPreservesLinkIdentityAndPointOverrides()=runBlocking{
  val milk=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=a,name="Молочка"))
  val cheese=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=a,name="Сыры"))
  val p=products.saveWithLinks(ProductEntity(name="Сыр"),listOf(ProductShopLinkEntity(productId=0,shopId=a,departmentId=milk)),branchDepartments=mapOf(second to cheese))
  val link=db.productShopLinkDao().getLinksForProductSync(p).single()
  products.saveWithLinks(db.productDao().getById(p)!!.copy(note="Сохранено"),listOf(link))
  assertEquals(link.id,db.productShopLinkDao().getLinksForProductSync(p).single().id)
  val purchase=basket.addProduct(p,choice=ShopChoice.EXPLICIT,shopId=a,branchId=second)
  assertEquals(cheese,db.shoppingListEntryDao().getById(purchase)!!.assignedDepartmentId)
  basket.movePending(setOf(purchase),a,first)
  assertEquals(milk,db.shoppingListEntryDao().getById(purchase)!!.assignedDepartmentId)
 }
 @Test fun globalGroupRenameKeepsLocalIdsAndIndependentOrder()=runBlocking{
  val x=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=a,name="Молочка",displayOrder=7))
  val y=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=b,name=" молочка ",displayOrder=1))
  val left=db.shopDepartmentDao().getById(x)!!;val right=db.shopDepartmentDao().getById(y)!!
  assertEquals(left.typeId,right.typeId);assertEquals(1,db.shopDepartmentDao().types().size)
  shops.renameDepartmentGlobally(left.typeId,"Молочные продукты")
  assertEquals("Молочные продукты",db.shopDepartmentDao().getById(y)!!.name)
  assertEquals(7,db.shopDepartmentDao().getById(x)!!.displayOrder);assertEquals(1,db.shopDepartmentDao().getById(y)!!.displayOrder)
  assertEquals(x,db.shopDepartmentDao().getById(x)!!.id)
 }
 @Test fun onePhysicalPointBelongsToNetworkAndVenueAcrossCities()=runBlocking{
  val network=shops.ensureNetwork("Командор");val mall=shops.ensureVenue(1,"Планета")
  shops.saveWithDepartments(db.shopDao().getById(a)!!.copy(networkId=network),emptyList(),listOf(db.shopBranchDao().get(first)!!,db.shopBranchDao().get(second)!!.copy(venueId=mall)))
  val otherCity=db.shopDao().insert(ShopEntity(name="Командор",cityId=2,networkId=network))
  assertEquals(network,db.shopDao().getById(otherCity)!!.networkId)
  assertEquals(mall,db.shopBranchDao().get(second)!!.venueId);assertEquals(a,db.shopBranchDao().get(second)!!.shopId)
  assertEquals(2,db.shopBranchDao().forShop(a).size)
  try{db.shopBranchDao().insert(ShopBranchEntity(shopId=otherCity,address="Чужой ТЦ",venueId=mall));fail("Cross-city venue must fail")}catch(expected:android.database.sqlite.SQLiteConstraintException){}
 }
 @Test fun manualMarketConversionPreservesProductsPurchasesPhotosAndGlobalGroups()=runBlocking{
  val photo=jpeg("pavilion");db.shopBranchDao().update(db.shopBranchDao().get(second)!!.copy(note="Здесь сыр",photoUri=photo.path))
  val dept=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=a,name="Молочка"))
  val p=products.saveWithLinks(ProductEntity(name="Сыр",note="Тот же продукт"),listOf(ProductShopLinkEntity(productId=0,shopId=a,departmentId=dept)),mapOf(a to setOf(second)),branchDepartments=mapOf(second to dept))
  val pending=basket.addProduct(p);val bought=basket.addProduct(p,unit="шт");basket.markBought(bought)
  val before=db.shoppingListEntryDao().getById(pending)!!
  val venue=shops.convertToVenue(a)
  assertNull(db.shopDao().getById(a));assertEquals(1,db.productDao().getAllSync().size)
  val physical=db.shopBranchDao().get(second)!!;assertEquals(venue,physical.venueId);assertEquals(photo.path,physical.photoUri);assertEquals("Здесь сыр",physical.note)
  val after=db.shoppingListEntryDao().getById(pending)!!
  assertEquals(before.id,after.id);assertEquals(before.quantity,after.quantity,0.0);assertEquals(before.createdAt,after.createdAt);assertEquals(second,after.assignedBranchId);assertEquals(physical.shopId,after.assignedShopId)
  assertTrue(db.shoppingListEntryDao().getById(bought)!!.isBought)
  assertEquals(db.shopDepartmentDao().getById(after.assignedDepartmentId!!)!!.typeId,db.retailStructureDao().productDepartments().single().typeId)
  assertEquals(physical.shopId,db.productShopLinkDao().getLinksForProductSync(p).single().shopId)
  assertTrue(BackupManager(app,db).exportFile().isFile)
 }
 @Test fun invalidRawOwnersAreRejectedByDatabase()=runBlocking{
  val p=product(true);val wrong=db.shopBranchDao().insert(ShopBranchEntity(shopId=b,address="Другой магазин"))
  try{db.shopBranchDao().insertAvailabilityRow(ProductBranchAvailabilityEntity(p,wrong,a));fail("Wrong owner")}catch(expected:android.database.sqlite.SQLiteConstraintException){}
  val wrongDept=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=b,name="Чужая группа"))
  try{db.productShopLinkDao().update(db.productShopLinkDao().getLinksForProductSync(p).single().copy(departmentId=wrongDept));fail("Wrong department")}catch(expected:android.database.sqlite.SQLiteConstraintException){}
  try{db.shoppingListEntryDao().insert(ShoppingListEntryEntity(productId=p,assignedShopId=a,assignedBranchId=wrong));fail("Wrong purchase point")}catch(expected:android.database.sqlite.SQLiteConstraintException){}
  try{db.shopDao().insert(ShopEntity(name="Без города",cityId=999));fail("City FK")}catch(expected:android.database.sqlite.SQLiteConstraintException){}
 }
 @Test fun restoredAgedPointPhotoSurvivesStartupCleanupAndOrphansAreCollected()=runBlocking{
  val photo=jpeg("live");db.shopBranchDao().update(db.shopBranchDao().get(second)!!.copy(photoUri=photo.path))
  val manager=BackupManager(app,db);manager.importFile(manager.exportFile())
  val restored=File(db.shopBranchDao().get(second)!!.photoUri!!);restored.setLastModified(System.currentTimeMillis()-600_000)
  val orphan=jpeg("orphan");orphan.setLastModified(System.currentTimeMillis()-600_000)
  repeat(3){MediaLifecycle.cleanup(app,db)}
  assertTrue(restored.isFile);assertFalse(orphan.exists())
 }
 @Test fun archiveCompressionDoesNotHoldDbTransactionAndLeasesProtectItsPhotos()=runBlocking{
  val photo=jpeg("leased");photo.setLastModified(System.currentTimeMillis()-600_000);db.shopBranchDao().update(db.shopBranchDao().get(second)!!.copy(photoUri=photo.path))
  val entered=CountDownLatch(1);val release=CountDownLatch(1)
  val manager=BackupManager(app,db){file->object:FilterOutputStream(file.outputStream()){
   private var signalled=false
   override fun write(bytes:ByteArray,offset:Int,length:Int){if(!signalled){signalled=true;entered.countDown();check(release.await(15,TimeUnit.SECONDS))};out.write(bytes,offset,length)}
  }}
  val exporting=async(Dispatchers.IO){manager.exportFile()}
  try{
   assertTrue(withContext(Dispatchers.IO){entered.await(15,TimeUnit.SECONDS)})
   withTimeout(3000){withContext(Dispatchers.IO){db.shopBranchDao().update(db.shopBranchDao().get(second)!!.copy(photoUri=null))}}
   MediaLifecycle.cleanup(app,db);assertTrue("An exporting snapshot owns a lease",photo.isFile)
  }finally{release.countDown()}
  val file=exporting.await();ZipFile(file).use{assertNotNull(it.getEntry("branch_photos/$second.jpg"))}
  MediaLifecycle.cleanup(app,db);assertFalse("No owner or lease remains",photo.exists())
 }
 @Test fun fullBackupRoundTripsNetworkVenueLocalOrderAndProductOverrides()=runBlocking{
  val network=shops.ensureNetwork("Сеть");val venue=shops.ensureVenue(1,"Рынок")
  val d1=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=a,name="Молочка"));val d2=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=a,name="Сыры"))
  val types=db.shopDepartmentDao().getByShopIdSync(a).map{it.typeId}
  shops.saveWithDepartments(db.shopDao().getById(a)!!.copy(networkId=network),db.shopDepartmentDao().getByShopIdSync(a),listOf(db.shopBranchDao().get(first)!!,db.shopBranchDao().get(second)!!.copy(venueId=venue)),branchOrders=mapOf(second to types.reversed()))
  val p=products.saveWithLinks(ProductEntity(name="Сыр",preferCityShopOrder=true),listOf(ProductShopLinkEntity(productId=0,shopId=a,departmentId=d1)),branchDepartments=mapOf(second to d2))
  val manager=BackupManager(app,db);val archive=manager.exportFile();manager.importFile(archive)
  assertEquals(network,db.shopDao().getById(a)!!.networkId);assertEquals(venue,db.shopBranchDao().get(second)!!.venueId)
  assertEquals(types.reversed(),db.retailStructureDao().departmentOrder().filter{it.branchId==second}.sortedBy{it.displayOrder}.map{it.typeId})
  assertEquals(d2,db.shoppingListEntryDao().getById(basket.addProduct(p,choice=ShopChoice.EXPLICIT,shopId=a,branchId=second))!!.assignedDepartmentId)
  assertTrue(db.productDao().getById(p)!!.preferCityShopOrder)
 }
 @Test fun removingLocalGroupCollectsPointSettingsButKeepsTheGlobalIdentity()=runBlocking{
  val dept=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=a,name="Молочка"));val value=db.shopDepartmentDao().getById(dept)!!
  db.retailStructureDao().setDepartmentOrder(BranchDepartmentOrderEntity(second,value.typeId,0))
  val p=products.saveWithLinks(ProductEntity(name="Сыр"),listOf(ProductShopLinkEntity(productId=0,shopId=a,departmentId=dept)),branchDepartments=mapOf(second to dept))
  shops.saveWithDepartments(db.shopDao().getById(a)!!,emptyList())
  assertTrue(db.retailStructureDao().departmentOrder().isEmpty());assertTrue(db.retailStructureDao().productDepartments().isEmpty())
  assertNotNull(db.shopDepartmentDao().type(value.typeId));assertNull(db.productShopLinkDao().getLinksForProductSync(p).single().departmentId)
  assertTrue(BackupManager(app,db).exportFile().isFile)
 }
 @Test fun deletingRetailerDetachesHistoricalPurchaseAsOneConsistentAssignment()=runBlocking{
  val p=product();val entry=basket.addProduct(p)
  shops.deleteShop(db.shopDao().getById(a)!!)
  val after=db.shoppingListEntryDao().getById(entry)!!
  assertEquals(p,after.productId);assertNull(after.assignedShopId);assertNull(after.assignedBranchId);assertNull(after.assignedDepartmentId)
  assertTrue(BackupManager(app,db).exportFile().isFile)
 }
 @Test fun cityCorrectionDetachesFormerVenueAtomicallyAndRetainsPointIdentity()=runBlocking{
  val venue=shops.ensureVenue(1,"Планета")
  db.shopBranchDao().update(db.shopBranchDao().get(second)!!.copy(venueId=venue))
  shops.saveWithDepartments(db.shopDao().getById(a)!!.copy(cityId=2),emptyList(),db.shopBranchDao().forShop(a).map{it.copy(venueId=null)})
  assertEquals(2L,db.shopDao().getById(a)!!.cityId);assertEquals(a,db.shopBranchDao().get(second)!!.shopId);assertNull(db.shopBranchDao().get(second)!!.venueId)
  shops.deleteVenue(venue);assertNotNull(db.shopBranchDao().get(second))
 }
}
