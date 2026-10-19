package com.spisokryadom.app.data.entity

/** Read model: the name belongs to the shared type, never to a local placement. */
data class ShopDepartmentEntity(val id:Long=0,val shopId:Long,val name:String,val displayOrder:Int=0,val typeId:Long=0)
