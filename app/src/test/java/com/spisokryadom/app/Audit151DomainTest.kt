package com.spisokryadom.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.db.RetailConstraints
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.domain.GoalListTransfer
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
class Audit151DomainTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: AppDatabase
    private lateinit var locations: LocationRepository
    private lateinit var shopping: ShoppingListRepository
    private lateinit var goals: GoalListRepository
    private lateinit var products: ProductRepository
    private lateinit var classifiers: ClassifierRepository

    @Before fun setup() = runBlocking {
        val prefs = app.getSharedPreferences("audit151-domain-location", 0)
        prefs.edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        RetailConstraints.install(db.openHelper.writableDatabase)
        db.cityDao().insert(CityEntity(1, "Город"))
        locations = LocationRepository(db.cityDao(), db.shopDao(), prefs)
        locations.select(1)
        shopping = ShoppingListRepository(db.shoppingListEntryDao(), db, locations)
        goals = GoalListRepository(db, shopping)
        products = ProductRepository(db.productDao(), db.productShopLinkDao(), db)
        classifiers = ClassifierRepository(db.productGroupDao(), db.recipientDao(), db.unitDao(), db)
    }

    @After fun cleanup() { locations.closeForTest(); db.close() }
    private suspend fun row(id: Long) = db.shoppingListEntryDao().getById(id)!!
    private suspend fun goal(name: String, visible: Boolean = false) = goals.create(name).also {
        if (visible) goals.updateList(it, name, true)
    }

    @Test fun dictionaryRenameCannotReinterpretHiddenOrVisibleGoalPriceUnits() = runBlocking {
        val unit = classifiers.insertUnit(UnitEntity(name = "кг"))
        val product = db.productDao().insert(ProductEntity(name = "Грибы", defaultUnit = "кг"))
        val regular = shopping.addProduct(product, quantity = 2.0, unit = "КГ.", choice = ShopChoice.NONE)
        val historical = db.shoppingListEntryDao().insert(ShoppingListEntryEntity(productId = product,
            quantity = 3.0, unit = "КГ.", isBought = true))
        val hidden = goal("Ужин")
        val shown = goal("Праздник", true)
        val first = goals.addCatalogue(hidden, product, quantity = 0.5, unit = "кг", plannedUnitPrice = 300.0)
        val second = goals.addCatalogue(shown, product, quantity = 2.0, unit = "кг", plannedUnitPrice = 450.0)
        val before = listOf(first, second, historical).associateWith { row(it) }
        classifiers.renameUnitAndProducts(classifiers.getUnitById(unit)!!, "пакет")
        assertEquals("пакет", row(regular).unit)
        assertEquals("пакет", db.productDao().getById(product)!!.defaultUnit)
        before.forEach { (id, saved) -> assertEquals(saved, row(id)) }
        assertEquals("кг", goals.exportDocument(hidden).items.single().unit)
        assertEquals(150.0, row(first).quantity * row(first).plannedUnitPrice!!, 0.0)
    }

    @Test fun dictionaryDeletionPreservesGoalFactsAndOnlyClearsRegularDefaults() = runBlocking {
        val unit = classifiers.insertUnit(UnitEntity(name = "бутылка"))
        val product = db.productDao().insert(ProductEntity(name = "Сок", defaultUnit = "бутылка"))
        val regular = shopping.addProduct(product, quantity = 2.0, choice = ShopChoice.NONE)
        val hidden = goal("Поездка")
        val target = goals.addCatalogue(hidden, product, quantity = 3.0, unit = "бутылка", plannedUnitPrice = 120.0)
        val bought = goals.addQuick(hidden, "Вода", quantity = 2.0, unit = "бутылка", plannedUnitPrice = 50.0)
        goals.setBought(bought, true)
        val before = listOf(target, bought).associateWith { row(it) }
        classifiers.deleteUnitAndProducts(classifiers.getUnitById(unit)!!)
        assertEquals("", row(regular).unit)
        assertNull(db.productDao().getById(product)!!.defaultUnit)
        before.forEach { (id, saved) -> assertEquals(saved, row(id)) }
        assertEquals("бутылка", goals.exportDocument(hidden).items.first().unit)
        assertEquals(360.0, row(target).quantity * row(target).plannedUnitPrice!!, 0.0)
    }

    @Test fun movedRegularCommentSurvivesGoalEditingAndIsExplicitlyEditable() = runBlocking {
        val product = db.productDao().insert(ProductEntity(name = "Огурцы"))
        val purchase = shopping.addProduct(product, choice = ShopChoice.NONE)
        shopping.updatePurchaseDetails(purchase, 2.0, "Для салата", "Без пакета")
        val target = goal("Рецепт")
        goals.moveEntriesExpected(mapOf(purchase to null), target)
        goals.updateItem(purchase, null, 3.0, "кг", 150.0, "Спелые", expectedListId = target)
        assertEquals("Без пакета", row(purchase).comment)
        assertEquals("Спелые", goals.exportDocument(target).items.single().note)
        assertEquals("Без пакета", goals.exportDocument(target).items.single().comment)
        goals.updateItem(purchase, null, 3.0, "кг", 150.0, "Спелые", expectedListId = target, comment = "  Не резать  ")
        assertEquals("Не резать", row(purchase).comment)
        goals.updateItem(purchase, null, 3.0, "кг", 150.0, "Спелые", expectedListId = target, comment = "")
        assertNull(row(purchase).comment)
        assertEquals("Спелые", goals.exportDocument(target).items.single().note)
        assertEquals("", goals.exportDocument(target).items.single().comment)
        goals.moveEntriesFrom(target, setOf(purchase), null)
        assertEquals("Спелые", row(purchase).note)
        assertNull(row(purchase).comment)
    }

    @Test fun newQuickCommentAndRetryPreserveSeparateNoteWithoutDuplicatingPurchase() = runBlocking {
        val list = goal("Праздник")
        val id = goals.addQuick(list, "Торт", note = "Свечи", draftStableId = "audit151-draft", comment = "Без орехов")
        assertEquals("Свечи", row(id).note)
        assertEquals("Без орехов", row(id).comment)
        assertEquals(id, goals.addQuick(list, "Торт", note = "Свечи", draftStableId = "audit151-draft"))
        assertEquals("Без орехов", row(id).comment)
        assertEquals(1, goals.snapshot(list).entries.size)
        assertEquals("Свечи", goals.exportDocument(list).items.single().note)
        assertEquals("Без орехов", goals.exportDocument(list).items.single().comment)
    }

    @Test fun fullLengthNoteAndCommentRoundTripSeparatelyThroughBothPortableFormats() = runBlocking {
        val list = goal("Рецепт")
        val note = "н".repeat(10_000)
        val comment = "к".repeat(10_000)
        goals.addQuick(list, "Продукты", 2.5, "кг", 200.0, "Рынок", "Улица, 8", note, comment = comment)
        val exported = goals.exportDocument(list)
        val encoded = listOf(GoalListTransfer.exportText(exported), GoalListTransfer.exportJson(exported))
        encoded.forEach { payload ->
            val preview = GoalListTransfer.parse(payload)
            assertTrue(preview.errors.joinToString(), preview.canImport)
            assertEquals(exported, preview.document)
            val imported = goals.importDocument(preview.document!!)
            val actual = goals.snapshot(imported).entries.single()
            assertEquals(note, actual.note)
            assertEquals(comment, actual.comment)
            assertEquals(exported, goals.exportDocument(imported))
        }
    }

    @Test fun invalidCommentRollsBackNameQuantityAndEveryOtherPurchaseFact() = runBlocking {
        val list = goal("Планы")
        val id = goals.addQuick(list, "Деталь", quantity = 2.0, unit = "шт", plannedUnitPrice = 100.0, comment = "Гарантия")
        val before = row(id)
        assertTrue(runCatching {
            goals.updateItem(id, "Изменено", 3.0, "уп", 200.0, comment = "x".repeat(10_001))
        }.isFailure)
        assertEquals(before, row(id))
        assertEquals("Деталь", db.productDao().getById(before.productId)!!.name)
        assertTrue(runCatching { goals.addQuick(list, "Плохой", comment = "Неверно\u0000") }.isFailure)
        assertEquals(1, db.productDao().getAllSync().size)
    }

    @Test fun quantityDeltasUseLatestRowAndCannotOverwriteChangedPriceNotesOrPlace() = runBlocking {
        val list = goal("Компьютер")
        val id = goals.addQuick(list, "Деталь", 1.0, "шт", 100.0, note = "Первая заметка")
        // This is the row a still-visible +/- button was drawn from.
        val stale = row(id)
        goals.updateItem(id, "Деталь", 5.0, "пара", 700.0, "Обновлено", "Новый продавец", "Улица, 7", comment = "Проверить")
        val latest = row(id)
        goals.adjustQuantity(stale.id, 1.0, expectedListId = list)
        val actual = row(id)
        assertEquals(latest.copy(quantity = 6.0, updatedAt = actual.updatedAt), actual)
        coroutineScope { repeat(5) { launch(Dispatchers.Default) { goals.adjustQuantity(id, 1.0, expectedListId = list) } } }
        assertEquals(11.0, row(id).quantity, 0.0)
        assertEquals("Обновлено", row(id).note)
        assertEquals("Проверить", row(id).comment)
        assertEquals("Новый продавец", row(id).customShopName)
        assertEquals(700.0, row(id).plannedUnitPrice!!, 0.0)
    }

    @Test fun maskedNoteEditCannotRestoreAnyStaleFactsOrDetachLatestRealShop() = runBlocking {
        val firstShop = db.shopDao().insert(ShopEntity(name = "Первый магазин"))
        val secondShop = db.shopDao().insert(ShopEntity(name = "Второй магазин"))
        val point = db.shopBranchDao().insert(ShopBranchEntity(shopId = secondShop, address = "Новый адрес"))
        val product = products.saveWithLinks(ProductEntity(name = "Сыр", defaultUnit = "кг"),
            listOf(ProductShopLinkEntity(productId = 0, shopId = firstShop), ProductShopLinkEntity(productId = 0, shopId = secondShop)))
        val list = goal("Рецепт")
        val id = goals.addCatalogue(list, product, quantity = 1.0, unit = "кг", plannedUnitPrice = 300.0,
            choice = ShopChoice.EXPLICIT, shopId = firstShop)
        val stale = row(id)
        shopping.movePending(setOf(id), secondShop, point)
        goals.updateItem(id, null, 5.0, "уп", 900.0, "Другая заметка", comment = "Новый комментарий")
        val latest = row(id)
        // Only NOTE was changed by the old editor; all other captured values are stale.
        goals.updateItem(id, "Даже неверное имя из старого UI", stale.quantity, stale.unit, stale.plannedUnitPrice,
            note = "Свежая заметка", customShopName = "Первый магазин", customShopAddress = "",
            expectedListId = list, comment = "Старый комментарий", editedFields = setOf(GoalItemField.NOTE))
        val actual = row(id)
        assertEquals(latest.copy(note = "Свежая заметка", updatedAt = actual.updatedAt), actual)
        assertEquals(secondShop, actual.assignedShopId)
        assertEquals(point, actual.assignedBranchId)
        assertNull(actual.customShopName)
        assertEquals("Сыр", db.productDao().getById(product)!!.name)
        // Invalid unedited raw fields must not prevent the valid note patch.
        goals.updateItem(id, "", Double.NaN, "\u0000", Double.NaN, "Ещё одна заметка", "\u0000", "\u0000",
            expectedListId = list, comment = "x".repeat(10_001), editedFields = setOf(GoalItemField.NOTE))
        val final = row(id)
        assertEquals(actual.copy(note = "Ещё одна заметка", updatedAt = final.updatedAt), final)
    }

    @Test fun maskedExplicitClearsAreAppliedWhileUneditedFactsAndEmptyMaskStayIntact() = runBlocking {
        val list = goal("Проект")
        val id = goals.addQuick(list, "Деталь", 4.0, "шт", 200.0, "Продавец", "Улица, 4", "Записать", comment = "Проверить")
        val before = row(id)
        goals.updateItem(id, "Нельзя переименовать", 99.0, "другое", null, note = null,
            customShopName = "", customShopAddress = "", expectedListId = list, comment = null,
            editedFields = setOf(GoalItemField.PRICE, GoalItemField.NOTE, GoalItemField.COMMENT, GoalItemField.PLACE))
        val actual = row(id)
        assertEquals(before.copy(plannedUnitPrice = null, note = null, comment = null, customShopName = null,
            customShopAddress = null, updatedAt = actual.updatedAt), actual)
        assertEquals("Деталь", db.productDao().getById(actual.productId)!!.name)
        goals.updateItem(id, "", Double.NaN, "\u0000", Double.NaN, editedFields = emptySet())
        assertEquals(actual, row(id))
    }

    @Test fun quantityDeltaRespectsOwnerBoughtMinimumAndAtomicRangeValidation() = runBlocking {
        val first = goal("Первый")
        val second = goal("Второй")
        val id = goals.addQuick(first, "Предмет", 1.0, "шт", 10.0)
        val original = row(id)
        goals.adjustQuantity(id, -1.0, expectedListId = first)
        assertEquals(original, row(id))
        goals.setBought(id, true, expectedListId = first)
        val bought = row(id)
        goals.adjustQuantity(id, 1.0, expectedListId = first)
        assertEquals(bought, row(id))
        goals.moveEntriesFrom(first, setOf(id), second)
        val moved = row(id)
        assertTrue(runCatching { goals.adjustQuantity(id, 1.0, expectedListId = first) }.isFailure)
        assertEquals(moved, row(id))
        goals.setBought(id, false, expectedListId = second)
        val pending = row(id)
        assertTrue(runCatching { goals.adjustQuantity(id, Double.NaN, expectedListId = second) }.isFailure)
        assertTrue(runCatching { goals.adjustQuantity(id, 1_000_000_000.0, expectedListId = second) }.isFailure)
        assertEquals(pending, row(id))
    }

    @Test fun temporaryTargetCardSaveKeepsTemporaryAndTargetFactsUntilExplicitPromotion() = runBlocking {
        val list = goal("Компьютер", true)
        val id = goals.addQuick(list, "Видеокарта", 2.0, "шт", 50_000.0, "Авито", note = "Гарантия")
        val before = row(id)
        val card = db.productDao().getById(before.productId)!!
        val savedId = products.saveEditorProduct(card.copy(name = "Видеокарта новая", defaultUnit = "комплект"),
            emptyList(), emptyMap(), emptyList(), emptyMap(), newSession = false, allowDuplicate = true)
        assertEquals(card.id, savedId)
        assertTrue(db.productDao().getById(card.id)!!.isTemporary)
        assertEquals(before, row(id))
        assertEquals("Видеокарта новая", goals.exportDocument(list).items.single().name)
        products.saveEditorProduct(db.productDao().getById(card.id)!!.copy(isTemporary = false),
            emptyList(), emptyMap(), emptyList(), emptyMap(), newSession = false, allowDuplicate = true)
        assertFalse(db.productDao().getById(card.id)!!.isTemporary)
        assertEquals(before, row(id))
    }
}
