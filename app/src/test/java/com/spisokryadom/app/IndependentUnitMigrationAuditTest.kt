package com.spisokryadom.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.ShopChoice
import com.spisokryadom.app.data.repository.LocationRepository
import com.spisokryadom.app.data.repository.ShoppingListRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class IndependentUnitMigrationAuditTest {
 @Test fun migratedLegacyUnitSpellingStillMergesWithNormalizedNewPurchase()=runBlocking {
  val context=ApplicationProvider.getApplicationContext<Application>()
  val name="u.db"
  println("AUDIT database path=${context.getDatabasePath(name)} length=${context.getDatabasePath(name).absolutePath.length}")
  context.getDatabasePath(name).parentFile!!.mkdirs()
  val schema=JSONArray(javaClass.classLoader!!.getResourceAsStream("schema7.json")!!.reader().readText())
  context.openOrCreateDatabase(name,0,null).use{old->
   val statements=(0 until schema.length()).map{schema.getString(it)}
   statements.filter{it.startsWith("CREATE TABLE")}.forEach{old.execSQL(it)}
   statements.filter{it.startsWith("CREATE INDEX")}.forEach{old.execSQL(it)}
   old.execSQL("INSERT INTO products(id,name,purchaseType,isTemporary,defaultUnit) VALUES(1,'Картофель','offline',0,'КГ.')")
   old.execSQL("INSERT INTO shopping_list_entries(id,productId,quantity,unit,isBought,isUrgent,createdAt,updatedAt) VALUES(1,1,2.0,'КГ.',0,0,1,1)")
   old.version=7
  }
  val db=Room.databaseBuilder(context,AppDatabase::class.java,name).allowMainThreadQueries().addMigrations(AppDatabase.MIGRATION_7_8,AppDatabase.MIGRATION_8_9,AppDatabase.MIGRATION_9_10,AppDatabase.MIGRATION_10_11,AppDatabase.MIGRATION_11_12,AppDatabase.MIGRATION_12_13,AppDatabase.MIGRATION_13_14,AppDatabase.MIGRATION_14_15,AppDatabase.MIGRATION_15_16,AppDatabase.MIGRATION_16_17,AppDatabase.MIGRATION_17_18,AppDatabase.MIGRATION_18_19,AppDatabase.MIGRATION_19_20,AppDatabase.MIGRATION_20_21,AppDatabase.MIGRATION_21_22,AppDatabase.MIGRATION_22_23,AppDatabase.MIGRATION_23_24,AppDatabase.MIGRATION_24_25,AppDatabase.MIGRATION_25_26).build()
  assertEquals(26,db.openHelper.writableDatabase.version)
  val locations=LocationRepository(db.cityDao(),db.shopDao(),context.getSharedPreferences("location_prefs",0))
  try{
   val basket=ShoppingListRepository(db.shoppingListEntryDao(),db,locations)
   basket.addProduct(1,1.0,"кг")
   val entries=db.shoppingListEntryDao().getAllSync()
   println("AUDIT migrated unit entries=$entries")
   assertEquals("Equivalent unit spellings must not split a migrated purchase",1,entries.size)
   assertEquals(3.0,entries.single().quantity,0.0)
  }finally{locations.closeForTest();db.close();context.deleteDatabase(name)}
 }

 @Test fun existingVersion8AliasesConsolidateSafelyWithoutChangingAnnotatedOrBoughtRows()=runBlocking {
  val context=ApplicationProvider.getApplicationContext<Application>()
  val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).allowMainThreadQueries().build()
  db.cityDao().insert(CityEntity(1,"Мой город"))
  val prefs=context.getSharedPreferences("unit_aliases_existing_8",0);prefs.edit().clear().commit()
  val locations=LocationRepository(db.cityDao(),db.shopDao(),prefs)
  try {
   val dao=db.shoppingListEntryDao()
   val p=db.productDao().insert(ProductEntity(name="Картофель",defaultUnit="КГ."))
   val first=dao.insert(ShoppingListEntryEntity(productId=p,quantity=2.0,unit="КГ.",isUrgent=true))
   val second=dao.insert(ShoppingListEntryEntity(productId=p,quantity=3.0,unit="килограмма"))
   val note=dao.insert(ShoppingListEntryEntity(productId=p,quantity=4.0,unit="кг",note="Для супа"))
   val bought=dao.insert(ShoppingListEntryEntity(productId=p,quantity=5.0,unit="кг.",isBought=true))
   val pieces=dao.insert(ShoppingListEntryEntity(productId=p,quantity=6.0,unit="шт"))
   val before=dao.getAllSync().associateBy{it.id}
   val basket=ShoppingListRepository(dao,db,locations)
   basket.addProduct(p,1.0,"кг",ShopChoice.NONE)
   assertEquals(4,dao.getAllSync().size)
   assertEquals(6.0,dao.getById(first)!!.quantity,0.0)
   assertEquals("кг",dao.getById(first)!!.unit);assertTrue(dao.getById(first)!!.isUrgent)
   assertNull(dao.getById(second))
   listOf(note,bought,pieces).forEach{assertEquals(before[it],dao.getById(it))}
   assertEquals(first,basket.addProduct(p,1.0,"кг",ShopChoice.NONE))
   assertEquals(first,basket.addProduct(p,1.0,"КИЛОГРАММ",ShopChoice.NONE))
   assertEquals(8.0,dao.getById(first)!!.quantity,0.0)
   assertEquals(4,dao.getAllSync().size)
  }finally{locations.closeForTest();db.close()}
 }

 @Test fun canonicalMergeOverflowRollsBackAndDifferentCommentsAndDepartmentsStaySeparate()=runBlocking {
  val context=ApplicationProvider.getApplicationContext<Application>()
  val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).allowMainThreadQueries().build()
  db.cityDao().insert(CityEntity(1,"Мой город"))
  val prefs=context.getSharedPreferences("unit_aliases_overflow",0);prefs.edit().clear().commit()
  val locations=LocationRepository(db.cityDao(),db.shopDao(),prefs)
  try {
   val dao=db.shoppingListEntryDao();val basket=ShoppingListRepository(dao,db,locations)
   val p=db.productDao().insert(ProductEntity(name="Крупа"))
   val shop=db.shopDao().insert(ShopEntity(name="Магазин",cityId=1))
   val dept=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=shop,name="Бакалея"))
   dao.insert(ShoppingListEntryEntity(productId=p,quantity=1_000_000_000.0,unit="КГ.",assignedShopId=shop))
   dao.insert(ShoppingListEntryEntity(productId=p,quantity=2.0,unit="килограмма",assignedShopId=shop))
   val before=dao.getAllSync()
   try{basket.addProduct(p,1.0,"кг",ShopChoice.EXPLICIT,shop);fail("Overflow must be rejected")}catch(_:IllegalArgumentException){}
   assertEquals(before,dao.getAllSync())
   val annotated=basket.addOrMergeEntry(ShoppingListEntryEntity(productId=p,quantity=3.0,unit="кг",assignedShopId=shop,comment="Другой сорт"))
   val grouped=basket.addOrMergeEntry(ShoppingListEntryEntity(productId=p,quantity=4.0,unit="кг",assignedShopId=shop,assignedDepartmentId=dept))
   assertEquals(4,dao.getAllSync().size)
   assertEquals("Другой сорт",dao.getById(annotated)!!.comment)
   assertEquals(dept,dao.getById(grouped)!!.assignedDepartmentId)
  }finally{locations.closeForTest();db.close()}
 }
}
