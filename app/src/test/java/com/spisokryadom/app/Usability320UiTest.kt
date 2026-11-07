package com.spisokryadom.app

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.ui.addtolist.*
import com.spisokryadom.app.ui.components.*
import com.spisokryadom.app.ui.theme.SpisokRyadomTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=Application::class,qualifiers="w360dp-h800dp-xhdpi")
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Usability320UiTest {
    @get:Rule val ui=createComposeRule()
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:AppDatabase
    private lateinit var places:LocationRepository
    private lateinit var vm:BulkProductViewModel
    private val models=ViewModelStore()
    @Before fun seed():Unit=runBlocking {
        db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build()
        db.cityDao().insert(CityEntity(1,"Город"))
        val prefs=app.getSharedPreferences("usability320-ui",0);prefs.edit().clear().commit()
        places=LocationRepository(db.cityDao(),db.shopDao(),prefs)
        vm=BulkProductViewModel(BulkPurchaseRepository(db,ShoppingListRepository(db.shoppingListEntryDao(),db,places),places),SavedStateHandle())
        models.put("bulk",vm)
    }
    @After fun close(){models.clear();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();places.closeForTest();db.close()}
    private fun waitState(check:()->Boolean)=ui.waitUntil(25_000){Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(20));check()}
    private fun back()=ui.runOnIdle{(org.robolectric.shadows.ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed()}
    private fun shot(name:String)=ui.runOnIdle {
        val v=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.window?.decorView?:return@runOnIdle
        val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b))
        val f=File("build/reports/usability320/screens/$name-api${android.os.Build.VERSION.SDK_INT}.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
    }
    @Test fun bulkShowsInlineOptionsAndBackKeepsEveryChoiceAndTheSource() {
        val bread=runBlocking{db.productDao().insert(ProductEntity(name="Хлеб",defaultUnit="шт"))}
        ui.setContent{SpisokRyadomTheme{BulkProductSheet(null,null,{}, {},vm)}}
        ui.onNodeWithTag("bulk-input").performTextInput("Белый хлеб, Особый товар")
        ui.onNodeWithTag("bulk-confirm").performClick();waitState{vm.state.value.preview&&!vm.state.value.busy}
        ui.onNodeWithTag("bulk-option-0-$bread").performScrollTo().assertIsDisplayed().performClick()
        ui.onNodeWithTag("bulk-new-1").performScrollTo().assertIsDisplayed().performClick()
        ui.onNodeWithTag("bulk-new-name-1").performScrollTo().performTextReplacement("Особый товар для дома")
        val chosen=vm.state.value.rows
        ui.onNode(hasText("Другой товар") and hasAnyAncestor(hasTestTag("bulk-row-0"))).performScrollTo().performClick()
        waitState{vm.state.value.choosing!=null&&!vm.state.value.searching};back()
        waitState{vm.state.value.choosing==null};assertEquals(chosen,vm.state.value.rows)
        assertTrue(vm.state.value.preview);shot("bulk-inline-preview")
        back();waitState{!vm.state.value.preview};ui.onNodeWithTag("bulk-input").assertTextContains("Белый хлеб, Особый товар")
        assertTrue(runBlocking{db.shoppingListEntryDao().getAllSync().isEmpty()})
        ui.onNodeWithTag("bulk-confirm").performClick();waitState{vm.state.value.preview&&!vm.state.value.busy}
        assertEquals(chosen,vm.state.value.rows);ui.onNodeWithTag("bulk-confirm").assertIsEnabled()
    }
    @Test @Config(qualifiers="w640dp-h360dp-xhdpi") fun bulkHelpIsOnDemandAndMicrophoneIsAtTheTop() {
        ui.setContent{SpisokRyadomTheme{BulkProductSheet(null,null,{}, {},vm)}}
        ui.onAllNodesWithText("Разделители:",substring=true).assertCountEquals(0)
        val field=ui.onNodeWithTag("bulk-input").fetchSemanticsNode().boundsInRoot
        val mic=ui.onNodeWithContentDescription("Голосовой ввод",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertTrue(mic.top<field.top+field.height/2);assertTrue(mic.left>field.left+field.width/2)
        shot("bulk-input-landscape")
        ui.onNodeWithContentDescription("Подсказка: Товары").performScrollTo().performClick()
        ui.onNodeWithText("Разделители:",substring=true).assertIsDisplayed();ui.onNodeWithText("Понятно").performClick()
        ui.onAllNodesWithText("Разделители:",substring=true).assertCountEquals(0)
    }
    @Test fun linkHasOneValuePlusOrPencilAndClipboardPasteWithoutDuplicatePreview() {
        ui.setContent{SpisokRyadomTheme{var value by remember{mutableStateOf("")};EditableProductLink("Ссылка на продавца",value,{value=it})}}
        ui.onNodeWithText("Ссылка на продавца").assertExists();ui.onNodeWithContentDescription("Добавить: Ссылка на продавца").performClick()
        val url="https://example.org/item?sku=42"
        ui.runOnIdle{(app.getSystemService(Application.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("", "Описание товара\n$url\nОписание"))}
        ui.onNodeWithContentDescription("Вставить из буфера").performClick()
        ui.onNodeWithTag("product-link-input").assertTextContains(url,substring=true)
        ui.onAllNodesWithText(url,substring=true).assertCountEquals(1)
        shot("link-paste")
        ui.onNodeWithText("Применить").performClick();ui.onNodeWithText(url).assertExists()
        ui.onNodeWithContentDescription("Изменить: Ссылка на продавца").assertExists()
        ui.onNodeWithContentDescription("Добавить: Ссылка на продавца").assertDoesNotExist()
    }
    @Test fun photoViewerUsesOnlyTopRightCloseAndKeepsPinchZoom() {
        val p=File(app.filesDir,"shop-test.jpg");val image=Bitmap.createBitmap(200,120,Bitmap.Config.ARGB_8888)
        image.eraseColor(android.graphics.Color.GREEN);p.outputStream().use{image.compress(Bitmap.CompressFormat.JPEG,90,it)};image.recycle()
        var closed=false
        ui.setContent{SpisokRyadomTheme{ProductPhotoViewer(listOf(ProductPhotoEntity(0,0,p.path,"Вход магазина")),0,{closed=true})}}
        ui.onNodeWithText("Сбросить масштаб").assertDoesNotExist()
        val close=ui.onNodeWithContentDescription("Закрыть просмотр фото").fetchSemanticsNode().boundsInRoot
        val area=ui.onNodeWithTag("photo-zoom-area").fetchSemanticsNode().boundsInRoot
        assertTrue(close.left>area.width/2);assertTrue(close.bottom<=area.top)
        ui.onNodeWithTag("photo-zoom-area").performTouchInput{doubleClick()}
        ui.onNodeWithTag("photo-zoom-status").assertTextContains("250%",substring=true)
        shot("shop-photo-zoom");ui.onNodeWithContentDescription("Закрыть просмотр фото").performClick();assertTrue(closed)
    }
}
