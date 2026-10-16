package com.spisokryadom.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.spisokryadom.app.data.dao.ProductDao
import com.spisokryadom.app.data.dao.ProductGroupDao
import com.spisokryadom.app.data.dao.ProductShopLinkDao
import com.spisokryadom.app.data.dao.RecipientDao
import com.spisokryadom.app.data.dao.ShopDao
import com.spisokryadom.app.data.dao.ShopDepartmentDao
import com.spisokryadom.app.data.dao.ShoppingListEntryDao
import com.spisokryadom.app.data.dao.UnitDao
import com.spisokryadom.app.data.entity.ProductEntity
import com.spisokryadom.app.data.entity.ProductGroupEntity
import com.spisokryadom.app.data.entity.ProductShopLinkEntity
import com.spisokryadom.app.data.entity.RecipientEntity
import com.spisokryadom.app.data.entity.ShopDepartmentEntity
import com.spisokryadom.app.data.entity.ShopEntity
import com.spisokryadom.app.data.entity.ShoppingListEntryEntity
import com.spisokryadom.app.data.entity.UnitEntity

@Database(
    entities = [
        ProductEntity::class,
        ProductGroupEntity::class,
        RecipientEntity::class,
        ShopEntity::class,
        ShopDepartmentEntity::class,
        ProductShopLinkEntity::class,
        ShoppingListEntryEntity::class,
        UnitEntity::class
    ],
    version = 7,
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
    abstract fun unitDao(): UnitDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN priceValue REAL")
                db.execSQL("ALTER TABLE products ADD COLUMN priceDate TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shops ADD COLUMN showUrgentInCollapsed INTEGER")
                db.execSQL("ALTER TABLE shops ADD COLUMN boostUrgentShop INTEGER")
                db.execSQL("ALTER TABLE shops ADD COLUMN manualOrderOverride INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE product_groups ADD COLUMN displayOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE recipients ADD COLUMN displayOrder INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shopping_list_entries ADD COLUMN comment TEXT")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN isTemporary INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `units` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`displayOrder` INTEGER NOT NULL DEFAULT 0)"
                )
                // Seed units table from existing distinct defaultUnit values in products
                db.execSQL(
                    "INSERT INTO `units` (`name`, `displayOrder`) " +
                            "SELECT DISTINCT defaultUnit, " +
                            "(SELECT COUNT(*) FROM products p2 WHERE p2.defaultUnit < products.defaultUnit " +
                            "AND p2.defaultUnit IS NOT NULL AND p2.defaultUnit != '') " +
                            "FROM products " +
                            "WHERE defaultUnit IS NOT NULL AND defaultUnit != '' " +
                            "ORDER BY defaultUnit ASC"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "spisok_ryadom.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
