package com.spisokryadom.app

import android.app.Application
import android.util.Base64
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.backup.BackupManager
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.ProductEditorTransactions
import com.spisokryadom.app.data.repository.ProductVariantsRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class Audit291BackupTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: AppDatabase
    private lateinit var backup: BackupManager
    private val files = mutableListOf<File>()

    @Before fun setup(): Unit = runBlocking {
        listOf("location_prefs", "shopping_list_prefs", "product_db_prefs", "product_card_drafts").forEach {
            app.getSharedPreferences(it, 0).edit().clear().commit()
        }
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        db.cityDao().insert(CityEntity(1, "Город"))
        backup = BackupManager(app, db)
    }

    @After fun cleanup() {
        db.close()
        files.forEach { it.delete() }
    }

    private fun image(bytes: ByteArray) = File(app.filesDir, "product_photos/audit291-${UUID.randomUUID()}.jpg").also {
        it.parentFile!!.mkdirs(); it.writeBytes(bytes); files += it
    }

    private fun legacy(photo: ByteArray?) = File(app.cacheDir, "audit291-${UUID.randomUUID()}.json").also {
        val product = JSONObject().put("id", 42).put("stableId", "audit291-photo").put("name", "Товар")
        if (photo != null) product.put("photoBase64", Base64.encodeToString(photo, Base64.NO_WRAP))
        it.writeText(JSONObject().put("version", 3).put("data", JSONObject().put("products", JSONArray().put(product))).toString())
        files += it
    }

    @Test fun legacyPhotoReplacementUpdatesTheVisiblePrimaryAndKeepsOtherPhotos(): Unit = runBlocking {
        val first = image(byteArrayOf(1, 2, 3))
        val second = image(byteArrayOf(4, 5, 6))
        val id = db.productDao().insert(ProductEntity(name = "Товар", stableId = "audit291-photo", photoUri = first.path))
        val variants = ProductVariantsRepository(db)
        variants.savePhotos(id, listOf(ProductPhotoEntity(id, 0, first.path, "Первое"), ProductPhotoEntity(id, 1, second.path, "Второе")))
        val entry = db.shoppingListEntryDao().insert(ShoppingListEntryEntity(productId = id, quantity = 2.0, unit = "шт"))
        val replacement = byteArrayOf(9, 8, 7)

        val source = legacy(replacement)
        backup.importFile(source)
        val photos = variants.photos(id)
        assertEquals(2, photos.size)
        assertEquals(listOf("Первое", "Второе"), photos.map { it.caption })
        assertArrayEquals(replacement, File(photos.first().uri).readBytes())
        assertEquals(db.productDao().getById(id)!!.photoUri, photos.first().uri)
        assertEquals(second.path, photos[1].uri)
        assertEquals(id, db.shoppingListEntryDao().getById(entry)!!.productId)

        backup.importFile(source)
        assertEquals(2, variants.photos(id).size)
        val full = backup.exportFile().also { files += it }
        backup.importFile(full)
        assertArrayEquals(replacement, File(variants.photos(id).first().uri).readBytes())
        assertArrayEquals(second.readBytes(), File(variants.photos(id)[1].uri).readBytes())
    }

    @Test fun legacyMergeWithoutPhotoRetainsAllCurrentPhotos(): Unit = runBlocking {
        val first = image(byteArrayOf(1, 2, 3))
        val second = image(byteArrayOf(4, 5, 6))
        val id = db.productDao().insert(ProductEntity(name = "Товар", stableId = "audit291-photo", photoUri = first.path))
        val variants = ProductVariantsRepository(db)
        variants.savePhotos(id, listOf(ProductPhotoEntity(id, 0, first.path, "Первое"), ProductPhotoEntity(id, 1, second.path, "Второе")))
        val before = variants.photos(id)
        backup.importFile(legacy(null))
        assertEquals(before, variants.photos(id))
        assertEquals(first.path, db.productDao().getById(id)!!.photoUri)
    }

    @Test fun fullBackupRejectsCoercedIdentifiersBeforeReplacingAnyData(): Unit = runBlocking {
        val product = db.productDao().insert(ProductEntity(name = "Товар"))
        val shop = db.shopDao().insert(ShopEntity(name = "Магазин", cityId = 1))
        val branch = db.shopBranchDao().insert(ShopBranchEntity(shopId = shop, address = "Адрес"))
        db.productShopLinkDao().insert(ProductShopLinkEntity(productId = product, shopId = shop))
        db.shoppingListEntryDao().insert(ShoppingListEntryEntity(productId = product, quantity = 2.0, unit = "шт", assignedShopId = shop, assignedBranchId = branch))
        val full = backup.exportFile().also { files += it }
        val source = ZipFile(full).use { zip -> zip.getInputStream(zip.getEntry("manifest.json")).reader().readText() }
        db.productDao().insert(ProductEntity(name = "После копии — сохранить"))
        val products = db.productDao().getAllSync()
        val entries = db.shoppingListEntryDao().getAllSync()
        val generation = ProductEditorTransactions.generation.get()
        app.getSharedPreferences("shopping_list_prefs", 0).edit().putBoolean("browse_products_on_add", false).commit()

        for ((table, key) in listOf("products" to "id", "shoppingListEntries" to "productId", "shoppingListEntries" to "assignedShopId", "shoppingListEntries" to "assignedBranchId", "productShopLinks" to "shopId", "shops" to "cityId")) {
            for (asString in listOf(false, true)) {
                val root = JSONObject(source)
                val row = root.getJSONObject("data").getJSONArray(table).getJSONObject(0)
                val id = row.getLong(key)
                row.put(key, if (asString) id.toString() else id + 0.9)
                val malformed = File(app.cacheDir, "audit291-${UUID.randomUUID()}.zip").also { files += it }
                ZipOutputStream(malformed.outputStream()).use { zip ->
                    zip.putNextEntry(ZipEntry("manifest.json")); zip.write(root.toString().toByteArray()); zip.closeEntry()
                }
                assertTrue("Accepted $table.$key = ${row.get(key)}", runCatching { backup.importFile(malformed) }.isFailure)
                assertEquals(products, db.productDao().getAllSync())
                assertEquals(entries, db.shoppingListEntryDao().getAllSync())
                assertEquals(generation, ProductEditorTransactions.generation.get())
                assertFalse(app.getSharedPreferences("shopping_list_prefs", 0).getBoolean("browse_products_on_add", true))
            }
        }
    }

    @Test fun missingPointPrioritiesRejectsModernBackupButRemainsReadableBeforeVersionNine(): Unit = runBlocking {
        val product = db.productDao().insert(ProductEntity(name = "Товар"))
        val shop = db.shopDao().insert(ShopEntity(name = "Магазин", cityId = 1))
        val branch = db.shopBranchDao().insert(ShopBranchEntity(shopId = shop, address = "Адрес"))
        db.productShopLinkDao().insert(ProductShopLinkEntity(productId = product, shopId = shop))
        db.shopBranchDao().setPriority(ProductBranchPriorityEntity(product, branch, shop, 3))
        val full = backup.exportFile().also { files += it }
        val root = ZipFile(full).use { zip -> JSONObject(zip.getInputStream(zip.getEntry("manifest.json")).reader().readText()) }
        root.getJSONObject("data").remove("productBranchPriorities")
        db.productDao().insert(ProductEntity(name = "После копии — сохранить"))
        val products = db.productDao().getAllSync()
        val priorities = db.shopBranchDao().priorities()
        fun archive() = File(app.cacheDir, "audit291-${UUID.randomUUID()}.zip").also { file ->
            files += file
            ZipOutputStream(file.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json")); zip.write(root.toString().toByteArray()); zip.closeEntry()
            }
        }
        assertTrue("Modern full backup lost its required point-priority table", runCatching { backup.importFile(archive()) }.isFailure)
        assertEquals(products, db.productDao().getAllSync())
        assertEquals(priorities, db.shopBranchDao().priorities())

        root.put("version", 8)
        backup.importFile(archive())
        assertEquals(product, db.productDao().getAllSync().single().id)
        assertTrue(db.shopBranchDao().priorities().isEmpty())
    }
}
