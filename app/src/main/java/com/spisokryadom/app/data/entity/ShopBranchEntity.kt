package com.spisokryadom.app.data.entity
import androidx.room.*

/** One physical outlet; its retailer may be a network, and its venue is independent. */
@Entity(tableName="shop_branches",foreignKeys=[
 ForeignKey(entity=ShopEntity::class,parentColumns=["id"],childColumns=["shopId"],onDelete=ForeignKey.CASCADE),
 ForeignKey(entity=VenueEntity::class,parentColumns=["id"],childColumns=["venueId"],onDelete=ForeignKey.SET_NULL)
],indices=[Index("shopId"),Index(value=["shopId","address"],unique=true),Index(value=["id","shopId"],unique=true),Index("venueId")])
data class ShopBranchEntity(@PrimaryKey(autoGenerate=true)val id:Long=0,val shopId:Long,val address:String,val displayOrder:Int=0,val note:String?=null,val photoUri:String?=null,val venueId:Long?=null)

/** This subset is owned by the product–retailer link, not by the product alone. */
@Entity(tableName="product_branch_availability",primaryKeys=["productId","branchId"],foreignKeys=[
 ForeignKey(entity=ProductShopLinkEntity::class,parentColumns=["productId","shopId"],childColumns=["productId","shopId"],onDelete=ForeignKey.CASCADE),
 ForeignKey(entity=ShopBranchEntity::class,parentColumns=["id","shopId"],childColumns=["branchId","shopId"],onDelete=ForeignKey.CASCADE)
],indices=[Index("productId"),Index("branchId"),Index(value=["productId","shopId"]),Index(value=["branchId","shopId"])])
data class ProductBranchAvailabilityEntity(val productId:Long,val branchId:Long,val shopId:Long=0)
