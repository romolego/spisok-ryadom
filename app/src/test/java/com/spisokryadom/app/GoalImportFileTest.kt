package com.spisokryadom.app

import android.app.Application
import android.net.Uri
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.db.RetailConstraints
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.domain.*
import com.spisokryadom.app.ui.goallists.GoalImportViewModel
import com.spisokryadom.app.ui.goallists.GoalListViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.SQLiteMode
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Exercises the actual ContentResolver read/decode/preview/write boundary, not just the codec. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class GoalImportFileTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: AppDatabase
    private lateinit var location: LocationRepository
    private lateinit var shopping: ShoppingListRepository
    private lateinit var goals: GoalListRepository
    private lateinit var directory: File
    private lateinit var drafts: File
    private val store = ViewModelStore()
    private val files = mutableListOf<File>()
    private val savedState = SavedStateHandle()
    private var sequence = 0
    private val document = GoalListDocument("Салат на праздник", listOf(
        GoalListItemDraft("Огурцы | свежие", .75, "кг", 125.25, "Без горечи\nне солёные", "Батон", "Весны, 16а", isBought = true, isUrgent = true),
        GoalListItemDraft("Огурцы | свежие", 2.0, "шт", .0000001, "Другая позиция", "Рынок — павильон", "Мира, 8"),
    ))

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        RetailConstraints.install(db.openHelper.writableDatabase)
        db.cityDao().insert(CityEntity(1, "Город"))
        val prefs = app.getSharedPreferences("goal-file-${UUID.randomUUID()}", 0)
        location = LocationRepository(db.cityDao(), db.shopDao(), prefs)
        shopping = ShoppingListRepository(db.shoppingListEntryDao(), db, location)
        goals = GoalListRepository(db, shopping)
        val product = db.productDao().insert(ProductEntity(name = "Исходная покупка", defaultUnit = "кг"))
        shopping.addProduct(product, 3.0, choice = ShopChoice.NONE)
        // An identically named local store must not create an implicit link during file import.
        db.shopDao().insert(ShopEntity(name = "Батон"))
        directory = File(app.cacheDir, "goal-import-file-${UUID.randomUUID()}").also { it.mkdirs() }
        drafts = File(directory, "drafts").also { it.mkdirs() }
    }

    @After fun cleanup() {
        store.clear()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        location.closeForTest()
        db.close()
        files.forEach { it.delete() }
        drafts.listFiles()?.forEach { it.delete() }
        drafts.delete(); directory.delete()
    }
    private fun model() = GoalImportViewModel(goals, savedState, drafts).also { store.put("import", it) }
    private fun state() = runBlocking { Triple(db.goalListDao().getAllSync(), db.shoppingListEntryDao().getAllSync(), db.productDao().getAllSync()) }
    private fun file(bytes: ByteArray) = File(directory, "source-${++sequence}.json").also { it.writeBytes(bytes); files += it }
    private fun jsonFile() = file(GoalListTransfer.exportJson(document).toByteArray(Charsets.UTF_8))
    private fun await(message: String, condition: () -> Boolean) {
        val until = System.nanoTime() + 15_000_000_000L
        while (!condition() && System.nanoTime() < until) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertTrue(message, condition())
    }
    private fun select(model: GoalImportViewModel, source: File) {
        model.file(app.contentResolver, Uri.fromFile(source))
        await("File read/preview must finish") { !model.uiState.value.busy }
    }

    @Test fun utf8JsonFilePreviewsWithoutWritesThenImportsOnceWithAllPortableFacts() = runBlocking {
        val before = state(); val source = jsonFile(); val bytes = source.readBytes(); val model = model()
        select(model, source)
        assertNull(model.uiState.value.error)
        assertEquals(document, model.uiState.value.preview!!.document)
        assertTrue(model.uiState.value.preview!!.canImport)
        assertEquals(before, state())
        model.confirm(); model.confirm()
        await("Confirmed file must create a new list") { !model.uiState.value.busy && model.uiState.value.importedId != null }
        // Calling confirm again after completion must not create another owner.
        model.confirm()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        val owner = db.goalListDao().getAllSync().single()
        assertEquals("Салат на праздник", owner.name)
        assertFalse(owner.showInPurchases)
        val rows = db.shoppingListEntryDao().getForListSync(owner.id)
        assertEquals(2, rows.size)
        val first = rows.single { it.quantity == .75 }
        val second = rows.single { it.quantity == 2.0 }
        assertEquals(125.25, first.plannedUnitPrice!!, 0.0)
        assertEquals(.0000001, second.plannedUnitPrice!!, 0.0)
        assertEquals("кг", first.unit)
        assertEquals("Без горечи\nне солёные", first.note)
        assertEquals("Батон", first.customShopName)
        assertEquals("Весны, 16а", first.customShopAddress)
        assertEquals("Рынок — павильон", second.customShopName)
        assertTrue(first.isUrgent)
        assertTrue(rows.none { it.isBought || it.assignedShopId != null || it.assignedBranchId != null })
        assertNotEquals(first.productId, second.productId)
        assertTrue(rows.all { db.productDao().getById(it.productId)!!.isTemporary })
        assertEquals(before.second, db.shoppingListEntryDao().getForListSync(null))
        assertEquals(1, db.productDao().getAllSync().count { !it.isTemporary })
        assertArrayEquals(bytes, source.readBytes())
        assertNull(model.uiState.value.preview)
        assertNull(savedState.get<String>("goal_import_text"))
    }

    @Test fun selectingReadableTextWithBomAndWindowsNewlinesCanBeCancelledWithoutMutation() {
        val before = state(); val model = model()
        val text = "\uFEFF" + GoalListTransfer.exportText(document).replace("\n", "\r\n")
        val source = file(text.toByteArray(Charsets.UTF_8)); val bytes = source.readBytes()
        select(model, source)
        assertEquals(GoalListTransferFormat.TEXT, model.uiState.value.preview!!.format)
        assertEquals(document, model.uiState.value.preview!!.document)
        assertEquals(before, state())
        // This is the sheet's explicit Cancel action after selecting a valid document.
        model.discard()
        assertEquals("", model.uiState.value.text)
        assertNull(model.uiState.value.preview)
        assertNull(model.uiState.value.importedId)
        assertNull(savedState.get<String>("goal_import_text"))
        assertNull(savedState.get<String>("goal_import_text_file"))
        assertTrue(drafts.listFiles().orEmpty().isEmpty())
        assertEquals(before, state())
        assertArrayEquals(bytes, source.readBytes())
        model.confirm()
        assertEquals(before, state())
    }

    @Test fun malformedUtf8ClearsOldPreviewRejectsConfirmationAndPreservesExistingData() {
        val before = state(); val model = model()
        select(model, jsonFile())
        assertTrue(model.uiState.value.preview!!.canImport)
        val oldText = model.uiState.value.text
        select(model, file(byteArrayOf(0xC3.toByte(), 0x28)))
        assertEquals("Файл должен быть в кодировке UTF-8", model.uiState.value.error)
        assertNull(model.uiState.value.preview)
        assertNull(model.uiState.value.importedId)
        assertEquals(oldText, model.uiState.value.text)
        model.confirm()
        assertEquals(before, state())
    }

    @Test fun fileLimitCountsUtf8BytesAndAcceptsExactBoundaryWithoutTruncating() {
        val before = state(); val model = model()
        val oversized = "Ж".repeat(GoalListTransfer.MAX_INPUT_BYTES / 2 + 1).toByteArray(Charsets.UTF_8)
        assertTrue(oversized.size > GoalListTransfer.MAX_INPUT_BYTES)
        assertTrue(oversized.size / 2 < GoalListTransfer.MAX_INPUT_BYTES)
        select(model, file(oversized))
        assertEquals("Список больше 1 МБ", model.uiState.value.error)
        assertNull(model.uiState.value.preview)
        model.confirm()
        assertEquals(before, state())
        val content = GoalListTransfer.exportJson(document).toByteArray(Charsets.UTF_8)
        val boundary = ByteArray(GoalListTransfer.MAX_INPUT_BYTES) { 32 }
        content.copyInto(boundary)
        select(model, file(boundary))
        assertNull(model.uiState.value.error)
        assertEquals(document, model.uiState.value.preview!!.document)
        assertEquals(GoalListTransfer.MAX_INPUT_BYTES, model.uiState.value.text.toByteArray(Charsets.UTF_8).size)
        assertNull(savedState.get<String>("goal_import_text"))
        assertNotNull(savedState.get<String>("goal_import_text_file"))
        assertEquals(before, state())
        model.discard()
        assertTrue(drafts.listFiles().orEmpty().isEmpty())
    }

    @Test fun clearingViewModelWhileProviderReadsCancelsPreviewAndClosesInputWithoutWrites() {
        val before = state(); val model = model()
        val uri = Uri.parse("content://goal-import-cancellation/list.json")
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val closed = AtomicBoolean(false)
        val source = ByteArrayInputStream(GoalListTransfer.exportJson(document).toByteArray(Charsets.UTF_8))
        val blocking = object : InputStream() {
            override fun read(): Int = source.read()
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS)) { "Test provider was not released" }
                return source.read(buffer, offset, length)
            }
            override fun close() { source.close(); closed.set(true) }
        }
        Shadows.shadowOf(app.contentResolver).registerInputStream(uri, blocking)
        try {
            model.file(app.contentResolver, uri)
            await("Provider read must start") { entered.count == 0L }
            store.clear()
        } finally { release.countDown() }
        await("Cancelled provider input must close") { closed.get() && !model.uiState.value.busy }
        assertNull(model.uiState.value.preview)
        assertNull(model.uiState.value.error)
        assertEquals("", model.uiState.value.text)
        assertTrue(drafts.listFiles().orEmpty().isEmpty())
        assertEquals(before, state())
    }

    @Test fun unreadableSelectionDoesNotWriteAndSubsequentValidSelectionCanStillPreview() {
        val before = state(); val model = model()
        val missing = File(directory, "missing.json")
        assertFalse(missing.exists())
        select(model, missing)
        assertNotNull(model.uiState.value.error)
        assertNull(model.uiState.value.preview)
        assertEquals(before, state())
        select(model, jsonFile())
        assertNull(model.uiState.value.error)
        assertEquals(document, model.uiState.value.preview!!.document)
        assertEquals(before, state())
    }

    private fun copiedState(source: SavedStateHandle) = SavedStateHandle(source.keys().associateWith { source.get<Any?>(it) })
    private fun listModel(id: Long, handle: SavedStateHandle, revision: String) = GoalListViewModel(
        id, goals, ProductRepository(db.productDao(), db.productShopLinkDao(), db),
        ShopRepository(db.shopDao(), db.shopDepartmentDao(), location, db), handle, drafts, revision
    ).also { store.put("export", it) }

    @Test fun preparedExportRestoresThroughSavedStateCacheTokenAndWritesTheSameJsonSnapshot() = runBlocking {
        val large = document.copy(items = document.items.map { it.copy(note = "Подробная заметка ".repeat(400)) })
        val id = goals.importDocument(large)
        val expected = goals.exportDocument(id); val before = state()
        val original = listModel(id, savedState, "database-A")
        var filename: String? = null
        original.prepareFile { filename = it }
        await("Export snapshot must be prepared") { !original.uiState.value.busy && filename != null }
        assertTrue(filename!!.endsWith(".json"))
        assertNull(savedState.get<String>("goal_export_json"))
        assertNotNull(savedState.get<String>("goal_export_json_file"))
        val restoredState = copiedState(savedState)
        store.clear(); Shadows.shadowOf(Looper.getMainLooper()).idle()
        val restored = listModel(id, restoredState, "database-A")
        val output = File(directory, "export-after-recreation.json").also { files += it }
        restored.writeFile(app.contentResolver, Uri.fromFile(output))
        await("Recreated ViewModel must save its prepared file") { !restored.uiState.value.busy && restored.uiState.value.message != null }
        assertNull(restored.uiState.value.error)
        val parsed = GoalListTransfer.parse(output.readText(Charsets.UTF_8))
        assertTrue(parsed.errors.joinToString(), parsed.canImport)
        assertEquals(expected, parsed.document)
        assertEquals(before, state())
        assertNull(restoredState.get<String>("goal_export_json"))
        assertNull(restoredState.get<String>("goal_export_json_file"))
    }

    @Test fun databaseRevisionChangeDiscardsPreparedExportBeforeAnyOutputIsWritten() = runBlocking {
        val id = goals.importDocument(document); val before = state()
        val original = listModel(id, savedState, "database-before-restore")
        var ready = false
        original.prepareFile { ready = true }
        await("Original export must be ready") { ready && !original.uiState.value.busy }
        val restoredState = copiedState(savedState)
        store.clear(); Shadows.shadowOf(Looper.getMainLooper()).idle()
        val replacement = listModel(id, restoredState, "database-after-restore")
        val output = File(directory, "stale-export.json").also { files += it }
        replacement.writeFile(app.contentResolver, Uri.fromFile(output))
        await("Old export must report that it is unavailable") { !replacement.uiState.value.busy && replacement.uiState.value.error != null }
        assertEquals("Откройте экспорт списка ещё раз", replacement.uiState.value.error)
        assertFalse(output.exists())
        assertNull(restoredState.get<String>("goal_export_json"))
        assertNull(restoredState.get<String>("goal_export_json_file"))
        assertEquals(before, state())
    }

    @Test fun completedImportAndCommitWindowSessionRestoreNeverDuplicateTheImportedList() = runBlocking {
        val source = jsonFile(); val original = model()
        select(original, source); original.confirm()
        await("Original import must finish") { !original.uiState.value.busy && original.uiState.value.importedId != null }
        val id = original.uiState.value.importedId!!
        val imported = state(); val completedState = copiedState(savedState)
        val commitWindowState = copiedState(savedState).also { it.remove<Long>("goal_import_completed_id") }
        store.clear(); Shadows.shadowOf(Looper.getMainLooper()).idle()
        val completed = GoalImportViewModel(goals, completedState, drafts).also { store.put("completed", it) }
        assertEquals(id, completed.uiState.value.importedId)
        select(completed, source); completed.confirm()
        assertEquals(imported, state())
        val commitWindow = GoalImportViewModel(goals, commitWindowState, drafts).also { store.put("commit-window", it) }
        select(commitWindow, source); commitWindow.confirm()
        await("Recovered import session must resolve to the existing owner") { !commitWindow.uiState.value.busy && commitWindow.uiState.value.importedId != null }
        assertEquals(id, commitWindow.uiState.value.importedId)
        assertEquals(imported, state())
        assertEquals(1, db.goalListDao().getAllSync().size)
    }
}
