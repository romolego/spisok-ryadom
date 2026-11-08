package com.spisokryadom.app

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.data.backup.*
import com.spisokryadom.app.ui.addtolist.*
import com.spisokryadom.app.widget.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import java.time.Duration
import java.util.UUID
import java.util.zip.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class WidgetQueue330DataTest {
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:AppDatabase
    private lateinit var places:LocationRepository
    private lateinit var basket:ShoppingListRepository
    private lateinit var repo:BulkPurchaseRepository
    private val models=ViewModelStore()
    private var hideDefault=true
    @Before fun seed():Unit=runBlocking {
        db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build()
        db.cityDao().insert(CityEntity(1,"Город"))
        val prefs=app.getSharedPreferences("queue330-locations",0);prefs.edit().clear().commit()
        app.getSharedPreferences("bulk_product_draft",0).edit().clear().commit()
        places=LocationRepository(db.cityDao(),db.shopDao(),prefs)
        basket=ShoppingListRepository(db.shoppingListEntryDao(),db,places,widgetDefault={hideDefault})
        repo=BulkPurchaseRepository(db,basket,places);WidgetPurchaseMotion.clear()
    }
    @After fun close(){models.clear();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();places.closeForTest();db.close();WidgetPurchaseMotion.clear()}
    private fun waitFor(check:()->Boolean){val until=System.nanoTime()+25_000_000_000;while(!check()&&System.nanoTime()<until){Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(20));Thread.sleep(5)};assertTrue(check())}
    private fun model(handle:SavedStateHandle=SavedStateHandle()):BulkProductViewModel=BulkProductViewModel(repo,handle,revision={"db330"},store=app.getSharedPreferences("bulk_product_draft",0)).also{models.put(UUID.randomUUID().toString(),it)}
    private fun prepared(vm:BulkProductViewModel){vm.prepare();waitFor{vm.state.value.preview&&!vm.state.value.busy}}

    @Test fun eyeOnlyChangesThisPurchaseAndResetsOnCompletionAndNewAddition():Unit=runBlocking {
        val product=db.productDao().insert(ProductEntity(name="Личный",isWidgetPrivate=true))
        val one=basket.addProduct(product)
        val list=db.goalListDao().insert(GoalListEntity(name="Второй",showInPurchases=true))
        val two=basket.addProduct(product,listId=list)
        val data=ShoppingWidgetData(db,basket,places)
        val before=data.rows().single{it.id==one}
        assertTrue(before.hidden);assertTrue(data.visibility(before,true));assertFalse(data.visibility(before,true))
        assertTrue(db.productDao().getById(product)!!.isWidgetPrivate)
        assertFalse(ShoppingWidgetData(db,basket,places).rows().single{it.id==one}.hidden)
        assertTrue(data.rows().single{it.id==two}.hidden)
        basket.addProduct(product) // A quantity merge does not reset a disclosure.
        assertFalse(db.shoppingListEntryDao().getById(one)!!.widgetHidden)
        assertFalse(data.check(before)) // The pre-disclosure frame cannot change the purchase.
        basket.markBought(one);assertTrue(db.shoppingListEntryDao().getById(one)!!.widgetHidden)
        basket.clearBoughtEntries();val next=basket.addProduct(product);assertTrue(db.shoppingListEntryDao().getById(next)!!.widgetHidden)
        hideDefault=false
        basket.markBought(next);basket.clearBoughtEntries();val visible=basket.addProduct(product)
        assertFalse(data.rows().single{it.id==visible}.hidden)
        hideDefault=true;assertFalse(data.rows().single{it.id==visible}.hidden) // Setting is captured at creation.
        hideDefault=false
        assertTrue(data.visibility(data.rows().single{it.id==visible},false))
        assertTrue(data.check(data.rows().single{it.id==visible}))
        assertTrue("Buying must never disclose a concealed name, even with a visible creation default",db.shoppingListEntryDao().getById(visible)!!.widgetHidden)
    }
    @Test fun widgetSettingAndPurchaseDisclosureRoundTripAndOldCopyDefaultsHidden():Unit=runBlocking {
        val prefs=app.getSharedPreferences(WidgetSettings.PREFS,0);prefs.edit().clear().commit()
        WidgetSettings(hidePrivateByDefault=false).save(prefs)
        val p=db.productDao().insert(ProductEntity(name="Личный",isWidgetPrivate=true))
        hideDefault=false;val id=basket.addProduct(p)
        val manager=BackupManager(app,db);val file=manager.exportFile()
        val manifest=ZipFile(file).use{JSONObject(it.getInputStream(it.getEntry("manifest.json")).reader().readText())}
        assertEquals(28,manifest.getInt("version"));assertFalse(manifest.getJSONObject("data").getJSONArray("shoppingListEntries").getJSONObject(0).getBoolean("widgetHidden"))
        manager.importFile(file);assertFalse(db.shoppingListEntryDao().getById(id)!!.widgetHidden);assertFalse(WidgetSettings.load(prefs).hidePrivateByDefault)
        val encoded=BackupJson.encode(db.shoppingListEntryDao().getById(id)!!)
        encoded.remove("widgetHidden");assertTrue(BackupJson.entries(encoded).widgetHidden)
        encoded.put("widgetHidden","false");assertTrue(runCatching{BackupJson.entries(encoded)}.isFailure)
        manager.discard(file)
    }
    @Test fun uncheckedQueueSurvivesFreshModelAndOnlyConfirmedBatchDisappears():Unit=runBlocking {
        val onion=db.productDao().insert(ProductEntity(name="Лук"))
        val a=model();a.text("Лук, Незнакомый товар");a.enqueue();a.text("Морковь. Картофель");a.enqueue()
        assertEquals(2,a.state.value.queue.size);assertTrue(db.shoppingListEntryDao().getAllSync().isEmpty())
        val fresh=model();assertEquals(2,fresh.state.value.queue.size)
        val first=fresh.state.value.queue.first().id
        fresh.openQueued(first);waitFor{fresh.state.value.preview&&!fresh.state.value.busy}
        assertEquals(onion,fresh.state.value.rows[0].productId)
        fresh.selectFor(1,null);fresh.step(0,1);fresh.newName(1,"Свой товар")
        val selected=fresh.state.value.rows;fresh.enqueue()
        val again=model();again.openQueued(first);waitFor{again.state.value.preview&&!again.state.value.busy}
        assertEquals(selected,again.state.value.rows)
        again.add(null,null);waitFor{again.state.value.completed}
        assertEquals(1,again.state.value.queue.size);assertEquals("Морковь. Картофель",again.state.value.queue.single().text)
        assertEquals(2,db.shoppingListEntryDao().getAllSync().size)
        again.consumeCompletion();again.removeQueued(again.state.value.queue.single().id)
        assertTrue(model().state.value.queue.isEmpty())
    }
    @Test fun clearAndZeroRemoveWithoutWritingAndOpeningAnotherBatchKeepsCurrentInput():Unit=runBlocking {
        db.productDao().insert(ProductEntity(name="Лук"))
        val vm=model();vm.text("Лук, Новый");prepared(vm);vm.selectFor(1,null)
        vm.step(0,-1);assertEquals(listOf(1),vm.state.value.rows.map{it.key})
        vm.quantity(1,"0");assertTrue(vm.state.value.rows.isEmpty())
        assertEquals("",vm.state.value.text);assertFalse(vm.state.value.preview)
        vm.clear();assertEquals("",vm.state.value.text);assertFalse(vm.state.value.preview)
        vm.text("Лук");prepared(vm);vm.back();vm.text("")
        assertTrue("Clearing the input must also discard its old preview",vm.state.value.rows.isEmpty())
        vm.text("Первый");vm.enqueue();val first=vm.state.value.queue.single().id
        vm.text("Второй");vm.openQueued(first);waitFor{!vm.state.value.busy}
        assertEquals(2,vm.state.value.queue.size);assertTrue(vm.state.value.queue.any{it.text=="Второй"})
        vm.clear();assertEquals(1,vm.state.value.queue.size)
        assertTrue(db.shoppingListEntryDao().getAllSync().isEmpty());assertEquals(1,db.productDao().getAllSync().size)
    }
    @Test fun retryAfterCommitBeforeQueueCleanupNeverAddsTwice():Unit=runBlocking {
        val p=db.productDao().insert(ProductEntity(name="Лук"));val vm=model();vm.text("Лук");prepared(vm);vm.enqueue()
        val batch=vm.state.value.queue.single();val j=JSONObject(batch.draft)
        val rows=listOf(BulkProductRow(0,"Лук","Лук",p,db.productDao().getById(p)!!.stableId,true,"1"))
        assertTrue(repo.add(batch.token,rows,batch.city,ShopChoice.NONE,null,null,true,"",ProductEditorTransactions.generation.get()))
        val fresh=model();waitFor{fresh.state.value.queue.isEmpty()}
        assertFalse(repo.add(batch.token,rows,batch.city,ShopChoice.NONE,null,null,true,"",ProductEditorTransactions.generation.get()))
        assertTrue(fresh.state.value.queue.isEmpty());assertEquals(1.0,db.shoppingListEntryDao().getAllSync().single().quantity,0.0)
        assertEquals("Лук",j.getString("text"))
    }
    @Test fun schema29UpgradeKeepsIdsAndInitializesOnlyNewVisibility():Unit=runBlocking {
        val name="queue330-${UUID.randomUUID()}.db"
        app.openOrCreateDatabase(name,0,null).use{old->
            val entities=JSONObject(javaClass.classLoader!!.getResourceAsStream("schema-v29.json")!!.reader().readText()).getJSONObject("database").getJSONArray("entities")
            for(i in 0 until entities.length()){
                val e=entities.getJSONObject(i);val table=e.getString("tableName")
                old.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",table))
                val indices=e.getJSONArray("indices");for(k in 0 until indices.length())old.execSQL(indices.getJSONObject(k).getString("createSql").replace("\${TABLE_NAME}",table))
            }
            old.execSQL("INSERT INTO cities(id,name) VALUES(1,'Город')")
            old.execSQL("INSERT INTO products(id,name,purchaseType,isTemporary,stableId,isWidgetPrivate)VALUES(71,'Старый','offline',0,'old330',1)")
            old.execSQL("INSERT INTO shopping_list_entries(id,productId,quantity,unit,isBought,isUrgent,createdAt,updatedAt)VALUES(81,71,3,'шт',0,0,100,200)")
            old.version=29
        }
        val migrated=Room.databaseBuilder(app,AppDatabase::class.java,name).allowMainThreadQueries().addMigrations(AppDatabase.MIGRATION_29_30).build()
        try{
            assertEquals(30,migrated.openHelper.writableDatabase.version)
            val row=migrated.shoppingListEntryDao().getById(81)!!
            assertEquals(71L,row.productId);assertEquals(3.0,row.quantity,0.0);assertEquals(100L,row.createdAt);assertEquals(200L,row.updatedAt);assertTrue(row.widgetHidden)
            assertEquals("old330",migrated.productDao().getById(71)!!.stableId)
            migrated.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
        }finally{migrated.close();app.deleteDatabase(name)}
    }
    @Test fun fastBatchKeepsDepartedRowsHiddenUntilSingleReorder(){
        val settings=WidgetSettings();val a=WidgetPurchase(1,"a",1,1,false,"А","1",false,"a")
        val b=WidgetPurchase(2,"b",2,2,false,"Б","1",false,"b")
        val before=listOf(WidgetItem.Purchase(a),WidgetItem.Purchase(b))
        WidgetPurchaseMotion.present(1,"db",settings,before,before,0)
        WidgetPurchaseMotion.begin(a,"db",0)
        val afterA=listOf(WidgetItem.Purchase(b),WidgetItem.Purchase(a.copy(bought=true,revision="a2")))
        assertEquals(setOf(1L),WidgetPurchaseMotion.present(1,"db",settings,afterA,afterA,20).second)
        WidgetPurchaseMotion.begin(b,"db",100)
        val afterBoth=listOf(WidgetItem.Purchase(a.copy(bought=true,revision="a2")),WidgetItem.Purchase(b.copy(bought=true,revision="b2")))
        assertEquals(setOf(1L,2L),WidgetPurchaseMotion.present(1,"db",settings,afterBoth,afterBoth,190).second)
        assertEquals(emptySet<Long>(),WidgetPurchaseMotion.present(1,"db",settings,afterBoth,afterBoth,281).second)
        assertEquals(afterBoth,WidgetPurchaseMotion.present(1,"new-db",settings,afterBoth,afterBoth,282).first)
    }
}

