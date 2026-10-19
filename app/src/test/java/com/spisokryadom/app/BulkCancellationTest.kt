package com.spisokryadom.app

import android.app.Application
import android.content.SharedPreferences
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.CityEntity
import com.spisokryadom.app.data.entity.ProductEntity
import com.spisokryadom.app.data.entity.ProductShopLinkEntity
import com.spisokryadom.app.data.entity.ShopEntity
import com.spisokryadom.app.data.repository.LocationRepository
import com.spisokryadom.app.data.repository.ProductRepository
import com.spisokryadom.app.data.repository.ShoppingListRepository
import com.spisokryadom.app.ui.bulk.BulkViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.SQLiteMode
import java.time.Duration
import java.util.UUID

/** Real Room and ViewModel checks for cancellation, persisted drafts, and atomic review/add. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class BulkCancellationTest {
    private lateinit var db:AppDatabase
    private lateinit var locations:LocationRepository
    private lateinit var products:ProductRepository
    private lateinit var basket:ShoppingListRepository
    private lateinit var drafts:SharedPreferences
    private val store=ViewModelStore()
    private var gala=0L
    private var milk=0L
    private var shop=0L

    @Before fun setup()=runBlocking {
        val app=ApplicationProvider.getApplicationContext<Application>()
        db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build()
        db.cityDao().insert(CityEntity(1,"Томск"))
        locations=LocationRepository(db.cityDao(),db.shopDao(),app.getSharedPreferences("bulk_location_${UUID.randomUUID()}",0))
        locations.select(1)
        products=ProductRepository(db.productDao(),db.productShopLinkDao(),db)
        basket=ShoppingListRepository(db.shoppingListEntryDao(),db,locations)
        drafts=app.getSharedPreferences("bulk_draft_${UUID.randomUUID()}",0)
        shop=db.shopDao().insert(ShopEntity(name="Магазин рядом",cityId=1))
        gala=db.productDao().insert(ProductEntity(name="Яблоки Гала",defaultUnit="кг"))
        db.productDao().insert(ProductEntity(name="Яблоки Гренни",defaultUnit="кг"))
        milk=db.productDao().insert(ProductEntity(name="Молоко",defaultUnit="л"))
        db.productShopLinkDao().insert(ProductShopLinkEntity(productId=gala,shopId=shop))
        db.productShopLinkDao().insert(ProductShopLinkEntity(productId=milk,shopId=shop))
        Unit
    }

    @After fun close() {
        store.clear()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        locations.closeForTest()
        db.close()
    }

    private fun vm(key:String)=BulkViewModel(products,basket,locations,drafts).also{store.put(key,it)}

    private fun await(message:String,condition:()->Boolean) {
        val deadline=System.nanoTime()+10_000_000_000L
        while(!condition()&&System.nanoTime()<deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertTrue(message,condition())
    }

    private fun drainCancelledWork() {
        repeat(20) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20))
            Thread.sleep(5)
        }
    }

    @Test fun cancellingReviewRetainsRawDraftAndRestoresItInFreshViewModel() {
        val model=vm("original")
        val raw="яблки 0,5 кг\nМолоко 2 л\nНовая покупка 3"
        model.text(raw)
        assertEquals(raw,drafts.getString("bulk_text",null))
        model.review()
        assertTrue(model.uiState.value.busy)
        assertTrue(model.uiState.value.parsing)
        // With the main looper paused, cancellation wins before the suspended catalogue read resumes.
        model.cancelReview()
        assertFalse(model.uiState.value.busy)
        assertFalse(model.uiState.value.parsing)
        drainCancelledWork()
        assertFalse(model.uiState.value.review)
        assertTrue(model.uiState.value.rows.isEmpty())
        assertNull(model.uiState.value.error)
        assertEquals(raw,model.uiState.value.text)
        assertEquals(raw,drafts.getString("bulk_text",null))
        assertEquals(raw,vm("restored").uiState.value.text)
        assertTrue(runBlocking{db.shoppingListEntryDao().getAllSync()}.isEmpty())
    }

    @Test fun cancelledOldReviewCannotOverwriteTheSubsequentReview() {
        val model=vm("review")
        repeat(3) { attempt ->
            model.text("Старая строка $attempt 11")
            model.review()
            model.cancelReview()
            model.text("Молоко 2 л")
            model.review()
            await("The new review must finish") {model.uiState.value.review&&!model.uiState.value.busy}
            drainCancelledWork()
            val state=model.uiState.value
            assertFalse(state.parsing)
            assertNull(state.error)
            assertEquals("Молоко 2 л",state.text)
            assertEquals(listOf("Молоко"),state.rows.map{it.name})
            assertEquals(listOf(milk),state.rows.map{it.productId})
            assertEquals(listOf("2"),state.rows.map{it.quantity})
            model.edit()
        }
    }

    @Test fun reviewedChoiceAddsAtomicallyAndCanBeRetriedAfterAWriteFailure() {
        val model=vm("add")
        val raw="яблоки 0,5 кг\nМолоко 2 л"
        model.text(raw)
        model.review()
        await("Review must finish") {model.uiState.value.review&&!model.uiState.value.busy}
        val apple=model.uiState.value.rows.first()
        assertFalse("A generic apple name needs an explicit choice",apple.resolved)
        assertTrue(apple.candidates.any{it.id==gala})
        model.choose(apple.index,gala)
        assertEquals(gala,model.uiState.value.rows.first().productId)
        model.update(1){it.copy(quantity="-2")}
        model.add()
        assertNotNull(model.uiState.value.error)
        assertTrue(runBlocking{db.shoppingListEntryDao().getAllSync()}.isEmpty())
        model.update(1){it.copy(quantity="2")}

        // Fail the second INSERT in the real SQLite transaction: the first item must roll back too.
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_second_bulk BEFORE INSERT ON shopping_list_entries WHEN NEW.productId=$milk BEGIN SELECT RAISE(ABORT,'bulk test rollback'); END")
        model.add()
        await("Failed batch must report an error and release busy") {!model.uiState.value.busy&&model.uiState.value.error!=null}
        assertEquals(0,model.uiState.value.added)
        assertTrue(runBlocking{db.shoppingListEntryDao().getAllSync()}.isEmpty())
        assertEquals(raw,drafts.getString("bulk_text",null))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_second_bulk")

        model.add()
        model.add() // Busy guard must prevent a second batch while the first is pending.
        await("Retry must add both selected rows") {model.uiState.value.added==2&&!model.uiState.value.busy}
        val entries=runBlocking{db.shoppingListEntryDao().getAllSync()}.associateBy{it.productId}
        assertEquals(setOf(gala,milk),entries.keys)
        assertEquals(.5,entries.getValue(gala).quantity,0.0)
        assertEquals("кг",entries.getValue(gala).unit)
        assertEquals(2.0,entries.getValue(milk).quantity,0.0)
        assertEquals("л",entries.getValue(milk).unit)
        assertEquals(setOf(shop),entries.values.map{it.assignedShopId}.toSet())
        assertNull(model.uiState.value.error)
        assertFalse(drafts.contains("bulk_text"))
        assertEquals("",vm("after-success").uiState.value.text)
        model.undo()
        await("Undo must restore the original draft") {!model.uiState.value.busy&&model.uiState.value.added==0}
        assertTrue(runBlocking{db.shoppingListEntryDao().getAllSync()}.isEmpty())
        assertEquals(raw,drafts.getString("bulk_text",null))
    }

    @Test fun storesFromHeadersOverrideDefaultsAndUndoRestoresTheBasket() {
        val second=runBlocking{db.shopDao().insert(ShopEntity(name="Батон",cityId=1))}
        val model=vm("sections")
        model.text("Магазин рядом:\nЯблоки Гала 0,5 кг\nБатон:\nМолоко 2 л\nБез магазина:\nНовый товар 1")
        model.review()
        await("Sections must resolve") {model.uiState.value.review&&!model.uiState.value.busy}
        assertEquals(3,model.uiState.value.rows.size)
        assertEquals(listOf(shop,second,null),model.uiState.value.rows.map{it.shopId})
        assertTrue(model.uiState.value.rows.all{it.shopResolved})
        model.shop(second) // A global override must not override explicit headings or NONE.
        model.add()
        await("Three purchases must be added") {model.uiState.value.added==3&&!model.uiState.value.busy}
        val entries=runBlocking{db.shoppingListEntryDao().getAllSync()}
        assertEquals(shop,entries.single{it.productId==gala}.assignedShopId)
        assertEquals(second,entries.single{it.productId==milk}.assignedShopId)
        assertNull(entries.single{it.productId!=gala&&it.productId!=milk}.assignedShopId)
        assertEquals(2,runBlocking{db.shopDao().getAllSync()}.size)
        model.undo()
        await("Undo must finish") {!model.uiState.value.busy&&model.uiState.value.added==0}
        assertTrue(runBlocking{db.shoppingListEntryDao().getAllSync()}.isEmpty())
    }

    @Test fun unknownOrAmbiguousHeaderNeverSilentlyCreatesOrChoosesAStore() {
        runBlocking {
            db.shopDao().insert(ShopEntity(name="Батон",address="Улица 1",cityId=1))
            db.shopDao().insert(ShopEntity(name="Батон",address="Улица 2",cityId=1))
            db.cityDao().insert(CityEntity(2,"Другой город"))
            db.shopDao().insert(ShopEntity(name="Чужой магазин",cityId=2))
        }
        val model=vm("ambiguous")
        model.text("Батон:\nМолоко 1\nЧужой магазин:\nЯблоки Гала 1")
        model.review()
        await("Review must finish") {model.uiState.value.review&&!model.uiState.value.busy}
        assertFalse(model.uiState.value.rows[0].shopResolved)
        assertFalse(model.uiState.value.rows[1].shopResolved)
        model.add()
        assertNotNull(model.uiState.value.error)
        assertTrue(runBlocking{db.shoppingListEntryDao().getAllSync()}.isEmpty())
        model.chooseShop(0,shop)
        model.chooseShop(1,null)
        model.add()
        await("Explicit correction must add") {model.uiState.value.added==2&&!model.uiState.value.busy}
        assertEquals(setOf(shop,null),runBlocking{db.shoppingListEntryDao().getAllSync()}.map{it.assignedShopId}.toSet())
        assertEquals(4,runBlocking{db.shopDao().getAllSync()}.size)
    }

    @Test fun storeTypoRequiresConfirmationAndShowsCandidate() {
        val model=vm("shop-typo")
        model.text("Магазин ряом:\nМолоко 1")
        model.review()
        await("Review must finish") {model.uiState.value.review&&!model.uiState.value.busy}
        val line=model.uiState.value.rows.single()
        assertFalse(line.shopResolved)
        assertTrue(line.shopMatches.contains(shop))
        model.chooseShop(0,shop)
        model.add()
        await("Confirmed shop must add") {model.uiState.value.added==1&&!model.uiState.value.busy}
        assertEquals(shop,runBlocking{db.shoppingListEntryDao().getAllSync()}.single().assignedShopId)
    }
}
