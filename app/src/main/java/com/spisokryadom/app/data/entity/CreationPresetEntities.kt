package com.spisokryadom.app.data.entity

import androidx.room.*

@Entity(tableName="shop_creation_presets",foreignKeys=[ForeignKey(entity=ShopEntity::class,parentColumns=["id"],childColumns=["shopId"],onDelete=ForeignKey.CASCADE)])
data class ShopCreationPreset(@PrimaryKey val shopId:Long,val purchaseMode:String)

@Entity(tableName="group_creation_presets",foreignKeys=[
 ForeignKey(entity=ProductGroupEntity::class,parentColumns=["id"],childColumns=["groupId"],onDelete=ForeignKey.CASCADE),
 ForeignKey(entity=RecipientEntity::class,parentColumns=["id"],childColumns=["recipientId"],onDelete=ForeignKey.SET_NULL)
],indices=[Index("recipientId")])
data class GroupCreationPreset(@PrimaryKey val groupId:Long,val recipientId:Long?)
