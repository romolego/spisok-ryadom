package com.spisokryadom.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.backup.BackupManager
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.File
import java.util.zip.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class Audit270DataTest {
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:AppDatabase
    private lateinit var locations:LocationRepository
    private lateinit var shops:ShopRepository
    private lateinit var basket:ShoppingListRepository
    @Before fun setup():Unit=runBlocking {
        db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build()
        db.cityDao().insert(CityEntity(1,"Город"));db.cityDao().insert(CityEntity(2,"Другой город"))
        val prefs=app.getSharedPreferences("270-location",0);prefs.edit().clear().commit()
        locations=LocationRepository(db.cityDao(),db.shopDao(),prefs)
        shops=ShopRepository(db.shopDao(),db.shopDepartmentDao(),locations,db)
        basket=ShoppingListRepository(db.shoppingListEntryDao(),db,locations)
    }
    @After fun close(){locations.closeForTest();db.close()}
    private fun root(file:File)=ZipFile(file).use{zip->JSONObject(zip.getInputStream(zip.getEntry("manifest.json")).reader().readText())}
    private fun archive(root:JSONObject)=File(app.cacheDir,"audit270-${java.util.UUID.randomUUID()}.zip").also{file->
        ZipOutputStream(file.outputStream()).use{zip->zip.putNextEntry(ZipEntry("manifest.json"));zip.write(root.toString().toByteArray());zip.closeEntry()}
    }

    @Test fun shopPinsDoNotChangeRouteProductPreferencesOrExistingPurchases():Unit=runBlocking {
        val first=db.shopDao().insert(ShopEntity(name="Бета",displayOrder=0,manualOrderOverride=true))
        val second=db.shopDao().insert(ShopEntity(name="Альфа",displayOrder=1))
        val third=db.shopDao().insert(ShopEntity(name="Гамма",displayOrder=2))
        val p=db.productDao().insert(ProductEntity(name="Товар",preferCityShopOrder=true))
        db.productShopLinkDao().insert(ProductShopLinkEntity(productId=p,shopId=first,priority=1))
        db.productShopLinkDao().insert(ProductShopLinkEntity(productId=p,shopId=second,priority=2))
        val purchase=basket.addProduct(p)
        val oldRow=basket.getEntryById(purchase);val oldLinks=db.productShopLinkDao().getAllSync()
        shops.saveFavoriteFixture(second,true);shops.saveFavoriteFixture(first,true);shops.moveFavoriteShop(first,-1)
        shops.moveFavoriteShop(third,-1)
        assertEquals(listOf(first,second),db.shopDao().getAllSync().filter{it.isFavorite}.sortedBy{it.favoriteOrder}.map{it.id})
        assertEquals(listOf(first,second,third),shops.getAllShops().first().map{it.id})
        assertEquals(oldRow,basket.getEntryById(purchase));assertEquals(oldLinks,db.productShopLinkDao().getAllSync())
        assertTrue(db.productDao().getById(p)!!.preferCityShopOrder)
        assertTrue(db.shopDao().getById(first)!!.manualOrderOverride)
        assertEquals(first,basket.getEntryById(basket.addProduct(p))!!.assignedShopId)
    }

    @Test fun shopPinsAreIdempotentAndCompactExtremeOrderWithoutLosingCityIsolation():Unit=runBlocking {
        val first=db.shopDao().insert(ShopEntity(name="Первый",isFavorite=true,favoriteOrder=Int.MAX_VALUE,displayOrder=8))
        val second=db.shopDao().insert(ShopEntity(name="Второй",displayOrder=3))
        val foreign=db.shopDao().insert(ShopEntity(name="В другом городе",cityId=2,isFavorite=true,favoriteOrder=7,displayOrder=4))
        val original=db.shopDao().getAllSync();shops.saveFavoriteFixture(first,true);assertEquals(original,db.shopDao().getAllSync())
        shops.saveFavoriteFixture(second,true)
        assertEquals(listOf(foreign,first,second),db.shopDao().getAllSync().filter{it.isFavorite}.sortedBy{it.favoriteOrder}.map{it.id})
        val other=db.shopDao().getById(foreign)
        shops.moveFavoriteShop(second,-1)
        assertEquals(other,db.shopDao().getById(foreign));assertEquals(8,db.shopDao().getById(first)!!.displayOrder)
        assertEquals(listOf(second,first),db.shopDao().getAllSync().filter{it.cityId==1L&&it.isFavorite}.sortedBy{it.favoriteOrder}.map{it.id})
        shops.saveFavoriteFixture(second,false);assertEquals(0,db.shopDao().getById(second)!!.favoriteOrder)
        assertTrue(runCatching{shops.moveFavoriteShop(first,2)}.isFailure)
    }

    @Test fun cardSaveAppendsPinsAndUsesCurrentFavoriteOrderRatherThanStaleForm():Unit=runBlocking {
        val first=shops.saveWithDepartments(ShopEntity(name="Первый",isFavorite=true),emptyList())
        val second=shops.saveWithDepartments(ShopEntity(name="Второй",isFavorite=true),emptyList())
        val stale=db.shopDao().getById(first)!!
        assertTrue(db.shopDao().getById(second)!!.favoriteOrder>stale.favoriteOrder)
        shops.moveFavoriteShop(second,-1)
        val current=db.shopDao().getById(first)!!
        shops.saveWithDepartments(stale.copy(name="Переименован"),emptyList())
        assertEquals(current.favoriteOrder,db.shopDao().getById(first)!!.favoriteOrder)
        shops.saveWithDepartments(db.shopDao().getById(first)!!.copy(isFavorite=false),emptyList())
        assertFalse(db.shopDao().getById(first)!!.isFavorite)
        shops.saveWithDepartments(db.shopDao().getById(first)!!.copy(isFavorite=true),emptyList())
        assertTrue(db.shopDao().getById(first)!!.favoriteOrder>db.shopDao().getById(second)!!.favoriteOrder)
    }

    @Test fun fullBackupRoundTripsShopPinsAndNewVersion():Unit=runBlocking {
        val id=db.shopDao().insert(ShopEntity(name="Не терять",isFavorite=true,favoriteOrder=17,displayOrder=9,shoppingBackground="rose"))
        val manager=BackupManager(app,db);val file=manager.exportFile()
        try {
            val json=root(file);assertEquals(23,json.getInt("version"))
            val encoded=json.getJSONObject("data").getJSONArray("shops").getJSONObject(0)
            assertTrue(encoded.getBoolean("isFavorite"));assertEquals(17,encoded.getInt("favoriteOrder"))
            val before=db.shopDao().getById(id);shops.saveFavoriteFixture(id,false);manager.importFile(file)
            assertEquals(before,db.shopDao().getById(id))
        } finally {manager.discard(file)}
    }

    @Test fun old21BackupDefaultsShopPinsWithoutChangingRoute():Unit=runBlocking {
        val id=db.shopDao().insert(ShopEntity(name="Магазин",displayOrder=19,manualOrderOverride=true))
        val manager=BackupManager(app,db);val source=manager.exportFile()
        val json=root(source).put("version",21);val encoded=json.getJSONObject("data").getJSONArray("shops").getJSONObject(0)
        encoded.remove("isFavorite");encoded.remove("favoriteOrder")
        val old=archive(json)
        try {shops.saveFavoriteFixture(id,true);manager.importFile(old);val shop=db.shopDao().getById(id)!!
            assertFalse(shop.isFavorite);assertEquals(0,shop.favoriteOrder);assertEquals(19,shop.displayOrder);assertTrue(shop.manualOrderOverride)
        } finally {manager.discard(source);manager.discard(old)}
    }

    @Test fun malformedShopFavoriteMetadataFailsBeforeReplacingAnyData():Unit=runBlocking {
        val id=db.shopDao().insert(ShopEntity(name="Не терять",isFavorite=true,favoriteOrder=4))
        val manager=BackupManager(app,db);val source=manager.exportFile();val before=db.shopDao().getAllSync()
        suspend fun reject(change:(JSONObject,JSONObject)->Unit){
            val json=root(source);change(json,json.getJSONObject("data").getJSONArray("shops").getJSONObject(0));val file=archive(json)
            try {assertTrue(runCatching{manager.importFile(file)}.isFailure);assertEquals(before,db.shopDao().getAllSync());assertEquals("Не терять",db.shopDao().getById(id)!!.name)}finally{manager.discard(file)}
        }
        try {
            reject{_,row->row.remove("isFavorite")};reject{_,row->row.remove("favoriteOrder")}
            for(value in listOf<Any>("true",1,JSONObject.NULL))reject{_,row->row.put("isFavorite",value)}
            for(value in listOf<Any>("1",1.5,-1,Int.MAX_VALUE.toLong()+1,JSONObject.NULL))reject{_,row->row.put("favoriteOrder",value)}
            reject{json,_->json.put("version",21)}
        }finally{manager.discard(source)}
    }

    @Test fun batchAddingDepartmentsHonorsNetworkContractAcrossCities():Unit=runBlocking {
        val network=shops.ensureNetwork("Сеть")
        val first=db.shopDao().insert(ShopEntity(name="Первый",networkId=network))
        val second=db.shopDao().insert(ShopEntity(name="Второй",cityId=2,networkId=network))
        val independent=db.shopDao().insert(ShopEntity(name="Другой магазин"))
        val originalType=shops.createDepartmentType("Молочка")
        val original=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=second,name="Молочка",typeId=originalType,displayOrder=3))
        val type=shops.createDepartmentType("Бытовая химия")
        val product=db.productDao().insert(ProductEntity(name="Молоко"))
        val link=db.productShopLinkDao().insert(ProductShopLinkEntity(productId=product,shopId=second,departmentId=original))
        val purchase=basket.addProduct(product,choice=ShopChoice.NONE)
        val beforeRow=basket.getEntryById(purchase)
        val batch=BatchRepository(db,ProductRepository(db.productDao(),db.productShopLinkDao(),db),basket)
        batch.addDepartments(setOf(first),setOf(type))
        val firstDepartments=db.shopDepartmentDao().getByShopIdSync(first)
        val secondDepartments=db.shopDepartmentDao().getByShopIdSync(second)
        assertEquals(listOf(type,originalType),firstDepartments.map{it.typeId})
        assertEquals(firstDepartments.map{it.typeId},secondDepartments.map{it.typeId})
        assertEquals(original,secondDepartments.single{it.typeId==originalType}.id)
        assertEquals(original,db.productShopLinkDao().getById(link)!!.departmentId)
        assertEquals(beforeRow,basket.getEntryById(purchase))
        assertTrue(db.shopDepartmentDao().getByShopIdSync(independent).isEmpty())
        val before=db.shopDepartmentDao().getAllSync();batch.addDepartments(setOf(first),setOf(type));assertEquals(before,db.shopDepartmentDao().getAllSync())
    }

    @Test fun batchAssigningNewDepartmentSyncsNetworkAndUpdatesOnlyChosenProducts():Unit=runBlocking {
        val network=shops.ensureNetwork("Сеть")
        val first=db.shopDao().insert(ShopEntity(name="Первый",networkId=network))
        val second=db.shopDao().insert(ShopEntity(name="Второй",networkId=network))
        val repo=ProductRepository(db.productDao(),db.productShopLinkDao(),db)
        val chosen=repo.saveWithLinks(ProductEntity(name="Порошок"),listOf(ProductShopLinkEntity(productId=0,shopId=first)))
        val other=repo.saveWithLinks(ProductEntity(name="Мыло"),listOf(ProductShopLinkEntity(productId=0,shopId=first)))
        val pending=basket.addProduct(chosen);val bought=basket.addProduct(other);basket.markBought(bought)
        val history=basket.getEntryById(bought);val type=shops.createDepartmentType("Бытовая химия")
        val batch=BatchRepository(db,repo,basket)
        batch.changeProducts(setOf(chosen),ProductBatchChange.Department(first,type))
        val placement=db.shopDepartmentDao().getByShopIdSync(first).single()
        assertEquals(type,placement.typeId);assertEquals(listOf(type),db.shopDepartmentDao().getByShopIdSync(second).map{it.typeId})
        assertEquals(placement.id,db.productShopLinkDao().getLinksForProductSync(chosen).single().departmentId)
        assertEquals(placement.id,basket.getEntryById(pending)!!.assignedDepartmentId)
        assertNull(db.productShopLinkDao().getLinksForProductSync(other).single().departmentId)
        assertEquals(history,basket.getEntryById(bought))
        db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
    }

    @Test fun addingShopLinksAfterExtremeImportedPrioritiesPreservesExistingRoute():Unit=runBlocking {
        val first=db.shopDao().insert(ShopEntity(name="Первый",displayOrder=2))
        val second=db.shopDao().insert(ShopEntity(name="Второй",displayOrder=1))
        val third=db.shopDao().insert(ShopEntity(name="Третий",displayOrder=3))
        val repo=ProductRepository(db.productDao(),db.productShopLinkDao(),db)
        for(inherit in listOf(false,true)) {
            val product=db.productDao().insert(ProductEntity(name="Товар $inherit",preferCityShopOrder=inherit))
            // Duplicate extreme priorities use city route and shop ID as tie-breaks.
            db.productShopLinkDao().insert(ProductShopLinkEntity(productId=product,shopId=first,priority=Int.MAX_VALUE))
            db.productShopLinkDao().insert(ProductShopLinkEntity(productId=product,shopId=second,priority=Int.MAX_VALUE))
            val history=basket.addProduct(product);basket.markBought(history);val old=basket.getEntryById(history)
            repo.ensureShopLink(product,third)
            val links=repo.getShopLinksForProductSync(product)
            assertEquals(listOf(second,first,third),links.sortedBy{it.priority}.map{it.shopId})
            assertTrue(links.all{it.priority>0});assertEquals(old,basket.getEntryById(history))
            assertEquals(second,basket.getEntryById(basket.addProduct(product))!!.assignedShopId)
            assertEquals(inherit,db.productDao().getById(product)!!.preferCityShopOrder)
        }
    }

    @Test fun extendingAvailabilityAfterExtremePriorityDoesNotChangeFuturePreference():Unit=runBlocking {
        val original=db.shopDao().insert(ShopEntity(name="Предпочтительный",displayOrder=1))
        val destination=db.shopDao().insert(ShopEntity(name="Для этой покупки",displayOrder=2))
        val product=db.productDao().insert(ProductEntity(name="Товар",preferCityShopOrder=false))
        db.productShopLinkDao().insert(ProductShopLinkEntity(productId=product,shopId=original,priority=Int.MAX_VALUE))
        val id=basket.addProduct(product)
        basket.movePending(setOf(id),destination,extendAvailability=true,rememberPriority=false,strictPending=true)
        assertEquals(destination,basket.getEntryById(id)!!.assignedShopId)
        val links=db.productShopLinkDao().getLinksForProductSync(product)
        assertEquals(listOf(original,destination),links.sortedBy{it.priority}.map{it.shopId})
        assertTrue(links.all{it.priority>0})
        assertEquals(original,basket.getEntryById(basket.addProduct(product))!!.assignedShopId)
    }

    @Test fun convertingFavoriteShopPreservesPinsAppearanceAndPhysicalPurchaseIdentity():Unit=runBlocking {
        val other=db.shopDao().insert(ShopEntity(name="Другое избранное",isFavorite=true,favoriteOrder=Int.MAX_VALUE))
        val original=shops.saveWithDepartments(ShopEntity(name="Рынок",isFavorite=true,shoppingBackground="rose",iconKey="store"),emptyList(),
            listOf(ShopBranchEntity(id=-1,shopId=0,address="Павильон 1"),ShopBranchEntity(id=-2,shopId=0,address="Павильон 2")))
        val points=db.shopBranchDao().forShop(original)
        val product=ProductRepository(db.productDao(),db.productShopLinkDao(),db).saveWithLinks(ProductEntity(name="Товар"),listOf(ProductShopLinkEntity(productId=0,shopId=original)))
        val rowId=basket.addProduct(product,choice=ShopChoice.EXPLICIT,shopId=original,branchId=points.last().id)
        val venue=shops.convertToVenue(original,VenueKinds.MARKET)
        val sellers=db.shopDao().getAllSync().filter{it.id!=other}
        assertEquals(2,sellers.size);assertTrue(sellers.all{it.isFavorite&&it.shoppingBackground=="rose"&&it.iconKey=="store"})
        assertEquals(2,sellers.map{it.favoriteOrder}.distinct().size)
        assertTrue(sellers.all{it.favoriteOrder>db.shopDao().getById(other)!!.favoriteOrder})
        assertNull(db.shopDao().getById(original));assertEquals(points.map{it.id},db.shopBranchDao().getAll().map{it.id})
        assertTrue(db.shopBranchDao().getAll().all{it.venueId==venue})
        val row=basket.getEntryById(rowId)!!;assertEquals(points.last().id,row.assignedBranchId)
        assertTrue(row.assignedShopId in sellers.map{it.id});assertEquals(product,row.productId)
    }

    @Test fun sharedListWriterRejectsNonPortableFieldsAtomicallyAndAcceptsBoundaryValues():Unit=runBlocking {
        val goals=GoalListRepository(db,basket);val list=goals.create("Список")
        val product=db.productDao().insert(ProductEntity(name="Товар"))
        val before=db.productDao().getAllSync()
        for(unit in listOf("x".repeat(81),"кг\u0000")){
            assertTrue(runCatching{basket.addProduct(product,unit=unit,listId=list)}.isFailure)
            assertTrue(db.shoppingListEntryDao().getAllSync().isEmpty())
        }
        for(name in listOf("x".repeat(501),"Товар\u0000")){
            assertTrue(runCatching{basket.addNewProduct(name,1.0,"шт",true,listId=list)}.isFailure)
            assertEquals(before,db.productDao().getAllSync());assertTrue(db.shoppingListEntryDao().getAllSync().isEmpty())
        }
        db.productDao().update(db.productDao().getById(product)!!.copy(name="я".repeat(500)))
        val id=basket.addProduct(product,unit="x".repeat(80),listId=list)
        val document=goals.exportDocument(list)
        assertEquals(id,goals.snapshot(list).entries.single().id)
        assertTrue(com.spisokryadom.app.domain.GoalListTransfer.parse(com.spisokryadom.app.domain.GoalListTransfer.exportText(document)).canImport)
    }

    @Test fun appendingStoresAndDepartmentsAfterExtremeOrderPreservesRouteTies():Unit=runBlocking {
        val first=db.shopDao().insert(ShopEntity(name="Альфа",displayOrder=Int.MAX_VALUE))
        val preferred=db.shopDao().insert(ShopEntity(name="Бета",displayOrder=Int.MAX_VALUE))
        val product=db.productDao().insert(ProductEntity(name="Товар",preferCityShopOrder=true))
        db.productShopLinkDao().insert(ProductShopLinkEntity(productId=product,shopId=first,priority=2))
        db.productShopLinkDao().insert(ProductShopLinkEntity(productId=product,shopId=preferred,priority=1))
        val added=shops.createInCurrentCity("Новый")
        assertEquals(db.shopDao().getById(first)!!.displayOrder,db.shopDao().getById(preferred)!!.displayOrder)
        assertTrue(db.shopDao().getById(added)!!.displayOrder>db.shopDao().getById(first)!!.displayOrder)
        assertEquals(preferred,basket.getEntryById(basket.addProduct(product))!!.assignedShopId)
        db.shopDao().update(db.shopDao().getById(added)!!.copy(displayOrder=Int.MAX_VALUE))
        val card=shops.saveWithDepartments(ShopEntity(name="Из карточки"),emptyList(),appendToRoute=true)
        assertTrue(db.shopDao().getById(card)!!.displayOrder>db.shopDao().getById(added)!!.displayOrder)
        val oldType=shops.createDepartmentType("Прежняя")
        val oldDepartment=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=first,typeId=oldType,name="Прежняя",displayOrder=Int.MAX_VALUE))
        val type=shops.createDepartmentType("Новая")
        BatchRepository(db,ProductRepository(db.productDao(),db.productShopLinkDao(),db),basket).addDepartments(setOf(first),setOf(type))
        val departments=db.shopDepartmentDao().getByShopIdSync(first)
        assertEquals(listOf(oldType,type),departments.map{it.typeId});assertEquals(oldDepartment,departments.first().id)
        assertTrue(departments.all{it.displayOrder>=0})
    }

    @Test fun appendingManualDictionariesAfterExtremeOrderPreservesRowsAndReferences():Unit=runBlocking {
        val classifiers=ClassifierRepository(db.productGroupDao(),db.recipientDao(),db.unitDao(),db)
        val recipient=classifiers.insertRecipient(RecipientEntity(name="Первый",displayOrder=Int.MAX_VALUE,iconKey="person"))
        val other=classifiers.insertRecipient(RecipientEntity(name="Второй",displayOrder=Int.MAX_VALUE))
        val unit=classifiers.insertUnit(UnitEntity(name="шт",displayOrder=Int.MAX_VALUE))
        val type=shops.createDepartmentType("Прежняя")
        db.shopDepartmentDao().updateType(db.shopDepartmentDao().type(type)!!.copy(displayOrder=Int.MAX_VALUE))
        val product=db.productDao().insert(ProductEntity(name="Товар",recipientId=recipient,defaultUnit="шт"))
        val r=classifiers.insertRecipient(RecipientEntity(name="Добавленный",displayOrder=classifiers.getMaxRecipientDisplayOrder()+1))
        val u=classifiers.insertUnit(UnitEntity(name="кг",displayOrder=classifiers.getMaxUnitDisplayOrder()+1))
        val t=shops.createDepartmentType("Добавленная")
        val recipients=db.recipientDao().getAllSync()
        assertEquals(recipients.single{it.id==recipient}.displayOrder,recipients.single{it.id==other}.displayOrder)
        assertTrue(recipients.single{it.id==r}.displayOrder>recipients.single{it.id==recipient}.displayOrder)
        assertEquals("person",db.recipientDao().getById(recipient)!!.iconKey)
        assertTrue(db.unitDao().getById(u)!!.displayOrder>db.unitDao().getById(unit)!!.displayOrder)
        assertTrue(db.shopDepartmentDao().type(t)!!.displayOrder>db.shopDepartmentDao().type(type)!!.displayOrder)
        assertEquals(recipient,db.productDao().getById(product)!!.recipientId);assertEquals("шт",db.productDao().getById(product)!!.defaultUnit)
    }

    @Test fun captureAndMoveValidateBeforeMutationAndPreserveBoundaryDuplicatesAndIds():Unit=runBlocking {
        val goals=GoalListRepository(db,basket);val target=goals.create("Назначение")
        val good=db.productDao().insert(ProductEntity(name="Товар"))
        val source=db.productDao().insert(ProductEntity(name="Источник"))
        val first=db.shoppingListEntryDao().insert(ShoppingListEntryEntity(productId=good,quantity=1.0,unit="шт"))
        val second=db.shoppingListEntryDao().insert(ShoppingListEntryEntity(productId=source,quantity=2.0,unit="шт",isBought=true))
        for((name,unit) in listOf("Источник" to "x".repeat(81),"я".repeat(501) to "шт")){
            db.productDao().update(db.productDao().getById(source)!!.copy(name=name))
            db.shoppingListEntryDao().update(db.shoppingListEntryDao().getById(second)!!.copy(unit=unit))
            val beforeRows=db.shoppingListEntryDao().getAllSync();val beforeProducts=db.productDao().getAllSync()
            val beforeLists=db.goalListDao().getAllSync()
            assertTrue(runCatching{goals.captureList(null,"Не создавать")}.isFailure)
            assertTrue(runCatching{goals.moveEntriesExpected(linkedMapOf(first to null,second to null),target)}.isFailure)
            assertEquals(beforeRows,db.shoppingListEntryDao().getAllSync());assertEquals(beforeProducts,db.productDao().getAllSync())
            assertEquals(beforeLists,db.goalListDao().getAllSync())
        }
        db.productDao().update(db.productDao().getById(source)!!.copy(name="я".repeat(500)))
        val boundary=db.shoppingListEntryDao().getById(second)!!.copy(unit="x".repeat(80))
        db.shoppingListEntryDao().update(boundary)
        // Two rows can deliberately have identical metadata; capture must not merge them.
        val duplicate=db.shoppingListEntryDao().insert(boundary.copy(id=0,quantity=3.0,isBought=false))
        val before=db.shoppingListEntryDao().getAllSync().associateBy{it.id}
        val copy=goals.captureList(null,"Копия")
        val captured=goals.snapshot(copy).entries.filter{it.productId==source}
        assertEquals(2,captured.size);assertEquals(listOf(2.0,3.0),captured.map{it.quantity}.sorted())
        assertTrue(captured.none{it.id==second||it.id==duplicate});assertTrue(captured.none{it.isBought})
        assertEquals(boundary,db.shoppingListEntryDao().getById(second))
        assertTrue(com.spisokryadom.app.domain.GoalListTransfer.parse(com.spisokryadom.app.domain.GoalListTransfer.exportText(goals.exportDocument(copy))).canImport)
        goals.moveEntriesExpected(linkedMapOf(second to null,duplicate to null),target)
        for(id in listOf(second,duplicate)){
            val actual=db.shoppingListEntryDao().getById(id)!!
            assertEquals(before.getValue(id).copy(listId=target,updatedAt=actual.updatedAt),actual)
        }
        assertEquals(2,goals.snapshot(target).entries.size)
    }

    @Test fun migration23KeepsCompleteGraphAndDefaultsPins():Unit=runBlocking {
        val name="audit270-migration.db";app.deleteDatabase(name)
        val schema=File("schemas/com.spisokryadom.app.data.db.AppDatabase/23.json").takeIf{it.isFile}?:File("app/schemas/com.spisokryadom.app.data.db.AppDatabase/23.json")
        app.openOrCreateDatabase(name,0,null).use{old->
            val entities=JSONObject(schema.readText()).getJSONObject("database").getJSONArray("entities")
            for(i in 0 until entities.length()){val e=entities.getJSONObject(i);old.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName")));val indices=e.getJSONArray("indices");for(j in 0 until indices.length())old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName")))}
            old.execSQL("INSERT INTO cities(id,name) VALUES(1,'Город')")
            old.execSQL("INSERT INTO shops(id,name,cityId,displayOrder,manualOrderOverride,shoppingBackground,iconKey,purchaseBackground) VALUES(7,'Магазин',1,13,1,'rose','store','auto')")
            old.execSQL("INSERT INTO products(id,name,purchaseType,isTemporary,stableId,preferCityShopOrder,isFavorite,accent) VALUES(8,'Товар','offline',0,'audit270-product',1,1,'green')")
            old.execSQL("INSERT INTO product_shop_links(id,productId,shopId,priority,restrictBranches) VALUES(9,8,7,2,0)")
            old.execSQL("INSERT INTO shopping_list_entries(id,productId,quantity,unit,isBought,isUrgent,createdAt,updatedAt,assignedShopId,explicitNoShop) VALUES(10,8,2.5,'кг',1,1,11,12,7,0)")
            old.version=23
        }
        val migrated=Room.databaseBuilder(app,AppDatabase::class.java,name).allowMainThreadQueries().addMigrations(AppDatabase.MIGRATION_23_24,AppDatabase.MIGRATION_24_25).build()
        try {
            val shop=migrated.shopDao().getById(7)!!;assertEquals(25,migrated.openHelper.writableDatabase.version)
            assertFalse(shop.isFavorite);assertEquals(0,shop.favoriteOrder);assertEquals(13,shop.displayOrder);assertTrue(shop.manualOrderOverride)
            assertEquals("rose",shop.shoppingBackground);assertEquals("auto",shop.purchaseBackground)
            val row=migrated.shoppingListEntryDao().getById(10)!!;assertEquals(2.5,row.quantity,0.0);assertTrue(row.isBought);assertTrue(row.isUrgent);assertEquals(12L,row.updatedAt)
            assertEquals(7L,row.assignedShopId);assertEquals(2,migrated.productShopLinkDao().getLinksForProductSync(8).single().priority)
            assertTrue(migrated.productDao().getById(8)!!.isFavorite)
            migrated.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
        }finally{migrated.close();app.deleteDatabase(name)}
    }
}
