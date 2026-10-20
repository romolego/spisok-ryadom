package com.spisokryadom.app.data.repository

import androidx.room.withTransaction
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.domain.GoalListDocument
import com.spisokryadom.app.domain.GoalListItemDraft
import com.spisokryadom.app.domain.GoalListTransfer
import com.spisokryadom.app.domain.PlaceLabel
import com.spisokryadom.app.domain.PlaceLabels
import com.spisokryadom.app.domain.physicalPlaceLabel
import com.spisokryadom.app.util.Quantities

/** Fields deliberately edited in one target-purchase editor session. */
enum class GoalItemField { NAME, QUANTITY, UNIT, PRICE, NOTE, COMMENT, PLACE }

data class GoalListSnapshot(
    val list: GoalListEntity,
    val entries: List<ShoppingListEntryEntity>,
    val products: List<ProductEntity>,
    val shops: List<ShopEntity>,
    val branches: List<ShopBranchEntity>,
    val venues: List<VenueEntity>
) {
    fun place(entry: ShoppingListEntryEntity): PlaceLabel? {
        val shop = shops.firstOrNull { it.id == entry.assignedShopId }
        if (shop != null) return PlaceLabels(branches, venues).place(shop.name, branches.firstOrNull { it.id == entry.assignedBranchId })
        val name = entry.customShopName.orEmpty().trim()
        val address = entry.customShopAddress.orEmpty().trim()
        return if (name.isEmpty() && address.isEmpty()) null
        else physicalPlaceLabel(name.ifBlank { address }, address.takeIf { name.isNotBlank() }, null)
    }
}

/** Target lists own the same purchase rows used by the main basket, never copies of them. */
class GoalListRepository(private val db: AppDatabase, private val shopping: ShoppingListRepository) {
    private val lists get() = db.goalListDao()
    private val entries get() = db.shoppingListEntryDao()

    fun observeLists() = lists.observeAll()
    fun observeEntries(listId: Long) = entries.getForList(listId)
    suspend fun getList(id: Long) = lists.getById(id)

    /** Serializes an editor session with full restore and rejects commands from an older database. */
    suspend fun <T> withSession(expectedGeneration: Long, block: suspend () -> T): T = ProductEditorTransactions.locked {
        require(ProductEditorTransactions.generation.get() == expectedGeneration) {
            "База восстановлена. Откройте список заново"
        }
        block()
    }

    suspend fun create(name: String): Long = db.withTransaction {
        lists.insert(GoalListEntity(name = validListName(name)))
    }

