package com.spisokryadom.app

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.db.RetailConstraints
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.ui.productcard.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.*
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class Audit121ActiveEditorTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val preferences get() = ProductDraftStore.preferences(app)
    private val editors = ViewModelStore()
    private lateinit var db: AppDatabase
    private lateinit var products: ProductRepository
    private lateinit var shops: ShopRepository
    private lateinit var classifiers: ClassifierRepository
    private lateinit var basket: ShoppingListRepository
    private lateinit var locations: LocationRepository
    private var editor: ProductCardViewModel? = null
    private var product = 0L
    private var unit = 0L

    private suspend fun await(condition: () -> Boolean) {
        val end = System.nanoTime() + 20_000_000_000
        while (!condition() && System.nanoTime() < end) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20))
            yield()
            Thread.sleep(5)
        }
        assertTrue("Active editor did not finish: ${editor?.uiState?.value}", condition())
    }

    @Before fun setup() = runBlocking {
        ProductEditorTransactions.generation.incrementAndGet()
        preferences.edit().clear().commit()
        val locationPrefs = app.getSharedPreferences("audit121-active-locations", 0)
        locationPrefs.edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        RetailConstraints.install(db.openHelper.writableDatabase)
        db.cityDao().insert(CityEntity(1, "Красноярск"))
        locations = LocationRepository(db.cityDao(), db.shopDao(), locationPrefs)
        val edits = ProductDraftSynchronizer(preferences)
        products = ProductRepository(db.productDao(), db.productShopLinkDao(), db, edits)
        shops = ShopRepository(db.shopDao(), db.shopDepartmentDao(), locations, db, edits)
        classifiers = ClassifierRepository(db.productGroupDao(), db.recipientDao(), db.unitDao(), db, edits)
        basket = ShoppingListRepository(db.shoppingListEntryDao(), db, locations, edits)
        unit = classifiers.insertUnit(UnitEntity(name = "бутылка"))
        product = products.saveWithLinks(ProductEntity(name = "Газировка", defaultUnit = "бутылка", defaultQuantity = 1.0, note = "Исходная заметка"), emptyList())
        Unit
    }

    @After fun close() = runBlocking {
        editors.clear()
        await { !ProductEditorTransactions.mutex.isLocked }
        locations.closeForTest()
        db.close()
    }

    @Test fun activeEditorKeepsExternalUnitRenameAndNewLocalInputWhenFinishing() = runBlocking {
        val stable = products.getProductById(product)!!.stableId
        val entry = basket.addProduct(product)
        val purchase = db.shoppingListEntryDao().getById(entry)!!
        val vm = ProductCardViewModel(product, productRepository = products, shopRepository = shops, classifierRepository = classifiers, draftPreferences = preferences)
        editor = vm
        editors.put("card", vm)
        await { !vm.uiState.value.isLoading && vm.uiState.value.defaultUnit == "бутылка" }
        vm.updateDefaultQuantity("0,")
        vm.updateNote("Незавершённая заметка")
        await { ProductDraftStore.find(preferences, stable)?.state?.let { it.defaultQuantity == "0," && it.note == "Незавершённая заметка" } == true }
        classifiers.renameUnitAndProducts(classifiers.getUnitById(unit)!!, "флакон")
        assertEquals("флакон", products.getProductById(product)!!.defaultUnit)
        assertEquals("флакон", ProductDraftStore.find(preferences, stable)!!.state.defaultUnit)
        vm.updateNote("Заметка после внешней правки")
        await { ProductDraftStore.find(preferences, stable)?.state?.note == "Заметка после внешней правки" }
        val raw = ProductDraftStore.find(preferences, stable)!!
        assertEquals("A later local note must not restore the old unit into the durable draft", "флакон", raw.state.defaultUnit)
        assertEquals("0,", raw.state.defaultQuantity)
        vm.updateDefaultQuantity("2")
        vm.finishEditing()
        await { vm.uiState.value.isSaved }
        val saved = products.getProductById(product)!!
        assertEquals("флакон", saved.defaultUnit)
        assertEquals(2.0, saved.defaultQuantity!!, 0.0)
        assertEquals("Заметка после внешней правки", saved.note)
        val updatedPurchase = db.shoppingListEntryDao().getById(entry)!!
        assertEquals("флакон", updatedPurchase.unit)
        assertEquals(purchase.quantity, updatedPurchase.quantity, 0.0)
        assertEquals(purchase.id, updatedPurchase.id)
        assertEquals(purchase.createdAt, updatedPurchase.createdAt)
        assertTrue(preferences.all.isEmpty())
    }

    @Test fun clearingEditorWhileExternalCommandOwnsLockRebasesItsLastRawWrite() = runBlocking {
        val stable = products.getProductById(product)!!.stableId
        val vm = ProductCardViewModel(product, productRepository = products, shopRepository = shops, classifierRepository = classifiers, draftPreferences = preferences)
        editor = vm
        editors.put("card", vm)
        await { !vm.uiState.value.isLoading && vm.uiState.value.defaultUnit == "бутылка" }
        vm.updateDefaultQuantity("0,")
        vm.updateNote("До последнего ввода")
        await { ProductDraftStore.find(preferences, stable)?.state?.defaultQuantity == "0," }
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val mutation = launch(Dispatchers.IO) {
            ProductEditorTransactions.locked {
                entered.complete(Unit)
                release.await()
                classifiers.renameUnitAndProducts(classifiers.getUnitById(unit)!!, "флакон")
            }
        }
        try {
            await { entered.isCompleted }
            vm.updateNote("Последний символ перед закрытием")
            editors.clear()
            release.complete(Unit)
            await { mutation.isCompleted && !ProductEditorTransactions.mutex.isLocked }
            mutation.join()
            val raw = ProductDraftStore.find(preferences, stable)!!
            assertEquals("флакон", raw.state.defaultUnit)
            assertEquals("0,", raw.state.defaultQuantity)
            assertEquals("Последний символ перед закрытием", raw.state.note)
            assertEquals("флакон", products.getProductById(product)!!.defaultUnit)
            assertEquals("Исходная заметка", products.getProductById(product)!!.note)
        } finally {
            release.complete(Unit)
            mutation.cancelAndJoin()
        }
    }

    @Test fun availabilityRebaseKeepsLocalAllPointsAndMergesOnlyExplicitSubsetDeltas() {
        val shop = 10L
        val a = 11L
        val b = 12L
        val c = 13L
        val base = ProductEditDraft("active-points", 1, false, false, ProductCardUiState(
            isNew = false, isLoading = false, name = "Газировка", branchSelections = mapOf(shop to setOf(a))
        ))
        val remote = base.copy(state = base.state.copy(branchSelections = mapOf(shop to setOf(a, b))))
        val allPoints = base.copy(state = base.state.copy(branchSelections = emptyMap(), defaultQuantity = "0,", note = "Новый локальный ввод"))
        val unrestricted = allPoints.rebaseExternal(base, remote)
        assertFalse("Expanding an older subset cannot undo the user's all-points choice", unrestricted.state.branchSelections.containsKey(shop))
        assertEquals("0,", unrestricted.state.defaultQuantity)
        assertEquals("Новый локальный ввод", unrestricted.state.note)
        val localSubset = base.copy(state = base.state.copy(branchSelections = mapOf(shop to setOf(c))))
        assertEquals("Keep the local removal/addition while adding the externally selected point",
            setOf(b, c), localSubset.rebaseExternal(base, remote).state.branchSelections[shop])
        val formerlyUnrestricted = base.copy(state = base.state.copy(branchSelections = emptyMap()))
        assertEquals("A newly introduced external restriction remains an explicit choice",
            setOf(a, b), formerlyUnrestricted.rebaseExternal(formerlyUnrestricted, remote).state.branchSelections[shop])
    }
}

