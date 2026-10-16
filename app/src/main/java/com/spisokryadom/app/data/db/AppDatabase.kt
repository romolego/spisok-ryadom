package com.spisokryadom.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.spisokryadom.app.data.dao.CityDao
import com.spisokryadom.app.data.entity.CityEntity
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
        UnitEntity::class,
        CityEntity::class
    ],
    version = 8,
    exportSchema = true
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
    abstract fun cityDao(): CityDao

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

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS cities (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)")
                db.execSQL("INSERT OR IGNORE INTO cities (id,name) VALUES(1,'Мой город')")
                db.execSQL("ALTER TABLE shops ADD COLUMN cityId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_shops_cityId ON shops(cityId)")
                db.execSQL("ALTER TABLE products ADD COLUMN stableId TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE products SET stableId = lower(hex(randomblob(16))) WHERE stableId = ''")
                db.execSQL("CREATE UNIQUE INDEX index_products_stableId ON products(stableId)")
                db.execSQL("UPDATE product_shop_links SET departmentId=NULL WHERE departmentId IS NOT NULL AND NOT EXISTS(SELECT 1 FROM shop_departments d WHERE d.id=departmentId AND d.shopId=product_shop_links.shopId)")
                db.execSQL("UPDATE shopping_list_entries SET assignedDepartmentId=NULL WHERE assignedDepartmentId IS NOT NULL AND NOT EXISTS(SELECT 1 FROM shop_departments d WHERE d.id=assignedDepartmentId AND d.shopId=shopping_list_entries.assignedShopId)")
                db.execSQL("DELETE FROM product_shop_links WHERE id NOT IN (SELECT MIN(id) FROM product_shop_links GROUP BY productId,shopId)")
                db.execSQL("CREATE UNIQUE INDEX index_product_shop_links_productId_shopId ON product_shop_links(productId,shopId)")
                db.execSQL("DELETE FROM products WHERE isTemporary=1 AND id NOT IN (SELECT productId FROM shopping_list_entries)")
                db.execSQL("UPDATE shopping_list_entries SET quantity=1 WHERE quantity<0.000001 OR quantity>1000000000")
                db.execSQL("UPDATE products SET defaultQuantity=NULL WHERE defaultQuantity<0.000001 OR defaultQuantity>1000000000")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "spisok_ryadom.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL("INSERT OR IGNORE INTO cities(id,name) VALUES(1,'Мой город')")
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
