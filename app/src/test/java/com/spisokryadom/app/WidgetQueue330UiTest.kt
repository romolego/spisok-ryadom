package com.spisokryadom.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
class WidgetQueue330UiTest {
    @get:Rule val ui=createComposeRule()
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:AppDatabase
    private lateinit var places:LocationRepository
    private lateinit var vm:BulkProductViewModel
    private val models=ViewModelStore()
    @Before fun seed():Unit=runBlocking {
        db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build();db.cityDao().insert(CityEntity(1,"Город"))
        val prefs=app.getSharedPreferences("queue330-ui",0);prefs.edit().clear().commit()
        places=LocationRepository(db.cityDao(),db.shopDao(),prefs)
        vm=BulkProductViewModel(BulkPurchaseRepository(db,ShoppingListRepository(db.shoppingListEntryDao(),db,places),places),SavedStateHandle())
        models.put("bulk",vm)
    }
    @After fun close(){models.clear();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();places.closeForTest();db.close()}
    private fun waitFor(check:()->Boolean)=ui.waitUntil(25_000){Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(20));check()}
    private fun back()=ui.runOnIdle{(org.robolectric.shadows.ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed()}
    private fun show(){ui.setContent{SpisokRyadomTheme{
        var open by remember{mutableStateOf(true)}
        if(open)BulkProductSheet(null,null,{open=false},{},vm)
        else Button(onClick={open=true}){Text("Открыть очередь")}
    }}}
    private fun shot(name:String)=ui.runOnIdle {
        val v=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.window?.decorView?:return@runOnIdle
        val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b))
        val f=File("build/reports/widget330/screens/$name-api${android.os.Build.VERSION.SDK_INT}.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
    }
    @Test fun queueReviewUsesInlineStepperAndRemovesOnlyTheConfirmedBatch(){
        val onion=runBlocking{db.productDao().insert(ProductEntity(name="Лук"))}
        show();ui.onNodeWithTag("bulk-input").performTextInput("Лук, Новый товар")
        ui.onNodeWithTag("bulk-enqueue").performClick();waitFor{vm.state.value.queue.size==1}
        ui.onNodeWithTag("bulk-input").performTextInput("Морковь. Картофель")
        ui.onNodeWithTag("bulk-enqueue").performClick();waitFor{vm.state.value.queue.size==2}
        shot("queue-two-batches")
        ui.onNodeWithText("Список 1").performScrollTo().performClick();waitFor{vm.state.value.preview&&!vm.state.value.busy}
        ui.onNodeWithTag("bulk-option-0-$onion").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("bulk-quantity-0").assertTextEquals("1")
        ui.onNodeWithContentDescription("Увеличить: Лук").performClick();ui.onNodeWithTag("bulk-quantity-0").assertTextEquals("2")
        ui.onNodeWithTag("bulk-new-1").performScrollTo().performClick()
        ui.onNodeWithTag("bulk-new-name-1").performScrollTo().performTextReplacement("Свой товар")
        ui.onNodeWithTag("bulk-quantity-1").performTextReplacement("3")
        ui.onNodeWithText("Другой товар").assertDoesNotExist()
        shot("queue-selected-quantities")
        ui.onNodeWithTag("bulk-confirm").performClick();waitFor{vm.state.value.queue.size==1&&!vm.state.value.busy&&!vm.state.value.preview}
        ui.onNodeWithText("Морковь. Картофель").performScrollTo().assertIsDisplayed()
        assertEquals(2,runBlocking{db.shoppingListEntryDao().getAllSync().size})
        ui.onNodeWithContentDescription("Удалить список 1").performClick();assertTrue(vm.state.value.queue.isEmpty())
    }
    @Test fun pickerBackCloseAndReopenNeverTrapTheSheetOrLoseTheBatch(){
        runBlocking{db.productDao().insert(ProductEntity(name="Хлеб"))}
        show();ui.onNodeWithTag("bulk-input").performTextInput("Белый хлеб, Новый")
        ui.onNodeWithTag("bulk-confirm").performClick();waitFor{vm.state.value.preview&&!vm.state.value.busy}
        ui.onNodeWithTag("bulk-choose-0").performScrollTo().performClick();waitFor{vm.state.value.choosing!=null&&!vm.state.value.searching}
        back();waitFor{vm.state.value.choosing==null};assertTrue(vm.state.value.preview)
        ui.onNodeWithTag("bulk-choose-0").performScrollTo().performClick();waitFor{!vm.state.value.searching}
        ui.onNodeWithContentDescription("Закрыть массовый ввод").performClick();ui.onNodeWithText("Открыть очередь").assertIsDisplayed().performClick()
        ui.onNodeWithText("Список 1").performScrollTo().performClick();waitFor{vm.state.value.preview&&!vm.state.value.busy}
        assertEquals("Белый хлеб, Новый",vm.state.value.text);assertEquals(2,vm.state.value.rows.size)
        ui.onNodeWithTag("bulk-clear").performClick();ui.onNodeWithTag("bulk-input").assertTextEquals("")
        assertTrue(vm.state.value.queue.isEmpty());assertFalse(vm.state.value.preview)
        ui.onNodeWithTag("bulk-input").performTextInput("Другой список")
        ui.onNodeWithTag("bulk-enqueue").performClick();ui.onNodeWithText("Список 1").performScrollTo().assertIsDisplayed()
        assertTrue(runBlocking{db.shoppingListEntryDao().getAllSync().isEmpty()});shot("queue-reopened-after-picker")
    }
    @Test fun zeroFromStepperRemovesRowAndClearReturnsToUsableInput(){
        runBlocking{db.productDao().insert(ProductEntity(name="Лук"))};show()
        ui.onNodeWithTag("bulk-input").performTextInput("Лук, Новый")
        ui.onNodeWithTag("bulk-confirm").performClick();waitFor{vm.state.value.preview&&!vm.state.value.busy}
        ui.onNodeWithContentDescription("Уменьшить: Лук").performScrollTo().performClick()
        ui.onNodeWithTag("bulk-row-0").assertDoesNotExist();assertEquals(1,vm.state.value.rows.size)
        ui.onNodeWithTag("bulk-clear").performClick();ui.onNodeWithTag("bulk-input").performTextInput("Теперь другой")
        ui.onNodeWithTag("bulk-confirm").performClick();waitFor{vm.state.value.preview&&!vm.state.value.busy}
        assertEquals("Теперь другой",vm.state.value.rows.single().source)
    }
    @Test fun swipeDismissSavesTheBatchAndAllowsOpeningAgain(){
        show();ui.onNodeWithTag("bulk-input").performTextInput("Сохранить после жеста")
        ui.onNodeWithTag("editor-sheet").performTouchInput{swipe(start=androidx.compose.ui.geometry.Offset(width/2f,2f),end=androidx.compose.ui.geometry.Offset(width/2f,height-2f),durationMillis=150)}
        ui.waitUntil(25_000){ui.onAllNodesWithText("Открыть очередь").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("Открыть очередь").performClick()
        ui.onNodeWithText("Список 1").performScrollTo().assertIsDisplayed()
        assertEquals("Сохранить после жеста",vm.state.value.queue.single().text)
        assertTrue(runBlocking{db.shoppingListEntryDao().getAllSync().isEmpty()})
    }
    @Test @Config(qualifiers="w640dp-h360dp-xhdpi") fun landscapeQueueAndPreviewActionsRemainReachable(){
        show();ui.onNodeWithTag("bulk-input").performTextInput("Один. Два. Три")
        ui.onNodeWithTag("bulk-enqueue").assertIsDisplayed().performClick()
        ui.onNodeWithText("Список 1").performScrollTo().performClick();waitFor{vm.state.value.preview&&!vm.state.value.busy}
        ui.onNodeWithTag("bulk-preview").performScrollToNode(hasTestTag("bulk-row-2"))
        ui.onNodeWithTag("bulk-new-2").performScrollTo().assertIsDisplayed().performClick()
        ui.onNodeWithContentDescription("Увеличить: Три").performClick()
        ui.onNodeWithTag("bulk-confirm").assertIsDisplayed();ui.onNodeWithTag("bulk-clear").assertIsDisplayed()
        shot("queue-landscape-preview")
    }
}
