package com.spisokryadom.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.backup.BackupManager
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.ui.addtolist.*
import com.spisokryadom.app.ui.theme.SpisokRyadomTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.*
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=Application::class,qualifiers="w360dp-h740dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class Audit201UiTest {
    @get:Rule val ui=createComposeRule()
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:AppDatabase
    private lateinit var locations:LocationRepository
    private lateinit var shops:ShopRepository
    private lateinit var vm:AddToListViewModel
    private val store=ViewModelStore()
    private var product=0L;private var venue=0L
    private var root:View?=null
    @Before fun setup():Unit=runBlocking {
        app.getSharedPreferences("shopping_list_prefs",0).edit().clear().commit()
        db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build()
        db.cityDao().insert(CityEntity(1,"Город"))
        locations=LocationRepository(db.cityDao(),db.shopDao(),app.getSharedPreferences("location_prefs",0))
        shops=ShopRepository(db.shopDao(),db.shopDepartmentDao(),locations,db)
        val shop=db.shopDao().insert(ShopEntity(name="Павильон 29"))
        venue=db.retailStructureDao().insertVenue(VenueEntity(cityId=1,name="Рынок"))
        db.shopBranchDao().insert(ShopBranchEntity(shopId=shop,address="Павильон 29",venueId=venue))
        val products=ProductRepository(db.productDao(),db.productShopLinkDao(),db)
        product=products.saveWithLinks(ProductEntity(name="Сыр",defaultQuantity=1.0,defaultUnit="шт",isFavorite=true),listOf(ProductShopLinkEntity(productId=0,shopId=shop)))
        vm=AddToListViewModel(products,shops,ShoppingListRepository(db.shoppingListEntryDao(),db,locations),app.getSharedPreferences("shopping_list_prefs",0))
        store.put("add",vm)
    }
    @After fun close(){ui.runOnIdle{store.clear()};locations.closeForTest();db.close()}
    private fun waitState(condition:()->Boolean){ui.waitUntil(15_000){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20));condition()}}
    private fun show(){ui.setContent{val view=LocalView.current;SideEffect{root=view.rootView};SpisokRyadomTheme{AddToListScreen({},viewModel=vm)}};waitState{vm.uiState.value.searchResults.isNotEmpty()}}
    private fun shot(name:String){ui.waitForIdle();ui.runOnIdle{
        val view=root!!;val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))
        val file=File("build/reports/audit201/screens/$name-api${RuntimeEnvironment.getApiLevel()}.png");file.parentFile!!.mkdirs();file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }}
    @Test fun favoriteAndFullCandidatesUseTheSameLiveMarketContext(){
        show();waitState{vm.uiState.value.productContexts[product]?.contains("Рынок — Павильон 29")==true}
        assertEquals(2,ui.onAllNodesWithText("Сыр").fetchSemanticsNodes().size)
        ui.onNodeWithTag("candidate-favorite-$product").assertIsDisplayed();ui.onNodeWithTag("candidate-all-$product").assertIsDisplayed()
        ui.runOnIdle{runBlocking{shops.updateVenue(db.retailStructureDao().venue(venue)!!.copy(name="Новый рынок"))}}
        waitState{vm.uiState.value.productContexts[product]?.contains("Новый рынок — Павильон 29")==true}
        assertEquals(2,ui.onAllNodesWithText("1 шт · Новый рынок — Павильон 29").fetchSemanticsNodes().size)
        shot("01-market-candidates")
    }
    @Test fun oldAddScreenShowsRestoreErrorAndDoesNotReportSuccess(){
        show();ui.onNodeWithTag("candidate-all-$product").performClick();waitState{vm.uiState.value.selectedProduct?.id==product&&!vm.uiState.value.isLoading}
        ui.runOnIdle{runBlocking{val backup=BackupManager(app,db);val file=backup.exportFile();try{backup.importFile(file)}finally{backup.discard(file)}}}
        ui.onNodeWithText("Добавить в список").performScrollTo().performClick()
        waitState{vm.uiState.value.error!=null}
        ui.onNodeWithText(vm.uiState.value.error!!).performScrollTo().assertIsDisplayed()
        ui.runOnIdle{assertNull(vm.uiState.value.addedMessage);assertTrue(runBlocking{db.shoppingListEntryDao().getAllSync().isEmpty()})}
        shot("02-stale-add-error")
    }
}
