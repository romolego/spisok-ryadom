package com.spisokryadom.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.spisokryadom.app.data.entity.RecipientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipientDao {
    @Query("SELECT * FROM recipients ORDER BY displayOrder ASC, name ASC")
    fun getAll(): Flow<List<RecipientEntity>>

    @Query("SELECT * FROM recipients WHERE id = :id")
    suspend fun getById(id: Long): RecipientEntity?

    @Query("SELECT * FROM recipients ORDER BY displayOrder ASC, name ASC")
    suspend fun getAllSync(): List<RecipientEntity>

    @Query("DELETE FROM recipients")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recipient: RecipientEntity): Long

    @Update
    suspend fun update(recipient: RecipientEntity)

    @Query("SELECT COALESCE(MAX(displayOrder), 0) FROM recipients")
    suspend fun getMaxDisplayOrder(): Int

    @Delete
    suspend fun delete(recipient: RecipientEntity)
}
