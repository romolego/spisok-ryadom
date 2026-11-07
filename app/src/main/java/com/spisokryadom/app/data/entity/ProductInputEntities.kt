package com.spisokryadom.app.data.entity

import androidx.room.*

/** One persisted policy; foreign keys also apply when classifiers are deleted elsewhere. */
@Entity(tableName="product_input_settings", foreignKeys=[ForeignKey(entity=RecipientEntity::class,
    parentColumns=["id"],childColumns=["recipientId"],onDelete=ForeignKey.SET_NULL)],indices=[Index("recipientId")])
data class ProductInputSettings(
    @PrimaryKey val id:Int=1,
    val prefillEnabled:Boolean=true,
    val prefillCategory:Boolean=true,
    val prefillUnit:Boolean=true,
    val prefillDepartment:Boolean=true,
    val inheritDepartment:Boolean=true,
    val recipientEnabled:Boolean=false,
    val recipientId:Long?=null
)

/** A present row with null type means an intentional empty default. */
@Entity(tableName="product_default_departments",foreignKeys=[
    ForeignKey(entity=ProductEntity::class,parentColumns=["id"],childColumns=["productId"],onDelete=ForeignKey.CASCADE),
    ForeignKey(entity=DepartmentTypeEntity::class,parentColumns=["id"],childColumns=["typeId"],onDelete=ForeignKey.SET_NULL)
],indices=[Index("typeId")])
data class ProductDefaultDepartment(@PrimaryKey val productId:Long,val typeId:Long?)

/** Commit receipt makes resubmitting a restored preview harmless. */
@Entity(tableName="bulk_purchase_runs")
data class BulkPurchaseRun(@PrimaryKey val token:String,val createdAt:Long)
