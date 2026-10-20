package com.spisokryadom.app
import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.backup.BackupManager
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.util.PhotoStore
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File
import java.util.zip.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MarketDataTest {
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 @Test fun v9To10KeepsPointLinksAndPurchases()=runBlocking{
  val name="m.db"
  app.openOrCreateDatabase(name,0,null).use{old->
   val tables=JSONObject(javaClass.classLoader!!.getResourceAsStream("schema-v9.json")!!.reader().readText()).getJSONObject("database").getJSONArray("entities")
   for(i in 0 until tables.length()){val t=tables.getJSONObject(i);val n=t.getString("tableName");old.execSQL(t.getString("createSql").replace("\${TABLE_NAME}",n));val idx=t.getJSONArray("indices");for(j in 0 until idx.length())old.execSQL(idx.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",n))}
   old.execSQL("INSERT INTO cities(id,name)VALUES(1,'Красноярск')")
   old.execSQL("INSERT INTO shops(id,name,displayOrder,manualOrderOverride,cityId)VALUES(1,'Рынок',0,0,1)")
   old.execSQL("INSERT INTO shop_branches(id,shopId,address,displayOrder)VALUES(8,1,'29 павильон',0)")
   old.execSQL("INSERT INTO products(id,name,purchaseType,isTemporary,stableId)VALUES(2,'Сыр','offline',0,'cheese')")
   old.execSQL("INSERT INTO product_shop_links(id,productId,shopId,priority,restrictBranches)VALUES(3,2,1,1,1)")
   old.execSQL("INSERT INTO product_branch_availability(productId,branchId)VALUES(2,8)")
   old.execSQL("INSERT INTO shopping_list_entries(id,productId,quantity,unit,isBought,isUrgent,createdAt,updatedAt,assignedShopId,assignedBranchId)VALUES(4,2,0.5,'кг',0,1,10,20,1,8)")
   old.version=9
  }
  val db=Room.databaseBuilder(app,AppDatabase::class.java,name).allowMainThreadQueries().addMigrations(AppDatabase.MIGRATION_9_10,AppDatabase.MIGRATION_10_11,AppDatabase.MIGRATION_11_12,AppDatabase.MIGRATION_12_13,AppDatabase.MIGRATION_13_14,AppDatabase.MIGRATION_14_15,AppDatabase.MIGRATION_15_16,AppDatabase.MIGRATION_16_17,AppDatabase.MIGRATION_17_18).build()
  try{assertEquals(18,db.openHelper.writableDatabase.version);val b=db.shopBranchDao().get(8)!!;assertNull(b.note);assertNull(b.photoUri);assertEquals(8L,db.shopBranchDao().availability(2).single().branchId);assertEquals(8L,db.shoppingListEntryDao().getById(4)!!.assignedBranchId);db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}}
  finally{db.close();app.deleteDatabase(name)}
 }
 @Test fun pointNoteAndPhotoRoundTripAndUnrelatedProductLinkIsPreserved()=runBlocking{
  val db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build()
  try{
   db.cityDao().insert(CityEntity(1,"Красноярск"));val shop=db.shopDao().insert(ShopEntity(name="Рынок"));val other=db.shopDao().insert(ShopEntity(name="Батон"))
   val source=File(app.cacheDir,"source.jpg");val bitmap=Bitmap.createBitmap(1800,900,Bitmap.Config.ARGB_8888);source.outputStream().use{bitmap.compress(Bitmap.CompressFormat.JPEG,90,it)};bitmap.recycle()
   val path=PhotoStore.importPhoto(app,Uri.fromFile(source),"branch_photos")
   val branch=db.shopBranchDao().insert(ShopBranchEntity(shopId=shop,address="Сыр масло",note="Беру здесь сыр",photoUri=path))
   val p=db.productDao().insert(ProductEntity(name="Цукаты",note="Старые данные",priceValue=42.0))
   db.productShopLinkDao().insert(ProductShopLinkEntity(productId=p,shopId=other,priority=1))
   val repo=ProductRepository(db.productDao(),db.productShopLinkDao(),db)
   repo.ensureShopLink(p,shop);repo.ensureShopLink(p,shop)
   assertEquals(1,db.productDao().getAllSync().size);assertEquals(2,db.productShopLinkDao().getLinksForProductSync(p).size);assertEquals("Старые данные",db.productDao().getById(p)!!.note)
   val manager=BackupManager(app,db);val file=manager.exportFile()
   ZipFile(file).use{z->assertNotNull(z.getEntry("branch_photos/$branch.jpg"));assertEquals(14,JSONObject(z.getInputStream(z.getEntry("manifest.json")).reader().readText()).getInt("version"))}
   db.shopBranchDao().update(db.shopBranchDao().get(branch)!!.copy(note="Изменено",photoUri=null))
   manager.importFile(file)
   val restored=db.shopBranchDao().get(branch)!!;assertEquals("Беру здесь сыр",restored.note);assertNotNull(PhotoStore.load(restored.photoUri!!));assertTrue(File(restored.photoUri!!).isFile)
  }finally{db.close()}
 }
 @Test fun oldFullVersion5LoadsPointsWithoutExtraFields()=runBlocking{
  val db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build()
  try{
   db.cityDao().insert(CityEntity(1,"Город"));val shop=db.shopDao().insert(ShopEntity(name="Рынок"));val b=db.shopBranchDao().insert(ShopBranchEntity(shopId=shop,address="29 павильон"))
   val manager=BackupManager(app,db);val current=manager.exportFile();val root=ZipFile(current).use{z->JSONObject(z.getInputStream(z.getEntry("manifest.json")).reader().readText())};root.put("version",5);root.getJSONObject("data").getJSONArray("shopBranches").getJSONObject(0).remove("note")
   val old=File(app.cacheDir,"v5.zip");ZipOutputStream(old.outputStream()).use{it.putNextEntry(ZipEntry("manifest.json"));it.write(root.toString().toByteArray());it.closeEntry()}
   manager.importFile(old);assertEquals("29 павильон",db.shopBranchDao().get(b)!!.address);assertNull(db.shopBranchDao().get(b)!!.note);assertNull(db.shopBranchDao().get(b)!!.photoUri)
  }finally{db.close()}
 }
 @Test fun explicitlyBuyingInSecondPointDoesNotMergeFirstPoint()=runBlocking{
  val db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build();db.cityDao().insert(CityEntity(1,"Город"))
  val locations=LocationRepository(db.cityDao(),db.shopDao(),app.getSharedPreferences("market",0))
  try{val shop=db.shopDao().insert(ShopEntity(name="Рынок"));val a=db.shopBranchDao().insert(ShopBranchEntity(shopId=shop,address="29"));val b=db.shopBranchDao().insert(ShopBranchEntity(shopId=shop,address="68"));val p=db.productDao().insert(ProductEntity(name="Сыр"));db.productShopLinkDao().insert(ProductShopLinkEntity(productId=p,shopId=shop))
   val basket=ShoppingListRepository(db.shoppingListEntryDao(),db,locations);val first=basket.addProduct(p,choice=ShopChoice.EXPLICIT,shopId=shop,branchId=a);val second=basket.addProduct(p,choice=ShopChoice.EXPLICIT,shopId=shop,branchId=b);assertNotEquals(first,second);assertEquals(b,db.shoppingListEntryDao().getById(second)!!.assignedBranchId)
  }finally{locations.closeForTest();db.close()}
 }
}
