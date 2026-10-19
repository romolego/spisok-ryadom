package com.spisokryadom.app
import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.ProductRepository
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class DraftDepartmentTest {
 private lateinit var db:AppDatabase
 private lateinit var repo:ProductRepository
 private var shop=0L;private var other=0L;private var point=0L
 @Before fun setup()=runBlocking{db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),AppDatabase::class.java).allowMainThreadQueries().build();repo=ProductRepository(db.productDao(),db.productShopLinkDao(),db);db.cityDao().insert(CityEntity(1,"Город"));shop=db.shopDao().insert(ShopEntity(name="Сеть"));other=db.shopDao().insert(ShopEntity(name="Другой"));point=db.shopBranchDao().insert(ShopBranchEntity(shopId=shop,address="Третий адрес",displayOrder=2));Unit}
 @After fun close(){db.close()}
 @Test fun newDepartmentAndRestrictionAreAtomicAndUnusedDraftIsNotStored()=runBlocking{
  val draft=ShopDepartmentEntity(id=-1,shopId=shop,name="Чай",displayOrder=1)
  val id=repo.saveWithLinks(ProductEntity(name="Зелёный чай"),listOf(ProductShopLinkEntity(productId=0,shopId=shop,departmentId=-1)),mapOf(shop to setOf(point)),listOf(draft,ShopDepartmentEntity(id=-2,shopId=other,name="Не используется")))
  val link=db.productShopLinkDao().getLinksForProductSync(id).single();assertTrue(link.departmentId!!>0);assertEquals("Чай",db.shopDepartmentDao().getById(link.departmentId)!!.name);assertTrue(link.restrictBranches);assertEquals(point,db.shopBranchDao().availability(id).single().branchId);assertTrue(db.shopDepartmentDao().getByShopIdSync(other).isEmpty())
 }
 @Test fun badBranchRollsBackProductAndNewGroup()=runBlocking{
  val foreign=db.shopBranchDao().insert(ShopBranchEntity(shopId=other,address="Другой адрес"))
  try{repo.saveWithLinks(ProductEntity(name="Чай"),listOf(ProductShopLinkEntity(productId=0,shopId=shop,departmentId=-1)),mapOf(shop to setOf(foreign)),listOf(ShopDepartmentEntity(id=-1,shopId=shop,name="Напитки")));fail("Must reject foreign point")}catch(expected:IllegalArgumentException){}
  assertTrue(db.productDao().getAllSync().isEmpty());assertTrue(db.shopDepartmentDao().getByShopIdSync(shop).isEmpty())
 }
 @Test fun groupCreatedElsewhereWhileProductIsOpenIsReusedWithinSameShop()=runBlocking{
  val existing=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=shop,name="Чай и кофе",displayOrder=5))
  val elsewhere=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=other,name="Чай и кофе"))
  val id=repo.saveWithLinks(ProductEntity(name="Чай"),listOf(ProductShopLinkEntity(productId=0,shopId=shop,departmentId=-3)),departments=listOf(ShopDepartmentEntity(id=-3,shopId=shop,name=" чай и кофе ")))
  assertEquals(existing,db.productShopLinkDao().getLinksForProductSync(id).single().departmentId);assertEquals(1,db.shopDepartmentDao().getByShopIdSync(shop).size);assertEquals(elsewhere,db.shopDepartmentDao().getByShopIdSync(other).single().id)
 }
 @Test fun reusingProductPreservesOldLinkAndAttachesNewGroupAndSubset()=runBlocking{
  val p=db.productDao().insert(ProductEntity(name="Цукаты",note="Не менять"))
  val old=db.productShopLinkDao().insert(ProductShopLinkEntity(productId=p,shopId=other,priority=7))
  val draft=ProductShopLinkEntity(productId=p,shopId=shop,departmentId=-1)
  repo.ensureShopLinks(p,listOf(other,shop),listOf(draft),mapOf(shop to setOf(point)),listOf(ShopDepartmentEntity(id=-1,shopId=shop,name="Сладости")))
  val links=db.productShopLinkDao().getLinksForProductSync(p)
  assertEquals(old,links.first{it.shopId==other}.id);assertEquals(7,links.first{it.shopId==other}.priority)
  val added=links.first{it.shopId==shop};assertTrue(added.restrictBranches);assertEquals("Сладости",db.shopDepartmentDao().getById(added.departmentId!!)!!.name)
  assertEquals(point,db.shopBranchDao().availability(p).single().branchId);assertEquals("Не менять",db.productDao().getById(p)!!.note)
 }

}
