package com.spisokryadom.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.db.RetailConstraints
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

/** Independent integration checks: the same catalogue command must have the same basket effect. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class Audit121DomainTest {
    private lateinit var db: AppDatabase
    private lateinit var locations: LocationRepository
    private lateinit var products: ProductRepository
    private lateinit var basket: ShoppingListRepository
    private lateinit var batch: BatchRepository
    private var shop = 0L
    private var point = 0L

    @Before fun setup() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        RetailConstraints.install(db.openHelper.writableDatabase)
        db.cityDao().insert(CityEntity(1, "Красноярск"))
        locations = LocationRepository(db.cityDao(), db.shopDao(), app.getSharedPreferences("audit121domain", 0))
        locations.select(1)
        products = ProductRepository(db.productDao(), db.productShopLinkDao(), db)
        basket = ShoppingListRepository(db.shoppingListEntryDao(), db, locations)
        batch = BatchRepository(db, products, basket)
        shop = db.shopDao().insert(ShopEntity(name = "Батон"))
        point = db.shopBranchDao().insert(ShopBranchEntity(shopId = shop, address = "Весны, 16а"))
        Unit
    }

    @After fun close() {
        locations.closeForTest()
        db.close()
    }

    @Test fun singleShopPickerAndBatchLinkResolveTheSameAutomaticPurchase() = runBlocking {
        val single = db.productDao().insert(ProductEntity(name = "Одиночное добавление"))
        val multiple = db.productDao().insert(ProductEntity(name = "Массовое добавление"))
        val x = basket.addProduct(single, quantity = 3.0, unit = "шт")
        val y = basket.addProduct(multiple, quantity = 3.0, unit = "шт")
        val explicit = basket.addProduct(single, choice = ShopChoice.NONE)
        val bought = basket.addProduct(single, quantity = 7.0, unit = "пакет")
        basket.markBought(bought)
        val before = db.shoppingListEntryDao().getById(x)!!
        val historical = db.shoppingListEntryDao().getById(bought)

        batch.changeProducts(setOf(multiple), ProductBatchChange.Shops(setOf(shop)))
        products.toggleShopLink(single, shop)

        val actual = db.shoppingListEntryDao().getById(x)!!
        val reference = db.shoppingListEntryDao().getById(y)!!
        assertEquals("Shop picker and batch must apply one live-assignment policy", reference.assignedShopId, actual.assignedShopId)
        assertEquals(shop, actual.assignedShopId)
        assertEquals(point, actual.assignedBranchId)
        assertNull(actual.awaitingShopCityId)
        assertEquals(before.quantity, actual.quantity, 0.0)
        assertEquals(before.createdAt, actual.createdAt)
        assertTrue(db.shoppingListEntryDao().getById(explicit)!!.explicitNoShop)
        assertNull(db.shoppingListEntryDao().getById(explicit)!!.assignedShopId)
        assertEquals(historical, db.shoppingListEntryDao().getById(bought))
    }

    @Test fun copyingShopLinkResolvesWaitingPurchaseAndNeverMovesValidExplicitPurchase() = runBlocking {
        val p = db.productDao().insert(ProductEntity(name = "Творог"))
        val waiting = basket.addProduct(p)
        val other = db.shopDao().insert(ShopEntity(name = "Красный Яр", displayOrder = 1))
        val selected = basket.addProduct(p, quantity = 2.0, choice = ShopChoice.EXPLICIT, shopId = other)
        val selectedBefore = db.shoppingListEntryDao().getById(selected)

        products.ensureShopLink(p, shop)
        products.ensureShopLink(p, shop)

        val actual = db.shoppingListEntryDao().getById(waiting)!!
        assertEquals("A new link must resolve a purchase waiting for a matching shop", shop, actual.assignedShopId)
        assertEquals(point, actual.assignedBranchId)
        assertNull(actual.awaitingShopCityId)
        assertEquals(selectedBefore, db.shoppingListEntryDao().getById(selected))
        assertEquals(1, products.getShopLinksForProductSync(p).size)
    }

    @Test fun linkAdditionUsesTheWaitingPurchasesCityAndNewPointDepartment() = runBlocking {
        val p = db.productDao().insert(ProductEntity(name = "Сок"))
        val entry = basket.addProduct(p, quantity = 4.0, unit = "бутылка")
        val second = db.shopBranchDao().insert(ShopBranchEntity(shopId = shop, address = "Молокова", displayOrder = 1))
        val department = db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId = shop, name = "Напитки"))
        db.cityDao().insert(CityEntity(2, "Томск"))
        locations.select(2)

        products.ensureShopLinks(
            p, listOf(shop),
            availability = mapOf(shop to setOf(second)),
            branchDepartments = mapOf(second to department)
        )

        val row = db.shoppingListEntryDao().getById(entry)!!
        assertEquals(shop, row.assignedShopId)
        assertEquals(second, row.assignedBranchId)
        assertEquals(department, row.assignedDepartmentId)
        assertEquals(4.0, row.quantity, 0.0)
        assertEquals("бутылка", row.unit)
        assertNull(row.awaitingShopCityId)
    }

    @Test fun staleUiMoveRejectsTheWholeSelectionBeforeChangingAvailabilityOrPriority() = runBlocking {
        val p = products.saveWithLinks(
            ProductEntity(name = "Творог", preferCityShopOrder = true),
            listOf(ProductShopLinkEntity(productId = 0, shopId = shop)),
            availability = mapOf(shop to setOf(point)), branchPriorities = mapOf(shop to listOf(point))
        )
        val q = products.saveWithLinks(
            ProductEntity(name = "Молоко", preferCityShopOrder = true),
            listOf(ProductShopLinkEntity(productId = 0, shopId = shop)),
            availability = mapOf(shop to setOf(point)), branchPriorities = mapOf(shop to listOf(point))
        )
        val x = basket.addProduct(p, quantity = 3.0)
        val y = basket.addProduct(q, quantity = 2.0)
        val selectedWhilePending = setOf(x, y)
        val target = db.shopDao().insert(ShopEntity(name = "Красный Яр", displayOrder = 1))
        val targetPoint = db.shopBranchDao().insert(ShopBranchEntity(shopId = target, address = "Молокова, 54"))
        basket.markBought(y) // The widget changes this purchase after the picker opens.
        val purchasesBefore = db.shoppingListEntryDao().getAllSync()
        val productsBefore = db.productDao().getAllSync()
        val linksBefore = db.productShopLinkDao().getAllSync()
        val pointsBefore = db.shopBranchDao().allAvailability()
        val prioritiesBefore = db.shopBranchDao().priorities()

        for (staleSelection in listOf(selectedWhilePending, setOf(y))) {
            val failure = runCatching {
                basket.movePending(staleSelection, target, targetPoint, rememberPriority = true, extendAvailability = true, strictPending = true)
            }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
            assertEquals(purchasesBefore, db.shoppingListEntryDao().getAllSync())
            assertEquals(productsBefore, db.productDao().getAllSync())
            assertEquals(linksBefore, db.productShopLinkDao().getAllSync())
            assertEquals(pointsBefore, db.shopBranchDao().allAvailability())
            assertEquals(prioritiesBefore, db.shopBranchDao().priorities())
        }
        // The established pending-only repository command still accepts mixed sets.
        assertEquals(1, basket.movePending(selectedWhilePending, target, targetPoint, rememberPriority = true, extendAvailability = true))
        assertEquals(targetPoint, db.shoppingListEntryDao().getById(x)!!.assignedBranchId)
        assertEquals(purchasesBefore.first { it.id == y }, db.shoppingListEntryDao().getById(y))
        assertTrue(products.getShopLinksForProductSync(p).any { it.shopId == target })
        assertTrue(products.getShopLinksForProductSync(q).none { it.shopId == target })
    }

    @Test fun marketConversionPreservesTheProductSpecificPreferredPavilion() = runBlocking {
        assertPreferredPavilionAfterConversion(false)
    }

    @Test fun marketConversionPreservesPointOverrideWithInheritedCityShopOrder() = runBlocking {
        assertPreferredPavilionAfterConversion(true)
    }

    @Test fun conversionPreservesCompetingRetailersAndOrdersInAnotherCity() = runBlocking {
        val before = db.shopDao().insert(ShopEntity(name = "Раньше рынка", displayOrder = -1))
        val after = db.shopDao().insert(ShopEntity(name = "После рынка", displayOrder = 10))
        val second = db.shopBranchDao().insert(ShopBranchEntity(shopId = shop, address = "68 павильон", displayOrder = 1))
        db.cityDao().insert(CityEntity(2, "Томск"))
        val foreignFirst = db.shopDao().insert(ShopEntity(name = "Первый в Томске", cityId = 2, displayOrder = 1))
        val foreignLast = db.shopDao().insert(ShopEntity(name = "Последний в Томске", cityId = 2, displayOrder = 2))
        val p = products.saveWithLinks(
            ProductEntity(name = "Молоко", preferCityShopOrder = true),
            listOf(after, foreignLast, shop, foreignFirst, before).mapIndexed { i, id -> ProductShopLinkEntity(productId = 0, shopId = id, priority = i + 1) },
            branchPriorities = mapOf(shop to listOf(second, point))
        )
        ShopRepository(db.shopDao(), db.shopDepartmentDao(), locations, db).convertToVenue(shop)
        val preferredPavilion = db.shopBranchDao().get(second)!!.shopId
        val fallbackPavilion = db.shopBranchDao().get(point)!!.shopId

        suspend fun consumeRetailers(expected: List<Long>) {
            for (expectedShop in expected) {
                val entry = basket.addProduct(p)
                assertEquals(expectedShop, db.shoppingListEntryDao().getById(entry)!!.assignedShopId)
                basket.removeEntry(entry)
                products.deleteShopLink(products.getShopLinksForProductSync(p).first { it.shopId == expectedShop })
            }
        }
        consumeRetailers(listOf(before, preferredPavilion, fallbackPavilion, after))
        locations.select(2)
        consumeRetailers(listOf(foreignFirst, foreignLast))
    }

    @Test fun conversionRetainsCityInheritanceWhenNoIndividualRouteConflicts() = runBlocking {
        val other = db.shopDao().insert(ShopEntity(name = "После рынка", displayOrder = 10))
        db.shopBranchDao().insert(ShopBranchEntity(shopId = shop, address = "68 павильон", displayOrder = 1))
        val p = products.saveWithLinks(
            ProductEntity(name = "Овощи", preferCityShopOrder = true),
            listOf(ProductShopLinkEntity(productId = 0, shopId = other, priority = 1), ProductShopLinkEntity(productId = 0, shopId = shop, priority = 2))
        )
        ShopRepository(db.shopDao(), db.shopDepartmentDao(), locations, db).convertToVenue(shop)

        assertTrue(products.getProductById(p)!!.preferCityShopOrder)
        val entry = basket.addProduct(p)
        assertEquals(point, db.shoppingListEntryDao().getById(entry)!!.assignedBranchId)
    }

    private suspend fun assertPreferredPavilionAfterConversion(inheritCityOrder: Boolean) {
        val second = db.shopBranchDao().insert(ShopBranchEntity(shopId = shop, address = "68 павильон", displayOrder = 1))
        val p = products.saveWithLinks(
            ProductEntity(name = "Сыр", preferCityShopOrder = inheritCityOrder),
            listOf(ProductShopLinkEntity(productId = 0, shopId = shop)),
            branchPriorities = mapOf(shop to listOf(second, point))
        )
        val before = basket.addProduct(p)
        assertEquals(second, db.shoppingListEntryDao().getById(before)!!.assignedBranchId)
        basket.markBought(before)
        val old = db.shoppingListEntryDao().getById(before)!!

        ShopRepository(db.shopDao(), db.shopDepartmentDao(), locations, db).convertToVenue(shop)
        val next = basket.addProduct(p)

        assertEquals("Structural conversion must preserve the user's preferred physical purchase place", second, db.shoppingListEntryDao().getById(next)!!.assignedBranchId)
        val historical = db.shoppingListEntryDao().getById(before)!!
        assertEquals(old.assignedBranchId, historical.assignedBranchId)
        assertEquals(old.quantity, historical.quantity, 0.0)
        assertEquals(old.createdAt, historical.createdAt)
        assertTrue(historical.isBought)
        db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
    }
}
