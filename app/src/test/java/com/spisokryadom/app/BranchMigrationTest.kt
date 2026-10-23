package com.spisokryadom.app
import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.util.UUID
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class BranchMigrationTest {
 @Test fun v8ToV9PreservesData()=runBlocking {
  val app=ApplicationProvider.getApplicationContext<Application>();val name="b.db"
  app.openOrCreateDatabase(name,0,null).use{old->
   val schema=JSONObject(javaClass.classLoader!!.getResourceAsStream("schema-v8.json")!!.reader().readText()).getJSONObject("database").getJSONArray("entities")
   for(i in 0 until schema.length()) {
    val table=schema.getJSONObject(i);val tableName=table.getString("tableName")
    old.execSQL(table.getString("createSql").replace("\${TABLE_NAME}",tableName))
    val indices=table.getJSONArray("indices")
    for(j in 0 until indices.length())old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",tableName))
   }
   old.execSQL("INSERT INTO cities(id,name)VALUES(1,'Красноярск')")
   old.execSQL("INSERT INTO shops(id,name,address,displayOrder,manualOrderOverride,cityId)VALUES(7,'Красный Яр','Мира, 10',2,0,1)")
   old.execSQL("INSERT INTO products(id,name,purchaseType,isTemporary,stableId)VALUES(9,'Спагетти','offline',0,'original-product-id')")
   old.execSQL("INSERT INTO product_shop_links(id,productId,shopId,priority)VALUES(5,9,7,1)")
   old.execSQL("INSERT INTO shopping_list_entries(id,productId,quantity,unit,assignedShopId,isBought,isUrgent,note,comment,createdAt,updatedAt)VALUES(12,9,0.5,'КГ.',7,0,1,'Заметка','Комментарий',123,456)")
   old.version=8
  }
  val db=Room.databaseBuilder(app,AppDatabase::class.java,name).allowMainThreadQueries().addMigrations(AppDatabase.MIGRATION_8_9,AppDatabase.MIGRATION_9_10,AppDatabase.MIGRATION_10_11,AppDatabase.MIGRATION_11_12,AppDatabase.MIGRATION_12_13,AppDatabase.MIGRATION_13_14,AppDatabase.MIGRATION_14_15,AppDatabase.MIGRATION_15_16,AppDatabase.MIGRATION_16_17,AppDatabase.MIGRATION_17_18,AppDatabase.MIGRATION_18_19,AppDatabase.MIGRATION_19_20,AppDatabase.MIGRATION_20_21,AppDatabase.MIGRATION_21_22,AppDatabase.MIGRATION_22_23,AppDatabase.MIGRATION_23_24,AppDatabase.MIGRATION_24_25).build()
  try {
   assertEquals(25,db.openHelper.writableDatabase.version)
   val row=db.shoppingListEntryDao().getById(12)!!
   assertEquals(9L,row.productId);assertEquals(.5,row.quantity,0.0);assertEquals("КГ.",row.unit)
   assertEquals("Заметка",row.note);assertEquals("Комментарий",row.comment);assertTrue(row.isUrgent);assertFalse(row.isBought)
   assertEquals(123L,row.createdAt);assertEquals(456L,row.updatedAt)
   val branch=db.shopBranchDao().get(row.assignedBranchId!!)!!
   assertEquals(7L,branch.shopId);assertEquals("Мира, 10",branch.address)
   assertEquals("original-product-id",db.productDao().getById(9)!!.stableId)
   assertFalse(db.productShopLinkDao().getAllSync().single().restrictBranches)
   db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use{assertEquals(0,it.count)}
  }finally{db.close();app.deleteDatabase(name)}
 }
}
