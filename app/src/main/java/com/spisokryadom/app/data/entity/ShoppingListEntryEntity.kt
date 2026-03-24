package com.spisokryadom.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shopping_list_entries",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ShopEntity::class,
            parentColumns = ["id"],
            childColumns = ["assignedShopId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = ShopDepartmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["assignedDepartmentId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("productId"),
        Index("assignedShopId"),
        Index("assignedDepartmentId")
    ]
)
data class ShoppingListEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val productId: Long,
    val quantity: Double = 0.0,
    val unit: String = "",
    val assignedShopId: Long? = null,
    val assignedDepartmentId: Long? = null,
    val isBought: Boolean = false,
    val isUrgent: Boolean = false,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
