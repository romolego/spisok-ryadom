package com.spisokryadom.app.data.entity

import androidx.room.*

/** A brand is global; a Shop is its retailer profile in one city. */
@Entity(tableName="retail_networks")
data class RetailNetworkEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val name:String)

/** A market or mall contains physical outlets, independently of their retail brand. */
@Entity(tableName="venues",foreignKeys=[ForeignKey(entity=CityEntity::class,parentColumns=["id"],childColumns=["cityId"],onDelete=ForeignKey.RESTRICT)],indices=[Index("cityId")])
data class VenueEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val cityId:Long,val name:String,val address:String?=null,val note:String?=null,val photoUri:String?=null)

@Entity(tableName="department_types",indices=[Index(value=["normalizedName"],unique=true)])
data class DepartmentTypeEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val name:String,val normalizedName:String,
 @ColumnInfo(defaultValue="0") val displayOrder:Int=0,
 @ColumnInfo(defaultValue="''") val accent:String="",
 @ColumnInfo(defaultValue="''") val iconKey:String="",
 @ColumnInfo(defaultValue="0") val isFavorite:Boolean=false,
 @ColumnInfo(defaultValue="0") val favoriteOrder:Int=0)

/** Local route position of a shared group. Its ID remains stable on rename/reorder. */
@Entity(tableName="shop_departments",foreignKeys=[
 ForeignKey(entity=ShopEntity::class,parentColumns=["id"],childColumns=["shopId"],onDelete=ForeignKey.CASCADE),
 ForeignKey(entity=DepartmentTypeEntity::class,parentColumns=["id"],childColumns=["typeId"],onDelete=ForeignKey.CASCADE)
],indices=[Index("shopId"),Index("typeId")])
data class ShopDepartmentPlacementEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val shopId:Long,val typeId:Long,val displayOrder:Int=0)

/** Local route override; without one, the outlet inherits its retailer's route. */
@Entity(tableName="branch_department_order",primaryKeys=["branchId","typeId"],foreignKeys=[
 ForeignKey(entity=ShopBranchEntity::class,parentColumns=["id"],childColumns=["branchId"],onDelete=ForeignKey.CASCADE),
 ForeignKey(entity=DepartmentTypeEntity::class,parentColumns=["id"],childColumns=["typeId"],onDelete=ForeignKey.CASCADE)
],indices=[Index("typeId")])
data class BranchDepartmentOrderEntity(val branchId:Long,val typeId:Long,val displayOrder:Int)

/** A product's department at an exact physical outlet, overriding the retailer default. */
@Entity(tableName="product_branch_departments",primaryKeys=["productId","branchId"],foreignKeys=[
 ForeignKey(entity=ProductShopLinkEntity::class,parentColumns=["productId","shopId"],childColumns=["productId","shopId"],onDelete=ForeignKey.CASCADE),
 ForeignKey(entity=ShopBranchEntity::class,parentColumns=["id","shopId"],childColumns=["branchId","shopId"],onDelete=ForeignKey.CASCADE),
 ForeignKey(entity=DepartmentTypeEntity::class,parentColumns=["id"],childColumns=["typeId"],onDelete=ForeignKey.CASCADE)
],indices=[Index(value=["productId","shopId"]),Index(value=["branchId","shopId"]),Index("typeId")])
data class ProductBranchDepartmentEntity(val productId:Long,val branchId:Long,val shopId:Long,val typeId:Long)
