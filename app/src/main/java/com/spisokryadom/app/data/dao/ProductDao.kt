package com.spisokryadom.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.spisokryadom.app.data.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE isTemporary = 0 ORDER BY name ASC")
    fun getAll(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllIncludingTemporary(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE isTemporary = 0 AND name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchByName(query: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: Long): ProductEntity?

    @Query("SELECT DISTINCT defaultUnit FROM products WHERE defaultUnit IS NOT NULL AND defaultUnit != '' ORDER BY defaultUnit ASC")
    fun getDistinctUnits(): Flow<List<String>>

    @Query("SELECT * FROM products WHERE recipientId = :recipientId ORDER BY name ASC")
    fun getByRecipientId(recipientId: Long): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE productGroupId = :groupId ORDER BY name ASC")
    fun getByGroupId(groupId: Long): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE defaultUnit = :unit ORDER BY name ASC")
    fun getByUnit(unit: String): Flow<List<ProductEntity>>

    @Query("UPDATE products SET defaultUnit = :newUnit WHERE defaultUnit = :oldUnit")
    suspend fun renameUnit(oldUnit: String, newUnit: String)

    @Query("UPDATE products SET defaultUnit = NULL WHERE defaultUnit = :unit")
    suspend fun clearUnit(unit: String)

    @Query("SELECT * FROM products ORDER BY name ASC")
    suspend fun getAllSync(): List<ProductEntity>

    @Query("DELETE FROM products")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(product: ProductEntity): Long

    @Update
    suspend fun update(product: ProductEntity)

    @Delete
    suspend fun delete(product: ProductEntity)
    @Query("DELETE FROM products WHERE isTemporary=1 AND id NOT IN (SELECT productId FROM shopping_list_entries)")
    suspend fun deleteUnusedTemporary()
    @Query("SELECT * FROM products WHERE stableId=:stableId LIMIT 1")
    suspend fun getByStableId(stableId: String): ProductEntity?
}
