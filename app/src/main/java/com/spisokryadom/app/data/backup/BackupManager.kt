package com.spisokryadom.app.data.backup

import android.content.Context
import android.util.Base64
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.spisokryadom.app.data.dao.ProductDao
import com.spisokryadom.app.data.dao.ProductGroupDao
import com.spisokryadom.app.data.dao.ProductShopLinkDao
import com.spisokryadom.app.data.dao.RecipientDao
import com.spisokryadom.app.data.dao.ShopDao
import com.spisokryadom.app.data.dao.ShopDepartmentDao
import com.spisokryadom.app.data.dao.ShoppingListEntryDao
import com.spisokryadom.app.data.entity.ProductEntity
import com.spisokryadom.app.data.entity.ProductGroupEntity
import com.spisokryadom.app.data.entity.ProductShopLinkEntity
import com.spisokryadom.app.data.entity.RecipientEntity
import com.spisokryadom.app.data.entity.ShopDepartmentEntity
import com.spisokryadom.app.data.entity.ShopEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Экспорт и импорт постоянной базы товаров и магазинов в JSON-файл.
 *
 * Формат файла (версия 2):
 * {
 *   "version": 2,
 *   "exportedAt": "2026-03-25T10:30:00",
 *   "data": {
 *     "productGroups": [...],
 *     "recipients": [...],
 *     "shops": [...],
 *     "shopDepartments": [...],
 *     "products": [...],          // включает photoBase64 для товаров с фото
 *     "productShopLinks": [...]
 *   }
 * }
 *
 * Список покупок (shoppingListEntries) НЕ участвует в экспорте/импорте.
 * Импорт — полная замена постоянной базы: товары, магазины, группы, получатели, отделы, привязки.
 */
