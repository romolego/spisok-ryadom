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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancel
import kotlinx.coroutines.yield
import androidx.lifecycle.viewModelScope
import com.spisokryadom.app.ui.goallists.ListCopyViewModel
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
class Templates240DataTest {
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 private lateinit var db:AppDatabase
 private lateinit var location:LocationRepository
 private lateinit var shopping:ShoppingListRepository
 private lateinit var goals:GoalListRepository
 @Before fun setup()=runBlocking{
  db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build()
  RetailConstraints.install(db.openHelper.writableDatabase);db.cityDao().insert(CityEntity(1,"Город"))
  location=LocationRepository(db.cityDao(),db.shopDao(),app.getSharedPreferences("240-location",0))
  shopping=ShoppingListRepository(db.shoppingListEntryDao(),db,location);goals=GoalListRepository(db,shopping)
 }
 @After fun close(){location.closeForTest();db.close()}
 private suspend fun template():Long=goals.create("На неделю")
 private fun root(file:File)=ZipFile(file).use{JSONObject(it.getInputStream(it.getEntry("manifest.json")).reader().readText())}
 private fun archive(root:JSONObject)=File.createTempFile("240-",".zip",app.cacheDir).also{f->ZipOutputStream(f.outputStream()).use{it.putNextEntry(ZipEntry("manifest.json"));it.write(root.toString().toByteArray());it.closeEntry()}}
 @Test fun templatesStayOutOfMainAndWidgetUntilApplied():Unit=runBlocking{
  val id=template();goals.addQuick(id,"Хлеб",2.0,"шт",45.0)
  assertTrue(db.shoppingListEntryDao().getVisibleSync().isEmpty());assertTrue(shopping.getAllEntries().first().isEmpty())
  assertTrue(ShoppingWidgetData(db,shopping,location).rows().isEmpty())
  goals.copyList(id)
  assertEquals(1,db.shoppingListEntryDao().getVisibleSync().size);assertEquals("Хлеб",ShoppingWidgetData(db,shopping,location).rows().single().title)
  assertFalse(goals.getList(id)!!.isTemplate);assertFalse(goals.getList(id)!!.showInPurchases)
 }
 @Test fun applyingKeepsQuantityUnitMoneyPlaceNotesAndOriginalRows():Unit=runBlocking{
  val id=template();goals.addQuick(id,"Грибы",.75,"кг",200.0,"Рынок","Мира, 1","Свежие",comment="Звонок")
  val source=goals.snapshot(id);goals.copyList(id)
  val copied=db.shoppingListEntryDao().getVisibleSync().single();val original=source.entries.single()
  assertNotEquals(original.id,copied.id);assertNotEquals(original.productId,copied.productId)
  assertEquals(original.copy(id=copied.id,productId=copied.productId,listId=null,createdAt=copied.createdAt,updatedAt=copied.updatedAt),copied)
  assertEquals(source,goals.snapshot(id));assertFalse(copied.isBought)
 }
 @Test fun existingListReceivesIndependentCopiesAndSourceCanBeReused():Unit=runBlocking{
  val id=template();val product=db.productDao().insert(ProductEntity(name="Банан",defaultUnit="кг"))
  shopping.addGoalCatalogueFixture(id,product,.5,"кг");val target=goals.create("Поездка")
  shopping.addGoalCatalogueFixture(target,product,1.0,"кг");goals.copyList(id,target)
  assertEquals(1.5,goals.snapshot(target).entries.single().quantity,0.0)
  goals.copyList(id,target);assertEquals(2.0,goals.snapshot(target).entries.single().quantity,0.0)
  assertEquals(.5,goals.snapshot(id).entries.single().quantity,0.0);assertTrue(db.shoppingListEntryDao().getVisibleSync().isEmpty())
 }
 @Test fun applyCanCreateNewOrdinaryHiddenList():Unit=runBlocking{
  val id=template();goals.addQuick(id,"Сыр")
  val target=goals.copyList(id,newListName="Праздник")!!
  assertFalse(goals.getList(target)!!.isTemplate);assertFalse(goals.getList(target)!!.showInPurchases)
  assertEquals("Праздник",goals.getList(target)!!.name);assertEquals(1,goals.snapshot(target).entries.size)
  assertTrue(db.shoppingListEntryDao().getVisibleSync().isEmpty())
 }
 @Test fun quickCopyEditsAndDeletionCannotChangeTemplate():Unit=runBlocking{
  val id=template();goals.addQuick(id,"Деталь",2.0,"шт");val target=goals.copyList(id,newListName="Сборка")!!
  val copied=goals.snapshot(target).entries.single();goals.updateItem(copied.id,"Новая деталь",3.0,"уп",null,null,expectedListId=target)
  assertEquals("Деталь",goals.snapshot(id).products.single().name);assertEquals(2.0,goals.snapshot(id).entries.single().quantity,0.0)
  goals.deleteList(target);assertEquals(1,goals.snapshot(id).products.size)
 }
 @Test fun snapshotFromMainIncludesVisibleListsResetsBoughtAndSkipsHidden():Unit=runBlocking{
  val product=db.productDao().insert(ProductEntity(name="Молоко"));val regular=shopping.addProduct(product,choice=ShopChoice.NONE)
  shopping.markBought(regular);val visible=goals.create("К ужину");goals.updateList(visible,"К ужину",true);goals.addQuick(visible,"Соль")
  val hidden=goals.create("Секрет");goals.addQuick(hidden,"Скрыто");val another=template();goals.addQuick(another,"Заготовка")
  val id=goals.captureList(null,"План");val rows=goals.snapshot(id)
  assertFalse(rows.list.isTemplate);assertEquals(setOf("Молоко","Соль"),rows.products.map{it.name}.toSet());assertTrue(rows.entries.none{it.isBought})
  assertTrue(db.shoppingListEntryDao().getById(regular)!!.isBought)
 }
 @Test fun savingAnOrdinaryListAsTemplateDoesNotMoveOrShareQuickItems():Unit=runBlocking{
  val list=goals.create("Рецепт");val row=goals.addQuick(list,"Тыква");goals.setBought(row,true)
  val before=goals.snapshot(list);val id=goals.captureList(list,"Рецепт каждую осень")
  assertEquals(before,goals.snapshot(list));assertFalse(goals.snapshot(id).entries.single().isBought)
  assertNotEquals(before.products.single().stableId,goals.snapshot(id).products.single().stableId)
 }
 @Test fun everyListCanBeBoughtMovedOrCopiedButCannotCopyIntoItself():Unit=runBlocking{
  val id=template();val row=goals.addQuick(id,"Хлеб");val list=goals.create("Другой список")
  assertTrue(runCatching{goals.copyList(id,id)}.isFailure)
  assertTrue(runCatching{goals.copyList(id,9999)}.isFailure)
  goals.setBought(row,true);assertTrue(goals.snapshot(id).entries.single().isBought)
  goals.copyList(id,list);assertFalse(goals.snapshot(list).entries.single().isBought)
  goals.batch(id,setOf(row),bought=false);goals.moveEntriesFrom(id,setOf(row),null)
  assertEquals(null,db.shoppingListEntryDao().getById(row)!!.listId)
 }
 @Test fun emptyOrStaleTemplateDoesNotCreateDestinationOrPurchases():Unit=runBlocking{
  val id=template();assertTrue(runCatching{goals.copyList(id,newListName="Пустой")}.isFailure);assertEquals(1,db.goalListDao().getAllSync().size)
  val row=goals.addQuick(id,"Сыр");val version=goals.snapshot(id).entries.associate{it.id to it.updatedAt}
  db.shoppingListEntryDao().update(db.shoppingListEntryDao().getById(row)!!.let{it.copy(quantity=3.0,updatedAt=it.updatedAt+1)})
  assertTrue(runCatching{goals.copyList(id,newListName="Устарел",expectedRows=version)}.isFailure)
  assertEquals(1,db.goalListDao().getAllSync().size);assertTrue(db.shoppingListEntryDao().getVisibleSync().isEmpty())
 }
 @Test fun overflowRollsBackEntireApplicationIncludingQuickProductClones():Unit=runBlocking{
  val id=template();goals.addQuick(id,"Первая позиция");val product=db.productDao().insert(ProductEntity(name="Максимум"))
  shopping.addGoalCatalogueFixture(id,product,1.0,"шт");shopping.addProduct(product,1_000_000_000.0,"шт",choice=ShopChoice.NONE)
  val rows=db.shoppingListEntryDao().getAllSync();val products=db.productDao().getAllSync()
  assertTrue(runCatching{goals.copyList(id)}.isFailure)
  assertEquals(rows,db.shoppingListEntryDao().getAllSync());assertEquals(products,db.productDao().getAllSync())
 }
 @Test fun failureWhileCreatingDestinationRollsBackDestinationAndEarlierRows():Unit=runBlocking{
  val id=template();goals.addQuick(id,"Первая");val bad=goals.addQuick(id,"Неверная")
  db.shoppingListEntryDao().update(db.shoppingListEntryDao().getById(bad)!!.copy(quantity=Double.MAX_VALUE))
  val products=db.productDao().getAllSync();val lists=db.goalListDao().getAllSync()
  assertTrue(runCatching{goals.copyList(id,newListName="Не создастся")}.isFailure)
  assertEquals(lists,db.goalListDao().getAllSync());assertEquals(products,db.productDao().getAllSync())
 }
 @Test fun fullBackupRoundTripsTemplateIdentityAndRejectsMalformedTypeBeforeReplacing():Unit=runBlocking{
  val id=template();goals.addQuick(id,"Сок");val before=goals.snapshot(id);val manager=BackupManager(app,db);val file=manager.exportFile()
  assertEquals(23,root(file).getInt("version"));goals.deleteList(id);manager.importFile(file);assertEquals(before,goals.snapshot(id))
  listOf("bad",null,true).forEach{value->
   val root=root(file);val list=root.getJSONObject("data").getJSONArray("goalLists").getJSONObject(0)
   if(value==true)list.put("isTemplate",true)else list.put("isTemplate",value?:JSONObject.NULL)
   assertTrue(runCatching{manager.importFile(archive(root))}.isFailure);assertEquals(before,goals.snapshot(id))
  }
  val old=root(file).put("version",17);old.getJSONObject("data").getJSONArray("goalLists").getJSONObject(0).put("isTemplate",true);assertTrue(runCatching{manager.importFile(archive(old))}.isFailure)
 }
 @Test fun oldZip17WithNoTemplateFlagRemainsAnOrdinaryList():Unit=runBlocking{
  val id=goals.create("Прежний список");goals.addQuick(id,"Чай");val manager=BackupManager(app,db);val root=root(manager.exportFile()).put("version",17)
  root.getJSONObject("data").getJSONArray("goalLists").getJSONObject(0).remove("isTemplate")
  manager.importFile(archive(root));assertFalse(goals.getList(id)!!.isTemplate);assertEquals("Чай",goals.snapshot(id).products.single().name)
 }
 @Test fun portableTemplateKeepsItsKindAndImportsWithoutPurchases():Unit=runBlocking{
  val id=template();goals.addQuick(id,"Кофе",2.0,"пачка",350.0,comment="Молотый")
  val document=goals.exportDocument(id);assertFalse(document.isTemplate)
  listOf(GoalListTransfer.exportText(document),GoalListTransfer.exportJson(document)).forEach{text->
   val parsed=GoalListTransfer.parse(text);assertEquals(emptyList<String>(),parsed.errors);assertEquals(document,parsed.document)
   val copy=goals.importDocument(parsed.document!!);assertFalse(goals.getList(copy)!!.isTemplate);assertEquals("Кофе",goals.snapshot(copy).products.single().name)
  }
  assertTrue(db.shoppingListEntryDao().getVisibleSync().isEmpty())
 }
 @Test fun repeatedUiConfirmationIsOneApplication():Unit=runBlocking{
  val id=template();goals.addQuick(id,"Хлеб");val vm=ListCopyViewModel(id,goals)
  suspend fun await(condition:()->Boolean){val end=System.nanoTime()+15_000_000_000;while(!condition()&&System.nanoTime()<end){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(20));yield();Thread.sleep(5)};assertTrue(condition())}
  try{await{!vm.uiState.value.loading};vm.apply(null,null);vm.apply(null,null);await{vm.uiState.value.completed};assertEquals(1,db.shoppingListEntryDao().getVisibleSync().size)}finally{vm.viewModelScope.cancel()}
 }
 @Test fun migration20KeepsOrdinaryOwnerIdsAndPurchases():Unit=runBlocking{
  val name="templates240-migration.db";app.deleteDatabase(name)
  val schema=File("schemas/com.spisokryadom.app.data.db.AppDatabase/20.json").takeIf{it.isFile}?:File("app/schemas/com.spisokryadom.app.data.db.AppDatabase/20.json")
  app.openOrCreateDatabase(name,0,null).use{old->val entities=JSONObject(schema.readText()).getJSONObject("database").getJSONArray("entities");for(i in 0 until entities.length()){val e=entities.getJSONObject(i);old.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName")));val indices=e.getJSONArray("indices");for(j in 0 until indices.length())old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName")))}
   old.execSQL("INSERT INTO goal_lists(id,stableId,name,showInPurchases,createdAt,updatedAt)VALUES(9,'old-list','История',1,1,2)")
   old.execSQL("INSERT INTO products(id,name,purchaseType,isTemporary,stableId)VALUES(8,'Чай','offline',1,'old-product')")
   old.execSQL("INSERT INTO shopping_list_entries(id,productId,listId,quantity,unit,isBought,isUrgent,createdAt,updatedAt)VALUES(7,8,9,3,'пачка',1,0,1,2)");old.version=20
  }
  val migrated=Room.databaseBuilder(app,AppDatabase::class.java,name).allowMainThreadQueries().addMigrations(AppDatabase.MIGRATION_20_21,AppDatabase.MIGRATION_21_22,AppDatabase.MIGRATION_22_23,AppDatabase.MIGRATION_23_24,AppDatabase.MIGRATION_24_25).build()
  try{val owner=migrated.goalListDao().getById(9)!!;assertFalse(owner.isTemplate);assertTrue(owner.showInPurchases);assertEquals("old-list",owner.stableId);assertEquals(9L,migrated.shoppingListEntryDao().getById(7)!!.listId);assertTrue(migrated.shoppingListEntryDao().getById(7)!!.isBought);assertEquals(25,migrated.openHelper.writableDatabase.version)}finally{migrated.close();app.deleteDatabase(name)}
 }
}
