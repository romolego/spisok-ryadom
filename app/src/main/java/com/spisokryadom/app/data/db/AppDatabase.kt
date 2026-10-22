package com.spisokryadom.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.spisokryadom.app.data.entity.ShopBranchEntity
import com.spisokryadom.app.data.entity.ProductBranchAvailabilityEntity
import com.spisokryadom.app.data.dao.ShopBranchDao
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
        com.spisokryadom.app.data.entity.ShopCreationPreset::class,
        com.spisokryadom.app.data.entity.GroupCreationPreset::class,
        ProductEntity::class,
        ProductGroupEntity::class,
        RecipientEntity::class,
        ShopEntity::class,
        com.spisokryadom.app.data.entity.ShopDepartmentPlacementEntity::class,
        com.spisokryadom.app.data.entity.DepartmentTypeEntity::class,
        com.spisokryadom.app.data.entity.BranchDepartmentOrderEntity::class,
        com.spisokryadom.app.data.entity.ProductBranchDepartmentEntity::class,
        com.spisokryadom.app.data.entity.RetailNetworkEntity::class,
        com.spisokryadom.app.data.entity.VenueEntity::class,
        ProductShopLinkEntity::class,
        ShoppingListEntryEntity::class,
        com.spisokryadom.app.data.entity.GoalListEntity::class,
        UnitEntity::class,
        ShopBranchEntity::class, ProductBranchAvailabilityEntity::class, com.spisokryadom.app.data.entity.ProductBranchPriorityEntity::class,
        CityEntity::class
    ],
    version = 22,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun creationPresetDao():com.spisokryadom.app.data.dao.CreationPresetDao
    abstract fun productDao(): ProductDao
    abstract fun productGroupDao(): ProductGroupDao
    abstract fun recipientDao(): RecipientDao
    abstract fun shopDao(): ShopDao
    abstract fun shopDepartmentDao(): ShopDepartmentDao
    abstract fun productShopLinkDao(): ProductShopLinkDao
    abstract fun shoppingListEntryDao(): ShoppingListEntryDao
    abstract fun goalListDao(): com.spisokryadom.app.data.dao.GoalListDao
    abstract fun unitDao(): UnitDao
    abstract fun shopBranchDao(): ShopBranchDao
    abstract fun cityDao(): CityDao
    abstract fun retailStructureDao(): com.spisokryadom.app.data.dao.RetailStructureDao

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

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db:SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS shop_branches (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, shopId INTEGER NOT NULL, address TEXT NOT NULL, displayOrder INTEGER NOT NULL, FOREIGN KEY(shopId) REFERENCES shops(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX index_shop_branches_shopId ON shop_branches(shopId)")
                db.execSQL("CREATE UNIQUE INDEX index_shop_branches_shopId_address ON shop_branches(shopId,address)")
                db.execSQL("INSERT INTO shop_branches(id,shopId,address,displayOrder) SELECT id,id,trim(address),0 FROM shops WHERE address IS NOT NULL AND trim(address)!=''")
                db.execSQL("ALTER TABLE product_shop_links ADD COLUMN restrictBranches INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS product_branch_availability (productId INTEGER NOT NULL, branchId INTEGER NOT NULL, PRIMARY KEY(productId,branchId), FOREIGN KEY(productId) REFERENCES products(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(branchId) REFERENCES shop_branches(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX index_product_branch_availability_productId ON product_branch_availability(productId)")
                db.execSQL("CREATE INDEX index_product_branch_availability_branchId ON product_branch_availability(branchId)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `shopping_list_entries_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `productId` INTEGER NOT NULL, `quantity` REAL NOT NULL, `unit` TEXT NOT NULL, `assignedShopId` INTEGER, `assignedDepartmentId` INTEGER, `isBought` INTEGER NOT NULL, `isUrgent` INTEGER NOT NULL, `note` TEXT, `comment` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `assignedBranchId` INTEGER, FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`assignedShopId`) REFERENCES `shops`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`assignedDepartmentId`) REFERENCES `shop_departments`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`assignedBranchId`) REFERENCES `shop_branches`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )""")
                db.execSQL("INSERT INTO shopping_list_entries_new(`id`,`productId`,`quantity`,`unit`,`assignedShopId`,`assignedDepartmentId`,`isBought`,`isUrgent`,`note`,`comment`,`createdAt`,`updatedAt`,assignedBranchId) SELECT `id`,`productId`,`quantity`,`unit`,`assignedShopId`,`assignedDepartmentId`,`isBought`,`isUrgent`,`note`,`comment`,`createdAt`,`updatedAt`,(SELECT id FROM shop_branches WHERE shopId=shopping_list_entries.assignedShopId ORDER BY displayOrder,id LIMIT 1) FROM shopping_list_entries")
                db.execSQL("DROP TABLE shopping_list_entries")
                db.execSQL("ALTER TABLE shopping_list_entries_new RENAME TO shopping_list_entries")
                listOf("productId","assignedShopId","assignedDepartmentId","assignedBranchId").forEach { db.execSQL("CREATE INDEX index_shopping_list_entries_$it ON shopping_list_entries($it)") }
            }
        }

        val MIGRATION_9_10 = object : Migration(9,10) {
            override fun migrate(db:SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shop_branches ADD COLUMN note TEXT")
                db.execSQL("ALTER TABLE shop_branches ADD COLUMN photoUri TEXT")
            }
        }

        val MIGRATION_11_12 = object : Migration(11,12) {
            override fun migrate(db:SupportSQLiteDatabase) {
                for(table in listOf("product_groups","recipients","department_types")) {
                    db.execSQL("ALTER TABLE $table ADD COLUMN accent TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE $table ADD COLUMN iconKey TEXT NOT NULL DEFAULT ''")
                }
                db.execSQL("ALTER TABLE shopping_list_entries ADD COLUMN awaitingShopCityId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE shopping_list_entries ADD COLUMN explicitNoShop INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE products ADD COLUMN accent TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE products ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE department_types ADD COLUMN displayOrder INTEGER NOT NULL DEFAULT 0")
                // Repair pending catalogue projections from older versions. Historical
                // bought rows and explicit nonempty units are never rewritten.
                db.execSQL("UPDATE shopping_list_entries SET unit=COALESCE((SELECT defaultUnit FROM products p WHERE p.id=shopping_list_entries.productId),'') WHERE isBought=0 AND TRIM(unit)=''")
                db.execSQL("UPDATE shopping_list_entries SET assignedDepartmentId=COALESCE((SELECT d.id FROM product_branch_departments pd JOIN shop_departments d ON d.shopId=pd.shopId AND d.typeId=pd.typeId WHERE pd.productId=shopping_list_entries.productId AND pd.branchId=shopping_list_entries.assignedBranchId LIMIT 1),(SELECT l.departmentId FROM product_shop_links l WHERE l.productId=shopping_list_entries.productId AND l.shopId=shopping_list_entries.assignedShopId)) WHERE isBought=0 AND EXISTS(SELECT 1 FROM product_shop_links l WHERE l.productId=shopping_list_entries.productId AND l.shopId=shopping_list_entries.assignedShopId)")
                // Preserve the previous alphabetic order, including equal names.
                db.execSQL("UPDATE department_types SET displayOrder=(SELECT COUNT(*) FROM department_types t WHERE t.name<department_types.name OR (t.name=department_types.name AND t.id<department_types.id))")
            }
        }
        val MIGRATION_12_13 = object : Migration(12,13) {
            override fun migrate(db:SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS product_branch_priorities (productId INTEGER NOT NULL, branchId INTEGER NOT NULL, shopId INTEGER NOT NULL, displayOrder INTEGER NOT NULL, PRIMARY KEY(productId,branchId), FOREIGN KEY(productId,shopId) REFERENCES product_shop_links(productId,shopId) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(branchId,shopId) REFERENCES shop_branches(id,shopId) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX index_product_branch_priorities_productId_shopId ON product_branch_priorities(productId,shopId)")
                db.execSQL("CREATE INDEX index_product_branch_priorities_branchId_shopId ON product_branch_priorities(branchId,shopId)")
            }
        }
        val MIGRATION_10_11 = RetailMigration
        val MIGRATION_13_14 = object:Migration(13,14){
            override fun migrate(db:SupportSQLiteDatabase){db.execSQL("ALTER TABLE shops ADD COLUMN shoppingBackground TEXT NOT NULL DEFAULT ''")}
        }
        val MIGRATION_14_15 = object:Migration(14,15){
            override fun migrate(db:SupportSQLiteDatabase){
                db.execSQL("CREATE TABLE IF NOT EXISTS goal_lists (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, stableId TEXT NOT NULL, name TEXT NOT NULL, showInPurchases INTEGER NOT NULL DEFAULT 0, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX index_goal_lists_stableId ON goal_lists(stableId)")
                // Nullable reference can be added without rebuilding purchase rows or changing IDs.
                db.execSQL("ALTER TABLE shopping_list_entries ADD COLUMN listId INTEGER DEFAULT NULL REFERENCES goal_lists(id) ON DELETE CASCADE")
                db.execSQL("CREATE INDEX index_shopping_list_entries_listId ON shopping_list_entries(listId)")
                db.execSQL("ALTER TABLE shopping_list_entries ADD COLUMN plannedUnitPrice REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE shopping_list_entries ADD COLUMN customShopName TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE shopping_list_entries ADD COLUMN customShopAddress TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_15_16=object:Migration(15,16){override fun migrate(db:SupportSQLiteDatabase){
            db.execSQL("CREATE TABLE IF NOT EXISTS shop_creation_presets (shopId INTEGER NOT NULL, purchaseMode TEXT NOT NULL, PRIMARY KEY(shopId), FOREIGN KEY(shopId) REFERENCES shops(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE TABLE IF NOT EXISTS group_creation_presets (groupId INTEGER NOT NULL, recipientId INTEGER, PRIMARY KEY(groupId), FOREIGN KEY(groupId) REFERENCES product_groups(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(recipientId) REFERENCES recipients(id) ON UPDATE NO ACTION ON DELETE SET NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_group_creation_presets_recipientId ON group_creation_presets(recipientId)")
            StandardCategories.install(db)
        }}
        val MIGRATION_16_17=object:Migration(16,17){override fun migrate(db:SupportSQLiteDatabase){
            db.execSQL("ALTER TABLE shops ADD COLUMN iconKey TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE shops ADD COLUMN logoUri TEXT DEFAULT NULL")
        }}
        val MIGRATION_17_18=object:Migration(17,18){override fun migrate(db:SupportSQLiteDatabase){
            listOf("product_groups","department_types").forEach { table ->
                db.execSQL("ALTER TABLE $table ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE $table ADD COLUMN favoriteOrder INTEGER NOT NULL DEFAULT 0")
            }
        }}
        val MIGRATION_18_19=object:Migration(18,19){override fun migrate(db:SupportSQLiteDatabase){
            db.execSQL("ALTER TABLE shops ADD COLUMN purchaseBackground TEXT NOT NULL DEFAULT ''")
        }}
        val MIGRATION_19_20=object:Migration(19,20){override fun migrate(db:SupportSQLiteDatabase){
            db.execSQL("ALTER TABLE venues ADD COLUMN photoUri TEXT")
            db.execSQL("ALTER TABLE shop_branches ADD COLUMN label TEXT NOT NULL DEFAULT ''")
            db.execSQL("DROP INDEX IF EXISTS index_shop_branches_shopId_address")
            // Legacy venue-point addresses were either a copy of the common address
            // or an outlet description. Keep the latter as a label; never invent an address.
            val points=mutableListOf<com.spisokryadom.app.data.entity.ShopBranchEntity>()
            val venues=mutableListOf<com.spisokryadom.app.data.entity.VenueEntity>()
            val names=mutableMapOf<Long,String>()
            db.query("SELECT id,shopId,address,displayOrder,venueId FROM shop_branches").use{c->while(c.moveToNext())points.add(com.spisokryadom.app.data.entity.ShopBranchEntity(c.getLong(0),c.getLong(1),c.getString(2),c.getInt(3),venueId=if(c.isNull(4))null else c.getLong(4)))}
            db.query("SELECT id,cityId,name,address FROM venues").use{c->while(c.moveToNext())venues.add(com.spisokryadom.app.data.entity.VenueEntity(c.getLong(0),c.getLong(1),c.getString(2),if(c.isNull(3))null else c.getString(3)))}
            db.query("SELECT id,name FROM shops").use{c->while(c.moveToNext())names[c.getLong(0)]=c.getString(1)}
            com.spisokryadom.app.domain.OutletLocations.migrate(points,venues,names).forEach{point->db.execSQL("UPDATE shop_branches SET address=?,label=? WHERE id=?",arrayOf(point.address,point.label,point.id))}
            db.execSQL("UPDATE shops SET address=(SELECT CASE WHEN b.venueId IS NULL THEN b.address ELSE (SELECT v.address FROM venues v WHERE v.id=b.venueId) END FROM shop_branches b WHERE b.shopId=shops.id ORDER BY b.displayOrder,b.id LIMIT 1) WHERE EXISTS(SELECT 1 FROM shop_branches b WHERE b.shopId=shops.id)")
        }}
        val MIGRATION_20_21=object:Migration(20,21){override fun migrate(db:SupportSQLiteDatabase){
            db.execSQL("ALTER TABLE goal_lists ADD COLUMN isTemplate INTEGER NOT NULL DEFAULT 0")
        }}
        val MIGRATION_21_22=object:Migration(21,22){override fun migrate(db:SupportSQLiteDatabase){
            db.execSQL("ALTER TABLE venues ADD COLUMN kind TEXT NOT NULL DEFAULT 'unspecified'")
        }}
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val appContext=context.applicationContext
                val instance = Room.databaseBuilder(
                    appContext,
                    AppDatabase::class.java,
                    "spisok_ryadom.db"
                )
                    .openHelperFactory(BeforeOpenHelperFactory{DatabaseRecovery.preserveBeforeUpgrade(appContext,22)})
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) { RetailConstraints.install(db) }
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL("INSERT OR IGNORE INTO cities(id,name) VALUES(1,'Мой город')")
                            StandardCategories.install(db)
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
