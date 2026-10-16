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

        // A new installation starts empty; periodically remove unused temporary data and photos.
        applicationScope.launch {
            runCatching { container.database.withTransaction { container.database.productDao().deleteUnusedTemporary() }
            com.spisokryadom.app.util.PhotoStore.cleanup(this@SpisokRyadomApp,container.database.productDao().getAllSync().mapNotNull{it.photoUri}.toSet()) }.onFailure { android.util.Log.w("DataCleanup","Deferred cleanup failed",it) }
        }

    }
}
