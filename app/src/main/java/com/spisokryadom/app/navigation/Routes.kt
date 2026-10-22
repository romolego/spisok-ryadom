package com.spisokryadom.app.navigation

sealed class Routes(val route: String) {
    data object ShoppingList : Routes("shopping_list")
    data object ProductDatabase : Routes("product_database")
    data object ShopList : Routes("shop_list")
    data object ProductCard : Routes("product_card/{productId}?preselectedShopId={preselectedShopId}&initialName={initialName}") {
        fun createRoute(productId: Long = -1L, preselectedShopId: Long? = null, initialName: String = ""): String {
            val base = "product_card/$productId"
            val args = buildList {
                preselectedShopId?.let { add("preselectedShopId=$it") }
                if(initialName.isNotBlank()) add("initialName="+android.net.Uri.encode(initialName.trim()))
            }
            return if(args.isEmpty()) base else base+"?"+args.joinToString("&")
        }
    }
    data object ShopContent : Routes("shop_content/{shopId}") {
        fun createRoute(shopId: Long) = "shop_content/$shopId"
    }
    data object ShopCard : Routes("shop_card/{shopId}") {
        fun createRoute(shopId: Long = -1L) = "shop_card/$shopId"
    }
    data object Settings : Routes("settings")
    data object AddToList : Routes("add_to_list?preselectedShopId={preselectedShopId}&preselectedBranchId={preselectedBranchId}&listId={listId}") {
        fun createRoute(preselectedShopId:Long?=null,preselectedBranchId:Long?=null,listId:Long?=null):String =
            "add_to_list?preselectedShopId=${preselectedShopId ?: -1L}&preselectedBranchId=${preselectedBranchId ?: -1L}&listId=${listId ?: -1L}"
    }
}
