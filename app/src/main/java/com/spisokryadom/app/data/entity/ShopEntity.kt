package com.spisokryadom.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "shops", foreignKeys=[
 ForeignKey(entity=CityEntity::class,parentColumns=["id"],childColumns=["cityId"],onDelete=ForeignKey.RESTRICT),
 ForeignKey(entity=RetailNetworkEntity::class,parentColumns=["id"],childColumns=["networkId"],onDelete=ForeignKey.SET_NULL)
], indices = [Index("cityId"),Index("networkId")])
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
    val cityId: Long = 1,
    val networkId:Long? = null,
    /** Empty means transparent; color is independent of group/product accents. */
    @androidx.room.ColumnInfo(defaultValue="''") val shoppingBackground:String="",
    @androidx.room.ColumnInfo(defaultValue="''") val iconKey:String="",
    val logoUri:String?=null
)
