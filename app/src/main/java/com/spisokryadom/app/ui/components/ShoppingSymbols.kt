package com.spisokryadom.app.ui.components
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.unit.dp

/** Original 24dp shopping symbols; the rest of the pack uses Google Material Icons. */
internal object ShoppingSymbols {
 private fun symbol(name:String,data:String)=ImageVector.Builder(name,24.dp,24.dp,24f,24f).apply{
  addPath(pathData=PathParser().parsePathString(data).toNodes(),fill=SolidColor(Color.Black),pathFillType=PathFillType.EvenOdd)
 }.build()
 val Canned=symbol("Canned","M7 2h10v3H7z M7 6h10v2c2 0 3 1 3 3v9c0 1.1-.9 2-2 2H6c-1.1 0-2-.9-2-2v-9c0-2 1-3 3-3z M6 11v8c0 .6.4 1 1 1h10c.6 0 1-.4 1-1v-8z M8 13h2v5H8z M12 12h2v6h-2z M16 13h1v4h-1z")
 val Cheese=symbol("Cheese","M3 10 19 3l3 7v10H3z M5 12v6h15v-6z M7 14h2v2H7z M12 13h2v2h-2z M16 15h2v2h-2z")
 val Nuts=symbol("Nuts","M12 2c4-1 8 2 7 6-.4 2-2 3-2 4s2 2 2 4c1 4-3 7-7 6-4 1-8-2-7-6 .4-2 2-3 2-4S5 10 5 8c-1-4 3-7 7-6z M11 5v14h2V5z M8 6h1v3H8z M15 15h1v3h-1z")
 val Bottle=symbol("Bottle","M9 2h6v3H9z M9 6h6v3c0 1 3 3 3 5v6c0 1.1-.9 2-2 2H8c-1.1 0-2-.9-2-2v-6c0-2 3-4 3-5z M8 14v4h8v-4z")
 val Tooth=symbol("Tooth","M5 3c3-2 4 1 7 1s4-3 7-1c4 3 0 10-1 14-1 5-4 5-4 1 0-2 0-4-2-4s-2 2-2 4c0 4-3 4-4-1C5 13 1 6 5 3z")
 val Fruit=symbol("Fruit","M12 6C11 1 15 1 18 2c0 3-3 5-5 4v2c3-2 8-1 8 4 0 5-3 10-6 10-2 0-2-1-3-1s-1 1-3 1c-3 0-6-5-6-10 0-5 5-6 8-4V6z")
}
