package com.spisokryadom.app.data.entity
import androidx.room.*

/** Product-specific outlet order, independent of availability and the retailer's common order. */
@Entity(tableName="product_branch_priorities",primaryKeys=["productId","branchId"],foreignKeys=[
 ForeignKey(entity=ProductShopLinkEntity::class,parentColumns=["productId","shopId"],childColumns=["productId","shopId"],onDelete=ForeignKey.CASCADE),
 ForeignKey(entity=ShopBranchEntity::class,parentColumns=["id","shopId"],childColumns=["branchId","shopId"],onDelete=ForeignKey.CASCADE)
],indices=[Index(value=["productId","shopId"]),Index(value=["branchId","shopId"])])
data class ProductBranchPriorityEntity(val productId:Long,val branchId:Long,val shopId:Long,val displayOrder:Int)
