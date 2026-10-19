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
import kotlinx.coroutines.runBlocking
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
class GoalListWidgetTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: AppDatabase
    private lateinit var location: LocationRepository
    private lateinit var shopping: ShoppingListRepository
    private lateinit var goals: GoalListRepository
    private lateinit var widget: ShoppingWidgetData
    private var listId = 0L

    @Before fun setup() = runBlocking {
        val prefs = app.getSharedPreferences("goal-list-widget", 0)
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
    private suspend fun add(name: String, shop: String? = null, address: String? = null) = goals.addQuick(listId, name, 2.0, "шт", 150.0, shop, address)
    private fun headings(items: List<WidgetItem>) = items.filterIsInstance<WidgetItem.Header>()
    private fun sections(items: List<WidgetItem>): Map<String, List<Long>> {
        val result = linkedMapOf<String, MutableList<Long>>()
        var current = ""
        items.forEach { item -> when (item) {
            is WidgetItem.Header -> { current = item.key; result.getOrPut(current) { mutableListOf() } }
            is WidgetItem.Purchase -> result.getOrPut(current) { mutableListOf() }.add(item.row.id)
        } }
        return result
    }

    @Test fun portableStoresAddressesAndUnassignedPurchasesAreSeparateWidgetSections() = runBlocking {
        val first = add("Огурцы", "Батон", "Весны, 16а")
        val samePlace = add("Помидоры", "Батон", "Весны, 16а")
        val otherAddress = add("Хлеб", "Батон", "Весны, 20")
        val online = add("Видеокарта", "Авито")
        val nowhere = add("Подарок")
        val items = widget.presentation(WidgetSettings())
        val headers = headings(items)
        assertEquals(4, headers.size)
        val original = headers.single { it.title == "Батон" && it.subtitle == "Весны, 16а" }
        val second = headers.single { it.title == "Батон" && it.subtitle == "Весны, 20" }
        assertEquals(setOf(first, samePlace), sections(items).getValue(original.key).toSet())
        assertEquals(listOf(otherAddress), sections(items).getValue(second.key))
        assertEquals(listOf(online), sections(items).getValue(headers.single { it.title == "Авито" }.key))
        assertEquals(listOf(nowhere), sections(items).getValue(headers.single { it.title == "Без магазина" }.key))
        assertEquals("Без магазина", headers.last().title)
        assertEquals(headers.size, headers.map { it.stableId }.distinct().size)
    }

    @Test fun addressOnlyAndRepeatedCaptionsRenderOnceAndRowsHavePortableFallback() = runBlocking {
        val addressOnly = add("Лампа", address = "Пункт выдачи, 8")
        val repeated = add("Сыр", "29 павильон (сыры)", "29 павильон (сыры)")
        val before = db.shoppingListEntryDao().getAllSync()
        val items = widget.presentation(WidgetSettings())
        val headers = headings(items)
        assertNull(headers.single { it.title == "Пункт выдачи, 8" }.subtitle)
        assertNull(headers.single { it.title == "29 павильон (сыры)" }.subtitle)
        val rawRows = widget.rows().associateBy { it.id }
        assertEquals("2 шт · Пункт выдачи, 8", rawRows.getValue(addressOnly).detail)
        assertEquals("2 шт · 29 павильон (сыры)", rawRows.getValue(repeated).detail)
        assertTrue(items.filterIsInstance<WidgetItem.Purchase>().all { it.row.detail == "2 шт" })
        assertEquals(before, db.shoppingListEntryDao().getAllSync())
    }

    @Test fun portableHintDoesNotMergeIntoLocalShopWithIdenticalCaption() = runBlocking {
        val shop = db.shopDao().insert(ShopEntity(name = "Батон"))
        val branch = db.shopBranchDao().insert(ShopBranchEntity(shopId = shop, address = "Весны, 16а"))
        val product = db.productDao().insert(ProductEntity(name = "Молоко", defaultUnit = "шт"))
        db.productShopLinkDao().insert(ProductShopLinkEntity(productId = product, shopId = shop))
        val real = shopping.addProduct(product, choice = ShopChoice.EXPLICIT, shopId = shop, branchId = branch)
        val portable = add("Огурцы", "Батон", "Весны, 16а")
        val items = widget.presentation(WidgetSettings())
        val headers = headings(items)
        assertEquals(2, headers.size)
        val assigned = headers.single { it.key == "place:$shop:$branch" }
        val hint = headers.single { it.key.startsWith("hint:") }
        assertEquals("Батон", assigned.title)
        assertEquals(assigned.title, hint.title)
        assertEquals(assigned.subtitle, hint.subtitle)
        assertEquals(listOf(real), sections(items).getValue(assigned.key))
        assertEquals(listOf(portable), sections(items).getValue(hint.key))
        assertNotEquals(assigned.stableId, hint.stableId)
    }

    @Test fun hintIdentityIsDelimiterSafeStableAcrossPurchaseChangesAndOtherModesStayUnchanged() = runBlocking {
        val first = add("Один", "A|B", "C")
        add("Два", "A", "B|C")
        val initial = headings(widget.presentation(WidgetSettings())).associate { it.key to it.stableId }
        assertEquals(2, initial.size)
        goals.setBought(first, true)
        shopping.updateQuantity(first, 3.0)
        val changed = headings(widget.presentation(WidgetSettings())).associate { it.key to it.stableId }
        assertEquals(initial, changed)
        assertTrue(headings(widget.presentation(WidgetSettings(ViewMode.FLAT))).isEmpty())
        assertEquals(listOf("Без группы"), headings(widget.presentation(WidgetSettings(ViewMode.BY_GROUPS))).map { it.title })
        assertEquals(listOf("Без получателя"), headings(widget.presentation(WidgetSettings(ViewMode.BY_RECIPIENTS))).map { it.title })
        assertEquals(2, widget.presentation(WidgetSettings(ViewMode.FLAT)).filterIsInstance<WidgetItem.Purchase>().size)
    }

    @Test fun hiddenOrDeletedTargetDoesNotLeakPortableHeadingsAndRejectsStaleWidgetAction() = runBlocking {
        val row = add("Огурцы", "Батон", "Весны, 16а")
        val token = widget.rows().single { it.id == row }
        assertTrue(headings(widget.presentation(WidgetSettings())).any { it.title == "Батон" })
        goals.updateList(listId, "Праздник", false)
        assertTrue(widget.rows().isEmpty())
        assertTrue(widget.presentation(WidgetSettings()).isEmpty())
        assertFalse(widget.check(token))
        assertFalse(db.shoppingListEntryDao().getById(row)!!.isBought)
        goals.deleteList(listId)
        assertFalse(widget.check(token))
    }
}