    suspend fun updateList(id: Long, name: String, showInPurchases: Boolean) = db.withTransaction {
        val list = requireList(id)
        lists.update(list.copy(name = validListName(name), showInPurchases = showInPurchases, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteList(id: Long) = db.withTransaction {
        requireList(id)
        lists.deleteById(id)
        db.productDao().deleteUnusedTemporary()
    }

    suspend fun addQuick(
        listId: Long, name: String, quantity: Double = 1.0, unit: String = "",
        plannedUnitPrice: Double? = null, shopName: String? = null,
        shopAddress: String? = null, note: String? = null, draftStableId: String? = null,
        comment: String? = null
    ): Long = db.withTransaction {
        requireList(listId)
        val safeName = validItemName(name)
        val safeQuantity = Quantities.valid(quantity)
        val safeUnit = validUnit(unit)
        val price = validPrice(plannedUnitPrice)
        require(price == null || (price * safeQuantity).isFinite()) { "Слишком большая сумма" }
        val placeName = shortText(shopName, 500)
        val placeAddress = shortText(shopAddress, 1000)
        val safeNote = longText(note)
        val safeComment = longText(comment)
        val stableId = draftStableId ?: java.util.UUID.randomUUID().toString()
        require(stableId.matches(Regex("[a-zA-Z0-9_-]{1,80}"))) { "Неверная идентичность черновика" }
        val existing = db.productDao().getByStableId(stableId)
        if (existing != null) {
            require(existing.isTemporary) { "Черновик конфликтует с товаром из базы" }
            val row = entries.getAllSync().filter { it.productId == existing.id }.singleOrNull()
                ?: error("Покупка черновика удалена или изменена")
            require(row.listId == listId) { "Покупка перемещена; откройте её из нового списка" }
            updateItem(row.id, safeName, safeQuantity, safeUnit, price, safeNote,
                placeName.orEmpty(), placeAddress.orEmpty(), expectedListId = listId, comment = comment)
            return@withTransaction row.id
        }
        val productId = db.productDao().insert(ProductEntity(name = safeName, isTemporary = true,
            defaultUnit = safeUnit.ifBlank { null }, stableId = stableId))
        entries.insert(ShoppingListEntryEntity(productId = productId, listId = listId,
            quantity = safeQuantity, unit = safeUnit, explicitNoShop = true,
            plannedUnitPrice = price, customShopName = placeName, customShopAddress = placeAddress,
            note = safeNote, comment = safeComment))
    }

    /** Reuses the regular list's availability and physical-outlet assignment policy. */
    suspend fun addCatalogue(
        listId: Long, productId: Long, quantity: Double? = null, unit: String? = null,
        plannedUnitPrice: Double? = null, choice: ShopChoice = ShopChoice.NONE,
        shopId: Long? = null, branchId: Long? = null
    ): Long = db.withTransaction {
        requireList(listId)
        if (unit != null) validUnit(unit)
        shopping.addProduct(productId, quantity, unit, choice, shopId, branchId, listId, validPrice(plannedUnitPrice))
    }

    /** Null place parameters preserve the current assignment; empty strings deliberately clear it. */
    suspend fun updateItem(
        entryId: Long, name: String?, quantity: Double, unit: String, plannedUnitPrice: Double?,
        note: String? = null, customShopName: String? = null, customShopAddress: String? = null,
        expectedListId: Long? = null, comment: String? = null, editedFields: Set<GoalItemField>? = null
    ) = db.withTransaction {
        var entry = requireGoalEntry(entryId, expectedListId)
        if (editedFields?.isEmpty() == true) return@withTransaction
        val product = db.productDao().getById(entry.productId) ?: error("Товар удалён")
        fun edited(field: GoalItemField) = editedFields == null || field in editedFields
        val safeName = name?.takeIf { edited(GoalItemField.NAME) }?.let(::validItemName)
        val safeQuantity = if (edited(GoalItemField.QUANTITY)) Quantities.valid(quantity) else entry.quantity
        val safeUnit = if (edited(GoalItemField.UNIT)) validUnit(unit) else entry.unit
        val price = if (edited(GoalItemField.PRICE)) validPrice(plannedUnitPrice) else entry.plannedUnitPrice
        require(price == null || (price * safeQuantity).isFinite()) { "Слишком большая сумма" }
        val safeNote = if (edited(GoalItemField.NOTE)) longText(note) else entry.note
        // A comment is a separate purchase fact in the regular basket. Moving the
        // row into a target list must not hide or erase it on an unrelated edit.
        val safeComment = when {
            editedFields != null -> if (GoalItemField.COMMENT in editedFields) longText(comment) else entry.comment
            comment == null -> entry.comment
            else -> longText(comment)
        }
        if (safeName != null && safeName != product.name) {
            require(product.isTemporary) { "Название товара из базы меняется в карточке товара" }
            // A moved/shared temporary product may still be referenced by another list.
            val shared = entries.getAllSync().any { it.id != entryId && it.productId == product.id }
            if (shared) {
                val clone = db.productDao().insert(product.copy(id = 0, stableId = java.util.UUID.randomUUID().toString(), name = safeName))
                entry = entry.copy(productId = clone)
            } else db.productDao().update(product.copy(name = safeName))
        }
        if (edited(GoalItemField.PLACE) && (customShopName != null || customShopAddress != null)) {
            val currentPlace = snapshot(entry.listId!!).place(entry)
            val currentName = currentPlace?.title.orEmpty()
            val currentAddress = currentPlace?.subtitle.orEmpty()
            val requestedName = shortText(customShopName ?: currentName, 500)
            val requestedAddress = shortText(customShopAddress ?: currentAddress, 1000)
            if (requestedName.orEmpty() != currentName || requestedAddress.orEmpty() != currentAddress) {
                entry = entry.copy(assignedShopId = null, assignedBranchId = null, assignedDepartmentId = null,
                    awaitingShopCityId = null, explicitNoShop = true,
                    customShopName = requestedName, customShopAddress = requestedAddress)
            }
        }
        entries.update(entry.copy(quantity = safeQuantity, unit = safeUnit, plannedUnitPrice = price, note = safeNote,
            comment = safeComment, updatedAt = System.currentTimeMillis()))
        db.productDao().deleteUnusedTemporary()
    }

    /** Read the latest purchase facts before applying a +/- action from a UI snapshot. */
    suspend fun adjustQuantity(entryId: Long, delta: Double, expectedListId: Long? = null) = db.withTransaction {
        require(delta.isFinite()) { "Укажите корректное изменение количества" }
        val entry = requireGoalEntry(entryId, expectedListId)
        if (entry.isBought) return@withTransaction
        // Unlike the regular basket, a target's last unit remains an editable plan.
        val next = entry.quantity + delta
        if (next <= 0.0) return@withTransaction
        val quantity = Quantities.valid(next)
        entries.update(entry.copy(quantity = quantity, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setBought(entryId: Long, bought: Boolean, expectedListId: Long? = null) = db.withTransaction {
        requireGoalEntry(entryId, expectedListId)
        if (bought) entries.markBought(entryId) else entries.markNotBought(entryId)
    }

    suspend fun toggleUrgent(entryId: Long, expectedListId: Long? = null) = db.withTransaction {
        requireGoalEntry(entryId, expectedListId)
        entries.toggleUrgency(entryId)
    }

    suspend fun removeItem(entryId: Long, expectedListId: Long? = null) = db.withTransaction {
        requireGoalEntry(entryId, expectedListId)
        entries.deleteById(entryId)
        db.productDao().deleteUnusedTemporary()
    }

    suspend fun clearBought(listId: Long) = db.withTransaction {
        requireList(listId)
        entries.clearBoughtInList(listId)
        db.productDao().deleteUnusedTemporary()
    }


    /** A selection is valid only while every row still belongs to its original owner. */
    suspend fun moveEntriesExpected(expectedOwners: Map<Long, Long?>, targetListId: Long?) = db.withTransaction {
        if (targetListId != null) requireList(targetListId)
        val rows = requireOwners(expectedOwners)
        rows.forEach { entries.update(it.copy(listId = targetListId, updatedAt = System.currentTimeMillis())) }
    }

    suspend fun moveEntriesFrom(listId: Long, ids: Set<Long>, targetListId: Long?) =
        moveEntriesExpected(ids.associateWith { listId }, targetListId)

    /** Validate the complete selection before changing even its first member. */
    suspend fun batch(listId: Long, ids: Set<Long>, bought: Boolean? = null, delete: Boolean = false) = db.withTransaction {
        requireList(listId)
        require(delete != (bought != null)) { "Выберите одно действие" }
        val rows = requireOwners(ids.associateWith { listId })
        rows.forEach {
            if (delete) entries.deleteById(it.id)
            else if (bought == true) entries.markBought(it.id)
            else entries.markNotBought(it.id)
        }
        if (delete) db.productDao().deleteUnusedTemporary()
    }

    suspend fun snapshot(listId: Long): GoalListSnapshot = db.withTransaction {
        val list = requireList(listId)
        val rows = entries.getForListSync(listId)
        val ids = rows.map { it.productId }.toSet()
        GoalListSnapshot(list, rows, db.productDao().getAllSync().filter { it.id in ids },
            db.shopDao().getAllSync(), db.shopBranchDao().getAll(), db.retailStructureDao().venues())
    }

    suspend fun exportDocument(listId: Long): GoalListDocument {
        val snapshot = snapshot(listId)
        val products = snapshot.products.associateBy { it.id }
        return GoalListDocument(snapshot.list.name, snapshot.entries.map { entry ->
            val product = products[entry.productId] ?: error("Товар удалён")
            val place = snapshot.place(entry)
            val assigned = snapshot.shops.any { it.id == entry.assignedShopId }
            GoalListItemDraft(name = product.name, quantity = entry.quantity, unit = entry.unit,
                unitPrice = entry.plannedUnitPrice,
                note = entry.note.orEmpty(), comment = entry.comment.orEmpty(),
                shopName = if (assigned) place?.title.orEmpty() else entry.customShopName.orEmpty(),
                shopAddress = if (assigned) place?.subtitle.orEmpty() else entry.customShopAddress.orEmpty(),
                isBought = entry.isBought, isUrgent = entry.isUrgent)
        })
    }

    /** Portable import creates an isolated hidden list, never matches local catalogue IDs/names. */
    suspend fun importDocument(document: GoalListDocument, importStableId: String? = null): Long = db.withTransaction {
        val stableId = importStableId ?: java.util.UUID.randomUUID().toString()
        require(stableId.matches(Regex("[a-zA-Z0-9_-]{1,80}"))) { "Неверная идентичность импорта" }
        // A restored confirmation may retry after the transaction committed but before
        // its UI result was delivered. It opens that exact import without changing it.
        lists.getByStableId(stableId)?.let { return@withTransaction it.id }
        val errors = GoalListTransfer.validate(document)
        require(errors.isEmpty()) { errors.joinToString("\n") }
        val name = validListName(document.name)
        // Validate every item before starting inserts, also for callers bypassing the text codec.
        document.items.forEach {
            validItemName(it.name); Quantities.valid(it.quantity); validUnit(it.unit); validPrice(it.unitPrice)
            longText(it.note); longText(it.comment); shortText(it.shopName, 500); shortText(it.shopAddress, 1000)
        }
        val id = lists.insert(GoalListEntity(name = name, stableId = stableId))
        document.items.forEach {
            val entryId = addQuick(id, it.name, it.quantity, it.unit, it.unitPrice, it.shopName, it.shopAddress, it.note,
                comment = it.comment)
            if (it.isUrgent) entries.toggleUrgency(entryId)
        }
        id
    }

    private suspend fun requireList(id: Long) = lists.getById(id) ?: error("Список удалён")
    private suspend fun requireOwners(expectedOwners: Map<Long, Long?>): List<ShoppingListEntryEntity> {
        require(expectedOwners.isNotEmpty()) { "Выберите покупки" }
        return expectedOwners.map { (id, owner) ->
            val row = entries.getById(id) ?: error("Покупка удалена; обновите выделение")
            require(row.listId == owner) { "Покупки перемещены; обновите выделение" }
            row
        }
    }
    private suspend fun requireGoalEntry(id: Long, expectedListId: Long? = null): ShoppingListEntryEntity {
        val entry = entries.getById(id) ?: error("Покупка удалена")
        require(entry.listId != null) { "Покупка не относится к целевому списку" }
        require(expectedListId == null || entry.listId == expectedListId) { "Покупка перемещена; откройте её из нового списка" }
        requireList(entry.listId)
        return entry
    }

    companion object {
        fun validPrice(value: Double?): Double? {
            require(value == null || (value.isFinite() && value >= 0.0 && value <= 1_000_000_000_000.0)) { "Укажите корректную цену" }
            return value
        }
        private fun validListName(value: String) = validName(value, 200)
        private fun validItemName(value: String) = validName(value, 500)
        private fun validName(value: String, limit: Int): String = value.trim().also {
            require(it.isNotEmpty() && it.length <= limit && '\u0000' !in it) { "Укажите название до $limit символов" }
        }
        private fun validUnit(value: String): String {
            require(value.length <= 80 && '\u0000' !in value) { "Некорректная единица измерения" }
            return Quantities.unit(value)
        }
        private fun shortText(value: String?, limit: Int): String? = value?.trim()?.also {
            require(it.length <= limit && '\u0000' !in it) { "Слишком длинное место покупки" }
        }?.ifBlank { null }
        private fun longText(value: String?): String? = value?.trim()?.also {
            require(it.length <= 10_000 && '\u0000' !in it) { "Заметка слишком длинная" }
        }?.ifBlank { null }
    }
}
