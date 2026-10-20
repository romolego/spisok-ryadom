package com.spisokryadom.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/** Soft tint keeps text/urgent and bought cards legible in both application themes. */
fun shoppingBlockColor(key:String,surface:Color):Color?=accentColor(key)?.copy(alpha=.16f)?.compositeOver(surface)
