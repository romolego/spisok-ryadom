package com.spisokryadom.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "product_groups")
data class ProductGroupEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String
)
