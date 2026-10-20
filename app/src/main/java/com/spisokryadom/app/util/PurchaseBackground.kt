package com.spisokryadom.app.util

object PurchaseBackground {
 const val AUTO="auto"
 fun valid(value:String)=value==AUTO||AppearanceColors.validOptional(value)
}
