package com.spisokryadom.app

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.backup.*
import com.spisokryadom.app.data.db.*
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.ui.productcard.ProductCardViewModel
import com.spisokryadom.app.util.ProductEditingPreferences
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
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
class ProductVariants290DataTest{
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 private lateinit var db:AppDatabase;private lateinit var locations:LocationRepository
 private lateinit var products:ProductRepository;private lateinit var basket:ShoppingListRepository
 private val models=ViewModelStore()
 @Before fun setup():Unit=runBlocking{
  db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build();RetailConstraints.install(db.openHelper.writableDatabase)
  db.cityDao().insert(CityEntity(1,"Красноярск"));db.cityDao().insert(CityEntity(2,"Петербург"))
  val prefs=app.getSharedPreferences("290-location",0);prefs.edit().clear().commit()
  locations=LocationRepository(db.cityDao(),db.shopDao(),prefs)
  products=ProductRepository(db.productDao(),db.productShopLinkDao(),db);basket=ShoppingListRepository(db.shoppingListEntryDao(),db,locations)
 }
 private fun await(check:()->Boolean){val until=System.nanoTime()+25_000_000_000;while(!check()&&System.nanoTime()<until){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5)};assertTrue(check())}
 @After fun close(){models.clear();await{!ProductEditorTransactions.mutex.isLocked};locations.closeForTest();db.close()}
 private suspend fun family():Pair<Long,Long>{val a=db.productDao().insert(ProductEntity(name="Огурцы солёные",defaultQuantity=2.0,defaultUnit="банка"));return a to products.variants.create(a,"Марка Б")}
 private fun image(n:String)=File(app.filesDir,"product_photos/290-$n.jpg").also{it.parentFile!!.mkdirs();it.writeBytes(byteArrayOf(1,2,3,4))}.absolutePath
 private suspend fun photos(id:Long,count:Int){val rows=(0 until count).map{ProductPhotoEntity(id,it,image("$id-$it"),"Подпись $it")};db.withTransaction{products.variants.savePhotos(id,rows);db.productDao().update(db.productDao().getById(id)!!.copy(photoUri=rows.firstOrNull()?.uri))}}
 private fun manifest(file:File)=ZipFile(file).use{z->JSONObject(z.getInputStream(z.getEntry("manifest.json")).reader().readText())}
 private fun zip(root:JSONObject,source:File?=null)=File(app.cacheDir,"tamper290-${java.util.UUID.randomUUID()}.zip").also{f->ZipOutputStream(f.outputStream()).use{z->
  if(source!=null)ZipFile(source).use{old->old.entries().asSequence().filter{it.name!="manifest.json"}.forEach{e->z.putNextEntry(ZipEntry(e.name));old.getInputStream(e).use{it.copyTo(z)};z.closeEntry()}}
  z.putNextEntry(ZipEntry("manifest.json"));z.write(root.toString().toByteArray());z.closeEntry()
 }}


 @Test fun familyHasOneCatalogueItemAndKeepsOriginalPurchaseIdentity():Unit=runBlocking{
  val (a,b)=family();val row=basket.addProduct(a,exactVariant=true)
  assertEquals(listOf(a),products.getAllProducts().first().map{it.id});assertEquals(setOf(a,b),db.productDao().getAllSync().map{it.id}.toSet())
  assertEquals(a,db.shoppingListEntryDao().getById(row)!!.productId);assertEquals(2,products.variants.options(a,1).size)
  val solo=db.productDao().insert(ProductEntity(name="Без вариантов"));val unchanged=db.productDao().getAllSync()
  assertTrue(runCatching{products.variants.create(solo,"  ОСНОВНОЙ ВАРИАНТ ")}.isFailure)
  assertNull(db.productVariantDao().variant(solo));assertEquals(unchanged,db.productDao().getAllSync())
 }
 @Test fun exactOutletAndCityVisibilityUseOnlyEligibleVariant():Unit=runBlocking{
  val (a,b)=family();val local=db.shopDao().insert(ShopEntity(name="Сеть",cityId=1))
  val pointA=db.shopBranchDao().insert(ShopBranchEntity(shopId=local,address="Первый адрес"))
  val pointB=db.shopBranchDao().insert(ShopBranchEntity(shopId=local,address="Второй адрес"))
  products.saveWithLinks(db.productDao().getById(a)!!,listOf(ProductShopLinkEntity(productId=a,shopId=local)),availability=mapOf(local to setOf(pointA)))
  products.saveWithLinks(db.productDao().getById(b)!!.copy(defaultQuantity=.5,defaultUnit="кг"),listOf(ProductShopLinkEntity(productId=b,shopId=local)),availability=mapOf(local to setOf(pointB)))
  products.variants.configureCity(a,1,listOf(a to true,b to true))
  val second=basket.addProduct(a,choice=ShopChoice.EXPLICIT,shopId=local,branchId=pointB)
  val neighbor=basket.addProduct(a,choice=ShopChoice.EXPLICIT,shopId=local,branchId=pointA)
  assertEquals(b,db.shoppingListEntryDao().getById(second)!!.productId);assertEquals(pointB,db.shoppingListEntryDao().getById(second)!!.assignedBranchId)
  val shops=ShopRepository(db.shopDao(),db.shopDepartmentDao(),locations,db)
  val prefs=app.getSharedPreferences("290-exact-point",0);prefs.edit().clear().commit()
  val vm=com.spisokryadom.app.ui.addtolist.AddToListViewModel(products,shops,basket,prefs,preselectedShopId=local,preselectedBranchId=pointB);models.put("point-picker",vm)
  await{vm.uiState.value.searchResults.any{it.id==a}}
  vm.selectProduct(db.productDao().getById(a)!!);await{!vm.uiState.value.isLoading&&vm.uiState.value.selectedProduct?.id==b}
  vm.quickAdd(db.productDao().getById(a)!!);await{!vm.uiState.value.isLoading&&vm.uiState.value.browseMessage!=null}
  assertEquals(1.0,db.shoppingListEntryDao().getById(second)!!.quantity,0.0)
  val untouched=db.shoppingListEntryDao().getById(neighbor)!!.copy(updatedAt=System.currentTimeMillis()+1000);db.shoppingListEntryDao().update(untouched)
  vm.quickDecrease(db.productDao().getById(a)!!);await{!vm.uiState.value.isLoading}
  assertEquals(.5,db.shoppingListEntryDao().getById(second)!!.quantity,0.0);assertEquals(untouched,db.shoppingListEntryDao().getById(neighbor))
  assertEquals(a,products.variants.resolve(a,1).id)

  val hidden=db.shopDao().insert(ShopEntity(name="Только отключённый вариант",cityId=1))
  val remote=db.shopDao().insert(ShopEntity(name="Другой город",cityId=2))
  products.ensureShopLink(b,hidden);products.ensureShopLink(b,remote)
  val facts=db.shoppingListEntryDao().getAllSync()
  products.variants.configureCity(a,1,listOf(a to true,b to false))
  val links=products.getCatalogueShopLinks().first()
  assertTrue(links.none{it.productId==a&&it.shopId==hidden});assertTrue(links.any{it.productId==a&&it.shopId==remote})
  val available=products.observeCatalogueAvailability().first()
  assertTrue(available.any{it.productId==a&&it.branchId==pointA});assertTrue(available.none{it.productId==a&&it.branchId==pointB})
  await{vm.uiState.value.searchResults.none{it.id==a}}
  assertTrue(products.getShopLinksForProductSync(b).any{it.shopId==hidden})
  assertTrue(runCatching{basket.addProduct(a,choice=ShopChoice.EXPLICIT,shopId=local,branchId=pointB)}.isFailure)
  assertEquals(facts,db.shoppingListEntryDao().getAllSync())
  products.variants.configureCity(a,1,listOf(a to true,b to true));await{vm.uiState.value.searchResults.any{it.id==a}}
 }
 @Test fun commonFieldsPropagateButVariantFieldsAndShopsStayIndependent():Unit=runBlocking{
  val (a,b)=family();val group=db.productGroupDao().insert(ProductGroupEntity(name="Продукты"));val recipient=db.recipientDao().insert(RecipientEntity(name="Общее"))
  db.productDao().update(db.productDao().getById(b)!!.copy(name="Огурцы",productGroupId=group,recipientId=recipient,isFavorite=true,accent="rose",defaultUnit="кг",priceValue=75.0,purchaseType="both"))
  val root=db.productDao().getById(a)!!;assertEquals("Огурцы",root.name);assertEquals(group,root.productGroupId);assertEquals(recipient,root.recipientId);assertTrue(root.isFavorite);assertEquals("банка",root.defaultUnit);assertNull(root.priceValue);assertEquals("offline",root.purchaseType)
  val shop=db.shopDao().insert(ShopEntity(name="Красный Яр"));products.ensureShopLink(b,shop)
  assertTrue(products.getShopLinksForProductSync(a).isEmpty());assertEquals(a,products.getCatalogueShopLinks().first().single().productId)
 }
 @Test fun cityOrderAndAvailabilityAreIndependentAndChoiceCanBeRemembered():Unit=runBlocking{
  val (a,b)=family();products.variants.configureCities(a,mapOf(1L to listOf(b to true,a to true),2L to listOf(a to true,b to false)))
  assertEquals(b,products.variants.resolve(a,1).id);assertEquals(a,products.variants.resolve(a,2).id)
  locations.select(2);assertTrue(runCatching{basket.addProduct(b,exactVariant=true)}.isFailure);assertTrue(db.shoppingListEntryDao().getAllSync().isEmpty());locations.select(1)
  assertTrue(runCatching{products.variants.configureCities(a,mapOf(1L to listOf(a to true,b to true),2L to listOf(a to false,b to false)))}.isFailure)
  assertEquals(b,products.variants.resolve(a,1).id);assertEquals(a,products.variants.resolve(a,2).id)
  products.variants.rememberChoice(a,1);assertEquals(a,products.variants.resolve(a,1).id);assertFalse(products.variants.options(a,2).single{it.product.id==b}.enabled)
  assertTrue(runCatching{products.variants.configureCity(a,1,listOf(a to false,b to false))}.isFailure)
  assertEquals(a,products.variants.resolve(a,1).id)
 }
 @Test fun automaticVariantUsesLocalShopAndItsOwnDefaults():Unit=runBlocking{
  val (a,b)=family();val local=db.shopDao().insert(ShopEntity(name="Местный",cityId=1));val other=db.shopDao().insert(ShopEntity(name="Другой",cityId=2))
  products.ensureShopLink(a,other);products.ensureShopLink(b,local);db.productDao().update(db.productDao().getById(b)!!.copy(defaultQuantity=.5,defaultUnit="кг"))
  val row=db.shoppingListEntryDao().getById(basket.addProduct(a))!!;assertEquals(b,row.productId);assertEquals(local,row.assignedShopId);assertEquals(.5,row.quantity,0.0);assertEquals("кг",row.unit)
 }
 @Test fun replacementPreservesPurchasePlanAndRejectsStaleOrDifferentFamily():Unit=runBlocking{
  val (a,b)=family();val list=GoalListRepository(db,basket).create("Рецепт");val id=basket.addProduct(a,3.0,"банка",listId=list,plannedUnitPrice=60.0,exactVariant=true)
  val old=db.shoppingListEntryDao().getById(id)!!;basket.replaceVariant(id,b,old.updatedAt,list)
  val next=db.shoppingListEntryDao().getById(id)!!;assertEquals(b,next.productId);assertEquals(3.0,next.quantity,0.0);assertEquals(60.0,next.plannedUnitPrice!!,0.0);assertEquals("банка",next.unit)
  val c=db.productDao().insert(ProductEntity(name="Другой товар"));assertTrue(runCatching{basket.replaceVariant(id,c,next.updatedAt,list)}.isFailure)
  assertTrue(runCatching{basket.replaceVariant(id,a,old.updatedAt,list)}.isFailure)
  assertEquals(next,db.shoppingListEntryDao().getById(id))
  val place=db.shopDao().insert(ShopEntity(name="Точка замены",cityId=1))
  db.shopBranchDao().insert(ShopBranchEntity(shopId=place,address="Предпочтительный адрес"))
  val chosen=db.shopBranchDao().insert(ShopBranchEntity(shopId=place,address="Явно выбранный адрес",displayOrder=1))
  products.ensureShopLink(a,place);products.ensureShopLink(b,place)
  val regular=basket.addProduct(a,2.0,"банка",ShopChoice.EXPLICIT,place,chosen,exactVariant=true)
  val unchanged=db.shoppingListEntryDao().getById(regular)!!
  basket.replaceVariant(regular,a,unchanged.updatedAt,null);assertEquals(unchanged,db.shoppingListEntryDao().getById(regular))
  basket.replaceVariant(regular,b,unchanged.updatedAt,null)
  val replaced=db.shoppingListEntryDao().getById(regular)!!;assertEquals(b,replaced.productId);assertEquals(chosen,replaced.assignedBranchId);assertEquals("банка",replaced.unit);assertEquals(2.0,replaced.quantity,0.0)

 }
 @Test fun citySwitchUsesLocalVariantWithoutChangingBoughtOrSavedLists():Unit=runBlocking{
  val (a,b)=family();val s1=db.shopDao().insert(ShopEntity(name="Здесь",cityId=1));val s2=db.shopDao().insert(ShopEntity(name="Там",cityId=2));products.ensureShopLink(a,s1);products.ensureShopLink(b,s2)
  products.variants.configureCity(a,2,listOf(b to true,a to false))
  val regular=basket.addProduct(a,exactVariant=true);val bought=basket.addProduct(a,choice=ShopChoice.NONE,exactVariant=true);val boughtRow=db.shoppingListEntryDao().getById(bought)!!;db.shoppingListEntryDao().update(boughtRow.copy(isBought=true))
  val list=GoalListRepository(db,basket).create("План");val saved=basket.addProduct(a,listId=list,exactVariant=true)
  basket.changeShoppingCity(2)
  assertEquals(b,db.shoppingListEntryDao().getById(regular)!!.productId);assertEquals(s2,db.shoppingListEntryDao().getById(regular)!!.assignedShopId)
  assertEquals(a,db.shoppingListEntryDao().getById(bought)!!.productId);assertEquals(a,db.shoppingListEntryDao().getById(saved)!!.productId)
 }
 @Test fun pickerMinusIsScopedAndRemovesLastUnitWithoutTouchingBoughtOrOtherList():Unit=runBlocking{
  val (a,b)=family();val list=GoalListRepository(db,basket).create("Рецепт");val other=basket.addProduct(b,listId=list,exactVariant=true)
  val bought=basket.addProduct(a,choice=ShopChoice.NONE,exactVariant=true);db.shoppingListEntryDao().update(db.shoppingListEntryDao().getById(bought)!!.copy(isBought=true))
  val current=basket.addProduct(b,4.0,exactVariant=true)
  basket.decreaseFromPicker(a,null);assertEquals(2.0,db.shoppingListEntryDao().getById(current)!!.quantity,0.0)
  basket.decreaseFromPicker(a,null);assertNull(db.shoppingListEntryDao().getById(current));assertNotNull(db.shoppingListEntryDao().getById(other));assertTrue(db.shoppingListEntryDao().getById(bought)!!.isBought)
  basket.decreaseFromPicker(a,list);assertNull(db.shoppingListEntryDao().getById(other))
  val s1=db.shopDao().insert(ShopEntity(name="Первый"));val s2=db.shopDao().insert(ShopEntity(name="Другой"))
  val scoped=basket.addProduct(b,2.0,choice=ShopChoice.EXPLICIT,shopId=s1,exactVariant=true)
  val untouched=basket.addProduct(a,2.0,choice=ShopChoice.EXPLICIT,shopId=s2,exactVariant=true)
  basket.decreaseFromPicker(a,null,ShopChoice.EXPLICIT,s1);assertNull(db.shoppingListEntryDao().getById(scoped));assertNotNull(db.shoppingListEntryDao().getById(untouched))
  basket.decreaseFromPicker(a,null,ShopChoice.EXPLICIT,s1);assertEquals(2.0,db.shoppingListEntryDao().getById(untouched)!!.quantity,0.0)
  val older=basket.addProduct(a,2.0,"шт",exactVariant=true);val newer=basket.addProduct(b,2.0,"кг",exactVariant=true)
  val now=System.currentTimeMillis();db.shoppingListEntryDao().update(db.shoppingListEntryDao().getById(older)!!.copy(updatedAt=now+1000))
  basket.decreaseFromPicker(a,null);assertEquals(1.0,db.shoppingListEntryDao().getById(older)!!.quantity,0.0);assertEquals(2.0,db.shoppingListEntryDao().getById(newer)!!.quantity,0.0)

 }
 @Test fun copiedListKeepsVariantAndPortableExportCarriesItsHumanName():Unit=runBlocking{
  val (a,b)=family();val goals=GoalListRepository(db,basket);val list=goals.create("Рецепт");val row=basket.addProduct(b,3.0,"банка",listId=list,exactVariant=true)
  goals.copyList(list);val copied=db.shoppingListEntryDao().getAllSync().single{it.listId==null};assertEquals(b,copied.productId);assertEquals(3.0,copied.quantity,0.0)
  val document=goals.exportDocument(list);assertEquals("Огурцы солёные · Марка Б",document.items.single().name);assertNotNull(db.shoppingListEntryDao().getById(row));assertEquals(2,products.variants.options(a,1).size)
 }
 @Test fun deletingFamilyPreservesSavedListRowsAsIndependentQuickProducts():Unit=runBlocking{
  val (a,b)=family();photos(b,2);val goals=GoalListRepository(db,basket);val list=goals.create("Не потерять");val row=basket.addProduct(b,listId=list,exactVariant=true)
  products.deleteProduct(db.productDao().getById(a)!!);assertNull(db.productDao().getById(a));assertNull(db.productDao().getById(b))
  val retained=db.shoppingListEntryDao().getById(row)!!;assertTrue(db.productDao().getById(retained.productId)!!.isTemporary);assertEquals(2,products.variants.photos(retained.productId).size)
  assertEquals("Огурцы солёные · Марка Б",db.productDao().getById(retained.productId)!!.name)
  val copied=goals.copyList(list,newListName="Копия")!!;val copiedProduct=db.shoppingListEntryDao().getForListSync(copied).single().productId
  assertEquals(2,products.variants.photos(copiedProduct).size)
 }
 @Test fun removingVariantArchivesItsPurchasesAndReenablesAReplacementInEveryCity():Unit=runBlocking{
  val (a,b)=family();photos(b,2);products.variants.configureCity(a,1,listOf(b to true,a to false))
  val row=basket.addProduct(b,exactVariant=true);products.variants.remove(b)
  assertEquals(b,db.shoppingListEntryDao().getById(row)!!.productId);assertTrue(db.productDao().getById(b)!!.isTemporary)
  assertEquals("Огурцы солёные · Марка Б",db.productDao().getById(b)!!.name);assertEquals(2,products.variants.photos(b).size)
  assertEquals(a,products.variants.resolve(a,1).id);assertEquals(listOf(a),products.getAllProducts().first().map{it.id});assertTrue(runCatching{products.variants.create(b,"Не сохранять молча")}.isFailure)
 }
 @Test fun widgetAndDictionaryViewsUseLogicalFamilyAndActualVariantAppropriately():Unit=runBlocking{
  val (a,b)=family();val group=db.productGroupDao().insert(ProductGroupEntity(name="Продукты"));db.productDao().update(db.productDao().getById(a)!!.copy(productGroupId=group));db.productDao().update(db.productDao().getById(b)!!.copy(defaultUnit="кг"))
  assertEquals(listOf(a),products.getProductsByGroupId(group).first().map{it.id})
  assertEquals("Огурцы солёные · Марка Б",products.getProductsByUnit("кг").first().single().name)
  basket.addProduct(b,exactVariant=true)
  val widget=com.spisokryadom.app.widget.ShoppingWidgetData(db,basket,locations)
  assertEquals("Огурцы солёные · Марка Б",widget.rows().single().title)
  products.variants.rename(b,"Новая марка");assertEquals("Огурцы солёные · Новая марка",widget.rows().single().title)
 }
 @Test fun fivePhotosAndCaptionsSurviveZipAndInvalidGraphRollsBack():Unit=runBlocking{
  val (a,b)=family();db.productDao().update(db.productDao().getById(a)!!.copy(stableId="backup290"));db.productDao().update(db.productDao().getById(b)!!.copy(stableId="backup290_0"));photos(a,5);photos(b,2);products.variants.configureCity(a,2,listOf(b to true,a to false))
  val prefs=app.getSharedPreferences("shopping_list_prefs",0);prefs.edit().putBoolean("remember_variant_choice",true).commit()
  val manager=BackupManager(app,db);val file=manager.exportFile();val root=manifest(file)
  assertEquals(24,root.getInt("version"));assertEquals(1,manager.inspect(file).products);assertEquals(2,manager.inspect(file).variants);assertFalse(root.toString().contains(app.filesDir.absolutePath))
  manager.importFile(file);val restored=products.variants.photos(a);assertEquals(5,restored.size);assertEquals((0..4).map{"Подпись $it"},restored.map{it.caption});assertTrue(restored.all{File(it.uri).readBytes().contentEquals(byteArrayOf(1,2,3,4))});assertEquals(b,products.variants.resolve(a,2).id)
  val original=manager.exportFile();val bad=manifest(original);bad.getJSONObject("data").getJSONArray("productVariants").getJSONObject(1).put("familyId",99999)
  val before=db.productDao().getAllSync();val error=runCatching{manager.importFile(zip(bad,original))}.exceptionOrNull();assertNotNull(error);assertTrue(error!!.message.orEmpty().contains("семейство"));assertEquals(before,db.productDao().getAllSync());assertEquals(5,products.variants.photos(a).size)
  val duplicate=manifest(original);val labels=duplicate.getJSONObject("data").getJSONArray("productVariants");labels.getJSONObject(1).put("label",labels.getJSONObject(0).getString("label"))
  assertTrue(runCatching{manager.importFile(zip(duplicate,original))}.exceptionOrNull()!!.message.orEmpty().contains("название варианта"));assertEquals(before,db.productDao().getAllSync())
  val missing=manifest(original);val row=missing.getJSONObject("data").getJSONArray("productPhotos").getJSONObject(1);row.remove("photo");row.put("photoUnavailable",true)
  assertEquals(1,manager.inspect(zip(missing,original)).missingPhotos)
  row.remove("photoUnavailable");assertTrue(runCatching{manager.importFile(zip(missing,original))}.exceptionOrNull()!!.message.orEmpty().contains("явно отмечена"));assertEquals(before,db.productDao().getAllSync())
 }
 @Test fun photosValidateLimitAndDraftRoundTripKeepsOrderAndCaption():Unit=runBlocking{
  val (a,_)=family();photos(a,5);val before=products.variants.photos(a)
  assertTrue(runCatching{db.withTransaction{products.variants.savePhotos(a,before+ProductPhotoEntity(a,5,image("six")))}}.isFailure);assertEquals(before,products.variants.photos(a))
  val state=com.spisokryadom.app.ui.productcard.ProductCardUiState(name="Огурцы",photos=before.reversed(),photoUri=before.last().uri)
  val d=com.spisokryadom.app.ui.productcard.ProductEditDraft("draft290",a,false,false,state)
  val restored=com.spisokryadom.app.ui.productcard.ProductEditDraft.decode(d.encode());assertEquals(state.photos.map{it.uri to it.caption},restored.state.photos.map{it.uri to it.caption})
 }
 @Test fun manualVariantPagesSaveTogetherAndBackDiscardsParameterChanges():Unit=runBlocking{
  val (a,b)=family();val shops=ShopRepository(db.shopDao(),db.shopDepartmentDao(),locations,db);val classifiers=ClassifierRepository(db.productGroupDao(),db.recipientDao(),db.unitDao(),db)
  val prefs=app.getSharedPreferences("290-edit",0);prefs.edit().putBoolean(ProductEditingPreferences.KEY,false).commit()
  val vm=ProductCardViewModel(a,-1,products,shops,classifiers,app.getSharedPreferences("290-draft",0),prefs);models.put("card",vm);await{!vm.uiState.value.isLoading}
  vm.updateDefaultUnit("шт");vm.switchVariant(b);await{vm.uiState.value.activeVariantId==b};vm.updateDefaultUnit("кг");vm.save();await{vm.uiState.value.isSaved}
  assertEquals("шт",db.productDao().getById(a)!!.defaultUnit);assertEquals("кг",db.productDao().getById(b)!!.defaultUnit)
  val second=ProductCardViewModel(a,-1,products,shops,classifiers,app.getSharedPreferences("290-draft2",0),prefs);models.put("second",second);await{!second.uiState.value.isLoading};second.updateDefaultUnit("мешок");second.switchVariant(b);await{second.uiState.value.activeVariantId==b};second.updateDefaultUnit("л");second.finishEditing();await{second.uiState.value.isSaved}
  assertEquals("шт",db.productDao().getById(a)!!.defaultUnit);assertEquals("кг",db.productDao().getById(b)!!.defaultUnit)
 }
 @Test fun cleanupRetainsAllRawDraftPhotosAfterProcessDeath():Unit=runBlocking{
  val photos=(0..4).map{ProductPhotoEntity(0,it,image("raw-$it"),"Подпись $it")}
  photos.forEach{File(it.uri).setLastModified(System.currentTimeMillis()-900_000)}
  val raw=com.spisokryadom.app.ui.productcard.ProductEditDraft("raw290",-1,true,false,com.spisokryadom.app.ui.productcard.ProductCardUiState(name="",photos=photos,photoUri=photos.first().uri))
  val prefs=com.spisokryadom.app.ui.productcard.ProductDraftStore.preferences(app)
  com.spisokryadom.app.ui.productcard.ProductDraftStore.write(prefs,"raw290",raw)
  MediaLifecycle.cleanup(app,db);assertTrue(photos.all{File(it.uri).isFile})
  com.spisokryadom.app.ui.productcard.ProductDraftStore.remove(prefs,"raw290")
  MediaLifecycle.cleanup(app,db);assertTrue(photos.none{File(it.uri).exists()})
 }
 @Test fun databaseRejectsNestedFamilyAndMigrationKeepsLegacyPhotoAndPurchase():Unit=runBlocking{
  val (a,b)=family();val c=db.productDao().insert(ProductEntity(name="Другой"));assertTrue(runCatching{db.productVariantDao().insert(ProductVariantEntity(c,b,"Вложенный"))}.isFailure)
  val name="migration290.db";app.deleteDatabase(name);val photo=image("legacy")
  app.openOrCreateDatabase(name,0,null).use{old->
   val entities=JSONObject(File("schemas/com.spisokryadom.app.data.db.AppDatabase/25.json").readText()).getJSONObject("database").getJSONArray("entities")
   for(i in 0 until entities.length()){val e=entities.getJSONObject(i);old.execSQL(e.getString("createSql").replace("${'$'}{TABLE_NAME}",e.getString("tableName")));val indices=e.getJSONArray("indices");for(j in 0 until indices.length())old.execSQL(indices.getJSONObject(j).getString("createSql").replace("${'$'}{TABLE_NAME}",e.getString("tableName")))}
   old.execSQL("INSERT INTO cities(id,name) VALUES(1,'Красноярск')");old.execSQL("INSERT INTO products(id,name,purchaseType,isTemporary,stableId,photoUri) VALUES(7,'Хлеб','offline',0,'legacy290',?)",arrayOf(photo));old.execSQL("INSERT INTO shopping_list_entries(id,productId,quantity,unit,isBought,isUrgent,createdAt,updatedAt) VALUES(8,7,3,'шт',0,0,100,100)");old.version=25
  }
  val migrated=Room.databaseBuilder(app,AppDatabase::class.java,name).allowMainThreadQueries().addMigrations(AppDatabase.MIGRATION_25_26).build()
  try{assertEquals(26,migrated.openHelper.writableDatabase.version);assertEquals(photo,migrated.productVariantDao().photos(7).single().uri);assertEquals(7L,migrated.shoppingListEntryDao().getById(8)!!.productId);assertEquals(3.0,migrated.shoppingListEntryDao().getById(8)!!.quantity,0.0);assertTrue(migrated.productVariantDao().variants().isEmpty())}finally{migrated.close();app.deleteDatabase(name)}
  assertEquals(a,products.variants.familyId(b))
 }
}
