package com.spisokryadom.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.util.WebLinks
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class Audit311InputDataTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: AppDatabase
    private lateinit var places: LocationRepository
    private lateinit var bulk: BulkPurchaseRepository

    @Before fun setup(): Unit = runBlocking {
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        db.cityDao().insert(CityEntity(1, "Город"))
        val prefs = app.getSharedPreferences("audit311-input-location", 0)
        prefs.edit().clear().commit()
        places = LocationRepository(db.cityDao(), db.shopDao(), prefs)
        bulk = BulkPurchaseRepository(db, ShoppingListRepository(db.shoppingListEntryDao(), db, places), places)
    }

    @After fun close() { places.closeForTest(); db.close() }

    @Test fun pastedLinksEndAtUnicodeSpacesWithoutLosingQueryParameters() {
        val url = "https://example.org/item?sku=42&from=share"
        listOf(" ", "\u00a0", "\u202f", "\u2028").forEach { separator ->
            assertEquals("separator U+${separator.first().code.toString(16)}", listOf(url),
                WebLinks.extract("Название товара\n$url${separator}Описание"))
        }
        assertEquals(listOf(url, "https://example.net/other"), WebLinks.extract("$url\u00a0https://example.net/other"))
    }

    @Test fun everySameNameProductRemainsSelectableAndTheChosenIdentityIsAdded(): Unit = runBlocking {
        // Duplicate names are valid catalogue data. Refining the query cannot reveal
        // an omitted same-name item, so a fixed candidate cap makes it unreachable.
        val ids = (1..13).map { index ->
            db.productDao().insert(ProductEntity(name = "Молоко", defaultUnit = if (index == 13) "л" else "упак"))
        }
        val preview = bulk.preview("Молоко", 1).single()
        assertFalse(preview.resolved)
        val choices = bulk.candidates("Молоко", 1)
        assertEquals(ids.toSet(), choices.map { it.id }.toSet())
        assertTrue(db.shoppingListEntryDao().getAllSync().isEmpty())
        val chosen = choices.single { it.id == ids.last() }
        val row = preview.copy(productId = chosen.id, stableId = chosen.stableId, name = chosen.name, resolved = true)
        bulk.add(UUID.randomUUID().toString(), listOf(row), 1, ShopChoice.NONE, null, null, false, "шт", ProductEditorTransactions.generation.get())
        val purchase = db.shoppingListEntryDao().getAllSync().single()
        assertEquals(chosen.id, purchase.productId)
        assertEquals("л", purchase.unit)
        assertEquals(1.0, purchase.quantity, 0.0)
        assertEquals(ids.toSet(), db.productDao().getAllSync().map { it.id }.toSet())
    }

    @Test fun aVariantDisabledAfterPreviewRollsBackTheWholePackage(): Unit = runBlocking {
        val base = db.productDao().insert(ProductEntity(name = "Хлеб", defaultUnit = "шт"))
        val variants = ProductVariantsRepository(db)
        val alternate = variants.create(base, "Белый")
        val chosen = bulk.candidates("Хлеб", 1).single { it.id == alternate }
        val rows = listOf(
            BulkProductRow(0, "Новый товар", resolved = true),
            BulkProductRow(1, "Белый хлеб", chosen.name, chosen.id, chosen.stableId, true)
        )
        variants.configureCity(base, 1, listOf(base to true, alternate to false))
        val token = UUID.randomUUID().toString()
        assertTrue(runCatching {
            bulk.add(token, rows, 1, ShopChoice.NONE, null, null, true, "шт", ProductEditorTransactions.generation.get())
        }.isFailure)
        assertTrue(db.shoppingListEntryDao().getAllSync().isEmpty())
        assertEquals(setOf(base, alternate), db.productDao().getAllSync().map { it.id }.toSet())
        assertNull(db.productInputDao().run(token))
        assertFalse(bulk.candidates("Хлеб", 1).any { it.id == alternate })
    }
}
