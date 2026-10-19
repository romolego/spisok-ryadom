package com.spisokryadom.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.spisokryadom.app.data.entity.GoalListEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalListDao {
    @Query("SELECT * FROM goal_lists ORDER BY createdAt ASC,id ASC")
    fun observeAll(): Flow<List<GoalListEntity>>

    @Query("SELECT * FROM goal_lists ORDER BY createdAt ASC,id ASC")
    suspend fun getAllSync(): List<GoalListEntity>

    @Query("SELECT * FROM goal_lists WHERE id=:id")
    suspend fun getById(id: Long): GoalListEntity?

    @Query("SELECT * FROM goal_lists WHERE stableId=:stableId LIMIT 1")
    suspend fun getByStableId(stableId: String): GoalListEntity?

    @Insert
    suspend fun insert(list: GoalListEntity): Long

    @Update
    suspend fun update(list: GoalListEntity)

    @Query("DELETE FROM goal_lists WHERE id=:id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM goal_lists")
    suspend fun deleteAll()
}
