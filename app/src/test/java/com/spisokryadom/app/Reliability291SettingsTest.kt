package com.spisokryadom.app

import android.net.Uri
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.ProductEntity
import com.spisokryadom.app.data.repository.ProductEditorTransactions
import com.spisokryadom.app.ui.settings.SettingsViewModel
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.*
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = SpisokRyadomApp::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class Reliability291SettingsTest {
    private val app get() = ApplicationProvider.getApplicationContext<SpisokRyadomApp>()
    private val db get() = app.container.database
    private var controller: ActivityController<ComponentActivity>? = null

    private fun await(check: () -> Boolean) {
        val end = System.nanoTime() + 25_000_000_000
        while (!check() && System.nanoTime() < end) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(25))
            Thread.sleep(5)
        }
        assertTrue(check())
    }

    private fun activity(saved: Bundle? = null): SettingsViewModel {
        val next = Robolectric.buildActivity(ComponentActivity::class.java).create(saved).start().resume()
        controller = next
        return ViewModelProvider(next.get(), SettingsViewModel.Factory)[SettingsViewModel::class.java]
    }

    private fun restoreAfterProcessRecreation(): SettingsViewModel {
        val saved = Bundle()
        controller!!.pause().saveInstanceState(saved).stop().destroy()
        controller = null
        // Deliberately create a new Activity and ViewModelStore, rather than retain the VM
        // as configuration-change recreation does. Only Android's saved Bundle survives.
        return activity(saved)
    }

    @Before fun seed(): Unit = runBlocking {
        db.productDao().insert(ProductEntity(name = "Синтетический товар для копии", defaultUnit = "шт", defaultQuantity = 2.0))
    }

    @After fun close() {
        controller?.pause()?.stop()?.destroy()
        await { !ProductEditorTransactions.mutex.isLocked }
        app.container.locations.closeForTest()
        db.close()
        org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java, "INSTANCE", null)
    }

    private fun export(model: SettingsViewModel): File {
        model.prepareExport()
        await { model.uiState.value.pendingExportPath != null && !model.uiState.value.isLoading }
        model.exportLaunched()
        return File(model.uiState.value.pendingExportPath!!)
    }

    @Test fun documentResultAfterProcessRecreationWritesThePreparedBackup() {
        val before = activity()
        val source = export(before)
        val bytes = source.readBytes()
        val restored = restoreAfterProcessRecreation()
        assertNotSame(before, restored)
        val target = File(app.cacheDir, "291-selected-export.zip").apply { writeBytes(byteArrayOf()) }
        restored.saveExport(Uri.fromFile(target))
        await { !restored.uiState.value.isLoading }
        assertArrayEquals(bytes, target.readBytes())
        assertTrue(restored.uiState.value.message.orEmpty().startsWith("Полная резервная копия сохранена"))
        assertNull(restored.uiState.value.pendingExportPath)
        assertFalse(source.exists())
        val repeated = File(app.cacheDir, "291-repeated-export.zip").apply { writeBytes(byteArrayOf(9, 8, 7)) }
        restored.saveExport(Uri.fromFile(repeated))
        assertArrayEquals(byteArrayOf(9, 8, 7), repeated.readBytes())
        assertNull(restoreAfterProcessRecreation().uiState.value.pendingExportPath)
    }

    @Test fun cancelledDocumentResultAfterProcessRecreationDiscardsOnlyTemporaryExport(): Unit = runBlocking {
        val before = activity()
        val source = export(before)
        val products = db.productDao().getAllSync()
        val restored = restoreAfterProcessRecreation()
        assertEquals(source.absolutePath, restored.uiState.value.pendingExportPath)
        assertTrue(restored.uiState.value.exportLaunched)
        restored.saveExport(null)
        await { !source.exists() }
        assertNull(restored.uiState.value.pendingExportPath)
        assertNull(restored.uiState.value.message)
        assertEquals(products, db.productDao().getAllSync())
    }

    @Test fun importPreviewSurvivesProcessRecreationAndCancelPreservesDatabase(): Unit = runBlocking {
        val before = activity()
        val source = app.container.backupManager.exportFile()
        before.receiveImport(Uri.fromFile(source))
        await { before.uiState.value.pendingImportPath != null && !before.uiState.value.isLoading }
        val pending = File(before.uiState.value.pendingImportPath!!)
        val summary = before.uiState.value.importSummary
        val products = db.productDao().getAllSync()
        val restored = restoreAfterProcessRecreation()
        assertEquals(pending.absolutePath, restored.uiState.value.pendingImportPath)
        assertEquals(summary, restored.uiState.value.importSummary)
        restored.cancelImport()
        assertFalse(pending.exists())
        assertNull(restored.uiState.value.pendingImportPath)
        assertNull(restored.uiState.value.importSummary)
        assertEquals(products, db.productDao().getAllSync())
        app.container.backupManager.discard(source)
    }

    @Test fun missingPreparedFileAfterProcessRecreationReportsFailureInsteadOfSuccess() {
        val source = export(activity())
        assertTrue(source.delete())
        val restored = restoreAfterProcessRecreation()
        val target = File(app.cacheDir, "291-missing-export.zip")
        restored.saveExport(Uri.fromFile(target))
        await { !restored.uiState.value.isLoading }
        assertTrue(restored.uiState.value.message.orEmpty().startsWith("Не удалось записать копию:"))
        assertNull(restored.uiState.value.pendingExportPath)
    }

    @Test fun missingPreparedFileDoesNotTruncateTheSelectedDocument() {
        val model = activity()
        val source = export(model)
        assertTrue(source.delete())
        val original = byteArrayOf(4, 3, 2, 1)
        val target = File(app.cacheDir, "291-existing-document.zip").apply { writeBytes(original) }
        model.saveExport(Uri.fromFile(target))
        await { !model.uiState.value.isLoading }
        assertTrue(model.uiState.value.message.orEmpty().startsWith("Не удалось записать копию:"))
        assertArrayEquals(original, target.readBytes())
    }
}
