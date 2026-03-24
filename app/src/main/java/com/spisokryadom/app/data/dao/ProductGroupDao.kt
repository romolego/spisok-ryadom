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
    @Query("SELECT * FROM product_groups ORDER BY name ASC")
    fun getAll(): Flow<List<ProductGroupEntity>>

    @Query("SELECT * FROM product_groups WHERE id = :id")
    suspend fun getById(id: Long): ProductGroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: ProductGroupEntity): Long

    @Update
    suspend fun update(group: ProductGroupEntity)

    @Delete
    suspend fun delete(group: ProductGroupEntity)
}
