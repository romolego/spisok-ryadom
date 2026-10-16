package com.spisokryadom.app
import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class DatabaseSmokeTest {
 @Test fun realRoomDatabaseOpens() = runBlocking {
  val context=ApplicationProvider.getApplicationContext<Application>()
  val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).allowMainThreadQueries().build()
  try{db.cityDao().insert(CityEntity(1,"Иркутск"));val id=db.productDao().insert(ProductEntity(name="Яблоки Гала"));assertEquals("Яблоки Гала",db.productDao().getById(id)?.name)}finally{db.close()}
 }
}
