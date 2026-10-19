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
    private val widgetPreferencesListener=android.content.SharedPreferences.OnSharedPreferenceChangeListener{_,key->
        val widgetKeys=setOf(com.spisokryadom.app.widget.WidgetSettings.MODE,com.spisokryadom.app.widget.WidgetSettings.DEPARTMENTS,com.spisokryadom.app.widget.WidgetSettings.TEXT_SIZE)
        if(key==null||key in widgetKeys||key=="global_default_boost_urgent_shop"||key=="active_city"){
            com.spisokryadom.app.widget.ShoppingWidgetProvider.refresh(this,
                updateLayout=key==null||key==com.spisokryadom.app.widget.WidgetSettings.TEXT_SIZE)
        }
    }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        getSharedPreferences(com.spisokryadom.app.widget.WidgetSettings.PREFS,MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(widgetPreferencesListener)
        getSharedPreferences("location_prefs",MODE_PRIVATE).registerOnSharedPreferenceChangeListener(widgetPreferencesListener)
        applicationScope.launch {
            container.database.invalidationTracker.addObserver(object:androidx.room.InvalidationTracker.Observer(
                "goal_lists","shopping_list_entries","products","shops","shop_branches","venues","cities",
                "product_groups","recipients","shop_departments","department_types","branch_department_order") {
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
