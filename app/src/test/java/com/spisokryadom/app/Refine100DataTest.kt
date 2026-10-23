package com.spisokryadom.app

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.*
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.data.backup.BackupManager
import com.spisokryadom.app.ui.productcard.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.*
import java.time.Duration
import java.util.zip.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class Refine100DataTest {
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 private lateinit var db:AppDatabase;private lateinit var products:ProductRepository;private lateinit var basket:ShoppingListRepository;private lateinit var locations:LocationRepository
 private lateinit var shops:ShopRepository;private lateinit var classifiers:ClassifierRepository
 private var a=0L;private var b=0L;private var p=0L;private var first=0L;private var second=0L;private var third=0L
 private val store=ViewModelStore()
 private suspend fun pump(condition:()->Boolean){val end=System.nanoTime()+20_000_000_000;while(!condition()&&System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20));yield();Thread.sleep(5)};assertTrue("Condition did not finish",condition())}
 @Before fun setup()=runBlocking{
  ProductEditorTransactions.generation.incrementAndGet();ProductDraftStore.preferences(app).edit().clear().commit()
  db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build();RetailConstraints.install(db.openHelper.writableDatabase)
  db.cityDao().insert(CityEntity(1,"Красноярск"));locations=LocationRepository(db.cityDao(),db.shopDao(),app.getSharedPreferences("100locations",0));products=ProductRepository(db.productDao(),db.productShopLinkDao(),db);basket=ShoppingListRepository(db.shoppingListEntryDao(),db,locations);shops=ShopRepository(db.shopDao(),db.shopDepartmentDao(),locations,db);classifiers=ClassifierRepository(db.productGroupDao(),db.recipientDao(),db.unitDao(),db)
  a=db.shopDao().insert(ShopEntity(name="Батон"));b=db.shopDao().insert(ShopEntity(name="Красный Яр",displayOrder=1));first=db.shopBranchDao().insert(ShopBranchEntity(shopId=b,address="Весны, 1"));second=db.shopBranchDao().insert(ShopBranchEntity(shopId=b,address="Молокова, 54",displayOrder=1));third=db.shopBranchDao().insert(ShopBranchEntity(shopId=b,address="Третья точка",displayOrder=2))
  p=products.saveWithLinks(ProductEntity(name="Спагетти",defaultUnit="упаковка",preferCityShopOrder=true),listOf(ProductShopLinkEntity(productId=0,shopId=a)));Unit
 }
 @After fun close()=runBlocking{store.clear();pump{!ProductEditorTransactions.mutex.isLocked};locations.closeForTest();db.close()}
 private suspend fun row(id:Long)=db.shoppingListEntryDao().getById(id)!!
 private suspend fun links()=db.productShopLinkDao().getLinksForProductSync(p)
 @Test fun addingDestinationAndMovingIsAtomicAndRetainsPurchaseFacts()=runBlocking{
  val id=basket.addProduct(p,quantity=3.0);val old=row(id);db.shoppingListEntryDao().update(old.copy(isUrgent=true,note="Не ломать",comment="Для дома"))
  assertTrue(basket.purchasePlaces(setOf(id)).none{it.shopId==b});assertTrue(basket.additionalPurchasePlaces(setOf(id)).any{it.branchId==second})
  basket.movePending(setOf(id),b,second,extendAvailability=true)
  val after=row(id);assertEquals(b,after.assignedShopId);assertEquals(second,after.assignedBranchId);assertEquals(3.0,after.quantity,0.0);assertEquals(old.createdAt,after.createdAt);assertEquals("Не ломать",after.note);assertTrue(after.isUrgent);assertEquals("Для дома",after.comment)
  assertEquals(1,db.productDao().getAllSync().size);assertEquals(setOf(a,b),links().map{it.shopId}.toSet());assertEquals(setOf(second),db.shopBranchDao().availability(p).map{it.branchId}.toSet());assertTrue(db.productDao().getById(p)!!.preferCityShopOrder)
 }
 @Test fun rememberingMovePromotesOnlyThisProductAndExactPoint()=runBlocking{
  products.ensureShopLink(p,b);val other=products.saveWithLinks(ProductEntity(name="Рис",preferCityShopOrder=true),listOf(ProductShopLinkEntity(productId=0,shopId=a),ProductShopLinkEntity(productId=0,shopId=b,priority=2)))
  val global=db.shopDao().getAllSync();val points=db.shopBranchDao().getAll();val id=basket.addProduct(p);basket.movePending(setOf(id),b,second,rememberPriority=true)
  assertFalse(db.productDao().getById(p)!!.preferCityShopOrder);assertEquals(b,links().minBy{it.priority}.shopId);assertEquals(second,basket.resolveBranch(p,b));assertEquals(first,basket.resolveBranch(other,b));assertEquals(global,db.shopDao().getAllSync());assertEquals(points,db.shopBranchDao().getAll());assertTrue(db.productDao().getById(other)!!.preferCityShopOrder)
  basket.removeEntry(id);val next=basket.addProduct(p);assertEquals(b,row(next).assignedShopId);assertEquals(second,row(next).assignedBranchId)
 }
 @Test fun disabledRememberingDoesNotChangeManualPreferences()=runBlocking{
  products.ensureShopLink(p,b);val old=db.productDao().getById(p);val oldLinks=links();val id=basket.addProduct(p);basket.movePending(setOf(id),b,second)
  assertEquals(old,db.productDao().getById(p));assertEquals(oldLinks,links());assertTrue(db.shopBranchDao().priorities().isEmpty());basket.removeEntry(id);assertEquals(a,row(basket.addProduct(p)).assignedShopId)
 }
 @Test fun restrictedDestinationGainsOnePointWithoutLosingExistingPoints()=runBlocking{
  products.saveWithLinks(db.productDao().getById(p)!!,links()+ProductShopLinkEntity(productId=p,shopId=b,priority=2),mapOf(b to setOf(first)))
  val id=basket.addProduct(p);assertTrue(basket.purchasePlaces(setOf(id)).none{it.branchId==second});basket.movePending(setOf(id),b,second,extendAvailability=true)
  assertEquals(setOf(first,second),db.shopBranchDao().availability(p).map{it.branchId}.toSet());assertEquals(second,row(id).assignedBranchId)
 }
 @Test fun invalidMoveRollsBackNewLinksAndPreferencesForWholeSelection()=runBlocking{
  db.cityDao().insert(CityEntity(2,"Томск"));val foreign=db.shopDao().insert(ShopEntity(name="Чужой город",cityId=2));val id=basket.addProduct(p);val old=row(id);val oldLinks=links()
  assertTrue(runCatching{basket.movePending(setOf(id),foreign,null,true,true)}.isFailure)
  assertTrue(runCatching{basket.movePending(setOf(id),a,second,true,true)}.isFailure)
  assertTrue(runCatching{basket.movePending(setOf(id,9999),b,second,true,true)}.isFailure)
  assertEquals(old,row(id));assertEquals(oldLinks,links());assertTrue(db.shopBranchDao().priorities().isEmpty())
 }
 @Test fun pointPriorityEditImmediatelyMovesPendingAndPreservesBoughtHistory()=runBlocking{
  products.ensureShopLink(p,b);val id=basket.addProduct(p,choice=ShopChoice.EXPLICIT,shopId=b,branchId=first);val bought=basket.addProduct(p,quantity=2.0,choice=ShopChoice.EXPLICIT,shopId=b,branchId=second);db.shoppingListEntryDao().update(row(bought).copy(isBought=true));val history=row(bought);val before=row(id)
  val dep=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=b,name="Бакалея"))
  products.saveWithLinks(db.productDao().getById(p)!!,links(),branchDepartments=mapOf(second to dep),branchPriorities=mapOf(b to listOf(second,first,third)))
  assertEquals(second,row(id).assignedBranchId);assertEquals(dep,row(id).assignedDepartmentId);assertEquals(before.quantity,row(id).quantity,0.0);assertEquals(before.createdAt,row(id).createdAt);assertEquals(history,row(bought));assertEquals(second,basket.resolveBranch(p,b))
  products.saveWithLinks(db.productDao().getById(p)!!.copy(note="Просто заметка"),links());assertEquals(second,row(id).assignedBranchId)
  products.saveWithLinks(db.productDao().getById(p)!!,links(),mapOf(b to setOf(third)))
  assertEquals(b,row(id).assignedShopId);assertEquals(third,row(id).assignedBranchId);assertEquals(history,row(bought))
 }
 @Test fun shopPriorityEditMovesPendingWhileOrdinaryEditsPreserveExplicitPlace()=runBlocking{
  products.ensureShopLink(p,b);val id=basket.addProduct(p,choice=ShopChoice.EXPLICIT,shopId=a)
  products.saveWithLinks(db.productDao().getById(p)!!.copy(preferCityShopOrder=false),links().map{it.copy(priority=if(it.shopId==b)1 else 2)})
  assertEquals(b,row(id).assignedShopId);basket.movePending(setOf(id),a,null)
  products.saveWithLinks(db.productDao().getById(p)!!.copy(name="Спагетти длинные"),links());assertEquals(a,row(id).assignedShopId)
 }
 @Test fun changedAvailabilityRepairsLegacyStaleUnlinkedPurchase()=runBlocking{
  products.saveWithLinks(db.productDao().getById(p)!!,listOf(ProductShopLinkEntity(productId=p,shopId=b)),mapOf(b to setOf(first)))
  val id=db.shoppingListEntryDao().insert(ShoppingListEntryEntity(productId=p,quantity=8.0,unit="упаковка",assignedShopId=a,note="Не потерять"))
  products.saveWithLinks(db.productDao().getById(p)!!,links(),mapOf(b to setOf(second)))
  assertEquals(b,row(id).assignedShopId);assertEquals(second,row(id).assignedBranchId);assertEquals("Не потерять",row(id).note);assertEquals(8.0,row(id).quantity,0.0)
 }
 @Test fun backupRoundTripKeepsPrioritiesAndSettingAndRejectsWrongOwner()=runBlocking{
  products.ensureShopLink(p,b);products.saveWithLinks(db.productDao().getById(p)!!,links(),branchPriorities=mapOf(b to listOf(second,third,first)))
  app.getSharedPreferences("shopping_list_prefs",0).edit().putBoolean("remember_shop_priority_on_move",true).commit();val manager=BackupManager(app,db);val file=manager.exportFile();manager.importFile(file)
  assertEquals(second,basket.resolveBranch(p,b));assertTrue(app.getSharedPreferences("shopping_list_prefs",0).getBoolean("remember_shop_priority_on_move",false))
  val root=ZipFile(file).use{z->JSONObject(z.getInputStream(z.getEntry("manifest.json")).reader().readText())};assertEquals(23,root.getInt("version"));root.getJSONObject("data").getJSONArray("productBranchPriorities").getJSONObject(0).put("shopId",a)
  val bad=File(app.cacheDir,"bad-priority.zip");ZipOutputStream(bad.outputStream()).use{z->z.putNextEntry(ZipEntry("manifest.json"));z.write(root.toString().toByteArray());z.closeEntry()};assertTrue(runCatching{manager.importFile(bad)}.isFailure);assertEquals(second,basket.resolveBranch(p,b));manager.discard(file);bad.delete();Unit
 }
 @Test fun priorityOwnershipAndDeletionHaveForeignKeyProtection()=runBlocking{
  products.ensureShopLink(p,b);assertTrue(runCatching{db.shopBranchDao().setPriority(ProductBranchPriorityEntity(p,first,a,0))}.isFailure)
  db.shopBranchDao().setPriority(ProductBranchPriorityEntity(p,second,b,0));db.productShopLinkDao().delete(links().single{it.shopId==b});assertTrue(db.shopBranchDao().priorities().isEmpty());db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
 }
 @Test fun editorDraftAndAutosaveRetainProductSpecificPointOrder()=runBlocking{
  products.ensureShopLink(p,b);val id=basket.addProduct(p,choice=ShopChoice.EXPLICIT,shopId=b,branchId=first)
  val vm=ProductCardViewModel(p,productRepository=products,shopRepository=shops,classifierRepository=classifiers,draftPreferences=ProductDraftStore.preferences(app));store.put("card",vm);pump{!vm.uiState.value.isLoading};vm.configureShop(b,vm.uiState.value.shopLinks.first{it.link.shopId==b}.link.departmentId,null,listOf(second,first,third));vm.updateNote("На месте")
  pump{runBlocking{row(id).assignedBranchId==second}};assertEquals("На месте",db.productDao().getById(p)!!.note)
  val raw=ProductDraftStore.preferences(app).getString("product_$p",null)!!;val draft=ProductEditDraft.decode(raw);assertEquals(listOf(second,first,third),draft.state.branchPriorities[b]);vm.finishEditing();pump{vm.uiState.value.isSaved};assertEquals(second,basket.resolveBranch(p,b))
 }
 @Test fun schema12MigratesWithoutChangingExistingRows()=runBlocking{
  val name="priority-schema12.db";app.deleteDatabase(name);app.openOrCreateDatabase(name,0,null).use{old->
   val entities=JSONObject(javaClass.classLoader!!.getResourceAsStream("schema-v12.json")!!.reader().readText()).getJSONObject("database").getJSONArray("entities")
   for(i in 0 until entities.length()){val e=entities.getJSONObject(i);val table=e.getString("tableName");old.execSQL(e.getString("createSql").replace("${'$'}{TABLE_NAME}",table));val idx=e.getJSONArray("indices");for(j in 0 until idx.length())old.execSQL(idx.getJSONObject(j).getString("createSql").replace("${'$'}{TABLE_NAME}",table))}
   old.execSQL("INSERT INTO cities(id,name)VALUES(1,'Красноярск')");old.execSQL("INSERT INTO products(id,name,purchaseType,isTemporary,stableId)VALUES(7,'Спагетти','offline',0,'same-spaghetti')");old.execSQL("INSERT INTO shopping_list_entries(id,productId,quantity,unit,isBought,isUrgent,createdAt,updatedAt,note)VALUES(8,7,3,'упаковка',0,1,10,20,'Сохранить')");old.version=12
  }
  val migrated=Room.databaseBuilder(app,AppDatabase::class.java,name).allowMainThreadQueries().addMigrations(AppDatabase.MIGRATION_12_13,AppDatabase.MIGRATION_13_14,AppDatabase.MIGRATION_14_15,AppDatabase.MIGRATION_15_16,AppDatabase.MIGRATION_16_17,AppDatabase.MIGRATION_17_18,AppDatabase.MIGRATION_18_19,AppDatabase.MIGRATION_19_20,AppDatabase.MIGRATION_20_21,AppDatabase.MIGRATION_21_22,AppDatabase.MIGRATION_22_23,AppDatabase.MIGRATION_23_24,AppDatabase.MIGRATION_24_25).build()
  try{assertEquals(25,migrated.openHelper.writableDatabase.version);assertEquals("same-spaghetti",migrated.productDao().getById(7)!!.stableId);val row=migrated.shoppingListEntryDao().getById(8)!!;assertEquals("Сохранить",row.note);assertEquals(3.0,row.quantity,0.0);assertTrue(row.isUrgent);assertEquals(10L,row.createdAt);assertEquals(20L,row.updatedAt);assertTrue(migrated.shopBranchDao().priorities().isEmpty());migrated.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}}
  finally{migrated.close();app.deleteDatabase(name)}
 }
}
