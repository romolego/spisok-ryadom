package com.spisokryadom.app

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.*
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.data.backup.BackupManager
import com.spisokryadom.app.ui.productcard.*
import com.spisokryadom.app.ui.addtolist.AddToListViewModel
import com.spisokryadom.app.ui.settings.SettingsViewModel
import com.spisokryadom.app.ui.components.*
import com.spisokryadom.app.util.*
import kotlinx.coroutines.*
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
class Refine170DataTest {
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 private val prefs get()=ProductEditingPreferences.preferences(app)
 private lateinit var db:AppDatabase;private lateinit var products:ProductRepository;private lateinit var shops:ShopRepository;private lateinit var classifiers:ClassifierRepository;private lateinit var locations:LocationRepository
 private val store=ViewModelStore();private var serial=0;private var diagnostic:()->String={""}
 private fun await(c:()->Boolean){val end=System.nanoTime()+20_000_000_000;while(!c()&&System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(25));Thread.sleep(5)};assertTrue("Timed out: ${diagnostic()}",c())}
 private fun card(id:Long=-1,shop:Long=-1)=ProductCardViewModel(id,shop,products,shops,classifiers,ProductDraftStore.preferences(app),prefs).also{v->store.put("card${serial++}",v);diagnostic={v.uiState.value.toString()};await{!v.uiState.value.isLoading}}
 private fun clear(){store.clear();await{!ProductEditorTransactions.mutex.isLocked}}
 @Before fun setup():Unit=runBlocking{
  ProductEditorTransactions.generation.incrementAndGet();prefs.edit().clear().putBoolean(ProductEditingPreferences.KEY,false).commit();ProductDraftStore.preferences(app).edit().clear().commit()
  db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build();RetailConstraints.install(db.openHelper.writableDatabase);db.cityDao().insert(CityEntity(1,"Тестовый город"))
  locations=LocationRepository(db.cityDao(),db.shopDao(),app.getSharedPreferences("170-location",0));products=ProductRepository(db.productDao(),db.productShopLinkDao(),db);shops=ShopRepository(db.shopDao(),db.shopDepartmentDao(),locations,db);classifiers=ClassifierRepository(db.productGroupDao(),db.recipientDao(),db.unitDao(),db)
 }
 @After fun close(){clear();locations.closeForTest();db.close()}
 private fun save(v:ProductCardViewModel){v.save();await{v.uiState.value.isSaved}}
 @Test fun missingUnitAndQuantityAreFilledOnlyInCommittedProduct():Unit=runBlocking{
  prefs.edit().putString(ProductCreationDefaults.UNIT,"ед.").commit();val v=card();v.updateName("Новая позиция");assertEquals("",v.uiState.value.defaultUnit);assertEquals("",v.uiState.value.defaultQuantity);assertTrue(db.productDao().getAllSync().isEmpty())
  save(v);val p=db.productDao().getAllSync().single();assertEquals("ед",p.defaultUnit);assertEquals(1.0,p.defaultQuantity!!,0.0)
 }
 @Test fun explicitValuesOverrideMissingUnitFallback():Unit=runBlocking{
  prefs.edit().putString(ProductCreationDefaults.UNIT,"шт").commit();val v=card();v.updateName("Яблоко");v.updateDefaultUnit("кг");v.updateDefaultQuantity("0,75");save(v);val p=db.productDao().getAllSync().single();assertEquals("кг",p.defaultUnit);assertEquals(.75,p.defaultQuantity!!,0.0)
 }
 @Test fun fallbackCanBeDisabledAndAutosaveLeavesRawInputBlank():Unit=runBlocking{
  prefs.edit().putString(ProductCreationDefaults.UNIT,"").putBoolean(ProductEditingPreferences.KEY,true).commit();val v=card();v.updateName("Без единицы");await{v.uiState.value.savedProductId!=null};assertNull(db.productDao().getAllSync().single().defaultUnit);assertEquals("",v.uiState.value.defaultUnit)
 }
 @Test fun globalOnlineDefaultAndHistoricalProductStayIndependent():Unit=runBlocking{
  prefs.edit().putString(ProductCreationDefaults.TYPE,"online").commit();val old=db.productDao().insert(ProductEntity(name="Прежний",purchaseType="offline"));val existing=card(old);assertEquals("offline",existing.uiState.value.purchaseType)
  val v=card();v.updateName("Новый");save(v);assertEquals("online",db.productDao().getAllSync().single{it.id!=old}.purchaseType);assertEquals("offline",db.productDao().getById(old)!!.purchaseType)
 }
 @Test fun onlineOnlyShopOverridesGlobalAndDisallowsOffline():Unit=runBlocking{
  val shop=db.shopDao().insert(ShopEntity(name="Онлайн-магазин"));db.creationPresetDao().setShop(ShopCreationPreset(shop,"online"));val v=card(shop=shop);v.updateName("Кабель");v.updatePurchaseType("offline");save(v)
  assertEquals("online",db.productDao().getAllSync().single().purchaseType);assertEquals(setOf("online"),v.uiState.value.allowedPurchaseTypes)
 }
 @Test fun primaryShopPriorityControlsNewProductPreset():Unit=runBlocking{
  val a=db.shopDao().insert(ShopEntity(name="Онлайн",displayOrder=0));val b=db.shopDao().insert(ShopEntity(name="Офлайн",displayOrder=1));db.creationPresetDao().setShop(ShopCreationPreset(a,"online"));db.creationPresetDao().setShop(ShopCreationPreset(b,"offline"))
  val v=card();await{v.uiState.value.shops.size==2};v.addPendingShopLink(a,null);v.addPendingShopLink(b,null);await{v.uiState.value.purchaseType=="online"};v.movePendingShopLinkUp(v.uiState.value.pendingShopLinks.single{it.shopId==b}.tempId);v.updateName("Выбор магазина");save(v);assertEquals("offline",db.productDao().getAllSync().single().purchaseType)
 }
 @Test fun explicitTypeSurvivesUnrelatedEditsWhenBothAllowed():Unit=runBlocking{
  val v=card();v.updatePurchaseType("online");v.updateName("Явно онлайн");v.updateNote("Заметка");save(v);assertEquals("online",db.productDao().getAllSync().single().purchaseType)
 }
 @Test fun groupPresetAppliesEvenOnImmediateSave():Unit=runBlocking{
  val group=db.productGroupDao().insert(ProductGroupEntity(name="Для питомца"));val recipient=db.recipientDao().insert(RecipientEntity(name="Питомец"));classifiers.defaultRecipient(group,recipient)
  val v=card();v.updateName("Корм");v.updateProductGroup(group);save(v);assertEquals(recipient,db.productDao().getAllSync().single().recipientId)
 }
 @Test fun manualRecipientAndExplicitNoneSurviveGroupChanges():Unit=runBlocking{
  val group=db.productGroupDao().insert(ProductGroupEntity(name="Группа"));val a=db.recipientDao().insert(RecipientEntity(name="Первый"));val b=db.recipientDao().insert(RecipientEntity(name="Второй"));classifiers.defaultRecipient(group,a)
  val v=card();await{v.uiState.value.groups.isNotEmpty()};v.updateProductGroup(group);await{v.uiState.value.recipientId==a};v.updateRecipient(b);v.updateProductGroup(null);v.updateProductGroup(group);v.updateName("Ручной выбор");save(v);assertEquals(b,db.productDao().getAllSync().single().recipientId)
  clear();val next=card();next.updateRecipient(null);next.updateProductGroup(group);next.updateName("Без получателя");save(next);assertNull(db.productDao().getAllSync().single{it.name=="Без получателя"}.recipientId)
 }
 @Test fun removingGroupClearsOnlyAutomaticallyAssignedRecipient():Unit=runBlocking{
  val group=db.productGroupDao().insert(ProductGroupEntity(name="Группа"));val a=db.recipientDao().insert(RecipientEntity(name="Первый"));classifiers.defaultRecipient(group,a);val v=card();v.updateProductGroup(group);await{v.uiState.value.recipientId==a};v.updateProductGroup(null);assertNull(v.uiState.value.recipientId)
 }
 @Test fun deletingReferencesCascadesPresetsAndClearsAutomaticRecipient():Unit=runBlocking{
  val group=db.productGroupDao().insert(ProductGroupEntity(name="Группа"));val recipient=db.recipientDao().insert(RecipientEntity(name="Получатель"));classifiers.defaultRecipient(group,recipient);val shop=db.shopDao().insert(ShopEntity(name="Магазин"));db.creationPresetDao().setShop(ShopCreationPreset(shop,"online"))
  val v=card();v.updateProductGroup(group);await{v.uiState.value.recipientId==recipient};db.recipientDao().delete(db.recipientDao().getById(recipient)!!);await{v.uiState.value.recipientId==null};assertNull(db.creationPresetDao().groups().single().recipientId)
  db.productGroupDao().delete(db.productGroupDao().getById(group)!!);db.shopDao().delete(db.shopDao().getById(shop)!!);assertTrue(db.creationPresetDao().groups().isEmpty());assertTrue(db.creationPresetDao().shops().isEmpty())
 }
 private fun manifest(file:File)=ZipFile(file).use{z->JSONObject(z.getInputStream(z.getEntry("manifest.json")).reader(Charsets.UTF_8).readText())}
 private fun archive(root:JSONObject)=File.createTempFile("170-copy-",".zip",app.cacheDir).also{f->ZipOutputStream(f.outputStream()).use{it.putNextEntry(ZipEntry("manifest.json"));it.write(root.toString().toByteArray(Charsets.UTF_8));it.closeEntry()}}
 @Test fun fullBackupRestoresRulesAndDefaultsAndReadsVersion11():Unit=runBlocking{
  val group=db.productGroupDao().insert(ProductGroupEntity(name="Группа"));val recipient=db.recipientDao().insert(RecipientEntity(name="Получатель"));val shop=db.shopDao().insert(ShopEntity(name="Магазин"));classifiers.defaultRecipient(group,recipient);db.creationPresetDao().setShop(ShopCreationPreset(shop,"online"));prefs.edit().putString(ProductCreationDefaults.UNIT,"ед").putString(ProductCreationDefaults.TYPE,"online").commit()
  val manager=BackupManager(app,db);val file=manager.exportFile();val root=manifest(file);assertEquals(13,root.getInt("version"));manager.importFile(file);assertEquals("online",db.creationPresetDao().shops().single().purchaseMode);assertEquals(recipient,db.creationPresetDao().groups().single().recipientId);assertEquals(ProductCreationDefaults("online","ед"),ProductCreationDefaults.read(prefs))
  root.put("version",11);root.getJSONObject("data").remove("shopCreationPresets");root.getJSONObject("data").remove("groupCreationPresets");val old=archive(root);manager.importFile(old);assertTrue(db.creationPresetDao().shops().isEmpty());assertTrue(db.creationPresetDao().groups().isEmpty());assertNotNull(db.shopDao().getById(shop));manager.discard(old);manager.discard(file)
 }
 @Test fun malformedPresetCopiesRejectBeforeChangingCurrentData():Unit=runBlocking{
  val shop=db.shopDao().insert(ShopEntity(name="Сохранить"));db.creationPresetDao().setShop(ShopCreationPreset(shop,"online"));val manager=BackupManager(app,db);val file=manager.exportFile()
  val edits=listOf<(JSONObject)->Unit>({it.getJSONObject("data").getJSONArray("shopCreationPresets").getJSONObject(0).put("shopId",shop+.5)},{it.getJSONObject("data").getJSONArray("shopCreationPresets").getJSONObject(0).put("shopId",99999)},{it.getJSONObject("data").getJSONArray("shopCreationPresets").getJSONObject(0).put("purchaseMode","unknown")},{it.getJSONObject("data").remove("groupCreationPresets")},{it.getJSONObject("preferences").getJSONObject("shopping_list_prefs").put(ProductCreationDefaults.TYPE,"bad")})
  edits.forEach{edit->val root=manifest(file);edit(root);val bad=archive(root);assertTrue(runCatching{manager.importFile(bad)}.isFailure);assertEquals("Сохранить",db.shopDao().getById(shop)!!.name);assertEquals("online",db.creationPresetDao().shopMode(shop));manager.discard(bad)};manager.discard(file)
 }
 @Test fun standardCatalogueIsIdempotentPreservesAliasesAndNeverAddsShopPlacements():Unit=runBlocking{
  val id=db.productGroupDao().insert(ProductGroupEntity(name="Гигиена",iconKey="eyes",accent="rose",displayOrder=91));val product=db.productDao().insert(ProductEntity(name="Свой товар",productGroupId=id));db.shopDao().insert(ShopEntity(name="Аптека"))
  assertEquals(59,classifiers.addStandardCategories());assertEquals(0,classifiers.addStandardCategories());assertEquals(30,db.productGroupDao().getAllSync().size);assertEquals(30,db.shopDepartmentDao().types().size);assertEquals("eyes",db.productGroupDao().getById(id)!!.iconKey);assertEquals(91,db.productGroupDao().getById(id)!!.displayOrder);assertEquals(id,db.productDao().getById(product)!!.productGroupId);assertTrue(db.shopDepartmentDao().getAllSync().isEmpty())
 }
 @Test fun freshInstallationContainsEditableCategoriesWithoutProductsOrShops(){
  org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null);app.deleteDatabase("spisok_ryadom.db");val fresh=AppDatabase.getInstance(app)
  try{runBlocking{assertEquals(30,fresh.productGroupDao().getAllSync().size);assertEquals(30,fresh.shopDepartmentDao().types().size);assertTrue(fresh.productDao().getAllSync().isEmpty());assertTrue(fresh.shopDao().getAllSync().isEmpty());assertTrue(fresh.shopDepartmentDao().getAllSync().isEmpty())}}
  finally{fresh.close();org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null);app.deleteDatabase("spisok_ryadom.db")}
 }
 @Test fun schema15UpgradePreservesRowsAndAddsValidPresetTables():Unit=runBlocking{
  val name="170-schema15.db";app.deleteDatabase(name);app.openOrCreateDatabase(name,0,null).use{old->val entities=JSONObject(javaClass.getResourceAsStream("/schema-v15.json")!!.reader().readText()).getJSONObject("database").getJSONArray("entities");for(i in 0 until entities.length()){val e=entities.getJSONObject(i);old.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName")));val ix=e.getJSONArray("indices");for(j in 0 until ix.length())old.execSQL(ix.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName")))}
   old.execSQL("INSERT INTO cities(id,name)VALUES(1,'Город')");old.execSQL("INSERT INTO product_groups(id,name,displayOrder,accent,iconKey)VALUES(7,'Гигиена',23,'rose','eyes')");old.execSQL("INSERT INTO products(id,name,productGroupId,purchaseType,isTemporary,stableId)VALUES(9,'Товар',7,'offline',0,'migration-170')");old.execSQL("INSERT INTO shopping_list_entries(id,productId,quantity,unit,isBought,isUrgent,createdAt,updatedAt)VALUES(11,9,3.5,'кг',0,1,100,200)");old.version=15
  }
  val migrated=Room.databaseBuilder(app,AppDatabase::class.java,name).allowMainThreadQueries().addMigrations(AppDatabase.MIGRATION_15_16,AppDatabase.MIGRATION_16_17).build()
  try{assertEquals(17,migrated.openHelper.writableDatabase.version);assertEquals(7L,migrated.productDao().getById(9)!!.productGroupId);assertEquals("eyes",migrated.productGroupDao().getById(7)!!.iconKey);assertEquals(200L,migrated.shoppingListEntryDao().getById(11)!!.updatedAt);assertEquals(3.5,migrated.shoppingListEntryDao().getById(11)!!.quantity,0.0);assertTrue(migrated.creationPresetDao().shops().isEmpty());migrated.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}}
  finally{migrated.close();app.deleteDatabase(name)}
 }
 @Test fun quickPurchaseUsesSameStoreModeAndMissingUnitDefaults():Unit=runBlocking{
  val shop=db.shopDao().insert(ShopEntity(name="Офлайн"));db.creationPresetDao().setShop(ShopCreationPreset(shop,"offline"));prefs.edit().putString(ProductCreationDefaults.TYPE,"online").putString(ProductCreationDefaults.UNIT,"ед").commit()
  val vm=AddToListViewModel(products,shops,ShoppingListRepository(db.shoppingListEntryDao(),db,locations),prefs,shop);store.put("quick",vm);vm.onSearchQueryChanged("Новая покупка");await{vm.uiState.value.isNewProduct};vm.add();await{vm.uiState.value.addedMessage!=null};val product=db.productDao().getAllSync().single();assertEquals("offline",product.purchaseType);assertEquals("ед",product.defaultUnit);assertEquals("ед",db.shoppingListEntryDao().getAllSync().single().unit)
 }
 @Test fun marketConversionPreservesShopPreset():Unit=runBlocking{
  val shop=db.shopDao().insert(ShopEntity(name="Торговое место"));db.creationPresetDao().setShop(ShopCreationPreset(shop,"online"));db.shopBranchDao().insert(ShopBranchEntity(shopId=shop,address="Первый"));db.shopBranchDao().insert(ShopBranchEntity(shopId=shop,address="Второй"));shops.convertToVenue(shop);assertEquals(2,db.creationPresetDao().shops().size);assertTrue(db.creationPresetDao().shops().all{it.purchaseMode=="online"&&it.shopId!=shop})
 }
 @Test fun unitDictionaryRenameAndDeleteKeepFallbackPreferenceConsistent():Unit=runBlocking{
  val id=db.unitDao().insert(UnitEntity(name="шт"));val v=SettingsViewModel(BackupManager(app,db),classifiers,products,shops,prefs,app);store.put("settings",v);v.renameUnit(db.unitDao().getById(id)!!,"ед");await{ProductCreationDefaults.read(prefs).missingUnit=="ед"};v.deleteUnit(db.unitDao().getById(id)!!);await{ProductCreationDefaults.read(prefs).missingUnit.isEmpty()}
 }
 @Test fun iconLibraryCoversEveryStandardCategoryAndDistinctRecipients(){
  (StandardCategories.groups+StandardCategories.departments).forEach{assertNotNull(it.name,appearanceIcon(it.icon))};val options=appearanceOptions();assertEquals(options.size,options.map{it.first}.distinct().size);val people=listOf("man","woman","person","person2","person3","person4","family","shared","dog","cat");assertEquals(people.size,people.map{appearanceIcon(it)!!.name}.distinct().size)
 }
 @Test fun providedConvertedArchiveImportsAndReexportsWithAllReferencesValid():Unit=runBlocking{
  val supplied=System.getProperty("spisok.audit170.input")?.let{File(it)}
  val manager=BackupManager(app,db);val file=supplied?:manager.exportFile();val expected=manifest(file).getJSONObject("data");val beforeIds=(0 until expected.getJSONArray("products").length()).map{expected.getJSONArray("products").getJSONObject(it).getLong("id")}.toSet()
  manager.importFile(file);assertEquals(beforeIds,db.productDao().getAllSync().map{it.id}.toSet());assertEquals(expected.getJSONArray("shoppingListEntries").length(),db.shoppingListEntryDao().getAllSync().size);assertEquals(expected.optJSONArray("shopCreationPresets")?.length()?:0,db.creationPresetDao().shops().size);assertEquals(expected.optJSONArray("groupCreationPresets")?.length()?:0,db.creationPresetDao().groups().size);db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())};val exported=manager.exportFile();assertEquals(13,manifest(exported).getInt("version"));manager.discard(exported);if(supplied==null)manager.discard(file)
 }
}
