package com.spisokryadom.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.*
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.ProductEditorTransactions
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=SpisokRyadomApp::class,qualifiers="w360dp-h800dp-xhdpi")
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProductInput300UiTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val app get()=ApplicationProvider.getApplicationContext<SpisokRyadomApp>()
    private val db get()=app.container.database
    private var onion=0L;private var bread=0L;private var card=0L
    private fun waitFor(test:()->Boolean){try{ui.waitUntil(25_000,test)}catch(e:Throwable){
        val roots=ui.onAllNodes(isRoot(),useUnmergedTree=true)
        repeat(roots.fetchSemanticsNodes().size){println(roots[it].printToString(maxDepth=70))}
        println("INPUT300 purchases="+runBlocking{db.shoppingListEntryDao().getAllSync()})
        throw e
    }}
    private fun text(value:String)=waitFor{ui.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()}
    private fun tab(value:String){ui.onNode(hasText(value) and SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.Tab)).performClick()}
    private fun shot(name:String){ui.runOnIdle{
        val view=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf{it.isShowing}?.window?.decorView?:ui.activity.window.decorView
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))
        val file=File("build/reports/input300/screens/$name-api${android.os.Build.VERSION.SDK_INT}.png");file.parentFile!!.mkdirs();file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }}
    @Before fun seed(){runBlocking{
        app.getSharedPreferences("shopping_list_prefs",0).edit().clear().putBoolean("autosave_product_cards",false).commit()
        app.getSharedPreferences("product_card_drafts",0).edit().clear().commit()
        db.shoppingListEntryDao().deleteAll();db.goalListDao().deleteAll();db.productDao().deleteAll();db.shopDao().deleteAll();db.productInputDao().clearSettings();db.productInputDao().clearRuns()
        StandardCategories.install(db.openHelper.writableDatabase);app.container.locations.select(1)
        onion=db.productDao().insert(ProductEntity(name="Лук",defaultUnit="шт"));bread=db.productDao().insert(ProductEntity(name="Хлеб",defaultUnit="шт"))
        db.productDao().insert(ProductEntity(name="Сушёный хлеб",defaultUnit="упак"))
        card=db.productDao().insert(ProductEntity(name="Товар с фото",purchaseType="online",sellerUrl="https://example.org/seller",productUrl="https://example.org/old"))
        val photo=File(app.filesDir,"product_photos/input300-test.png");photo.parentFile!!.mkdirs()
        val bitmap=Bitmap.createBitmap(640,900,Bitmap.Config.ARGB_8888);bitmap.eraseColor(android.graphics.Color.rgb(210,225,195))
        Canvas(bitmap).drawText("Состав 67%",40f,200f,android.graphics.Paint().apply{color=android.graphics.Color.BLACK;textSize=48f})
        photo.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        app.container.productRepository.variants.savePhotos(card,listOf(ProductPhotoEntity(card,0,photo.absolutePath,"Состав")))
    };text("Список пуст")}
    @After fun close(){ui.activityRule.scenario.close();val end=System.nanoTime()+20_000_000_000;while(ProductEditorTransactions.mutex.isLocked&&System.nanoTime()<end){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();Thread.sleep(5)};assertFalse(ProductEditorTransactions.mutex.isLocked);app.container.locations.closeForTest();db.close();org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null)}
    private fun openCard(){tab("Товары");text("База товаров");text("Товар с фото");ui.onAllNodesWithText("Товар с фото").onLast().performClick();text("Название товара");waitFor{ui.onAllNodesWithTag("product-photo-0").fetchSemanticsNodes().isNotEmpty()}}
    @Test fun newCardPrefillsInExistingDropdownsAndTheUnitHasNoMicrophone() {
        tab("Товары");text("База товаров");ui.onNodeWithContentDescription("Новый товар").performClick();text("Название товара")
        ui.onNodeWithTag("product-name").performTextInput("бананс")
        text("Овощи/фрукты");text("Продукты")
        shot("card-prefill")
        ui.onNodeWithTag("product-unit").performScrollTo().performTextReplacement("")
        ui.onAllNodes(hasContentDescription("Голосовой ввод") and hasAnyAncestor(hasTestTag("product-unit")),useUnmergedTree=true).assertCountEquals(0)
        assertFalse(runBlocking{db.productDao().getAllSync().any{it.name=="бананс"}})
    }
    @Test fun packagePreviewShowsSourceAndChoicesAndAddsOnlyAfterConfirmation() {
        ui.onNodeWithContentDescription("Добавить в список").performClick();text("Добавить покупку")
        ui.onNodeWithTag("add-bulk-products").performScrollTo().performClick();text("Добавить несколько товаров")
        ui.onNodeWithTag("bulk-input").performTextInput("Лук. Белый хлеб; гречка")
        ui.onNodeWithText("Проверить",substring=false).performClick();text("Проверить товары")
        assertTrue(runBlocking{db.shoppingListEntryDao().getAllSync().isEmpty()})
        ui.onNode(hasText("Выбрать",substring=false) and hasAnyAncestor(hasTestTag("bulk-row-1"))).performScrollTo().performClick()
        text("Выбрать товар");waitFor{ui.onAllNodesWithTag("bulk-candidate-$bread").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithTag("bulk-candidate-$bread").performClick();text("Проверить товары")
        ui.onNode(hasText("Выбрать",substring=false) and hasAnyAncestor(hasTestTag("bulk-row-2"))).performScrollTo().performClick()
        text("Выбрать товар");waitFor{runCatching{ui.onNodeWithTag("bulk-create-new").assertIsEnabled()}.isSuccess}
        ui.onNodeWithTag("bulk-create-new").performClick();text("Проверить товары")
        ui.onNodeWithTag("bulk-quantity-0").performScrollTo().performTextReplacement("2")
        shot("bulk-preview")
        ui.onNodeWithText("Добавить все",substring=false).assertIsDisplayed().performClick()
        waitFor{runBlocking{db.shoppingListEntryDao().getAllSync().size==3}}
        val rows=runBlocking{db.shoppingListEntryDao().getAllSync()}
        assertEquals(2.0,rows.single{it.productId==onion}.quantity,0.0);assertTrue(rows.any{it.productId==bread})
        assertTrue(rows.all{it.listId==null});assertFalse(runBlocking{db.productDao().getAllSync().any{it.name=="Белый хлеб"}})
    }
    @Test fun previewSurvivesActivityRecreationAndCancellationDoesNotAddPurchases() {
        ui.onNodeWithContentDescription("Добавить в список").performClick();text("Добавить покупку")
        ui.onNodeWithTag("add-bulk-products").performScrollTo().performClick();text("Добавить несколько товаров")
        ui.onNodeWithTag("bulk-input").performTextInput("Хлеб. Неизвестный продукт")
        ui.onNodeWithText("Проверить",substring=false).performClick();text("Проверить товары")
        ui.activityRule.scenario.recreate();text("Проверить товары")
        ui.onNodeWithText("Добавить все",substring=false).assertIsNotEnabled()
        shot("bulk-restored")
        ui.onNodeWithText("Отмена",substring=false).performClick();text("Добавить покупку")
        assertTrue(runBlocking{db.shoppingListEntryDao().getAllSync().isEmpty()})
    }
    @Test fun photoViewerSupportsPinchAndEditingStillUsesThePencil() {
        openCard();ui.onNodeWithTag("product-photo-0").performScrollTo().performClick();text("Сбросить масштаб")
        ui.onNodeWithTag("photo-zoom-area").performTouchInput{
            val c=center
            pinch(start0=c+Offset(-30f,0f),end0=c+Offset(-110f,0f),start1=c+Offset(30f,0f),end1=c+Offset(110f,0f))
        }
        waitFor{!ui.onNodeWithTag("photo-zoom-status").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString().contains("100%")}
        shot("photo-zoom");ui.onNodeWithText("Сбросить масштаб").performClick();ui.onNodeWithTag("photo-zoom-status").assertTextContains("100%",substring=true)
        ui.onNodeWithContentDescription("Закрыть просмотр фото").performClick()
        ui.onNodeWithContentDescription("Изменить фото 1").performScrollTo().performClick();text("Примечание к фото")
        ui.onNodeWithText("Отмена",substring=false).performClick()
        assertEquals("Состав",runBlocking{db.productVariantDao().photos(card).single().caption})
    }
    @Test fun linkPencilExtractsSharedTextAndManualCardSaveRemainsExplicit() {
        openCard();ui.onNodeWithContentDescription("Изменить: Ссылка на товар").performScrollTo().performClick()
        ui.onNodeWithTag("product-link-input").performTextReplacement("Название товара\nhttps://example.org/new?sku=42&from=share\nОписание")
        ui.onNodeWithText("Применить",substring=false).performClick();text("https://example.org/new?sku=42&from=share")
        assertEquals("https://example.org/old",runBlocking{db.productDao().getById(card)!!.productUrl})
        shot("link-read-mode")
        ui.onNodeWithText("https://example.org/new?sku=42&from=share").performScrollTo().performClick()
        val intent=Shadows.shadowOf(ui.activity).nextStartedActivity
        assertEquals(android.content.Intent.ACTION_VIEW,intent.action);assertEquals("https://example.org/new?sku=42&from=share",intent.dataString)
        ui.onNodeWithText("Сохранить",substring=false).performClick();text("База товаров")
        assertEquals("https://example.org/new?sku=42&from=share",runBlocking{db.productDao().getById(card)!!.productUrl})
    }
    @Test fun selectedLinkFromSharedTextSurvivesActivityRecreation() {
        openCard();ui.onNodeWithContentDescription("Изменить: Ссылка на товар").performScrollTo().performClick()
        ui.onNodeWithTag("product-link-input").performTextReplacement("Название\nhttps://example.org/a\nhttps://example.org/b")
        ui.onNodeWithText("Применить",substring=false).assertIsNotEnabled()
        ui.onNodeWithText("Ссылка",substring=false).performClick()
        ui.onNodeWithText("https://example.org/b",substring=false).performClick()
        ui.onNodeWithText("Применить",substring=false).assertIsEnabled()
        ui.activityRule.scenario.recreate();waitFor{ui.onAllNodesWithTag("product-link-input").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("Применить",substring=false).assertIsEnabled().performClick()
        text("https://example.org/b")
        assertEquals("https://example.org/old",runBlocking{db.productDao().getById(card)!!.productUrl})
        ui.onNodeWithText("Сохранить",substring=false).performClick();text("База товаров")
        assertEquals("https://example.org/b",runBlocking{db.productDao().getById(card)!!.productUrl})
    }
    @Test @Config(qualifiers="w640dp-h360dp-xhdpi") fun newSettingsUseExistingScrollableLayoutInLandscape() {
        ui.onNodeWithContentDescription("Настройки").performClick();text("Настройки")
        ui.onNodeWithTag("settings-list").performScrollToNode(hasText("Предзаполнение новых товаров"))
        ui.onNodeWithText("Распознавать названия товаров").performScrollTo().performClick()
        waitFor{runBlocking{!app.container.productRepository.input.settings().prefillEnabled}}
        shot("settings-landscape")
        ui.onNodeWithText("Подставлять общую группу в новых магазинах").performScrollTo().assertIsDisplayed()
    }
}
