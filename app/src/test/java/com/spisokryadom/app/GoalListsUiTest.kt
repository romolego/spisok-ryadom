package com.spisokryadom.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.room.withTransaction
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.domain.GoalListDocument
import com.spisokryadom.app.domain.GoalListItemDraft
import com.spisokryadom.app.domain.GoalListTransfer
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
class GoalListsUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<SpisokRyadomApp>()
    private val db get() = app.container.database
    private val repository get() = app.container.goalListRepository
    private var banana = 0L
    private var apple = 0L
    private fun waitFor(condition: () -> Boolean) { ui.waitUntil(25_000) { Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(20)); ui.waitForIdle(); condition() } }
    private fun waitText(text: String) = waitFor { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun tab(label: String) { ui.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick() }
    private fun shot(name: String) {
        ui.waitForIdle(); ui.runOnIdle {
            val view = org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView ?: ui.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888); view.draw(Canvas(bitmap))
            val file = File("../.local/refinement-0.15.0/$name-api${android.os.Build.VERSION.SDK_INT}.png"); file.parentFile!!.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }
    @Before fun seed() {
        runBlocking {
            listOf("drafts", "shopping_list_prefs", "product_db_prefs").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
            db.shoppingListEntryDao().deleteAll(); db.goalListDao().deleteAll(); db.productDao().deleteAll();db.productGroupDao().deleteAll(); db.shopDao().deleteAll();db.shopDepartmentDao().clearTypes(); app.container.locations.select(1)
            banana = db.productDao().insert(ProductEntity(name = "Банан", isFavorite = true, defaultQuantity = .5, defaultUnit = "кг"))
            apple = db.productDao().insert(ProductEntity(name = "Яблоко", defaultQuantity = 2.0, defaultUnit = "шт"))
        }
        waitText("Список пуст")
    }
    @After fun close() {
        ui.activityRule.scenario.close(); app.container.locations.closeForTest(); db.close()
        org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java, "INSTANCE", null)
    }
    private fun openList(id: Long) { tab("Списки"); waitFor { ui.onAllNodesWithTag("goal-list-$id").fetchSemanticsNodes().isNotEmpty() }; ui.onNodeWithTag("goal-list-$id").performClick(); waitFor { ui.onAllNodesWithTag("goal-open-catalogue").fetchSemanticsNodes().isNotEmpty() } }
    private fun back() { ui.runOnIdle {
        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }
        when (dialog) {
            is androidx.activity.ComponentDialog -> dialog.onBackPressedDispatcher.onBackPressed()
            null -> ui.activity.onBackPressedDispatcher.onBackPressed()
            else -> @Suppress("DEPRECATION") dialog.onBackPressed()
        }
    }; ui.waitForIdle() }

    @Test fun fourTabsRegularEntryAndLooseAutosaveWithUnitPrice() {
        tab("Списки"); waitText("Текущие покупки")
        listOf("Покупки", "Списки", "Товары", "Магазины").forEach { ui.onNode(hasText(it) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertExists() }
        ui.onNodeWithTag("goal-regular").performClick(); waitText("Список покупок")
        tab("Списки"); ui.onNodeWithContentDescription("Создать список").performClick()
        ui.onNodeWithText("Название списка").performTextInput("Новый компьютер"); ui.onNodeWithText("Сохранить").performClick()
        waitText("Новый компьютер"); ui.onNodeWithTag("goal-add-item").performClick(); waitText("Новая позиция")
        ui.onNodeWithTag("goal-item-name").performTextInput("Видеокарта")
        ui.onNodeWithTag("goal-item-quantity").performTextReplacement("2")
        ui.onNodeWithTag("goal-item-unit").performTextInput("шт")
        ui.onNodeWithTag("goal-item-price").performTextInput("24500,50")
        shot("01-target-editor")
        back()
        waitFor { ui.onAllNodesWithTag("goal-item-name").fetchSemanticsNodes().isEmpty() }
        waitFor { runBlocking { db.shoppingListEntryDao().getAllSync().singleOrNull()?.plannedUnitPrice == 24500.5 } }
        runBlocking {
            val list = db.goalListDao().getAllSync().single(); val row = db.shoppingListEntryDao().getAllSync().single()
            assertEquals("Новый компьютер", list.name); assertFalse(list.showInPurchases); assertEquals(list.id, row.listId)
            assertEquals(2.0, row.quantity, 0.0); assertNull(row.assignedShopId); assertTrue(db.productDao().getById(row.productId)!!.isTemporary)
            assertEquals(2, db.productDao().getAllSync().count { !it.isTemporary })
        }
        waitText("Видеокарта"); shot("02-target-loose")
        back(); tab("Покупки"); waitText("Список пуст"); ui.onNodeWithText("Видеокарта").assertDoesNotExist()
    }

    @Test fun catalogueFavoritesRepeatAndQuickAddsStayOpen() {
        val id = runBlocking { repository.create("Салат") }; openList(id)
        ui.onNodeWithTag("goal-open-catalogue").performClick(); waitText("Избранные")
        ui.onNodeWithTag("goal-catalogue-favorite-$banana").assertIsDisplayed()
        ui.onNodeWithTag("goal-catalogue").performScrollToNode(hasTestTag("goal-catalogue-all-$banana")); ui.onNodeWithTag("goal-catalogue-all-$banana").assertIsDisplayed()
        shot("03-target-catalogue")
        ui.onNodeWithTag("goal-catalogue-all-$banana").performClick()
        waitFor { runBlocking { db.shoppingListEntryDao().getForListSync(id).size == 1 } }
        ui.onNodeWithTag("goal-catalogue").performScrollToNode(hasTestTag("goal-catalogue-all-$apple")); ui.onNodeWithTag("goal-catalogue-all-$apple").performClick()
        waitFor { runBlocking { db.shoppingListEntryDao().getForListSync(id).size == 2 } }
        ui.onNodeWithText("Из базы товаров").assertIsDisplayed()
        runBlocking { val rows = db.shoppingListEntryDao().getForListSync(id); assertEquals(.5, rows.single { it.productId == banana }.quantity, 0.0); assertEquals(2.0, rows.single { it.productId == apple }.quantity, 0.0); assertTrue(rows.all { it.assignedShopId == null }); assertEquals(2, db.productDao().getAllSync().size) }
        ui.onNodeWithTag("goal-catalogue-search").performTextInput("бан")
        waitFor { ui.onAllNodesWithTag("goal-catalogue-all-$apple").fetchSemanticsNodes().isEmpty() }
        ui.onAllNodesWithContentDescription("Избранный товар: Банан").onFirst().assertExists()
        back(); waitText("Салат")
    }

    @Test fun inclusionShowsTheSamePurchaseAndCheckboxSynchronizes() {
        val id = runBlocking { repository.create("На праздник") }
        val rowId = runBlocking { repository.addQuick(id, "Торт", 1.0, "шт", 1300.0, "Кондитерская", "Мира, 8") }
        openList(id)
        ui.onNodeWithContentDescription("Действия со списком").performClick(); ui.onNodeWithText("Настройки списка").performClick()
        ui.onNodeWithText("Отображать в текущих покупках").performClick()
        shot("12-target-settings")
        ui.onNodeWithText("Готово").performClick()
        waitFor { runBlocking { db.goalListDao().getById(id)!!.showInPurchases } }
        back(); tab("Покупки"); waitText("Торт"); waitFor { ui.onAllNodesWithText("Список: На праздник", substring = true).fetchSemanticsNodes().isNotEmpty() }; shot("04-target-in-main")
        runBlocking { assertEquals(rowId, db.shoppingListEntryDao().getAllSync().single().id) }
        openList(id); ui.onNodeWithContentDescription("Куплено: Торт").performClick()
        waitFor { runBlocking { db.shoppingListEntryDao().getById(rowId)!!.isBought } }
        runBlocking { assertEquals(1, db.shoppingListEntryDao().getAllSync().size) }
        shot("05-target-checked")
    }

    @Test fun importPreviewCancelDoesNotWriteAndConfirmedImportIsIsolated() {
        runBlocking { app.container.shoppingListRepository.addProduct(banana, .5) }; waitText("Банан")
        val text = GoalListTransfer.exportText(GoalListDocument("Грибной суп", listOf(GoalListItemDraft("Грибы", .3, "кг", 600.0, "Свежие", "Рынок", "Павильон 2", isBought = true, isUrgent = true))))
        tab("Списки"); ui.onNodeWithContentDescription("Импортировать список").performClick(); waitText("Импорт списка")
        ui.onNodeWithTag("goal-import-editor").performTextInput(text); ui.onNodeWithText("Проверить список").performClick(); waitFor { ui.onAllNodesWithTag("goal-import-confirm").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("goal-import-preview").performScrollToNode(hasText("Грибной суп")); waitText("Грибной суп")
        runBlocking { assertTrue(db.goalListDao().getAllSync().isEmpty()); assertEquals(1, db.shoppingListEntryDao().getAllSync().size) }
        shot("06-target-import-preview")
        ui.onNodeWithText("Отмена").performClick(); waitText("Текущие покупки")
        runBlocking { assertTrue(db.goalListDao().getAllSync().isEmpty()) }
        ui.onNodeWithContentDescription("Импортировать список").performClick(); ui.onNodeWithTag("goal-import-editor").performTextInput(text)
        ui.onNodeWithText("Проверить список").performClick(); waitFor { ui.onAllNodesWithTag("goal-import-confirm").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("goal-import-confirm").performClick(); waitText("Грибы")
        runBlocking {
            val list = db.goalListDao().getAllSync().single(); assertEquals("Грибной суп", list.name); assertFalse(list.showInPurchases)
            val imported = db.shoppingListEntryDao().getForListSync(list.id).single(); assertFalse(imported.isBought); assertTrue(imported.isUrgent)
            assertNull(imported.assignedShopId); assertEquals("Рынок", imported.customShopName); assertEquals(.5, db.shoppingListEntryDao().getAllSync().single { it.listId == null }.quantity, 0.0)
            assertEquals(2, db.productDao().getAllSync().count { !it.isTemporary })
        }
        shot("07-target-imported")
    }

    @Test fun shareUsesTemporaryEditedMessageAndReopeningRegenerates() {
        val id = runBlocking { repository.create("Рецепт") }; runBlocking { repository.addQuick(id, "Огурцы", .5, "кг", 100.0) }; openList(id)
        val before = runBlocking { db.shoppingListEntryDao().getAllSync() }
        ui.onNodeWithContentDescription("Поделиться целевым списком").performClick(); waitText("Текст списка")
        ui.onNodeWithTag("goal-share-editor").performTextReplacement("Купи огурцы для салата")
        ui.onNodeWithTag("goal-share-send").performClick()
        ui.runOnIdle {
            val chooser = Shadows.shadowOf(ui.activity).nextStartedActivity; assertEquals(Intent.ACTION_CHOOSER, chooser.action)
            val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!; assertEquals("Купи огурцы для салата", send.getStringExtra(Intent.EXTRA_TEXT))
        }
        shot("08-target-share")
        back(); waitText("Рецепт"); ui.onNodeWithContentDescription("Поделиться целевым списком").performClick()
        waitFor { ui.onAllNodes(hasSetTextAction() and hasText("Огурцы", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        runBlocking { assertEquals(before, db.shoppingListEntryDao().getAllSync()) }
    }

    @Test fun invalidDraftSurvivesRecreationAndDoesNotOverwriteLastValidQuantity() {
        val id = runBlocking { repository.create("Закупка") }; val rowId = runBlocking { repository.addQuick(id, "Краска", 2.0, "банка", .0000001, shopAddress = "Мира, 8") }; openList(id)
        ui.onNodeWithTag("goal-entry-$rowId").performClick(); waitText("Позиция списка")
        ui.onNodeWithTag("goal-item-shop").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        ui.onNodeWithTag("goal-item-address").performScrollTo().assertTextContains("Мира, 8")
        ui.onNodeWithTag("goal-item-price").performScrollTo().assertTextContains("0,0000001")
        ui.onNodeWithTag("goal-item-quantity").performScrollTo().performTextReplacement("1,,")
        ui.activityRule.scenario.recreate(); waitText("Позиция списка")
        ui.onNodeWithTag("goal-item-quantity").assertTextContains("1,,")
        ui.onNodeWithTag("goal-item-quantity").performTextReplacement("ошибка"); back(); waitText("Введите положительное количество")
        shot("13-invalid-back-editor")
        ui.runOnIdle { assertTrue("Invalid editor must retain its focused dialog window", org.robolectric.shadows.ShadowDialog.getLatestDialog().isShowing) }
        ui.onNodeWithTag("goal-item-quantity").assertIsDisplayed(); ui.onNodeWithText("Введите положительное количество").assertIsDisplayed()
        runBlocking { assertEquals(2.0, db.shoppingListEntryDao().getById(rowId)!!.quantity, 0.0) }
        ui.onNodeWithTag("goal-item-quantity").performTextReplacement("3,5"); ui.onNodeWithTag("goal-item-done").performClick()
        waitFor { ui.onAllNodesWithTag("goal-item-quantity").fetchSemanticsNodes().isEmpty() }
        runBlocking { val row = db.shoppingListEntryDao().getById(rowId)!!; assertEquals(3.5, row.quantity, 0.0); assertEquals(.0000001, row.plannedUnitPrice!!, 0.0); assertEquals("Мира, 8", row.customShopAddress); assertNull(row.customShopName); assertEquals(1, db.shoppingListEntryDao().getAllSync().size) }
        ui.onNodeWithTag("goal-entry-$rowId").performClick(); ui.onNodeWithTag("goal-item-quantity").performTextReplacement("ошибка")
        ui.runOnIdle { com.spisokryadom.app.data.repository.ProductEditorTransactions.generation.incrementAndGet(); com.spisokryadom.app.util.DatabaseRevision.invalidate(app) }
        waitFor { ui.onAllNodesWithTag("goal-item-quantity").fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithTag("goal-entry-$rowId").performClick(); ui.onNodeWithTag("goal-item-quantity").performTextReplacement("4"); ui.onNodeWithTag("goal-item-done").performClick()
        waitFor { runBlocking { db.shoppingListEntryDao().getById(rowId)!!.quantity == 4.0 } }
    }

    @Test fun batchBoughtAndMoveKeepTheSameRowsWithoutCopies() {
        val id = runBlocking { repository.create("Пикник") }
        val first = runBlocking { repository.addQuick(id, "Скатерть", 1.0, "шт") }
        val second = runBlocking { repository.addQuick(id, "Стаканы", 12.0, "шт") }
        openList(id); ui.onNodeWithContentDescription("Выбрать позиции").performClick()
        ui.onNodeWithContentDescription("Выбрать: Скатерть").performClick()
        ui.onNodeWithTag("goal-selection-actions").performClick(); ui.onNodeWithText("Отметить купленными").performClick()
        waitFor { runBlocking { db.shoppingListEntryDao().getById(first)!!.isBought } }
        ui.onNodeWithContentDescription("Выбрать позиции").performClick(); ui.onNodeWithContentDescription("Выбрать всё").performClick()
        shot("09-target-selection")
        ui.onNodeWithTag("goal-selection-actions").performClick(); ui.onNodeWithText("В другой список").performClick()
        ui.onNodeWithText("Переместить").performClick()
        waitFor { runBlocking { db.shoppingListEntryDao().getForListSync(id).isEmpty() } }
        runBlocking {
            val rows = db.shoppingListEntryDao().getAllSync(); assertEquals(setOf(first, second), rows.map { it.id }.toSet()); assertTrue(rows.all { it.listId == null })
            assertTrue(rows.single { it.id == first }.isBought); assertEquals(12.0, rows.single { it.id == second }.quantity, 0.0)
        }
        shot("09-target-batch-moved")
    }

    @Test fun doneDuringAnInflightAutosaveCreatesOnlyOnePurchase() {
        val id = runBlocking { repository.create("Ремонт") }; openList(id)
        ui.onNodeWithTag("goal-add-item").performClick(); waitText("Новая позиция")
        val acquired = java.util.concurrent.CountDownLatch(1); val release = java.util.concurrent.CountDownLatch(1)
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        val holder = executor.submit { runBlocking { db.withTransaction { acquired.countDown(); check(release.await(30, java.util.concurrent.TimeUnit.SECONDS)) } } }
        try {
            assertTrue(acquired.await(10, java.util.concurrent.TimeUnit.SECONDS))
            ui.onNodeWithTag("goal-item-name").performTextInput("Обои")
            waitFor { ui.onAllNodesWithTag("goal-item-saving").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithTag("goal-item-done").performClick(); release.countDown(); holder.get(15, java.util.concurrent.TimeUnit.SECONDS)
            waitFor { ui.onAllNodesWithTag("goal-item-name").fetchSemanticsNodes().isEmpty() }
            runBlocking { val rows = db.shoppingListEntryDao().getForListSync(id); assertEquals(1, rows.size); assertEquals("Обои", db.productDao().getById(rows.single().productId)!!.name) }
        } finally { release.countDown(); executor.shutdownNow() }
    }

    @Test fun mainContextMoveAndOpenOriginNavigateWithoutCopyingPurchases() {
        val target = runBlocking { repository.create("Для рецепта") }
        val rowId = runBlocking { app.container.shoppingListRepository.addProduct(banana, .5, choice = com.spisokryadom.app.data.repository.ShopChoice.NONE) }
        waitText("Банан"); ui.onNodeWithTag("purchase-row-$rowId").performTouchInput { longClick() }
        ui.onNodeWithText("Перенести в другой список").performClick(); waitText("Для рецепта")
        ui.onNodeWithText("Для рецепта").performClick(); shot("10-main-move-list")
        ui.onNodeWithText("Перенести").performClick(); waitText("Список пуст")
        runBlocking { val rows = db.shoppingListEntryDao().getAllSync(); assertEquals(1, rows.size); assertEquals(rowId, rows.single().id); assertEquals(target, rows.single().listId) }
        openList(target); ui.onNodeWithTag("goal-entry-$rowId").assertExists()
        runBlocking { repository.updateList(target, "Для рецепта", true) }
        back(); tab("Покупки"); waitText("Банан"); ui.onNodeWithTag("purchase-row-$rowId").performTouchInput { longClick() }
        ui.onNodeWithText("Открыть список «Для рецепта»").performClick(); waitFor { ui.onAllNodesWithTag("goal-entry-$rowId").fetchSemanticsNodes().isNotEmpty() }
        runBlocking { assertEquals(1, db.shoppingListEntryDao().getAllSync().size) }
        shot("11-main-open-origin")
    }
}
