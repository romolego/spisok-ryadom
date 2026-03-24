package com.spisokryadom.app

import android.app.Application
import com.spisokryadom.app.data.DemoDataProvider
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

        // Заполнение демо-данными при первом запуске.
        // Чтобы отключить: закомментировать этот блок или вызвать DemoDataProvider.resetDemoFlag(context).
        applicationScope.launch {
            DemoDataProvider.populateIfNeeded(this@SpisokRyadomApp, container.database)
        }
    }
}
