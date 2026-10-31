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
import com.spisokryadom.app.ui.productcard.ProductCardViewModel
import com.spisokryadom.app.ui.productcard.ProductCardUiState
import com.spisokryadom.app.ui.productcard.ProductDraftStore
import com.spisokryadom.app.ui.productcard.ProductEditDraft
import com.spisokryadom.app.util.ProductEditingPreferences
import kotlinx.coroutines.runBlocking
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
class Reliability291ProductCardTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: AppDatabase
    private lateinit var locations: LocationRepository
    private lateinit var products: ProductRepository
    private lateinit var shops: ShopRepository
    private lateinit var classifiers: ClassifierRepository
    private lateinit var basket: ShoppingListRepository
    private val models = ViewModelStore()
    private val prefs get() = app.getSharedPreferences("291-card-editing", 0)
    private val drafts get() = app.getSharedPreferences("291-card-drafts", 0)
    private var root = 0L
    private var variant = 0L
    private var shop = 0L
    private var branch = 0L
    private var commonDepartment = 0L
    private var legacyDepartment = 0L
    private var legacyType = 0L
    private var purchase = 0L

    private fun await(check: () -> Boolean) {
        val end = System.nanoTime() + 25_000_000_000
        while (!check() && System.nanoTime() < end) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(25))
            Thread.sleep(5)
        }
        assertTrue(check())
    }

    @Before fun setup(): Unit = runBlocking {
        prefs.edit().clear().commit()
        drafts.edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        RetailConstraints.install(db.openHelper.writableDatabase)
        db.cityDao().insert(CityEntity(1, "Тестовый город"))
        locations = LocationRepository(db.cityDao(), db.shopDao(), app.getSharedPreferences("291-card-location", 0))
        products = ProductRepository(db.productDao(), db.productShopLinkDao(), db)
        shops = ShopRepository(db.shopDao(), db.shopDepartmentDao(), locations, db)
        classifiers = ClassifierRepository(db.productGroupDao(), db.recipientDao(), db.unitDao(), db)
        basket = ShoppingListRepository(db.shoppingListEntryDao(), db, locations)
        root = db.productDao().insert(ProductEntity(name = "Сыр", defaultQuantity = 1.0, defaultUnit = "шт"))
        variant = products.variants.create(root, "Другая марка")
        shop = db.shopDao().insert(ShopEntity(name = "Тестовый магазин"))
        branch = db.shopBranchDao().insert(ShopBranchEntity(shopId = shop, address = "Первый адрес"))
        db.shopBranchDao().insert(ShopBranchEntity(shopId = shop, address = "Второй адрес", displayOrder = 1))
        val commonType = db.shopDepartmentDao().insertType(DepartmentTypeEntity(name = "Молочка", normalizedName = "молочка"))
        legacyType = db.shopDepartmentDao().insertType(DepartmentTypeEntity(name = "Сыры", normalizedName = "сыры"))
        commonDepartment = db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId = shop, name = "Молочка", typeId = commonType))
        legacyDepartment = db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId = shop, name = "Сыры", typeId = legacyType))
        products.saveWithLinks(db.productDao().getById(variant)!!,
            listOf(ProductShopLinkEntity(productId = variant, shopId = shop, departmentId = commonDepartment)))
        db.retailStructureDao().setProductDepartment(ProductBranchDepartmentEntity(variant, branch, shop, legacyType))
        purchase = basket.addProduct(variant, choice = ShopChoice.EXPLICIT, shopId = shop, branchId = branch, exactVariant = true)
        assertEquals(legacyDepartment, db.shoppingListEntryDao().getById(purchase)!!.assignedDepartmentId)
    }

    @After fun close() {
        models.clear()
        await { !ProductEditorTransactions.mutex.isLocked }
        locations.closeForTest()
        db.close()
    }

    private fun card(autoSave: Boolean): ProductCardViewModel {
        prefs.edit().putBoolean(ProductEditingPreferences.KEY, autoSave).commit()
        return ProductCardViewModel(root, -1, products, shops, classifiers, drafts, prefs).also {
            models.put("card", it)
            await { !it.uiState.value.isLoading && it.uiState.value.variantOptions.size == 2 }
        }
    }

    private suspend fun assertLegacyAssignmentRetained() {
        assertEquals(listOf(ProductBranchDepartmentEntity(variant, branch, shop, legacyType)), products.branchDepartments(variant))
        val row = db.shoppingListEntryDao().getById(purchase)!!
        assertEquals(variant, row.productId)
        assertEquals(branch, row.assignedBranchId)
        assertEquals(legacyDepartment, row.assignedDepartmentId)
    }

    @Test fun manualSaveAfterVisitingVariantPreservesLegacyPointDepartment(): Unit = runBlocking {
        val card = card(false)
        card.switchVariant(variant)
        await { card.uiState.value.activeVariantId == variant }
        card.updateNote("Заметка без изменения магазина")
        card.save()
        await { card.uiState.value.isSaved }
        assertEquals("Заметка без изменения магазина", db.productDao().getById(variant)!!.note)
        assertLegacyAssignmentRetained()
    }

    @Test fun autosaveWhenLeavingVariantPreservesLegacyPointDepartment(): Unit = runBlocking {
        val card = card(true)
        card.switchVariant(variant)
        await { card.uiState.value.activeVariantId == variant }
        card.switchVariant(root)
        await { card.uiState.value.activeVariantId == root }
        assertLegacyAssignmentRetained()
        // Explicitly applying the current common shop group still removes the old point exception.
        card.switchVariant(variant)
        await { card.uiState.value.activeVariantId == variant }
        card.configureShop(shop, commonDepartment, null, emptyList())
        card.save()
        await { card.uiState.value.isSaved }
        assertTrue(products.branchDepartments(variant).isEmpty())
        assertEquals(commonDepartment, db.shoppingListEntryDao().getById(purchase)!!.assignedDepartmentId)
    }

    @Test fun rawRecoveryKeepsAlreadyLoadedVariantControls(): Unit = runBlocking {
        val product = db.productDao().getById(root)!!
        ProductDraftStore.write(drafts, "product_$root", ProductEditDraft(product.stableId, root, false, false,
            ProductCardUiState(name = product.name, defaultQuantity = "3..", isNew = false, isLoading = false)))
        // Loading the catalogue and the saved raw values are independent jobs. Hold only
        // the editor lock so the real options Flow wins that ordinary startup race.
        ProductEditorTransactions.mutex.lock()
        val model: ProductCardViewModel
        try {
            model = card(true)
            assertEquals(2, model.uiState.value.variantOptions.size)
        } finally {
            ProductEditorTransactions.mutex.unlock()
        }
        await { model.uiState.value.defaultQuantity == "3.." }
        assertEquals(root, model.uiState.value.activeVariantId)
        assertEquals(setOf(root, variant), model.uiState.value.variantOptions.map { it.product.id }.toSet())
        model.updateDefaultQuantity("3")
        model.save()
        await { model.uiState.value.isSaved }
        assertEquals(3.0, db.productDao().getById(root)!!.defaultQuantity!!, 0.0)
    }
}
