package com.spisokryadom.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.ShopChoice
import com.spisokryadom.app.util.DatabaseRevision
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.*
import java.io.File
import java.time.Duration
import java.util.UUID
import java.util.zip.ZipFile
import org.json.JSONObject

/** Independent regression scenarios: retained selection and bought-action consistency. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = SpisokRyadomApp::class, qualifiers = "w360dp-h640dp-xhdpi")
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Audit121UiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<SpisokRyadomApp>()
    private val db get() = app.container.database
    private var first = 0L
    private var second = 0L
    private var shop = 0L
    private var purchase = 0L

    private fun waitFor(condition: () -> Boolean) {
        ui.waitUntil(20_000) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(20))
            ui.waitForIdle()
            condition()
        }
    }
    private fun waitText(text: String) = waitFor { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun tab(text: String) {
        ui.onNode(hasText(text) and SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.Role,
            androidx.compose.ui.semantics.Role.Tab
        )).performClick()
    }
    private fun shot(name: String) {
        ui.waitForIdle()
        ui.runOnIdle {
            val view = org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }
                ?.window?.decorView ?: ui.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("../.local/audit-0.12.1/$name-api${android.os.Build.VERSION.SDK_INT}.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Before fun seed() {
        runBlocking {
            listOf("drafts", "product_card_drafts", "shopping_list_prefs", "product_db_prefs")
                .forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
            db.shoppingListEntryDao().deleteAll()
            db.productDao().deleteAll();db.productGroupDao().deleteAll()
            db.shopDao().deleteAll()
            db.shopDepartmentDao().clearTypes()
            db.retailStructureDao().clearVenues()
            app.container.locations.select(1)
            shop = db.shopDao().insert(ShopEntity(name = "Батон"))
            first = app.container.productRepository.saveWithLinks(
                ProductEntity(name = "Банан", defaultUnit = "шт"),
                listOf(ProductShopLinkEntity(productId = 0, shopId = shop))
            )
            second = app.container.productRepository.saveWithLinks(
                ProductEntity(name = "Газировка", defaultUnit = "бутылка"),
                listOf(ProductShopLinkEntity(productId = 0, shopId = shop))
            )
            purchase = app.container.shoppingListRepository.addProduct(first, choice = ShopChoice.EXPLICIT, shopId = shop)
            app.container.shoppingListRepository.addProduct(second, choice = ShopChoice.EXPLICIT, shopId = shop)
        }
        waitFor { ui.onAllNodesWithTag("purchase-row-$purchase").fetchSemanticsNodes().isNotEmpty() }
    }

    @After fun close() {
        ui.activityRule.scenario.close()
        app.container.locations.closeForTest()
        db.close()
        org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java, "INSTANCE", null)
    }

    @Test fun catalogueSelectionSurvivesActivityRecreationAndDoesNotChangeDatabase() {
        tab("Товары")
        waitText("База товаров")
        waitFor { runCatching { ui.onNodeWithContentDescription("Выбрать товары").assertIsEnabled() }.isSuccess }
        ui.onNodeWithContentDescription("Выбрать товары").performClick()
        waitFor { ui.onAllNodesWithTag("catalogue-all-$first").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("catalogue-all-$first").performClick()
        waitText("Выбрано: 1")
        ui.activityRule.scenario.recreate()
        waitText("Выбрано: 1")
        waitFor { ui.onAllNodesWithTag("catalogue-all-$first").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("catalogue-all-$first").assertIsOn()
        ui.onNodeWithTag("catalogue-all-$second").assertIsOff()
        shot("catalogue-restored-selection")
        ui.runOnUiThread { ui.activity.onBackPressedDispatcher.onBackPressed() }
        waitText("База товаров")
        runBlocking {
            assertEquals(2, db.productDao().getAllSync().size)
            assertEquals(2, db.shoppingListEntryDao().getAllSync().size)
            assertTrue(db.shoppingListEntryDao().getAllSync().none { it.isBought || it.isUrgent })
        }
    }

    @Test fun nonDefaultCatalogueSelectionRestoresBeforeLoadedModeAndRealModeChangeClearsIt() {
        tab("Товары")
        waitText("База товаров")
        ui.onNodeWithText("Получатели").performScrollTo().performClick()
        waitText("Без получателя")
        waitFor { runCatching { ui.onNodeWithContentDescription("Выбрать товары").assertIsEnabled() }.isSuccess }
        ui.onNodeWithContentDescription("Выбрать товары").performClick()
        waitFor { ui.onAllNodesWithContentDescription("Выбрать раздел: Без получателя").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithContentDescription("Выбрать раздел: Без получателя").performClick()
        waitText("Выбрано: 2")
        ui.activityRule.scenario.recreate()
        waitText("Выбрано: 2")
        waitFor { ui.onAllNodesWithTag("catalogue-no_recipient-$first").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("catalogue-no_recipient-$first").assertIsOn()
        ui.onNodeWithTag("catalogue-no_recipient-$second").assertIsOn()
        ui.onNodeWithText("Группы").performScrollTo().performClick()
        waitText("База товаров")
        ui.onAllNodesWithContentDescription("Отменить выбор").assertCountEquals(0)
        runBlocking {
            assertEquals(2, db.productDao().getAllSync().size)
            assertTrue(db.productDao().getAllSync().all { it.recipientId == null })
        }
    }

    @Test fun shopListAndShopCatalogueKeepTheirLogicalSelectionAfterRecreation() {
        tab("Магазины")
        waitText("Батон")
        ui.onNodeWithContentDescription("Выбрать магазины").performClick()
        waitFor { ui.onAllNodesWithContentDescription("Выбрать все").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithContentDescription("Выбрать все").performClick()
        waitText("Выбрано: 1")
        ui.activityRule.scenario.recreate()
        waitText("Выбрано: 1")
        waitFor { ui.onAllNodesWithTag("batch-row-$shop").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("batch-row-$shop").assertIsOn()
        ui.onNodeWithContentDescription("Отменить выбор").performClick()
        waitFor { ui.onAllNodesWithContentDescription("Выбрать магазины").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Батон").performClick()
        waitFor { runCatching { ui.onNodeWithContentDescription("Выбрать товары").assertIsEnabled() }.isSuccess }
        ui.onNodeWithContentDescription("Выбрать товары").performClick()
        ui.onNodeWithContentDescription("Выбрать все").performClick()
        waitText("Выбрано: 2")
        ui.activityRule.scenario.recreate()
        waitText("Выбрано: 2")
        waitFor { ui.onAllNodesWithTag("shop-product-0-$first").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("shop-product-0-$first").assertIsOn()
        ui.onNodeWithTag("shop-product-0-$second").assertIsOn()
        runBlocking {
            assertEquals(2, db.productDao().getAllSync().size)
            assertEquals(2, db.shoppingListEntryDao().getAllSync().size)
            assertTrue(db.shoppingListEntryDao().getAllSync().none { it.isBought || it.isUrgent })
        }
    }

    @Test fun purchaseSelectionSurvivesActivityRecreationAndBackOnlyCancelsSelection() {
        ui.onNodeWithContentDescription("Выбрать покупки").performClick()
        ui.onNodeWithContentDescription("Выбрать: Банан").performClick()
        waitText("Выбрано: 1")
        ui.activityRule.scenario.recreate()
        waitText("Выбрано: 1")
        ui.onNodeWithContentDescription("Выбрать: Банан").assertIsOn()
        ui.onNodeWithContentDescription("Выбрать: Газировка").assertIsOff()
        ui.runOnUiThread { ui.activity.onBackPressedDispatcher.onBackPressed() }
        waitText("Список покупок")
        runBlocking {
            assertEquals(2, db.shoppingListEntryDao().getAllSync().size)
            assertTrue(db.shoppingListEntryDao().getAllSync().none { it.isBought || it.isUrgent })
        }
    }

    @Test fun boughtPurchaseMenuDoesNotOfferPendingOnlyUrgencyOrMove() {
        runBlocking { app.container.shoppingListRepository.markBought(purchase) }
        waitFor { ui.onAllNodesWithContentDescription("Вернуть в покупки: Банан").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("purchase-row-$purchase").performClick()
        waitText("Карточка товара")
        ui.onAllNodesWithText("Сделать срочным").assertCountEquals(0)
        ui.onAllNodesWithText("Снять срочность").assertCountEquals(0)
        ui.onAllNodesWithText("Сменить место покупки").assertCountEquals(0)
        runBlocking {
            val actual = db.shoppingListEntryDao().getById(purchase)!!
            assertTrue(actual.isBought)
            assertFalse(actual.isUrgent)
        }
    }

    @Test fun collapsedDepartmentAndVenueSelectionOverlapWithoutDuplicateOrOutsideRows() {
        val marketPurchases = mutableSetOf<Long>()
        runBlocking {
            val venue = db.retailStructureDao().insertVenue(VenueEntity(cityId = 1, name = "Енисейский привоз"))
            val cheese = db.shopDao().insert(ShopEntity(name = "29 павильон", displayOrder = -2))
            val meat = db.shopDao().insert(ShopEntity(name = "68 павильон", displayOrder = -1))
            val cheesePoint = db.shopBranchDao().insert(ShopBranchEntity(shopId = cheese, address = "29 павильон", venueId = venue))
            val meatPoint = db.shopBranchDao().insert(ShopBranchEntity(shopId = meat, address = "68 павильон", venueId = venue))
            val dairy = db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId = cheese, name = "Молочка"))
            val bakery = db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId = cheese, name = "Выпечка", displayOrder = 1))
            repeat(10) { index ->
                val product = app.container.productRepository.saveWithLinks(
                    ProductEntity(name = "Молоко $index", defaultUnit = "л"),
                    listOf(ProductShopLinkEntity(productId = 0, shopId = cheese, departmentId = dairy))
                )
                marketPurchases += app.container.shoppingListRepository.addProduct(product, choice = ShopChoice.EXPLICIT, shopId = cheese, branchId = cheesePoint)
            }
            val bread = app.container.productRepository.saveWithLinks(
                ProductEntity(name = "Хлеб", defaultUnit = "шт"),
                listOf(ProductShopLinkEntity(productId = 0, shopId = cheese, departmentId = bakery))
            )
            marketPurchases += app.container.shoppingListRepository.addProduct(bread, choice = ShopChoice.EXPLICIT, shopId = cheese, branchId = cheesePoint)
            val steak = app.container.productRepository.saveWithLinks(
                ProductEntity(name = "Мясо", defaultUnit = "кг"),
                listOf(ProductShopLinkEntity(productId = 0, shopId = meat))
            )
            marketPurchases += app.container.shoppingListRepository.addProduct(steak, choice = ShopChoice.EXPLICIT, shopId = meat, branchId = meatPoint)
        }
        waitText("В списке: 14")
        ui.onNodeWithContentDescription("Выбрать покупки").performClick()
        waitFor { ui.onAllNodesWithContentDescription("Свернуть отдел: Молочка").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithContentDescription("Свернуть отдел: Молочка").performClick()
        ui.onNodeWithContentDescription("Выбрать раздел: Молочка").performClick()
        waitText("Выбрано: 10")
        ui.onNodeWithContentDescription("Рынок или ТЦ: Енисейский привоз").performClick()
        ui.onNodeWithContentDescription("Выбрать раздел: Енисейский привоз").performClick()
        waitText("Выбрано: 12")
        ui.onNodeWithContentDescription("Выбрать раздел: Енисейский привоз").assertIsOn()
        ui.onNodeWithContentDescription("Выбрать раздел: Енисейский привоз").performClick()
        waitText("Выбрано: 0")
        ui.onNodeWithContentDescription("Выбрать раздел: Енисейский привоз").performClick()
        waitText("Выбрано: 12")
        shot("shopping-venue-department-selection")
        ui.onNodeWithContentDescription("Действия с выбранными").performClick()
        waitText("Сделать срочными")
        ui.onNodeWithText("Сделать срочными").performClick()
        waitText("Список покупок")
        runBlocking {
            val actual = db.shoppingListEntryDao().getAllSync()
            assertEquals(14, actual.size)
            assertEquals(marketPurchases, actual.filter { it.isUrgent }.map { it.id }.toSet())
            assertEquals(2, actual.count { !it.isUrgent })
            assertTrue(actual.none { it.isBought })
        }
    }

    @Test fun shopBatchAddRequiresAndUsesExplicitPointInsteadOfDifferentPreferredPoint() {
        var pointA = 0L
        var pointB = 0L
        runBlocking {
            db.shoppingListEntryDao().deleteAll()
            pointA = db.shopBranchDao().insert(ShopBranchEntity(shopId = shop, address = "Адрес А", displayOrder = 0))
            pointB = db.shopBranchDao().insert(ShopBranchEntity(shopId = shop, address = "Адрес Б", displayOrder = 1))
            val dairy = db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId = shop, name = "Молочка"))
            val type = db.shopDepartmentDao().getAllSync().single { it.id == dairy }.typeId
            // A point-specific route makes the real shop catalogue display both physical sections.
            db.retailStructureDao().setProductDepartment(ProductBranchDepartmentEntity(first, pointB, shop, type))
            db.shopBranchDao().setPriority(ProductBranchPriorityEntity(first, pointA, shop, 0))
        }
        tab("Магазины")
        waitText("Батон")
        ui.onNodeWithText("Батон").performClick()
        waitFor { runCatching { ui.onNodeWithContentDescription("Выбрать товары").assertIsEnabled() }.isSuccess }
        ui.onNodeWithContentDescription("Выбрать товары").performClick()
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("shop-product-$pointB-$first"))
        waitFor { ui.onAllNodesWithTag("shop-product-$pointB-$first").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("shop-product-$pointB-$first").performClick()
        waitText("Выбрано: 1")
        ui.onNodeWithContentDescription("Действия с выбранными").performClick()
        waitText("Добавить в список покупок")
        waitFor { runCatching { ui.onNodeWithText("Добавить в список покупок").assertIsEnabled() }.isSuccess }
        ui.onNodeWithText("Добавить в список покупок").performClick()
        waitText("Место покупки")
        ui.onNodeWithText("Применить к 1").assertIsNotEnabled()
        ui.onNode(hasText("Адрес Б") and SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.Role,
            androidx.compose.ui.semantics.Role.RadioButton
        )).performClick()
        ui.onNodeWithText("Применить к 1").assertIsEnabled()
        shot("bulk-explicit-point")
        ui.onNodeWithText("Применить к 1").performClick()
        waitFor { runBlocking { db.shoppingListEntryDao().getAllSync().singleOrNull()?.assignedBranchId == pointB } }
        runBlocking {
            val actual = db.shoppingListEntryDao().getAllSync().single()
            assertEquals(first, actual.productId)
            assertEquals(shop, actual.assignedShopId)
            assertEquals(pointB, actual.assignedBranchId)
            assertEquals(pointA, db.shopBranchDao().priorities().single { it.productId == first }.branchId)
            assertEquals(2, db.productDao().getAllSync().size)
        }
    }

    @Test fun fullRestoreInvalidatesProductAndStoreSelectionsSavedInOtherTabsWithReusedIds() {
        val originalProduct = runBlocking { db.productDao().getById(first)!! }
        val originalShop = runBlocking { db.shopDao().getById(shop)!! }
        val backup = app.container.backupManager
        val archive = runBlocking { backup.exportFile() }
        val invalid = File(app.cacheDir, "invalid-restore-selection.json").also { it.writeText("{}") }
        try {
            val manifest = ZipFile(archive).use { zip ->
                JSONObject(zip.getInputStream(zip.getEntry("manifest.json")).bufferedReader(Charsets.UTF_8).use { it.readText() })
            }
            assertFalse(manifest.getJSONObject("preferences").has("local_database_revision"))
            for (products in listOf(true, false)) {
                val differentUuid = UUID.randomUUID().toString()
                runBlocking {
                    db.productDao().update(originalProduct.copy(stableId = differentUuid, name = "Подмена Б"))
                    db.shopDao().update(originalShop.copy(name = "Магазин Б"))
                }
                tab(if (products) "Товары" else "Магазины")
                waitText(if (products) "Подмена Б" else "Магазин Б")
                val choose = if (products) "Выбрать товары" else "Выбрать магазины"
                waitFor { runCatching { ui.onNodeWithContentDescription(choose).assertIsEnabled() }.isSuccess }
                ui.onNodeWithContentDescription(choose).performClick()
                val row = if (products) "catalogue-all-$first" else "batch-row-$shop"
                waitFor { ui.onAllNodesWithTag(row).fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithTag(row).performClick()
                waitText("Выбрано: 1")
                tab("Покупки")
                waitFor { ui.onAllNodesWithContentDescription("Настройки").fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithContentDescription("Настройки").performClick()
                waitText("Импорт / Восстановление")
                val before = DatabaseRevision.current(app)
                val failed = runCatching { runBlocking { backup.importFile(invalid) } }
                assertTrue(failed.isFailure)
                assertEquals(before, DatabaseRevision.current(app))
                assertEquals(differentUuid, runBlocking { db.productDao().getById(first)!!.stableId })
                val result = runBlocking { backup.importFile(archive) }
                assertTrue(result.full)
                val after = DatabaseRevision.current(app)
                assertNotEquals(before, after)
                assertEquals(after, app.getSharedPreferences("local_database_revision", 0).getString("revision", null))
                UUID.fromString(after)
                ui.onNodeWithContentDescription("Назад").performClick()
                waitText("Список покупок")
                tab(if (products) "Товары" else "Магазины")
                waitFor { runCatching { ui.onNodeWithContentDescription(choose).assertIsEnabled() }.isSuccess }
                ui.onAllNodesWithContentDescription("Отменить выбор").assertCountEquals(0)
                ui.onAllNodesWithContentDescription("Действия с выбранными").assertCountEquals(0)
                runBlocking {
                    val actual = db.productDao().getById(first)!!
                    assertEquals(originalProduct.stableId, actual.stableId)
                    assertEquals(originalProduct.name, actual.name)
                    assertEquals(originalProduct.isFavorite, actual.isFavorite)
                    assertEquals(originalShop.name, db.shopDao().getById(shop)!!.name)
                    assertEquals(2, db.shoppingListEntryDao().getAllSync().size)
                    assertTrue(db.shoppingListEntryDao().getAllSync().none { it.isBought || it.isUrgent })
                }
            }
        } finally {
            backup.discard(archive)
            invalid.delete()
        }
    }
}
