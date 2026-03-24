package com.spisokryadom.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(
            entity = ProductGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["productGroupId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = RecipientEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipientId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("productGroupId"),
        Index("recipientId"),
        Index("name")
    ]
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val productGroupId: Long? = null,
    val recipientId: Long? = null,
    val defaultUnit: String? = null,
    val defaultQuantity: Double? = null,
    val note: String? = null,
    val purchaseType: String = "offline",
    val sellerUrl: String? = null,
    val productUrl: String? = null,
    val photoUri: String? = null
)
