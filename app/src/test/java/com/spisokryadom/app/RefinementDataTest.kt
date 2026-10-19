package com.spisokryadom.app
import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.*
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.data.backup.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import org.json.JSONObject
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class RefinementDataTest {
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 private lateinit var db:AppDatabase;private lateinit var locations:LocationRepository;private lateinit var shops:ShopRepository;private lateinit var products:ProductRepository;private lateinit var basket:ShoppingListRepository;private lateinit var classifiers:ClassifierRepository
 private var a=0L;private var b=0L;private var point=0L
 @Before fun setup()=runBlocking{
  db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build();RetailConstraints.install(db.openHelper.writableDatabase)
  db.cityDao().insert(CityEntity(1,"Красноярск"));locations=LocationRepository(db.cityDao(),db.shopDao(),app.getSharedPreferences("refinement",0));locations.select(1)
  shops=ShopRepository(db.shopDao(),db.shopDepartmentDao(),locations,db);products=ProductRepository(db.productDao(),db.productShopLinkDao(),db);basket=ShoppingListRepository(db.shoppingListEntryDao(),db,locations)
  classifiers=ClassifierRepository(db.productGroupDao(),db.recipientDao(),db.unitDao(),db)
  a=db.shopDao().insert(ShopEntity(name="Батон"));b=db.shopDao().insert(ShopEntity(name="Красный Яр",displayOrder=1));point=db.shopBranchDao().insert(ShopBranchEntity(shopId=a,address="Рядом"));Unit
 }
 @After fun close(){locations.closeForTest();db.close()}
 @Test fun creatingGlobalDepartmentNeverAttachesItToAnyShop()=runBlocking{
  shops.createDepartmentType("Гастроном (готовая еда)");shops.addDepartmentInCity("Заморозка",1)
  assertEquals(2,db.shopDepartmentDao().types().size);assertTrue(db.shopDepartmentDao().getAllSync().isEmpty())
 }
 @Test fun selectingGlobalTypeOnlyCreatesOneLocalPlacementAndReusesIdentity()=runBlocking{
  val type=shops.createDepartmentType("Молочка")
  shops.renameDepartmentGlobally(type,"Молочные продукты")
  val p=products.saveWithLinks(ProductEntity(name="Сыр"),listOf(ProductShopLinkEntity(productId=0,shopId=a,departmentId=-1)),departments=listOf(ShopDepartmentEntity(-1,a,"Молочка",0,typeId=type)))
  assertEquals(type,db.shopDepartmentDao().getByShopIdSync(a).single().typeId);assertTrue(db.shopDepartmentDao().getByShopIdSync(b).isEmpty());assertEquals(1,db.shopDepartmentDao().types().size);assertNotNull(db.productShopLinkDao().getLinksForProductSync(p).single().departmentId)
  assertEquals("Молочные продукты",db.shopDepartmentDao().getByShopIdSync(a).single().name)
 }
 @Test fun globalDepartmentOrderAndStylesDoNotOverwriteLocalRoutes()=runBlocking{
  val z=shops.createDepartmentType("Ягоды");val m=shops.createDepartmentType("Молочка");val d=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=a,name="Молочка",displayOrder=8))
  shops.styleDepartmentType(m,"green","milk");shops.moveDepartmentType(m,-1);assertEquals(m,db.shopDepartmentDao().types().first().id)
  shops.alphabetizeDepartmentTypes();assertEquals(listOf("Молочка","Ягоды"),db.shopDepartmentDao().types().map{it.name});assertEquals(8,db.shopDepartmentDao().getById(d)!!.displayOrder)
  val type=db.shopDepartmentDao().type(m)!!;assertEquals("green",type.accent);assertEquals("milk",type.iconKey);assertEquals(z,db.shopDepartmentDao().types().last().id)
 }
 @Test fun alphabeticalResetPreservesClassifierIdsRelationsAndAppearance()=runBlocking{
  val z=db.productGroupDao().insert(ProductGroupEntity(name="Ягоды",accent="yellow",iconKey="vegetables"));val a=db.productGroupDao().insert(ProductGroupEntity(name="Аптека",displayOrder=1));val p=db.productDao().insert(ProductEntity(name="Линзы",productGroupId=z))
  classifiers.alphabetize("group");assertEquals(listOf(a,z),db.productGroupDao().getAllSync().map{it.id});assertEquals(z,db.productDao().getById(p)!!.productGroupId);assertEquals("yellow",db.productGroupDao().getById(z)!!.accent)
  val rz=db.recipientDao().insert(RecipientEntity(name="Я"));val ra=db.recipientDao().insert(RecipientEntity(name="Анна",displayOrder=1));classifiers.alphabetize("recipient");assertEquals(listOf(ra,rz),db.recipientDao().getAllSync().map{it.id})
  val uz=db.unitDao().insert(UnitEntity(name="шт"));val ua=db.unitDao().insert(UnitEntity(name="кг",displayOrder=1));classifiers.alphabetize("unit");assertEquals(listOf(ua,uz),db.unitDao().getAllSync().map{it.id})
 }
 @Test fun cardChangesImmediatelyRefreshPendingDepartmentAndInheritedUnitOnly()=runBlocking{
  val dept=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=a,name="Овощи/фрукты"))
  val p=products.saveWithLinks(ProductEntity(name="Банан"),listOf(ProductShopLinkEntity(productId=0,shopId=a)))
  val pending=basket.addProduct(p,8.0);val custom=basket.addProduct(p,1.5,"кг");val bought=basket.addProduct(p,1.0,"пакет");basket.markBought(bought)
  val old=db.shoppingListEntryDao().getById(pending)!!;val historical=db.shoppingListEntryDao().getById(bought)!!
  db.shoppingListEntryDao().update(old.copy(isUrgent=true,note="Зелёные",comment="Для дома"))
  products.saveWithLinks(db.productDao().getById(p)!!.copy(defaultUnit="шт",isFavorite=true,accent="green"),listOf(db.productShopLinkDao().getLinksForProductSync(p).single().copy(departmentId=dept)))
  val actual=db.shoppingListEntryDao().getById(pending)!!;assertEquals(dept,actual.assignedDepartmentId);assertEquals("шт",actual.unit);assertEquals(8.0,actual.quantity,0.0);assertEquals(point,actual.assignedBranchId);assertTrue(actual.isUrgent);assertEquals("Зелёные",actual.note);assertEquals("Для дома",actual.comment);assertEquals(old.createdAt,actual.createdAt)
  assertEquals("кг",db.shoppingListEntryDao().getById(custom)!!.unit);assertEquals(dept,db.shoppingListEntryDao().getById(custom)!!.assignedDepartmentId);assertEquals(historical,db.shoppingListEntryDao().getById(bought))
 }
 @Test fun pendingPointSpecificDepartmentChangesImmediatelyAndExplicitNoShopStaysUnassigned()=runBlocking{
  val milk=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=a,name="Молочка"));val cheese=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=a,name="Сыры"))
  val p=products.saveWithLinks(ProductEntity(name="Сыр"),listOf(ProductShopLinkEntity(productId=0,shopId=a,departmentId=milk)))
  val row=basket.addProduct(p);val none=basket.addProduct(p,choice=ShopChoice.NONE)
  products.saveWithLinks(db.productDao().getById(p)!!,db.productShopLinkDao().getLinksForProductSync(p),branchDepartments=mapOf(point to cheese))
  assertEquals(cheese,db.shoppingListEntryDao().getById(row)!!.assignedDepartmentId);assertNull(db.shoppingListEntryDao().getById(none)!!.assignedShopId);assertNull(db.shoppingListEntryDao().getById(none)!!.assignedDepartmentId)
 }
 @Test fun removingCurrentStoreLinkRefreshesPendingButKeepsBoughtHistory()=runBlocking{
  val p=products.saveWithLinks(ProductEntity(name="Чай"),listOf(ProductShopLinkEntity(productId=0,shopId=a),ProductShopLinkEntity(productId=0,shopId=b,priority=2)))
  val row=basket.addProduct(p);val bought=basket.addProduct(p,unit="пакет");basket.markBought(bought);val historical=db.shoppingListEntryDao().getById(bought)!!
  products.saveWithLinks(db.productDao().getById(p)!!,db.productShopLinkDao().getLinksForProductSync(p).filter{it.shopId==b})
  assertEquals(b,db.shoppingListEntryDao().getById(row)!!.assignedShopId);assertEquals(historical,db.shoppingListEntryDao().getById(bought))
 }
 @Test fun favoriteAndColorCommandsPreservePurchaseAndProductIdentity()=runBlocking{
  val p=products.saveWithLinks(ProductEntity(name="Линзы"),emptyList());val row=basket.addProduct(p);val old=db.productDao().getById(p)!!;val purchase=db.shoppingListEntryDao().getById(row)!!
  products.setFavorite(p,true);products.setAccent(p,"yellow");val actual=db.productDao().getById(p)!!
  assertEquals(old.copy(isFavorite=true,accent="yellow"),actual);assertEquals(purchase,db.shoppingListEntryDao().getById(row))
  products.saveWithLinks(actual.copy(accent="blue"),emptyList());assertEquals(purchase,db.shoppingListEntryDao().getById(row))
 }
 @Test fun newStoreIsInCurrentCityAndDoesNotInventProductLinks()=runBlocking{
  val id=shops.createInCurrentCity("Зоомагазин");assertEquals(1L,db.shopDao().getById(id)!!.cityId);assertEquals(id,shops.createInCurrentCity(" зоомагазин "));assertTrue(db.productShopLinkDao().getAllSync().isEmpty());assertTrue(db.shopDepartmentDao().getByShopIdSync(id).isEmpty())
 }
 @Test fun completeBackupRoundTripPreservesAllMarksAndOrders()=runBlocking{
  val g=db.productGroupDao().insert(ProductGroupEntity(name="Продукты",displayOrder=3,accent="green",iconKey="groceries"));val rec=db.recipientDao().insert(RecipientEntity(name="Рома",accent="blue",iconKey="person"));val t=shops.createDepartmentType("Молочка");shops.styleDepartmentType(t,"yellow","milk")
  val p=products.saveWithLinks(ProductEntity(name="Йогурт",productGroupId=g,recipientId=rec,isFavorite=true,accent="violet"),emptyList());val before=db.productDao().getById(p)!!;val types=db.shopDepartmentDao().types()
  val manager=BackupManager(app,db);val file=manager.exportFile();assertEquals(12,java.util.zip.ZipFile(file).use{zip->JSONObject(zip.getInputStream(zip.getEntry("manifest.json")).reader().readText()).getInt("version")})
  products.setFavorite(p,false);shops.moveDepartmentType(t,1);manager.importFile(file)
  assertEquals(before,db.productDao().getById(p));assertEquals(types,db.shopDepartmentDao().types());assertEquals("groceries",db.productGroupDao().getById(g)!!.iconKey);assertEquals("blue",db.recipientDao().getById(rec)!!.accent);manager.discard(file)
 }
 @Test fun newAddressAndExplicitNoAddressCanBeSelectedDuringTextEntry()=runBlocking{
  val p=products.saveWithLinks(ProductEntity(name="Сыр"),listOf(ProductShopLinkEntity(productId=0,shopId=a)),mapOf(a to setOf(point)))
  val created=shops.createPoint(a,"Новый адрес",p);assertTrue(db.shopBranchDao().availability(p).any{it.branchId==created})
  val purchase=basket.addProduct(p,choice=ShopChoice.EXPLICIT,shopId=a,branchId=created);assertEquals(created,db.shoppingListEntryDao().getById(purchase)!!.assignedBranchId)
  val noAddress=basket.addProduct(p,choice=ShopChoice.EXPLICIT,shopId=a,branchId=0);assertNull(db.shoppingListEntryDao().getById(noAddress)!!.assignedBranchId);assertEquals(a,db.shoppingListEntryDao().getById(noAddress)!!.assignedShopId)
 }
 @Test fun unitRenameUpdatesPendingRowsButPreservesBoughtHistory()=runBlocking{
  val unit=UnitEntity(id=db.unitDao().insert(UnitEntity(name="уп")),name="уп");val p=products.saveWithLinks(ProductEntity(name="Чай",defaultUnit="уп"),emptyList())
  val pending=basket.addProduct(p);val bought=basket.addProduct(p,unit="коробка");basket.markBought(bought);val historical=db.shoppingListEntryDao().getById(bought)!!
  classifiers.renameUnitAndProducts(unit,"упаковка");assertEquals("упаковка",db.productDao().getById(p)!!.defaultUnit);assertEquals("упаковка",db.shoppingListEntryDao().getById(pending)!!.unit);assertEquals(historical,db.shoppingListEntryDao().getById(bought))
 }
 @Test fun configuringAnUnresolvedPurchaseAssignsItsOriginalCityButExplicitNoShopRemains()=runBlocking{
  val p=products.saveWithLinks(ProductEntity(name="Банан"),emptyList());val unresolved=basket.addProduct(p,8.0);val explicitNone=basket.addProduct(p,2.0,choice=ShopChoice.NONE)
  assertNotEquals(unresolved,explicitNone);assertTrue(db.shoppingListEntryDao().getById(explicitNone)!!.explicitNoShop);assertFalse(db.shoppingListEntryDao().getById(unresolved)!!.explicitNoShop);assertEquals(1L,db.shoppingListEntryDao().getById(unresolved)!!.awaitingShopCityId);assertNull(db.shoppingListEntryDao().getById(explicitNone)!!.awaitingShopCityId)
  val beforeConfigure=db.shoppingListEntryDao().getById(unresolved)!!;assertEquals(beforeConfigure,BackupJson.entries(BackupJson.encode(beforeConfigure)))
  val p2=products.saveWithLinks(ProductEntity(name="Салат"),emptyList());val reviewNone=basket.addProduct(p2)
  val snapshot=basket.textSnapshot();basket.replacePendingText(snapshot.entries,snapshot.entries.map{row->ShoppingListRepository.PurchaseRequest(row.productId,if(row.productId==p)"Банан"else "Салат",row.quantity,row.unit,choice=ShopChoice.NONE,entryId=row.id,assignmentExplicit=row.id==reviewNone)})
  assertTrue(db.shoppingListEntryDao().getById(reviewNone)!!.explicitNoShop);assertNull(db.shoppingListEntryDao().getById(reviewNone)!!.awaitingShopCityId)
  assertEquals(1L,db.shoppingListEntryDao().getById(unresolved)!!.awaitingShopCityId)
  products.saveWithLinks(db.productDao().getById(p2)!!,listOf(ProductShopLinkEntity(productId=p2,shopId=a)));assertNull(db.shoppingListEntryDao().getById(reviewNone)!!.assignedShopId)

  db.cityDao().insert(CityEntity(2,"Томск"));val foreign=db.shopDao().insert(ShopEntity(name="Другой город",cityId=2));locations.select(2)
  products.saveWithLinks(db.productDao().getById(p)!!.copy(defaultUnit="шт"),listOf(ProductShopLinkEntity(productId=p,shopId=foreign,priority=1),ProductShopLinkEntity(productId=p,shopId=a,priority=2)))
  val actual=db.shoppingListEntryDao().getById(unresolved)!!;assertEquals(a,actual.assignedShopId);assertEquals(8.0,actual.quantity,0.0);assertEquals("шт",actual.unit);assertNull(actual.awaitingShopCityId)
  assertNull(db.shoppingListEntryDao().getById(explicitNone)!!.assignedShopId)
  val before=db.shoppingListEntryDao().getById(explicitNone)!!;val manager=BackupManager(app,db);val file=manager.exportFile();manager.importFile(file);assertEquals(before,db.shoppingListEntryDao().getById(explicitNone));manager.discard(file)
 }
 @Test fun oldJsonDefaultsToNoMarksAndRetainsOldGroupOrder(){
  val p=BackupJson.products(JSONObject("{name:'Чай',id:7}"));assertFalse(p.isFavorite);assertEquals("",p.accent)
  val g=BackupJson.groups(JSONObject("{name:'Продукты',id:2,displayOrder:5}"));assertEquals(5,g.displayOrder);assertEquals("",g.iconKey)
 }
 @Test fun schema11UpgradesWithoutLosingNamesIdentitiesOrRelations()=runBlocking{
  val name="refinement-schema11.db";app.deleteDatabase(name);app.openOrCreateDatabase(name,0,null).use { old->
   val entities=JSONObject(javaClass.classLoader!!.getResourceAsStream("schema-v11.json")!!.reader().readText()).getJSONObject("database").getJSONArray("entities")
   for(i in 0 until entities.length()){val e=entities.getJSONObject(i);val table=e.getString("tableName");old.execSQL(e.getString("createSql").replace("${'$'}{TABLE_NAME}",table));val indices=e.getJSONArray("indices");for(j in 0 until indices.length())old.execSQL(indices.getJSONObject(j).getString("createSql").replace("${'$'}{TABLE_NAME}",table))}
   old.execSQL("INSERT INTO department_types(id,name,normalizedName) VALUES(8,'Ягоды','ягоды'),(9,'Молочка','молочка')")
   old.execSQL("INSERT INTO cities(id,name) VALUES(1,'Красноярск')");old.execSQL("INSERT INTO shops(id,name,displayOrder,manualOrderOverride,cityId) VALUES(1,'Батон',0,0,1)");old.execSQL("INSERT INTO shop_departments(id,shopId,typeId,displayOrder) VALUES(5,1,9,0)")
   old.execSQL("INSERT INTO products(id,name,purchaseType,isTemporary,stableId,defaultUnit) VALUES(7,'Банан','offline',0,'same-banana','шт')")
   old.execSQL("INSERT INTO product_shop_links(id,productId,shopId,departmentId,priority,restrictBranches) VALUES(1,7,1,5,1,0)")
   old.execSQL("INSERT INTO shopping_list_entries(id,productId,quantity,unit,assignedShopId,isBought,isUrgent,createdAt,updatedAt) VALUES(8,7,8,'',1,0,0,10,20),(9,7,3,'',1,1,0,11,21)");old.version=11
  }
  val migrated=Room.databaseBuilder(app,AppDatabase::class.java,name).allowMainThreadQueries().addMigrations(AppDatabase.MIGRATION_11_12,AppDatabase.MIGRATION_12_13,AppDatabase.MIGRATION_13_14,AppDatabase.MIGRATION_14_15,AppDatabase.MIGRATION_15_16).build()
  try{assertEquals(16,migrated.openHelper.writableDatabase.version);assertEquals(listOf(9L,8L),migrated.shopDepartmentDao().types().take(2).map{it.id});assertEquals(31,migrated.shopDepartmentDao().types().size);assertEquals("Ягоды",migrated.shopDepartmentDao().type(8)!!.name);assertEquals(1,migrated.shopDepartmentDao().getAllSync().size);assertEquals("same-banana",migrated.productDao().getById(7)!!.stableId);assertFalse(migrated.productDao().getById(7)!!.isFavorite);assertEquals("шт",migrated.shoppingListEntryDao().getById(8)!!.unit);assertEquals(5L,migrated.shoppingListEntryDao().getById(8)!!.assignedDepartmentId);assertEquals("",migrated.shoppingListEntryDao().getById(9)!!.unit);assertNull(migrated.shoppingListEntryDao().getById(9)!!.assignedDepartmentId);migrated.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}}
  finally{migrated.close();app.deleteDatabase(name)}
 }
}
