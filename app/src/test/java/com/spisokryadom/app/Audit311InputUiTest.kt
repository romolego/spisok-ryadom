package com.spisokryadom.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.ui.addtolist.BulkProductSheet
import com.spisokryadom.app.ui.addtolist.BulkProductViewModel
import com.spisokryadom.app.ui.theme.SpisokRyadomTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Audit311InputUiTest {
    @get:Rule val ui = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: AppDatabase
    private lateinit var places: LocationRepository
    private lateinit var vm: BulkProductViewModel
    private val models = ViewModelStore()
    private var dismissed = false

    @Before fun setup(): Unit = runBlocking {
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        db.cityDao().insert(CityEntity(1, "Город"))
        val prefs = app.getSharedPreferences("audit311-input-ui-location", 0)
        prefs.edit().clear().commit()
        places = LocationRepository(db.cityDao(), db.shopDao(), prefs)
        vm = BulkProductViewModel(BulkPurchaseRepository(db, ShoppingListRepository(db.shoppingListEntryDao(), db, places), places), SavedStateHandle())
        models.put("bulk", vm)
    }

    @After fun close() {
        models.clear()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        places.closeForTest(); db.close()
    }

    private fun show() = ui.setContent {
        SpisokRyadomTheme { BulkProductSheet(null, null, { dismissed = true }, {}, viewModel = vm) }
    }

    private fun waitState(condition: () -> Boolean) = ui.waitUntil(25_000) {
        // The stand-alone Compose host does not advance ViewModel Main tasks
        // while a predicate only reads StateFlow (see Audit201UiTest).
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(20))
        condition()
    }

    private fun shot(name: String) = ui.runOnIdle {
        val view = org.robolectric.shadows.ShadowDialog.getLatestDialog()!!.window!!.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val file = File("build/reports/audit311/screens/$name-api${android.os.Build.VERSION.SDK_INT}.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun thirteenthSameNameCandidateCanBeSelectedAndCancellationWritesNothing() {
        val last = runBlocking { (1..13).map { index ->
            db.productDao().insert(ProductEntity(name = "Молоко", defaultUnit = if (index == 13) "л" else "упак"))
        }.last() }
        show()
        ui.onNodeWithTag("bulk-input").performTextInput("Молоко")
        ui.onNodeWithTag("bulk-confirm").performClick()
        waitState { vm.state.value.preview || vm.state.value.error != null }
        assertNull(vm.state.value.error)
        ui.onNodeWithTag("bulk-confirm").assertIsNotEnabled()
        ui.onNodeWithTag("bulk-choose-0").performClick()
        waitState { !vm.state.value.searching && vm.state.value.candidates.isNotEmpty() }
        ui.onNodeWithTag("bulk-candidates").performScrollToNode(hasTestTag("bulk-candidate-$last"))
        ui.onNodeWithTag("bulk-candidate-$last").assertIsDisplayed().performClick()
        waitState { vm.state.value.choosing == null }
        assertEquals(last, vm.state.value.rows.single().productId)
        ui.onNodeWithTag("bulk-confirm").assertIsEnabled()
        shot("bulk-thirteenth-choice")
        ui.onNodeWithContentDescription("Закрыть массовый ввод").performClick()
        assertTrue(dismissed)
        assertTrue(runBlocking { db.shoppingListEntryDao().getAllSync().isEmpty() })
        assertEquals(13, runBlocking { db.productDao().getAllSync().size })
    }

    @Test @Config(qualifiers = "w640dp-h360dp-xhdpi")
    fun landscapeInputKeepsItsExplanationReachableAndPreviewActionsVisible() {
        show()
        ui.onNodeWithTag("bulk-input").performTextInput((1..12).joinToString("\n") { "Новый товар $it" })
        ui.onNodeWithText("Единицы и количество в тексте указывать не нужно. Перед добавлением появится предпросмотр.").assertDoesNotExist()
        ui.onNodeWithContentDescription("Подсказка: Товары").performScrollTo().performClick()
        ui.onNodeWithText("Понятно").assertIsDisplayed().performClick()
        ui.onNodeWithTag("bulk-confirm").assertIsDisplayed().assertIsEnabled()
        shot("bulk-input-landscape")
        ui.onNodeWithTag("bulk-confirm").performClick()
        waitState { vm.state.value.preview || vm.state.value.error != null }
        assertNull(vm.state.value.error)
        ui.onNodeWithTag("bulk-confirm").assertIsDisplayed().assertIsNotEnabled()
        ui.onNodeWithTag("bulk-preview").performScrollToNode(hasTestTag("bulk-row-11"))
        ui.onNodeWithTag("bulk-row-11").assertIsDisplayed()
        ui.onNodeWithContentDescription("Закрыть массовый ввод").performClick()
        assertTrue(dismissed)
        assertTrue(runBlocking { db.shoppingListEntryDao().getAllSync().isEmpty() })
    }
}
