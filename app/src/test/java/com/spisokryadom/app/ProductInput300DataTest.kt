package com.spisokryadom.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.*
import com.spisokryadom.app.data.backup.BackupManager
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.domain.*
import com.spisokryadom.app.util.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File
import java.util.UUID
import java.util.zip.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class ProductInput300DataTest {
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:AppDatabase
    private lateinit var places:LocationRepository
    private lateinit var products:ProductRepository
    private lateinit var basket:ShoppingListRepository
    private lateinit var input:ProductInputRepository
    private lateinit var bulk:BulkPurchaseRepository
    @Before fun setup():Unit=runBlocking {
        db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build()
        RetailConstraints.install(db.openHelper.writableDatabase)
        StandardCategories.install(db.openHelper.writableDatabase)
        db.cityDao().insert(CityEntity(1,"Город"));db.cityDao().insert(CityEntity(2,"Другой город"))
        app.getSharedPreferences("input300-location",0).edit().clear().commit()
        places=LocationRepository(db.cityDao(),db.shopDao(),app.getSharedPreferences("input300-location",0))
        products=ProductRepository(db.productDao(),db.productShopLinkDao(),db)
        basket=ShoppingListRepository(db.shoppingListEntryDao(),db,places)
        input=products.input;bulk=BulkPurchaseRepository(db,basket,places)
    }
    @After fun close(){places.closeForTest();db.close()}
    private suspend fun type(name:String)=db.shopDepartmentDao().types().single{it.name==name}.id
    private suspend fun product(name:String)=db.productDao().insert(ProductEntity(name=name,defaultUnit="шт"))
    private fun token()=UUID.randomUUID().toString()
    private suspend fun commit(rows:List<BulkProductRow>,key:String=token(),city:Long=1,save:Boolean=false)=bulk.add(key,rows,city,ShopChoice.AUTO,null,null,save,"шт",ProductEditorTransactions.generation.get())
    private fun manifest(file:File)=ZipFile(file).use{z->JSONObject(z.getInputStream(z.getEntry("manifest.json")).reader().readText())}
    private fun changed(file:File,change:(JSONObject)->Unit):File {
        val root=manifest(file);change(root)
        return File(app.cacheDir,"input300-${token()}.zip").also{target->ZipOutputStream(target.outputStream()).use{out->ZipFile(file).use{z->z.entries().asSequence().forEach{entry->
            out.putNextEntry(ZipEntry(entry.name));out.write(if(entry.name=="manifest.json")root.toString().toByteArray()else z.getInputStream(entry).readBytes());out.closeEntry()
        }}}}
    }

    @Test fun hiddenRussianHintsNeverCreateCatalogueRowsAndRecognizeTypos():Unit=runBlocking {
        assertTrue(ProductHints.entries.size>=200)
        assertTrue(ProductHints.entries.all{it.aliases.all{a->!a.contains(Regex("[A-Za-z]"))}})
        assertEquals("Бананы",ProductHints.match("бананс")!!.name)
        assertEquals("Гречка",ProductHints.match("гречневая крупа")!!.name)
        assertEquals("упак",input.suggest("спагетти").unit)
        assertEquals(type("Макароны"),input.suggest("спагетти").departmentTypeId)
        assertNull(ProductHints.match("масло"));assertNull(ProductHints.match("йцукенабракадабра"))
        assertTrue(db.productDao().getAllSync().isEmpty())
        assertTrue(bulk.candidates("бананы",1).isEmpty())
        assertTrue(db.shoppingListEntryDao().getAllSync().isEmpty())
    }
    @Test fun hintSwitchesAreIndependentAndPreserveUnrelatedFallbackRules():Unit=runBlocking {
        val category=input.suggest("банан").categoryId;assertNotNull(category)
        input.updateSettings{it.copy(prefillUnit=false)}
        assertNull(input.suggest("банан").unit);assertEquals(category,input.suggest("банан").categoryId)
        input.updateSettings{it.copy(prefillCategory=false,prefillDepartment=false)}
        assertNull(input.suggest("банан").categoryId);assertNull(input.suggest("банан").departmentTypeId)
        input.updateSettings{it.copy(prefillEnabled=false,prefillUnit=true)}
        assertNull(input.suggest("банан").unit);assertTrue(input.settings().inheritDepartment)
    }
    @Test fun commonDepartmentCreatesOnlyNewPlacementsAndKeepsLocalExceptions():Unit=runBlocking {
        val id=product("Сахар");val grain=type("Крупы");val milk=type("Молочка")
        val shops=(1..5).map{db.shopDao().insert(ShopEntity(name="Магазин $it"))}
        val department=input.placement(shops[0],grain)
        products.saveWithLinks(db.productDao().getById(id)!!,listOf(ProductShopLinkEntity(productId=id,shopId=shops[0],departmentId=department)))
        products.ensureShopLink(id,shops[1])
        val original=db.productShopLinkDao().getLinksForProductSync(id)
        assertEquals(grain,db.shopDepartmentDao().getById(original.single{it.shopId==shops[1]}.departmentId!!)!!.typeId)
        input.setDepartment(id,milk);products.ensureShopLink(id,shops[2])
        assertEquals(original,db.productShopLinkDao().getLinksForProductSync(id).filter{it.shopId in shops.take(2)})
        assertEquals(milk,db.shopDepartmentDao().getById(db.productShopLinkDao().getLinksForProductSync(id).single{it.shopId==shops[2]}.departmentId!!)!!.typeId)
        input.updateSettings{it.copy(inheritDepartment=false)};products.ensureShopLink(id,shops[3])
        assertNull(db.productShopLinkDao().getLinksForProductSync(id).single{it.shopId==shops[3]}.departmentId)
        assertTrue(db.shopDepartmentDao().getByShopIdSync(shops[3]).isEmpty())
        input.updateSettings{it.copy(inheritDepartment=true)};input.setDepartment(id,null);products.ensureShopLink(id,shops[4])
        assertNull(db.productShopLinkDao().getLinksForProductSync(id).single{it.shopId==shops[4]}.departmentId)
    }
    @Test fun variantDefaultsStayIndependentAndDeletingClassifierClearsReferences():Unit=runBlocking {
        val root=product("Товар");val grain=type("Крупы");val milk=type("Молочка")
        input.setDepartment(root,grain);val child=products.variants.create(root,"Другая упаковка")
        assertEquals(grain,input.department(child)!!.typeId)
        input.setDepartment(child,milk);assertEquals(grain,input.department(root)!!.typeId)
        db.shopDepartmentDao().deleteType(milk)
        assertNull(input.department(child)!!.typeId);assertEquals(grain,input.department(root)!!.typeId)
        db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
    }
    @Test fun explicitRecipientAndCategoryRuleWinOverGlobalFallback():Unit=runBlocking {
        val a=db.recipientDao().insert(RecipientEntity(name="Первый"));val b=db.recipientDao().insert(RecipientEntity(name="Второй"))
        val group=db.productGroupDao().getAllSync().first().id
        input.updateSettings{it.copy(recipientId=a,recipientEnabled=true)}
        assertEquals(a,input.recipient(null,false,null));assertNull(input.recipient(null,true,null))
        assertEquals(b,input.recipient(null,true,b))
        db.creationPresetDao().setGroup(GroupCreationPreset(group,b));assertEquals(b,input.recipient(group,false,null))
        db.creationPresetDao().setGroup(GroupCreationPreset(group,null));assertNull(input.recipient(group,false,null))
        db.recipientDao().delete(db.recipientDao().getById(a)!!)
        assertNull(input.recipient(null,false,null));assertNull(input.settings().recipientId)
    }
    @Test fun newProductsUseHintsButKeepExplicitUnitsAndExistingRows():Unit=runBlocking {
        val old=product("Бананы");val before=db.productDao().getById(old)!!
        val shop=db.shopDao().insert(ShopEntity(name="Магазин"))
        val row=basket.addNewProduct("Спагетти",1.0,"пачка",true,ShopChoice.EXPLICIT,shop)
        val entry=db.shoppingListEntryDao().getById(row)!!;val created=db.productDao().getById(entry.productId)!!
        assertEquals("пачка",created.defaultUnit);assertEquals(type("Макароны"),input.department(created.id)!!.typeId)
        assertEquals(type("Макароны"),db.shopDepartmentDao().getById(entry.assignedDepartmentId!!)!!.typeId)
        assertNotNull(created.productGroupId);assertEquals(before,db.productDao().getById(old))
    }
    @Test fun everyPeriodIsADelimiterAndNoQuantityOrUnitIsParsed():Unit=runBlocking {
        assertEquals(listOf("лук","морковь","картошка","хлеб"),BulkProductInput.split("лук. морковь,\nкартошка;хлеб"))
        assertEquals(listOf("молоко 1","5 кг"),BulkProductInput.split("молоко 1.5 кг"))
        assertEquals(listOf("зелёный лук"),BulkProductInput.split("зелёный лук"))
        assertTrue(bulk.preview("лук;морковь",1).all{it.quantity=="1"&&!it.resolved})
        assertTrue(db.productDao().getAllSync().isEmpty())
    }
    @Test fun approximatePreviewOffersGenericAndSpecificCatalogueChoicesWithoutAddingAnything():Unit=runBlocking {
        val bread=product("Хлеб");val dried=product("Сушёный хлеб")
        val rows=bulk.preview("Белый хлеб",1)
        assertFalse(rows.single().resolved)
        assertEquals(setOf(bread,dried),bulk.candidates("Белый хлеб",1).map{it.id}.toSet())
        assertEquals(bread,bulk.preview("хлеб",1).single().productId)
        assertTrue(db.shoppingListEntryDao().getAllSync().isEmpty())
    }
    @Test fun atomicPackageMergesRegularRowsOnlyAndTokenPreventsDoubleSubmission():Unit=runBlocking {
        val id=product("Лук");val p=db.productDao().getById(id)!!
        db.productDao().update(p.copy(defaultQuantity=7.0))
        val list=db.goalListDao().insert(GoalListEntity(name="Отдельный список"))
        val other=basket.addProduct(id,9.0,listId=list)
        val rows=listOf(BulkProductRow(0,"лук",p.name,p.id,p.stableId,true,"2"),BulkProductRow(1,"лук",p.name,p.id,p.stableId,true,"1"),BulkProductRow(2,"гречка",resolved=true))
        val key=token();assertTrue(commit(rows,key));assertFalse(commit(rows,key))
        val entries=db.shoppingListEntryDao().getAllSync()
        assertEquals(3,entries.size);assertEquals(9.0,entries.single{it.id==other}.quantity,0.0)
        assertEquals(3.0,entries.single{it.listId==null&&it.productId==id}.quantity,0.0)
        val new=entries.single{it.listId==null&&it.productId!=id}
        assertEquals("упак",new.unit);assertTrue(db.productDao().getById(new.productId)!!.isTemporary)
        assertEquals(1,db.productDao().getAllSync().count{!it.isTemporary})
    }
    @Test fun staleChoiceRollsBackWholePackageIncludingNewProductsAndReceipt():Unit=runBlocking {
        val id=product("Хлеб");val p=db.productDao().getById(id)!!;db.productDao().delete(p)
        val key=token();val rows=listOf(BulkProductRow(0,"гречка",resolved=true),BulkProductRow(1,"Хлеб",p.name,p.id,p.stableId,true))
        assertTrue(runCatching{commit(rows,key)}.isFailure)
        assertTrue(db.productDao().getAllSync().isEmpty());assertTrue(db.shoppingListEntryDao().getAllSync().isEmpty());assertNull(db.productInputDao().run(key))
        val unresolved=rows.take(1).map{it.copy(resolved=false)}
        assertTrue(runCatching{commit(unresolved)}.isFailure)
    }
    @Test fun changedCityOrGenerationCannotSubmitOldPreview():Unit=runBlocking {
        val row=BulkProductRow(0,"Новый",resolved=true)
        places.select(2);assertTrue(runCatching{commit(listOf(row))}.isFailure)
        val generation=ProductEditorTransactions.generation.get()-1
        assertTrue(runCatching{bulk.add(token(),listOf(row),2,ShopChoice.AUTO,null,null,false,"",generation)}.isFailure)
        assertTrue(db.shoppingListEntryDao().getAllSync().isEmpty())
    }
    @Test fun fullBackupRestoresSettingsDefaultsAndCommitReceiptsAndRejectsCorruption():Unit=runBlocking {
        val id=product("Товар");val recipient=db.recipientDao().insert(RecipientEntity(name="Получатель"));input.setDepartment(id,type("Крупы"))
        input.updateSettings{it.copy(prefillUnit=false,inheritDepartment=false,recipientId=recipient,recipientEnabled=true)}
        val rows=listOf(BulkProductRow(0,"Новая позиция",resolved=true));val key=token();commit(rows,key)
        val manager=BackupManager(app,db);val file=manager.exportFile()
        assertEquals(28,manifest(file).getInt("version"));val policy=input.settings()
        input.updateSettings{it.copy(prefillUnit=true,inheritDepartment=true)};manager.importFile(file)
        assertEquals(policy,input.settings());assertEquals(type("Крупы"),input.department(id)!!.typeId);assertFalse(commit(rows,key))
        val count=db.shoppingListEntryDao().getAllSync().size;val generation=ProductEditorTransactions.generation.get()
        val broken=changed(file){it.getJSONObject("data").getJSONArray("productDefaultDepartments").getJSONObject(0).put("typeId","1")}
        assertTrue(runCatching{manager.importFile(broken)}.isFailure)
        val badFlag=changed(file){it.getJSONObject("data").getJSONArray("productInputSettings").getJSONObject(0).put("prefillEnabled","true")}
        assertTrue(runCatching{manager.importFile(badFlag)}.isFailure)
        val badRecipient=changed(file){it.getJSONObject("data").getJSONArray("productInputSettings").getJSONObject(0).put("recipientId",999999)}
        assertTrue(runCatching{manager.importFile(badRecipient)}.isFailure)
        assertEquals(count,db.shoppingListEntryDao().getAllSync().size);assertEquals(policy,input.settings());assertEquals(generation,ProductEditorTransactions.generation.get())
        db.openHelper.writableDatabase.query("PRAGMA integrity_check").use{assertTrue(it.moveToFirst());assertEquals("ok",it.getString(0))}
    }
    @Test fun oldFullBackupRemainsReadableWithoutInventingNewRules():Unit=runBlocking {
        val id=product("Старый товар");val manager=BackupManager(app,db);val file=manager.exportFile()
        val old=changed(file){it.put("version",24);listOf("productInputSettings","productDefaultDepartments","bulkPurchaseRuns").forEach{key->it.getJSONObject("data").remove(key)}}
        input.updateSettings{it.copy(prefillEnabled=false)};input.setDepartment(id,type("Молочка"))
        manager.importFile(old)
        assertEquals("Старый товар",db.productDao().getById(id)!!.name)
        assertNull(db.productInputDao().settings());assertNull(db.productInputDao().department(id));assertTrue(db.productInputDao().runs().isEmpty())
    }
    @Test fun preservingAndCopyingListRowsKeepsTheirIndependentDefaultDepartment():Unit=runBlocking {
        val id=product("Товар");val type=type("Крупы");input.setDepartment(id,type)
        val list=db.goalListDao().insert(GoalListEntity(name="Исходный"))
        val row=basket.addProduct(id,listId=list)
        products.deleteProduct(db.productDao().getById(id)!!)
        val retained=db.shoppingListEntryDao().getById(row)!!
        assertNotEquals(id,retained.productId);assertEquals(type,input.department(retained.productId)!!.typeId)
        val copy=GoalListRepository(db,basket).copyList(list,newListName="Копия")!!
        val copied=db.shoppingListEntryDao().getAllSync().single{it.listId==copy}
        assertNotEquals(retained.productId,copied.productId);assertEquals(type,input.department(copied.productId)!!.typeId)
        db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
    }
    @Test fun linkExtractionKeepsQueryAndBalancedParenthesesAndDoesNotChooseAmongSeveral() {
        val url="https://example.org/item(123)?sku=42&from=share#details"
        assertEquals(listOf(url),WebLinks.extract("Название товара\n$url\nОписание"))
        assertEquals(listOf("https://example.org/item(123)"),WebLinks.extract("(https://example.org/item(123))."))
        assertEquals(listOf("https://example.org/a","https://example.net/b"),WebLinks.extract("https://example.org/a\nhttps://example.net/b"))
        assertEquals(listOf("https://example.org/a"),WebLinks.extract("example.org/a"))
        assertTrue(WebLinks.extract("Просто текст без ссылки").isEmpty());assertTrue(WebLinks.extract("javascript:alert(1)").isEmpty())
    }
}
