package com.spisokryadom.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.backup.BackupManager
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.CityEntity
import com.spisokryadom.app.data.repository.*
import kotlinx.coroutines.runBlocking
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
class Audit151BackupTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: AppDatabase
    private lateinit var location: LocationRepository
    private lateinit var goals: GoalListRepository
    private lateinit var backup: BackupManager
    private lateinit var original: File
    private val files = mutableListOf<File>()

    @Before fun setup(): Unit = runBlocking {
        listOf("location_prefs", "shopping_list_prefs", "product_db_prefs").forEach {
            app.getSharedPreferences(it, 0).edit().clear().commit()
        }
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        db.cityDao().insert(CityEntity(1, "Город"))
        location = LocationRepository(db.cityDao(), db.shopDao(), app.getSharedPreferences("location_prefs", 0))
        goals = GoalListRepository(db, ShoppingListRepository(db.shoppingListEntryDao(), db, location))
        backup = BackupManager(app, db)
        val list = goals.create("Ужин")
        goals.addQuick(list, "BAD_UTF8_MARKER", plannedUnitPrice = 12.5, shopName = "Батон", shopAddress = "Весны, 16а")
        original = backup.exportFile().also { files += it }
        // A malformed restore must leave subsequent local work and preferences intact.
        goals.addQuick(goals.create("После копии"), "Не терять")
        app.getSharedPreferences("shopping_list_prefs", 0).edit().putBoolean("browse_products_on_add", false).commit()
    }
    @After fun cleanup() {
        files.forEach { it.delete() }
        location.closeForTest()
        db.close()
    }
    private fun root() = ZipFile(original).use { archive ->
        JSONObject(archive.getInputStream(archive.getEntry("manifest.json")).reader(Charsets.UTF_8).readText())
    }
    private fun archive(bytes: ByteArray): File = File(app.cacheDir, "audit151-${UUID.randomUUID()}.zip").also { file ->
        files += file
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(bytes); zip.closeEntry()
        }
    }
    private suspend fun reject(root: JSONObject) = rejectBytes(root.toString().toByteArray(Charsets.UTF_8))
    private suspend fun rejectBytes(bytes: ByteArray) {
        val lists = db.goalListDao().getAllSync()
        val rows = db.shoppingListEntryDao().getAllSync()
        val products = db.productDao().getAllSync()
        assertTrue("Malformed full backup was accepted", runCatching { backup.importFile(archive(bytes)) }.isFailure)
        assertEquals(lists, db.goalListDao().getAllSync())
        assertEquals(rows, db.shoppingListEntryDao().getAllSync())
        assertEquals(products, db.productDao().getAllSync())
        assertFalse(app.getSharedPreferences("shopping_list_prefs", 0).getBoolean("browse_products_on_add", true))
    }
    private fun JSONObject.goal() = getJSONObject("data").getJSONArray("goalLists").getJSONObject(0)
    private fun JSONObject.entry() = getJSONObject("data").getJSONArray("shoppingListEntries").getJSONObject(0)

    @Test fun fractionalListIdsCannotBeTruncatedIntoAnotherOwner() = runBlocking {
        val value = root(); val id = value.goal().getLong("id")
        value.goal().put("id", id + .9); value.entry().put("listId", id + .9)
        reject(value)
    }
    @Test fun stringListIdCannotBeCoercedIntoAnOwner() = runBlocking {
        val value = root(); value.entry().put("listId", value.goal().getLong("id").toString())
        reject(value)
    }
    @Test fun missingOwnerAndPlanningFieldsCannotSilentlyEraseMetadata() = runBlocking {
        for (key in listOf("listId", "plannedUnitPrice", "customShopName", "customShopAddress")) {
            val value = root(); value.entry().remove(key); reject(value)
        }
    }
    @Test fun goalVisibilityCannotBeCoercedFromString() = runBlocking {
        val value = root(); value.goal().put("showInPurchases", "true"); reject(value)
    }
    @Test fun goalStringsCannotBeCoercedFromNonStrings() = runBlocking {
        for (key in listOf("name", "stableId")) {
            val value = root(); value.goal().put(key, 123); reject(value)
        }
        for (key in listOf("customShopName", "customShopAddress")) {
            val value = root(); value.entry().put(key, 123); reject(value)
        }
    }
    @Test fun plannedPriceCannotBeCoercedFromNumericString() = runBlocking {
        val value = root(); value.entry().put("plannedUnitPrice", "12.5"); reject(value)
    }
    @Test fun malformedUtf8InsideManifestCannotSilentlyReplaceSavedName() = runBlocking {
        val bytes = root().toString().toByteArray(Charsets.UTF_8)
        val marker = "BAD_UTF8_MARKER".toByteArray(Charsets.UTF_8)
        val at = (0..bytes.size - marker.size).first { offset -> marker.indices.all { bytes[offset + it] == marker[it] } }
        bytes[at + 1] = 0x80.toByte()
        rejectBytes(bytes)
    }
    @Test fun numericVersionMustBeAnIntegerRatherThanRounded() = runBlocking {
        reject(root().put("version", BackupManager.BACKUP_VERSION + .9))
    }
}
