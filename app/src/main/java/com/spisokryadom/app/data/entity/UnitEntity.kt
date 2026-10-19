package com.spisokryadom.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "units")
data class UnitEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val displayOrder: Int = 0
)
