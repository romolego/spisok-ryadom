package com.spisokryadom.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shop_departments",
    foreignKeys = [
        ForeignKey(
            entity = ShopEntity::class,
            parentColumns = ["id"],
            childColumns = ["shopId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("shopId")]
)
data class ShopDepartmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val shopId: Long,
    val name: String,
    val displayOrder: Int = 0
)
