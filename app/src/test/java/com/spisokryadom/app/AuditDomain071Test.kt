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
        products.toggleShopLink(p,a)
        assertEquals(b,db.shoppingListEntryDao().getById(id)!!.assignedShopId)
    }

    @Test fun losingLastLinkRetainsOriginalCityUntilTheCardCanResolveItAgain()=runBlocking {
        val p=card(false)
        val id=basket.addProduct(p,7.0)
        val none=basket.addProduct(p,choice=ShopChoice.NONE)
        products.saveWithLinks(products.getProductById(p)!!,emptyList())
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

    @Test fun newProductsRespectExactAndExplicitlyUnassignedAddresses()=runBlocking {
        basket.addNewProduct("Орешки",2.0,"уп",false,ShopChoice.EXPLICIT,a,second)
        basket.addNewProduct("Сухофрукты",1.0,"кг",false,ShopChoice.EXPLICIT,a,0)
        val rows=db.shoppingListEntryDao().getAllSync().associateBy{db.productDao().getById(it.productId)!!.name}
        assertEquals(second,rows.getValue("Орешки").assignedBranchId)
        assertNull(rows.getValue("Сухофрукты").assignedBranchId)
        assertEquals(a,rows.getValue("Сухофрукты").assignedShopId)
    }

    @Test fun unitUsageFindsCanonicalCardAndLegacyAliasSpellings()=runBlocking {
        val p=db.productDao().insert(ProductEntity(name="Банан",defaultUnit="кг"))
        val legacy=db.productDao().insert(ProductEntity(name="Яблоко",defaultUnit="КИЛОГРАММ"))
        db.productDao().insert(ProductEntity(name="Молоко",defaultUnit="л"))
        assertEquals(setOf(p,legacy),products.getProductsByUnit("кг.").first().map{it.id}.toSet())
    }
}
