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
import com.spisokryadom.app.ui.shopcontent.ShopContentViewModel
import kotlinx.coroutines.flow.first
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
import java.time.Duration

/** Fresh synthetic Room data; actual purchase IDs stay distinct from catalogue family IDs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class Reliability291ScenariosDataTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: AppDatabase
    private lateinit var locations: LocationRepository
    private lateinit var products: ProductRepository
    private lateinit var basket: ShoppingListRepository
    private val models = ViewModelStore()

    @Before fun setup(): Unit = runBlocking {
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        RetailConstraints.install(db.openHelper.writableDatabase)
        db.cityDao().insert(CityEntity(1, "Город проверки"))
        db.cityDao().insert(CityEntity(2, "Другой город"))
        val prefs = app.getSharedPreferences("reliability291-locations", 0)
        prefs.edit().clear().commit()
        locations = LocationRepository(db.cityDao(), db.shopDao(), prefs)
        products = ProductRepository(db.productDao(), db.productShopLinkDao(), db)
        basket = ShoppingListRepository(db.shoppingListEntryDao(), db, locations)
    }

    private fun await(check: () -> Boolean) {
        val until = System.nanoTime() + 20_000_000_000
        while (!check() && System.nanoTime() < until) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(150))
            Thread.sleep(5)
        }
        assertTrue("Timed out waiting for Room/ViewModel state", check())
    }

    @After fun close() {
        models.clear()
        await { !ProductEditorTransactions.mutex.isLocked }
        locations.closeForTest()
        db.close()
    }

    private suspend fun family(): Pair<Long, Long> {
        val root = db.productDao().insert(ProductEntity(name = "Молоко", defaultUnit = "л", defaultQuantity = 1.0))
        val variant = products.variants.create(root, "Большая упаковка")
        db.productDao().update(db.productDao().getById(variant)!!.copy(defaultQuantity = 2.0))
        return root to variant
    }

    private fun model(shop: Long) = ShopContentViewModel(
        shop, products, ShopRepository(db.shopDao(), db.shopDepartmentDao(), locations, db), basket,
        ClassifierRepository(db.productGroupDao(), db.recipientDao(), db.unitDao(), db)
    ).also { models.put("shop-$shop", it) }

    @Test fun familyLinkToggleFollowsVisibleCityAvailabilityAndRetainsHiddenLinks(): Unit = runBlocking {
        val (root, variant) = family()
        val shop = db.shopDao().insert(ShopEntity(name = "Магазин", cityId = 1))
        val remote = db.shopDao().insert(ShopEntity(name = "Другой профиль", cityId = 2))
        val control = db.productDao().insert(ProductEntity(name = "Контроль загрузки"))
        products.ensureShopLink(control, shop)
        products.ensureShopLink(variant, shop)
        products.ensureShopLink(variant, remote)
        products.variants.configureCity(root, 1, listOf(root to true, variant to false))
        val originalHiddenLinks = products.getShopLinksForProductSync(variant)
        val originalRules = db.productVariantDao().cities()
        val vm = model(shop)
        await { !vm.uiState.value.isLoading && vm.uiState.value.products.any { it.product.id == control } }
        vm.openPickProducts()
        await { vm.uiState.value.pickProducts.any { it.product.id == root && !it.isLinked } }

        // This is the repository command sent by the unchecked visible checkbox.
        products.toggleFamilyShopLink(root, shop)
        assertTrue(products.getCatalogueLinksForShop(shop).first().any { it.productId == root })
        assertEquals(originalHiddenLinks, products.getShopLinksForProductSync(variant))
        await { vm.uiState.value.pickProducts.any { it.product.id == root && it.isLinked } }
        products.toggleFamilyShopLink(root, shop)
        assertTrue(products.getCatalogueLinksForShop(shop).first().none { it.productId == root })
        assertEquals(originalHiddenLinks, products.getShopLinksForProductSync(variant))
        assertEquals(originalRules, db.productVariantDao().cities())
        await { vm.uiState.value.pickProducts.any { it.product.id == root && !it.isLinked } }
    }

    @Test fun physicalPointSectionKeepsTheVariantPurchaseAndChangesItsActualRow(): Unit = runBlocking {
        val (root, variant) = family()
        val shop = db.shopDao().insert(ShopEntity(name = "Магазин", cityId = 1))
        val first = db.shopBranchDao().insert(ShopBranchEntity(shopId = shop, address = "Первый адрес"))
        val second = db.shopBranchDao().insert(ShopBranchEntity(shopId = shop, address = "Второй адрес"))
        products.saveWithLinks(db.productDao().getById(root)!!,
            listOf(ProductShopLinkEntity(productId = root, shopId = shop)), mapOf(shop to setOf(first)))
        products.saveWithLinks(db.productDao().getById(variant)!!,
            listOf(ProductShopLinkEntity(productId = variant, shopId = shop)), mapOf(shop to setOf(second)))
        val own = basket.addProduct(root, choice = ShopChoice.EXPLICIT, shopId = shop, branchId = second)
        val neighbor = basket.addProduct(root, choice = ShopChoice.EXPLICIT, shopId = shop, branchId = first)
        val list = GoalListRepository(db, basket).create("Отдельный список")
        val otherOwner = basket.addProduct(variant, choice = ShopChoice.EXPLICIT, shopId = shop,
            branchId = second, listId = list, exactVariant = true)
        val savedNeighbor = db.shoppingListEntryDao().getById(neighbor)
        val savedOtherOwner = db.shoppingListEntryDao().getById(otherOwner)
        val vm = model(shop)
        await { vm.uiState.value.products.any { it.product.id == root && it.isInShoppingList } &&
            vm.uiState.value.sections.any { it.id == second && it.products.isNotEmpty() } }
        val item = vm.uiState.value.sections.single { it.id == second }.products.single()
        assertTrue("A child-variant purchase must be visible in its physical point", item.isInShoppingList)
        assertEquals(own, item.listEntryId)
        assertEquals(listOf(own), item.purchases.map { it.id })
        assertEquals(2.0, item.listQuantity!!, 0.0)

        // The existing stepper invokes the real purchase ID from this projection.
        vm.incrementEntry(item.listEntryId!!)
        await { runBlocking { db.shoppingListEntryDao().getById(own)?.quantity == 4.0 } }
        assertEquals(variant, db.shoppingListEntryDao().getById(own)!!.productId)
        assertEquals(savedNeighbor, db.shoppingListEntryDao().getById(neighbor))
        assertEquals(savedOtherOwner, db.shoppingListEntryDao().getById(otherOwner))
    }

    @Test fun generalShopSectionRetainsSeparatePurchasesOfAllVariants(): Unit = runBlocking {
        val (root, variant) = family()
        val shop = db.shopDao().insert(ShopEntity(name = "Магазин", cityId = 1))
        val point = db.shopBranchDao().insert(ShopBranchEntity(shopId = shop, address = "Адрес"))
        products.ensureShopLink(root, shop)
        products.ensureShopLink(variant, shop)
        val first = basket.addProduct(root, choice = ShopChoice.EXPLICIT, shopId = shop, branchId = point, exactVariant = true)
        val second = basket.addProduct(variant, choice = ShopChoice.EXPLICIT, shopId = shop, branchId = point, exactVariant = true)
        val vm = model(shop)
        await { vm.uiState.value.products.singleOrNull()?.purchases?.size == 2 && vm.uiState.value.sections.isNotEmpty() }
        val item = vm.uiState.value.sections.single { it.id == null }.products.single()
        assertEquals("Both real rows must remain reachable from the family card", setOf(first, second), item.purchases.map { it.id }.toSet())
        assertNull(item.listEntryId)
        assertEquals(listOf(1.0, 2.0), item.purchases.sortedBy { it.id }.map { it.quantity })
        vm.incrementEntry(second)
        await { runBlocking { db.shoppingListEntryDao().getById(second)?.quantity == 4.0 } }
        assertEquals(1.0, db.shoppingListEntryDao().getById(first)!!.quantity, 0.0)
    }

    @Test fun shoppingTextPreservesTheActualVariantWithoutChangingStoredNames(): Unit = runBlocking {
        val (root, variant) = family()
        basket.addProduct(root, quantity = 1.0, choice = ShopChoice.NONE, exactVariant = true)
        basket.addProduct(variant, quantity = 2.0, choice = ShopChoice.NONE, exactVariant = true)
        val ordinary = db.productDao().insert(ProductEntity(name = "Хлеб", defaultUnit = "шт"))
        basket.addProduct(ordinary, choice = ShopChoice.NONE)
        val hidden = GoalListRepository(db, basket).create("Скрытый список")
        basket.addProduct(variant, quantity = 3.0, listId = hidden, exactVariant = true)
        val bought = basket.addProduct(variant, quantity = 4.0, unit = "уп", choice = ShopChoice.NONE, exactVariant = true)
        basket.markBought(bought)
        val beforeProducts = db.productDao().getAllSync()
        val beforeEntries = db.shoppingListEntryDao().getAllSync()

        val text = com.spisokryadom.app.ui.bulk.renderShoppingText(basket.textSnapshot())
        assertTrue(text.contains("Молоко · Основной вариант — 1 л"))
        assertTrue(text.contains("Молоко · Большая упаковка — 2 л"))
        assertTrue(text.contains("Хлеб — 1 шт"))
        assertFalse(text.contains("Скрытый список"))
        assertFalse(text.contains("4 уп"))
        assertEquals(beforeProducts, db.productDao().getAllSync())
        assertEquals(beforeEntries, db.shoppingListEntryDao().getAllSync())
    }
}
