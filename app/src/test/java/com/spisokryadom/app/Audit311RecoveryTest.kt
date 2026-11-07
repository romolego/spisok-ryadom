package com.spisokryadom.app

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

/** Exercise the production open factory, rather than calling recovery with a test target. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class Audit311RecoveryTest {
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    private var opened:AppDatabase?=null
    @Before fun reset(){org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null)}
    @After fun close(){opened?.close();org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null);app.deleteDatabase("spisok_ryadom.db")}
    @Test fun productionOpenPreservesSchema27MediaAndPreferencesBeforeMigration28():Unit=runBlocking {
        val photo=File(app.filesDir,"product_photos/audit311.jpg").also{it.parentFile!!.mkdirs();it.writeBytes(byteArrayOf(7,3,1,1))}
        app.getSharedPreferences("shopping_list_prefs",0).edit().putBoolean("widget_show_bought",false).commit()
        app.openOrCreateDatabase("spisok_ryadom.db",0,null).use{old->
            val entities=JSONObject(javaClass.classLoader!!.getResourceAsStream("schema-v27.json")!!.reader().readText()).getJSONObject("database").getJSONArray("entities")
            for(i in 0 until entities.length()){
                val entity=entities.getJSONObject(i);val table=entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",table))
                val indices=entity.getJSONArray("indices")
                for(j in 0 until indices.length())old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))
            }
            old.execSQL("INSERT INTO cities(id,name)VALUES(1,'Город')")
            old.execSQL("INSERT INTO products(id,name,purchaseType,isTemporary,stableId,photoUri)VALUES(31,'Товар до обновления','offline',0,'audit311-original',?)",arrayOf(photo.absolutePath))
            old.execSQL("INSERT INTO product_default_departments(productId,typeId)VALUES(31,NULL)")
            old.execSQL("INSERT INTO shopping_list_entries(id,productId,quantity,unit,isBought,isUrgent,createdAt,updatedAt)VALUES(41,31,3,'шт',0,0,100,200)")
            old.version=27
        }
        val recovery=File(app.filesDir,"recovery/schema-27-before-28")
        val db=AppDatabase.getInstance(app).also{opened=it}
        assertFalse("Construction must not copy data on the main thread",recovery.exists())
        withContext(Dispatchers.IO){
            assertEquals(28,db.openHelper.writableDatabase.version)
            assertFalse(db.productDao().getById(31)!!.isWidgetPrivate)
            assertEquals("audit311-original",db.productDao().getById(31)!!.stableId)
            assertEquals(3.0,db.shoppingListEntryDao().getById(41)!!.quantity,0.0)
            assertNotNull(db.productInputDao().department(31))
        }
        assertTrue("The production 27→28 upgrade must preserve its recovery checkpoint",File(recovery,"READY").isFile)
        assertArrayEquals(photo.readBytes(),File(recovery,"product_photos/audit311.jpg").readBytes())
        assertTrue(File(recovery,"shared_prefs/shopping_list_prefs.xml").isFile)
        SQLiteDatabase.openDatabase(File(recovery,"spisok_ryadom.db").path,null,SQLiteDatabase.OPEN_READONLY).use{old->
            assertEquals(27,old.version)
            old.rawQuery("SELECT stableId FROM products WHERE id=31",null).use{assertTrue(it.moveToFirst());assertEquals("audit311-original",it.getString(0))}
        }
    }
}
