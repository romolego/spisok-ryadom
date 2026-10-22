package com.spisokryadom.app

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.ui.shopcard.ShopCardViewModel
import com.spisokryadom.app.ui.shoplist.ShopListViewModel
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class UnifiedLists250DataTest {
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 private lateinit var db:AppDatabase
 private lateinit var locations:LocationRepository
 private lateinit var shops:ShopRepository
 private val models=ViewModelStore()
 private val prefs get()=app.getSharedPreferences("250-location",0)
 @Before fun setup()=runBlocking{
  prefs.edit().clear().commit()
  db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build()
  db.cityDao().insert(CityEntity(1,"Красноярск"))
  locations=LocationRepository(db.cityDao(),db.shopDao(),prefs)
  shops=ShopRepository(db.shopDao(),db.shopDepartmentDao(),locations,db)
 }
 @After fun close(){models.clear();Shadows.shadowOf(Looper.getMainLooper()).idle();locations.closeForTest();db.close()}
 private fun await(check:()->Boolean){val end=System.nanoTime()+15_000_000_000;while(!check()&&System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5)};assertTrue(check())}
 @Test fun sharedAddMarkersBelongToTheirDestinationAndReloadFromRoom(){
  val ids=runBlocking{val goals=GoalListRepository(db,ShoppingListRepository(db.shoppingListEntryDao(),db,locations));val a=goals.create("Рецепт");val b=goals.create("Другой");val p=db.productDao().insert(ProductEntity(name="Банан",isFavorite=true,defaultQuantity=.5,defaultUnit="кг"));goals.addCatalogue(b,p,3.0);ShoppingListRepository(db.shoppingListEntryDao(),db,locations).addProduct(p,8.0);Triple(a,b,p)}
  val basket=ShoppingListRepository(db.shoppingListEntryDao(),db,locations)
  fun model()=com.spisokryadom.app.ui.addtolist.AddToListViewModel(ProductRepository(db.productDao(),db.productShopLinkDao(),db),shops,basket,prefs,targetListId=ids.first)
  val vm=model();models.put("add",vm);await{vm.uiState.value.searchResults.isNotEmpty()&&vm.uiState.value.targetListName=="Рецепт"}
  assertTrue(vm.uiState.value.pendingProductLabels.isEmpty());vm.quickAdd(vm.uiState.value.searchResults.single())
  await{vm.uiState.value.pendingProductLabels[ids.third]=="0,5 кг"};assertEquals("Банан",vm.uiState.value.addedItems.single().name)
  vm.quickAdd(vm.uiState.value.searchResults.single());await{vm.uiState.value.pendingProductLabels[ids.third]=="1 кг"}
  val reopened=model();models.put("again",reopened);await{reopened.uiState.value.pendingProductLabels[ids.third]=="1 кг"}
  runBlocking{assertEquals(3.0,db.shoppingListEntryDao().getForListSync(ids.second).single().quantity,0.0);assertEquals(8.0,db.shoppingListEntryDao().getForListSync(null).single().quantity,0.0)}
 }
 @Test fun newQuickItemStaysInItsListAndDeletedDestinationCannotReceiveAnything(){
  val list=runBlocking{GoalListRepository(db,ShoppingListRepository(db.shoppingListEntryDao(),db,locations)).create("Детали")}
  val vm=com.spisokryadom.app.ui.addtolist.AddToListViewModel(ProductRepository(db.productDao(),db.productShopLinkDao(),db),shops,ShoppingListRepository(db.shoppingListEntryDao(),db,locations),prefs,targetListId=list)
  models.put("add",vm);vm.onSearchQueryChanged("Видеокарта");await{vm.uiState.value.isNewProduct&&!vm.uiState.value.isSearching}
  vm.onSaveToDatabaseChanged(false);vm.quantity("2");vm.unit("шт");vm.add();await{vm.uiState.value.addedItems.size==1}
  runBlocking{val row=db.shoppingListEntryDao().getForListSync(list).single();assertEquals(2.0,row.quantity,0.0);assertTrue(db.productDao().getById(row.productId)!!.isTemporary);assertTrue(db.shoppingListEntryDao().getVisibleSync().isEmpty());db.goalListDao().deleteById(list)}
  await{!vm.uiState.value.targetAvailable};vm.add();Shadows.shadowOf(Looper.getMainLooper()).idle();runBlocking{assertTrue(db.shoppingListEntryDao().getAllSync().isEmpty())}
 }
 @Test fun oldTemplateFileImportsAsOneReusableListWithPricesAndPlaces():Unit=runBlocking{
  val goals=GoalListRepository(db,ShoppingListRepository(db.shoppingListEntryDao(),db,locations))
  val legacy=com.spisokryadom.app.domain.GoalListDocument("Рецепт",listOf(com.spisokryadom.app.domain.GoalListItemDraft("Грибы",.3,"кг",120.0,shopName="Рынок")),isTemplate=true)
  val parsed=com.spisokryadom.app.domain.GoalListTransfer.parse(com.spisokryadom.app.domain.GoalListTransfer.exportText(legacy))
  val id=goals.importDocument(parsed.document!!);assertFalse(goals.getList(id)!!.isTemplate)
  val before=goals.snapshot(id);goals.copyList(id);assertEquals(before,goals.snapshot(id));val copy=db.shoppingListEntryDao().getVisibleSync().single();assertEquals(.3,copy.quantity,0.0);assertEquals(120.0,copy.plannedUnitPrice!!,0.0);assertEquals("Рынок",copy.customShopName)
 }
 @Test fun schema22UpgradeKeepsBothListKindsAndEveryRowIdentity():Unit=runBlocking{
  val name="unified250-upgrade.db";app.deleteDatabase(name)
  val schema=java.io.File("schemas/com.spisokryadom.app.data.db.AppDatabase/22.json").takeIf{it.isFile}?:java.io.File("app/schemas/com.spisokryadom.app.data.db.AppDatabase/22.json")
  app.openOrCreateDatabase(name,0,null).use{old->val entities=org.json.JSONObject(schema.readText()).getJSONObject("database").getJSONArray("entities");for(i in 0 until entities.length()){val e=entities.getJSONObject(i);old.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName")));val indices=e.getJSONArray("indices");for(j in 0 until indices.length())old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName")))}
   old.execSQL("INSERT INTO goal_lists(id,stableId,name,showInPurchases,isTemplate,createdAt,updatedAt)VALUES(7,'list-a','Рецепт',0,1,1,2),(8,'list-b','Покупки',1,0,1,3)")
   old.execSQL("INSERT INTO products(id,name,stableId,purchaseType,isTemporary)VALUES(9,'Сыр','product-a','offline',0)")
   old.execSQL("INSERT INTO shopping_list_entries(id,productId,listId,quantity,unit,isBought,isUrgent,createdAt,updatedAt)VALUES(10,9,7,2,'кг',0,1,1,2),(11,9,8,3,'кг',1,0,1,2)");old.version=22
  }
  val migrated=Room.databaseBuilder(app,AppDatabase::class.java,name).allowMainThreadQueries().addMigrations(AppDatabase.MIGRATION_22_23).build()
  try{assertEquals(23,migrated.openHelper.writableDatabase.version);assertEquals(listOf(7L,8L),migrated.goalListDao().getAllSync().sortedBy{it.id}.map{it.id});assertTrue(migrated.goalListDao().getAllSync().none{it.isTemplate});assertEquals(2.0,migrated.shoppingListEntryDao().getById(10)!!.quantity,0.0);assertTrue(migrated.shoppingListEntryDao().getById(11)!!.isBought);migrated.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}}finally{migrated.close();app.deleteDatabase(name)}
 }
 @Test fun fullLegacyBackupConvertsOnlyTheOldTypeAndKeepsIdentityAndPurchases():Unit=runBlocking{
  val goals=GoalListRepository(db,ShoppingListRepository(db.shoppingListEntryDao(),db,locations));val id=goals.create("Прежний шаблон");goals.addQuick(id,"Сыр",2.0,"кг",100.0)
  val before=goals.snapshot(id);val manager=com.spisokryadom.app.data.backup.BackupManager(app,db);val exported=manager.exportFile()
  val json=java.util.zip.ZipFile(exported).use{z->org.json.JSONObject(z.getInputStream(z.getEntry("manifest.json")).reader().readText())};json.getJSONObject("data").getJSONArray("goalLists").getJSONObject(0).put("isTemplate",true)
  fun archive():java.io.File=java.io.File(app.cacheDir,"legacy-list250.zip").also{f->java.util.zip.ZipOutputStream(f.outputStream()).use{z->z.putNextEntry(java.util.zip.ZipEntry("manifest.json"));z.write(json.toString().toByteArray());z.closeEntry()}}
  assertTrue(runCatching{manager.importFile(archive())}.isFailure);assertEquals(before,goals.snapshot(id))
  json.put("version",19);manager.importFile(archive());assertEquals(before,goals.snapshot(id));manager.discard(exported)
 }

}
