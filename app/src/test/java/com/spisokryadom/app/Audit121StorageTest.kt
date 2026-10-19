package com.spisokryadom.app

import android.app.Application
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

/** Independent regression audit: durable edits must not be undone by a prior editor draft. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class Audit121StorageTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: AppDatabase
    private lateinit var products: ProductRepository
    private lateinit var shops: ShopRepository
    private lateinit var basket: ShoppingListRepository
    private lateinit var classifiers: ClassifierRepository
    private lateinit var locations: LocationRepository
    private var a = 0L
    private var b = 0L
    private var aPoint = 0L
    private var bPoint = 0L
    private var product = 0L
    @Volatile private var afterOuterCommit: (() -> Unit)? = null

    private val commitHooks = SupportSQLiteOpenHelper.Factory { configuration ->
        val delegate = FrameworkSQLiteOpenHelperFactory().create(configuration)
        fun wrapped(database: SupportSQLiteDatabase) = object : SupportSQLiteDatabase by database {
            override fun endTransaction() {
                database.endTransaction()
                if (!database.inTransaction()) {
                    val callback = afterOuterCommit
                    afterOuterCommit = null
                    callback?.invoke()
                }
            }
        }
        object : SupportSQLiteOpenHelper by delegate {
            override val writableDatabase: SupportSQLiteDatabase get() = wrapped(delegate.writableDatabase)
            override val readableDatabase: SupportSQLiteDatabase get() = wrapped(delegate.readableDatabase)
        }
    }

    @Before fun setup() = runBlocking {
        ProductDraftStore.preferences(app).edit().clear().commit()
        app.getSharedPreferences("audit121-locations", 0).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).openHelperFactory(commitHooks).allowMainThreadQueries().build()
        RetailConstraints.install(db.openHelper.writableDatabase)
        db.cityDao().insert(CityEntity(1, "Красноярск"))
        locations = LocationRepository(db.cityDao(), db.shopDao(), app.getSharedPreferences("audit121-locations", 0))
        val edits=ProductDraftSynchronizer(ProductDraftStore.preferences(app))
        products = ProductRepository(db.productDao(), db.productShopLinkDao(), db,edits)
        shops = ShopRepository(db.shopDao(), db.shopDepartmentDao(), locations, db,edits)
        basket = ShoppingListRepository(db.shoppingListEntryDao(), db, locations,edits)
        classifiers = ClassifierRepository(db.productGroupDao(), db.recipientDao(), db.unitDao(), db,edits)
        a = db.shopDao().insert(ShopEntity(name = "Батон"))
        b = db.shopDao().insert(ShopEntity(name = "Красный Яр", displayOrder = 1))
        aPoint = db.shopBranchDao().insert(ShopBranchEntity(shopId = a, address = "Весны, 16а"))
        bPoint = db.shopBranchDao().insert(ShopBranchEntity(shopId = b, address = "Весны, 1"))
        product = products.saveWithLinks(
            ProductEntity(name = "Газировка", defaultUnit = "бутылка", defaultQuantity = 1.0, note = "Исходная заметка"),
            listOf(ProductShopLinkEntity(productId = 0, shopId = a, priority = 1))
        )
        Unit
    }

    @After fun close() {
        afterOuterCommit = null
        ProductDraftStore.preferences(app).edit().clear().commit()
        locations.closeForTest()
        db.close()
    }

    private suspend fun keepInterruptedEditor(): ProductEditDraft {
        val p = products.getProductById(product)!!
        val names = db.shopDao().getAllSync().associateBy { it.id }
        val draft = ProductEditDraft(p.stableId, p.id, false, false, ProductCardUiState(
            isNew = false, isLoading = false, name = p.name,
            recipientId = p.recipientId, productGroupId = p.productGroupId,
            defaultUnit = p.defaultUnit.orEmpty(), defaultQuantity = "1",
            note = "Сохранённый ввод пользователя", preferCityShopOrder = p.preferCityShopOrder,
            shopLinks = products.getShopLinksForProductSync(product).map { ShopLinkUi(it, names.getValue(it.shopId).name, null) },
            branchSelections = products.getShopLinksForProductSync(product).filter { it.restrictBranches }.associate { link ->
                link.shopId to products.branchAvailability(product).filter { it.shopId == link.shopId }.map { it.branchId }.toSet()
            },
            branchPriorities = products.branchPriorities(product).groupBy { it.shopId }.mapValues { (_, rows) -> rows.sortedBy { it.displayOrder }.map { it.branchId } }
        ))
        ProductDraftStore.write(ProductDraftStore.preferences(app), "product_$product", draft)
        return draft
    }

    @Test fun rememberedTransferCannotBeUndoneByInterruptedEditorRecovery() = runBlocking {
        products.ensureShopLink(product, b)
        keepInterruptedEditor()
        val entry = basket.addProduct(product)
        assertEquals(a, db.shoppingListEntryDao().getById(entry)!!.assignedShopId)
        basket.movePending(setOf(entry), b, bPoint, rememberPriority = true)
        assertEquals(b, db.shoppingListEntryDao().getById(entry)!!.assignedShopId)
        assertEquals(b, products.getShopLinksForProductSync(product).minBy { it.priority }.shopId)
        ProductDraftStore.recover(app, products, shops)
        assertEquals("Recovery must retain the explicitly remembered store priority", b,
            products.getShopLinksForProductSync(product).minBy { it.priority }.shopId)
        assertEquals("Recovery must not move the purchase back", b, db.shoppingListEntryDao().getById(entry)!!.assignedShopId)
        assertEquals("Сохранённый ввод пользователя", products.getProductById(product)!!.note)
    }

    @Test fun transferExtensionCannotBeRemovedByInterruptedEditorRecovery() = runBlocking {
        keepInterruptedEditor()
        val entry = basket.addProduct(product)
        basket.movePending(setOf(entry), b, bPoint, extendAvailability = true)
        assertTrue(products.getShopLinksForProductSync(product).any { it.shopId == b })
        ProductDraftStore.recover(app, products, shops)
        assertTrue("Recovery must keep the store added by a successful transfer",
            products.getShopLinksForProductSync(product).any { it.shopId == b })
        assertTrue(products.branchAvailability(product).any { it.branchId == bPoint })
        assertEquals(bPoint, db.shoppingListEntryDao().getById(entry)!!.assignedBranchId)
    }

    @Test fun classifierUnitRenameCannotBeRevertedByInterruptedEditorRecovery() = runBlocking {
        val unitId = classifiers.insertUnit(UnitEntity(name = "бутылка"))
        keepInterruptedEditor()
        val entry = basket.addProduct(product)
        classifiers.renameUnitAndProducts(classifiers.getUnitById(unitId)!!, "флакон")
        assertEquals("флакон", products.getProductById(product)!!.defaultUnit)
        assertEquals("флакон", db.shoppingListEntryDao().getById(entry)!!.unit)
        ProductDraftStore.recover(app, products, shops)
        assertEquals("Recovery must retain the newer global unit edit", "флакон", products.getProductById(product)!!.defaultUnit)
        assertEquals("флакон", db.shoppingListEntryDao().getById(entry)!!.unit)
        assertEquals("Сохранённый ввод пользователя", products.getProductById(product)!!.note)
    }

    @Test fun deletedRecipientAndGroupDoNotTrapAnInterruptedEditorInForeignKeyErrors() = runBlocking {
        val recipient = classifiers.insertRecipient(RecipientEntity(name = "Рома"))
        val group = classifiers.insertGroup(ProductGroupEntity(name = "Продукты"))
        val p = products.getProductById(product)!!
        products.saveWithLinks(p.copy(recipientId = recipient, productGroupId = group), products.getShopLinksForProductSync(product))
        keepInterruptedEditor()
        classifiers.deleteRecipient(classifiers.getRecipientById(recipient)!!)
        classifiers.deleteGroup(classifiers.getGroupById(group)!!)
        assertNull(products.getProductById(product)!!.recipientId)
        assertNull(products.getProductById(product)!!.productGroupId)
        ProductDraftStore.recover(app, products, shops)
        assertEquals("Recovery must keep unrelated entered text after clearing deleted references",
            "Сохранённый ввод пользователя", products.getProductById(product)!!.note)
        val reopened = ProductDraftStore.find(ProductDraftStore.preferences(app), p.stableId)!!
        assertNull(reopened.state.recipientId)
        assertNull(reopened.state.productGroupId)
        reopened.copy(state = reopened.state.copy(defaultQuantity = "2")).persist(products)
        assertEquals(2.0, products.getProductById(product)!!.defaultQuantity!!, 0.0)
    }

    @Test fun deletedStoreDepartmentCannotBeRecreatedFromResolvedTemporaryDraftIds() = runBlocking {
        val raw = keepInterruptedEditor()
        val temporaryDepartment = ShopDepartmentEntity(id = -10, shopId = a, name = "Напитки")
        val edited = raw.copy(state = raw.state.copy(
            pendingDepartments = listOf(temporaryDepartment),
            shopLinks = raw.state.shopLinks.map { it.copy(link = it.link.copy(departmentId = temporaryDepartment.id), departmentName = temporaryDepartment.name) }
        ))
        // This is the same raw snapshot kept after canonical autosave resolves -10.
        edited.persist(products)
        ProductDraftStore.write(ProductDraftStore.preferences(app), "product_$product", edited)
        val canonical = db.shopDepartmentDao().getByShopIdSync(a).single()
        shops.deleteDepartmentGlobally(canonical.typeId)
        assertNull(db.shopDepartmentDao().type(canonical.typeId))
        assertNull(products.getShopLinksForProductSync(product).single().departmentId)
        ProductDraftStore.recover(app, products, shops)
        assertTrue("Deleted global group must not be resurrected from a resolved temporary ID",
            db.shopDepartmentDao().types().none { it.name == "Напитки" })
        assertTrue(db.shopDepartmentDao().getByShopIdSync(a).isEmpty())
        assertNull(products.getShopLinksForProductSync(product).single().departmentId)
        assertEquals("Сохранённый ввод пользователя", products.getProductById(product)!!.note)
    }

    @Test fun cancellationAtRestoreCommitCannotDeleteCommittedPhotosOrLeaveOldDrafts() = runBlocking {
        val image = java.io.File(app.filesDir, "product_photos/audit121_${java.util.UUID.randomUUID()}.jpg")
        image.parentFile!!.mkdirs()
        image.writeBytes(byteArrayOf(1, 3, 5, 7))
        val before = products.getProductById(product)!!
        products.saveWithLinks(before.copy(photoUri = image.absolutePath), products.getShopLinksForProductSync(product))
        val preferences = app.getSharedPreferences("product_db_prefs", 0)
        preferences.edit().putBoolean("favorites_only", true).commit()
        val backup = com.spisokryadom.app.data.backup.BackupManager(app, db)
        val archive = backup.exportFile()
        try {
            products.saveWithLinks(products.getProductById(product)!!.copy(name = "После копии"), products.getShopLinksForProductSync(product))
            preferences.edit().putBoolean("favorites_only", false).commit()
            keepInterruptedEditor()
            val generation = ProductEditorTransactions.generation.get()
            val hookInvoked = java.util.concurrent.atomic.AtomicBoolean()
            val restoring = launch(Dispatchers.IO, start = CoroutineStart.LAZY) { backup.importFile(archive) }
            // Simulate closing the caller precisely after SQLite commits, before its
            // suspended withTransaction continuation can set BackupManager.committed.
            afterOuterCommit = {
                hookInvoked.set(true)
                restoring.cancel(CancellationException("Caller closed after commit"))
            }
            restoring.start()
            withTimeout(20_000) { restoring.join() }
            afterOuterCommit = null
            val restored = products.getProductById(product)!!
            assertTrue("The caller must actually be cancelled at the commit boundary", hookInvoked.get() && restoring.isCancelled)
            assertEquals("The hook must have run after the replacement committed", "Газировка", restored.name)
            assertTrue("Cancellation must not remove photos referenced by committed rows", java.io.File(restored.photoUri!!).isFile)
            assertTrue("Committed restore must invalidate old editors", ProductEditorTransactions.generation.get() > generation)
            assertTrue("Committed restore must remove pre-restore drafts", ProductDraftStore.preferences(app).all.isEmpty())
            assertTrue("Committed restore must finish restoring preferences", preferences.getBoolean("favorites_only", false))
        } finally {
            afterOuterCommit = null
            backup.discard(archive)
            image.delete()
            preferences.edit().clear().commit()
        }
    }
}
