package com.spisokryadom.app.data.repository

import com.spisokryadom.app.data.dao.ShoppingListEntryDao
import com.spisokryadom.app.data.entity.ShoppingListEntryEntity
import kotlinx.coroutines.flow.Flow

class ShoppingListRepository(
    private val entryDao: ShoppingListEntryDao
) {
    fun getAllEntries(): Flow<List<ShoppingListEntryEntity>> = entryDao.getAll()
    fun getEntriesByShop(shopId: Long): Flow<List<ShoppingListEntryEntity>> = entryDao.getByShopId(shopId)
    suspend fun getEntryById(id: Long): ShoppingListEntryEntity? = entryDao.getById(id)
    suspend fun addEntry(entry: ShoppingListEntryEntity): Long = entryDao.insert(entry)

    /**
     * Adds product to shopping list or merges with existing unbought entry.
     * If an active (not bought) entry for the same productId exists,
     * its quantity is increased by [entry.quantity] and the entry is updated.
     * Otherwise a new entry is inserted.
     * Uses a Room @Transaction to guarantee atomicity.
     */
    suspend fun addOrMergeEntry(entry: ShoppingListEntryEntity): Long {
        return entryDao.addOrMerge(entry)
    }
    suspend fun findActiveByProductId(productId: Long): ShoppingListEntryEntity? =
        entryDao.findActiveByProductId(productId)

    /**
     * Updates quantity of the active entry for [productId].
     * If [newQuantity] <= 0, the entry is removed.
     * Returns true if the entry was updated/removed, false if not found.
     */
    suspend fun updateQuantityByProductId(productId: Long, newQuantity: Double): Boolean {
        val existing = entryDao.findActiveByProductId(productId) ?: return false
        if (newQuantity <= 0.0) {
            entryDao.deleteById(existing.id)
        } else {
            entryDao.update(existing.copy(quantity = newQuantity, updatedAt = System.currentTimeMillis()))
        }
        return true
    }

    suspend fun removeByProductId(productId: Long): Boolean {
        val existing = entryDao.findActiveByProductId(productId) ?: return false
        entryDao.deleteById(existing.id)
        return true
    }

    suspend fun updateEntry(entry: ShoppingListEntryEntity) = entryDao.update(entry)
    suspend fun removeEntry(id: Long) = entryDao.deleteById(id)
    suspend fun markBought(id: Long) = entryDao.markBought(id)
    suspend fun markNotBought(id: Long) = entryDao.markNotBought(id)
    suspend fun clearBoughtEntries() = entryDao.clearBought()
    suspend fun clearBoughtByShop(shopId: Long) = entryDao.clearBoughtByShop(shopId)
    suspend fun clearBoughtWithoutShop() = entryDao.clearBoughtWithoutShop()
}
