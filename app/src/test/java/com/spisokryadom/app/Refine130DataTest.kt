package com.spisokryadom.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.backup.*
import com.spisokryadom.app.data.db.*
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.ui.shoppinglist.ViewMode
import com.spisokryadom.app.widget.*
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
class Refine130DataTest {
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 private val prefs get()=app.getSharedPreferences(WidgetSettings.PREFS,0)
 private lateinit var db:AppDatabase
 private lateinit var locations:LocationRepository
 private lateinit var products:ProductRepository
 private lateinit var basket:ShoppingListRepository
 private lateinit var widget:ShoppingWidgetData
 private var a=0L;private var b=0L;private var pavilion=0L;private var branch=0L;private var secondBranch=0L;private var dairy=0L;private var bakery=0L
 private var group=0L;private var recipient=0L;private var bread=0L
 private var purchases=emptySet<Long>()
 @Before fun setup()=runBlocking{
  prefs.edit().clear().commit()
  db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build();RetailConstraints.install(db.openHelper.writableDatabase)
  db.cityDao().insert(CityEntity(1,"Город А"));locations=LocationRepository(db.cityDao(),db.shopDao(),app.getSharedPreferences("130-location",0))
  products=ProductRepository(db.productDao(),db.productShopLinkDao(),db);basket=ShoppingListRepository(db.shoppingListEntryDao(),db,locations);widget=ShoppingWidgetData(db,basket,locations)
  a=db.shopDao().insert(ShopEntity(name="Красный Яр",displayOrder=1,shoppingBackground="rose"));b=db.shopDao().insert(ShopEntity(name="Батон",displayOrder=0))
  branch=db.shopBranchDao().insert(ShopBranchEntity(shopId=a,address="Адрес А",displayOrder=0));secondBranch=db.shopBranchDao().insert(ShopBranchEntity(shopId=a,address="Адрес Б",displayOrder=1))
  dairy=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=a,name="Молочка",displayOrder=0));bakery=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=a,name="Выпечка",displayOrder=1))
  db.retailStructureDao().setDepartmentOrder(BranchDepartmentOrderEntity(branch,db.shopDepartmentDao().getById(bakery)!!.typeId,0))
  db.retailStructureDao().setDepartmentOrder(BranchDepartmentOrderEntity(branch,db.shopDepartmentDao().getById(dairy)!!.typeId,1))
  val venue=db.retailStructureDao().insertVenue(VenueEntity(cityId=1,name="Рынок А",address="Адрес рынка"))
  pavilion=db.shopDao().insert(ShopEntity(name="29 павильон",displayOrder=2));val point=db.shopBranchDao().insert(ShopBranchEntity(shopId=pavilion,address="29 павильон",venueId=venue))
  group=db.productGroupDao().insert(ProductGroupEntity(name="Продукты"));recipient=db.recipientDao().insert(RecipientEntity(name="Получатель А"))
  suspend fun add(name:String,shop:Long?,point:Long?=null,dept:Long?=null,classify:Boolean=true):Long{
   val p=products.saveWithLinks(ProductEntity(name=name,defaultUnit="шт",productGroupId=if(classify)group else null,recipientId=if(classify)recipient else null),
    shop?.let{listOf(ProductShopLinkEntity(productId=0,shopId=it,departmentId=dept))}.orEmpty())
   return basket.addProduct(p,choice=if(shop==null)ShopChoice.NONE else ShopChoice.EXPLICIT,shopId=shop,branchId=point)
  }
  val values=mutableSetOf<Long>();values+=add("Молоко",a,branch,dairy);bread=add("Хлеб",a,branch,bakery);values+=bread
  values+=add("Йогурт",a,secondBranch,dairy);values+=add("Вода",b);values+=add("Сыр",pavilion,point);values+=add("Пакет",null,classify=false);purchases=values
 }
 @After fun close(){locations.closeForTest();db.close()}
 private fun header(items:List<WidgetItem>)=items.filterIsInstance<WidgetItem.Header>()
 private fun rows(items:List<WidgetItem>)=items.filterIsInstance<WidgetItem.Purchase>()
 private fun manifest(file:File)=ZipFile(file).use{z->JSONObject(z.getInputStream(z.getEntry("manifest.json")).reader().readText())}
 private fun archive(root:JSONObject)=File(app.cacheDir,"130-${java.util.UUID.randomUUID()}.zip").also{file->ZipOutputStream(file.outputStream()).use{it.putNextEntry(ZipEntry("manifest.json"));it.write(root.toString().toByteArray());it.closeEntry()}}
 @Test fun widgetSettingsAreIndependentPersistedAndDefensive(){
  prefs.edit().putString("shopping_list_view_mode","FLAT").commit();assertEquals(WidgetSettings(),WidgetSettings.load(prefs))
  WidgetSettings(ViewMode.BY_RECIPIENTS,false,12).save(prefs);assertEquals(WidgetSettings(ViewMode.BY_RECIPIENTS,false,12),WidgetSettings.load(app.getSharedPreferences(WidgetSettings.PREFS,0)))
  prefs.edit().putString(WidgetSettings.MODE,"unknown").putLong(WidgetSettings.TEXT_SIZE,Long.MAX_VALUE).commit()
  assertEquals(ViewMode.BY_SHOPS,WidgetSettings.load(prefs).mode);assertEquals(22,WidgetSettings.load(prefs).textSize)
  prefs.edit().putString(WidgetSettings.TEXT_SIZE,"wrong type").commit();assertEquals(16,WidgetSettings.load(prefs).textSize)
 }
 @Test fun shopsUseSeparatePhysicalBlocksAndTheirOwnDepartmentRoute()=runBlocking{
  val items=widget.presentation(WidgetSettings())
  assertEquals(purchases,rows(items).map{it.row.id}.toSet());assertEquals(purchases.size,rows(items).size)
  assertEquals(listOf("Адрес А","Адрес Б"),header(items).filter{it.title=="Красный Яр"}.map{it.subtitle})
  val first=items.indexOfFirst{it is WidgetItem.Header&&it.title=="Красный Яр"}
  assertEquals("Молочка",(items[first+1] as WidgetItem.Header).title);assertEquals("Молоко",(items[first+2] as WidgetItem.Purchase).row.title)
  assertEquals("Выпечка",(items[first+3] as WidgetItem.Header).title);assertEquals(bread,(items[first+4] as WidgetItem.Purchase).row.id)
  assertTrue(rows(items).all{it.row.detail=="1 шт"});assertTrue(items.map{it.stableId}.distinct().size==items.size)
  val venue=header(items).indexOfFirst{it.title=="Рынок А"};assertTrue(venue>=0);assertEquals("29 павильон",header(items)[venue+1].title);assertNull(header(items)[venue+1].subtitle)
 }
 @Test fun everyGroupingPreservesAllRowsAndMissingClassifiers()=runBlocking{
  for(mode in ViewMode.entries){
   val items=widget.presentation(WidgetSettings(mode,false,12));assertEquals(purchases,rows(items).map{it.row.id}.toSet());assertEquals(purchases.size,rows(items).size)
   if(mode==ViewMode.FLAT)assertTrue(header(items).isEmpty())
   if(mode==ViewMode.BY_GROUPS)assertEquals(listOf("Продукты","Без группы"),header(items).map{it.title})
   if(mode==ViewMode.BY_RECIPIENTS)assertEquals(listOf("Получатель А","Без получателя"),header(items).map{it.title})
   if(mode==ViewMode.BY_SHOPS)assertFalse(header(items).any{it.depth==2})
  }
 }
 @Test fun widgetCheckboxUsesSameFreshTokenAfterRegrouping()=runBlocking{
  val original=rows(widget.presentation(WidgetSettings(ViewMode.BY_GROUPS))).first{it.row.id==bread}.row
  assertTrue(widget.check(original));assertFalse(widget.check(original))
  val checked=rows(widget.presentation(WidgetSettings(ViewMode.BY_RECIPIENTS))).single{it.row.id==bread}.row;assertTrue(checked.bought);assertTrue(widget.check(checked))
  val before=widget.presentation(WidgetSettings());val after=widget.presentation(WidgetSettings(textSize=12));assertEquals(before.map{it.stableId},after.map{it.stableId})
 }
 @Test fun hidingDepartmentsDoesNotChangeTheShoppingRoute()=runBlocking{
  val shown=widget.presentation(WidgetSettings(showDepartments=true));val hidden=widget.presentation(WidgetSettings(showDepartments=false))
  assertEquals(rows(shown).map{it.row.id},rows(hidden).map{it.row.id});assertFalse(header(hidden).any{it.depth==2})
 }
 @Test fun urgencyRespectsManualOrderAndBoughtHistory()=runBlocking{
  basket.toggleUrgent(bread)
  fun first(items:List<WidgetItem>)=header(items).first().title
  assertEquals("Красный Яр",first(widget.presentation(WidgetSettings())))
  assertEquals("Батон",first(widget.presentation(WidgetSettings(),boostUrgentShops=false)))
  db.shopDao().update(db.shopDao().getById(a)!!.copy(manualOrderOverride=true));assertEquals("Батон",first(widget.presentation(WidgetSettings())))
  db.shopDao().update(db.shopDao().getById(a)!!.copy(manualOrderOverride=false));basket.markBought(bread);assertEquals("Батон",first(widget.presentation(WidgetSettings())))
 }
 @Test fun fullBackupRestoresShopBackgroundAndWidgetSettingsAndOlderCopiesDefault()=runBlocking{
  WidgetSettings(ViewMode.BY_RECIPIENTS,false,12).save(prefs)
  val manager=BackupManager(app,db);val file=manager.exportFile();val root=manifest(file);assertEquals(21,root.getInt("version"))
  db.shopDao().update(db.shopDao().getById(a)!!.copy(shoppingBackground=""));WidgetSettings().save(prefs);manager.importFile(file)
  assertEquals("rose",db.shopDao().getById(a)!!.shoppingBackground);assertEquals(WidgetSettings(ViewMode.BY_RECIPIENTS,false,12),WidgetSettings.load(prefs))
  root.put("version",9);val shops=root.getJSONObject("data").getJSONArray("shops");for(i in 0 until shops.length())shops.getJSONObject(i).remove("shoppingBackground")
  val oldPrefs=root.getJSONObject("preferences").getJSONObject(WidgetSettings.PREFS);listOf(WidgetSettings.MODE,WidgetSettings.DEPARTMENTS,WidgetSettings.TEXT_SIZE).forEach{oldPrefs.remove(it)}
  val old=archive(root);manager.importFile(old);assertEquals("",db.shopDao().getById(a)!!.shoppingBackground);assertEquals(WidgetSettings(),WidgetSettings.load(prefs));assertEquals(purchases,db.shoppingListEntryDao().getAllSync().map{it.id}.toSet())
  manager.discard(file);manager.discard(old)
 }
 @Test fun malformedNewPreferencesAndBackgroundRejectBeforeDatabaseChanges()=runBlocking{
  val manager=BackupManager(app,db);val base=manager.exportFile()
  val edits=listOf<(JSONObject)->Unit>(
   {it.getJSONObject("preferences").getJSONObject(WidgetSettings.PREFS).put(WidgetSettings.TEXT_SIZE,2)},
   {it.getJSONObject("preferences").getJSONObject(WidgetSettings.PREFS).put(WidgetSettings.MODE,"unknown")},
   {it.getJSONObject("preferences").getJSONObject(WidgetSettings.PREFS).put(WidgetSettings.DEPARTMENTS,"yes")},
   {it.getJSONObject("data").getJSONArray("shops").getJSONObject(0).put("shoppingBackground","#bad")})
  for(edit in edits){val root=manifest(base);edit(root);val bad=archive(root);assertTrue(runCatching{manager.importFile(bad)}.isFailure);assertEquals("rose",db.shopDao().getById(a)!!.shoppingBackground);assertEquals(purchases,db.shoppingListEntryDao().getAllSync().map{it.id}.toSet());manager.discard(bad)}
  manager.discard(base)
 }
 @Test fun schema13MigratesWithoutChangingPurchases()=runBlocking{
  val name="130-old-schema.db";app.deleteDatabase(name)
  app.openOrCreateDatabase(name,0,null).use{old->
   val entities=JSONObject(javaClass.getResourceAsStream("/schema-v13.json")!!.reader().readText()).getJSONObject("database").getJSONArray("entities")
   for(i in 0 until entities.length()){val entity=entities.getJSONObject(i);val table=entity.getString("tableName");old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",table));val indices=entity.getJSONArray("indices");for(j in 0 until indices.length())old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))}
   old.execSQL("INSERT INTO cities(id,name)VALUES(1,'Город А')");old.execSQL("INSERT INTO shops(id,name,displayOrder,manualOrderOverride,cityId)VALUES(7,'Старый магазин',3,1,1)")
   old.execSQL("INSERT INTO products(id,name,purchaseType,isTemporary,stableId)VALUES(8,'Товар','offline',0,'synthetic-stable')")
   old.execSQL("INSERT INTO shopping_list_entries(id,productId,quantity,unit,isBought,isUrgent,createdAt,updatedAt,assignedShopId,note)VALUES(9,8,3.5,'кг',0,1,10,20,7,'Сохранить')");old.version=13
  }
  val migrated=Room.databaseBuilder(app,AppDatabase::class.java,name).allowMainThreadQueries().addMigrations(AppDatabase.MIGRATION_13_14,AppDatabase.MIGRATION_14_15,AppDatabase.MIGRATION_15_16,AppDatabase.MIGRATION_16_17,AppDatabase.MIGRATION_17_18,AppDatabase.MIGRATION_18_19,AppDatabase.MIGRATION_19_20,AppDatabase.MIGRATION_20_21,AppDatabase.MIGRATION_21_22,AppDatabase.MIGRATION_22_23).build()
  try{assertEquals(23,migrated.openHelper.writableDatabase.version);assertEquals("",migrated.shopDao().getById(7)!!.shoppingBackground);assertTrue(migrated.shopDao().getById(7)!!.manualOrderOverride);val row=migrated.shoppingListEntryDao().getById(9)!!;assertEquals(7L,row.assignedShopId);assertEquals(3.5,row.quantity,0.0);assertEquals("Сохранить",row.note);assertEquals(20L,row.updatedAt);migrated.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}}
  finally{migrated.close();app.deleteDatabase(name)}
 }
}
