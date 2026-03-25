package com.spisokryadom.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.spisokryadom.app.ui.productcard.ProductCardScreen
import com.spisokryadom.app.ui.productdb.ProductDatabaseScreen
import com.spisokryadom.app.ui.settings.SettingsScreen
import com.spisokryadom.app.ui.shopcard.ShopCardScreen
import com.spisokryadom.app.ui.shopcontent.ShopContentScreen
import com.spisokryadom.app.ui.shoplist.ShopListScreen
import com.spisokryadom.app.ui.shoppinglist.ShoppingListScreen

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem(Routes.ShoppingList.route, "Список", Icons.AutoMirrored.Filled.FormatListBulleted),
    BottomNavItem(Routes.ProductDatabase.route, "Товары", Icons.Filled.Inventory2),
    BottomNavItem(Routes.ShopList.route, "Магазины", Icons.Filled.Store)
)

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = bottomNavItems.any { item ->
        currentDestination?.hierarchy?.any { it.route == item.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == item.route
                        } == true
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.ShoppingList.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.ShoppingList.route) {
                ShoppingListScreen(
                    onNavigateToProducts = {
                        navController.navigate(Routes.ProductDatabase.route)
                    },
                    onNavigateToProductCard = { productId ->
                        navController.navigate(Routes.ProductCard.createRoute(productId))
                    },
                    onNavigateToSettings = {
                        navController.navigate(Routes.Settings.route)
                    }
                )
            }

            composable(Routes.ProductDatabase.route) {
                ProductDatabaseScreen(
                    onNavigateToProductCard = { productId ->
                        navController.navigate(Routes.ProductCard.createRoute(productId))
                    },
                    onCreateNewProduct = {
                        navController.navigate(Routes.ProductCard.createRoute(-1L))
                    }
                )
            }

            composable(Routes.ShopList.route) {
                ShopListScreen(
                    onNavigateToShopContent = { shopId ->
                        navController.navigate(Routes.ShopContent.createRoute(shopId))
                    },
                    onNavigateToShopCard = { shopId ->
                        navController.navigate(Routes.ShopCard.createRoute(shopId))
                    },
                    onCreateNewShop = {
                        navController.navigate(Routes.ShopCard.createRoute(-1L))
                    }
                )
            }

            composable(
                route = Routes.ShopContent.route,
                arguments = listOf(navArgument("shopId") { type = NavType.LongType })
            ) { backStackEntry ->
                val shopId = backStackEntry.arguments?.getLong("shopId") ?: -1L
                ShopContentScreen(
                    shopId = shopId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToProductCard = { productId ->
                        navController.navigate(Routes.ProductCard.createRoute(productId))
                    }
                )
            }

            composable(
                route = Routes.ProductCard.route,
                arguments = listOf(navArgument("productId") { type = NavType.LongType })
            ) { backStackEntry ->
                val productId = backStackEntry.arguments?.getLong("productId") ?: -1L
                ProductCardScreen(
                    productId = productId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.ShopCard.route,
                arguments = listOf(navArgument("shopId") { type = NavType.LongType })
            ) { backStackEntry ->
                val shopId = backStackEntry.arguments?.getLong("shopId") ?: -1L
                ShopCardScreen(
                    shopId = shopId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Routes.Settings.route) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
