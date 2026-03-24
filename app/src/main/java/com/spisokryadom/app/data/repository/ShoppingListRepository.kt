package com.spisokryadom.app.data.repository

import com.spisokryadom.app.data.dao.ShoppingListEntryDao
import com.spisokryadom.app.data.entity.ShoppingListEntryEntity
import kotlinx.coroutines.flow.Flow

class ShoppingListRepository(
    private val entryDao: ShoppingListEntryDao
) {
    fun getAllEntries(): Flow<List<ShoppingListEntryEntity>> = entryDao.getAll()
    suspend fun getEntryById(id: Long): ShoppingListEntryEntity? = entryDao.getById(id)
    suspend fun addEntry(entry: ShoppingListEntryEntity): Long = entryDao.insert(entry)
    suspend fun updateEntry(entry: ShoppingListEntryEntity) = entryDao.update(entry)
    suspend fun removeEntry(id: Long) = entryDao.deleteById(id)
    suspend fun markBought(id: Long) = entryDao.markBought(id)
    suspend fun markNotBought(id: Long) = entryDao.markNotBought(id)
    suspend fun clearBoughtEntries() = entryDao.clearBought()
    suspend fun clearBoughtByShop(shopId: Long) = entryDao.clearBoughtByShop(shopId)
    suspend fun clearBoughtWithoutShop() = entryDao.clearBoughtWithoutShop()
}
