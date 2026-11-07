package com.spisokryadom.app

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.ProductEditorTransactions
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=SpisokRyadomApp::class,qualifiers="w360dp-h800dp-xhdpi")
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Audit311CardUiTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val app get()=ApplicationProvider.getApplicationContext<SpisokRyadomApp>()
    private val db get()=app.container.database
    private var shop=0L
    private var department=0L
    private fun waitFor(test:()->Boolean)=ui.waitUntil(25_000,test)
    private fun text(value:String)=waitFor{ui.onAllNodesWithText(value,substring=false).fetchSemanticsNodes().isNotEmpty()}
    private fun shot(name:String)=ui.runOnIdle {
        val view=org.robolectric.shadows.ShadowDialog.getLatestDialog()!!.window!!.decorView
        val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val file=java.io.File("build/reports/audit311/screens/$name-api${android.os.Build.VERSION.SDK_INT}.png")
        file.parentFile!!.mkdirs();file.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    @Before fun seed(){runBlocking {
        app.getSharedPreferences("shopping_list_prefs",0).edit().clear().putBoolean("autosave_product_cards",false).commit()
        app.getSharedPreferences("product_card_drafts",0).edit().clear().commit()
        db.shoppingListEntryDao().deleteAll();db.goalListDao().deleteAll();db.productDao().deleteAll();db.shopDao().deleteAll()
        db.productInputDao().clearSettings();app.container.locations.select(1)
        shop=db.shopDao().insert(ShopEntity(name="Магазин аудита",cityId=1))
        val type=db.shopDepartmentDao().ensureType("Отдел аудита")
        department=db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=shop,typeId=type,name="Отдел аудита"))
    };text("Список пуст")}
    @After fun close(){
        ui.activityRule.scenario.close()
        val end=System.nanoTime()+20_000_000_000
        while(ProductEditorTransactions.mutex.isLocked&&System.nanoTime()<end){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();Thread.sleep(5)}
        assertFalse(ProductEditorTransactions.mutex.isLocked)
        app.container.locations.closeForTest();db.close()
        org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null)
    }
    private fun openShopChoice(){
        ui.onNode(hasText("Товары") and SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.Tab)).performClick()
        text("База товаров");ui.onNodeWithContentDescription("Новый товар").performClick();text("Название товара")
        ui.onNodeWithTag("product-name").performTextInput("бананы")
        text("Овощи/фрукты")
        ui.onNodeWithText("Добавить магазин",substring=false).performScrollTo().performClick()
        ui.onNodeWithText("Магазин",substring=false).performClick()
        ui.onNodeWithText("Магазин аудита",substring=false).performClick()
        text("Группа магазина")
        waitFor{ui.onAllNodesWithText("Овощи/фрукты",substring=false).fetchSemanticsNodes().size>=2}
    }
    @Test fun explicitDepartmentSurvivesRecreationUntilManualCardSave(){
        openShopChoice()
        ui.onNodeWithText("Группа магазина",substring=false).performClick()
        ui.onNodeWithText("Отдел аудита",substring=false).performClick()
        ui.activityRule.scenario.recreate()
        text("Группа магазина")
        ui.onNodeWithText("Отдел аудита",substring=false).assertIsDisplayed()
        ui.onNodeWithText("Добавить",substring=false).performClick()
        assertTrue(runBlocking{db.productDao().getAllSync().isEmpty()})
        ui.onNodeWithText("Сохранить",substring=false).performClick();text("База товаров")
        val product=runBlocking{db.productDao().getAllSync().single()}
        assertEquals(department,runBlocking{db.productShopLinkDao().getLinksForProductSync(product.id).single().departmentId})
    }
    @Test fun explicitNoDepartmentSurvivesRecreationAndCancelDoesNotWrite(){
        openShopChoice()
        ui.onNodeWithText("Группа магазина",substring=false).performClick()
        ui.onAllNodesWithText("Не задано",substring=false).onLast().performClick()
        ui.activityRule.scenario.recreate()
        text("Группа магазина")
        ui.onNode(hasText("Группа магазина") and hasText("Не задано")).assertIsDisplayed()
        shot("shop-choice-restored")
        ui.onNodeWithText("Отмена",substring=false).performClick()
        assertTrue(runBlocking{db.productDao().getAllSync().isEmpty()})
        assertEquals(listOf(department),runBlocking{db.shopDepartmentDao().getByShopIdSync(shop).map{it.id}})
    }

    private fun existingCard(): Long {
        val id=runBlocking{app.container.productRepository.saveWithLinks(ProductEntity(name="Товар аудита"),
            listOf(ProductShopLinkEntity(productId=0,shopId=shop,departmentId=department)))}
        ui.onNode(hasText("Товары") and SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.Tab)).performClick()
        text("База товаров");text("Товар аудита")
        ui.onNodeWithText("Товар аудита",substring=false).performClick();text("Название товара")
        return id
    }

    @Test fun existingShopDepartmentSurvivesRecreationUntilManualCardSave(){
        val other=runBlocking{
            val type=db.shopDepartmentDao().ensureType("Другой отдел аудита")
            db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId=shop,typeId=type,name="Другой отдел аудита"))
        }
        val id=existingCard()
        val originalLink=runBlocking{db.productShopLinkDao().getLinksForProductSync(id).single()}
        ui.onNodeWithText("1. Магазин аудита",substring=false).performScrollTo().performClick()
        text("Товар в магазине")
        ui.onNodeWithText("Группа магазина",substring=false).performClick()
        ui.onNodeWithText("Другой отдел аудита",substring=false).performClick()
        ui.activityRule.scenario.recreate();text("Товар в магазине")
        ui.onNodeWithText("Другой отдел аудита",substring=false).assertIsDisplayed()
        shot("shop-settings-restored")
        assertEquals(originalLink,runBlocking{db.productShopLinkDao().getLinksForProductSync(id).single()})
        ui.onNodeWithText("Применить",substring=false).performClick()
        assertEquals(originalLink,runBlocking{db.productShopLinkDao().getLinksForProductSync(id).single()})
        ui.onNodeWithText("Сохранить",substring=false).performClick();text("База товаров")
        val saved=runBlocking{db.productShopLinkDao().getLinksForProductSync(id).single()}
        assertEquals(originalLink.id,saved.id);assertEquals(other,saved.departmentId)
    }

    @Test fun pendingShopDepartmentCanStillBeCancelledAfterRecreation(){
        openShopChoice()
        ui.onNodeWithText("Группа магазина",substring=false).performClick()
        ui.onNodeWithText("Отдел аудита",substring=false).performClick()
        ui.onNodeWithText("Добавить",substring=false).performClick()
        ui.onNodeWithText("1. Магазин аудита",substring=false).performScrollTo().performClick()
        text("Товар в магазине")
        ui.onNodeWithText("Группа магазина",substring=false).performClick()
        ui.onAllNodesWithText("Не задано",substring=false).onLast().performClick()
        ui.activityRule.scenario.recreate();text("Товар в магазине")
        ui.onNode(hasText("Группа магазина") and hasText("Не задано")).assertIsDisplayed()
        ui.onNodeWithText("Отмена",substring=false).performClick()
        assertTrue(runBlocking{db.productDao().getAllSync().isEmpty()})
        ui.onNodeWithText("Сохранить",substring=false).performClick();text("База товаров")
        val id=runBlocking{db.productDao().getAllSync().single().id}
        assertEquals(department,runBlocking{db.productShopLinkDao().getLinksForProductSync(id).single().departmentId})
    }

    @Test fun shopLinkEditingModeSurvivesRecreation(){
        val id=existingCard()
        val originalLink=runBlocking{db.productShopLinkDao().getLinksForProductSync(id).single()}
        ui.onNodeWithContentDescription("Редактировать",substring=false).performScrollTo().performClick()
        ui.onNodeWithText("Добавить магазин",substring=false).performScrollTo().assertIsDisplayed()
        ui.activityRule.scenario.recreate();text("Название товара")
        ui.onNodeWithText("Добавить магазин",substring=false).performScrollTo().assertIsDisplayed()
        ui.onNodeWithContentDescription("Удалить связь",substring=false).assertExists()
        assertEquals(originalLink,runBlocking{db.productShopLinkDao().getLinksForProductSync(id).single()})
    }
}
