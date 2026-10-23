package com.spisokryadom.app

import android.app.Application
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.backup.BackupManager
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.db.RetailConstraints
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.ui.shoppinglist.ViewMode
import com.spisokryadom.app.util.FavoriteStyle
import com.spisokryadom.app.widget.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Native Room integration: widget presentation must never become another basket owner. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class Widget260DataTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val prefs get() = app.getSharedPreferences(WidgetSettings.PREFS, 0)
    private lateinit var db: AppDatabase
    private lateinit var locations: LocationRepository
    private lateinit var basket: ShoppingListRepository
    private lateinit var goals: GoalListRepository
    private lateinit var widget: ShoppingWidgetData
    private val files = mutableListOf<File>()
    private var shopA = 0L
    private var shopB = 0L
    private var visibleList = 0L
    private var hiddenList = 0L
    private var first = 0L
    private var second = 0L
    private var otherOwner = 0L
    private var boughtDepartment = 0L
    private var boughtVenue = 0L
    private var visibleItem = 0L
    private var hiddenItem = 0L
    private val pending get() = setOf(first, second, otherOwner, visibleItem)
    private val visible get() = pending + boughtDepartment + boughtVenue
    private val newBooleanKeys get() = listOf(
        WidgetSettings.SHOW_BOUGHT, WidgetSettings.BOUGHT_TOGGLE,
        WidgetSettings.SHOP_BACKGROUND, WidgetSettings.PRODUCT_BACKGROUND
    )

    @Before fun setup() = runBlocking {
        listOf(WidgetSettings.PREFS, "location_prefs", "product_db_prefs", "widget260-location").forEach {
            app.getSharedPreferences(it, 0).edit().clear().commit()
        }
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        RetailConstraints.install(db.openHelper.writableDatabase)
        db.cityDao().insert(CityEntity(1, "Красноярск"))
        locations = LocationRepository(db.cityDao(), db.shopDao(), app.getSharedPreferences("widget260-location", 0))
        basket = ShoppingListRepository(db.shoppingListEntryDao(), db, locations)
        goals = GoalListRepository(db, basket)
        widget = ShoppingWidgetData(db, basket, locations)
        val activeVenue = db.retailStructureDao().insertVenue(VenueEntity(cityId = 1, name = "ТЦ действующий", address = "Общий адрес, 1"))
        val retiredVenue = db.retailStructureDao().insertVenue(VenueEntity(cityId = 1, name = "ТЦ только купленное", address = "Общий адрес, 2"))
        shopA = db.shopDao().insert(ShopEntity(name = "Сеть А", shoppingBackground = "#E8A0C0", purchaseBackground = "auto"))
        shopB = db.shopDao().insert(ShopEntity(name = "Магазин Б", shoppingBackground = "#75B5E5", purchaseBackground = "#82CF9B"))
        val shopC = db.shopDao().insert(ShopEntity(name = "Магазин только купленное"))
        val a1 = db.shopBranchDao().insert(ShopBranchEntity(shopId = shopA, address = "", label = "Первый этаж", venueId = activeVenue))
        val a2 = db.shopBranchDao().insert(ShopBranchEntity(shopId = shopA, address = "Самостоятельный адрес, 5"))
        val b1 = db.shopBranchDao().insert(ShopBranchEntity(shopId = shopB, address = "Другой адрес, 7"))
        val c1 = db.shopBranchDao().insert(ShopBranchEntity(shopId = shopC, address = "", label = "Павильон 9", venueId = retiredVenue))
        val dairy = db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId = shopA, name = "Молочка"))
        val completedDept = db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId = shopA, name = "Отдел только купленное"))
        val food = db.productGroupDao().insert(ProductGroupEntity(name = "Продукты"))
        val completedGroup = db.productGroupDao().insert(ProductGroupEntity(name = "Группа только купленное"))
        val hiddenGroup = db.productGroupDao().insert(ProductGroupEntity(name = "Группа скрытого списка"))
        val me = db.recipientDao().insert(RecipientEntity(name = "Рома"))
        val completedRecipient = db.recipientDao().insert(RecipientEntity(name = "Получатель только купленное"))
        val hiddenRecipient = db.recipientDao().insert(RecipientEntity(name = "Получатель скрытого списка"))
        visibleList = goals.create("Рецепт на Главной")
        goals.updateList(visibleList, "Рецепт на Главной", true)
        hiddenList = goals.create("Отдельный рецепт")
        suspend fun item(name: String, shop: Long? = null, branch: Long? = null, department: Long? = null,
                         group: Long? = food, recipient: Long? = me, bought: Boolean = false, list: Long? = null): Long {
            val product = db.productDao().insert(ProductEntity(name = name, defaultUnit = "шт", productGroupId = group, recipientId = recipient))
            if (shop != null) db.productShopLinkDao().insert(ProductShopLinkEntity(productId = product, shopId = shop, departmentId = department))
            return db.shoppingListEntryDao().insert(ShoppingListEntryEntity(productId = product, quantity = 2.0, unit = "шт",
                assignedShopId = shop, assignedBranchId = branch, assignedDepartmentId = department,
                isBought = bought, listId = list, explicitNoShop = shop == null))
        }
        first = item("Молоко в ТЦ", shopA, a1, dairy)
        second = item("Молоко по адресу", shopA, a2, dairy)
        otherOwner = item("Вода", shopB, b1)
        boughtDepartment = item("Крупа куплена", shopA, a1, completedDept, completedGroup, completedRecipient, bought = true)
        boughtVenue = item("Лампа куплена", shopC, c1, group = completedGroup, recipient = completedRecipient, bought = true)
        visibleItem = item("Лук из рецепта", list = visibleList)
        hiddenItem = item("Скрытые грибы", group = hiddenGroup, recipient = hiddenRecipient, list = hiddenList)
    }

    @After fun close() {
        locations.closeForTest()
        db.close()
        files.forEach { it.delete() }
    }

    private fun rows(items: List<WidgetItem>) = items.filterIsInstance<WidgetItem.Purchase>()
    private fun headers(items: List<WidgetItem>) = items.filterIsInstance<WidgetItem.Header>()
    private suspend fun snapshot(): List<Any> = listOf(
        db.productDao().getAllSync(), db.shopDao().getAllSync(), db.shopBranchDao().getAll(),
        db.shoppingListEntryDao().getAllSync(), db.goalListDao().getAllSync(),
        db.productShopLinkDao().getAllSync(), db.shopDepartmentDao().getAllSync()
    )
    private fun manifest(file: File) = ZipFile(file).use { zip ->
        JSONObject(zip.getInputStream(zip.getEntry("manifest.json")).reader(Charsets.UTF_8).readText())
    }
    private fun archive(root: JSONObject) = File(app.cacheDir, "widget260-${UUID.randomUUID()}.zip").also { file ->
        files += file
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(root.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    @Test fun widgetPreferencesKeepOldDefaultsAndRecoverFromWrongLocalTypes() {
        prefs.edit().putString("shopping_list_view_mode", ViewMode.FLAT.name).commit()
        val defaults = WidgetSettings.load(prefs)
        assertEquals(WidgetSettings(), defaults)
        assertTrue(defaults.showBought)
        assertTrue(defaults.showBoughtToggle)
        assertFalse(defaults.showShopBackground)
        assertFalse(defaults.showProductBackground)
        val selected = WidgetSettings(ViewMode.BY_GROUPS, false, 14,
            showBought = false, showBoughtToggle = false, showShopBackground = true, showProductBackground = true)
        selected.save(prefs)
        assertEquals(selected, WidgetSettings.load(app.getSharedPreferences(WidgetSettings.PREFS, 0)))
        assertEquals(ViewMode.FLAT.name, prefs.getString("shopping_list_view_mode", null))
        newBooleanKeys.forEach { key -> prefs.edit().putString(key, "false").commit() }
        val recovered = WidgetSettings.load(prefs)
        assertTrue(recovered.showBought)
        assertTrue(recovered.showBoughtToggle)
        assertFalse(recovered.showShopBackground)
        assertFalse(recovered.showProductBackground)
        assertEquals(ViewMode.BY_GROUPS, recovered.mode)
        assertEquals(14, recovered.textSize)
        assertFalse(recovered.showDepartments)
    }

    @Test fun hidingBoughtFiltersBeforeEveryGroupingWithoutChangingAnyBasketOrOwner() = runBlocking {
        val before = snapshot()
        prefs.edit().putString("shopping_list_view_mode", ViewMode.BY_RECIPIENTS.name).commit()
        for (mode in ViewMode.entries) {
            val hidden = widget.presentation(WidgetSettings(mode = mode, showBought = false))
            assertEquals("Pending scope for $mode", pending, rows(hidden).map { it.row.id }.toSet())
            assertEquals(pending.size, rows(hidden).size)
            assertTrue(rows(hidden).none { it.row.bought })
            val captions = headers(hidden).map { it.title }
            assertFalse(captions.any { "только купленное" in it || "скрытого списка" in it })
            if (mode == ViewMode.FLAT) assertTrue(headers(hidden).isEmpty())
            val shown = widget.presentation(WidgetSettings(mode = mode, showBought = true))
            assertEquals(visible, rows(shown).map { it.row.id }.toSet())
            assertEquals(visible.size, rows(shown).size)
            assertFalse(rows(shown).any { it.row.id == hiddenItem })
        }
        assertFalse(headers(widget.presentation(WidgetSettings(showBought = false, showDepartments = false))).any { it.depth == 2 })
        assertEquals("Raw widget data still includes bought history", visible, widget.rows().map { it.id }.toSet())
        assertEquals(visible, db.shoppingListEntryDao().getVisibleSync().map { it.id }.toSet())
        assertEquals(before, snapshot())
        assertEquals(ViewMode.BY_RECIPIENTS.name, prefs.getString("shopping_list_view_mode", null))
        // A list being hidden is independent of widget bought visibility.
        goals.updateList(visibleList, "Рецепт на Главной", false)
        assertEquals(pending - visibleItem, rows(widget.presentation(WidgetSettings(showBought = false))).map { it.row.id }.toSet())
        assertNotNull(db.shoppingListEntryDao().getById(visibleItem))
        for (id in pending - visibleItem) basket.markBought(id)
        for (mode in ViewMode.entries) {
            assertTrue("All-bought $mode must have no orphan headers", widget.presentation(WidgetSettings(mode = mode, showBought = false)).isEmpty())
        }
    }

    @Test fun filteredAndRestyledFramesStillUseFreshIdempotentPurchaseTokens() = runBlocking {
        val token = rows(widget.presentation(WidgetSettings(showBought = false))).single { it.row.id == first }.row
        val restyled = rows(widget.presentation(WidgetSettings(ViewMode.BY_GROUPS, showBought = false,
            showShopBackground = true, showProductBackground = true))).single { it.row.id == first }.row
        assertEquals(token, restyled)
        assertTrue(widget.check(token))
        assertFalse(widget.check(token))
        assertFalse(rows(widget.presentation(WidgetSettings(showBought = false))).any { it.row.id == first })
        assertTrue(db.shoppingListEntryDao().getById(first)!!.isBought)
        val bought = rows(widget.presentation(WidgetSettings(showBought = true))).single { it.row.id == first }.row
        assertTrue(widget.check(bought))
        val staleQuantity = widget.rows().single { it.id == first }
        basket.updateQuantity(first, 7.0)
        assertFalse(widget.check(staleQuantity))
        assertEquals(7.0, db.shoppingListEntryDao().getById(first)!!.quantity, 0.0)
        assertFalse(db.shoppingListEntryDao().getById(first)!!.isBought)
        val listToken = widget.rows().single { it.id == visibleItem }
        goals.updateList(visibleList, "Рецепт на Главной", false)
        assertFalse(widget.check(listToken))
        assertFalse(db.shoppingListEntryDao().getById(visibleItem)!!.isBought)
    }

    @Test fun storeAndProductPresentationAreIndependentAndNeverLeakAcrossOwnersOrModes() = runBlocking {
        val before = snapshot()
        val styled = WidgetSettings(showBought = false, showShopBackground = true, showProductBackground = true)
        val frame = widget.presentation(styled)
        val a1 = rows(frame).single { it.row.id == first }
        val a2 = rows(frame).single { it.row.id == second }
        val b = rows(frame).single { it.row.id == otherOwner }
        val free = rows(frame).single { it.row.id == visibleItem }
        assertEquals("#E8A0C0", a1.blockBackground)
        assertEquals(a1.blockBackground, a2.blockBackground)
        assertEquals("#75B5E5", b.blockBackground)
        assertEquals("", free.blockBackground)
        assertEquals("auto", a1.purchaseBackground)
        assertEquals("#82CF9B", b.purchaseBackground)
        val noFavoriteBackground = FavoriteStyle(background = false)
        val aColor = WidgetAppearance.productBackground(a1, styled, noFavoriteBackground)
        assertEquals(aColor, WidgetAppearance.productBackground(a2, styled, noFavoriteBackground))
        assertNotEquals(aColor, WidgetAppearance.productBackground(b, styled, noFavoriteBackground))
        assertNotEquals(Color.TRANSPARENT, WidgetAppearance.shopBackground(a1, styled))
        assertEquals(Color.TRANSPARENT, WidgetAppearance.shopBackground(b, styled.copy(showShopBackground = false)))
        assertEquals(Color.TRANSPARENT, WidgetAppearance.shopBackground(free, styled))
        val productsOff = styled.copy(showProductBackground = false)
        assertEquals(WidgetAppearance.productBackground(a1, productsOff, noFavoriteBackground),
            WidgetAppearance.productBackground(b, productsOff, noFavoriteBackground))
        assertEquals(aColor, WidgetAppearance.productBackground(a1, styled.copy(showShopBackground = false), noFavoriteBackground))
        for (mode in ViewMode.entries.filter { it != ViewMode.BY_SHOPS }) {
            val settings = styled.copy(mode = mode)
            val items = widget.presentation(settings)
            assertTrue(items.all { WidgetAppearance.shopBackground(it, settings) == Color.TRANSPARENT })
            assertEquals(pending, rows(items).map { it.row.id }.toSet())
            assertEquals(aColor, WidgetAppearance.productBackground(rows(items).single { it.row.id == first }, settings, noFavoriteBackground))
        }
        assertEquals(before, snapshot())
    }

    @Test fun fullBackupRoundTripsWidgetOptionsAndOldCopiesUseCompatibleMissingDefaults() = runBlocking {
        val selected = WidgetSettings(ViewMode.BY_RECIPIENTS, false, 12,
            showBought = false, showBoughtToggle = false, showShopBackground = true, showProductBackground = true)
        selected.save(prefs)
        val expected = snapshot()
        val manager = BackupManager(app, db)
        val backup = manager.exportFile().also { files += it }
        val root = manifest(backup)
        assertEquals(23, root.getInt("version"))
        db.shopDao().update(db.shopDao().getById(shopA)!!.copy(shoppingBackground = ""))
        db.shoppingListEntryDao().deleteById(visibleItem)
        WidgetSettings().save(prefs)
        manager.importFile(backup)
        assertEquals(selected, WidgetSettings.load(prefs))
        assertEquals(expected, snapshot())
        root.put("version", 20)
        val oldPrefs = root.getJSONObject("preferences").getJSONObject(WidgetSettings.PREFS)
        newBooleanKeys.forEach { oldPrefs.remove(it) }
        manager.importFile(archive(root))
        assertEquals(WidgetSettings(ViewMode.BY_RECIPIENTS, false, 12), WidgetSettings.load(prefs))
        assertEquals(expected, snapshot())
        assertEquals(visible, widget.rows().map { it.id }.toSet())
    }

    @Test fun malformedWidgetBooleansFailBeforeDatabasePreferencesOrGenerationChange() = runBlocking {
        val selected = WidgetSettings(showBought = false, showBoughtToggle = false,
            showShopBackground = true, showProductBackground = true)
        selected.save(prefs)
        val manager = BackupManager(app, db)
        val backup = manager.exportFile().also { files += it }
        val before = snapshot()
        val beforePrefs = prefs.all.toMap()
        val generation = ProductEditorTransactions.generation.get()
        for (key in newBooleanKeys) {
            for (wrong in listOf<Any>("false", 0)) {
                val root = manifest(backup)
                root.getJSONObject("preferences").getJSONObject(WidgetSettings.PREFS).put(key, wrong)
                // A successful accidental import would visibly erase this current row.
                root.getJSONObject("data").getJSONArray("shoppingListEntries").remove(0)
                assertTrue("Wrong typed $key must be rejected", runCatching { manager.importFile(archive(root)) }.isFailure)
                assertEquals(before, snapshot())
                assertEquals(beforePrefs, prefs.all)
                assertEquals(generation, ProductEditorTransactions.generation.get())
            }
        }
    }
}
