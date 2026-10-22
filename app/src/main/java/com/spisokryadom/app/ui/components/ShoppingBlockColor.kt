package com.spisokryadom.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/** Soft tint keeps text/urgent and bought cards legible in both application themes. */
fun shoppingBlockColor(key:String,surface:Color):Color?=accentColor(key)?.copy(alpha=.16f)?.compositeOver(surface)

/** A stronger version of the same tint separates cards from their surrounding shop zone. */
fun purchaseCardColor(block:String,choice:String,surface:Color):Color {
 val key=if(choice==com.spisokryadom.app.util.PurchaseBackground.AUTO)block else choice
 return accentColor(key)?.copy(alpha=.28f)?.compositeOver(surface)?:surface
}
