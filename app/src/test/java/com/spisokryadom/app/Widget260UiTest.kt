package com.spisokryadom.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.ShopChoice
import com.spisokryadom.app.ui.shoppinglist.ViewMode
import com.spisokryadom.app.widget.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.*
import java.io.File
import java.time.Duration

/** Real Room, RemoteViews inflation/reapplication, broadcast PendingIntents and native drawing.
 * Most cases deliberately do not start MainActivity: the widget must work without an app screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = SpisokRyadomApp::class, qualifiers = "w360dp-h800dp-xhdpi")
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Widget260UiTest {
    @get:Rule val ui = createEmptyComposeRule()
    @get:Rule val case = org.junit.rules.TestName()
    private val app get() = ApplicationProvider.getApplicationContext<SpisokRyadomApp>()
    private val db get() = app.container.database
    private val basket get() = app.container.shoppingListRepository
    private val prefs get() = app.getSharedPreferences(WidgetSettings.PREFS, 0)
    private var scenario: ActivityScenario<MainActivity>? = null
    private var activity: MainActivity? = null
    private val visibilityReceiver = ShoppingWidgetProvider()
    private var receiverRegistered = false
    private var shop = 0L
    private var plainShop = 0L
    private var pending = 0L
    private var bought = 0L
    private var other = 0L
    private var unassigned = 0L

    @Before fun seed() = runBlocking {
        listOf("drafts", "product_card_drafts", "shopping_list_prefs", "product_db_prefs").forEach {
            app.getSharedPreferences(it, 0).edit().clear().commit()
        }
        db.shoppingListEntryDao().deleteAll()
        db.goalListDao().deleteAll()
        db.productDao().deleteAll()
        db.shopDao().deleteAll()
        db.productGroupDao().deleteAll()
        db.recipientDao().deleteAll()
        app.container.locations.select(1)
        val food = db.productGroupDao().insert(ProductGroupEntity(name = "Продукты"))
        val hygiene = db.productGroupDao().insert(ProductGroupEntity(name = "Гигиена", displayOrder = 1))
        val recipient = db.recipientDao().insert(RecipientEntity(name = "Рома"))
        shop = db.shopDao().insert(ShopEntity(name = "Красный Яр", shoppingBackground = "#DC7090", purchaseBackground = "auto"))
        plainShop = db.shopDao().insert(ShopEntity(name = "Батон", displayOrder = 1))
        val department = db.shopDepartmentDao().insert(ShopDepartmentEntity(shopId = shop, name = "Овощи и фрукты"))
        suspend fun add(name: String, group: Long?, shopId: Long?, departmentId: Long? = null): Long {
            val product = app.container.productRepository.saveWithLinks(
                ProductEntity(name = name, productGroupId = group, recipientId = recipient, defaultUnit = "шт"),
                if (shopId == null) emptyList() else listOf(ProductShopLinkEntity(productId = 0, shopId = shopId, departmentId = departmentId))
            )
            return basket.addProduct(product, quantity = 2.0, choice = if (shopId == null) ShopChoice.NONE else ShopChoice.EXPLICIT, shopId = shopId)
        }
        pending = add("Бананы", food, shop, department)
        bought = add("Молоко", food, shop)
        basket.markBought(bought)
        other = add("Шампунь", hygiene, plainShop)
        unassigned = add("Подарочный пакет", null, null)
        // Make accidental coupling to the application's grouping observable.
        prefs.edit().putString("shopping_list_view_mode", ViewMode.FLAT.name).commit()
        WidgetSettings().save(prefs)
        registerVisibilityReceiver()
    }

    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag") // Runtime registration is test-only; production uses its private manifest receiver.
    private fun registerVisibilityReceiver() {
        // Robolectric delivers runtime registrations rather than instantiating the manifest
        // receiver for this PendingIntent. Register the actual production receiver, keeping
        // delivery through RemoteViews.performClick -> PendingIntent -> broadcast intact.
        val target = ShoppingWidgetProvider.visibilityIntent(app, false).component!!
        assertEquals(ShoppingWidgetProvider::class.java.name, target.className)
        val info = app.packageManager.getReceiverInfo(target, android.content.pm.PackageManager.GET_META_DATA)
        assertTrue(info.enabled)
        assertFalse(info.exported)
        assertTrue(info.metaData.containsKey("android.appwidget.provider"))
        val filter = android.content.IntentFilter(ShoppingWidgetProvider.ACTION_SET_BOUGHT_VISIBILITY)
        if (android.os.Build.VERSION.SDK_INT >= 33) app.registerReceiver(visibilityReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        else { @Suppress("DEPRECATION") app.registerReceiver(visibilityReceiver, filter) }
        receiverRegistered = true
    }

    @After fun close() {
        if (receiverRegistered) app.unregisterReceiver(visibilityReceiver)
        scenario?.close()
        app.container.locations.closeForTest()
        db.close()
        org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java, "INSTANCE", null)
    }

    private fun waitFor(condition: () -> Boolean) {
        ui.waitUntil(25_000) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20))
            ui.waitForIdle()
            condition()
        }
    }
    private fun waitTag(tag: String) = waitFor { ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun settings() {
        scenario = ActivityScenario.launch(MainActivity::class.java).also { it.onActivity { current -> activity = current } }
        waitFor { ui.onAllNodesWithContentDescription("Настройки").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithContentDescription("Настройки").performClick()
        waitTag("settings-list")
        if(ui.onAllNodesWithTag("widget-grouping").fetchSemanticsNodes().isEmpty()){ui.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("widget-settings"));ui.onNodeWithTag("widget-settings").performClick()}
    }
    private fun switch(tag: String) = ui.onNodeWithTag(tag).performScrollTo().performClick()
    private fun group(label: String, mode: ViewMode) {
        ui.onNodeWithText("Группировка в виджете").performScrollTo().performClick()
        ui.onAllNodesWithText(label).onLast().performClick()
        waitFor { WidgetSettings.load(prefs).mode == mode }
    }
    private fun frame(id: Int = 260, width: Int = 660, height: Int = 850): Pair<FrameLayout, View> {
        val host = FrameLayout(app)
        val root = ShoppingWidgetProvider.views(app, id).apply(app, host)
        host.addView(root)
        measure(host, width, height)
        return host to root
    }
    private fun measure(view: View, width: Int = 660, height: Int = 850) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
    }
    private fun factory() = ShoppingWidgetFactory(app).also { it.onCreate() }
    private fun ids(factory: ShoppingWidgetFactory) = (0 until factory.count).map { factory.getItemId(it) }.filter { it > 0 }.toSet()
    private fun row(factory: ShoppingWidgetFactory, id: Long): android.widget.RemoteViews =
        factory.getViewAt((0 until factory.count).single { factory.getItemId(it) == id })!!
    private fun headers(factory: ShoppingWidgetFactory): List<String> = (0 until factory.count).mapNotNull { index ->
        val view = factory.getViewAt(index)!!.apply(app, FrameLayout(app))
        view.findViewById<TextView>(R.id.widget_section_title)?.text?.toString()
    }
    private fun showCollection(factory: ShoppingWidgetFactory, width: Int = 660, height: Int = 850, id: Int = 260): Pair<FrameLayout, View> {
        val (host, root) = frame(id = id, width = width, height = height)
        root.findViewById<ListView>(R.id.widget_list).adapter = object : BaseAdapter() {
            override fun getCount() = factory.count
            override fun getItem(position: Int) = position
            override fun getItemId(position: Int) = factory.getItemId(position)
            override fun getView(position: Int, convertView: View?, parent: ViewGroup) = factory.getViewAt(position)!!.apply(app, parent)
        }
        measure(host, width, height)
        return host to root
    }
    private fun shot(name: String, supplied: View? = null) {
        if (activity != null) ui.waitForIdle()
        val view = supplied ?: org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView ?: activity!!.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val file = File("build/reports/widget260/screens/$name-api${android.os.Build.VERSION.SDK_INT}.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun backgroundPixel(view: View, width: Int = 660, height: Int = 140): Int {
        measure(view, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val drawable = if (view is android.widget.ImageView) view.drawable else view.background
        drawable?.let { drawable ->
            // These views are unattached: View.draw normally establishes background bounds.
            // Set them explicitly when drawing just the isolated background (without children).
            drawable.setBounds(0, 0, width, height)
            drawable.draw(Canvas(bitmap))
        }
        // Far from text, checkbox, rounded corners and the frame edges.
        return bitmap.getPixel(width - 30, height / 2).also { bitmap.recycle() }
    }

    @Test fun actualSettingsPersistIndependentGroupingVisibilityAndAppearanceAcrossRecreation() {
        settings()
        group("По товарным группам", ViewMode.BY_GROUPS)
        switch("widget-show-bought")
        switch("widget-bought-toggle")
        ui.onNodeWithTag("widget-shop-background").performScrollTo().performClick()
        ui.onNodeWithTag("widget-product-background").performScrollTo().performClick()
        ui.onNodeWithTag("widget-shop-background").assertIsOn()
        ui.onNodeWithTag("widget-product-background").assertIsOn()
        shot("appearance-settings")
        ui.onAllNodesWithText("Готово").onLast().performClick()
        waitFor { WidgetSettings.load(prefs).let { !it.showBought && !it.showBoughtToggle && it.showShopBackground && it.showProductBackground } }
        assertEquals(ViewMode.FLAT.name, prefs.getString("shopping_list_view_mode", null))
        scenario!!.recreate()
        scenario!!.onActivity { activity = it }
        waitTag("settings-list")
        if(ui.onAllNodesWithTag("widget-grouping").fetchSemanticsNodes().isEmpty()){ui.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("widget-settings"));ui.onNodeWithTag("widget-settings").performClick()}
        ui.onNodeWithTag("widget-show-bought").assertIsOff()
        ui.onNodeWithTag("widget-bought-toggle").assertIsOff()
        ui.onNode(hasText("По товарным группам") and hasAnyAncestor(hasTestTag("widget-grouping"))).assertExists()
        ui.onNodeWithTag("widget-shop-background").assertIsOn()
        ui.onNodeWithTag("widget-product-background").assertIsOn()
        assertEquals(4, runBlocking { db.shoppingListEntryDao().getAllSync().size })
    }

    @Test fun widgetEyePendingIntentHidesAndRestoresBoughtWithoutChangingAnyPurchase() {
        val before = runBlocking { db.shoppingListEntryDao().getAllSync() }
        val factory = factory()
        try {
            assertEquals(setOf(pending, bought, other, unassigned), ids(factory))
            val (_, original) = frame()
            val toggle = original.findViewById<View>(R.id.widget_bought_toggle)
            assertEquals("Скрыть купленные товары", toggle.contentDescription.toString())
            assertTrue(toggle.performClick()) // Framework RemoteViews listener sends the real broadcast PendingIntent.
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertFalse(WidgetSettings.load(prefs).showBought)
            factory.onDataSetChanged()
            assertEquals(setOf(pending, other, unassigned), ids(factory))
            val (host, hidden) = showCollection(factory)
            assertEquals(View.VISIBLE, hidden.findViewById<View>(R.id.widget_bought_status).visibility)
            assertEquals("Купленные скрыты", hidden.findViewById<TextView>(R.id.widget_bought_status).text.toString())
            assertEquals("Показать купленные товары", hidden.findViewById<View>(R.id.widget_bought_toggle).contentDescription.toString())
            shot("widget-bought-hidden", host)
            // A repeated old hide action is idempotent and cannot accidentally show purchases again.
            assertTrue(toggle.performClick())
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertFalse(WidgetSettings.load(prefs).showBought)
            settings()
            ui.onNodeWithTag("widget-show-bought").assertIsOff()
            scenario!!.recreate()
            scenario!!.onActivity { activity = it }
            waitTag("settings-list")
            if(ui.onAllNodesWithTag("widget-grouping").fetchSemanticsNodes().isEmpty()){ui.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("widget-settings"));ui.onNodeWithTag("widget-settings").performClick()}
            ui.onNodeWithTag("widget-show-bought").assertIsOff()
            assertTrue(hidden.findViewById<View>(R.id.widget_bought_toggle).performClick())
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertTrue(WidgetSettings.load(prefs).showBought)
            waitFor { runCatching { ui.onNodeWithTag("widget-show-bought").assertIsOn() }.isSuccess }
            factory.onDataSetChanged()
            assertEquals(setOf(pending, bought, other, unassigned), ids(factory))
            assertEquals(before, runBlocking { db.shoppingListEntryDao().getAllSync() })
        } finally { factory.onDestroy() }
    }

    @Test fun disabledWidgetButtonDisappearsAndAnAlreadyRenderedStaleActionDoesNothing() {
        val before = runBlocking { db.shoppingListEntryDao().getAllSync() }
        val (_, oldRoot) = frame()
        val staleButton = oldRoot.findViewById<View>(R.id.widget_bought_toggle)
        // Positive control proves that this exact listener and registration deliver a command;
        // the later disabled-action assertion cannot pass merely because no receiver was run.
        assertTrue(staleButton.performClick())
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertFalse(WidgetSettings.load(prefs).showBought)
        val (_, hiddenRoot) = frame(262)
        assertTrue(hiddenRoot.findViewById<View>(R.id.widget_bought_toggle).performClick())
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue(WidgetSettings.load(prefs).showBought)
        WidgetSettings(showBoughtToggle = false).save(prefs)
        val (_, current) = frame(261)
        assertEquals(View.GONE, current.findViewById<View>(R.id.widget_bought_toggle).visibility)
        assertTrue(staleButton.performClick())
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue(WidgetSettings.load(prefs).showBought)
        assertEquals(before, runBlocking { db.shoppingListEntryDao().getAllSync() })
        WidgetSettings(showBought = false, showBoughtToggle = false).save(prefs)
        ShoppingWidgetProvider.views(app, 261).reapply(app, current)
        assertEquals(View.GONE, current.findViewById<View>(R.id.widget_bought_toggle).visibility)
        assertEquals(View.VISIBLE, current.findViewById<View>(R.id.widget_bought_status).visibility)
    }

    @Test fun nativeRowsFollowProductGroupsAndFlatModeWithoutLosingPendingOrBoughtItems() {
        val before = runBlocking { db.shoppingListEntryDao().getAllSync() }
        val factory = factory()
        try {
            WidgetSettings(mode = ViewMode.BY_GROUPS, showBought = false).save(prefs)
            factory.onDataSetChanged()
            assertEquals(setOf("Продукты", "Гигиена", "Без группы"), headers(factory).toSet())
            assertEquals(setOf(pending, other, unassigned), ids(factory))
            shot("widget-by-product-groups", showCollection(factory).first)
            WidgetSettings(mode = ViewMode.FLAT).save(prefs)
            factory.onDataSetChanged()
            assertTrue(headers(factory).isEmpty())
            assertEquals(setOf(pending, bought, other, unassigned), ids(factory))
            val boughtView = row(factory, bought).apply(app, FrameLayout(app))
            assertTrue(boughtView.findViewById<TextView>(R.id.widget_item_title).paintFlags and Paint.STRIKE_THRU_TEXT_FLAG != 0)
            assertEquals("Вернуть: Молоко", boughtView.findViewById<View>(R.id.widget_check).contentDescription.toString())
            shot("widget-flat", showCollection(factory).first)
            assertEquals(before, runBlocking { db.shoppingListEntryDao().getAllSync() })
        } finally { factory.onDestroy() }
    }

    @Test fun shopAndProductTintAreIndependentAndRemoteViewsReapplyClearsRecycledColors() {
        val factory = factory()
        try {
            val holder = FrameLayout(app)
            val plain = row(factory, pending).apply(app, holder)
            val defaultCard = backgroundPixel(plain.findViewById(R.id.widget_purchase_background))
            assertEquals(Color.WHITE, defaultCard)
            WidgetSettings(showShopBackground = true, showProductBackground = false).save(prefs)
            factory.onDataSetChanged()
            row(factory, pending).reapply(app, plain)
            val tint = backgroundPixel(plain.findViewById(R.id.widget_purchase_root))
            assertTrue("The shop area must visibly inherit the configured rose tint", Color.red(tint) > Color.green(tint))
            assertEquals(defaultCard, backgroundPixel(plain.findViewById(R.id.widget_purchase_background)))
            WidgetSettings(showShopBackground = true, showProductBackground = true).save(prefs)
            factory.onDataSetChanged()
            row(factory, pending).reapply(app, plain)
            val coloredCard = backgroundPixel(plain.findViewById(R.id.widget_purchase_background))
            assertNotEquals(defaultCard, coloredCard)
            assertTrue(Color.red(coloredCard) > Color.green(coloredCard))
            val sectionIndex = (0 until factory.count).single { index ->
                factory.getItemId(index) < 0 && factory.getViewAt(index)!!.apply(app, holder).findViewById<TextView>(R.id.widget_section_title).text.toString() == "Красный Яр"
            }
            val section = factory.getViewAt(sectionIndex)!!.apply(app, holder)
            assertTrue(Color.red(backgroundPixel(section)) > Color.green(backgroundPixel(section)))
            shot("widget-shop-and-product-colors", showCollection(factory).first)
            // Recycle both layout types for another shop, then switch settings off and reuse again.
            row(factory, other).reapply(app, plain)
            assertEquals(defaultCard, backgroundPixel(plain.findViewById(R.id.widget_purchase_background)))
            WidgetSettings(showShopBackground = false, showProductBackground = false).save(prefs)
            factory.onDataSetChanged()
            row(factory, pending).reapply(app, plain)
            assertEquals(defaultCard, backgroundPixel(plain.findViewById(R.id.widget_purchase_background)))
            assertEquals(Color.TRANSPARENT, backgroundPixel(plain.findViewById(R.id.widget_purchase_root)))
            factory.getViewAt(sectionIndex)!!.reapply(app, section)
            assertEquals(Color.TRANSPARENT, backgroundPixel(section))
        } finally { factory.onDestroy() }
    }

    @Test fun narrowWidgetAtLargestFontKeepsBothHeaderActionsReachableAndShowsHiddenState() {
        WidgetSettings(textSize = 22, showBought = false).save(prefs)
        val manager = AppWidgetManager.getInstance(app)
        val id = Shadows.shadowOf(manager).createWidget(ShoppingWidgetProvider::class.java, R.layout.shopping_widget)
        manager.updateAppWidgetOptions(id, Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 240)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 240)
        })
        val factory = factory()
        try {
            val (host, root) = showCollection(factory, width = 480, height = 620, id = id)
            val add = root.findViewById<View>(R.id.widget_add)
            val toggle = root.findViewById<View>(R.id.widget_bought_toggle)
            val heading = root.findViewById<TextView>(R.id.widget_heading)
            assertTrue(add.width >= 48 * app.resources.displayMetrics.density)
            assertTrue(toggle.width >= 48 * app.resources.displayMetrics.density)
            assertTrue(heading.width > 0)
            assertEquals("Покупки", heading.text.toString())
            assertTrue(add.right <= root.width - root.paddingRight)
            assertTrue(toggle.right <= root.width - root.paddingRight)
            assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widget_bought_status).visibility)
            assertEquals(setOf(pending, other, unassigned), ids(factory))
            shot("widget-narrow-largest-font", host)
        } finally { factory.onDestroy() }
    }

    @Test @Config(sdk = [35], qualifiers = "w640dp-h240dp-land-xhdpi")
    fun lowLandscapeWidgetKeepsVisibilityActionAndScrollablePurchasesAtLargestFont() {
        WidgetSettings(textSize = 22, showBought = false, showShopBackground = true, showProductBackground = true).save(prefs)
        val factory = factory()
        try {
            val (host, root) = showCollection(factory, width = 1280, height = 480)
            assertEquals(setOf(pending, other, unassigned), ids(factory))
            val list = root.findViewById<ListView>(R.id.widget_list)
            assertTrue(list.height > 96)
            assertTrue(list.canScrollVertically(1))
            assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widget_bought_toggle).visibility)
            assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widget_bought_status).visibility)
            shot("widget-low-landscape", host)
        } finally { factory.onDestroy() }
    }
}
