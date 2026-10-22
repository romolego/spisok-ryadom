package com.spisokryadom.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.ui.goallists.GoalListViewModel
import com.spisokryadom.app.widget.WidgetIntents
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.*
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = SpisokRyadomApp::class, qualifiers = "w360dp-h800dp-xhdpi")
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Audit151UiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<SpisokRyadomApp>()
    private val db get() = app.container.database
    private val repository get() = app.container.goalListRepository
    private val stores = mutableListOf<ViewModelStore>()
    private fun waitFor(condition: () -> Boolean) { ui.waitUntil(25_000) { Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(20)); ui.waitForIdle(); condition() } }
    private fun waitText(text: String) = waitFor { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun tab(label: String) { ui.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick() }
    private fun shot(name: String) {
        ui.waitForIdle(); ui.runOnIdle {
            val view = org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView ?: ui.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888); view.draw(Canvas(bitmap))
            val file = File("../.local/refinement-0.15.1/$name-api${android.os.Build.VERSION.SDK_INT}.png"); file.parentFile!!.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }
    @Before fun seed() {
        runBlocking {
            listOf("drafts", "shopping_list_prefs", "product_db_prefs").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
            db.shoppingListEntryDao().deleteAll(); db.goalListDao().deleteAll(); db.productDao().deleteAll();db.productGroupDao().deleteAll(); db.shopDao().deleteAll();db.shopDepartmentDao().clearTypes(); app.container.locations.select(1)
        }
        waitText("Список пуст")
    }
    @After fun close() {
        ui.runOnIdle { stores.forEach { it.clear() } }
        ui.activityRule.scenario.close()
        // Every teardown write in this suite is explicitly awaited before close.
        app.container.locations.closeForTest(); db.close()
        org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java, "INSTANCE", null)
    }
    private fun openList(id: Long) { tab("Списки"); waitFor { ui.onAllNodesWithTag("goal-list-$id").fetchSemanticsNodes().isNotEmpty() }; ui.onNodeWithTag("goal-list-$id").performClick(); waitFor { ui.onAllNodesWithTag("goal-open-catalogue").fetchSemanticsNodes().isNotEmpty() } }
    private fun editPurchase(name: String) { ui.onNodeWithContentDescription("Действия: $name").performClick(); ui.onNodeWithText("Количество, цена и заметка").performClick() }
    private fun back() { ui.runOnIdle {
        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }
        when (dialog) {
            is androidx.activity.ComponentDialog -> dialog.onBackPressedDispatcher.onBackPressed()
            null -> ui.activity.onBackPressedDispatcher.onBackPressed()
            else -> @Suppress("DEPRECATION") dialog.onBackPressed()
        }
    }; ui.waitForIdle() }
    private fun model(id: Long): Pair<GoalListViewModel, ViewModelStore> {
        val store = ViewModelStore().also { stores += it }
        val model = GoalListViewModel(id, repository, app.container.productRepository, app.container.shopRepository, SavedStateHandle())
        store.put("editor", model)
        return model to store
    }

    @Test fun removingViewModelBeforeDebounceCommitsTheLastValidDraftExactlyOnce() {
        val id = runBlocking { repository.create("Поездка") }
        ui.runOnIdle {
            val (model, store) = model(id)
            model.newItem()
            model.editDraft { it.copy(name = "Билеты", quantity = "2", unit = "шт", price = "123,45", note = "До пятницы", comment = "С местами рядом") }
            // Same main-thread turn: the 450ms debounce cannot have fired.
            assertTrue(runBlocking { db.shoppingListEntryDao().getForListSync(id).isEmpty() })
            store.clear()
        }
        waitFor { runBlocking { db.shoppingListEntryDao().getForListSync(id).size == 1 } }
        runBlocking {
            val row = db.shoppingListEntryDao().getForListSync(id).single()
            assertEquals(2.0, row.quantity, 0.0); assertEquals(123.45, row.plannedUnitPrice!!, 0.0)
            assertEquals("До пятницы", row.note); assertEquals("С местами рядом", row.comment)
            assertEquals("Билеты", db.productDao().getById(row.productId)!!.name)
        }
    }

    @Test fun warmWidgetAddKeepsEditedPurchaseAndOpensRegularAddFlow() {
        val id = runBlocking { repository.create("Ремонт") }
        val rowId = runBlocking { repository.addQuick(id, "Краска", 1.0, "банка") }
        openList(id); editPurchase("Краска"); waitText("Позиция списка")
        ui.onNodeWithTag("goal-item-quantity").performTextReplacement("3,5")
        val initialIntent = ui.activity.intent
        ui.runOnUiThread { org.robolectric.util.ReflectionHelpers.callInstanceMethod<Unit>(ui.activity, "onNewIntent", org.robolectric.util.ReflectionHelpers.ClassParameter.from(Intent::class.java, WidgetIntents.open(app, true))) }
        try {
            waitText("Добавить покупку")
            waitFor { runBlocking { db.shoppingListEntryDao().getById(rowId)!!.quantity == 3.5 } }
            runBlocking { val rows = db.shoppingListEntryDao().getAllSync(); assertEquals(1, rows.size); assertEquals(id, rows.single().listId) }
            shot("01-widget-deep-link-saved")
        } finally { ui.runOnUiThread { ui.activity.intent = initialIntent } }
    }

    @Test fun movedPurchaseCannotTrapAnUnsavedEditorOrBeRecreatedInItsOldList() {
        val from = runBlocking { repository.create("Рецепт") }; val to = runBlocking { repository.create("Поездка") }
        val rowId = runBlocking { repository.addQuick(from, "Салфетки", 2.0, "пачка") }
        openList(from); editPurchase("Салфетки"); waitText("Позиция списка")
        runBlocking { repository.moveEntriesFrom(from, setOf(rowId), to) }
        ui.onNodeWithTag("goal-item-quantity").performTextReplacement("ошибка"); back()
        waitText("Покупка удалена или перемещена. Откройте её из текущего списка")
        ui.onNodeWithTag("goal-item-name").assertDoesNotExist()
        shot("02-stale-owner-editor-closed")
        ui.onNodeWithText("Понятно").performClick()
        ui.onNodeWithTag("goal-add-item").assertIsDisplayed()
        runBlocking { val row = db.shoppingListEntryDao().getById(rowId)!!; assertEquals(to, row.listId); assertEquals(2.0, row.quantity, 0.0); assertTrue(db.shoppingListEntryDao().getForListSync(from).isEmpty()); assertEquals(1, db.shoppingListEntryDao().getAllSync().size) }
    }

    @Test fun regularPurchaseCommentRemainsVisibleEditableAndSeparateAfterMove() {
        val id = runBlocking { repository.create("Для дома") }
        val rowId = runBlocking {
            val product = db.productDao().insert(ProductEntity(name = "Лампа"))
            val row = db.shoppingListEntryDao().insert(ShoppingListEntryEntity(productId = product, quantity = 1.0, unit = "шт", note = "Тёплый свет", comment = "Цоколь E27"))
            repository.moveEntriesExpected(mapOf(row to null), id); row
        }
        openList(id); waitText("Цоколь E27"); ui.onNodeWithText("Тёплый свет").assertIsDisplayed()
        editPurchase("Лампа"); waitText("Позиция списка")
        ui.onNodeWithTag("goal-item-comment").performScrollTo().performTextReplacement("Цоколь E14")
        shot("03-goal-purchase-comment")
        ui.onNodeWithTag("goal-item-done").performClick()
        waitFor { ui.onAllNodesWithTag("goal-item-comment").fetchSemanticsNodes().isEmpty() }
        waitText("Цоколь E14")
        runBlocking { val row = db.shoppingListEntryDao().getById(rowId)!!; assertEquals("Тёплый свет", row.note); assertEquals("Цоколь E14", row.comment) }
    }

    @Test fun commentEditFromAnOldEditorPreservesExternalQuantityPriceUnitAndRealShop() {
        val id = runBlocking { repository.create("Для дома") }
        val (rowId, targetShop) = runBlocking {
            val product = db.productDao().insert(ProductEntity(name = "Лампочки", defaultUnit = "шт"))
            val first = db.shopDao().insert(ShopEntity(name = "Первый магазин", cityId = 1))
            val second = db.shopDao().insert(ShopEntity(name = "Второй магазин", cityId = 1))
            db.productShopLinkDao().insert(ProductShopLinkEntity(productId = product, shopId = first))
            db.productShopLinkDao().insert(ProductShopLinkEntity(productId = product, shopId = second))
            app.container.shoppingListRepository.addGoalCatalogueFixture(id, product, quantity = 1.0, unit = "шт", plannedUnitPrice = 100.0,
                choice = com.spisokryadom.app.data.repository.ShopChoice.EXPLICIT, shopId = first) to second
        }
        lateinit var editor: GoalListViewModel
        ui.runOnIdle { editor = model(id).first }
        waitFor { !editor.uiState.value.loading && editor.uiState.value.rows.isNotEmpty() }
        ui.runOnIdle { editor.edit(editor.uiState.value.rows.single()) }
        runBlocking {
            repository.updateItem(rowId, null, 5.0, "пара", 777.0, "Обновлённая заметка", expectedListId = id)
            app.container.shoppingListRepository.changeShop(rowId, targetShop, null)
        }
        ui.runOnIdle { editor.editDraft { it.copy(comment = "Проверить упаковку") }; editor.finishEditor() }
        waitFor { editor.uiState.value.editor == null }
        runBlocking {
            val row = db.shoppingListEntryDao().getById(rowId)!!
            assertEquals(targetShop, row.assignedShopId); assertNull(row.customShopName); assertNull(row.customShopAddress)
            assertEquals(5.0, row.quantity, 0.0); assertEquals("пара", row.unit); assertEquals(777.0, row.plannedUnitPrice!!, 0.0)
            assertEquals("Обновлённая заметка", row.note); assertEquals("Проверить упаковку", row.comment)
        }
        openList(id); waitText("Второй магазин"); ui.onNodeWithText("Проверить упаковку").assertIsDisplayed()
        shot("07-fresh-fields-preserved")
    }

    @Test fun customPlacesWithIdenticalFlattenedTextKeepSeparateHeaders() {
        val id = runBlocking { repository.create("Поставщики") }
        runBlocking {
            repository.addQuick(id, "Деталь первая", shopName = "А · Б", shopAddress = "В")
            repository.addQuick(id, "Деталь вторая", shopName = "А", shopAddress = "Б · В")
        }
        openList(id)
        ui.onNodeWithTag("goal-items").performScrollToNode(hasText("А"))
        ui.onNodeWithText("А").assertIsDisplayed(); ui.onNodeWithText("Б · В").assertIsDisplayed()
        ui.onNodeWithTag("goal-items").performScrollToNode(hasText("А · Б")); ui.onNodeWithText("А · Б").assertIsDisplayed(); ui.onNodeWithText("В").assertIsDisplayed()
        shot("04-raw-place-groups")
    }

    @Test fun unnamedDraftWithEnteredFieldsStaysVisibleUntilNameIsProvided() {
        val id = runBlocking { repository.create("Проект") }; openList(id)
        ui.onNodeWithContentDescription("Действия со списком").performClick(); ui.onNodeWithText("Быстрая позиция с ценой").performClick(); waitText("Новая позиция")
        ui.onNodeWithTag("goal-item-price").performScrollTo().performTextInput("150")
        back(); waitText("Укажите название")
        ui.onNodeWithTag("goal-item-name").performScrollTo().assertIsDisplayed()
        runBlocking { assertTrue(db.shoppingListEntryDao().getForListSync(id).isEmpty()) }
        shot("05-unnamed-draft-preserved")
        ui.onNodeWithTag("goal-item-name").performTextInput("Крепление"); back()
        waitFor { ui.onAllNodesWithTag("goal-item-name").fetchSemanticsNodes().isEmpty() }
        runBlocking { assertEquals(150.0, db.shoppingListEntryDao().getForListSync(id).single().plannedUnitPrice!!, 0.0) }
    }

    @Test fun remainingBudgetIsComputedFromPendingRowsWithoutLargeNumberCancellation() {
        val id = runBlocking { repository.create("Смета") }
        runBlocking {
            val bought = repository.addQuick(id, "Большая закупка", 1_000_000_000.0, "шт", 1_000_000_000_000.0)
            repository.setBought(bought, true, expectedListId = id)
            repository.addQuick(id, "Остаток", 1.0, "шт", .25)
        }
        openList(id)
        ui.onNodeWithText("Осталось: 0,25 ₽", substring = true).assertIsDisplayed()
        shot("06-decimal-budget")
    }

    @Test fun queuedMoveCompletesItsCallbackWhenRestoreGenerationRejectsIt() {
        var result: Boolean? = null
        val id = runBlocking { app.container.shoppingListRepository.addNewProduct("До восстановления", 2.0, "шт", false,
            com.spisokryadom.app.data.repository.ShopChoice.NONE) }
        ui.runOnIdle {
            val model = com.spisokryadom.app.ui.shoppinglist.ShoppingListViewModel(app.container.shoppingListRepository,
                app.container.productRepository, app.container.shopRepository, app.container.classifierRepository,
                app.getSharedPreferences("shopping_list_prefs", 0))
            val store = ViewModelStore().also { stores += it }; store.put("queued-move", model)
            val transactions = com.spisokryadom.app.data.repository.ProductEditorTransactions
            runBlocking { transactions.mutex.lock() }
            try {
                model.movePurchases(setOf(id), null, null) { result = it }
                assertNull(result)
                transactions.generation.incrementAndGet()
            } finally { transactions.mutex.unlock() }
        }
        waitFor { result != null }
        assertEquals(false, result)
        runBlocking { assertEquals(2.0, db.shoppingListEntryDao().getById(id)!!.quantity, 0.0) }
    }
}
