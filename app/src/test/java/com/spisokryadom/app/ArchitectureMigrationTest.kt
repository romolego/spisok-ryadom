package com.spisokryadom.app
import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.*
import com.spisokryadom.app.data.backup.BackupManager
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class ArchitectureMigrationTest {
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 private fun old(name:String){app.openOrCreateDatabase(name,0,null).use{db->
  val entities=JSONObject(javaClass.classLoader!!.getResourceAsStream("schema-v10.json")!!.reader().readText()).getJSONObject("database").getJSONArray("entities")
  for(i in 0 until entities.length()){val entity=entities.getJSONObject(i);val table=entity.getString("tableName");db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",table));val indices=entity.getJSONArray("indices");for(j in 0 until indices.length())db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))}
  db.execSQL("INSERT INTO cities(id,name)VALUES(1,'Красноярск')")
  db.execSQL("INSERT INTO shops(id,name,displayOrder,manualOrderOverride,cityId)VALUES(1,'Командор',0,0,1),(2,'Батон',1,0,1)")
  db.execSQL("INSERT INTO shop_branches(id,shopId,address,displayOrder,note,photoUri)VALUES(8,1,'Дом',0,'Беру молоко','/old/photo.jpg'),(9,2,'Другой адрес',0,NULL,NULL)")
  db.execSQL("INSERT INTO shop_departments(id,shopId,name,displayOrder)VALUES(11,1,'Молочка',7),(21,2,' молочка ',1)")
  db.execSQL("INSERT INTO products(id,name,purchaseType,isTemporary,stableId)VALUES(2,'Сыр','offline',0,'cheese'),(3,'Цукаты','offline',0,'fruit')")
  db.execSQL("INSERT INTO product_shop_links(id,productId,shopId,departmentId,priority,restrictBranches)VALUES(4,2,1,11,1,1)")
  db.execSQL("INSERT INTO product_branch_availability(productId,branchId)VALUES(2,8),(3,9)")
  db.execSQL("INSERT INTO shopping_list_entries(id,productId,quantity,unit,isBought,isUrgent,createdAt,updatedAt,assignedShopId,assignedDepartmentId,assignedBranchId,note,comment)VALUES(13,2,0.5,'кг',0,1,10,20,1,11,8,'Заметка','Комментарий'),(14,3,2,'шт',1,0,15,25,2,21,9,NULL,NULL)")
  db.version=10
 }}
 @Test fun schema10MigrationRetainsIdentitiesAndRepairsOnlyOrphanedAvailability()=runBlocking{
  val name="architecture-old.db";old(name)
  val db=Room.databaseBuilder(app,AppDatabase::class.java,name).allowMainThreadQueries().addMigrations(AppDatabase.MIGRATION_10_11,AppDatabase.MIGRATION_11_12,AppDatabase.MIGRATION_12_13,AppDatabase.MIGRATION_13_14,AppDatabase.MIGRATION_14_15,AppDatabase.MIGRATION_15_16,AppDatabase.MIGRATION_16_17,AppDatabase.MIGRATION_17_18).build()
  try{
   assertEquals(18,db.openHelper.writableDatabase.version)
   val one=db.shopDepartmentDao().getById(11)!!;val two=db.shopDepartmentDao().getById(21)!!
   assertEquals(one.typeId,two.typeId);assertEquals(7,one.displayOrder);assertEquals(1,two.displayOrder)
   assertEquals(4L,db.productShopLinkDao().getLinksForProductSync(2).single().id)
   assertEquals("cheese",db.productDao().getById(2)!!.stableId);assertFalse(db.productDao().getById(2)!!.preferCityShopOrder)
   val entry=db.shoppingListEntryDao().getById(13)!!;assertEquals(0.5,entry.quantity,0.0);assertEquals("Комментарий",entry.comment);assertEquals(11L,entry.assignedDepartmentId);assertEquals(8L,entry.assignedBranchId)
   assertTrue(db.shoppingListEntryDao().getById(14)!!.isBought);assertEquals(2,db.shoppingListEntryDao().getAllSync().size)
   assertEquals(1,db.shopBranchDao().allAvailability().size);assertEquals(1L,db.shopBranchDao().allAvailability().single().shopId)
   assertEquals("/old/photo.jpg",db.shopBranchDao().get(8)!!.photoUri)
   db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
   assertTrue(BackupManager(app,db).exportFile().isFile)
  }finally{db.close();app.deleteDatabase(name)}
 }
 @Test fun recoveryCheckpointPreservesOldSchemaMediaAndPreferencesBeforeUpgrade(){
  val name="spisok_ryadom.db";old(name)
  val photo=File(app.filesDir,"branch_photos/original.jpg");photo.parentFile!!.mkdirs();photo.writeBytes(byteArrayOf(1,2,3))
  app.getSharedPreferences("location_prefs",0).edit().putLong("active_city",1).commit()
  DatabaseRecovery.preserveBeforeUpgrade(app,11)
  val destination=File(app.filesDir,"recovery/schema-10-before-11")
  assertTrue(File(destination,"READY").isFile);assertArrayEquals(photo.readBytes(),File(destination,"branch_photos/original.jpg").readBytes())
  assertTrue(File(destination,"shared_prefs/location_prefs.xml").isFile)
  SQLiteDatabase.openDatabase(File(destination,name).path,null,SQLiteDatabase.OPEN_READONLY).use{assertEquals(10,it.version)}
  DatabaseRecovery.preserveBeforeUpgrade(app,11);assertTrue(File(destination,"READY").isFile)
  app.deleteDatabase(name)
 }
 @Test fun checkpointIsDeferredUntilBackgroundOpenAndPrecedesRoomMigration()=runBlocking{
  val name="spisok_ryadom.db";old(name)
  val prepared=java.util.concurrent.atomic.AtomicBoolean(false)
  val calls=java.util.concurrent.atomic.AtomicInteger()
  val factory=BeforeOpenHelperFactory{
   assertNotEquals(android.os.Looper.getMainLooper().thread,Thread.currentThread())
   SQLiteDatabase.openDatabase(app.getDatabasePath(name).path,null,SQLiteDatabase.OPEN_READONLY).use{assertEquals(10,it.version)}
   DatabaseRecovery.preserveBeforeUpgrade(app,11);calls.incrementAndGet();prepared.set(true)
  }
  val db=Room.databaseBuilder(app,AppDatabase::class.java,name).openHelperFactory(factory).addMigrations(AppDatabase.MIGRATION_10_11,AppDatabase.MIGRATION_11_12,AppDatabase.MIGRATION_12_13,AppDatabase.MIGRATION_13_14,AppDatabase.MIGRATION_14_15,AppDatabase.MIGRATION_15_16,AppDatabase.MIGRATION_16_17,AppDatabase.MIGRATION_17_18).build()
  try{
   assertFalse("Building the container must perform no recovery file I/O",prepared.get())
   kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){assertEquals(1,db.cityDao().getAllSync().size);assertEquals(18,db.openHelper.writableDatabase.version);db.shopDao().getAllSync()}
   assertTrue(prepared.get());assertEquals(1,calls.get())
  }finally{db.close();app.deleteDatabase(name)}
 }
}
