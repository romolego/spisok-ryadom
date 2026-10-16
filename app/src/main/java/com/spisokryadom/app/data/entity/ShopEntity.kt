package com.spisokryadom.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "shops", indices = [Index("cityId")])
data class ShopEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val address: String? = null,
    val note: String? = null,
    val displayOrder: Int = 0,
    /** null = use global default, true/false = local override */
    val showUrgentInCollapsed: Boolean? = null,
    /** null = use global default, true/false = local override */
    val boostUrgentShop: Boolean? = null,
    /** true if user manually reordered this shop — suppresses auto-boost */
    val manualOrderOverride: Boolean = false,
    val cityId: Long = 1
)
