package com.spisokryadom.app.data.db

import androidx.sqlite.db.SupportSQLiteDatabase

internal object ProductVariantSchema {
 fun create(db:SupportSQLiteDatabase){
  db.execSQL("CREATE TABLE IF NOT EXISTS product_variants (productId INTEGER NOT NULL PRIMARY KEY, familyId INTEGER NOT NULL, label TEXT NOT NULL, displayOrder INTEGER NOT NULL, FOREIGN KEY(productId) REFERENCES products(id) ON DELETE CASCADE, FOREIGN KEY(familyId) REFERENCES products(id) ON DELETE CASCADE)")
  db.execSQL("CREATE INDEX IF NOT EXISTS index_product_variants_familyId ON product_variants(familyId)")
  db.execSQL("CREATE TABLE IF NOT EXISTS product_variant_cities (productId INTEGER NOT NULL, cityId INTEGER NOT NULL, enabled INTEGER NOT NULL, displayOrder INTEGER NOT NULL, PRIMARY KEY(productId,cityId), FOREIGN KEY(productId) REFERENCES product_variants(productId) ON DELETE CASCADE, FOREIGN KEY(cityId) REFERENCES cities(id) ON DELETE CASCADE)")
  db.execSQL("CREATE INDEX IF NOT EXISTS index_product_variant_cities_cityId ON product_variant_cities(cityId)")
  db.execSQL("CREATE TABLE IF NOT EXISTS product_photos (productId INTEGER NOT NULL, position INTEGER NOT NULL, uri TEXT NOT NULL, caption TEXT NOT NULL, PRIMARY KEY(productId,position), FOREIGN KEY(productId) REFERENCES products(id) ON DELETE CASCADE)")
  db.execSQL("INSERT OR IGNORE INTO product_photos(productId,position,uri,caption) SELECT id,0,photoUri,'' FROM products WHERE photoUri IS NOT NULL AND photoUri!=''")
 }
 fun install(db:SupportSQLiteDatabase){
  val exists=db.query("SELECT 1 FROM sqlite_master WHERE type='table' AND name='product_variants'").use{it.moveToFirst()}
  if(!exists)return
  // A family is one level deep. Direct DAO writes must obey the same ownership rules.
  listOf("INSERT","UPDATE").forEach{op->
   db.execSQL("CREATE TRIGGER IF NOT EXISTS variant_family_$op BEFORE $op ON product_variants WHEN NEW.productId!=NEW.familyId AND NOT EXISTS(SELECT 1 FROM product_variants v WHERE v.productId=NEW.familyId AND v.familyId=v.productId) BEGIN SELECT RAISE(ABORT,'Invalid variant family'); END")
   db.execSQL("CREATE TRIGGER IF NOT EXISTS product_photo_$op BEFORE $op ON product_photos WHEN NEW.position<0 OR NEW.position>=5 OR trim(NEW.uri)='' BEGIN SELECT RAISE(ABORT,'Invalid product photo'); END")
  }
  db.execSQL("CREATE TRIGGER IF NOT EXISTS delete_product_family BEFORE DELETE ON products BEGIN DELETE FROM products WHERE id IN (SELECT productId FROM product_variants WHERE familyId=OLD.id AND productId!=OLD.id); END")
  db.execSQL("CREATE TRIGGER IF NOT EXISTS product_family_common AFTER UPDATE OF name,productGroupId,recipientId,isFavorite,accent ON products WHEN OLD.name IS NOT NEW.name OR OLD.productGroupId IS NOT NEW.productGroupId OR OLD.recipientId IS NOT NEW.recipientId OR OLD.isFavorite IS NOT NEW.isFavorite OR OLD.accent IS NOT NEW.accent BEGIN UPDATE products SET name=NEW.name,productGroupId=NEW.productGroupId,recipientId=NEW.recipientId,isFavorite=NEW.isFavorite,accent=NEW.accent WHERE id!=NEW.id AND id IN (SELECT productId FROM product_variants WHERE familyId=(SELECT familyId FROM product_variants WHERE productId=NEW.id)) AND (name IS NOT NEW.name OR productGroupId IS NOT NEW.productGroupId OR recipientId IS NOT NEW.recipientId OR isFavorite IS NOT NEW.isFavorite OR accent IS NOT NEW.accent); END")
 }
}
