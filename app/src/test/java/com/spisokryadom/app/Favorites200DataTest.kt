package com.spisokryadom.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.backup.BackupManager
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.ui.productcard.*
import com.spisokryadom.app.util.*
import kotlinx.coroutines.flow.first
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
class Favorites200DataTest {
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 private lateinit var db:AppDatabase
 private lateinit var locations:LocationRepository
 private lateinit var shops:ShopRepository
 private lateinit var groups:ClassifierRepository
 private val prefs get()=app.getSharedPreferences("shopping_list_prefs",0)
 @Before fun setup():Unit=runBlocking{
  prefs.edit().clear().commit();db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build()
  db.cityDao().insert(CityEntity(1,"Город"));locations=LocationRepository(db.cityDao(),db.shopDao(),app.getSharedPreferences("200-locations",0));shops=ShopRepository(db.shopDao(),db.shopDepartmentDao(),locations,db)
  groups=ClassifierRepository(db.productGroupDao(),db.recipientDao(),db.unitDao(),db)
 }
 @After fun close(){locations.closeForTest();db.close()}
 private fun manifest(f:File)=ZipFile(f).use{z->JSONObject(z.getInputStream(z.getEntry("manifest.json")).reader().readText())}
 private fun archive(root:JSONObject):File=File(app.cacheDir,"altered200.zip").also{f->ZipOutputStream(f.outputStream()).use{z->z.putNextEntry(ZipEntry("manifest.json"));z.write(root.toString().toByteArray());z.closeEntry()}}
 @Test fun dictionaryAlphabetAndPinsAreIndependentAndNamesReposition():Unit=runBlocking{
  val food=groups.insertGroup(ProductGroupEntity(name="Продукты",displayOrder=0,accent="green"));val health=groups.insertGroup(ProductGroupEntity(name="Аптека",displayOrder=2));val home=groups.insertGroup(ProductGroupEntity(name="Бытовая химия",displayOrder=1))
  assertEquals(listOf(health,home,food),groups.getAllGroups().first().map{it.id})
  groups.favoriteGroup(food,true);groups.favoriteGroup(health,true);groups.moveGroup(health,-1)
  assertEquals(listOf(health,food),db.productGroupDao().getAllSync().filter{it.isFavorite}.sortedBy{it.favoriteOrder}.map{it.id})
  val original=db.productGroupDao().getAllSync();groups.moveGroup(home,-1);assertEquals(original,db.productGroupDao().getAllSync())
  groups.renameGroup(food,"Автомобильное");assertEquals(listOf(food,health,home),groups.getAllGroups().first().map{it.id});assertTrue(groups.getGroupById(food)!!.isFavorite);assertEquals("green",groups.getGroupById(food)!!.accent)
  groups.favoriteGroup(food,false);assertEquals(listOf(health),groups.getAllGroups().first().filter{it.isFavorite}.map{it.id})
 }
 @Test fun departmentPinsNeverChangeStoreRoutesRelationsOrUnits():Unit=runBlocking{
  val shop=db.shopDao().insert(ShopEntity(name="Магазин"));val fruit=shops.createDepartmentType("Овощи/фрукты");val freeze=shops.createDepartmentType("Заморозка")
  val a=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=shop,name="Овощи/фрукты",typeId=fruit,displayOrder=8));val b=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=shop,name="Заморозка",typeId=freeze,displayOrder=2))
  val p=db.productDao().insert(ProductEntity(name="Ягоды"));db.productShopLinkDao().insert(ProductShopLinkEntity(productId=p,shopId=shop,departmentId=a))
  shops.favoriteDepartmentType(fruit,true);shops.favoriteDepartmentType(freeze,true);shops.moveDepartmentType(freeze,-1)
  assertEquals(listOf(freeze,fruit),shops.departmentTypes().first().map{it.id});assertEquals(freeze,db.shopDepartmentDao().types().filter{it.isFavorite}.minBy{it.favoriteOrder}.id)
  assertEquals(8,db.shopDepartmentDao().getById(a)!!.displayOrder);assertEquals(2,db.shopDepartmentDao().getById(b)!!.displayOrder);assertEquals(a,db.productShopLinkDao().getLinksForProductSync(p).single().departmentId)
  val u=groups.insertUnit(UnitEntity(name="шт",displayOrder=1));val kg=groups.insertUnit(UnitEntity(name="кг",displayOrder=2));groups.moveUnit(kg,-1);assertEquals(listOf(kg,u),groups.getAllUnits().first().map{it.id})
 }
 @Test fun fullBackupRestoresPinOrdersAppearanceAndQuantity():Unit=runBlocking{
  val g=groups.insertGroup(ProductGroupEntity(name="Продукты"));val t=shops.createDepartmentType("Молочка");groups.favoriteGroup(g,true);shops.favoriteDepartmentType(t,true)
  FavoriteAppearance(FavoriteStyle(false,"#EEDD44",false,false),FavoriteStyle(true,"#AACCEE",true,true)).save(prefs);prefs.edit().putString(ProductCreationDefaults.QUANTITY,"2,5").commit()
  val before=FavoriteAppearance.read(prefs);val manager=BackupManager(app,db);val file=manager.exportFile();assertEquals(24,manifest(file).getInt("version"))
  groups.favoriteGroup(g,false);shops.favoriteDepartmentType(t,false);prefs.edit().clear().commit();manager.importFile(file)
  assertTrue(groups.getGroupById(g)!!.isFavorite);assertTrue(db.shopDepartmentDao().type(t)!!.isFavorite);assertEquals(before,FavoriteAppearance.read(prefs));assertEquals(2.5,ProductCreationDefaults.read(prefs).missingQuantity,0.0);manager.discard(file)
 }
 @Test fun olderBackupDefaultsToUnpinnedWithoutChangingIdsOrRoutes():Unit=runBlocking{
  val g=groups.insertGroup(ProductGroupEntity(name="Группа",displayOrder=19));val t=shops.createDepartmentType("Отдел");val manager=BackupManager(app,db);val source=manager.exportFile();val root=manifest(source).put("version",13)
  listOf("productGroups","departmentTypes").forEach{k->val a=root.getJSONObject("data").getJSONArray(k);for(i in 0 until a.length()){a.getJSONObject(i).remove("isFavorite");a.getJSONObject(i).remove("favoriteOrder")}}
  manager.importFile(archive(root));assertFalse(groups.getGroupById(g)!!.isFavorite);assertEquals(19,groups.getGroupById(g)!!.displayOrder);assertFalse(db.shopDepartmentDao().type(t)!!.isFavorite);manager.discard(source)
 }
 @Test fun malformedFavoritesAndDefaultsRefuseBeforeReplacingDatabase():Unit=runBlocking{
  val id=groups.insertGroup(ProductGroupEntity(name="Не терять"));val manager=BackupManager(app,db);val source=manager.exportFile()
  suspend fun reject(mutate:(JSONObject)->Unit){val root=manifest(source);mutate(root);try{manager.importFile(archive(root));fail("Invalid metadata accepted")}catch(_:IllegalArgumentException){};assertEquals("Не терять",groups.getGroupById(id)!!.name)}
  reject{it.getJSONObject("data").getJSONArray("productGroups").getJSONObject(0).put("isFavorite","true")}
  reject{it.getJSONObject("data").getJSONArray("productGroups").getJSONObject(0).put("favoriteOrder",-1)}
  reject{it.getJSONObject("data").getJSONArray("productGroups").getJSONObject(0).remove("favoriteOrder")}
  reject{it.getJSONObject("preferences").getJSONObject("shopping_list_prefs").put("favorite_row_star","yes")}
  reject{it.getJSONObject("preferences").getJSONObject("shopping_list_prefs").put("favorite_block_color","#oops")}
  reject{it.getJSONObject("preferences").getJSONObject("shopping_list_prefs").put(ProductCreationDefaults.QUANTITY,"0")}
  manager.discard(source)
 }
 @Test fun blankNewCardUsesConfiguredQuantityButExplicitQuantityWins():Unit=runBlocking{
  prefs.edit().putString(ProductCreationDefaults.QUANTITY,"2,5").putString(ProductCreationDefaults.UNIT,"ед").commit()
  val repo=ProductRepository(db.productDao(),db.productShopLinkDao(),db)
  val id=ProductEditDraft("new200",-1,true,false,ProductCardUiState(name="Товар",isNew=true)).persist(repo,ProductCreationDefaults.read(prefs))
  assertEquals(2.5,db.productDao().getById(id)!!.defaultQuantity!!,0.0);assertEquals("ед",db.productDao().getById(id)!!.defaultUnit)
  val explicit=ProductEditDraft("explicit200",-1,true,false,ProductCardUiState(name="Другой",isNew=true,defaultQuantity="0,75",defaultUnit="кг")).persist(repo,ProductCreationDefaults.read(prefs))
  assertEquals(.75,db.productDao().getById(explicit)!!.defaultQuantity!!,0.0);assertEquals("кг",db.productDao().getById(explicit)!!.defaultUnit)
 }
 @Test fun migration17PreservesExistingClassifierOrderAndProductReferences():Unit=runBlocking{
  val name="migration200.db";app.deleteDatabase(name)
  val schema=File("schemas/com.spisokryadom.app.data.db.AppDatabase/17.json").takeIf{it.isFile}?:File("app/schemas/com.spisokryadom.app.data.db.AppDatabase/17.json")
  app.openOrCreateDatabase(name,0,null).use{old->val a=JSONObject(schema.readText()).getJSONObject("database").getJSONArray("entities");for(i in 0 until a.length()){val e=a.getJSONObject(i);old.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName")));val ix=e.getJSONArray("indices");for(j in 0 until ix.length())old.execSQL(ix.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName")))}
   old.execSQL("INSERT INTO product_groups(id,name,displayOrder,accent,iconKey)VALUES(11,'Продукты',42,'green','food')");old.execSQL("INSERT INTO department_types(id,name,normalizedName,displayOrder,accent,iconKey)VALUES(12,'Отдел','отдел',9,'blue','milk')");old.version=17
  }
  val migrated=Room.databaseBuilder(app,AppDatabase::class.java,name).allowMainThreadQueries().addMigrations(AppDatabase.MIGRATION_17_18,AppDatabase.MIGRATION_18_19,AppDatabase.MIGRATION_19_20,AppDatabase.MIGRATION_20_21,AppDatabase.MIGRATION_21_22,AppDatabase.MIGRATION_22_23,AppDatabase.MIGRATION_23_24,AppDatabase.MIGRATION_24_25,AppDatabase.MIGRATION_25_26).build()
  try{val g=migrated.productGroupDao().getById(11)!!;assertEquals(26,migrated.openHelper.writableDatabase.version);assertEquals(42,g.displayOrder);assertEquals("green",g.accent);assertFalse(g.isFavorite);assertEquals(0,g.favoriteOrder);val t=migrated.shopDepartmentDao().type(12)!!;assertEquals(9,t.displayOrder);assertFalse(t.isFavorite);assertEquals("milk",t.iconKey);migrated.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}}
  finally{migrated.close();app.deleteDatabase(name)}
 }
}
