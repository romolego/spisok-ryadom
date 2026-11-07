package com.spisokryadom.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.backup.BackupJson
import com.spisokryadom.app.data.backup.BackupManager
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.db.RetailConstraints
import com.spisokryadom.app.data.entity.CityEntity
import com.spisokryadom.app.data.entity.ProductEntity
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class Audit311BackupTest {
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:AppDatabase
    private val privateProduct=ProductEntity(name="Личный товар",isWidgetPrivate=true)
    @Before fun seed():Unit=runBlocking {
        db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build()
        RetailConstraints.install(db.openHelper.writableDatabase)
        db.cityDao().insert(CityEntity(1,"Город"))
    }
    @After fun close(){db.close()}
    private fun row(id:Long,secret:Boolean,stable:String?=null)=BackupJson.encode(privateProduct.copy(id=id,isWidgetPrivate=secret)).apply {
        if(stable==null)remove("stableId")else put("stableId",stable)
    }
    private suspend fun merge(vararg products:JSONObject){
        val root=JSONObject().put("version",1).put("data",JSONObject().put("products",JSONArray(products.toList())))
        val file=File(app.cacheDir,"audit311-legacy.json");file.writeText(root.toString())
        BackupManager(app,db).importFile(file)
    }
    @Test fun weakPrivacyMatchesDoNotDependOnIncomingOrder():Unit=runBlocking {
        for(order in listOf(listOf(false,true),listOf(true,false))){
            db.productDao().deleteAll()
            val original=db.productDao().insert(privateProduct)
            merge(*order.mapIndexed{i,secret->row(100L+i,secret)}.toTypedArray())
            val result=db.productDao().getAllSync()
            assertEquals("An incompatible first row must leave the private candidate for the next row",2,result.size)
            assertEquals(1,result.count{it.isWidgetPrivate});assertEquals(1,result.count{!it.isWidgetPrivate})
            assertEquals(privateProduct.copy(id=original),db.productDao().getById(original))
        }
    }
    @Test fun weakMatchesConsumeEachOriginalAtMostOnceAndPreserveSourceMultiplicity():Unit=runBlocking {
        val original=db.productDao().insert(privateProduct)
        merge(row(100,true),row(101,true))
        val result=db.productDao().getAllSync()
        assertEquals(2,result.size);assertTrue(result.all{it.isWidgetPrivate})
        assertEquals(2,result.map{it.stableId}.distinct().size)
        assertEquals(privateProduct.copy(id=original),db.productDao().getById(original))
    }
    @Test fun laterIdentityMatchRemainsReservedAgainstEarlierWeakMatches():Unit=runBlocking {
        val original=db.productDao().insert(privateProduct)
        val identity=row(101,false,privateProduct.stableId).put("note","Обновлено по идентичности")
        merge(row(100,true),identity)
        val result=db.productDao().getAllSync()
        assertEquals(2,result.size)
        assertFalse(db.productDao().getById(original)!!.isWidgetPrivate)
        assertEquals("Обновлено по идентичности",db.productDao().getById(original)!!.note)
        assertTrue(result.single{it.id!=original}.isWidgetPrivate)
    }
}
