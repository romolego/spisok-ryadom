package com.spisokryadom.app
import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.util.UUID
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class MigrationRegressionTest {
 private val context:Context get()=ApplicationProvider.getApplicationContext<Application>()
 private fun create(version:Int):String {
  val name="migration_${UUID.randomUUID()}.db"
  val schema=JSONArray(javaClass.classLoader!!.getResourceAsStream("schema7.json")!!.reader().readText())
  val statements=(0 until schema.length()).map{schema.getString(it)}
  context.openOrCreateDatabase(name,0,null).use{old->
   statements.filter{it.startsWith("CREATE TABLE")}.forEach{sql->
    if(version==7)old.execSQL(sql)
    else if(!sql.contains("`units`")){
     var first=sql
     val table=sql.substringBefore(" (")
     val remove=when {table.contains("`products`")->listOf("priceValue","priceDate","isTemporary");table.contains("`shops`")->listOf("showUrgentInCollapsed","boostUrgentShop","manualOrderOverride");table.contains("`product_groups`")||table.contains("`recipients`")->listOf("displayOrder");table.contains("`shopping_list_entries`")->listOf("comment");else->emptyList()}
     remove.forEach{column->first=first.replace(Regex(", `"+column+"` (?:INTEGER|TEXT|REAL)(?: NOT NULL)?"),"")};old.execSQL(first)
    }
   }
   statements.filter{it.startsWith("CREATE INDEX")}.forEach{old.execSQL(it)}
   if(version==7){
    old.execSQL("INSERT INTO products(id,name,purchaseType,isTemporary,defaultUnit) VALUES(1,'Яблоки Гала','offline',0,'кг'),(2,'Разовый','offline',1,NULL),(3,'Сирота','offline',1,NULL)")
    old.execSQL("INSERT INTO shops(id,name,displayOrder,manualOrderOverride) VALUES(1,'Слата',0,0),(2,'Батон',1,0)")
    old.execSQL("INSERT INTO shop_departments(id,shopId,name,displayOrder) VALUES(1,1,'Фрукты',0)")
    old.execSQL("INSERT INTO product_shop_links(id,productId,shopId,priority,departmentId) VALUES(1,1,2,1,1),(2,1,2,2,NULL)")
    old.execSQL("INSERT INTO shopping_list_entries(id,productId,quantity,unit,isBought,isUrgent,createdAt,updatedAt,assignedShopId,assignedDepartmentId) VALUES(1,1,0.5,'кг',0,1,1,1,2,1),(2,2,1,'шт',0,0,2,2,NULL,NULL)")
   }else old.execSQL("INSERT INTO products(id,name,purchaseType,defaultUnit) VALUES(1,'Яблоки Гала','offline','кг')")
   old.version=version
  }
  return name
 }
 private fun open(name:String)=Room.databaseBuilder(context,AppDatabase::class.java,name).allowMainThreadQueries().addMigrations(AppDatabase.MIGRATION_1_2,AppDatabase.MIGRATION_2_3,AppDatabase.MIGRATION_3_4,AppDatabase.MIGRATION_4_5,AppDatabase.MIGRATION_5_6,AppDatabase.MIGRATION_6_7,AppDatabase.MIGRATION_7_8,AppDatabase.MIGRATION_8_9,AppDatabase.MIGRATION_9_10,AppDatabase.MIGRATION_10_11,AppDatabase.MIGRATION_11_12,AppDatabase.MIGRATION_12_13,AppDatabase.MIGRATION_13_14,AppDatabase.MIGRATION_14_15,AppDatabase.MIGRATION_15_16,AppDatabase.MIGRATION_16_17,AppDatabase.MIGRATION_17_18,AppDatabase.MIGRATION_18_19,AppDatabase.MIGRATION_19_20).build()
 @Test fun schemaFromPhoneApkMigratesWithoutLosingBasket()=runBlocking {
  val name=create(7);val db=open(name)
  try{
   assertEquals(20,db.openHelper.writableDatabase.version)
   assertEquals("Яблоки Гала",db.productDao().getById(1)?.name);assertEquals(2,db.productDao().getAllSync().size)
   assertNull(db.productDao().getById(3));assertTrue(db.productDao().getById(2)!!.isTemporary)
   assertEquals(1L,db.shopDao().getById(2)!!.cityId);assertEquals("Мой город",db.cityDao().getById(1)?.name)
   assertEquals(1,db.productShopLinkDao().getLinksForProductSync(1).size);assertNull(db.productShopLinkDao().getLinksForProductSync(1).single().departmentId)
   val entry=db.shoppingListEntryDao().getById(1)!!;assertEquals(.5,entry.quantity,0.0);assertTrue(entry.isUrgent);assertNull(entry.assignedDepartmentId)
   db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{assertEquals(0,it.count)}
  }finally{db.close();context.deleteDatabase(name)}
 }
 @Test fun oldestDatabaseMigratesThroughEveryVersion()=runBlocking {
  val name=create(1);val db=open(name)
  try{assertEquals("Яблоки Гала",db.productDao().getById(1)?.name);assertEquals("кг",db.unitDao().getAllSync().single().name);assertFalse(db.productDao().getById(1)!!.isTemporary);assertEquals(20,db.openHelper.writableDatabase.version)}finally{db.close();context.deleteDatabase(name)}
 }
}
