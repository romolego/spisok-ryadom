package com.spisokryadom.app

import android.os.Looper
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.ProductEntity
import com.spisokryadom.app.data.repository.ProductEditorTransactions
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.*
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=SpisokRyadomApp::class,qualifiers="w360dp-h640dp-xhdpi")
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class Reset210NavigationTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 private val app get()=ApplicationProvider.getApplicationContext<SpisokRyadomApp>()
 private fun waitFor(condition:()->Boolean){ui.waitUntil(25_000){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(25));ui.waitForIdle();condition()}}
 @After fun close(){ui.activityRule.scenario.close();val until=System.nanoTime()+20_000_000_000;while(ProductEditorTransactions.mutex.isLocked&&System.nanoTime()<until){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5)};app.container.locations.closeForTest();app.container.database.close();org.robolectric.util.ReflectionHelpers.setStaticField(AppDatabase::class.java,"INSTANCE",null)}
 @Test fun confirmedFullResetReturnsToFreshPurchasesAndKeepsNavigationUsable(){
  runBlocking{app.container.database.productDao().insert(ProductEntity(name="Удалить при сбросе"))}
  waitFor{ui.onAllNodesWithContentDescription("Настройки").fetchSemanticsNodes().isNotEmpty()};ui.onNodeWithContentDescription("Настройки").performClick()
  ui.onNodeWithTag("settings-list").performScrollToNode(hasText("Сброс"));ui.onNodeWithText("Сброс").performClick();ui.onNodeWithText("Сбросить всё приложение").performClick();ui.onNodeWithText("Да").performClick()
  waitFor{ui.onAllNodesWithText("Список покупок").fetchSemanticsNodes().isNotEmpty()}
  assertTrue(runBlocking{app.container.database.productDao().getAllSync().isEmpty()});assertEquals("Мой город",runBlocking{app.container.database.cityDao().getAllSync().single().name})
  ui.onNodeWithText("Товары").performClick();waitFor{ui.onAllNodesWithText("База товаров").fetchSemanticsNodes().isNotEmpty()};ui.onNodeWithText("Удалить при сбросе").assertDoesNotExist()
 }
}
