package com.spisokryadom.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class ProductInput300MigrationTest {
    @Test fun version26MigrationRetainsIdsPhotosAndDifferentShopGroups():Unit=runBlocking {
        val app=ApplicationProvider.getApplicationContext<Application>();val name="input300-${UUID.randomUUID()}.db"
        app.openOrCreateDatabase(name,0,null).use{db->
            val entities=JSONObject(javaClass.classLoader!!.getResourceAsStream("schema-v26.json")!!.reader().readText()).getJSONObject("database").getJSONArray("entities")
            for(i in 0 until entities.length()){
                val entity=entities.getJSONObject(i);val table=entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",table))
                val indices=entity.getJSONArray("indices");for(j in 0 until indices.length())db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))
            }
            db.execSQL("INSERT INTO cities(id,name)VALUES(1,'Город')")
            db.execSQL("INSERT INTO shops(id,name,displayOrder,manualOrderOverride,cityId)VALUES(10,'Первый',7,0,1),(11,'Второй',9,0,1)")
            db.execSQL("INSERT INTO department_types(id,name,normalizedName,displayOrder,accent,iconKey)VALUES(20,'Крупы','крупы',0,'',''),(21,'Другая группа','другая группа',1,'','')")
            db.execSQL("INSERT INTO shop_departments(id,shopId,typeId,displayOrder)VALUES(30,10,20,8),(31,11,21,3)")
            db.execSQL("INSERT INTO products(id,name,purchaseType,isTemporary,stableId,photoUri)VALUES(40,'Один отдел','offline',0,'input300-one','/missing-photo.jpg'),(41,'Разные отделы','offline',0,'input300-two',NULL)")
            db.execSQL("INSERT INTO product_shop_links(id,productId,shopId,departmentId,priority,restrictBranches)VALUES(50,40,10,30,1,0),(51,41,10,30,1,0),(52,41,11,31,2,0)")
            db.execSQL("INSERT INTO shopping_list_entries(id,productId,quantity,unit,isBought,isUrgent,createdAt,updatedAt)VALUES(60,40,2,'шт',1,0,100,200)")
            db.version=26
        }
        val db=Room.databaseBuilder(app,AppDatabase::class.java,name).allowMainThreadQueries().addMigrations(AppDatabase.MIGRATION_26_27,AppDatabase.MIGRATION_27_28,AppDatabase.MIGRATION_28_29).build()
        try{
            assertEquals(29,db.openHelper.writableDatabase.version)
            assertEquals("/missing-photo.jpg",db.productDao().getById(40)!!.photoUri)
            assertEquals(setOf(50L,51L,52L),db.productShopLinkDao().getAllSync().map{it.id}.toSet())
            assertEquals(20L,db.productInputDao().department(40)!!.typeId)
            assertNull(db.productInputDao().department(41));assertNull(db.productInputDao().settings())
            val row=db.shoppingListEntryDao().getById(60)!!;assertTrue(row.isBought);assertEquals(2.0,row.quantity,0.0)
            assertEquals(200L,row.updatedAt)
            db.productInputDao().settings(com.spisokryadom.app.data.entity.ProductInputSettings(prefillEnabled=false,inheritDepartment=false))
            db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
            db.openHelper.writableDatabase.query("PRAGMA integrity_check").use{assertTrue(it.moveToFirst());assertEquals("ok",it.getString(0))}
        }finally{db.close()}
        val reopened=Room.databaseBuilder(app,AppDatabase::class.java,name).allowMainThreadQueries().build()
        try{
            assertFalse(reopened.productInputDao().settings()!!.prefillEnabled)
            assertFalse(reopened.productInputDao().settings()!!.inheritDepartment)
            assertEquals(20L,reopened.productInputDao().department(40)!!.typeId)
            assertEquals("/missing-photo.jpg",reopened.productDao().getById(40)!!.photoUri)
        }finally{reopened.close();app.deleteDatabase(name)}
    }
}
