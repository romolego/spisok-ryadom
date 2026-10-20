package com.spisokryadom.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.db.RetailConstraints
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.ui.productcard.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

/** Raw choices and successful external catalogue edits survive each other without broadening availability. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class Audit121SynchronizationTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val preferences get() = ProductDraftStore.preferences(app)
    private lateinit var db: AppDatabase
    private lateinit var products: ProductRepository
    private lateinit var shops: ShopRepository
    private lateinit var basket: ShoppingListRepository
    private lateinit var locations: LocationRepository
    private var shop = 0L
    private var otherShop = 0L
    private var first = 0L
    private var second = 0L
    private var foreign = 0L
    private var product = 0L
    private var otherProduct = 0L
    private val rawNote = "Не потерять введённую заметку"

    @Before fun setup() = runBlocking {
        preferences.edit().clear().commit()
        val locationPreferences = app.getSharedPreferences("audit121-sync-locations", 0)
        locationPreferences.edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        RetailConstraints.install(db.openHelper.writableDatabase)
        db.cityDao().insert(CityEntity(1, "Красноярск"))
        locations = LocationRepository(db.cityDao(), db.shopDao(), locationPreferences)
        val edits = ProductDraftSynchronizer(preferences)
        products = ProductRepository(db.productDao(), db.productShopLinkDao(), db, edits)
        shops = ShopRepository(db.shopDao(), db.shopDepartmentDao(), locations, db, edits)
        basket = ShoppingListRepository(db.shoppingListEntryDao(), db, locations, edits)
        shop = db.shopDao().insert(ShopEntity(name = "Батон"))
        otherShop = db.shopDao().insert(ShopEntity(name = "Аллея", displayOrder = 1))
        first = db.shopBranchDao().insert(ShopBranchEntity(shopId = shop, address = "Весны, 16а"))
        second = db.shopBranchDao().insert(ShopBranchEntity(shopId = shop, address = "Молокова, 19", displayOrder = 1))
        foreign = db.shopBranchDao().insert(ShopBranchEntity(shopId = otherShop, address = "78 Добровольческой Бригады, 12"))
        product = products.saveWithLinks(
            ProductEntity(name = "Газировка", defaultQuantity = 1.0, defaultUnit = "бутылка", note = "Записанная заметка"),
            listOf(ProductShopLinkEntity(productId = 0, shopId = shop)),
            availability = mapOf(shop to setOf(first))
        )
        otherProduct = products.saveWithLinks(ProductEntity(name = "Хлеб"), emptyList())
        Unit
    }

    @After fun close() {
        preferences.edit().clear().commit()
        locations.closeForTest()
        db.close()
    }

    private suspend fun draft(points: Map<Long, Set<Long>>, quantity: String = "1"): ProductEditDraft {
        val p = products.getProductById(product)!!
        return ProductEditDraft(p.stableId, p.id, false, false, ProductCardUiState(
            isNew = false, isLoading = false, name = p.name,
            defaultQuantity = quantity, defaultUnit = p.defaultUnit.orEmpty(), note = rawNote,
            preferCityShopOrder = p.preferCityShopOrder,
            shopLinks = products.getShopLinksForProductSync(product).map { ShopLinkUi(it, "Батон", null) },
            branchSelections = points
        )).also { ProductDraftStore.write(preferences, "product_$product", it) }
    }

    private suspend fun currentDraft(): ProductEditDraft = ProductDraftStore.find(preferences, products.getProductById(product)!!.stableId)!!

    @Test fun unrelatedFavoriteKeepsEmptyRawRestrictionAndRecoveryCannotBroadenAvailability() = runBlocking {
        draft(mapOf(shop to emptySet()))
        val original = products.getProductById(product)!!
        val originalLinks = products.getShopLinksForProductSync(product)
        val originalAvailability = products.branchAvailability(product)
        BatchRepository(db,products,basket).changeProducts(setOf(otherProduct),ProductBatchChange.Favorite(true))
        val raw = currentDraft()
        assertTrue("An empty subset is an unfinished restricted choice, not all points", raw.state.branchSelections.containsKey(shop))
        assertTrue(raw.state.branchSelections.getValue(shop).isEmpty())
        assertEquals(rawNote, raw.state.note)
        ProductDraftStore.recover(app, products, shops)
        assertEquals(original, products.getProductById(product))
        assertEquals(originalLinks, products.getShopLinksForProductSync(product))
        assertEquals(originalAvailability, products.branchAvailability(product))
        assertTrue(products.getProductById(otherProduct)!!.isFavorite)
    }

    @Test fun canonicalSubsetExtensionKeepsRawAllPointsChoiceWithoutInventingRestriction() = runBlocking {
        draft(emptyMap())
        val entry = basket.addProduct(product)
        basket.movePending(setOf(entry), shop, second, extendAvailability = true)
        assertEquals(setOf(first, second), products.branchAvailability(product).map { it.branchId }.toSet())
        assertFalse("The raw all-points choice already includes the newly added point", currentDraft().state.branchSelections.containsKey(shop))
        assertEquals(rawNote, currentDraft().state.note)
        ProductDraftStore.recover(app, products, shops)
        assertFalse(products.getShopLinksForProductSync(product).single().restrictBranches)
        assertTrue(products.branchAvailability(product).isEmpty())
        assertEquals(second, db.shoppingListEntryDao().getById(entry)!!.assignedBranchId)
        assertEquals(rawNote, products.getProductById(product)!!.note)
    }

    @Test fun resolvedTemporaryDepartmentFollowsRenameWithoutRecreatingItsFormerName() = runBlocking {
        val initial = draft(mapOf(shop to setOf(first)))
        val temporary = ShopDepartmentEntity(id = -10, shopId = shop, name = "Напитки")
        val raw = initial.copy(state = initial.state.copy(
            pendingDepartments = listOf(temporary),
            shopLinks = initial.state.shopLinks.map { it.copy(link = it.link.copy(departmentId = temporary.id), departmentName = temporary.name) }
        ))
        raw.persist(products)
        ProductDraftStore.write(preferences, "product_$product", raw)
        val placement = db.shopDepartmentDao().getByShopIdSync(shop).single()
        val link = products.getShopLinksForProductSync(product).single()
        shops.renameDepartmentGlobally(placement.typeId, "Соки и газировка")
        assertEquals("Соки и газировка", db.shopDepartmentDao().type(placement.typeId)!!.name)
        ProductDraftStore.recover(app, products, shops)
        val types = db.shopDepartmentDao().types()
        assertEquals("Recovery must retain one group identity", 1, types.size)
        assertEquals(placement.typeId, types.single().id)
        assertEquals("Соки и газировка", types.single().name)
        assertEquals(placement.id, products.getShopLinksForProductSync(product).single().departmentId)
        assertEquals(link.id, products.getShopLinksForProductSync(product).single().id)
        assertEquals(rawNote, products.getProductById(product)!!.note)
        assertEquals(rawNote, currentDraft().state.note)
        assertEquals("Соки и газировка", currentDraft().state.shopLinks.single().departmentName)
        assertEquals("Соки и газировка", currentDraft().state.pendingDepartments.single().name)
    }

    @Test fun deletingLastRawSelectedPointKeepsAnEmptyRestrictionInsteadOfAllRemainingPoints() = runBlocking {
        draft(mapOf(shop to setOf(second)))
        val canonical = products.getProductById(product)!!
        // saveWithDepartments is the public point-removal command used by the shop editor.
        val retained = shops.branches(shop).filter { it.id != second }
        shops.saveWithDepartments(shops.getShopById(shop)!!, shops.getDepartmentsByShopIdSync(shop), branches = retained)
        assertNull(db.shopBranchDao().get(second))
        val raw = currentDraft()
        assertTrue("Removing the selected point cannot imply permission for every point", raw.state.branchSelections.containsKey(shop))
        assertTrue(raw.state.branchSelections.getValue(shop).isEmpty())
        assertEquals(rawNote, raw.state.note)
        ProductDraftStore.recover(app, products, shops)
        assertEquals(canonical, products.getProductById(product))
        assertTrue(products.getShopLinksForProductSync(product).single().restrictBranches)
        assertEquals(setOf(first), products.branchAvailability(product).map { it.branchId }.toSet())
    }

    @Test fun addingStorePointDoesNotExpandRestrictedProductAvailabilityDuringRecovery() = runBlocking {
        draft(mapOf(shop to setOf(first)))
        val originalPoints=shops.branches(shop)
        shops.saveWithDepartments(shops.getShopById(shop)!!,shops.getDepartmentsByShopIdSync(shop),originalPoints+ShopBranchEntity(shopId=shop,address="Алексеева, 10"))
        val added=shops.branches(shop).single{it.address=="Алексеева, 10"}.id
        assertEquals(shop, db.shopBranchDao().get(added)!!.shopId)
        assertEquals(setOf(first), products.branchAvailability(product).map { it.branchId }.toSet())
        assertEquals(setOf(first), currentDraft().state.branchSelections[shop])
        ProductDraftStore.recover(app, products, shops)
        assertEquals(setOf(first), products.branchAvailability(product).map { it.branchId }.toSet())
        assertTrue(products.getShopLinksForProductSync(product).single().restrictBranches)
        assertEquals(rawNote, products.getProductById(product)!!.note)
        shops.saveWithDepartments(shops.getShopById(shop)!!,shops.getDepartmentsByShopIdSync(shop),shops.branches(shop))
        assertEquals(3, shops.branches(shop).size)
    }

    @Test fun failedExternalCommandRollsBackDatabaseAndLeavesExactRawDraftIntact() = runBlocking {
        draft(mapOf(shop to emptySet()), quantity = "1,")
        val rawBefore = preferences.all.toMap()
        val productBefore = db.productDao().getAllSync()
        val linksBefore = db.productShopLinkDao().getAllSync()
        val availabilityBefore = db.shopBranchDao().allAvailability()
        val shopsBefore = db.shopDao().getAllSync()
        val branchesBefore = db.shopBranchDao().getAll()
        val typesBefore = db.shopDepartmentDao().types()
        val placesBefore = db.shopDepartmentDao().getAllSync()
        val firstPoint = db.shopBranchDao().get(first)!!
        val otherShopPoint = db.shopBranchDao().get(foreign)!!
        val result = runCatching {
            shops.saveWithDepartments(
                shops.getShopById(shop)!!.copy(name = "Не должен сохраниться"),
                listOf(ShopDepartmentEntity(id = -1, shopId = shop, name = "Временная группа")),
                branches = listOf(firstPoint, otherShopPoint)
            )
        }
        assertTrue("A foreign point must reject the command after earlier writes", result.isFailure)
        assertEquals(productBefore, db.productDao().getAllSync())
        assertEquals(linksBefore, db.productShopLinkDao().getAllSync())
        assertEquals(availabilityBefore, db.shopBranchDao().allAvailability())
        assertEquals(shopsBefore, db.shopDao().getAllSync())
        assertEquals(branchesBefore, db.shopBranchDao().getAll())
        assertEquals(typesBefore, db.shopDepartmentDao().types())
        assertEquals(placesBefore, db.shopDepartmentDao().getAllSync())
        assertEquals("Failed commands must not sanitize or rewrite unrelated raw values", rawBefore, preferences.all)
    }

    @Test fun nullableCommandResultExecutesExactlyOnceWithAndWithoutSynchronizer() = runBlocking {
        listOf<CatalogueEdits?>(null, ProductDraftSynchronizer(preferences)).forEach { boundary ->
            var calls = 0
            val result: Long? = withCatalogueEdits(db, boundary) { calls++; null }
            assertNull(result)
            assertEquals("A successful null result must not repeat a command", 1, calls)
        }
    }

    @Test fun singleUrgencyRejectsBoughtPurchaseJustLikeBatchAndPreservesHistory() = runBlocking {
        val id = basket.addProduct(product)
        basket.markBought(id)
        val before = db.shoppingListEntryDao().getById(id)!!
        assertTrue(runCatching { basket.toggleUrgent(id,strictPending=true) }.isFailure)
        assertEquals(before, db.shoppingListEntryDao().getById(id))
        // Legacy record-editing callers retain their established history API;
        // the user action explicitly opts into the same pending policy as bulk.
        basket.toggleUrgent(id)
        val historical = db.shoppingListEntryDao().getById(id)!!
        assertTrue(historical.isUrgent)
        assertEquals(before, historical.copy(isUrgent=before.isUrgent,updatedAt=before.updatedAt))
    }

    @Test fun unrelatedFavoriteKeepsTemporaryDepartmentLabelsInBothDraftLinkRepresentations() = runBlocking {
        val initial = draft(mapOf(shop to setOf(first)), quantity = "0,")
        val temporary = ShopDepartmentEntity(id = -10, shopId = shop, name = "Напитки")
        val raw = initial.copy(state = initial.state.copy(
            pendingDepartments = listOf(temporary),
            shopLinks = initial.state.shopLinks.map { it.copy(link = it.link.copy(departmentId = temporary.id), departmentName = temporary.name) },
            pendingShopLinks = listOf(PendingShopLink(20, shop, "Батон", temporary.id, temporary.name, 1))
        ))
        ProductDraftStore.write(preferences, "product_$product", raw)
        val canonical = products.getProductById(product)!!
        BatchRepository(db,products,basket).changeProducts(setOf(otherProduct),ProductBatchChange.Favorite(true))
        val preserved = currentDraft()
        assertEquals("0,", preserved.state.defaultQuantity)
        assertEquals(rawNote, preserved.state.note)
        assertEquals(temporary.id, preserved.state.shopLinks.single().link.departmentId)
        assertEquals(temporary.id, preserved.state.pendingShopLinks.single().departmentId)
        assertEquals("Напитки", preserved.state.shopLinks.single().departmentName)
        assertEquals("Напитки", preserved.state.pendingShopLinks.single().departmentName)
        val reopened = preserved.applyTo(ProductCardUiState(isLoading = false))
        assertEquals("Напитки", reopened.shopLinks.single().departmentName)
        assertEquals("Напитки", reopened.pendingShopLinks.single().departmentName)
        ProductDraftStore.recover(app, products, shops)
        assertEquals("Unrelated command cannot commit an invalid raw card", canonical, products.getProductById(product))
        assertEquals("Напитки", currentDraft().state.shopLinks.single().departmentName)
        assertEquals("Напитки", currentDraft().state.pendingShopLinks.single().departmentName)
    }

    @Test fun singleAndBatchLinkAdditionKeepAlreadyConfiguredRawStoreChoices() = runBlocking {
        val edits = ProductDraftSynchronizer(preferences)
        val batch = BatchRepository(db, products, basket, edits)
        listOf(false, true).forEachIndexed { index, mass ->
            product = products.saveWithLinks(ProductEntity(name = "Напиток $index", defaultQuantity = 1.0),
                listOf(ProductShopLinkEntity(productId = 0, shopId = shop)))
            val initial = draft(mapOf(otherShop to setOf(foreign)), quantity = "0,")
            val department = ShopDepartmentEntity(id = -10, shopId = otherShop, name = "Напитки")
            val link = ProductShopLinkEntity(id = -20, productId = product, shopId = otherShop, departmentId = department.id, priority = 5, restrictBranches = true)
            val raw = initial.copy(state = initial.state.copy(
                shopLinks = initial.state.shopLinks + ShopLinkUi(link, "Аллея", department.name),
                pendingShopLinks = listOf(PendingShopLink(20, otherShop, "Аллея", department.id, department.name, 5)),
                pendingDepartments = listOf(department)
            ))
            ProductDraftStore.write(preferences, "product_$product", raw)
            if (mass) {
                val change = ProductBatchChange.Shops(setOf(otherShop))
                ProductDraftStore.applyBatch(app, products, setOf(product), change) { batch.changeProducts(setOf(product), change) }
            } else products.ensureShopLink(product, otherShop)
            val preserved = currentDraft()
            assertEquals(setOf(foreign), preserved.state.branchSelections[otherShop])
            val chosen = preserved.state.shopLinks.single { it.link.shopId == otherShop }
            assertEquals(department.id, chosen.link.departmentId)
            assertEquals(5, chosen.link.priority)
            assertEquals("Напитки", chosen.departmentName)
            assertEquals(department.id, preserved.state.pendingShopLinks.single().departmentId)
            assertEquals("0,", preserved.state.defaultQuantity)
            assertEquals(rawNote, preserved.state.note)
            preserved.copy(state = preserved.state.copy(defaultQuantity = "2")).persist(products)
            val canonical = products.getShopLinksForProductSync(product).single { it.shopId == otherShop }
            assertTrue(canonical.restrictBranches)
            assertEquals("Напитки", db.shopDepartmentDao().getById(canonical.departmentId!!)!!.name)
            assertEquals(setOf(foreign), products.branchAvailability(product).filter { it.shopId == otherShop }.map { it.branchId }.toSet())
            assertEquals(rawNote, products.getProductById(product)!!.note)
        }
    }

    @Test fun classifierCleanupPreservesDifferentValidRawChoicesAndTemporaryDepartment() = runBlocking {
        val classifiers = ClassifierRepository(db.productGroupDao(), db.recipientDao(), db.unitDao(), db, ProductDraftSynchronizer(preferences))
        val oldRecipient = classifiers.insertRecipient(RecipientEntity(name = "Рома"))
        val newRecipient = classifiers.insertRecipient(RecipientEntity(name = "Злата"))
        val oldGroup = classifiers.insertGroup(ProductGroupEntity(name = "Продукты"))
        val newGroup = classifiers.insertGroup(ProductGroupEntity(name = "Бытовая химия"))
        val oldUnit = classifiers.insertUnit(UnitEntity(name = "бутылка"))
        classifiers.insertUnit(UnitEntity(name = "л"))
        val oldDepartment = db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId = shop, name = "Молочка"))
        val type = db.shopDepartmentDao().getById(oldDepartment)!!.typeId
        products.saveWithLinks(products.getProductById(product)!!.copy(recipientId = oldRecipient, productGroupId = oldGroup),
            products.getShopLinksForProductSync(product).map { it.copy(departmentId = oldDepartment) }, mapOf(shop to setOf(first)))
        val initial = draft(mapOf(shop to setOf(first)), quantity = "0,")
        val temporary = ShopDepartmentEntity(id = -10, shopId = shop, name = "Напитки")
        val raw = initial.copy(state = initial.state.copy(
            recipientId = newRecipient, productGroupId = newGroup, defaultUnit = "л",
            pendingDepartments = listOf(temporary),
            shopLinks = initial.state.shopLinks.map { it.copy(link = it.link.copy(departmentId = temporary.id), departmentName = temporary.name) }
        ))
        ProductDraftStore.write(preferences, "product_$product", raw)
        classifiers.deleteRecipient(classifiers.getRecipientById(oldRecipient)!!)
        classifiers.deleteGroup(classifiers.getGroupById(oldGroup)!!)
        classifiers.renameUnitAndProducts(classifiers.getUnitById(oldUnit)!!, "флакон")
        shops.deleteDepartmentGlobally(type)
        val preserved = currentDraft()
        assertEquals(newRecipient, preserved.state.recipientId)
        assertEquals(newGroup, preserved.state.productGroupId)
        assertEquals("л", preserved.state.defaultUnit)
        assertEquals(temporary.id, preserved.state.shopLinks.single().link.departmentId)
        assertEquals("Напитки", preserved.state.shopLinks.single().departmentName)
        assertEquals("0,", preserved.state.defaultQuantity)
        assertEquals(rawNote, preserved.state.note)
        ProductDraftStore.recover(app, products, shops)
        assertNull(products.getProductById(product)!!.recipientId)
        assertEquals("флакон", products.getProductById(product)!!.defaultUnit)
        preserved.copy(state = preserved.state.copy(defaultQuantity = "2")).persist(products)
        val saved = products.getProductById(product)!!
        assertEquals(newRecipient, saved.recipientId)
        assertEquals(newGroup, saved.productGroupId)
        assertEquals("л", saved.defaultUnit)
        assertEquals(rawNote, saved.note)
        val department = products.getShopLinksForProductSync(product).single().departmentId!!
        assertEquals("Напитки", db.shopDepartmentDao().getById(department)!!.name)
    }
}
