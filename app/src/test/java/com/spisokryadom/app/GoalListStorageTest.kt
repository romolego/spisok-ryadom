package com.spisokryadom.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.db.RetailConstraints
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.domain.GoalListDocument
import com.spisokryadom.app.domain.GoalListItemDraft
import kotlinx.coroutines.flow.first
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class GoalListStorageTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: AppDatabase
    private lateinit var location: LocationRepository
    private lateinit var shopping: ShoppingListRepository
    private lateinit var goals: GoalListRepository

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        RetailConstraints.install(db.openHelper.writableDatabase)
        db.cityDao().insert(CityEntity(1, "Тестовый город"))
        location = LocationRepository(db.cityDao(), db.shopDao(), app.getSharedPreferences("goal-list-storage", 0))
        shopping = ShoppingListRepository(db.shoppingListEntryDao(), db, location)
        goals = GoalListRepository(db, shopping)
    }

    @After fun cleanup() { location.closeForTest(); db.close() }

    @Test fun listsHaveStableIndependentIdentityAndPreserveRowsAcrossRename() = runBlocking {
        val a = goals.create("  Рецепт  ")
        val b = goals.create("Рецепт")
        val row = goals.addQuick(a, "Грибы", 0.5, "кг", 450.0)
        val original = goals.getList(a)!!
        goals.updateList(a, "Праздник", true)
        val changed = goals.getList(a)!!
        assertEquals(original.stableId, changed.stableId)
        assertNotEquals(original.stableId, goals.getList(b)!!.stableId)
        assertEquals(original.createdAt, changed.createdAt)
        assertEquals(listOf(row), goals.observeEntries(a).first().map { it.id })
        assertEquals("Праздник", goals.observeLists().first().single { it.id == a }.name)
        assertTrue(changed.showInPurchases)
    }

    @Test fun quickPurchaseDoesNotPolluteCatalogueAndHasIndependentPrice() = runBlocking {
        val list = goals.create("Компьютер")
        val row = goals.addQuick(list, "Видеокарта", 1.0, "шт.", 35_000.0, "Авито", note = "Проверить гарантию")
        assertTrue(db.productDao().getAll().first().isEmpty())
        val snapshot = goals.snapshot(list)
        val entry = snapshot.entries.single()
        assertEquals(row, entry.id)
        assertEquals("шт", entry.unit)
        assertNull(entry.assignedShopId)
        assertTrue(entry.explicitNoShop)
        assertTrue(snapshot.products.single().isTemporary)
        assertEquals(35_000.0, entry.plannedUnitPrice!!, 0.0)
        assertEquals("Авито", snapshot.place(entry)!!.title)
    }

    @Test fun catalogueSelectionReusesExactOutletPolicyAndKeepsCardUnchanged() = runBlocking {
        val shop = db.shopDao().insert(ShopEntity(name = "Магазин"))
        val point = db.shopBranchDao().insert(ShopBranchEntity(shopId = shop, address = "Улица, 1"))
        val product = db.productDao().insert(ProductEntity(name = "Огурцы", defaultQuantity = 0.4, defaultUnit = "кг", priceValue = 150.0))
        db.productShopLinkDao().insert(ProductShopLinkEntity(productId = product, shopId = shop))
        val list = goals.create("Салат")
        val entry = goals.addCatalogue(list, product, plannedUnitPrice = 200.0, choice = ShopChoice.EXPLICIT, shopId = shop, branchId = point)
        val row = db.shoppingListEntryDao().getById(entry)!!
        assertEquals(0.4, row.quantity, 0.0)
        assertEquals(point, row.assignedBranchId)
        assertEquals(shop, row.assignedShopId)
        assertEquals(150.0, db.productDao().getById(product)!!.priceValue!!, 0.0)
        assertEquals(200.0, row.plannedUnitPrice!!, 0.0)
    }

    @Test fun looseEditIsLocalWhenTemporaryProductIsShared() = runBlocking {
        val a = goals.create("Один")
        val b = goals.create("Два")
        val first = goals.addQuick(a, "Исходное", 2.0, "шт")
        val original = db.shoppingListEntryDao().getById(first)!!
        val second = db.shoppingListEntryDao().insert(original.copy(id = 0, listId = b))
        goals.updateItem(first, "Другое", 3.0, "уп.", 25.0, "Заметка")
        val edited = db.shoppingListEntryDao().getById(first)!!
        assertNotEquals(original.productId, edited.productId)
        assertEquals("Другое", db.productDao().getById(edited.productId)!!.name)
        assertEquals("Исходное", db.productDao().getById(db.shoppingListEntryDao().getById(second)!!.productId)!!.name)
        assertEquals(3.0, edited.quantity, 0.0)
        assertEquals("Заметка", edited.note)
    }

    @Test fun editingCatalogueEntryCannotRenameSharedProductAndRollsBack() = runBlocking {
        val product = db.productDao().insert(ProductEntity(name = "Помидоры"))
        val list = goals.create("Салат")
        val entry = goals.addCatalogue(list, product)
        assertTrue(runCatching { goals.updateItem(entry, "Другое", 2.0, "шт", 100.0) }.isFailure)
        assertEquals("Помидоры", db.productDao().getById(product)!!.name)
        assertEquals(1.0, db.shoppingListEntryDao().getById(entry)!!.quantity, 0.0)
    }

    @Test fun editingPlacePreservesSameAssignmentAndDetachesOnlyOnActualChange() = runBlocking {
        val shop = db.shopDao().insert(ShopEntity(name = "Рынок"))
        val point = db.shopBranchDao().insert(ShopBranchEntity(shopId = shop, address = "Павильон 7"))
        val product = db.productDao().insert(ProductEntity(name = "Сыр"))
        db.productShopLinkDao().insert(ProductShopLinkEntity(productId = product, shopId = shop))
        val list = goals.create("Ужин")
        val entry = goals.addCatalogue(list, product, choice = ShopChoice.EXPLICIT, shopId = shop, branchId = point)
        val place = goals.snapshot(list).place(db.shoppingListEntryDao().getById(entry)!!)!!
        goals.updateItem(entry, null, 2.0, "шт", null, customShopName = place.title, customShopAddress = place.subtitle.orEmpty())
        assertEquals(point, db.shoppingListEntryDao().getById(entry)!!.assignedBranchId)
        goals.updateItem(entry, null, 2.0, "шт", null, customShopName = "Другой магазин", customShopAddress = "Адрес")
        val changed = db.shoppingListEntryDao().getById(entry)!!
        assertNull(changed.assignedShopId)
        assertNull(changed.assignedBranchId)
        assertEquals("Другой магазин", changed.customShopName)
        assertEquals("Адрес", goals.snapshot(list).place(changed)!!.subtitle)
    }

    @Test fun movePreservesIdentityAndDoesNotMergeRowsIntoExistingDestination() = runBlocking {
        val a = goals.create("Первый")
        val b = goals.create("Второй")
        val product = db.productDao().insert(ProductEntity(name = "Вода"))
        val source = goals.addCatalogue(a, product, quantity = 2.0)
        val destination = goals.addCatalogue(b, product, quantity = 3.0)
        goals.setBought(source, true)
        goals.toggleUrgent(source)
        goals.moveEntriesExpected(mapOf(source to a), b)
        assertTrue(goals.snapshot(a).entries.isEmpty())
        val moved = goals.snapshot(b).entries.associateBy { it.id }
        assertEquals(setOf(source, destination), moved.keys)
        assertEquals(2.0, moved.getValue(source).quantity, 0.0)
        assertTrue(moved.getValue(source).isBought)
        assertTrue(moved.getValue(source).isUrgent)
        goals.moveEntriesExpected(mapOf(source to b), null)
        assertNull(db.shoppingListEntryDao().getById(source)!!.listId)
    }

    @Test fun malformedBatchMoveRollsBackEverySource() = runBlocking {
        val a = goals.create("Первый")
        val b = goals.create("Второй")
        val row = goals.addQuick(a, "Покупка")
        assertTrue(runCatching { goals.moveEntriesExpected(mapOf(row to a, 99999L to a), b) }.isFailure)
        assertEquals(a, db.shoppingListEntryDao().getById(row)!!.listId)
    }

    @Test fun deletingListCollectsOnlyItsUnreferencedTemporaryProducts() = runBlocking {
        val a = goals.create("Удаляемый")
        val b = goals.create("Остаётся")
        val own = goals.addQuick(a, "Временный один")
        val ownProduct = db.shoppingListEntryDao().getById(own)!!.productId
        val shared = goals.addQuick(a, "Общий временный")
        val sharedRow = db.shoppingListEntryDao().getById(shared)!!
        val other = db.shoppingListEntryDao().insert(sharedRow.copy(id = 0, listId = b))
        val permanent = db.productDao().insert(ProductEntity(name = "Постоянный"))
        goals.addCatalogue(a, permanent)
        goals.deleteList(a)
        assertNull(goals.getList(a))
        assertNull(db.productDao().getById(ownProduct))
        assertNotNull(db.productDao().getById(sharedRow.productId))
        assertNotNull(db.productDao().getById(permanent))
        assertNotNull(db.shoppingListEntryDao().getById(other))
    }

    @Test fun portableImportNeverBindsByNameAndResetsBoughtWhileRetainingUrgency() = runBlocking {
        val local = db.productDao().insert(ProductEntity(name = "Грибы"))
        val document = GoalListDocument("Рецепт", listOf(GoalListItemDraft("Грибы", 0.5, "кг", 120.0,
            "Для соуса", "Рынок — Павильон", "Центральная, 2", isBought = true, isUrgent = true)))
        val first = goals.importDocument(document)
        val second = goals.importDocument(document)
        val snapshot = goals.snapshot(first)
        val entry = snapshot.entries.single()
        assertNotEquals(local, entry.productId)
        assertTrue(snapshot.products.single().isTemporary)
        assertFalse(snapshot.list.showInPurchases)
        assertFalse(entry.isBought)
        assertTrue(entry.isUrgent)
        assertNotEquals(snapshot.list.stableId, goals.getList(second)!!.stableId)
        assertEquals(document.items.single().copy(isBought = false), goals.exportDocument(first).items.single())
        assertTrue(db.shopDao().getAllSync().isEmpty())
        assertTrue(db.shoppingListEntryDao().getVisibleSync().isEmpty())
    }

    @Test fun directInvalidImportRejectsBeforeAnyWritesAndInvalidQuickAddLeavesNoOrphan() = runBlocking {
        val invalid = GoalListDocument("Рецепт", listOf(GoalListItemDraft("Хороший"), GoalListItemDraft("Плохой", quantity = Double.NaN)))
        assertTrue(runCatching { goals.importDocument(invalid) }.isFailure)
        assertTrue(db.goalListDao().getAllSync().isEmpty())
        assertTrue(db.productDao().getAllSync().isEmpty())
        val list = goals.create("Список")
        assertTrue(runCatching { goals.addQuick(list, "Предмет", plannedUnitPrice = Double.POSITIVE_INFINITY) }.isFailure)
        assertTrue(db.productDao().getAllSync().isEmpty())
        assertTrue(goals.snapshot(list).entries.isEmpty())
    }

    @Test fun unresolvedAddressIsPortableWithoutTurningItIntoAStoreName() = runBlocking {
        val document = GoalListDocument("Заказ", listOf(
            GoalListItemDraft("Деталь", shopAddress = "Улица, 3"),
            GoalListItemDraft("Другая деталь", shopName = "Павильон", shopAddress = "Павильон")))
        val list = goals.importDocument(document)
        assertEquals(document, goals.exportDocument(list))
        val snapshot = goals.snapshot(list)
        assertEquals("Улица, 3", snapshot.place(snapshot.entries[0])!!.title)
        assertNull(snapshot.place(snapshot.entries[1])!!.subtitle)
    }

    @Test fun targetClearBoughtIsScopedAndCannotEditRegularRowsThroughGoalCommands() = runBlocking {
        val a = goals.create("Первый")
        val b = goals.create("Второй")
        val first = goals.addQuick(a, "Первый товар")
        val second = goals.addQuick(b, "Второй товар")
        goals.setBought(first, true)
        goals.setBought(second, true)
        goals.clearBought(a)
        assertNull(db.shoppingListEntryDao().getById(first))
        assertNotNull(db.shoppingListEntryDao().getById(second))
        goals.moveEntriesExpected(mapOf(second to b), null)
        assertTrue(runCatching { goals.removeItem(second) }.isFailure)
        assertNotNull(db.shoppingListEntryDao().getById(second))
    }

    @Test fun staleSingleItemCommandsCannotMutatePurchaseMovedToAnotherList() = runBlocking {
        val a = goals.create("Первый")
        val b = goals.create("Второй")
        val row = goals.addQuick(a, "Позиция")
        goals.moveEntriesFrom(a, setOf(row), b)
        val before = db.shoppingListEntryDao().getById(row)!!
        assertTrue(runCatching { goals.setBought(row, true, expectedListId = a) }.isFailure)
        assertTrue(runCatching { goals.toggleUrgent(row, expectedListId = a) }.isFailure)
        assertTrue(runCatching { goals.removeItem(row, expectedListId = a) }.isFailure)
        assertTrue(runCatching { goals.updateItem(row, "Изменено", 3.0, "шт", 50.0, expectedListId = a) }.isFailure)
        assertEquals(before, db.shoppingListEntryDao().getById(row))
        assertEquals("Позиция", db.productDao().getById(before.productId)!!.name)
    }

    @Test fun scopedBatchChecksEveryOwnerBeforeAnyBoughtOrDeleteChange() = runBlocking {
        val a = goals.create("Первый")
        val b = goals.create("Второй")
        val first = goals.addQuick(a, "Первая")
        val second = goals.addQuick(a, "Вторая")
        goals.moveEntriesFrom(a, setOf(second), b)
        val before = db.shoppingListEntryDao().getAllSync()
        assertTrue(runCatching { goals.batch(a, setOf(first, second), bought = true) }.isFailure)
        assertEquals(before, db.shoppingListEntryDao().getAllSync())
        assertTrue(runCatching { goals.batch(a, setOf(first, second), delete = true) }.isFailure)
        assertEquals(before, db.shoppingListEntryDao().getAllSync())
        goals.batch(a, setOf(first), bought = true)
        assertTrue(db.shoppingListEntryDao().getById(first)!!.isBought)
        goals.batch(a, setOf(first), bought = false)
        assertFalse(db.shoppingListEntryDao().getById(first)!!.isBought)
        goals.batch(a, setOf(first), delete = true)
        assertNull(db.shoppingListEntryDao().getById(first))
        assertNotNull(db.shoppingListEntryDao().getById(second))
    }

    @Test fun mixedOwnerMoveUsesCapturedOwnershipAndRollsBackStaleSelection() = runBlocking {
        val a = goals.create("Первый")
        val b = goals.create("Второй")
        val target = goals.create("Назначение")
        val regularProduct = db.productDao().insert(ProductEntity(name = "Регулярный"))
        val regular = shopping.addProduct(regularProduct, choice = ShopChoice.NONE)
        val row = goals.addQuick(a, "Целевая покупка")
        val captured = mapOf(regular to null, row to a)
        goals.moveEntriesFrom(a, setOf(row), b)
        val before = db.shoppingListEntryDao().getAllSync()
        assertTrue(runCatching { goals.moveEntriesExpected(captured, target) }.isFailure)
        assertEquals(before, db.shoppingListEntryDao().getAllSync())
        goals.moveEntriesExpected(mapOf(regular to null, row to b), target)
        assertEquals(setOf(regular, row), goals.snapshot(target).entries.map { it.id }.toSet())
        assertTrue(db.shoppingListEntryDao().getForListSync(null).isEmpty())
    }

    @Test fun quickDraftRetryIsIdempotentAndCannotReclaimPurchaseMovedElsewhere() = runBlocking {
        val list = goals.create("Компьютер")
        val other = goals.create("Другой")
        val session = java.util.UUID.randomUUID().toString()
        val row = goals.addQuick(list, "Видеокарта", plannedUnitPrice = 500.0, draftStableId = session)
        val recovered = goals.addQuick(list, "Видеокарта новая", quantity = 2.0, plannedUnitPrice = 600.0, draftStableId = session)
        assertEquals(row, recovered)
        assertEquals(1, goals.snapshot(list).entries.size)
        assertEquals(1, db.productDao().getAllSync().size)
        assertEquals("Видеокарта новая", goals.snapshot(list).products.single().name)
        assertEquals(2.0, db.shoppingListEntryDao().getById(row)!!.quantity, 0.0)
        goals.moveEntriesFrom(list, setOf(row), other)
        val before = db.shoppingListEntryDao().getById(row)
        assertTrue(runCatching { goals.addQuick(list, "Старый черновик", draftStableId = session) }.isFailure)
        assertEquals(before, db.shoppingListEntryDao().getById(row))
        assertTrue(goals.snapshot(list).entries.isEmpty())
        val permanent = db.productDao().insert(ProductEntity(name = "Из базы", stableId = "permanent-stable"))
        assertTrue(runCatching { goals.addQuick(list, "Конфликт", draftStableId = "permanent-stable") }.isFailure)
        assertFalse(db.productDao().getById(permanent)!!.isTemporary)
    }

    @Test fun importConfirmationRetryReusesCommittedListButNewSessionCreatesAnother() = runBlocking {
        val token = java.util.UUID.randomUUID().toString()
        val document = GoalListDocument("Рецепт", listOf(GoalListItemDraft("Грибы", quantity = 0.5, unit = "кг", unitPrice = 200.0)))
        val first = goals.importDocument(document, importStableId = token)
        val before = goals.snapshot(first)
        val recoveredRepository = GoalListRepository(db, shopping)
        val recovered = recoveredRepository.importDocument(document.copy(name = "Не перезаписывать"), importStableId = token)
        assertEquals(first, recovered)
        assertEquals(before, goals.snapshot(first))
        assertEquals(1, db.goalListDao().getAllSync().size)
        assertEquals(1, db.productDao().getAllSync().size)
        val explicitSecond = recoveredRepository.importDocument(document, importStableId = java.util.UUID.randomUUID().toString())
        assertNotEquals(first, explicitSecond)
        assertEquals(2, db.goalListDao().getAllSync().size)
        assertEquals(2, db.productDao().getAllSync().size)
        assertTrue(runCatching { goals.importDocument(document, importStableId = "invalid/session") }.isFailure)
        assertEquals(2, db.goalListDao().getAllSync().size)
    }

    @Test fun migration14AddsNullableOwnerWithoutRewritingPurchaseIdsOrAssignments() = runBlocking {
        val name = "goal-list-schema14.db"
        app.deleteDatabase(name)
        app.openOrCreateDatabase(name, 0, null).use { old ->
            val entities = JSONObject(javaClass.getResourceAsStream("/schema-v13.json")!!.reader().readText()).getJSONObject("database").getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.getJSONArray("indices")
                for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            old.execSQL("ALTER TABLE shops ADD COLUMN shoppingBackground TEXT NOT NULL DEFAULT ''")
            old.execSQL("INSERT INTO cities(id,name)VALUES(1,'Город')")
            old.execSQL("INSERT INTO shops(id,name,displayOrder,manualOrderOverride,cityId,shoppingBackground)VALUES(17,'Магазин',2,1,1,'rose')")
            old.execSQL("INSERT INTO products(id,name,purchaseType,isTemporary,stableId)VALUES(18,'Товар','offline',0,'synthetic-15')")
            old.execSQL("INSERT INTO shopping_list_entries(id,productId,quantity,unit,isBought,isUrgent,createdAt,updatedAt,assignedShopId,note,explicitNoShop)VALUES(19,18,4.5,'кг',1,1,100,200,17,'История',0)")
            old.version = 14
        }
        val migrated = Room.databaseBuilder(app, AppDatabase::class.java, name).allowMainThreadQueries().addMigrations(AppDatabase.MIGRATION_14_15,AppDatabase.MIGRATION_15_16,AppDatabase.MIGRATION_16_17,AppDatabase.MIGRATION_17_18).build()
        try {
            assertEquals(18,migrated.openHelper.writableDatabase.version)
            val row = migrated.shoppingListEntryDao().getById(19)!!
            assertEquals(18L, row.productId)
            assertEquals(17L, row.assignedShopId)
            assertEquals(4.5, row.quantity, 0.0)
            assertEquals(200L, row.updatedAt)
            assertTrue(row.isBought && row.isUrgent)
            assertNull(row.listId)
            assertNull(row.plannedUnitPrice)
            assertEquals("rose", migrated.shopDao().getById(17)!!.shoppingBackground)
            val list = migrated.goalListDao().insert(GoalListEntity(name = "Новый"))
            migrated.shoppingListEntryDao().update(row.copy(listId = list))
            migrated.goalListDao().deleteById(list)
            assertNull(migrated.shoppingListEntryDao().getById(19))
            assertNotNull(migrated.productDao().getById(18))
            migrated.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally { migrated.close(); app.deleteDatabase(name) }
    }
}
