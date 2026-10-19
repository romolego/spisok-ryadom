package com.spisokryadom.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.backup.BackupManager
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.LocationRepository
import com.spisokryadom.app.data.repository.ShoppingListRepository
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
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28], application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class IndependentDataAuditTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var backup: BackupManager
    private val queries = CopyOnWriteArrayList<String>()

    @Before fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext<Application>()
        listOf("location_prefs", "shopping_list_prefs", "product_db_prefs").forEach {
            context.getSharedPreferences(it, 0).edit().clear().commit()
        }
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries()
            .setQueryCallback({ sql, _ -> queries.add(sql) }, java.util.concurrent.Executor { it.run() })
            .build()
        db.cityDao().insert(CityEntity(1, "Томск"))
        backup = BackupManager(context, db)
    }

    @After fun close() { db.close() }

    private fun legacy(data: JSONObject): File = File(context.cacheDir, "legacy_${UUID.randomUUID()}.json").also {
        it.writeText(JSONObject().put("version", 3).put("data", data).toString())
    }

    @Test fun legacyImportPreservesDifferentSourceProductsWithSameNameAndUnit() = runBlocking {
        val data = JSONObject()
            .put("recipients", JSONArray()
                .put(JSONObject().put("id", 1).put("name", "Мама"))
                .put(JSONObject().put("id", 2).put("name", "Ребёнок")))
            .put("products", JSONArray()
                .put(JSONObject().put("id", 10).put("name", "Молоко").put("defaultUnit", "л")
                    .put("recipientId", 1).put("note", "Без лактозы"))
                .put(JSONObject().put("id", 20).put("name", "Молоко").put("defaultUnit", "л")
                    .put("recipientId", 2).put("note", "Детское")))
        val file = legacy(data)
        backup.importFile(file)
        val imported = db.productDao().getAllSync()
        assertEquals("Two distinct IDs from one backup must remain two products", 2, imported.size)
        assertEquals(setOf("Без лактозы", "Детское"), imported.map { it.note }.toSet())
        backup.importFile(file)
        assertEquals(imported, db.productDao().getAllSync())
    }

    @Test fun legacyImportPreservesDifferentSourceShopsWithSameNameAndNoAddress() = runBlocking {
        val data = JSONObject().put("products", JSONArray())
            .put("shops", JSONArray()
                .put(JSONObject().put("id", 10).put("name", "Магнит").put("note", "У дома"))
                .put(JSONObject().put("id", 20).put("name", "Магнит").put("note", "У работы")))
        val file = legacy(data)
        backup.importFile(file)
        assertEquals("Two source shops may legitimately have the same chain name", 2, db.shopDao().getAllSync().size)
        val shops = db.shopDao().getAllSync()
        backup.importFile(file)
        assertEquals(shops, db.shopDao().getAllSync())
    }

    @Test fun sameNameSourceGraphAndExistingPurchasedCatalogSurviveRepeatedImport() = runBlocking {
        val existingGroup = db.productGroupDao().insert(ProductGroupEntity(name="Продукты", displayOrder=90))
        val existingRecipient = db.recipientDao().insert(RecipientEntity(name="Дом", displayOrder=90))
        val existingShop = db.shopDao().insert(ShopEntity(name="Магнит", note="Мой магазин", cityId=1))
        val existingProduct = ProductEntity(name="Молоко", defaultUnit="л", note="Моя запись",
            productGroupId=existingGroup, recipientId=existingRecipient, sellerUrl="https://existing.example")
        val existingProductId = db.productDao().insert(existingProduct)
        db.productShopLinkDao().insert(ProductShopLinkEntity(productId=existingProductId, shopId=existingShop))
        val bought = ShoppingListEntryEntity(productId=existingProductId, quantity=7.0, unit="л", isBought=true,
            assignedShopId=existingShop, note="Не менять")
        val boughtId = db.shoppingListEntryDao().insert(bought)
        val data = JSONObject()
            // Identical fields still represent distinct source IDs, not duplicate names to discard.
            .put("productGroups", JSONArray()
                .put(JSONObject().put("id",10).put("name","Продукты"))
                .put(JSONObject().put("id",20).put("name","Продукты")))
            .put("recipients", JSONArray()
                .put(JSONObject().put("id",10).put("name","Дом"))
                .put(JSONObject().put("id",20).put("name","Дом")))
            .put("shops", JSONArray()
                .put(JSONObject().put("id",10).put("name","Магнит").put("note","У дома"))
                .put(JSONObject().put("id",20).put("name","Магнит").put("note","У работы")))
            .put("shopDepartments", JSONArray()
                .put(JSONObject().put("id",10).put("shopId",10).put("name","Молочное"))
                .put(JSONObject().put("id",20).put("shopId",10).put("name","Молочное"))
                .put(JSONObject().put("id",30).put("shopId",20).put("name","Молочное")))
            .put("products", JSONArray()
                .put(JSONObject().put("id",10).put("name","Молоко").put("defaultUnit","л")
                    .put("recipientId",10).put("productGroupId",10).put("note","Без лактозы").put("defaultQuantity",0.5)
                    .put("sellerUrl","https://first.example").put("priceValue",101.0))
                .put(JSONObject().put("id",20).put("name","Молоко").put("defaultUnit","л")
                    .put("recipientId",20).put("productGroupId",20).put("note","Детское").put("defaultQuantity",2.0)
                    .put("sellerUrl","https://second.example").put("priceValue",202.0)))
            .put("productShopLinks", JSONArray()
                .put(JSONObject().put("id",10).put("productId",10).put("shopId",10).put("departmentId",10).put("priority",2))
                .put(JSONObject().put("id",20).put("productId",20).put("shopId",20).put("departmentId",30).put("priority",1)))
        val file = legacy(data)
        backup.importFile(file)
        val firstProducts=db.productDao().getAllSync()
        val firstShops=db.shopDao().getAllSync()
        val firstLinks=db.productShopLinkDao().getAllSync()
        val firstGroups=db.productGroupDao().getAllSync()
        val firstRecipients=db.recipientDao().getAllSync()
        val firstDepartments=db.shopDepartmentDao().getAllSync()
        assertEquals(3,firstProducts.size);assertEquals(3,firstShops.size)
        assertEquals(3,firstGroups.size);assertEquals(3,firstRecipients.size);assertEquals(3,firstDepartments.size)
        assertEquals(existingProduct.copy(id=existingProductId),db.productDao().getById(existingProductId))
        assertEquals(bought.copy(id=boughtId),db.shoppingListEntryDao().getById(boughtId))
        val imported=firstProducts.filter{it.id!=existingProductId}
        assertEquals(2,imported.map{it.productGroupId}.distinct().size)
        assertEquals(2,imported.map{it.recipientId}.distinct().size)
        for(product in imported){
            val link=firstLinks.single{it.productId==product.id}
            val shop=firstShops.single{it.id==link.shopId}
            assertEquals(if(product.note=="Без лактозы")"У дома" else "У работы",shop.note)
            assertEquals(shop.id,firstDepartments.single{it.id==link.departmentId}.shopId)
            assertEquals(if(product.note=="Без лактозы")0.5 else 2.0,product.defaultQuantity!!,0.0)
        }
        backup.importFile(file)
        assertEquals(firstProducts,db.productDao().getAllSync());assertEquals(firstShops,db.shopDao().getAllSync())
        assertEquals(firstLinks,db.productShopLinkDao().getAllSync());assertEquals(firstGroups,db.productGroupDao().getAllSync())
        assertEquals(firstRecipients,db.recipientDao().getAllSync());assertEquals(firstDepartments,db.shopDepartmentDao().getAllSync())
        assertEquals(bought.copy(id=boughtId),db.shoppingListEntryDao().getById(boughtId))
    }

    @Test fun identicalSourceProductsConsumePreexistingCandidatesAtMostOnce() = runBlocking {
        val originalId = db.productDao().insert(ProductEntity(name="Хлеб", defaultUnit="шт"))
        val data=JSONObject().put("products",JSONArray()
            .put(JSONObject().put("id",10).put("name","Хлеб").put("defaultUnit","шт"))
            .put(JSONObject().put("id",20).put("name","Хлеб").put("defaultUnit","шт")))
        val file=legacy(data)
        backup.importFile(file)
        val imported=db.productDao().getAllSync()
        assertEquals(2,imported.size);assertTrue(imported.any{it.id==originalId})
        backup.importFile(file)
        assertEquals(imported,db.productDao().getAllSync())
    }

    @Test fun largeLegacyImportReadsTablesOnceInsteadOfForEveryProduct() = runBlocking {
        val rows=JSONArray()
        repeat(1000){i->rows.put(JSONObject().put("id",i+1).put("name","Товар $i").put("defaultUnit","шт"))}
        val file=legacy(JSONObject().put("products",rows))
        queries.clear()
        backup.importFile(file)
        val selects=queries.count{it.trimStart().startsWith("SELECT",ignoreCase=true)}
        assertTrue("Whole catalog queries must not scale with product count: $selects",selects<40)
        val imported=db.productDao().getAllSync()
        assertEquals(1000,imported.size)
        queries.clear()
        backup.importFile(file)
        val secondSelects=queries.count{it.trimStart().startsWith("SELECT",ignoreCase=true)}
        assertTrue("Repeated import also uses fixed snapshots: $secondSelects",secondSelects<40)
        assertEquals(imported,db.productDao().getAllSync())
    }

    @Test fun malformedLegacyReferencesRollBackWithoutChangingCatalog() = runBlocking {
        val existingId=db.productDao().insert(ProductEntity(name="Оставить"))
        val data=JSONObject().put("products",JSONArray()
            .put(JSONObject().put("id",10).put("name","Плохая ссылка").put("recipientId",999)))
        try { backup.importFile(legacy(data)); fail("Invalid reference must be rejected") }
        catch (expected:IllegalArgumentException) { }
        assertEquals(existingId,db.productDao().getAllSync().single().id)
    }
}
