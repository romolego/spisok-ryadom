package com.spisokryadom.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.db.RetailConstraints
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.ui.shoppinglist.ViewMode
import com.spisokryadom.app.widget.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class Audit151CrosscutTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: AppDatabase
    private lateinit var location: LocationRepository
    private lateinit var shopping: ShoppingListRepository
    private lateinit var goals: GoalListRepository
    private lateinit var widget: ShoppingWidgetData
    private var listId = 0L

    @Before fun setup() = runBlocking {
        val prefs = app.getSharedPreferences("audit151-crosscut", 0)
        prefs.edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        RetailConstraints.install(db.openHelper.writableDatabase)
        db.cityDao().insert(CityEntity(1, "Город"))
        location = LocationRepository(db.cityDao(), db.shopDao(), prefs)
        shopping = ShoppingListRepository(db.shoppingListEntryDao(), db, location)
        goals = GoalListRepository(db, shopping)
        widget = ShoppingWidgetData(db, shopping, location)
        listId = goals.create("Праздник")
        goals.updateList(listId, "Праздник", true)
    }

    @After fun cleanup() { location.closeForTest(); db.close() }

    @Test fun staleSingleAggregateActionsCannotMutateNewlyHiddenGoal() = runBlocking {
        val id = goals.addQuick(listId, "Подарок", 2.0, "шт", 125.0)
        goals.updateList(listId, "Праздник", false)
        val before = db.shoppingListEntryDao().getById(id)!!
        val actions: List<suspend () -> Unit> = listOf(
            { shopping.markBought(id, strictVisible = true) },
            { shopping.markNotBought(id, strictVisible = true) },
            { shopping.adjustQuantity(id, 1.0, strictVisible = true) },
            { shopping.adjustByDefaultStep(id, 1, strictVisible = true) },
            { shopping.updatePurchaseDetails(id, 9.0, "stale", "stale", strictVisible = true) },
            { shopping.changeShop(id, null, null, strictVisible = true) },
            { shopping.toggleUrgent(id, strictPending = true) },
            { shopping.removeEntry(id, strictVisible = true) }
        )
        actions.forEach { action ->
            try { action(); fail("A hidden target purchase must reject the stale aggregate action") }
            catch (_: IllegalArgumentException) { }
            assertEquals(before, db.shoppingListEntryDao().getById(id))
        }
        assertTrue(widget.rows().isEmpty())
        assertTrue(shopping.textSnapshot().entries.isEmpty())
    }

    @Test fun visibleAggregateAndWidgetStillOperateOnTheSameGoalPurchase() = runBlocking {
        val id = goals.addQuick(listId, "Огурцы", 2.0, "кг", 120.0)
        shopping.adjustByDefaultStep(id, 1, strictVisible = true)
        assertEquals(3.0, goals.snapshot(listId).entries.single().quantity, 0.0)
        val row = widget.rows().single()
        assertEquals(id, row.id)
        assertTrue(widget.check(row))
        assertTrue(goals.snapshot(listId).entries.single().isBought)
        shopping.markNotBought(id, strictVisible = true)
        shopping.updatePurchaseDetails(id, 4.0, "Для салата", "Спелые", strictVisible = true)
        val result = goals.snapshot(listId).entries.single()
        assertFalse(result.isBought)
        assertEquals(4.0, result.quantity, 0.0)
        assertEquals(120.0, result.plannedUnitPrice!!, 0.0)
        assertEquals("Для салата", result.note)
        assertEquals("Спелые", result.comment)
        shopping.removeEntry(id, strictVisible = true)
        assertTrue(goals.snapshot(listId).entries.isEmpty())
        assertTrue(db.productDao().getAllSync().isEmpty())
    }

    @Test fun staleCatalogueControlsCannotEditPurchaseMovedIntoVisibleOrHiddenTarget() = runBlocking {
        val product = db.productDao().insert(ProductEntity(name = "Сок", defaultUnit = "л"))
        val id = shopping.addProduct(product, choice = ShopChoice.NONE)
        goals.moveEntriesExpected(mapOf(id to null), listId)
        for (visible in listOf(true, false)) {
            goals.updateList(listId, "Праздник", visible)
            val before = db.shoppingListEntryDao().getById(id)!!
            val actions: List<suspend () -> Unit> = listOf(
                { shopping.adjustByDefaultStep(id, 1, regularOnly = true) },
                { shopping.updateQuantity(id, 8.0, regularOnly = true) },
                { shopping.removeEntry(id, regularOnly = true) }
            )
            actions.forEach { action ->
                try { action(); fail("A target purchase must reject stale catalogue controls") }
                catch (_: IllegalArgumentException) { }
                assertEquals(before, db.shoppingListEntryDao().getById(id))
            }
        }
        goals.moveEntriesFrom(listId, setOf(id), null)
        shopping.updateQuantity(id, 3.0, regularOnly = true)
        shopping.adjustByDefaultStep(id, 1, regularOnly = true)
        assertEquals(4.0, db.shoppingListEntryDao().getById(id)!!.quantity, 0.0)
        shopping.removeEntry(id, regularOnly = true)
        assertNull(db.shoppingListEntryDao().getById(id))
    }

    @Test fun lastTargetUnitSurvivesMinusInAggregateAsInItsOwnList() = runBlocking {
        val id = goals.addQuick(listId, "Процессор", 1.0, "шт", 20_000.0)
        val before = goals.snapshot(listId).entries.single()
        shopping.adjustByDefaultStep(id, -1, strictVisible = true)
        assertEquals(before, goals.snapshot(listId).entries.single())
        shopping.adjustByDefaultStep(id, 1, strictVisible = true)
        shopping.adjustByDefaultStep(id, -1, strictVisible = true)
        assertEquals(1.0, goals.snapshot(listId).entries.single().quantity, 0.0)
        val regular = shopping.addNewProduct("Обычный", 1.0, "шт", false, ShopChoice.NONE)
        shopping.adjustByDefaultStep(regular, -1, strictVisible = true)
        assertNull(db.shoppingListEntryDao().getById(regular))
    }

    @Test fun queuedCommandCannotTouchRowsAfterDatabaseGenerationChanged() = runBlocking {
        val id = goals.addQuick(listId, "До восстановления", 2.0, "шт", 100.0)
        val previous = ProductEditorTransactions.generation.get()
        ProductEditorTransactions.mutex.lock()
        val waiting = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { ProductEditorTransactions.inGeneration(previous) {
                shopping.updatePurchaseDetails(id, 9.0, "Старое окно", null, strictVisible = true)
            } }
        }
        try {
            assertFalse(waiting.isCompleted)
            // Full restore holds this mutex and changes generation after replacement.
            val row = db.shoppingListEntryDao().getById(id)!!
            db.shoppingListEntryDao().update(row.copy(quantity = 5.0, note = "Восстановлено"))
            ProductEditorTransactions.generation.incrementAndGet()
        } finally { ProductEditorTransactions.mutex.unlock() }
        val result = waiting.await()
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals(5.0, db.shoppingListEntryDao().getById(id)!!.quantity, 0.0)
        assertEquals("Восстановлено", db.shoppingListEntryDao().getById(id)!!.note)
        ProductEditorTransactions.inGeneration(ProductEditorTransactions.generation.get()) {
            shopping.updatePurchaseDetails(id, 6.0, "Новое окно", null, strictVisible = true)
        }
        assertEquals(6.0, db.shoppingListEntryDao().getById(id)!!.quantity, 0.0)
    }

    @Test fun catalogueQuantityDialogCannotRewriteOrDeleteNewlyBoughtRegularRow() = runBlocking {
        val id = shopping.addNewProduct("Сок", 2.0, "л", false, ShopChoice.NONE)
        shopping.markBought(id)
        val before = db.shoppingListEntryDao().getById(id)!!
        val actions: List<suspend () -> Unit> = listOf(
            { shopping.updateQuantity(id, 7.0, regularOnly = true) },
            { shopping.removeEntry(id, regularOnly = true) }
        )
        actions.forEach { action ->
            try { action(); fail("Stale catalogue dialog must not change a bought purchase") }
            catch (_: IllegalArgumentException) { }
            assertEquals(before, db.shoppingListEntryDao().getById(id))
        }
        shopping.markNotBought(id)
        shopping.updateQuantity(id, 3.0, regularOnly = true)
        assertEquals(3.0, db.shoppingListEntryDao().getById(id)!!.quantity, 0.0)
    }
}
