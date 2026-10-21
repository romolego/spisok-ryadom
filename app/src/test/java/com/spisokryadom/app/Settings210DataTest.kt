package com.spisokryadom.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.backup.BackupManager
import com.spisokryadom.app.data.db.*
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.ui.productcard.*
import com.spisokryadom.app.util.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class Settings210DataTest {
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 private val prefs get()=app.getSharedPreferences("shopping_list_prefs",0)
 private lateinit var db:AppDatabase
 private lateinit var reset:ResetRepository
 private var shop=0L;private var product=0L;private var group=0L;private var department=0L
 @Before fun setup():Unit=runBlocking {
  prefs.edit().clear().commit()
  db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build()
  RetailConstraints.install(db.openHelper.writableDatabase)
  db.cityDao().insert(CityEntity(1,"Город"))
  group=db.productGroupDao().insert(ProductGroupEntity(name="Продукты",iconKey="car",isFavorite=true))
  val recipient=db.recipientDao().insert(RecipientEntity(name="Получатель"))
  shop=db.shopDao().insert(ShopEntity(name="Магазин"))
  val type=db.shopDepartmentDao().insertType(DepartmentTypeEntity(name="Молочка",normalizedName=FuzzySearch.normalize("Молочка")))
  department=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=shop,typeId=type,name="Молочка",displayOrder=9))
  product=db.productDao().insert(ProductEntity(name="Товар",productGroupId=group,recipientId=recipient,defaultUnit="кг"))
  db.productShopLinkDao().insert(ProductShopLinkEntity(productId=product,shopId=shop,departmentId=department))
  db.shoppingListEntryDao().insert(ShoppingListEntryEntity(productId=product,quantity=2.5,unit="кг",assignedShopId=shop,assignedDepartmentId=department,isBought=true))
  reset=ResetRepository(app,db,ProductDraftSynchronizer(ProductDraftStore.preferences(app)))
 }
 @After fun close(){db.close()}
 private fun foreignKeys(){db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}}
 @Test fun sixFavoriteStylesPersistIndependentlyAndLegacyKeysSeedNewContexts(){
  prefs.edit().putBoolean("favorite_block_star",false).putString("favorite_row_color","#112233").commit()
  val old=FavoriteAppearance.read(prefs)
  FavoriteSurface.entries.forEach{assertFalse(old.style(it,true).star);assertEquals("#112233",old.style(it,false).color)}
  var value=old
  FavoriteAppearance.scopes.forEachIndexed{i,(surface,pin,_)->value=value.change(surface,pin,FavoriteStyle(i%2==0,"#12345$i",i%3==0,i%2!=0))}
  value.save(prefs);assertEquals(value,FavoriteAppearance.read(prefs))
  val changed=value.change(FavoriteSurface.PURCHASES,false,FavoriteStyle(false,"#ABCDEF",false,false))
  changed.save(prefs);assertEquals(value.block,FavoriteAppearance.read(prefs).block);assertEquals(value.dropdownRow,FavoriteAppearance.read(prefs).dropdownRow)
 }
 @Test fun defaultsOffKeepsBlankFieldsAndOnFillsOnlyNewMissingFields():Unit=runBlocking {
  val repository=ProductRepository(db.productDao(),db.productShopLinkDao(),db)
  assertFalse(ProductCreationDefaults.read(prefs).fillMissing)
  suspend fun create(id:String,s:ProductCardUiState)=ProductEditDraft(id,-1,true,false,s).persist(repository,ProductCreationDefaults.read(prefs))
  val blank=create("blank210",ProductCardUiState(name="Без заполнения",isNew=true))
  assertNull(db.productDao().getById(blank)!!.defaultQuantity);assertNull(db.productDao().getById(blank)!!.defaultUnit)
  prefs.edit().putBoolean(ProductCreationDefaults.ENABLED,true).putString(ProductCreationDefaults.UNIT,"ед").putString(ProductCreationDefaults.QUANTITY,"3").commit()
  val filled=create("filled210",ProductCardUiState(name="С заполнением",isNew=true))
  assertEquals(3.0,db.productDao().getById(filled)!!.defaultQuantity!!,0.0);assertEquals("ед",db.productDao().getById(filled)!!.defaultUnit)
  val explicit=create("explicit210",ProductCardUiState(name="Явно",isNew=true,defaultQuantity="0,5",defaultUnit="кг"))
  assertEquals(.5,db.productDao().getById(explicit)!!.defaultQuantity!!,0.0)
  ProductEditDraft("filled210",filled,false,false,ProductCardUiState(name="С заполнением",isNew=false)).persist(repository,ProductCreationDefaults.read(prefs))
  assertNull(db.productDao().getById(filled)!!.defaultUnit);assertNull(db.productDao().getById(filled)!!.defaultQuantity)
 }
 @Test fun oldConfiguredDefaultsMigrateEnabledButCanBeExplicitlyDisabled(){
  prefs.edit().putString(ProductCreationDefaults.UNIT,"мешок").commit();assertTrue(ProductCreationDefaults.read(prefs).fillMissing)
  prefs.edit().putBoolean(ProductCreationDefaults.ENABLED,false).commit();assertFalse(ProductCreationDefaults.read(prefs).fillMissing)
 }
 @Test fun restoringBuiltInsKeepsCustomDataIdsRelationsAndLocalRouteAndIsIdempotent():Unit=runBlocking {
  val custom=db.productGroupDao().insert(ProductGroupEntity(name="Личная группа"))
  val entry=db.shoppingListEntryDao().getAllSync().single()
  reset.reset(ResetMode.BUILT_INS)
  assertNotNull(db.productGroupDao().getById(custom));assertEquals("groceries",db.productGroupDao().getById(group)!!.iconKey)
  assertFalse(db.productGroupDao().getById(group)!!.isFavorite);assertEquals(group,db.productDao().getById(product)!!.productGroupId)
  assertEquals(entry,db.shoppingListEntryDao().getAllSync().single());assertEquals(9,db.shopDepartmentDao().getById(department)!!.displayOrder)
  val groups=db.productGroupDao().getAllSync();reset.reset(ResetMode.BUILT_INS);assertEquals(groups,db.productGroupDao().getAllSync());foreignKeys()
 }
 @Test fun dictionaryResetDetachesReferencesButPreservesPurchasesAndRawInput():Unit=runBlocking {
  val p=db.productDao().getById(product)!!
  ProductDraftStore.write(ProductDraftStore.preferences(app),"draft",ProductEditDraft(p.stableId,product,false,false,ProductCardUiState(name="Незавершённый ввод",productGroupId=group,recipientId=p.recipientId,defaultQuantity="1,")))
  val generation=ProductEditorTransactions.generation.get()
  reset.reset(ResetMode.DICTIONARIES)
  assertEquals(generation+1,ProductEditorTransactions.generation.get());assertNull(db.productDao().getById(product)!!.productGroupId);assertNull(db.productDao().getById(product)!!.recipientId)
  val entry=db.shoppingListEntryDao().getAllSync().single();assertEquals(2.5,entry.quantity,0.0);assertEquals("кг",entry.unit);assertTrue(entry.isBought);assertNull(entry.assignedDepartmentId)
  assertEquals(shop,entry.assignedShopId!!);assertNull(db.productShopLinkDao().getLinksForProductSync(product).single().departmentId)
  val raw=ProductDraftStore.find(ProductDraftStore.preferences(app),p.stableId)!!;assertEquals("Незавершённый ввод",raw.state.name);assertEquals("1,",raw.state.defaultQuantity);assertNull(raw.state.recipientId);assertNull(raw.state.productGroupId)
  assertEquals(30,db.productGroupDao().getAllSync().size);assertTrue(db.recipientDao().getAllSync().isEmpty());foreignKeys()
 }
 @Test fun fullResetClearsPrivateDataAndSettingsAndInvalidatesOldEditors():Unit=runBlocking {
  val generation=ProductEditorTransactions.generation.get();val revision=DatabaseRevision.current(app)
  prefs.edit().putString("new_product_missing_quantity","8").commit()
  val photo=File(app.filesDir,"product_photos/test.jpg").also{it.parentFile!!.mkdirs();it.writeText("owned")}
  val external=File(app.cacheDir,"keep-outside-reset.txt").also{it.writeText("keep")}
  val goal=db.goalListDao().insert(GoalListEntity(name="Цель"))
  db.shoppingListEntryDao().insert(ShoppingListEntryEntity(productId=product,quantity=1.0,listId=goal))
  reset.reset(ResetMode.APPLICATION)
  assertTrue(db.productDao().getAllSync().isEmpty());assertTrue(db.shoppingListEntryDao().getAllSync().isEmpty());assertTrue(db.goalListDao().getAllSync().isEmpty());assertTrue(db.shopDao().getAllSync().isEmpty())
  assertEquals(30,db.productGroupDao().getAllSync().size);assertEquals("Мой город",db.cityDao().getAllSync().single().name);assertTrue(prefs.all.isEmpty());assertTrue(ProductDraftStore.preferences(app).all.isEmpty())
  assertFalse(photo.exists());assertEquals("keep",external.readText());assertNotEquals(revision,DatabaseRevision.current(app));assertFalse(ProductCreationDefaults.read(prefs).fillMissing)
  assertTrue(runCatching{ProductEditorTransactions.inGeneration(generation){error("must not run")}}.exceptionOrNull() is IllegalArgumentException);foreignKeys()
 }
 @Test fun failedDatabaseResetRollsBackBeforePreferencesAndPhotosAreTouched():Unit=runBlocking {
  prefs.edit().putString("keep","value").commit();val generation=ProductEditorTransactions.generation.get()
  db.openHelper.writableDatabase.execSQL("CREATE TRIGGER deny_reset BEFORE DELETE ON products BEGIN SELECT RAISE(ABORT, 'blocked'); END")
  assertTrue(runCatching{reset.reset(ResetMode.APPLICATION)}.isFailure)
  assertNotNull(db.productDao().getById(product));assertEquals(1,db.shoppingListEntryDao().getAllSync().size);assertEquals("value",prefs.getString("keep",null));assertEquals(generation,ProductEditorTransactions.generation.get());foreignKeys()
 }
 @Test fun backup15RoundTripsAllContextsAndDisabledDefaults():Unit=runBlocking {
  FavoriteAppearance().change(FavoriteSurface.PURCHASES,false,FavoriteStyle(false,"#ABCDEF",false,false)).change(FavoriteSurface.DROPDOWNS,true,FavoriteStyle(true,"#FEDCBA",false,true)).save(prefs)
  prefs.edit().putBoolean(ProductCreationDefaults.ENABLED,false).commit();val before=FavoriteAppearance.read(prefs)
  val manager=BackupManager(app,db);val file=manager.exportFile()
  ZipFile(file).use{z->assertEquals(18,JSONObject(z.getInputStream(z.getEntry("manifest.json")).reader().readText()).getInt("version"))}
  prefs.edit().clear().commit();manager.importFile(file);assertEquals(before,FavoriteAppearance.read(prefs));assertFalse(ProductCreationDefaults.read(prefs).fillMissing);manager.discard(file)
 }
 @Test fun oldBackup14SeedsContextsAndInvalidNewPreferenceTypesRefuseAtomically():Unit=runBlocking {
  prefs.edit().putBoolean("favorite_block_star",false).putString("favorite_row_color","#445566").commit()
  val manager=BackupManager(app,db);val file=manager.exportFile()
  val root=ZipFile(file).use{z->JSONObject(z.getInputStream(z.getEntry("manifest.json")).reader().readText())}
  fun archive(value:JSONObject):File=File(app.cacheDir,"modified210.zip").also{target->ZipOutputStream(target.outputStream()).use{z->z.putNextEntry(ZipEntry("manifest.json"));z.write(value.toString().toByteArray());z.closeEntry()}}
  root.put("version",14);manager.importFile(archive(root));FavoriteSurface.entries.forEach{assertFalse(FavoriteAppearance.read(prefs).style(it,true).star);assertEquals("#445566",FavoriteAppearance.read(prefs).style(it,false).color)}
  val before=db.productDao().getAllSync();val settings=prefs.all.toMap()
  val preferences=root.getJSONObject("preferences").getJSONObject("shopping_list_prefs")
  listOf("favorite_purchases_row_star","favorite_dropdowns_block_bold",ProductCreationDefaults.ENABLED).forEach{key->
   preferences.put(key,"wrong");root.put("version",15);assertTrue(runCatching{manager.importFile(archive(root))}.isFailure);assertEquals(before,db.productDao().getAllSync());assertEquals(settings,prefs.all);preferences.remove(key)
  }
  manager.discard(file)
 }
}
