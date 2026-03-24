package com.spisokryadom.app

import android.content.Context
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.repository.ClassifierRepository
import com.spisokryadom.app.data.repository.ProductRepository
import com.spisokryadom.app.data.repository.ShopRepository
import com.spisokryadom.app.data.repository.ShoppingListRepository

class AppContainer(context: Context) {
    val database = AppDatabase.getInstance(context)

    val productRepository = ProductRepository(
        database.productDao(),
        database.productShopLinkDao()
    )

    val shopRepository = ShopRepository(
        database.shopDao(),
        database.shopDepartmentDao()
    )

    val shoppingListRepository = ShoppingListRepository(
        database.shoppingListEntryDao()
    )

    val classifierRepository = ClassifierRepository(
        database.productGroupDao(),
        database.recipientDao()
    )
}
