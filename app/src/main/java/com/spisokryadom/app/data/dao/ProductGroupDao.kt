package com.spisokryadom.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.spisokryadom.app.data.entity.ProductGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductGroupDao {
    @Query("SELECT * FROM product_groups ORDER BY displayOrder ASC, name ASC")
    fun getAll(): Flow<List<ProductGroupEntity>>

    @Query("SELECT * FROM product_groups WHERE id = :id")
    suspend fun getById(id: Long): ProductGroupEntity?

    @Query("SELECT * FROM product_groups ORDER BY displayOrder ASC, name ASC")
    suspend fun getAllSync(): List<ProductGroupEntity>

    @Query("DELETE FROM product_groups")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: ProductGroupEntity): Long

    @Update
    suspend fun update(group: ProductGroupEntity)

    @Query("SELECT COALESCE(MAX(displayOrder), 0) FROM product_groups")
    suspend fun getMaxDisplayOrder(): Int

    @Delete
    suspend fun delete(group: ProductGroupEntity)
}
