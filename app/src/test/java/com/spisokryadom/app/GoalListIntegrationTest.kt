package com.spisokryadom.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.backup.BackupManager
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.db.RetailConstraints
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.ui.bulk.renderShoppingText
import com.spisokryadom.app.widget.ShoppingWidgetData
import com.spisokryadom.app.widget.WidgetItem
import com.spisokryadom.app.widget.WidgetSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.*
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class GoalListIntegrationTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: AppDatabase
    private lateinit var location: LocationRepository
    private lateinit var shopping: ShoppingListRepository
    private lateinit var goals: GoalListRepository
    private lateinit var products: ProductRepository
    private lateinit var backup: BackupManager
    private lateinit var widget: ShoppingWidgetData
    private val archives = mutableListOf<File>()

    @Before fun setup() = runBlocking {
        listOf("location_prefs", "shopping_list_prefs", "product_db_prefs").forEach {
            app.getSharedPreferences(it, 0).edit().clear().commit()
        }
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        RetailConstraints.install(db.openHelper.writableDatabase)
        db.cityDao().insert(CityEntity(1, "Город"))
        location = LocationRepository(db.cityDao(), db.shopDao(), app.getSharedPreferences("location_prefs", 0))
        shopping = ShoppingListRepository(db.shoppingListEntryDao(), db, location)
        goals = GoalListRepository(db, shopping)
        products = ProductRepository(db.productDao(), db.productShopLinkDao(), db)
        backup = BackupManager(app, db)
        widget = ShoppingWidgetData(db, shopping, location)
    }

    @After fun cleanup() {
        archives.forEach { backup.discard(it) }
        location.closeForTest()
        db.close()
    }

    private fun manifest(file: File) = ZipFile(file).use { archive ->
        JSONObject(archive.getInputStream(archive.getEntry("manifest.json")).reader(Charsets.UTF_8).readText())
    }
    private fun archive(root: JSONObject): File = File(app.cacheDir, "goal-integration-${UUID.randomUUID()}.zip").also { file ->
        archives += file
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(root.toString().toByteArray(Charsets.UTF_8)); zip.closeEntry()
        }
    }
    private suspend fun export() = backup.exportFile().also { archives += it }
    private suspend fun state() = Triple(db.goalListDao().getAllSync(), db.shoppingListEntryDao().getAllSync(), db.productDao().getAllSync())
    private suspend fun catalogue(name: String = "Огурцы") = db.productDao().insert(ProductEntity(name = name, defaultUnit = "кг", defaultQuantity = 0.5))
    private suspend fun show(id: Long, visible: Boolean = true) = goals.updateList(id, goals.getList(id)!!.name, visible)
    private fun replaceIdentity(root: JSONObject) = root.apply {
        val data = getJSONObject("data")
        data.getJSONArray("goalLists").getJSONObject(0).put("stableId", UUID.randomUUID().toString()).put("name", "Восстановленный список")
        data.getJSONArray("products").getJSONObject(0).put("stableId", UUID.randomUUID().toString()).put("name", "Восстановленный товар")
    }

    @Test fun fullZip11RestoresEveryListIdentityOwnershipPriceAndVisibility() = runBlocking {
        val product = catalogue()
        val regular = shopping.addProduct(product, quantity = 2.0, choice = ShopChoice.NONE)
        val hidden = goals.create("Рецепт")
        val shown = goals.create("Компьютер")
        val hiddenEntry = goals.addCatalogue(hidden, product, quantity = 0.75, plannedUnitPrice = 120.5)
        val shownEntry = goals.addQuick(shown, "Видеокарта", 1.0, "шт", 50_000.0, "Продавец", "Улица, 10", "Гарантия")
        show(shown)
        goals.setBought(hiddenEntry, true)
        goals.toggleUrgent(shownEntry)
        val before = state()
        val file = export()
        val root = manifest(file)
        assertEquals(20,root.getInt("version"))
        assertEquals(2, backup.inspect(file).goalLists)
        assertEquals(3, backup.inspect(file).entries)
        goals.deleteList(hidden)
        goals.deleteList(shown)
        shopping.removeEntry(regular)
        goals.create("Посторонний список")
        backup.importFile(file)
        assertEquals(before, state())
        assertEquals(setOf(regular, shownEntry), db.shoppingListEntryDao().getVisibleSync().map { it.id }.toSet())
        assertEquals(hidden, db.shoppingListEntryDao().getById(hiddenEntry)!!.listId)
        db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
    }

    @Test fun zip10RestoresRegularDefaultsAndReplacesAnyLaterGoalLists() = runBlocking {
        val regular = shopping.addProduct(catalogue(), quantity = 3.0, choice = ShopChoice.NONE)
        val root = manifest(export()).put("version", 10)
        val data = root.getJSONObject("data")
        data.remove("goalLists")
        val rows = data.getJSONArray("shoppingListEntries")
        for (i in 0 until rows.length()) listOf("listId", "plannedUnitPrice", "customShopName", "customShopAddress").forEach { rows.getJSONObject(i).remove(it) }
        val list = goals.create("Добавлено после копии")
        goals.addQuick(list, "Деталь", plannedUnitPrice = 100.0, shopName = "Другой продавец")
        backup.importFile(archive(root))
        assertTrue(goals.observeLists().first().isEmpty())
        val row = db.shoppingListEntryDao().getAllSync().single()
        assertEquals(regular, row.id)
        assertEquals(3.0, row.quantity, 0.0)
        assertNull(row.listId)
        assertNull(row.plannedUnitPrice)
        assertNull(row.customShopName)
        assertNull(row.customShopAddress)
    }

    @Test fun malformedGoalBackupRejectsAtomicallyIncludingHiddenRows() = runBlocking {
        val hidden = goals.create("Скрытый")
        goals.addQuick(hidden, "Деталь", plannedUnitPrice = 50.0, shopName = "Место")
        val other = goals.create("Второй")
        goals.addQuick(other, "Другой товар")
        val file = export()
        val before = state()
        val prefs = app.getSharedPreferences("shopping_list_prefs", 0)
        prefs.edit().putBoolean("browse_products_on_add", false).commit()
        val edits = listOf<(JSONObject) -> Unit>(
            { it.getJSONObject("data").getJSONArray("shoppingListEntries").getJSONObject(0).put("listId", 999_999) },
            { val lists = it.getJSONObject("data").getJSONArray("goalLists"); lists.getJSONObject(1).put("stableId", lists.getJSONObject(0).getString("stableId")) },
            { it.getJSONObject("data").getJSONArray("shoppingListEntries").getJSONObject(0).put("plannedUnitPrice", -1.0) },
            { it.getJSONObject("data").getJSONArray("shoppingListEntries").getJSONObject(0).put("plannedUnitPrice", "NaN") },
            { it.getJSONObject("data").getJSONArray("shoppingListEntries").getJSONObject(0).put("plannedUnitPrice", 1_000_000_000_001.0) },
            { it.getJSONObject("data").getJSONArray("shoppingListEntries").getJSONObject(0).put("quantity", 1.0000001) },
            { it.getJSONObject("data").getJSONArray("shoppingListEntries").getJSONObject(0).put("customShopName", "x".repeat(501)) },
            { it.getJSONObject("data").getJSONArray("shoppingListEntries").getJSONObject(0).put("customShopName", "Неверное\u0000место") },
            { it.getJSONObject("data").getJSONArray("shoppingListEntries").getJSONObject(0).put("customShopAddress", "Неверный\u0000адрес") },
            { it.getJSONObject("data").getJSONArray("goalLists").getJSONObject(0).put("name", "Неверное\u0000название") },
            { it.getJSONObject("data").remove("goalLists") }
        )
        edits.forEach { edit ->
            val malformed = manifest(file)
            edit(malformed)
            assertTrue(runCatching { backup.importFile(archive(malformed)) }.isFailure)
            assertEquals(before, state())
            assertFalse(prefs.getBoolean("browse_products_on_add", true))
        }
    }

    @Test fun olderVersionCannotSmuggleNewListMetadataIntoRegularPurchases() = runBlocking {
        val row = shopping.addProduct(catalogue(), choice = ShopChoice.NONE)
        val file = export()
        val before = state()
        for (field in listOf("listId", "plannedUnitPrice", "customShopName", "customShopAddress")) {
            val root = manifest(file).put("version", 10)
            root.getJSONObject("data").remove("goalLists")
            val purchase = root.getJSONObject("data").getJSONArray("shoppingListEntries").getJSONObject(0)
            purchase.put(field, when (field) { "listId" -> 123; "plannedUnitPrice" -> 25.0; else -> "Скрытые данные" })
            assertTrue(runCatching { backup.importFile(archive(root)) }.isFailure)
            assertEquals(before, state())
        }
        assertNotNull(db.shoppingListEntryDao().getById(row))
    }

    @Test fun widgetVisibilityUsesOriginalPurchaseIdsAndRejectsHiddenStaleClick() = runBlocking {
        val regular = shopping.addProduct(catalogue(), choice = ShopChoice.NONE)
        val list = goals.create("Рецепт")
        val target = goals.addQuick(list, "Грибы")
        assertEquals(listOf(regular), widget.rows().map { it.id })
        show(list)
        assertEquals(setOf(regular, target), widget.rows().map { it.id }.toSet())
        for (mode in com.spisokryadom.app.ui.shoppinglist.ViewMode.entries) {
            assertEquals(setOf(regular, target), widget.presentation(WidgetSettings(mode)).filterIsInstance<WidgetItem.Purchase>().map { it.row.id }.toSet())
        }
        val stale = widget.rows().single { it.id == target }
        show(list, false)
        assertFalse(widget.check(stale))
        assertFalse(db.shoppingListEntryDao().getById(target)!!.isBought)
        assertEquals(listOf(regular), widget.rows().map { it.id })
        assertEquals(target, goals.snapshot(list).entries.single().id)
    }

    @Test fun widgetBoughtFlagAndGoalListOperateOnOneRow() = runBlocking {
        val list = goals.create("Ужин")
        val target = goals.addQuick(list, "Грибы")
        show(list)
        val token = widget.rows().single()
        assertTrue(widget.check(token))
        assertTrue(goals.snapshot(list).entries.single().isBought)
        assertFalse(widget.check(token))
        val boughtToken = widget.rows().single()
        assertTrue(boughtToken.bought)
        goals.setBought(target, false)
        assertFalse(widget.check(boughtToken))
        assertFalse(widget.rows().single().bought)
        assertEquals(1, db.shoppingListEntryDao().getAllSync().size)
    }

    @Test fun regularCatalogueAddNeverMergesWithTargetEvenWhenBothShown() = runBlocking {
        val product = catalogue()
        val list = goals.create("Салат")
        val target = goals.addCatalogue(list, product, quantity = 2.0)
        show(list)
        val regular = shopping.addProduct(product, quantity = 3.0, choice = ShopChoice.NONE)
        assertNotEquals(target, regular)
        assertEquals(regular, shopping.addProduct(product, quantity = 4.0, choice = ShopChoice.NONE))
        assertEquals(2.0, db.shoppingListEntryDao().getById(target)!!.quantity, 0.0)
        assertEquals(7.0, db.shoppingListEntryDao().getById(regular)!!.quantity, 0.0)
        assertEquals(2, shopping.getAllEntries().first().size)
    }

    @Test fun catalogueUnitChangesPreserveTargetQuantityPriceMeaning() = runBlocking {
        val product = catalogue()
        val regular = shopping.addProduct(product, quantity = 2.0, choice = ShopChoice.NONE)
        val list = goals.create("Рецепт")
        val target = goals.addCatalogue(list, product, quantity = 2.0, plannedUnitPrice = 200.0)
        val card = db.productDao().getById(product)!!
        products.saveWithLinks(card.copy(defaultUnit = "шт", defaultQuantity = 1.0), emptyList())
        assertEquals("шт", db.shoppingListEntryDao().getById(regular)!!.unit)
        val row = db.shoppingListEntryDao().getById(target)!!
        assertEquals("кг", row.unit)
        assertEquals(2.0, row.quantity, 0.0)
        assertEquals(200.0, row.plannedUnitPrice!!, 0.0)
        assertEquals(400.0, row.quantity * row.plannedUnitPrice!!, 0.0)
    }

    @Test fun mainBatchRejectsWholeCapturedSelectionAfterMemberMovesIntoHiddenList() = runBlocking {
        val regular = shopping.addProduct(catalogue(), choice = ShopChoice.NONE)
        val shown = goals.create("Виден")
        val hidden = goals.create("Скрыт")
        val target = goals.addQuick(shown, "Деталь")
        show(shown)
        val selection = setOf(regular, target)
        assertEquals(selection, shopping.getAllEntries().first().map { it.id }.toSet())
        goals.moveEntriesFrom(shown, setOf(target), hidden)
        val before = state()
        val batch = BatchRepository(db, products, shopping)
        assertTrue(runCatching { batch.changePurchases(selection, PurchaseBatchChange.BOUGHT) }.isFailure)
        assertEquals(before, state())
        assertTrue(runCatching { batch.changePurchases(selection, PurchaseBatchChange.DELETE) }.isFailure)
        assertEquals(before, state())
    }

    @Test fun mainPlaceMoveRejectsHiddenCapturedRowBeforeAddingAvailabilityOrPriority() = runBlocking {
        val shown = goals.create("Виден")
        val hidden = goals.create("Скрыт")
        val target = goals.addQuick(shown, "Деталь")
        show(shown)
        val shop = db.shopDao().insert(ShopEntity(name = "Новый магазин"))
        goals.moveEntriesFrom(shown, setOf(target), hidden)
        val before = state()
        val links = db.productShopLinkDao().getAllSync()
        assertTrue(runCatching {
            shopping.movePending(setOf(target), shop, rememberPriority = true, extendAvailability = true, strictPending = true)
        }.isFailure)
        assertEquals(before, state())
        assertEquals(links, db.productShopLinkDao().getAllSync())
    }

    @Test fun staleEditorSessionRejectsWritesAfterRestoreReusesNumericIds() = runBlocking {
        val list = goals.create("Старый список")
        val row = goals.addQuick(list, "Старый товар")
        val generation = ProductEditorTransactions.generation.get()
        val replacement = archive(replaceIdentity(manifest(export())))
        backup.importFile(replacement)
        val restored = state()
        assertTrue(runCatching {
            goals.withSession(generation) { goals.updateItem(row, "Старый черновик", 5.0, "шт", 100.0, expectedListId = list) }
        }.isFailure)
        assertTrue(runCatching {
            goals.withSession(generation) { goals.addQuick(list, "Новый товар из старого черновика") }
        }.isFailure)
        assertEquals(restored, state())
        goals.withSession(ProductEditorTransactions.generation.get()) { goals.updateList(list, "Новая сессия", false) }
        assertEquals("Новая сессия", goals.getList(list)!!.name)
    }

    @Test fun queuedEditorSessionRechecksGenerationAfterWaitingBehindRestoreLock() = runBlocking {
        val list = goals.create("Старый список")
        goals.addQuick(list, "Старый товар")
        val generation = ProductEditorTransactions.generation.get()
        val replacement = archive(replaceIdentity(manifest(export())))
        val lockHeld = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val restoring = async(Dispatchers.Default) {
            ProductEditorTransactions.locked {
                lockHeld.complete(Unit)
                release.await()
                backup.importFile(replacement)
            }
        }
        lockHeld.await()
        val attempting = CompletableDeferred<Unit>()
        val stale = async(Dispatchers.Default) {
            attempting.complete(Unit)
            runCatching { goals.withSession(generation) { goals.addQuick(list, "Не создавать") } }
        }
        attempting.await()
        yield()
        val waitedForLock = !stale.isCompleted
        release.complete(Unit)
        restoring.await()
        assertTrue(waitedForLock)
        assertTrue(stale.await().isFailure)
        assertEquals("Восстановленный список", goals.getList(list)!!.name)
        assertEquals(listOf("Восстановленный товар"), goals.snapshot(list).products.map { it.name })
        assertEquals(1, db.shoppingListEntryDao().getAllSync().size)
    }

    @Test fun deletingCatalogueProductKeepsEveryTargetFactAsTemporaryProduct() = runBlocking {
        val product = catalogue()
        val original = db.productDao().getById(product)!!
        val regular = shopping.addProduct(product, choice = ShopChoice.NONE)
        val first = goals.create("Один")
        val second = goals.create("Два")
        val a = goals.addCatalogue(first, product, quantity = 2.0, plannedUnitPrice = 20.0)
        val b = goals.addCatalogue(second, product, quantity = 3.0, plannedUnitPrice = 30.0)
        goals.setBought(b, true)
        goals.toggleUrgent(a)
        show(second)
        products.deleteProduct(original)
        assertNull(db.productDao().getById(product))
        assertNull(db.shoppingListEntryDao().getById(regular))
        val rows = db.shoppingListEntryDao().getAllSync().associateBy { it.id }
        assertEquals(setOf(a, b), rows.keys)
        val clone = db.productDao().getById(rows.getValue(a).productId)!!
        assertTrue(clone.isTemporary)
        assertEquals(original.name, clone.name)
        assertNotEquals(original.stableId, clone.stableId)
        assertEquals(clone.id, rows.getValue(b).productId)
        assertEquals(20.0, rows.getValue(a).plannedUnitPrice!!, 0.0)
        assertEquals(30.0, rows.getValue(b).plannedUnitPrice!!, 0.0)
        assertTrue(rows.getValue(a).isUrgent)
        assertTrue(rows.getValue(b).isBought)
        assertEquals(first, rows.getValue(a).listId)
        assertEquals(second, rows.getValue(b).listId)
        assertTrue(db.productDao().getAll().first().isEmpty())
    }

    @Test fun quickTargetNameDoesNotBlockPermanentCatalogueCardWithSameName() = runBlocking {
        val list = goals.create("Компьютер")
        val quick = goals.addQuick(list, "Видеокарта")
        val permanent = ProductEntity(name = "Видеокарта", isTemporary = false)
        val id = products.saveEditorProduct(permanent, emptyList(), emptyMap(), emptyList(), emptyMap(), newSession = true, allowDuplicate = false)
        assertFalse(db.productDao().getById(id)!!.isTemporary)
        assertNotEquals(id, db.shoppingListEntryDao().getById(quick)!!.productId)
        assertEquals(listOf(id), db.productDao().getAll().first().map { it.id })
        assertTrue(runCatching {
            products.saveEditorProduct(ProductEntity(name = "Видеокарта"), emptyList(), emptyMap(), emptyList(), emptyMap(), newSession = true, allowDuplicate = false)
        }.isFailure)
        assertEquals(2, db.productDao().getAllSync().size)
    }

    @Test fun mainBoughtClearDoesNotTouchHiddenGoalPurchases() = runBlocking {
        val regular = shopping.addProduct(catalogue(), choice = ShopChoice.NONE)
        val hidden = goals.create("Скрытый")
        val shown = goals.create("Виден")
        val a = goals.addQuick(hidden, "Скрытая покупка")
        val b = goals.addQuick(shown, "Видимая покупка")
        show(shown)
        shopping.markBought(regular)
        goals.setBought(a, true)
        goals.setBought(b, true)
        shopping.clearBoughtEntries()
        assertEquals(listOf(a), db.shoppingListEntryDao().getAllSync().map { it.id })
        assertTrue(goals.snapshot(hidden).entries.single().isBought)
        assertTrue(goals.snapshot(shown).entries.isEmpty())
    }

    @Test fun placeClearSeparatesCustomPlaceFromUnassignedAndOtherCustomPlace() = runBlocking {
        val regular = shopping.addProduct(catalogue(), choice = ShopChoice.NONE)
        val shown = goals.create("Виден")
        val hidden = goals.create("Скрыт")
        val first = goals.addQuick(shown, "Первая", shopName = "Продавец А", shopAddress = "Адрес А")
        val second = goals.addQuick(shown, "Вторая", shopName = "Продавец Б", shopAddress = "Адрес Б")
        val protected = goals.addQuick(hidden, "Не трогать", shopName = "Продавец А", shopAddress = "Адрес А")
        show(shown)
        listOf(regular, first, second, protected).forEach { shopping.markBought(it) }
        shopping.clearBoughtInPlace(null, null, "Продавец А", "Адрес А")
        assertEquals(setOf(regular, second, protected), db.shoppingListEntryDao().getAllSync().map { it.id }.toSet())
        shopping.clearBoughtInPlace(null, null)
        assertEquals(setOf(second, protected), db.shoppingListEntryDao().getAllSync().map { it.id }.toSet())
    }

    @Test fun regularTextIncludesShownSourceNameAndCustomPlaceButNoHiddenOrBoughtRows() = runBlocking {
        shopping.addProduct(catalogue("Регулярный товар"), choice = ShopChoice.NONE)
        val hidden = goals.create("Секретный список")
        goals.addQuick(hidden, "Секретный товар")
        val shown = goals.create("Для ужина")
        val pending = goals.addQuick(shown, "Шампиньоны", 0.4, "кг", 100.0, "Рынок", "Павильон 5")
        val bought = goals.addQuick(shown, "Уже куплено")
        goals.setBought(bought, true)
        show(shown)
        val snapshot = shopping.textSnapshot()
        val text = renderShoppingText(snapshot)
        assertEquals(2, snapshot.entries.size)
        assertTrue(snapshot.entries.any { it.id == pending })
        assertTrue(text.contains("Регулярный товар"))
        assertTrue(text.contains("Шампиньоны — 0,4 кг [Для ужина]"))
        assertTrue(text.contains("Рынок · Павильон 5"))
        assertFalse(text.contains("Секрет"))
        assertFalse(text.contains("Уже куплено"))
        goals.updateList(shown, "Для праздника", true)
        assertTrue(renderShoppingText(shopping.textSnapshot()).contains("[Для праздника]"))
    }

}
