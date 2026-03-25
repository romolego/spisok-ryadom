package com.spisokryadom.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.spisokryadom.app.data.entity.ShopEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShopDao {
    @Query("SELECT * FROM shops ORDER BY displayOrder ASC, name ASC")
    fun getAllOrdered(): Flow<List<ShopEntity>>

    @Query("SELECT * FROM shops WHERE id = :id")
    suspend fun getById(id: Long): ShopEntity?

    @Query("SELECT * FROM shops ORDER BY displayOrder ASC, name ASC")
    suspend fun getAllSync(): List<ShopEntity>

    @Query("DELETE FROM shops")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(shop: ShopEntity): Long

    @Update
    suspend fun update(shop: ShopEntity)

    @Update
    suspend fun updateAll(shops: List<ShopEntity>)

    @Delete
    suspend fun delete(shop: ShopEntity)
}
