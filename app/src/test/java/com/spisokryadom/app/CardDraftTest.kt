package com.spisokryadom.app
import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.ui.productcard.ProductCardViewModel
import com.spisokryadom.app.ui.shopcard.ShopCardViewModel
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.*
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
class CardDraftTest {
 private fun await(condition:()->Boolean){val end=System.nanoTime()+10_000_000_000;while(!condition()&&System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5)};assertTrue("ViewModel did not finish",condition())}
 @Test fun cardChangesAreDraftsAndRepeatedSaveDoesNotDuplicate()=runBlocking {
  val context=ApplicationProvider.getApplicationContext<Application>();val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).allowMainThreadQueries().build()
  db.cityDao().insert(CityEntity(1,"Красноярск"));val locations=LocationRepository(db.cityDao(),db.shopDao(),context.getSharedPreferences("draft_test_location",0))
  val products=ProductRepository(db.productDao(),db.productShopLinkDao(),db);val shops=ShopRepository(db.shopDao(),db.shopDepartmentDao(),locations,db);val classifiers=ClassifierRepository(db.productGroupDao(),db.recipientDao(),db.unitDao(),db)
  val store=ViewModelStore()
  try{
   val shop=db.shopDao().insert(ShopEntity(name="Батон"));val dept=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=shop,name="Фрукты"))
   val p=db.productDao().insert(ProductEntity(name="Гала"));db.productShopLinkDao().insert(ProductShopLinkEntity(productId=p,shopId=shop,departmentId=dept))
   val product=ProductCardViewModel(p,productRepository=products,shopRepository=shops,classifierRepository=classifiers);store.put("product",product)
   await{!product.uiState.value.isLoading};product.removeShopLink(product.uiState.value.shopLinks.single().link)
   assertEquals(1,db.productShopLinkDao().getLinksForProductSync(p).size)
   product.save();await{product.uiState.value.isSaved||product.uiState.value.error!=null};assertNull(product.uiState.value.error);assertTrue(db.productShopLinkDao().getLinksForProductSync(p).isEmpty())
   val card=ShopCardViewModel(shop,shops);store.put("shop",card);await{!card.uiState.value.isLoading};card.deleteDepartment(card.uiState.value.departments.single())
   assertNotNull(db.shopDepartmentDao().getById(dept));card.save();await{card.uiState.value.isSaved||card.uiState.value.error!=null};assertNull(card.uiState.value.error);assertNull(db.shopDepartmentDao().getById(dept))
   val fresh=ProductCardViewModel(-1,productRepository=products,shopRepository=shops,classifierRepository=classifiers);store.put("fresh",fresh);fresh.updateName("Новый");fresh.save();fresh.save();await{fresh.uiState.value.isSaved||fresh.uiState.value.error!=null}
   assertNull(fresh.uiState.value.error);assertEquals(1,db.productDao().getAllSync().count{it.name=="Новый"})
  }finally{store.clear();locations.closeForTest();db.close()}
 }
}
