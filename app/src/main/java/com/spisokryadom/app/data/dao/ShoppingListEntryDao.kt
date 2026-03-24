package com.spisokryadom.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ShoppingListEntryEntity): Long

    @Update
    suspend fun update(entry: ShoppingListEntryEntity)

    @Delete
    suspend fun delete(entry: ShoppingListEntryEntity)

    @Query("DELETE FROM shopping_list_entries WHERE id = :id")
    suspend fun deleteById(id: Long)
}
