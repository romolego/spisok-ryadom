package com.spisokryadom.app

import android.app.Application
import android.os.Looper
import androidx.lifecycle.*
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.*
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.ui.productcard.*
import com.spisokryadom.app.ui.addtolist.BulkProductViewModel
import com.spisokryadom.app.util.ProductCreationDefaults
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ProductInput300EditorTest {
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:AppDatabase
    private lateinit var places:LocationRepository
    private lateinit var products:ProductRepository
    private lateinit var shops:ShopRepository
    private lateinit var classifier:ClassifierRepository
    private val models=ViewModelStore()
    private val prefs get()=app.getSharedPreferences("300-editing",0)
    private val drafts get()=app.getSharedPreferences("300-drafts",0)
    private fun await(check:()->Boolean){
        val end=System.nanoTime()+25_000_000_000
        while(!check()&&System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50));Thread.sleep(5)}
        assertTrue(check())
    }
    @Before fun setup():Unit=runBlocking {
        prefs.edit().clear().putBoolean("autosave_product_cards",false).commit();drafts.edit().clear().commit()
        db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build()
        RetailConstraints.install(db.openHelper.writableDatabase);StandardCategories.install(db.openHelper.writableDatabase)
        db.cityDao().insert(CityEntity(1,"Город"));app.getSharedPreferences("300-loc",0).edit().clear().commit()
        places=LocationRepository(db.cityDao(),db.shopDao(),app.getSharedPreferences("300-loc",0))
        products=ProductRepository(db.productDao(),db.productShopLinkDao(),db)
        shops=ShopRepository(db.shopDao(),db.shopDepartmentDao(),places,db)
        classifier=ClassifierRepository(db.productGroupDao(),db.recipientDao(),db.unitDao(),db)
    }
    @After fun close(){models.clear();Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));await{!ProductEditorTransactions.mutex.isLocked};places.closeForTest();db.close()}
    private fun card(id:Long=-1)=ProductCardViewModel(id,-1,products,shops,classifier,drafts,prefs).also{models.put("card-$id",it);await{!it.uiState.value.isLoading}}
    @Test fun immediateSaveFillsKnownNewProductWithoutWaitingForSearchDebounce() {
        val vm=card();vm.updateName("спагетти")
        assertTrue(runBlocking{db.productDao().getAllSync().isEmpty()})
        vm.save();await{vm.uiState.value.isSaved}
        val p=runBlocking{db.productDao().getAllSync().single()}
        assertEquals("спагетти",p.name);assertEquals("упак",p.defaultUnit);assertNotNull(p.productGroupId)
        assertEquals("Макароны",runBlocking{db.shopDepartmentDao().type(products.input.department(p.id)!!.typeId!!)!!.name})
    }
    @Test fun renamedNewDraftKeepsManualValuesIncludingIntentionalEmptyFields() {
        val vm=card();vm.updateName("спагетти");await{vm.uiState.value.defaultUnit=="упак"}
        vm.updateDefaultUnit("шт");vm.updateProductGroup(null);vm.updateDefaultDepartment(null);vm.updateRecipient(null)
        vm.updateName("бананы");vm.save();await{vm.uiState.value.isSaved}
        val p=runBlocking{db.productDao().getAllSync().single()}
        assertEquals("шт",p.defaultUnit);assertNull(p.productGroupId);assertNull(p.recipientId)
        assertNull(runBlocking{db.productInputDao().department(p.id)!!.typeId})
    }
    @Test fun rawRecoveryRetainsManualClearsInsteadOfApplyingHintsOrFallbackAgain():Unit=runBlocking {
        val state=ProductCardUiState(name="гречка",isNew=true,isLoading=false,categoryManuallySet=true,unitManuallySet=true,
            departmentManuallySet=true,defaultDepartmentChosen=true,defaultDepartmentDirty=true,recipientManuallySet=true)
        val original=ProductEditDraft("input300-recovery",-1,true,false,state)
        val recovered=ProductEditDraft.decode(original.encode())
        val id=recovered.persist(products,ProductCreationDefaults(fillMissing=true))
        val p=db.productDao().getById(id)!!
        assertNull(p.productGroupId);assertNull(p.defaultUnit);assertNull(p.recipientId)
        assertNotNull(db.productInputDao().department(id));assertNull(db.productInputDao().department(id)!!.typeId)
    }
    @Test fun explicitNoDepartmentInNewShopOverridesTheProductDefault() {
        val pair=runBlocking{
            val id=db.productDao().insert(ProductEntity(name="Товар"));val type=db.shopDepartmentDao().types().first().id
            products.input.setDepartment(id,type);id to db.shopDao().insert(ShopEntity(name="Магазин"))
        }
        val vm=card(pair.first);vm.loadDepartmentsForShop(pair.second)
        await{vm.uiState.value.suggestedDepartmentId!=null}
        vm.addShopLink(pair.second,null);vm.configureShop(pair.second,null,null,emptyList());vm.save();await{vm.uiState.value.isSaved}
        assertNull(runBlocking{db.productShopLinkDao().getLinksForProductSync(pair.first).single().departmentId})
        assertTrue(runBlocking{db.shopDepartmentDao().getByShopIdSync(pair.second).isEmpty()})
    }
    @Test fun existingCardStillCreatesAThirdVariantWithoutChangingSharedFields() {
        val root=runBlocking{
            val id=db.productDao().insert(ProductEntity(name="Огурцы",defaultUnit="шт"))
            products.variants.create(id,"Марка Б");id
        }
        val vm=card(root);vm.createVariant("Марка В")
        await{vm.uiState.value.variantOptions.size==3||vm.uiState.value.error!=null}
        assertNull(vm.uiState.value.error)
        assertEquals(3,runBlocking{db.productDao().getAllSync().size})
        assertTrue(runBlocking{db.productDao().getAllSync().all{it.name=="Огурцы"&&it.defaultUnit=="шт"}})
    }
    @Test fun aNewInlineDepartmentIsSharedWithTheNextShopAndCommittedOnce() {
        val stores=runBlocking{listOf(db.shopDao().insert(ShopEntity(name="Первый")),db.shopDao().insert(ShopEntity(name="Второй")))}
        val vm=card();await{vm.uiState.value.shops.map{it.id}.containsAll(stores)}
        vm.updateName("Мой товар");vm.loadDepartmentsForShop(stores[0]);await{vm.uiState.value.departmentsShopId==stores[0]}
        var draft=0L
        vm.createShopDepartment(stores[0],"Мой новый отдел"){draft=it}
        assertTrue(draft<0);vm.addPendingShopLink(stores[0],draft);vm.configureShop(stores[0],draft,null,emptyList())
        vm.loadDepartmentsForShop(stores[1]);await{vm.uiState.value.suggestedDepartmentId!=null}
        val next=vm.uiState.value.suggestedDepartmentId!!
        vm.addPendingShopLink(stores[1],next);vm.configureShop(stores[1],next,null,emptyList())
        assertTrue(runBlocking{db.shopDepartmentDao().getAllSync().isEmpty()})
        vm.save();await{vm.uiState.value.isSaved||vm.uiState.value.error!=null};assertNull(vm.uiState.value.error)
        val product=runBlocking{db.productDao().getAllSync().single()}
        val type=runBlocking{db.shopDepartmentDao().types().single{it.name=="Мой новый отдел"}.id}
        assertEquals(type,runBlocking{db.productInputDao().department(product.id)!!.typeId})
        assertEquals(2,runBlocking{db.productShopLinkDao().getLinksForProductSync(product.id).size})
        assertTrue(runBlocking{db.shopDepartmentDao().getAllSync().all{it.typeId==type}})
    }
    @Test fun restoredBulkPreviewKeepsChoiceAndQuantityAndCannotCommitTwice() {
        val product=runBlocking{val id=db.productDao().insert(ProductEntity(name="Хлеб",defaultUnit="шт"));db.productDao().getById(id)!!}
        val repo=BulkPurchaseRepository(db,ShoppingListRepository(db.shoppingListEntryDao(),db,places),places)
        val handle=SavedStateHandle()
        val first=BulkProductViewModel(repo,handle,false);models.put("bulk-first",first)
        first.text("Белый хлеб");first.prepare();await{first.state.value.preview}
        assertFalse(first.state.value.rows.single().resolved)
        first.choose(0);await{!first.state.value.searching&&first.state.value.candidates.isNotEmpty()}
        first.select(first.state.value.candidates.single());first.quantity(0,"3")
        val restoredHandle=SavedStateHandle(handle.keys().associateWith{handle.get<Any>(it)})
        first.add(-1,null);await{first.state.value.completed}
        val second=BulkProductViewModel(repo,restoredHandle,false);models.put("bulk-second",second)
        assertEquals(product.id,second.state.value.rows.single().productId);assertEquals("3",second.state.value.rows.single().quantity)
        second.add(-1,null);await{second.state.value.completed}
        val rows=runBlocking{db.shoppingListEntryDao().getAllSync()};assertEquals(1,rows.size);assertEquals(3.0,rows.single().quantity,0.0)
    }
    @Test fun savedPreviewFromAnotherDatabaseRevisionCannotWriteAfterProcessRecreation() {
        runBlocking{db.productDao().insert(ProductEntity(name="Хлеб"))}
        val repo=BulkPurchaseRepository(db,ShoppingListRepository(db.shoppingListEntryDao(),db,places),places)
        var revision="before"
        val handle=SavedStateHandle();val first=BulkProductViewModel(repo,handle,false,revision={revision});models.put("before-restore",first)
        first.text("Хлеб");first.prepare();await{first.state.value.preview}
        val saved=SavedStateHandle(handle.keys().associateWith{handle.get<Any>(it)})
        revision="after"
        val restored=BulkProductViewModel(repo,saved,false,revision={revision});models.put("after-restore",restored)
        restored.add(-1,null);await{restored.state.value.error!=null}
        assertTrue(runBlocking{db.shoppingListEntryDao().getAllSync().isEmpty()})
    }
    @Test fun externalCatalogueChangesToNewDraftAreNotUndoneByHints():Unit=runBlocking {
        val originalGroup=db.productGroupDao().getAllSync().first{it.name=="Продукты"}.id
        val otherGroup=db.productGroupDao().getAllSync().first{it.name=="Товары для дома"}.id
        val recipient=db.recipientDao().insert(RecipientEntity(name="Другой получатель"))
        val id=db.productDao().insert(ProductEntity(name="бананы",stableId="input300-external",productGroupId=originalGroup,defaultUnit="шт"))
        val raw=ProductEditDraft("input300-external",id,true,false,ProductCardUiState(name="бананы",productGroupId=originalGroup,defaultUnit="шт",isLoading=false))
        ProductDraftStore.write(drafts,"external",raw)
        val sync=ProductDraftSynchronizer(drafts)
        sync.change(db){val p=db.productDao().getById(id)!!;db.productDao().update(p.copy(productGroupId=otherGroup,recipientId=recipient,defaultUnit="упак"))}
        val updated=ProductDraftStore.find(drafts,"input300-external")!!
        assertTrue(updated.state.categoryManuallySet);assertTrue(updated.state.unitManuallySet);assertTrue(updated.state.recipientManuallySet)
        updated.persist(products)
        val result=db.productDao().getById(id)!!
        assertEquals(otherGroup,result.productGroupId);assertEquals("упак",result.defaultUnit);assertEquals(recipient,result.recipientId)
    }
    @Test fun quickCreationKeepsAnExplicitlyClearedUnitInsteadOfReplacingItWithAHint() {
        val basket=ShoppingListRepository(db.shoppingListEntryDao(),db,places)
        val vm=com.spisokryadom.app.ui.addtolist.AddToListViewModel(products,shops,basket,prefs)
        models.put("quick",vm)
        vm.onSearchQueryChanged("гречка");await{vm.uiState.value.isNewProduct&&!vm.uiState.value.isSearching}
        vm.unit("");vm.quantity("1");vm.add();await{vm.uiState.value.addedMessage!=null||vm.uiState.value.error!=null}
        assertNull(vm.uiState.value.error)
        val row=runBlocking{db.shoppingListEntryDao().getAllSync().single()}
        assertEquals("",row.unit);assertNull(runBlocking{db.productDao().getById(row.productId)!!.defaultUnit})
    }
}
