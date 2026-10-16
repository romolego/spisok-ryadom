package com.spisokryadom.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.spisokryadom.app.data.entity.UnitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UnitDao {
    @Query("SELECT * FROM units ORDER BY displayOrder ASC, name ASC")
    fun getAll(): Flow<List<UnitEntity>>

    @Query("SELECT * FROM units WHERE id = :id")
    suspend fun getById(id: Long): UnitEntity?

    @Query("SELECT * FROM units WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): UnitEntity?

    @Query("SELECT * FROM units ORDER BY displayOrder ASC, name ASC")
    suspend fun getAllSync(): List<UnitEntity>

    @Query("DELETE FROM units")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(unit: UnitEntity): Long

    @Update
    suspend fun update(unit: UnitEntity)

    @Query("SELECT COALESCE(MAX(displayOrder), 0) FROM units")
    suspend fun getMaxDisplayOrder(): Int

    @Delete
    suspend fun delete(unit: UnitEntity)
}
