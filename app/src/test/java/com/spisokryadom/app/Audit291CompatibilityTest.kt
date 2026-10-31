package com.spisokryadom.app

import android.app.Application
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.backup.BackupManager
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.db.RetailConstraints
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.ProductVariantsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.File
import java.util.UUID

/** Separate native SQLite files only; never opens the application's production database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class Audit291CompatibilityTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private fun open(name: String) = Room.databaseBuilder(app, AppDatabase::class.java, name)
        .allowMainThreadQueries().addCallback(object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) { RetailConstraints.install(db) }
        }).build()

    @Test fun currentDatabaseAndRestoredCopyRetainGraphAfterCloseAndReopen(): Unit = runBlocking {
        val token = UUID.randomUUID().toString()
        val sourceName = "audit291-source-$token.db"
        val targetName = "audit291-target-$token.db"
        val imageA = File(app.filesDir, "product_photos/audit291-$token-a.jpg")
        val imageB = File(app.filesDir, "product_photos/audit291-$token-b.jpg")
        imageA.parentFile!!.mkdirs(); imageA.writeBytes(byteArrayOf(1, 2, 3)); imageB.writeBytes(byteArrayOf(4, 5, 6))
        listOf("location_prefs", "shopping_list_prefs", "product_db_prefs", "product_card_drafts").forEach {
            app.getSharedPreferences(it, 0).edit().clear().commit()
        }
        var db: AppDatabase? = null
        var archive: File? = null
        try {
            val source = open(sourceName).also { db = it }
            source.cityDao().insert(CityEntity(1, "Город А"))
            source.cityDao().insert(CityEntity(2, "Город Б"))
            source.shopDao().insert(ShopEntity(id = 10, name = "Магазин", cityId = 1, cityGroupKey = "audit291-shop", isFavorite = true, favoriteOrder = 3, displayOrder = 7))
            source.shopDao().insert(ShopEntity(id = 11, name = "Магазин", cityId = 2, cityGroupKey = "audit291-shop", displayOrder = 2))
            source.retailStructureDao().insertVenue(VenueEntity(20, 1, "Рынок", "Общий адрес", kind = VenueKinds.MARKET))
            source.shopBranchDao().insert(ShopBranchEntity(id = 30, shopId = 10, address = "", venueId = 20, label = "Павильон 5"))
            source.shopBranchDao().insert(ShopBranchEntity(id = 31, shopId = 11, address = "Другой город, 2"))
            source.productDao().insert(ProductEntity(id = 40, name = "Товар", stableId = "audit291-root", defaultQuantity = 0.5, defaultUnit = "кг", isFavorite = true))
            val variants = ProductVariantsRepository(source)
            val child = variants.create(40, "Марка Б")
            variants.savePhotos(child, listOf(ProductPhotoEntity(child, 0, imageB.path, "Основное"), ProductPhotoEntity(child, 1, imageA.path, "Состав")))
            variants.configureCity(40, 1, listOf(child to true, 40L to false))
            variants.configureCity(40, 2, listOf(40L to true, child to false))
            source.productShopLinkDao().insert(ProductShopLinkEntity(id = 50, productId = child, shopId = 10, priority = 4, restrictBranches = true))
            source.shopBranchDao().insertAvailability(ProductBranchAvailabilityEntity(child, 30, 10))
            source.shopBranchDao().setPriority(ProductBranchPriorityEntity(child, 30, 10, 0))
            source.goalListDao().insert(GoalListEntity(id = 60, stableId = "audit291-list", name = "Поездка", showInPurchases = true, createdAt = 100, updatedAt = 200))
            source.shoppingListEntryDao().insert(ShoppingListEntryEntity(id = 70, productId = child, listId = 60, quantity = 0.75, unit = "кг", plannedUnitPrice = 120.5, assignedShopId = 10, assignedBranchId = 30, isBought = true, isUrgent = true, note = "Заметка", comment = "Комментарий", createdAt = 100, updatedAt = 200))
            source.shoppingListEntryDao().insert(ShoppingListEntryEntity(id = 71, productId = 40, quantity = 2.0, unit = "уп", explicitNoShop = true, createdAt = 101, updatedAt = 201))
            app.getSharedPreferences("location_prefs", 0).edit().putLong("active_city", 2).commit()
            app.getSharedPreferences("shopping_list_prefs", 0).edit().putBoolean("remember_variant_choice", true).commit()
            source.close(); db = null

            suspend fun verify(opened: AppDatabase) {
                assertEquals(26, opened.openHelper.writableDatabase.version)
                assertEquals(2, opened.cityDao().getAllSync().size)
                assertEquals(listOf(10L, 11L), opened.shopDao().getAllSync().map { it.id }.sorted())
                assertEquals(7, opened.shopDao().getById(10)!!.displayOrder)
                assertEquals(3, opened.shopDao().getById(10)!!.favoriteOrder)
                assertEquals("audit291-shop", opened.shopDao().getById(11)!!.cityGroupKey)
                assertEquals(20L, opened.shopBranchDao().get(30)!!.venueId)
                assertEquals("Павильон 5", opened.shopBranchDao().get(30)!!.label)
                assertEquals("Другой город, 2", opened.shopBranchDao().get(31)!!.address)
                val repo = ProductVariantsRepository(opened)
                assertEquals(child, repo.resolve(40, 1).id)
                assertEquals(40L, repo.resolve(40, 2).id)
                val photos = repo.photos(child)
                assertEquals(listOf("Основное", "Состав"), photos.map { it.caption })
                assertEquals(listOf(0, 1), photos.map { it.position })
                assertArrayEquals(imageB.readBytes(), File(photos[0].uri).readBytes())
                assertArrayEquals(imageA.readBytes(), File(photos[1].uri).readBytes())
                assertEquals(photos[0].uri, opened.productDao().getById(child)!!.photoUri)
                assertEquals(ProductBranchAvailabilityEntity(child, 30, 10), opened.shopBranchDao().allAvailability().single())
                assertEquals(ProductBranchPriorityEntity(child, 30, 10, 0), opened.shopBranchDao().priorities().single())
                assertEquals("audit291-list", opened.goalListDao().getById(60)!!.stableId)
                val bought = opened.shoppingListEntryDao().getById(70)!!
                assertEquals(child, bought.productId); assertEquals(60L, bought.listId)
                assertEquals(0.75, bought.quantity, 0.0); assertEquals("кг", bought.unit)
                assertEquals(120.5, bought.plannedUnitPrice!!, 0.0)
                assertTrue(bought.isBought && bought.isUrgent)
                assertEquals(30L, bought.assignedBranchId); assertEquals("Комментарий", bought.comment)
                assertEquals(200L, bought.updatedAt)
                assertTrue(opened.shoppingListEntryDao().getById(71)!!.explicitNoShop)
                assertEquals(2, opened.shoppingListEntryDao().getAllSync().size)
                opened.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
                opened.openHelper.writableDatabase.query("PRAGMA integrity_check").use { assertTrue(it.moveToFirst()); assertEquals("ok", it.getString(0)) }
            }

            val reopenedSource = open(sourceName).also { db = it }
            verify(reopenedSource)
            archive = BackupManager(app, reopenedSource).exportFile()
            reopenedSource.close(); db = null
            val destination = open(targetName).also { db = it }
            destination.cityDao().insert(CityEntity(99, "Тестовое назначение"))
            app.getSharedPreferences("shopping_list_prefs", 0).edit().putBoolean("remember_variant_choice", false).commit()
            BackupManager(app, destination).importFile(archive!!)
            destination.close(); db = null
            val reopenedDestination = open(targetName).also { db = it }
            verify(reopenedDestination)
            assertEquals(2L, app.getSharedPreferences("location_prefs", 0).getLong("active_city", 0))
            assertTrue(app.getSharedPreferences("shopping_list_prefs", 0).getBoolean("remember_variant_choice", false))
        } finally {
            db?.close(); archive?.delete()
            app.deleteDatabase(sourceName); app.deleteDatabase(targetName)
            imageA.delete(); imageB.delete()
        }
    }
}
