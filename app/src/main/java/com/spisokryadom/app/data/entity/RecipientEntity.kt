package com.spisokryadom.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recipients")
data class RecipientEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val displayOrder: Int = 0,
    @androidx.room.ColumnInfo(defaultValue="''") val accent:String="",
    @androidx.room.ColumnInfo(defaultValue="''") val iconKey:String=""
)
