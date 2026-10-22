package com.spisokryadom.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** An owner of purchase facts. The regular basket is represented by a null owner. */
@Entity(tableName = "goal_lists", indices = [Index(value = ["stableId"], unique = true)])
data class GoalListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stableId: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    @ColumnInfo(defaultValue = "0") val showInPurchases: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val isTemplate: Boolean = false
)
