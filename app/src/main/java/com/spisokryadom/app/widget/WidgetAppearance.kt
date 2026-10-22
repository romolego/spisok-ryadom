package com.spisokryadom.app.widget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.spisokryadom.app.ui.components.purchaseCardColor
import com.spisokryadom.app.ui.components.shoppingBlockColor
import com.spisokryadom.app.ui.shoppinglist.ViewMode
import com.spisokryadom.app.ui.theme.ErrorContainer
import com.spisokryadom.app.ui.theme.SurfaceVariant
import com.spisokryadom.app.util.FavoriteStyle

/** RemoteViews uses the application's light surface and the same tint composition as Main. */
object WidgetAppearance {
    fun shopBackground(item: WidgetItem, settings: WidgetSettings): Int {
        if (!settings.showShopBackground || settings.mode != ViewMode.BY_SHOPS) return Color.Transparent.toArgb()
        val key = when (item) {
            is WidgetItem.Header -> item.blockBackground
            is WidgetItem.Purchase -> item.blockBackground
        }
        return (shoppingBlockColor(key, Color.White) ?: Color.Transparent).toArgb()
    }

    fun productBackground(item: WidgetItem.Purchase, settings: WidgetSettings,
        favoriteStyle: FavoriteStyle = FavoriteStyle(color="#F1CF78")): Int {
        if (!settings.showProductBackground) return Color.White.toArgb()
        val base = purchaseCardColor(item.blockBackground, item.purchaseBackground, Color.White)
        return when {
            item.row.bought -> SurfaceVariant
            item.row.urgent -> ErrorContainer
            item.isFavorite && favoriteStyle.background -> shoppingBlockColor(favoriteStyle.color, base) ?: base
            else -> base
        }.toArgb()
    }
}
