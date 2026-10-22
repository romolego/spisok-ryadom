package com.spisokryadom.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.CityEntity
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.ui.goallists.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.*
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="w640dp-h360dp-xhdpi")
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class Templates240LandscapeUiTest {
 @get:Rule val ui=createComposeRule()
 @Test fun confirmationStaysReachableWithManyDestinationsAndCopiesOnlyAfterTap(){
  val app=ApplicationProvider.getApplicationContext<Application>()
  val db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build()
  val location=LocationRepository(db.cityDao(),db.shopDao(),app.getSharedPreferences("240-landscape",0))
  val repository=GoalListRepository(db,ShoppingListRepository(db.shoppingListEntryDao(),db,location))
  val id=runBlocking{db.cityDao().insert(CityEntity(1,"Город"));val id=repository.create("На неделю");repository.addQuick(id,"Хлеб");repeat(12){repository.create("Список $it")};id}
  val vm=ListCopyViewModel(id,repository)
  var root:android.view.View?=null
  try{
   ui.setContent{val view=LocalView.current;SideEffect{root=view.rootView};MaterialTheme{ListCopySheet(id,"landscape",{},model=vm,onApplied={})}}
   ui.waitUntil(20_000){Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(20));runCatching{ui.onNodeWithTag("list-copy-confirm").assertIsEnabled()}.isSuccess}
   ui.onNodeWithTag("list-copy-confirm").assertIsDisplayed()
   assertTrue(runBlocking{db.shoppingListEntryDao().getVisibleSync().isEmpty()})
   ui.runOnIdle{val view=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf{it.isShowing}?.window?.decorView?:root!!;val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap));val file=File("build/reports/refine240/screens/template-landscape-api35.png");file.parentFile!!.mkdirs();file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
   ui.onNodeWithTag("list-copy-confirm").performClick()
   ui.waitUntil(20_000){Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(20));vm.uiState.value.completed}
   assertEquals(1,runBlocking{db.shoppingListEntryDao().getVisibleSync().size});assertEquals(1,runBlocking{repository.snapshot(id).entries.size})
  }finally{vm.viewModelScope.cancel();location.closeForTest();db.close()}
 }
}
