package com.spisokryadom.app

import android.content.Context
import com.spisokryadom.app.data.repository.LocationRepository
import com.spisokryadom.app.data.backup.BackupManager
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.repository.ClassifierRepository
import com.spisokryadom.app.data.repository.ProductRepository
import com.spisokryadom.app.data.repository.ShopRepository
import com.spisokryadom.app.data.repository.ShoppingListRepository

class AppContainer(context: Context) {
    val database = AppDatabase.getInstance(context)

    val catalogueEdits = com.spisokryadom.app.ui.productcard.ProductDraftSynchronizer(com.spisokryadom.app.ui.productcard.ProductDraftStore.preferences(context))

    val locations = LocationRepository(database.cityDao(), database.shopDao(), context.getSharedPreferences("location_prefs", Context.MODE_PRIVATE))

    val productRepository = ProductRepository(
        database.productDao(),
        database.productShopLinkDao(), database,catalogueEdits
    )

    val shopRepository = ShopRepository(
        database.shopDao(),
        database.shopDepartmentDao(), locations, database,catalogueEdits
    )

    val shoppingListRepository = ShoppingListRepository(
        database.shoppingListEntryDao(), database, locations,catalogueEdits
    )

    val classifierRepository = ClassifierRepository(
        database.productGroupDao(),
        database.recipientDao(),
        database.unitDao(), database,catalogueEdits
    )

    val batchRepository = com.spisokryadom.app.data.repository.BatchRepository(database,productRepository,shoppingListRepository,catalogueEdits)

    val backupManager = BackupManager(context,database)
}
