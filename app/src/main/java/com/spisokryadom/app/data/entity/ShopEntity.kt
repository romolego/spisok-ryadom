package com.spisokryadom.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "shops", foreignKeys=[
 ForeignKey(entity=CityEntity::class,parentColumns=["id"],childColumns=["cityId"],onDelete=ForeignKey.RESTRICT),
 ForeignKey(entity=RetailNetworkEntity::class,parentColumns=["id"],childColumns=["networkId"],onDelete=ForeignKey.SET_NULL)
], indices = [Index("cityId"),Index("networkId"),Index("cityGroupKey")])
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
    val logoUri:String?=null,
    /** Empty = ordinary cards; auto = stronger shop tint; otherwise a custom color. */
    @androidx.room.ColumnInfo(defaultValue="''") val purchaseBackground:String="",
    /** Browsing pins are independent of the shopping route in displayOrder. */
    @androidx.room.ColumnInfo(defaultValue="0") val isFavorite:Boolean=false,
    @androidx.room.ColumnInfo(defaultValue="0") val favoriteOrder:Int=0,
    /** Explicit siblings in other cities; an empty legacy key never merges by name. */
    @androidx.room.ColumnInfo(defaultValue="''") val cityGroupKey:String=""
)