class BackupManager(
    private val context: Context,
    private val db: RoomDatabase,
    private val productDao: ProductDao,
    private val productGroupDao: ProductGroupDao,
    private val recipientDao: RecipientDao,
    private val shopDao: ShopDao,
    private val shopDepartmentDao: ShopDepartmentDao,
    private val productShopLinkDao: ProductShopLinkDao,
    private val shoppingListEntryDao: ShoppingListEntryDao
) {

    private val photosDir: File
        get() = File(context.filesDir, "product_photos").also { it.mkdirs() }

    // ── Export ────────────────────────────────────────────────────────────────

    suspend fun exportToJson(): String {
        val groups = productGroupDao.getAllSync()
        val recipients = recipientDao.getAllSync()
        val shops = shopDao.getAllSync()
        val departments = shopDepartmentDao.getAllSync()
        val products = productDao.getAllSync()
        val links = productShopLinkDao.getAllSync()

        val data = JSONObject().apply {
            put("productGroups", groupsToJson(groups))
            put("recipients", recipientsToJson(recipients))
            put("shops", shopsToJson(shops))
            put("shopDepartments", departmentsToJson(departments))
            put("products", productsToJson(products))
            put("productShopLinks", linksToJson(links))
        }

        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        return JSONObject().apply {
            put("version", BACKUP_VERSION)
            put("exportedAt", timestamp)
            put("data", data)
        }.toString(2)
    }

    // ── Import ────────────────────────────────────────────────────────────────

    /**
     * Полная замена постоянной базы: удаляет товары, магазины, группы, получателей,
     * отделы и привязки, затем вставляет данные из JSON.
     * Список покупок НЕ затрагивается.
     *
     * @throws IllegalArgumentException если формат файла не поддерживается
     * @throws org.json.JSONException если JSON некорректен
     */
    suspend fun importFromJson(json: String) {
        val root = JSONObject(json)
        val version = root.getInt("version")
        if (version > BACKUP_VERSION) {
            throw IllegalArgumentException(
                "Версия файла ($version) новее, чем поддерживаемая ($BACKUP_VERSION)"
            )
        }

        val data = root.getJSONObject("data")

        val groups = data.getJSONArray("productGroups").toProductGroups()
        val recipients = data.getJSONArray("recipients").toRecipients()
        val shops = data.getJSONArray("shops").toShops()
        val departments = data.getJSONArray("shopDepartments").toShopDepartments()
        val productsWithPhotos = data.getJSONArray("products").toProductsWithPhotos()
        val links = data.getJSONArray("productShopLinks").toProductShopLinks()

        // Восстанавливаем фото-файлы до транзакции
        deleteAllProductPhotos()
        val products = productsWithPhotos.map { (product, photoBase64) ->
            if (photoBase64 != null) {
                val photoFile = File(photosDir, "${UUID.randomUUID()}.jpg")
                val bytes = Base64.decode(photoBase64, Base64.DEFAULT)
                photoFile.writeBytes(bytes)
                product.copy(photoUri = photoFile.absolutePath)
            } else {
                product
            }
        }

        // Сохраняем записи списка покупок ДО транзакции, иначе CASCADE их удалит
        val savedEntries = shoppingListEntryDao.getAllSync()

        db.withTransaction {
            // Порядок удаления: сначала зависимые таблицы
            productShopLinkDao.deleteAll()
            shopDepartmentDao.deleteAll()
            // Явно удаляем записи списка покупок до удаления товаров (чтобы CASCADE не срабатывал)
            shoppingListEntryDao.deleteAll()
            productDao.deleteAll()
            shopDao.deleteAll()
            productGroupDao.deleteAll()
            recipientDao.deleteAll()

            // Порядок вставки: сначала родительские таблицы
            groups.forEach { productGroupDao.insert(it) }
            recipients.forEach { recipientDao.insert(it) }
            shops.forEach { shopDao.insert(it) }
            departments.forEach { shopDepartmentDao.insert(it) }
            products.forEach { productDao.insert(it) }
            links.forEach { productShopLinkDao.insert(it) }

            // Восстанавливаем записи списка покупок (только те, чьи товары есть в новой базе)
            val newProductIds = products.map { it.id }.toSet()
            val newShopIds = shops.map { it.id }.toSet()
            val newDeptIds = departments.map { it.id }.toSet()

            savedEntries.forEach { entry ->
                if (entry.productId in newProductIds) {
                    shoppingListEntryDao.insert(
                        entry.copy(
                            assignedShopId = entry.assignedShopId?.takeIf { it in newShopIds },
                            assignedDepartmentId = entry.assignedDepartmentId?.takeIf { it in newDeptIds }
                        )
                    )
                }
            }
        }
    }

    // ── Photo helpers ─────────────────────────────────────────────────────────

    private fun readPhotoAsBase64(photoPath: String): String? {
        return try {
            val file = File(photoPath)
            if (file.exists() && file.isFile) {
                Base64.encodeToString(file.readBytes(), Base64.DEFAULT)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun deleteAllProductPhotos() {
        val dir = photosDir
        if (dir.exists()) {
            dir.listFiles()?.forEach { it.delete() }
        }
    }

    // ── Serialization helpers ─────────────────────────────────────────────────

    private fun groupsToJson(list: List<ProductGroupEntity>) = JSONArray().also { arr ->
        list.forEach { g ->
            arr.put(JSONObject().apply {
                put("id", g.id)
                put("name", g.name)
            })
        }
    }

    private fun recipientsToJson(list: List<RecipientEntity>) = JSONArray().also { arr ->
        list.forEach { r ->
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("name", r.name)
            })
        }
    }

    private fun shopsToJson(list: List<ShopEntity>) = JSONArray().also { arr ->
        list.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                putOpt("address", s.address)
                putOpt("note", s.note)
                put("displayOrder", s.displayOrder)
            })
        }
    }

    private fun departmentsToJson(list: List<ShopDepartmentEntity>) = JSONArray().also { arr ->
        list.forEach { d ->
            arr.put(JSONObject().apply {
                put("id", d.id)
                put("shopId", d.shopId)
                put("name", d.name)
                put("displayOrder", d.displayOrder)
            })
        }
    }

    private fun productsToJson(list: List<ProductEntity>) = JSONArray().also { arr ->
        list.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                putOpt("productGroupId", p.productGroupId)
                putOpt("recipientId", p.recipientId)
                putOpt("defaultUnit", p.defaultUnit)
                putOpt("defaultQuantity", p.defaultQuantity)
                putOpt("note", p.note)
                put("purchaseType", p.purchaseType)
                putOpt("sellerUrl", p.sellerUrl)
                putOpt("productUrl", p.productUrl)
                putOpt("priceValue", p.priceValue)
                putOpt("priceDate", p.priceDate)
                if (!p.photoUri.isNullOrBlank()) {
                    val base64 = readPhotoAsBase64(p.photoUri)
                    if (base64 != null) {
                        put("photoBase64", base64)
                    }
                }
            })
        }
    }

    private fun linksToJson(list: List<ProductShopLinkEntity>) = JSONArray().also { arr ->
        list.forEach { l ->
            arr.put(JSONObject().apply {
                put("id", l.id)
                put("productId", l.productId)
                put("shopId", l.shopId)
                put("priority", l.priority)
                putOpt("departmentId", l.departmentId)
            })
        }
    }

    // ── Deserialization helpers ───────────────────────────────────────────────

    private fun JSONArray.toProductGroups() = (0 until length()).map { i ->
        val o = getJSONObject(i)
        ProductGroupEntity(id = o.getLong("id"), name = o.getString("name"))
    }

    private fun JSONArray.toRecipients() = (0 until length()).map { i ->
        val o = getJSONObject(i)
        RecipientEntity(id = o.getLong("id"), name = o.getString("name"))
    }

    private fun JSONArray.toShops() = (0 until length()).map { i ->
        val o = getJSONObject(i)
        ShopEntity(
            id = o.getLong("id"),
            name = o.getString("name"),
            address = o.optString("address").ifEmpty { null },
            note = o.optString("note").ifEmpty { null },
            displayOrder = o.optInt("displayOrder", 0)
        )
    }

    private fun JSONArray.toShopDepartments() = (0 until length()).map { i ->
        val o = getJSONObject(i)
        ShopDepartmentEntity(
            id = o.getLong("id"),
            shopId = o.getLong("shopId"),
            name = o.getString("name"),
            displayOrder = o.optInt("displayOrder", 0)
        )
    }

    /**
     * Возвращает пары (ProductEntity, photoBase64?).
     * photoUri в entity пока пустой — будет заполнен после записи файла.
     */
    private fun JSONArray.toProductsWithPhotos(): List<Pair<ProductEntity, String?>> =
        (0 until length()).map { i ->
            val o = getJSONObject(i)
            val photoBase64 = o.optString("photoBase64").ifEmpty { null }
            val product = ProductEntity(
                id = o.getLong("id"),
                name = o.getString("name"),
                productGroupId = if (o.isNull("productGroupId")) null else o.getLong("productGroupId"),
                recipientId = if (o.isNull("recipientId")) null else o.getLong("recipientId"),
                defaultUnit = o.optString("defaultUnit").ifEmpty { null },
                defaultQuantity = if (o.isNull("defaultQuantity")) null else o.getDouble("defaultQuantity"),
                note = o.optString("note").ifEmpty { null },
                purchaseType = o.optString("purchaseType", "offline"),
                sellerUrl = o.optString("sellerUrl").ifEmpty { null },
                productUrl = o.optString("productUrl").ifEmpty { null },
                photoUri = null, // будет заполнен после восстановления файла
                priceValue = if (o.isNull("priceValue")) null else o.getDouble("priceValue"),
                priceDate = o.optString("priceDate").ifEmpty { null }
            )
            product to photoBase64
        }

    private fun JSONArray.toProductShopLinks() = (0 until length()).map { i ->
        val o = getJSONObject(i)
        ProductShopLinkEntity(
            id = o.getLong("id"),
            productId = o.getLong("productId"),
            shopId = o.getLong("shopId"),
            priority = o.optInt("priority", 1),
            departmentId = if (o.isNull("departmentId")) null else o.getLong("departmentId")
        )
    }

    companion object {
        const val BACKUP_VERSION = 2
        const val BACKUP_FILE_MIME = "application/json"
        const val BACKUP_FILE_NAME_PREFIX = "spisok_ryadom_backup"
    }
}
