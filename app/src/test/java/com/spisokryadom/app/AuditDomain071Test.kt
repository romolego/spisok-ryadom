package com.spisokryadom.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.db.RetailConstraints
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class AuditDomain071Test {
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:AppDatabase
    private lateinit var locations:LocationRepository
    private lateinit var products:ProductRepository
    private lateinit var basket:ShoppingListRepository
    private var a=0L
    private var b=0L
    private var first=0L
    private var second=0L

    @Before fun setup()=runBlocking {
        db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build()
        RetailConstraints.install(db.openHelper.writableDatabase)
        db.cityDao().insert(CityEntity(1,"Красноярск"))
        db.cityDao().insert(CityEntity(2,"Томск"))
        locations=LocationRepository(db.cityDao(),db.shopDao(),app.getSharedPreferences("audit-domain-${UUID.randomUUID()}",0))
        locations.select(1)
        products=ProductRepository(db.productDao(),db.productShopLinkDao(),db)
        basket=ShoppingListRepository(db.shoppingListEntryDao(),db,locations)
        a=db.shopDao().insert(ShopEntity(name="Батон"))
        b=db.shopDao().insert(ShopEntity(name="Красный Яр",displayOrder=1))
        first=db.shopBranchDao().insert(ShopBranchEntity(shopId=a,address="Первый адрес"))
        second=db.shopBranchDao().insert(ShopBranchEntity(shopId=a,address="Второй адрес",displayOrder=1))
        Unit
    }
    @After fun close(){locations.closeForTest();db.close()}
    private suspend fun card(both:Boolean=true):Long=products.saveWithLinks(
        ProductEntity(name="Чай",defaultUnit="уп"),
        listOf(ProductShopLinkEntity(productId=0,shopId=a))+
            if(both)listOf(ProductShopLinkEntity(productId=0,shopId=b,priority=2))else emptyList()
    )

    @Test fun removingShopFromContentsUsesTheSamePendingPolicyAsTheCard()=runBlocking {
        val p=card()
        val pending=basket.addProduct(p,3.0)
        val bought=basket.addProduct(p,unit="коробка").also{basket.markBought(it)}
        val none=basket.addProduct(p,2.0,choice=ShopChoice.NONE)
        val initial=db.shoppingListEntryDao().getById(pending)!!
        val history=db.shoppingListEntryDao().getById(bought)!!
        val explicitNone=db.shoppingListEntryDao().getById(none)!!
        val retainedLink=db.productShopLinkDao().getLinksForProductSync(p).single{it.shopId==b}
        products.toggleShopLink(p,a)
        val actual=db.shoppingListEntryDao().getById(pending)!!
        assertEquals(b,actual.assignedShopId)
        assertNull(actual.assignedBranchId)
        assertEquals(initial.copy(assignedShopId=b,assignedBranchId=null,updatedAt=actual.updatedAt),actual)
        assertEquals(history,db.shoppingListEntryDao().getById(bought))
        assertEquals(explicitNone,db.shoppingListEntryDao().getById(none))
        assertEquals(retainedLink,db.productShopLinkDao().getLinksForProductSync(p).single())
        products.toggleShopLink(p,a)
        assertEquals(actual,db.shoppingListEntryDao().getById(pending))
    }

    @Test fun removingOneLinkDirectlyAlsoUpdatesPendingPurchases()=runBlocking {
        val p=card()
        val id=basket.addProduct(p)
        products.deleteShopLink(db.productShopLinkDao().getLinksForProductSync(p).single{it.shopId==a})
        assertEquals(b,db.shoppingListEntryDao().getById(id)!!.assignedShopId)
    }

    @Test fun losingLastLinkRetainsOriginalCityUntilTheCardCanResolveItAgain()=runBlocking {
        val p=card(false)
        val id=basket.addProduct(p,7.0)
        val none=basket.addProduct(p,choice=ShopChoice.NONE)
        products.deleteAllShopLinks(p)
        val waiting=db.shoppingListEntryDao().getById(id)!!
        assertNull(waiting.assignedShopId)
        assertEquals(1L,waiting.awaitingShopCityId)
        assertFalse(waiting.explicitNoShop)
        val foreign=db.shopDao().insert(ShopEntity(name="Другой город",cityId=2))
        locations.select(2)
        products.saveWithLinks(db.productDao().getById(p)!!,listOf(
            ProductShopLinkEntity(productId=p,shopId=foreign,priority=1),
            ProductShopLinkEntity(productId=p,shopId=b,priority=2)))
        val resolved=db.shoppingListEntryDao().getById(id)!!
        assertEquals(b,resolved.assignedShopId)
        assertNull(resolved.awaitingShopCityId)
        assertEquals(7.0,resolved.quantity,0.0)
        assertTrue(db.shoppingListEntryDao().getById(none)!!.explicitNoShop)
        assertNull(db.shoppingListEntryDao().getById(none)!!.assignedShopId)
    }

    @Test fun batchNewProductsRespectExactAndExplicitlyUnassignedAddresses()=runBlocking {
        basket.addBatch(listOf(
            ShoppingListRepository.PurchaseRequest(null,"Орешки",2.0,"уп",choice=ShopChoice.EXPLICIT,shopId=a,branchId=second),
            ShoppingListRepository.PurchaseRequest(null,"Сухофрукты",1.0,"кг",choice=ShopChoice.EXPLICIT,shopId=a,branchId=0)),1)
        val rows=db.shoppingListEntryDao().getAllSync().associateBy{db.productDao().getById(it.productId)!!.name}
        assertEquals(second,rows.getValue("Орешки").assignedBranchId)
        assertNull(rows.getValue("Сухофрукты").assignedBranchId)
        assertEquals(a,rows.getValue("Сухофрукты").assignedShopId)
    }

    @Test fun repeatedPermanentBatchProductRetainsEverySelectedShopAndOneCard()=runBlocking {
        basket.addBatch(listOf(
            ShoppingListRepository.PurchaseRequest(null,"Цукаты",1.0,"кг",permanent=true,choice=ShopChoice.EXPLICIT,shopId=a,branchId=second),
            ShoppingListRepository.PurchaseRequest(null,"цукаты",2.0,"кг.",permanent=true,choice=ShopChoice.EXPLICIT,shopId=b)),1)
        val product=db.productDao().getAllSync().single()
        assertFalse(product.isTemporary)
        assertEquals(setOf(a,b),db.productShopLinkDao().getLinksForProductSync(product.id).map{it.shopId}.toSet())
        assertEquals(setOf(product.id),db.shoppingListEntryDao().getAllSync().map{it.productId}.toSet())
        assertEquals(second,db.shoppingListEntryDao().getAllSync().single{it.assignedShopId==a}.assignedBranchId)
    }

    @Test fun mixedTemporaryAndPermanentBatchLinesRetainOneCard()=runBlocking {
        basket.addBatch(listOf(
            ShoppingListRepository.PurchaseRequest(null,"Курага",1.0,"кг",permanent=false),
            ShoppingListRepository.PurchaseRequest(null,"курага",2.0,"кг.",permanent=true)),1)
        val product=db.productDao().getAllSync().single()
        assertFalse(product.isTemporary)
        assertEquals(product.id,db.shoppingListEntryDao().getAllSync().single().productId)
        assertEquals(3.0,db.shoppingListEntryDao().getAllSync().single().quantity,0.0)
        db.shoppingListEntryDao().getAllSync().forEach{basket.removeEntry(it.id)}
        assertNotNull(db.productDao().getById(product.id))
    }

    @Test fun repeatedNewTextProductHonorsPermanentChoiceInEitherRowOrder()=runBlocking {
        for(permanentFirst in listOf(false,true)) {
            val name=if(permanentFirst)"Цукаты"else "Курага"
            val base=basket.textSnapshot().entries
            val keep=base.map{row->ShoppingListRepository.PurchaseRequest(row.productId,"",row.quantity,row.unit,choice=ShopChoice.NONE,entryId=row.id)}
            basket.replacePendingText(base,keep+listOf(
                ShoppingListRepository.PurchaseRequest(null,name,1.0,"кг",permanent=permanentFirst),
                ShoppingListRepository.PurchaseRequest(null,name.lowercase(),2.0,"кг.",permanent=!permanentFirst)))
            val product=db.productDao().getAllSync().single{it.name==name}
            assertFalse("Explicit catalogue retention must win in either row order",product.isTemporary)
            assertEquals(2,db.shoppingListEntryDao().getAllSync().count{it.productId==product.id})
        }
        db.shoppingListEntryDao().getAllSync().forEach{basket.removeEntry(it.id)}
        assertTrue(db.shoppingListEntryDao().getAllSync().isEmpty())
        assertEquals(2,db.productDao().getAllSync().size)
    }

    @Test fun repeatedNewTextProductAutomaticRowSeesTheLinkCreatedByThePreviousRow()=runBlocking {
        basket.replacePendingText(emptyList(),listOf(
            ShoppingListRepository.PurchaseRequest(null,"Зелёный чай",1.0,"уп",permanent=true,choice=ShopChoice.EXPLICIT,shopId=b),
            ShoppingListRepository.PurchaseRequest(null,"зелёный чай",2.0,"уп",choice=ShopChoice.AUTO)))
        assertEquals(1,db.productDao().getAllSync().size)
        assertTrue(db.shoppingListEntryDao().getAllSync().all{it.assignedShopId==b})
    }

    @Test fun unchangedTextKeepsNoAddressWhileExplicitPreferredAndExactChoicesApply()=runBlocking {
        val p=card(false)
        val id=basket.addProduct(p,choice=ShopChoice.EXPLICIT,shopId=a,branchId=0)
        val old=db.shoppingListEntryDao().getById(id)!!
        fun request(branch:Long,explicit:Boolean)=ShoppingListRepository.PurchaseRequest(p,"Чай",old.quantity,old.unit,choice=ShopChoice.EXPLICIT,shopId=a,entryId=id,branchId=branch,assignmentExplicit=explicit)
        basket.replacePendingText(listOf(old),listOf(request(-1,false)))
        assertEquals(old,db.shoppingListEntryDao().getById(id))
        basket.replacePendingText(basket.textSnapshot().entries,listOf(request(-1,true)))
        assertEquals(first,db.shoppingListEntryDao().getById(id)!!.assignedBranchId)
        basket.replacePendingText(basket.textSnapshot().entries,listOf(request(second,true)))
        assertEquals(second,db.shoppingListEntryDao().getById(id)!!.assignedBranchId)
        basket.replacePendingText(basket.textSnapshot().entries,listOf(request(-1,false)))
        assertEquals(first,db.shoppingListEntryDao().getById(id)!!.assignedBranchId)
    }

    @Test fun unitUsageFindsCanonicalCardAndLegacyAliasSpellings()=runBlocking {
        val p=db.productDao().insert(ProductEntity(name="Банан",defaultUnit="кг"))
        val legacy=db.productDao().insert(ProductEntity(name="Яблоко",defaultUnit="КИЛОГРАММ"))
        db.productDao().insert(ProductEntity(name="Молоко",defaultUnit="л"))
        assertEquals(setOf(p,legacy),products.getProductsByUnit("кг.").first().map{it.id}.toSet())
    }
}
