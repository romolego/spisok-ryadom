package com.spisokryadom.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.spisokryadom.app.data.entity.ShopDepartmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShopDepartmentDao {
    @Query("SELECT * FROM shop_departments WHERE shopId = :shopId ORDER BY displayOrder ASC, name ASC")
    fun getByShopId(shopId: Long): Flow<List<ShopDepartmentEntity>>

    @Query("SELECT * FROM shop_departments WHERE id = :id")
    suspend fun getById(id: Long): ShopDepartmentEntity?

    @Query("SELECT * FROM shop_departments ORDER BY shopId, displayOrder ASC")
    fun getAll(): Flow<List<ShopDepartmentEntity>>

    @Query("SELECT * FROM shop_departments WHERE shopId = :shopId ORDER BY displayOrder ASC, name ASC")
    suspend fun getByShopIdSync(shopId: Long): List<ShopDepartmentEntity>

    @Query("SELECT * FROM shop_departments ORDER BY shopId, displayOrder ASC")
    suspend fun getAllSync(): List<ShopDepartmentEntity>

    @Query("DELETE FROM shop_departments")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(department: ShopDepartmentEntity): Long

    @Update
    suspend fun update(department: ShopDepartmentEntity)

    @Delete
    suspend fun delete(department: ShopDepartmentEntity)
}
