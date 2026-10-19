package com.spisokryadom.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.ui.shopcontent.ShopContentScreen
import com.spisokryadom.app.ui.shopcontent.ShopContentViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.*
import java.io.File
import java.util.UUID

/** Pure screen integration: one fresh DB and explicit ViewModel, no app navigation/singleton. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=Application::class,qualifiers="w360dp-h640dp-xhdpi")
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IndependentShopPickerAuditTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()

    private fun capture(name:String) {
        ui.runOnIdle {
            val view=ui.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file=File("C:/Users/Роман/Documents/Codex/2026-10-16/new-chat/work/independent-audit/ui/shop-picker-$name-api${Build.VERSION.SDK_INT}.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
            println("Screenshot: ${file.absolutePath}")
        }
    }

    @Test fun visiblePickerCheckboxMustReceivePhysicalTap() {
        val context=ApplicationProvider.getApplicationContext<Application>()
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).allowMainThreadQueries().build()
        val locations=LocationRepository(db.cityDao(),db.shopDao(),context.getSharedPreferences("picker_audit_${UUID.randomUUID()}",0))
        val products=ProductRepository(db.productDao(),db.productShopLinkDao(),db)
        val shops=ShopRepository(db.shopDao(),db.shopDepartmentDao(),locations,db)
        val basket=ShoppingListRepository(db.shoppingListEntryDao(),db,locations)
        val classifiers=ClassifierRepository(db.productGroupDao(),db.recipientDao(),db.unitDao(),db)
        val store=ViewModelStore()
        lateinit var vm:ShopContentViewModel
        lateinit var batch:com.spisokryadom.app.ui.batch.BatchViewModel
        var backNavigations=0
        var targetProduct=0L
        var shopId=0L
        fun hasTargetLink():Boolean=runBlocking{db.productShopLinkDao().getLinksForProductSync(targetProduct).any{it.shopId==shopId}}
        try {
            runBlocking {
                db.cityDao().insert(CityEntity(1,"Город проверки"))
                locations.select(1)
                shopId=db.shopDao().insert(ShopEntity(name="Магазин проверки",cityId=1))
                // A linked control ensures the VM has actually loaded the full product table before opening the picker.
                val linkedControl=db.productDao().insert(ProductEntity(name="Контрольный товар"))
                targetProduct=db.productDao().insert(ProductEntity(name="Яблоки Гала"))
                db.productShopLinkDao().insert(ProductShopLinkEntity(productId=linkedControl,shopId=shopId))
            }
            ui.runOnIdle {
                vm=ShopContentViewModel(shopId,products,shops,basket,classifiers)
                store.put("shop-picker",vm)
                batch=com.spisokryadom.app.ui.batch.BatchViewModel(context,products,shops,basket,classifiers,BatchRepository(db,products,basket))
                store.put("shop-picker-batch",batch)
            }
            ui.setContent {
                MaterialTheme {
                    ShopContentScreen(shopId=shopId,onNavigateBack={backNavigations++},onNavigateToProductCard={},onCreateNewProduct={},viewModel=vm,batchVM=batch)
                }
            }
            ui.waitUntil(20_000){!vm.uiState.value.isLoading&&vm.uiState.value.shop!=null&&vm.uiState.value.products.any{it.product.name=="Контрольный товар"}}
            ui.runOnIdle{vm.openPickProducts()}
            // Initial Room emissions may restart the debounce after opening; pump the paused looper.
            repeat(20){
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(200))
                ui.waitForIdle();Thread.sleep(10)
            }
            println("Picker state after clock pump=${vm.uiState.value}")
            assertTrue("Picker setup must load the unlinked target",vm.uiState.value.showPickProducts&&vm.uiState.value.pickProducts.any{it.product.id==targetProduct&&!it.isLinked})
            ui.onNode(isToggleable() and isOff()).assertExists()
            println("Before physical tap:\n"+ui.onRoot().printToString())
            capture("before-physical")

            // performTouchInput sends an actual pointer at the checkbox position; rather than invoking OnClick semantics directly.
            ui.onNode(isToggleable() and isOff()).performTouchInput{click()}
            ui.waitForIdle()
            val deadline=System.nanoTime()+2_000_000_000L
            ui.waitUntil(3_000){hasTargetLink()||System.nanoTime()>deadline}
            val physicalLinked=hasTargetLink()
            capture("after-physical")
            println("Physical checkbox tap linked target=$physicalLinked; error=${vm.uiState.value.error}")

            // Restore off state if the physical tap worked, so the semantics action is an independent positive control.
            if(physicalLinked) {
                runBlocking{products.toggleShopLink(targetProduct,shopId)}
                // Room invalidation restarts the VM's 120 ms debounce. Advance the
                // paused Android clock before waiting for the independent control.
                repeat(20){
                    Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(200))
                    ui.waitForIdle();Thread.sleep(10)
                }
                ui.waitUntil(10_000){vm.uiState.value.pickProducts.any{it.product.id==targetProduct&&!it.isLinked}}
            }
            ui.onNode(isToggleable() and isOff()).performSemanticsAction(SemanticsActions.OnClick){it()}
            ui.waitUntil(10_000){hasTargetLink()}
            val semanticLinked=hasTargetLink()
            println("Direct OnClick semantics linked target=$semanticLinked; error=${vm.uiState.value.error}")
            assertTrue("Positive control: direct checkbox OnClick semantics must create the product-shop link",semanticLinked)
            assertTrue("The rendered picker checkbox must receive a physical pointer tap; direct semantics worked but pointer tap was blocked",physicalLinked)

            // Back dismisses the picker itself; navigation away from the shop is still a separate action.
            ui.runOnIdle { ui.activity.onBackPressedDispatcher.onBackPressed() }
            ui.waitUntil(5_000){!vm.uiState.value.showPickProducts}
            ui.onNodeWithText("Добавить товары").assertDoesNotExist()
            ui.onNodeWithText("Магазин проверки").assertExists()
            ui.runOnIdle { assertEquals(0,backNavigations) }
            ui.onNodeWithContentDescription("Назад").performClick()
            ui.runOnIdle { assertEquals(1,backNavigations) }
        } finally {
            ui.runOnIdle{store.clear()}
            ui.activityRule.scenario.close()
            locations.closeForTest()
            db.close()
        }
    }
}
