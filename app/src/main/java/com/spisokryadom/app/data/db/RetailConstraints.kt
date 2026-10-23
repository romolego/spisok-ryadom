package com.spisokryadom.app.data.db
import androidx.sqlite.db.SupportSQLiteDatabase

/** Cross-row ownership is enforced even for DAO/import paths, not only UI commands. */
object RetailConstraints {
 fun install(db:SupportSQLiteDatabase) {
  val cityProfiles=db.query("PRAGMA table_info(shops)").use{c->var found=false;while(c.moveToNext())if(c.getString(c.getColumnIndexOrThrow("name"))=="cityGroupKey")found=true;found}
  if(cityProfiles)listOf("INSERT","UPDATE").forEach{op->
   db.execSQL("CREATE TRIGGER IF NOT EXISTS unique_shop_city_$op BEFORE $op ON shops WHEN NEW.cityGroupKey!='' AND EXISTS(SELECT 1 FROM shops s WHERE s.id!=NEW.id AND s.cityGroupKey=NEW.cityGroupKey AND s.cityId=NEW.cityId) BEGIN SELECT RAISE(ABORT,'Duplicate shop city profile'); END")
  }
  val modernPoints=db.query("PRAGMA table_info(shop_branches)").use{c->var found=false;while(c.moveToNext())if(c.getString(c.getColumnIndexOrThrow("name"))=="label")found=true;found}
  if(modernPoints)listOf("INSERT","UPDATE").forEach{op->
   db.execSQL("CREATE TRIGGER IF NOT EXISTS unique_outlet_$op BEFORE $op ON shop_branches WHEN EXISTS(SELECT 1 FROM shop_branches b WHERE b.id!=NEW.id AND b.shopId=NEW.shopId AND b.venueId IS NEW.venueId AND lower(trim(b.address))=lower(trim(NEW.address)) AND lower(trim(b.label))=lower(trim(NEW.label))) BEGIN SELECT RAISE(ABORT,'Duplicate outlet'); END")
  }
  val conditions=mapOf(
   "product_shop_links" to "NEW.departmentId IS NOT NULL AND NOT EXISTS(SELECT 1 FROM shop_departments d WHERE d.id=NEW.departmentId AND d.shopId=NEW.shopId)",
   "shopping_list_entries" to "(NEW.assignedBranchId IS NOT NULL AND NOT EXISTS(SELECT 1 FROM shop_branches b WHERE b.id=NEW.assignedBranchId AND b.shopId=NEW.assignedShopId)) OR (NEW.assignedDepartmentId IS NOT NULL AND NOT EXISTS(SELECT 1 FROM shop_departments d WHERE d.id=NEW.assignedDepartmentId AND d.shopId=NEW.assignedShopId))",
   "shop_branches" to "NEW.venueId IS NOT NULL AND NOT EXISTS(SELECT 1 FROM venues v JOIN shops s ON s.cityId=v.cityId WHERE v.id=NEW.venueId AND s.id=NEW.shopId)",
   "product_branch_availability" to "NOT EXISTS(SELECT 1 FROM product_shop_links l WHERE l.productId=NEW.productId AND l.shopId=NEW.shopId AND l.restrictBranches=1)"
  )
  conditions.forEach{(table,condition)->listOf("INSERT","UPDATE").forEach{op->
   db.execSQL("CREATE TRIGGER IF NOT EXISTS ownership_${table}_$op BEFORE $op ON $table WHEN $condition BEGIN SELECT RAISE(ABORT,'Invalid relationship owner'); END")
  }}
  db.execSQL("CREATE TRIGGER IF NOT EXISTS clear_unrestricted_availability AFTER UPDATE OF restrictBranches ON product_shop_links WHEN NEW.restrictBranches=0 BEGIN DELETE FROM product_branch_availability WHERE productId=NEW.productId AND shopId=NEW.shopId; END")
  db.execSQL("CREATE TRIGGER IF NOT EXISTS protect_branch_owner BEFORE UPDATE OF shopId ON shop_branches WHEN EXISTS(SELECT 1 FROM shopping_list_entries e WHERE e.assignedBranchId=OLD.id AND e.assignedShopId IS NOT NEW.shopId) BEGIN SELECT RAISE(ABORT,'Outlet has assigned purchases'); END")
  db.execSQL("CREATE TRIGGER IF NOT EXISTS protect_shop_city BEFORE UPDATE OF cityId ON shops WHEN EXISTS(SELECT 1 FROM shop_branches b JOIN venues v ON v.id=b.venueId WHERE b.shopId=OLD.id AND v.cityId!=NEW.cityId) BEGIN SELECT RAISE(ABORT,'Outlet venue belongs to another city'); END")
  db.execSQL("CREATE TRIGGER IF NOT EXISTS protect_venue_city BEFORE UPDATE OF cityId ON venues WHEN EXISTS(SELECT 1 FROM shop_branches b JOIN shops s ON s.id=b.shopId WHERE b.venueId=OLD.id AND s.cityId!=NEW.cityId) BEGIN SELECT RAISE(ABORT,'Venue contains outlets in another city'); END")
  // Detach the entire historical assignment before individual FK SET_NULL actions run.
  db.execSQL("CREATE TRIGGER IF NOT EXISTS detach_deleted_shop BEFORE DELETE ON shops BEGIN UPDATE shopping_list_entries SET assignedShopId=NULL,assignedBranchId=NULL,assignedDepartmentId=NULL WHERE assignedShopId=OLD.id; END")
  db.execSQL("CREATE TRIGGER IF NOT EXISTS protect_department_owner BEFORE UPDATE OF shopId ON shop_departments WHEN EXISTS(SELECT 1 FROM product_shop_links l WHERE l.departmentId=OLD.id AND l.shopId!=NEW.shopId) OR EXISTS(SELECT 1 FROM shopping_list_entries e WHERE e.assignedDepartmentId=OLD.id AND e.assignedShopId IS NOT NEW.shopId) BEGIN SELECT RAISE(ABORT,'Department has assigned products or purchases'); END")
  db.execSQL("CREATE TRIGGER IF NOT EXISTS collect_local_group_settings BEFORE DELETE ON shop_departments WHEN NOT EXISTS(SELECT 1 FROM shop_departments d WHERE d.shopId=OLD.shopId AND d.typeId=OLD.typeId AND d.id!=OLD.id) BEGIN DELETE FROM branch_department_order WHERE typeId=OLD.typeId AND branchId IN (SELECT id FROM shop_branches WHERE shopId=OLD.shopId); DELETE FROM product_branch_departments WHERE shopId=OLD.shopId AND typeId=OLD.typeId; END")
  val pointOwners=mapOf(
   "branch_department_order" to "NOT EXISTS(SELECT 1 FROM shop_branches b JOIN shop_departments d ON d.shopId=b.shopId WHERE b.id=NEW.branchId AND d.typeId=NEW.typeId)",
   "product_branch_departments" to "NOT EXISTS(SELECT 1 FROM shop_departments d WHERE d.shopId=NEW.shopId AND d.typeId=NEW.typeId)"
  )
  pointOwners.forEach{(table,condition)->listOf("INSERT","UPDATE").forEach{op->db.execSQL("CREATE TRIGGER IF NOT EXISTS ownership_${table}_$op BEFORE $op ON $table WHEN $condition BEGIN SELECT RAISE(ABORT,'Invalid point group owner'); END")}}
 }
}
