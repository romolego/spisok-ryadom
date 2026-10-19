package com.spisokryadom.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.dp
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.spisokryadom.app.ui.addtolist.AddToListScreen
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
fun AppNavGraph(widgetRequest:String?=null,onWidgetRequestHandled:()->Unit={}) {
    val navController = rememberNavController()
    androidx.compose.runtime.LaunchedEffect(widgetRequest) {
        if(widgetRequest!=null){
            if(widgetRequest!="add"||navController.currentDestination?.route!=Routes.AddToList.route) {
                navController.popBackStack(Routes.ShoppingList.route,false)
                if(widgetRequest=="add")navController.navigate(Routes.AddToList.createRoute()){launchSingleTop=true}
            }
            onWidgetRequestHandled()
        }
    }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = bottomNavItems.any { item ->
        currentDestination?.hierarchy?.any { it.route == item.route } == true
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
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
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)
        ) {
            composable(Routes.ShoppingList.route) {
                ShoppingListScreen(
                    onNavigateToAddToList = {
                        navController.navigate(Routes.AddToList.createRoute())
                    },
                    onNavigateToAddToShop = { shopId,branchId ->
                        navController.navigate(Routes.AddToList.createRoute(preselectedShopId = shopId,preselectedBranchId=branchId))
                    },
                    onNavigateToProductCard = { productId ->
                        navController.navigate(Routes.ProductCard.createRoute(productId))
                    },
                    onNavigateToBulk = { navController.navigate("bulk") },
                    onNavigateToSettings = {
                        navController.navigate(Routes.Settings.route)
                    }
                )
            }

            composable(Routes.ProductDatabase.route) { entry ->
                val focus by entry.savedStateHandle.getStateFlow("focusProductId",-1L).collectAsStateWithLifecycle()
                ProductDatabaseScreen(focusProductId=focus,onFocusHandled={entry.savedStateHandle["focusProductId"]=-1L},
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
                val focus by backStackEntry.savedStateHandle.getStateFlow("focusProductId",-1L).collectAsStateWithLifecycle()
                ShopContentScreen(focusProductId=focus,onFocusHandled={backStackEntry.savedStateHandle["focusProductId"]=-1L},
                    shopId = shopId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToProductCard = { productId ->
                        navController.navigate(Routes.ProductCard.createRoute(productId))
                    },
                    onCreateNewProduct = {
                        navController.navigate(Routes.ProductCard.createRoute(-1L, preselectedShopId = shopId))
                    }
                )
            }

            composable(
                route = Routes.ProductCard.route,
                arguments = listOf(
                    navArgument("productId") { type = NavType.LongType },
                    navArgument("preselectedShopId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { backStackEntry ->
                val productId = backStackEntry.arguments?.getLong("productId") ?: -1L
                val preselectedShopId = backStackEntry.arguments?.getLong("preselectedShopId") ?: -1L
                ProductCardScreen(
                    productId = productId,
                    preselectedShopId = preselectedShopId,
                    onUseExistingProduct={id->navController.popBackStack();navController.navigate(Routes.ProductCard.createRoute(id))},
                    onProductSaved={id->navController.previousBackStackEntry?.savedStateHandle?.set("focusProductId",id);navController.popBackStack()},
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

            composable(
                route = Routes.AddToList.route,
                arguments = listOf(
                    navArgument("preselectedShopId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                    navArgument("preselectedBranchId") { type=NavType.LongType;defaultValue = -1L }
                )
            ) { backStackEntry ->
                val preselectedShopId = backStackEntry.arguments?.getLong("preselectedShopId") ?: -1L
                val branchId=backStackEntry.arguments?.getLong("preselectedBranchId") ?: -1L
                AddToListScreen(
                    preselectedBranchId=branchId.takeIf{it>=0},
                    preselectedShopId = if (preselectedShopId > 0) preselectedShopId else null,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable("bulk") { com.spisokryadom.app.ui.bulk.BulkScreen(onBack={navController.popBackStack()}) }

            composable(Routes.Settings.route) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
