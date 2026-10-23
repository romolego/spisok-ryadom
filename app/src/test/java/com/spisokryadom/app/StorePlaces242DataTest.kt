package com.spisokryadom.app

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.ui.shopcard.ShopCardViewModel
import com.spisokryadom.app.ui.shoplist.ShopListViewModel
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class StorePlaces242DataTest {
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 private lateinit var db:AppDatabase
 private lateinit var locations:LocationRepository
 private lateinit var shops:ShopRepository
 private val models=ViewModelStore()
 private val prefs get()=app.getSharedPreferences("242-location",0)
 @Before fun setup()=runBlocking{
  prefs.edit().clear().commit()
  db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build()
  db.cityDao().insert(CityEntity(1,"Красноярск"))
  locations=LocationRepository(db.cityDao(),db.shopDao(),prefs)
  shops=ShopRepository(db.shopDao(),db.shopDepartmentDao(),locations,db)
 }
 @After fun close(){models.clear();Shadows.shadowOf(Looper.getMainLooper()).idle();locations.closeForTest();db.close()}
 private fun await(check:()->Boolean){val end=System.nanoTime()+15_000_000_000;while(!check()&&System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5)};assertTrue(check())}
 @Test fun cardCityCreationAndDuplicateLookupDoNotChangeTheShoppingCity()=runBlocking{
  val id=locations.add("Томск",selectAfterCreate=false)
  assertEquals(1L,locations.currentId());assertFalse(prefs.contains("active_city"))
  assertEquals(id,locations.add("  ТОМСК  ",selectAfterCreate=false))
  assertEquals(1L,locations.currentId());assertEquals(2,db.cityDao().getAllSync().size)
  assertEquals(id,locations.add("Томск"));assertEquals(id,locations.currentId())
  assertEquals(id,prefs.getLong("active_city",0))
 }
 @Test fun cityFilterIncludesEmptyMarketsAndReactsToSwitchAndRename(){
  runBlocking{
   db.cityDao().insert(CityEntity(2,"Томск"));db.cityDao().insert(CityEntity(3,"Пустой город"))
   db.shopDao().insert(ShopEntity(name="Здесь",cityId=1));db.shopDao().insert(ShopEntity(name="В Томске",cityId=2))
   db.retailStructureDao().insertVenue(VenueEntity(cityId=1,name="Местный рынок"))
   db.retailStructureDao().insertVenue(VenueEntity(cityId=2,name="Томский рынок"))
  }
  val vm=ShopListViewModel(shops);models.put("shops",vm)
  await{!vm.uiState.value.isLoading&&vm.uiState.value.shops.map{it.name}==listOf("Здесь")}
  assertEquals(listOf("Местный рынок"),vm.uiState.value.venues.map{it.name})
  locations.select(2)
  await{vm.uiState.value.shops.map{it.name}==listOf("В Томске")&&vm.uiState.value.venues.map{it.name}==listOf("Томский рынок")}
  runBlocking{locations.rename(db.cityDao().getById(2)!!,"Томск обновлённый")}
  await{locations.activeCity.value.name=="Томск обновлённый"}
  assertTrue(vm.uiState.value.groups.any{it.venue?.name=="Томский рынок"&&it.shops.isEmpty()})
  locations.select(3);await{vm.uiState.value.shops.isEmpty()&&vm.uiState.value.venues.isEmpty()}
  assertEquals(2,runBlocking{db.shopDao().getAllSync().size})
  locations.select(1);await{vm.uiState.value.shops.map{it.name}==listOf("Здесь")}
 }
 @Test fun savingStoreInAnotherCityKeepsItsIdentityAndTheShoppingCity(){
  val id=runBlocking{db.cityDao().insert(CityEntity(2,"Томск"));db.shopDao().insert(ShopEntity(name="Магазин",cityId=1))}
  val vm=ShopCardViewModel(id,shops);models.put("card",vm);await{!vm.uiState.value.isLoading}
  vm.updateCity(2);await{!vm.uiState.value.isLoading&&vm.uiState.value.cityId==2L};assertEquals(1L,runBlocking{locations.currentId()})
  vm.updateBranches(listOf(ShopBranchEntity(shopId=0,address="Адрес в Томске")));vm.save();await{vm.uiState.value.isSaved}
  val result=runBlocking{db.shopDao().getById(id)!!}
  assertEquals(id,result.id);assertEquals(1L,result.cityId);assertEquals(1L,runBlocking{locations.currentId()})
  val peer=runBlocking{shops.cityProfiles(id).single{it.cityId==2L}};assertNotEquals(id,peer.id);assertEquals("Адрес в Томске",runBlocking{shops.branches(peer.id).single().address})
 }
}
