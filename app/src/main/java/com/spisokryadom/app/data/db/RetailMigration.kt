package com.spisokryadom.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.spisokryadom.app.util.FuzzySearch

/** Preserve original IDs. Old ambiguous market names are never guessed from spelling. */
object RetailMigration:Migration(10,11) {
 override fun migrate(db:SupportSQLiteDatabase) {
  db.execSQL("ALTER TABLE products ADD COLUMN preferCityShopOrder INTEGER NOT NULL DEFAULT 0")
  val names=linkedMapOf<String,Pair<Long,String>>()
  val types=mutableMapOf<Long,Long>()
  db.query("SELECT id,name FROM shop_departments ORDER BY id").use{c->while(c.moveToNext()){
   val id=c.getLong(0);val name=c.getString(1);val key=FuzzySearch.normalize(name)
   val canonical=names.getOrPut(key){id to name.trim()};types[id]=canonical.first
  }}
  val graph=listOf("shops","shop_branches","shop_departments","product_shop_links","shopping_list_entries","product_branch_availability")
  graph.forEach{db.execSQL("CREATE TEMP TABLE migration_$it AS SELECT * FROM $it")}
  graph.asReversed().forEach{db.execSQL("DROP TABLE $it")}
  db.execSQL("CREATE TABLE IF NOT EXISTS retail_networks(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,name TEXT NOT NULL)")
  db.execSQL("CREATE TABLE IF NOT EXISTS venues(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,cityId INTEGER NOT NULL,name TEXT NOT NULL,address TEXT,note TEXT,FOREIGN KEY(cityId) REFERENCES cities(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
  db.execSQL("CREATE INDEX index_venues_cityId ON venues(cityId)")
  db.execSQL("CREATE TABLE IF NOT EXISTS department_types(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,name TEXT NOT NULL,normalizedName TEXT NOT NULL)")
  db.execSQL("CREATE UNIQUE INDEX index_department_types_normalizedName ON department_types(normalizedName)")
  names.forEach{(key,v)->db.execSQL("INSERT INTO department_types(id,name,normalizedName) VALUES(?,?,?)",arrayOf(v.first,v.second,key))}
  db.execSQL("""CREATE TABLE IF NOT EXISTS `shops` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `address` TEXT, `note` TEXT, `displayOrder` INTEGER NOT NULL, `showUrgentInCollapsed` INTEGER, `boostUrgentShop` INTEGER, `manualOrderOverride` INTEGER NOT NULL, `cityId` INTEGER NOT NULL, `networkId` INTEGER, FOREIGN KEY(`cityId`) REFERENCES `cities`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`networkId`) REFERENCES `retail_networks`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)""")
  db.execSQL("""CREATE INDEX IF NOT EXISTS `index_shops_cityId` ON `shops` (`cityId`)""")
  db.execSQL("""CREATE TABLE IF NOT EXISTS `shop_branches` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `shopId` INTEGER NOT NULL, `address` TEXT NOT NULL, `displayOrder` INTEGER NOT NULL, `note` TEXT, `photoUri` TEXT, `venueId` INTEGER, FOREIGN KEY(`venueId`) REFERENCES `venues`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, FOREIGN KEY(`shopId`) REFERENCES `shops`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
  db.execSQL("""CREATE INDEX IF NOT EXISTS `index_shop_branches_shopId` ON `shop_branches` (`shopId`)""")
  db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_shop_branches_shopId_address` ON `shop_branches` (`shopId`, `address`)""")
  db.execSQL("""CREATE TABLE IF NOT EXISTS `shop_departments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `shopId` INTEGER NOT NULL, `typeId` INTEGER NOT NULL, `displayOrder` INTEGER NOT NULL, FOREIGN KEY(`typeId`) REFERENCES `department_types`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`shopId`) REFERENCES `shops`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
  db.execSQL("""CREATE INDEX IF NOT EXISTS `index_shop_departments_shopId` ON `shop_departments` (`shopId`)""")
  db.execSQL("""CREATE TABLE IF NOT EXISTS `product_shop_links` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `productId` INTEGER NOT NULL, `shopId` INTEGER NOT NULL, `priority` INTEGER NOT NULL, `departmentId` INTEGER, `restrictBranches` INTEGER NOT NULL DEFAULT 0, FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`shopId`) REFERENCES `shops`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`departmentId`) REFERENCES `shop_departments`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )""")
  db.execSQL("""CREATE INDEX IF NOT EXISTS `index_product_shop_links_productId` ON `product_shop_links` (`productId`)""")
  db.execSQL("""CREATE INDEX IF NOT EXISTS `index_product_shop_links_shopId` ON `product_shop_links` (`shopId`)""")
  db.execSQL("""CREATE INDEX IF NOT EXISTS `index_product_shop_links_departmentId` ON `product_shop_links` (`departmentId`)""")
  db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_product_shop_links_productId_shopId` ON `product_shop_links` (`productId`, `shopId`)""")
  db.execSQL("""CREATE TABLE IF NOT EXISTS `shopping_list_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `productId` INTEGER NOT NULL, `quantity` REAL NOT NULL, `unit` TEXT NOT NULL, `assignedShopId` INTEGER, `assignedDepartmentId` INTEGER, `isBought` INTEGER NOT NULL, `isUrgent` INTEGER NOT NULL, `note` TEXT, `comment` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `assignedBranchId` INTEGER, FOREIGN KEY(`assignedBranchId`) REFERENCES `shop_branches`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`assignedShopId`) REFERENCES `shops`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`assignedDepartmentId`) REFERENCES `shop_departments`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )""")
  db.execSQL("""CREATE INDEX IF NOT EXISTS `index_shopping_list_entries_assignedBranchId` ON `shopping_list_entries` (`assignedBranchId`)""")
  db.execSQL("""CREATE INDEX IF NOT EXISTS `index_shopping_list_entries_productId` ON `shopping_list_entries` (`productId`)""")
  db.execSQL("""CREATE INDEX IF NOT EXISTS `index_shopping_list_entries_assignedShopId` ON `shopping_list_entries` (`assignedShopId`)""")
  db.execSQL("""CREATE INDEX IF NOT EXISTS `index_shopping_list_entries_assignedDepartmentId` ON `shopping_list_entries` (`assignedDepartmentId`)""")
  db.execSQL("""CREATE TABLE IF NOT EXISTS `product_branch_availability` (`productId` INTEGER NOT NULL, `branchId` INTEGER NOT NULL, `shopId` INTEGER NOT NULL, PRIMARY KEY(`productId`,`branchId`), FOREIGN KEY(`productId`,`shopId`) REFERENCES `product_shop_links`(`productId`,`shopId`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`branchId`,`shopId`) REFERENCES `shop_branches`(`id`,`shopId`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
  db.execSQL("""CREATE INDEX IF NOT EXISTS `index_product_branch_availability_productId` ON `product_branch_availability` (`productId`)""")
  db.execSQL("""CREATE INDEX IF NOT EXISTS `index_product_branch_availability_branchId` ON `product_branch_availability` (`branchId`)""")
  db.execSQL("""CREATE INDEX IF NOT EXISTS index_shops_networkId ON shops(networkId)""")
  db.execSQL("""CREATE INDEX IF NOT EXISTS index_shop_branches_venueId ON shop_branches(venueId)""")
  db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS index_shop_branches_id_shopId ON shop_branches(id,shopId)""")
  db.execSQL("""CREATE INDEX IF NOT EXISTS index_shop_departments_typeId ON shop_departments(typeId)""")
  db.execSQL("""CREATE INDEX IF NOT EXISTS index_product_branch_availability_productId_shopId ON product_branch_availability(productId,shopId)""")
  db.execSQL("""CREATE INDEX IF NOT EXISTS index_product_branch_availability_branchId_shopId ON product_branch_availability(branchId,shopId)""")
  db.execSQL("INSERT OR IGNORE INTO cities(id,name) SELECT DISTINCT cityId,'Мой город '||cityId FROM migration_shops WHERE cityId NOT IN (SELECT id FROM cities)")
  db.execSQL("INSERT INTO shops SELECT *,NULL FROM migration_shops")
  db.execSQL("INSERT INTO shop_branches SELECT *,NULL FROM migration_shop_branches")
  db.query("SELECT id,shopId,displayOrder FROM migration_shop_departments").use{c->while(c.moveToNext()){
   val id=c.getLong(0);db.execSQL("INSERT INTO shop_departments(id,shopId,typeId,displayOrder) VALUES(?,?,?,?)",arrayOf(id,c.getLong(1),types.getValue(id),c.getInt(2)))
  }}
  db.execSQL("INSERT INTO product_shop_links SELECT * FROM migration_product_shop_links")
  db.execSQL("UPDATE product_shop_links SET departmentId=NULL WHERE departmentId IS NOT NULL AND NOT EXISTS(SELECT 1 FROM shop_departments d WHERE d.id=departmentId AND d.shopId=product_shop_links.shopId)")
  db.execSQL("INSERT INTO shopping_list_entries SELECT * FROM migration_shopping_list_entries")
  db.execSQL("UPDATE shopping_list_entries SET assignedDepartmentId=NULL WHERE assignedDepartmentId IS NOT NULL AND NOT EXISTS(SELECT 1 FROM shop_departments d WHERE d.id=assignedDepartmentId AND d.shopId=shopping_list_entries.assignedShopId)")
  db.execSQL("UPDATE shopping_list_entries SET assignedBranchId=NULL WHERE assignedBranchId IS NOT NULL AND NOT EXISTS(SELECT 1 FROM shop_branches b WHERE b.id=assignedBranchId AND b.shopId=shopping_list_entries.assignedShopId)")
  // Remove only orphaned availability from the old unlink bug; never remove purchases.
  db.execSQL("INSERT INTO product_branch_availability SELECT a.productId,a.branchId,b.shopId FROM migration_product_branch_availability a JOIN shop_branches b ON b.id=a.branchId JOIN product_shop_links l ON l.productId=a.productId AND l.shopId=b.shopId WHERE l.restrictBranches=1")
  db.execSQL("UPDATE product_shop_links SET restrictBranches=0 WHERE restrictBranches=1 AND NOT EXISTS(SELECT 1 FROM product_branch_availability a WHERE a.productId=product_shop_links.productId AND a.shopId=product_shop_links.shopId)")
  graph.forEach{db.execSQL("DROP TABLE migration_$it")}
  db.execSQL("CREATE TABLE IF NOT EXISTS branch_department_order(branchId INTEGER NOT NULL,typeId INTEGER NOT NULL,displayOrder INTEGER NOT NULL,PRIMARY KEY(branchId,typeId),FOREIGN KEY(branchId) REFERENCES shop_branches(id) ON UPDATE NO ACTION ON DELETE CASCADE,FOREIGN KEY(typeId) REFERENCES department_types(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
  db.execSQL("CREATE INDEX index_branch_department_order_typeId ON branch_department_order(typeId)")
  db.execSQL("CREATE TABLE IF NOT EXISTS product_branch_departments(productId INTEGER NOT NULL,branchId INTEGER NOT NULL,shopId INTEGER NOT NULL,typeId INTEGER NOT NULL,PRIMARY KEY(productId,branchId),FOREIGN KEY(productId,shopId) REFERENCES product_shop_links(productId,shopId) ON UPDATE NO ACTION ON DELETE CASCADE,FOREIGN KEY(branchId,shopId) REFERENCES shop_branches(id,shopId) ON UPDATE NO ACTION ON DELETE CASCADE,FOREIGN KEY(typeId) REFERENCES department_types(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
  db.execSQL("CREATE INDEX index_product_branch_departments_productId_shopId ON product_branch_departments(productId,shopId)")
  db.execSQL("CREATE INDEX index_product_branch_departments_branchId_shopId ON product_branch_departments(branchId,shopId)")
  db.execSQL("CREATE INDEX index_product_branch_departments_typeId ON product_branch_departments(typeId)")
  RetailConstraints.install(db)
 }
}
