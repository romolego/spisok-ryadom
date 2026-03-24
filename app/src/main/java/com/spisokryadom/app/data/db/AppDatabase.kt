package com.spisokryadom.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.spisokryadom.app.data.dao.ProductDao
import com.spisokryadom.app.data.dao.ProductGroupDao
import com.spisokryadom.app.data.dao.ProductShopLinkDao
import com.spisokryadom.app.data.dao.RecipientDao
import com.spisokryadom.app.data.dao.ShopDao
import com.spisokryadom.app.data.dao.ShopDepartmentDao
import com.spisokryadom.app.data.dao.ShoppingListEntryDao
import com.spisokryadom.app.data.entity.ProductEntity
import com.spisokryadom.app.data.entity.ProductGroupEntity
import com.spisokryadom.app.data.entity.ProductShopLinkEntity
import com.spisokryadom.app.data.entity.RecipientEntity
import com.spisokryadom.app.data.entity.ShopDepartmentEntity
import com.spisokryadom.app.data.entity.ShopEntity
import com.spisokryadom.app.data.entity.ShoppingListEntryEntity

@Database(
    entities = [
        ProductEntity::class,
        ProductGroupEntity::class,
        RecipientEntity::class,
        ShopEntity::class,
        ShopDepartmentEntity::class,
        ProductShopLinkEntity::class,
        ShoppingListEntryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun productGroupDao(): ProductGroupDao
    abstract fun recipientDao(): RecipientDao
    abstract fun shopDao(): ShopDao
    abstract fun shopDepartmentDao(): ShopDepartmentDao
    abstract fun productShopLinkDao(): ProductShopLinkDao
    abstract fun shoppingListEntryDao(): ShoppingListEntryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "spisok_ryadom.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
