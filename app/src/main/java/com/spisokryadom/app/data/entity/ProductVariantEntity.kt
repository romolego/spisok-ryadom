package com.spisokryadom.app.data.entity

import androidx.room.*

/** Each purchasable variant retains the ordinary product/link/purchase graph. */
@Entity(tableName="product_variants",foreignKeys=[
 ForeignKey(entity=ProductEntity::class,parentColumns=["id"],childColumns=["productId"],onDelete=ForeignKey.CASCADE),
 ForeignKey(entity=ProductEntity::class,parentColumns=["id"],childColumns=["familyId"],onDelete=ForeignKey.CASCADE)
],indices=[Index("familyId")])
data class ProductVariantEntity(@PrimaryKey val productId:Long,val familyId:Long,val label:String,val displayOrder:Int=0)

@Entity(tableName="product_variant_cities",primaryKeys=["productId","cityId"],foreignKeys=[
 ForeignKey(entity=ProductVariantEntity::class,parentColumns=["productId"],childColumns=["productId"],onDelete=ForeignKey.CASCADE),
 ForeignKey(entity=CityEntity::class,parentColumns=["id"],childColumns=["cityId"],onDelete=ForeignKey.CASCADE)
],indices=[Index("cityId")])
data class ProductVariantCityEntity(val productId:Long,val cityId:Long,val enabled:Boolean=true,val displayOrder:Int=0)

@Entity(tableName="product_photos",primaryKeys=["productId","position"],foreignKeys=[
 ForeignKey(entity=ProductEntity::class,parentColumns=["id"],childColumns=["productId"],onDelete=ForeignKey.CASCADE)
])
data class ProductPhotoEntity(val productId:Long,val position:Int,val uri:String,val caption:String="")
