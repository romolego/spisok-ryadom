package com.spisokryadom.app

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.backup.BackupManager
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.db.RetailConstraints
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.ui.shopcard.ShopCardViewModel
import com.spisokryadom.app.ui.shoplist.ShopListViewModel
import com.spisokryadom.app.ui.productcard.ProductCardViewModel
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.*
import java.io.File
import java.util.zip.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class SettingsCities280DataTest {
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 private lateinit var db:AppDatabase
 private lateinit var locations:LocationRepository
 private lateinit var shops:ShopRepository
 private lateinit var products:ProductRepository
 private lateinit var basket:ShoppingListRepository
 private val models=ViewModelStore()
 @Before fun setup():Unit=runBlocking{
  db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build()
  RetailConstraints.install(db.openHelper.writableDatabase)
  db.cityDao().insert(CityEntity(1,"Красноярск"));db.cityDao().insert(CityEntity(2,"Иркутск"));db.cityDao().insert(CityEntity(3,"Томск"))
  val prefs=app.getSharedPreferences("280-location",0);prefs.edit().clear().commit()
  locations=LocationRepository(db.cityDao(),db.shopDao(),prefs)
  shops=ShopRepository(db.shopDao(),db.shopDepartmentDao(),locations,db)
  products=ProductRepository(db.productDao(),db.productShopLinkDao(),db)
  basket=ShoppingListRepository(db.shoppingListEntryDao(),db,locations)
 }
 private fun await(check:()->Boolean){val end=System.nanoTime()+20_000_000_000;while(!check()&&System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5)};assertTrue(check())}
 @After fun close(){models.clear();await{!ProductEditorTransactions.mutex.isLocked};locations.closeForTest();db.close()}
 private suspend fun original():Long=shops.saveWithDepartments(ShopEntity(name="Лента",isFavorite=true),listOf(ShopDepartmentEntity(shopId=0,name="Продукты")),listOf(ShopBranchEntity(shopId=0,address="Мира, 1")))
 private suspend fun draft(id:Long)=ShopCityDraft(db.shopDao().getById(id)!!,db.shopDepartmentDao().getByShopIdSync(id),shops.branches(id))
 private fun root(file:File)=ZipFile(file).use{z->JSONObject(z.getInputStream(z.getEntry("manifest.json")).reader().readText())}
 private fun zip(j:JSONObject)=File(app.cacheDir,"changed280-"+java.util.UUID.randomUUID()+".zip").also{f->ZipOutputStream(f.outputStream()).use{z->z.putNextEntry(ZipEntry("manifest.json"));z.write(j.toString().toByteArray());z.closeEntry()}}

 @Test fun cityDraftsKeepOriginalPhysicalIdsAndCancelWritesNothing(){
  val id=runBlocking{original()};val point=runBlocking{shops.branches(id).single()}
  val vm=ShopCardViewModel(id,shops);models.put("card",vm);await{!vm.uiState.value.isLoading}
  vm.updateCity(2);await{!vm.uiState.value.isLoading&&vm.uiState.value.cityId==2L}
  assertTrue(vm.uiState.value.branches.isEmpty());assertTrue(vm.uiState.value.isNew)
  vm.updateBranches(listOf(ShopBranchEntity(id=-7,shopId=0,address="Ленина, 20")))
  vm.updateCity(1);await{!vm.uiState.value.isLoading&&vm.uiState.value.cityId==1L}
  assertEquals(listOf(point),vm.uiState.value.branches)
  vm.updateCity(2);await{!vm.uiState.value.isLoading&&vm.uiState.value.cityId==2L}
  assertEquals("Ленина, 20",vm.uiState.value.branches.single().address)
  assertEquals(listOf(point),runBlocking{shops.branches(id)});assertEquals(1,runBlocking{db.shopDao().getAllSync().size})
  assertEquals(1L,runBlocking{locations.currentId()})
 }
 @Test fun savingVisitedCitiesCreatesLocalAddressesAndReopeningFindsThem(){
  val id=runBlocking{original()};val oldPoint=runBlocking{shops.branches(id).single()}
  val vm=ShopCardViewModel(id,shops);models.put("card",vm);await{!vm.uiState.value.isLoading}
  vm.updateCity(2);await{!vm.uiState.value.isLoading&&vm.uiState.value.cityId==2L}
  vm.updateBranches(listOf(ShopBranchEntity(id=-1,shopId=0,address="Иркутская, 8")))
  vm.save();await{vm.uiState.value.isSaved||vm.uiState.value.error!=null};assertNull(vm.uiState.value.error)
  val all=runBlocking{shops.cityProfiles(id)};assertEquals(2,all.size);assertTrue(all.all{it.cityGroupKey.isNotEmpty()});assertEquals(1,all.map{it.cityGroupKey}.distinct().size)
  assertEquals(oldPoint,runBlocking{shops.branches(id).single()})
  val peer=all.single{it.cityId==2L};assertNotEquals(id,peer.id)
  assertEquals("Иркутская, 8",runBlocking{shops.branches(peer.id).single().address})
  val reopened=ShopCardViewModel(peer.id,shops);models.put("reopened",reopened);await{!reopened.uiState.value.isLoading}
  reopened.updateCity(1);await{!reopened.uiState.value.isLoading&&reopened.uiState.value.cityId==1L}
  assertEquals(oldPoint,reopened.uiState.value.branches.single());assertFalse(reopened.uiState.value.isNew)
  assertEquals(1L,runBlocking{locations.currentId()})
 }
 @Test fun emptyNewCityDoesNotCreateGhostOrMoveTheOriginal(){
  val id=runBlocking{original()};val before=runBlocking{shops.branches(id)}
  val vm=ShopCardViewModel(id,shops);models.put("card",vm);await{!vm.uiState.value.isLoading}
  vm.updateCity(2);await{!vm.uiState.value.isLoading&&vm.uiState.value.cityId==2L};vm.save();await{vm.uiState.value.isSaved}
  assertEquals(1,runBlocking{db.shopDao().getAllSync().size});assertEquals(1L,runBlocking{db.shopDao().getById(id)!!.cityId});assertEquals(before,runBlocking{shops.branches(id)})
 }
 @Test fun cityProfileSaveIsAtomicAndNeverMergesNamesOrForeignVenueIds():Unit=runBlocking{
  val id=original();val before=draft(id);val other=db.shopDao().insert(ShopEntity(name="Лента",cityId=2))
  val venue=shops.ensureVenue(1,"ТЦ в Красноярске")
  val invalid=ShopCityDraft(ShopEntity(name="Лента",cityId=2),emptyList(),listOf(ShopBranchEntity(shopId=0,address="",venueId=venue)))
  assertTrue(runCatching{shops.saveCityProfiles(id,"atomic280",listOf(before.copy(shop=before.shop.copy(name="Не сохранять")),invalid),2)}.isFailure)
  assertEquals(before.shop,db.shopDao().getById(id));assertEquals(2,db.shopDao().getAllSync().size)
  val local=invalid.copy(branches=listOf(ShopBranchEntity(shopId=0,address="Новый адрес")))
  val peer=shops.saveCityProfiles(id,"atomic280",listOf(before,local),2)
  assertNotEquals(other,peer);assertEquals("",db.shopDao().getById(other)!!.cityGroupKey)
  assertEquals(setOf(id,peer),shops.cityProfiles(id).map{it.id}.toSet())
  assertTrue(runCatching{db.shopDao().insert(ShopEntity(name="Запрещённый дубль",cityId=2,cityGroupKey="atomic280"))}.isFailure)
  assertTrue(runCatching{db.shopDao().update(db.shopDao().getById(peer)!!.copy(cityId=1))}.isFailure)
  assertEquals(2L,db.shopDao().getById(peer)!!.cityId)
 }
 @Test fun localCatalogueInheritancePreservesOldLinksPointsAndPurchases():Unit=runBlocking{
  val id=original();val original=draft(id);val point=original.branches.single()
  val product=products.saveWithLinks(ProductEntity(name="Банан"),listOf(ProductShopLinkEntity(productId=0,shopId=id,departmentId=original.departments.single().id)),mapOf(id to setOf(point.id)))
  val bought=basket.addProduct(product);basket.markBought(bought);val oldRow=basket.getEntryById(bought);val oldLink=products.getShopLinksForProductSync(product).single()
  val peer=shops.saveCityProfiles(id,"catalogue280",listOf(original,ShopCityDraft(ShopEntity(name="Лента",cityId=2),original.departments.map{it.copy(id=0,shopId=0)},listOf(ShopBranchEntity(shopId=0,address="Адрес в Иркутске")))),2)
  assertEquals(oldLink,products.getShopLinksForProductSync(product).single{it.shopId==id});assertEquals(oldRow,basket.getEntryById(bought));assertEquals(point,shops.branches(id).single())
  val localLink=products.getShopLinksForProductSync(product).single{it.shopId==peer};assertFalse(localLink.restrictBranches)
  assertEquals(peer,db.shopDepartmentDao().getById(localLink.departmentId!!)!!.shopId)
  basket.changeShoppingCity(2)
  val added=basket.addProduct(product);val row=basket.getEntryById(added)!!;assertEquals(peer,row.assignedShopId);assertEquals(shops.branches(peer).single().id,row.assignedBranchId)
 }
 @Test fun switchingCityReassignsRegularPendingAndRecoversOnReturnWithoutForeignDefault():Unit=runBlocking{
  val old=original();val local=shops.saveWithDepartments(ShopEntity(name="Местный",cityId=2),emptyList(),listOf(ShopBranchEntity(shopId=0,address="Местный адрес")))
  val linked=products.saveWithLinks(ProductEntity(name="Молоко"),listOf(ProductShopLinkEntity(productId=0,shopId=old),ProductShopLinkEntity(productId=0,shopId=local,priority=2)))
  val unmatched=products.saveWithLinks(ProductEntity(name="Хлеб"),listOf(ProductShopLinkEntity(productId=0,shopId=old)))
  val first=basket.addProduct(linked,2.5);val second=basket.addProduct(unmatched,4.0)
  basket.changeShoppingCity(2)
  assertEquals(local,basket.getEntryById(first)!!.assignedShopId);assertNull(basket.getEntryById(second)!!.assignedShopId);assertEquals(4.0,basket.getEntryById(second)!!.quantity,0.0);assertEquals(2L,basket.getEntryById(second)!!.awaitingShopCityId)
  val merged=basket.addProduct(unmatched);assertEquals(second,merged);assertNull(basket.getEntryById(merged)!!.assignedShopId)
  basket.changeShoppingCity(1)
  assertEquals(old,basket.getEntryById(first)!!.assignedShopId);assertEquals(old,basket.getEntryById(second)!!.assignedShopId);assertEquals(5.0,basket.getEntryById(second)!!.quantity,0.0)
  assertEquals(2,db.productDao().getAllSync().size)
 }
 @Test fun switchingCityKeepsBoughtExplicitNoneAndSeparateListPlaces():Unit=runBlocking{
  val id=original();val p=products.saveWithLinks(ProductEntity(name="Товар"),listOf(ProductShopLinkEntity(productId=0,shopId=id)))
  val bought=basket.addProduct(p);basket.markBought(bought)
  val noShop=basket.addProduct(p,choice=ShopChoice.NONE)
  val goals=GoalListRepository(db,basket);val goal=goals.create("Поездка");val goalRow=basket.addProduct(p,listId=goal)
  val preserved=listOf(bought,noShop,goalRow).associateWith{basket.getEntryById(it)}
  basket.changeShoppingCity(2)
  preserved.forEach{(id,row)->assertEquals(row,basket.getEntryById(id))}
  assertEquals(2L,locations.currentId())
  assertTrue(runCatching{basket.changeShoppingCity(999)}.isFailure);assertEquals(2L,locations.currentId())
 }
 @Test fun shopBatchChangesAreAtomicCityScopedAndPreserveRouteAndPurchaseIdentity():Unit=runBlocking{
  val a=original();val b=db.shopDao().insert(ShopEntity(name="Второй",displayOrder=9,manualOrderOverride=true,boostUrgentShop=false));val foreign=db.shopDao().insert(ShopEntity(name="Чужой",cityId=2))
  val p=products.saveWithLinks(ProductEntity(name="Покупка"),listOf(ProductShopLinkEntity(productId=0,shopId=a)));val row=basket.getEntryById(basket.addProduct(p))
  shops.changeSettings(setOf(a,b),ShopBatchChange.Favorite(true));assertTrue(db.shopDao().getById(b)!!.isFavorite)
  val order=db.shopDao().getById(b)!!.favoriteOrder;shops.changeSettings(setOf(a,b),ShopBatchChange.Favorite(true));assertEquals(order,db.shopDao().getById(b)!!.favoriteOrder)
  shops.changeSettings(setOf(a,b),ShopBatchChange.PurchaseMode("online"));assertEquals("online",shops.purchaseMode(a));assertEquals("online",shops.purchaseMode(b))
  shops.changeSettings(setOf(a,b),ShopBatchChange.Urgency(false,true));assertFalse(db.shopDao().getById(b)!!.showUrgentInCollapsed!!);assertFalse(db.shopDao().getById(b)!!.manualOrderOverride);assertEquals(9,db.shopDao().getById(b)!!.displayOrder)
  val before=db.shopDao().getAllSync();assertTrue(runCatching{shops.changeSettings(setOf(a,foreign),ShopBatchChange.Favorite(false))}.isFailure);assertEquals(before,db.shopDao().getAllSync())
  assertTrue(runCatching{shops.changeSettings(setOf(a,999),ShopBatchChange.Urgency(null,null))}.isFailure);assertEquals(before,db.shopDao().getAllSync());assertEquals(row,basket.getEntryById(row!!.id))
 }
 @Test fun favoriteShopFilterUsesOnlyLocalPinsAndKeepsEmptyVenueOutOfFilteredResult(){
  val ids=runBlocking{val a=original();val b=db.shopDao().insert(ShopEntity(name="Другой"));db.shopDao().insert(ShopEntity(name="Чужой",cityId=2,isFavorite=true));shops.ensureVenue(1,"Пустой ТЦ");a to b}
  val vm=ShopListViewModel(shops);models.put("shops",vm);await{!vm.uiState.value.isLoading&&vm.uiState.value.shops.size==2}
  vm.toggleFavorites();await{vm.uiState.value.favoritesOnly&&vm.uiState.value.shops.size==1};assertEquals(ids.first,vm.uiState.value.shops.single().id);assertTrue(vm.uiState.value.venues.isEmpty())
  vm.toggleFavorites();await{!vm.uiState.value.favoritesOnly&&vm.uiState.value.shops.size==2}
 }
 @Test fun full23RoundTripAndOld22PreserveExplicitCityIdentityAndMixedProduct():Unit=runBlocking{
  val id=original();val before=draft(id);shops.saveCityProfiles(id,"backup280",listOf(before,ShopCityDraft(ShopEntity(name="Лента",cityId=2),emptyList(),listOf(ShopBranchEntity(shopId=0,address="Другой город")))),2)
  val p=products.saveWithLinks(ProductEntity(name="Смешанный",purchaseType="both"),emptyList())
  val manager=BackupManager(app,db);val file=manager.exportFile();val expected=db.shopDao().getAllSync()
  try{
   assertEquals(24,root(file).getInt("version"));manager.importFile(file);assertEquals(expected,db.shopDao().getAllSync());assertEquals("both",db.productDao().getById(p)!!.purchaseType)
   val old=root(file).put("version",22);val data=old.getJSONObject("data");val rows=data.getJSONArray("shops");for(i in 0 until rows.length())rows.getJSONObject(i).remove("cityGroupKey")
   data.getJSONArray("products").getJSONObject(0).put("purchaseType","offline");val legacy=zip(old)
   try{manager.importFile(legacy);assertTrue(db.shopDao().getAllSync().all{it.cityGroupKey.isEmpty()});assertEquals("offline",db.productDao().getById(p)!!.purchaseType)}finally{manager.discard(legacy)}
  }finally{manager.discard(file)}
 }
 @Test fun malformedCityIdentityFailsBeforeReplacingCurrentData():Unit=runBlocking{
  original();val manager=BackupManager(app,db);val file=manager.exportFile();val before=db.shopDao().getAllSync()
  try{for(value in listOf<Any>(7,true,JSONObject.NULL,"bad key", "x".repeat(81))){val j=root(file);j.getJSONObject("data").getJSONArray("shops").getJSONObject(0).put("cityGroupKey",value);val bad=zip(j);try{assertTrue(runCatching{manager.importFile(bad)}.isFailure);assertEquals(before,db.shopDao().getAllSync())}finally{manager.discard(bad)}}}finally{manager.discard(file)}
 }
 private fun productCard(id:Long=-1,shop:Long=-1):ProductCardViewModel{
  val prefs=app.getSharedPreferences("280-product-edit",0);prefs.edit().clear().putBoolean(com.spisokryadom.app.util.ProductEditingPreferences.KEY,false).commit()
  val classifiers=ClassifierRepository(db.productGroupDao(),db.recipientDao(),db.unitDao(),db)
  val vm=ProductCardViewModel(id,shop,products,shops,classifiers,editingPreferences=prefs)
  models.put("product-card",vm);await{!vm.uiState.value.isLoading};return vm
 }
 @Test fun explicitMixedFormatSurvivesShopPresetsAndOtherProductEdits(){
  val id=runBlocking{original().also{shops.changeSettings(setOf(it),ShopBatchChange.PurchaseMode("online"))}}
  val vm=productCard(shop=id);await{vm.uiState.value.purchaseType=="online"}
  vm.updateName("Смешанная покупка");vm.updatePurchaseType("both");vm.updateDefaultQuantity("2");vm.updateDefaultUnit("шт")
  runBlocking{shops.changeSettings(setOf(id),ShopBatchChange.PurchaseMode("offline"))}
  await{vm.uiState.value.purchaseTypeManuallySet};assertEquals("both",vm.uiState.value.purchaseType)
  vm.save();await{vm.uiState.value.isSaved||vm.uiState.value.error!=null};assertNull(vm.uiState.value.error)
  assertEquals("both",runBlocking{db.productDao().getAllSync().single().purchaseType})
 }
 @Test fun stalePreselectedShopNeverAttachesToNewProductInAnotherCity(){
  val old=runBlocking{original()};locations.select(2);await{locations.activeCity.value.id==2L}
  val vm=productCard(shop=old);await{!vm.uiState.value.isLoading&&vm.uiState.value.shops.isEmpty()}
  assertTrue(vm.uiState.value.pendingShopLinks.isEmpty());vm.updateName("Новый местный товар");vm.save();await{vm.uiState.value.isSaved}
  val p=runBlocking{db.productDao().getAllSync().single()};assertTrue(runBlocking{products.getShopLinksForProductSync(p.id).isEmpty()})
  assertNull(runBlocking{basket.getEntryById(basket.addProduct(p.id))!!.assignedShopId})
 }
 @Test fun confirmingSinglePointShopClearsHistoricalPointRestrictions(){
  val id=runBlocking{original()};val p=runBlocking{products.saveWithLinks(ProductEntity(name="Одна точка"),listOf(ProductShopLinkEntity(productId=0,shopId=id)),mapOf(id to setOf(shops.branches(id).single().id)))}
  val vm=productCard(p);await{vm.uiState.value.branches.size==1&&vm.uiState.value.branchSelections[id]!=null}
  vm.configureShop(id,null,emptySet(),emptyList());assertFalse(vm.uiState.value.branchSelections.containsKey(id))
  vm.save();await{vm.uiState.value.isSaved};assertFalse(runBlocking{products.getShopLinksForProductSync(p).single().restrictBranches});assertTrue(runBlocking{db.shopBranchDao().availability(p).isEmpty()})
 }
 @Test fun explicitCityFamilySharesDepartmentsWithoutGuessingOtherStoreNames():Unit=runBlocking{
  val id=original();val root=draft(id);val peer=shops.saveCityProfiles(id,"departments280",listOf(root,ShopCityDraft(ShopEntity(name="Лента",cityId=2),root.departments.map{it.copy(id=0,shopId=0)},listOf(ShopBranchEntity(shopId=0,address="Иркутский адрес")))),2)
  val separate=db.shopDao().insert(ShopEntity(name="Лента",cityId=3))
  val firstIds=db.shopDepartmentDao().getByShopIdSync(id).map{it.id};val peerIds=db.shopDepartmentDao().getByShopIdSync(peer).map{it.id}
  val type=shops.createDepartmentType("Гигиена");val batch=BatchRepository(db,products,basket)
  batch.addDepartments(setOf(id),setOf(type),1)
  assertEquals(setOf("Гигиена","Продукты"),db.shopDepartmentDao().getByShopIdSync(peer).map{it.name}.toSet())
  assertTrue(db.shopDepartmentDao().getByShopIdSync(id).map{it.id}.containsAll(firstIds));assertTrue(db.shopDepartmentDao().getByShopIdSync(peer).map{it.id}.containsAll(peerIds));assertTrue(db.shopDepartmentDao().getByShopIdSync(separate).isEmpty())
 }
 @Test fun staleLockedAddPickerFallsBackToCurrentCityBeforeCreatingAnything(){
  val id=runBlocking{original()};locations.select(2);await{locations.activeCity.value.id==2L}
  val prefs=app.getSharedPreferences("280-picker",0);prefs.edit().clear().commit()
  val vm=com.spisokryadom.app.ui.addtolist.AddToListViewModel(products,shops,basket,prefs,id,runBlocking{shops.branches(id).single().id});models.put("picker",vm)
  await{!vm.uiState.value.isShopLocked&&vm.uiState.value.selectedShopId== -1L}
  vm.onSearchQueryChanged("Иркутская новая покупка");vm.quantity("1");await{!vm.uiState.value.isSearching&&vm.uiState.value.isNewProduct}
  vm.add();await{vm.uiState.value.addedMessage!=null||vm.uiState.value.error!=null};assertNull(vm.uiState.value.error)
  val row=runBlocking{db.shoppingListEntryDao().getAllSync().single()};assertNull(row.assignedShopId);assertNull(row.assignedBranchId)
  val p=runBlocking{db.productDao().getById(row.productId)!!};assertEquals("Иркутская новая покупка",p.name);assertTrue(runBlocking{products.getShopLinksForProductSync(p.id).isEmpty()})
 }
 @Test fun commonDepartmentAndNetworkDraftsFollowTheLastEditAcrossCities(){
  val id=runBlocking{original()};val network=runBlocking{shops.ensureNetwork("Общая сеть")}
  runBlocking{val root=draft(id);shops.saveCityProfiles(id,"sharedDraft280",listOf(root.copy(shop=root.shop.copy(networkId=network)),ShopCityDraft(ShopEntity(name="Лента",cityId=2,networkId=network),root.departments.map{it.copy(id=0,shopId=0)},listOf(ShopBranchEntity(shopId=0,address="Другой адрес")))),2)}
  val vm=ShopCardViewModel(id,shops);models.put("shared",vm);await{!vm.uiState.value.isLoading}
  vm.updateNetworkName("Первое название");vm.addDepartments(listOf("Гигиена"))
  vm.updateCity(2);await{!vm.uiState.value.isLoading&&vm.uiState.value.cityId==2L}
  assertEquals("Первое название",vm.uiState.value.networkName);assertEquals(setOf("Продукты","Гигиена"),vm.uiState.value.departments.map{it.name}.toSet())
  vm.updateNetworkName("Последнее название");vm.deleteDepartment(vm.uiState.value.departments.single{it.name=="Продукты"})
  vm.updateCity(1);await{!vm.uiState.value.isLoading&&vm.uiState.value.cityId==1L}
  assertEquals("Последнее название",vm.uiState.value.networkName);assertEquals(listOf("Гигиена"),vm.uiState.value.departments.map{it.name})
  vm.save();await{vm.uiState.value.isSaved||vm.uiState.value.error!=null};assertNull(vm.uiState.value.error)
  assertEquals("Последнее название",runBlocking{db.retailStructureDao().networks().single().name})
  runBlocking{shops.cityProfiles(id).forEach{assertEquals(listOf("Гигиена"),db.shopDepartmentDao().getByShopIdSync(it.id).map{it.name})}}
 }
 @Test fun migration24PreservesTheGraphAndDoesNotInferSameNameCities():Unit=runBlocking{
  val name="migration280.db";app.deleteDatabase(name)
  val schema=File("schemas/com.spisokryadom.app.data.db.AppDatabase/24.json")
  app.openOrCreateDatabase(name,0,null).use{old->
   val entities=JSONObject(schema.readText()).getJSONObject("database").getJSONArray("entities")
   for(i in 0 until entities.length()){val e=entities.getJSONObject(i);old.execSQL(e.getString("createSql").replace("${'$'}{TABLE_NAME}",e.getString("tableName")));val indices=e.getJSONArray("indices");for(j in 0 until indices.length())old.execSQL(indices.getJSONObject(j).getString("createSql").replace("${'$'}{TABLE_NAME}",e.getString("tableName")))}
   old.execSQL("INSERT INTO cities(id,name) VALUES(1,'Красноярск'),(2,'Иркутск')")
   old.execSQL("INSERT INTO shops(id,name,cityId,displayOrder,manualOrderOverride,shoppingBackground,iconKey,purchaseBackground,isFavorite,favoriteOrder) VALUES(7,'Лента',1,13,1,'rose','store','auto',1,3),(8,'Лента',2,4,0,'','','',0,0)")
   old.execSQL("INSERT INTO shop_branches(id,shopId,address,displayOrder,label) VALUES(9,7,'Мира 1',1,'')");old.version=24
  }
  val migrated=Room.databaseBuilder(app,AppDatabase::class.java,name).allowMainThreadQueries().addMigrations(AppDatabase.MIGRATION_24_25,AppDatabase.MIGRATION_25_26).build()
  try{assertEquals(26,migrated.openHelper.writableDatabase.version);assertEquals(setOf(7L,8L),migrated.shopDao().getAllSync().map{it.id}.toSet());assertTrue(migrated.shopDao().getAllSync().all{it.cityGroupKey.isEmpty()});assertEquals(3,migrated.shopDao().getById(7)!!.favoriteOrder);assertEquals(7L,migrated.shopBranchDao().get(9)!!.shopId);migrated.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}}finally{migrated.close();app.deleteDatabase(name)}
 }
}
