package com.spisokryadom.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.spisokryadom.app.data.entity.ShoppingListEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingListEntryDao {
    @Query("""
        SELECT * FROM shopping_list_entries
        ORDER BY isBought ASC, isUrgent DESC, createdAt DESC
    """)
    fun getAll(): Flow<List<ShoppingListEntryEntity>>

    @Query("SELECT * FROM shopping_list_entries WHERE id = :id")
    suspend fun getById(id: Long): ShoppingListEntryEntity?

    @Query("""
        SELECT * FROM shopping_list_entries
        WHERE assignedShopId = :shopId
        ORDER BY isBought ASC, isUrgent DESC, createdAt DESC
    """)
    fun getByShopId(shopId: Long): Flow<List<ShoppingListEntryEntity>>

    @Query("UPDATE shopping_list_entries SET isBought = 1, updatedAt = :now WHERE id = :id")
    suspend fun markBought(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE shopping_list_entries SET isBought = 0, updatedAt = :now WHERE id = :id")
    suspend fun markNotBought(id: Long, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM shopping_list_entries WHERE isBought = 1")
    suspend fun clearBought()

    @Query("DELETE FROM shopping_list_entries WHERE isBought = 1 AND assignedShopId = :shopId")
    suspend fun clearBoughtByShop(shopId: Long)

    @Query("DELETE FROM shopping_list_entries WHERE isBought = 1 AND assignedShopId IS NULL")
    suspend fun clearBoughtWithoutShop()

    @Query("""
        SELECT * FROM shopping_list_entries
        WHERE productId = :productId AND isBought = 0
        LIMIT 1
    """)
    suspend fun findActiveByProductId(productId: Long): ShoppingListEntryEntity?

    @Query("SELECT * FROM shopping_list_entries ORDER BY createdAt ASC")
    suspend fun getAllSync(): List<ShoppingListEntryEntity>

    @Query("DELETE FROM shopping_list_entries")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ShoppingListEntryEntity): Long

    @Update
    suspend fun update(entry: ShoppingListEntryEntity)

    @Delete
    suspend fun delete(entry: ShoppingListEntryEntity)

    @Query("DELETE FROM shopping_list_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM shopping_list_entries WHERE productId=:productId AND isBought=0 AND unit=:unit AND assignedShopId IS :shopId ORDER BY id LIMIT 1")
    suspend fun findCompatible(productId: Long, unit: String, shopId: Long?): ShoppingListEntryEntity?
    @Query("UPDATE shopping_list_entries SET isUrgent=CASE WHEN isUrgent=1 THEN 0 ELSE 1 END, updatedAt=:now WHERE id=:id")
    suspend fun toggleUrgency(id: Long, now: Long = System.currentTimeMillis())
    /**
     * Atomically adds a new entry or merges quantity with existing active entry.
     */
    @Transaction
    suspend fun addOrMerge(entry: ShoppingListEntryEntity): Long {
        val existing = findCompatible(entry.productId, entry.unit, entry.assignedShopId)
        return if (existing != null) {
            val merged = existing.copy(
                quantity = com.spisokryadom.app.util.Quantities.valid(existing.quantity + entry.quantity),
                updatedAt = System.currentTimeMillis()
            )
            update(merged)
            existing.id
        } else {
            insert(entry)
        }
    }
}
