package com.spisokryadom.app

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.backup.BackupManager
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.ui.shopcard.*
import com.spisokryadom.app.util.PhotoStore
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.*
import java.io.File
import java.time.Duration
import java.util.zip.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoreCard180DataTest {
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 private lateinit var db:AppDatabase;private lateinit var locations:LocationRepository;private lateinit var shops:ShopRepository
 private val store=ViewModelStore()
 private fun await(c:()->Boolean){val end=System.nanoTime()+20_000_000_000;while(!c()&&System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(25));Thread.sleep(5)};assertTrue(c())}
 @Before fun setup():Unit=runBlocking{
  db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build();db.cityDao().insert(CityEntity(1,"Тестовый город"))
  locations=LocationRepository(db.cityDao(),db.shopDao(),app.getSharedPreferences("180-location",0));shops=ShopRepository(db.shopDao(),db.shopDepartmentDao(),locations,db)
 }
 @After fun close(){store.clear();await{!ProductEditorTransactions.mutex.isLocked};locations.closeForTest();db.close()}
 private fun card(id:Long)=ShopCardViewModel(id,shops).also{store.put("card",it);await{!it.uiState.value.isLoading}}
 private fun logo():File {val f=File(app.filesDir,"logo-source.png");val b=Bitmap.createBitmap(600,300,Bitmap.Config.ARGB_8888);b.eraseColor(0x55FFAA00);f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle();return f}
 @Test fun logoImportIsSmallAndPreservesTransparency(){
  val path=PhotoStore.importPhoto(app,Uri.fromFile(logo()),"shop_logos");val b=PhotoStore.load(path)!!
  assertTrue("Thumbnail ${b.width}×${b.height}",maxOf(b.width,b.height)<=256);assertTrue("PNG alpha",b.hasAlpha());assertEquals(0x55,b.getPixel(b.width/2,b.height/2).ushr(24));b.recycle()
 }
 @Test fun appearanceAndUrgencySaveWithoutChangingBranchIdentity():Unit=runBlocking{
  val id=db.shopDao().insert(ShopEntity(name="Магазин",showUrgentInCollapsed=true,boostUrgentShop=false,shoppingBackground="yellow"));val branch=db.shopBranchDao().insert(ShopBranchEntity(shopId=id,address="Адрес"))
  val v=card(id);v.updateIcon("bag");v.updateShoppingBackground("#D0B75F");v.updateNote("Можно заказать доставку");v.save();await{v.uiState.value.isSaved}
  val s=db.shopDao().getById(id)!!;assertEquals("bag",s.iconKey);assertEquals("#D0B75F",s.shoppingBackground);assertEquals(true,s.showUrgentInCollapsed);assertEquals(false,s.boostUrgentShop);assertEquals(branch,db.shopBranchDao().forShop(id).single().id)
 }
 @Test fun choosingLogoAndIconIsMutuallyExclusiveAndCanBeRemoved():Unit=runBlocking{
  val v=card(-1);v.updateName("Магазин");v.updateIcon("bag");v.updateLogo("/local/logo.png");assertEquals("",v.uiState.value.iconKey);v.updateIcon("home");assertNull(v.uiState.value.logoUri);v.updateLogo(null);assertEquals("home",v.uiState.value.iconKey)
  v.updateIcon("");v.save();await{v.uiState.value.isSaved};assertEquals("",db.shopDao().getAllSync().single().iconKey)
 }
 @Test fun logoSurvivesBackupRestoreAndCleanup():Unit=runBlocking{
  val path=PhotoStore.importPhoto(app,Uri.fromFile(logo()),"shop_logos");val original=File(path).readBytes();val id=db.shopDao().insert(ShopEntity(name="Магазин",logoUri=path,shoppingBackground="rose"));val manager=BackupManager(app,db);val zip=manager.exportFile()
  ZipFile(zip).use{z->assertNotNull(z.getEntry("shop_logos/$id.png"));val root=JSONObject(z.getInputStream(z.getEntry("manifest.json")).reader().readText());assertEquals(17,root.getInt("version"));assertFalse(root.getJSONObject("data").getJSONArray("shops").getJSONObject(0).has("logoUri"))}
  db.shopDao().deleteAll();manager.importFile(zip);val s=db.shopDao().getAllSync().single();assertArrayEquals(original,File(s.logoUri!!).readBytes());File(s.logoUri!!).setLastModified(1);MediaLifecycle.cleanup(app,db);assertTrue(File(s.logoUri!!).isFile);assertEquals("rose",s.shoppingBackground)
 }
 @Test fun oldVersion12RestoresWithDefaultStoreSymbol():Unit=runBlocking{
  db.shopDao().insert(ShopEntity(name="Прежний"));val manager=BackupManager(app,db);val zip=manager.exportFile();val root=ZipFile(zip).use{JSONObject(it.getInputStream(it.getEntry("manifest.json")).reader().readText())};root.put("version",12);root.getJSONObject("data").getJSONArray("shops").getJSONObject(0).remove("iconKey")
  val old=File(app.cacheDir,"old12.zip");ZipOutputStream(old.outputStream()).use{it.putNextEntry(ZipEntry("manifest.json"));it.write(root.toString().toByteArray());it.closeEntry()};manager.importFile(old);val s=db.shopDao().getAllSync().single();assertEquals("",s.iconKey);assertNull(s.logoUri)
 }
 @Test fun invalidLogoPathIsRejectedBeforeReplacingDatabase():Unit=runBlocking{
  val id=db.shopDao().insert(ShopEntity(name="Сохранить"));val manager=BackupManager(app,db);val zip=manager.exportFile();val root=ZipFile(zip).use{JSONObject(it.getInputStream(it.getEntry("manifest.json")).reader().readText())};root.getJSONObject("data").getJSONArray("shops").getJSONObject(0).put("logo","../../other.png")
  val bad=File(app.cacheDir,"bad-logo.zip");ZipOutputStream(bad.outputStream()).use{it.putNextEntry(ZipEntry("manifest.json"));it.write(root.toString().toByteArray());it.closeEntry()};assertTrue(runCatching{manager.importFile(bad)}.isFailure);assertEquals("Сохранить",db.shopDao().getById(id)!!.name)
 }
 @Test fun oldCatalogueWithoutStoresStillImports():Unit=runBlocking{
  val f=File(app.cacheDir,"legacy-minimal.json")
  val root=JSONObject().put("version",3).put("data",JSONObject().put("products",org.json.JSONArray().put(JSONObject().put("id",9).put("name","Старая позиция"))))
  f.writeText(root.toString());val summary=BackupManager(app,db).importFile(f)
  assertFalse(summary.full);assertEquals("Старая позиция",db.productDao().getAllSync().single().name);assertTrue(db.shopDao().getAllSync().isEmpty())
 }
 @Test fun marketConversionPreservesAppearanceOnAllSellers():Unit=runBlocking{
  val id=db.shopDao().insert(ShopEntity(name="Рынок",iconKey="bag",logoUri="local.png",shoppingBackground="rose"));db.shopBranchDao().insert(ShopBranchEntity(shopId=id,address="Павильон 1"));db.shopBranchDao().insert(ShopBranchEntity(shopId=id,address="Павильон 2"));shops.convertToVenue(id)
  val sellers=db.shopDao().getAllSync();assertEquals(2,sellers.size);assertTrue(sellers.all{it.iconKey=="bag"&&it.logoUri=="local.png"&&it.shoppingBackground=="rose"})
 }
 @Test fun migration16PreservesStoreAndPurchaseFields():Unit=runBlocking{
  val name="180-migration.db";app.deleteDatabase(name)
  val schema=File("schemas/com.spisokryadom.app.data.db.AppDatabase/16.json").takeIf{it.isFile}?:File("app/schemas/com.spisokryadom.app.data.db.AppDatabase/16.json")
  app.openOrCreateDatabase(name,0,null).use{old->val entities=JSONObject(schema.readText()).getJSONObject("database").getJSONArray("entities");for(i in 0 until entities.length()){val e=entities.getJSONObject(i);old.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName")));val indices=e.getJSONArray("indices");for(j in 0 until indices.length())old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName")))}
   old.execSQL("INSERT INTO cities(id,name) VALUES(1,'Город')");old.execSQL("INSERT INTO shops(id,name,cityId,displayOrder,manualOrderOverride,shoppingBackground,showUrgentInCollapsed,boostUrgentShop) VALUES(7,'Магазин',1,4,1,'#DDBB44',0,1)");old.version=16
  }
  val migrated=Room.databaseBuilder(app,AppDatabase::class.java,name).allowMainThreadQueries().addMigrations(AppDatabase.MIGRATION_16_17,AppDatabase.MIGRATION_17_18,AppDatabase.MIGRATION_18_19,AppDatabase.MIGRATION_19_20).build()
  try{val s=migrated.shopDao().getById(7)!!;assertEquals("Магазин",s.name);assertEquals("#DDBB44",s.shoppingBackground);assertEquals(4,s.displayOrder);assertTrue(s.manualOrderOverride);assertEquals(false,s.showUrgentInCollapsed);assertEquals(true,s.boostUrgentShop);assertEquals("",s.iconKey);assertNull(s.logoUri);migrated.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}}
  finally{migrated.close();app.deleteDatabase(name)}
 }
}
