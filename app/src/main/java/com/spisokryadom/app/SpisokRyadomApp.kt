package com.spisokryadom.app

import android.app.Application
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SpisokRyadomApp : Application() {
    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        applicationScope.launch {
            container.database.invalidationTracker.addObserver(object:androidx.room.InvalidationTracker.Observer(
                "shopping_list_entries","products","shops","shop_branches","venues") {
                override fun onInvalidated(tables:Set<String>) {
                    com.spisokryadom.app.widget.ShoppingWidgetProvider.refresh(this@SpisokRyadomApp)
                }
            })
        }

        // A new installation starts empty; periodically remove unused temporary data and photos.
        applicationScope.launch {
            com.spisokryadom.app.ui.productcard.ProductDraftStore.recover(this@SpisokRyadomApp,container.productRepository,container.shopRepository)
            runCatching { container.database.withTransaction { container.database.productDao().deleteUnusedTemporary() }
            com.spisokryadom.app.data.repository.MediaLifecycle.cleanup(this@SpisokRyadomApp,container.database) }.onFailure { android.util.Log.w("DataCleanup","Deferred cleanup failed",it) }
        }

    }
}
