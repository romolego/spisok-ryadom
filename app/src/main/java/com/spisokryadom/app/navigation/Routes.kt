package com.spisokryadom.app.navigation

sealed class Routes(val route: String) {
    data object ShoppingList : Routes("shopping_list")
    data object ProductDatabase : Routes("product_database")
    data object ShopList : Routes("shop_list")
    data object ProductCard : Routes("product_card/{productId}") {
        fun createRoute(productId: Long = -1L) = "product_card/$productId"
    }
    data object ShopCard : Routes("shop_card/{shopId}") {
        fun createRoute(shopId: Long = -1L) = "shop_card/$shopId"
    }
}
