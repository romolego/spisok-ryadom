package com.spisokryadom.app.util

import android.content.SharedPreferences

data class ProductCreationDefaults(val purchaseType:String="offline",val missingUnit:String="шт",val missingQuantity:Double=1.0,val fillMissing:Boolean=true) {
 companion object {
  const val TYPE="new_product_purchase_type"
  const val QUANTITY="new_product_missing_quantity"
  const val UNIT="new_product_missing_unit"
  const val ENABLED="new_product_fill_missing"
  fun read(prefs:SharedPreferences)=ProductCreationDefaults(prefs.getString(TYPE,"offline").orEmpty().takeIf{it in setOf("online","offline")}?:"offline",Quantities.unit(prefs.getString(UNIT,"шт").orEmpty()),Quantities.parse(prefs.getString(QUANTITY,"1").orEmpty())?:1.0,prefs.getBoolean(ENABLED,prefs.contains(UNIT)||prefs.contains(QUANTITY)))
 }
}

object PurchaseModes {
 val labels=linkedMapOf("inherit" to "По общей настройке","offline" to "Только офлайн","online" to "Только онлайн","both" to "Онлайн и офлайн")
 fun allowed(mode:String)=when(mode){"online"->setOf("online");"offline"->setOf("offline");else->setOf("offline","online")}
 fun resolve(mode:String,global:String)=if(mode in setOf("online","offline"))mode else global
}
