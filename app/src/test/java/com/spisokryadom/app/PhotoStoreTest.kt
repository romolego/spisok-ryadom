package com.spisokryadom.app
import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.util.PhotoStore
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoStoreTest {
 @Test fun largePhotoIsDownsampledAndOriented(){
  val context=ApplicationProvider.getApplicationContext<Application>();val source=File(context.cacheDir,"camera.jpg")
  val bitmap=Bitmap.createBitmap(3000,2000,Bitmap.Config.ARGB_8888);source.outputStream().use{bitmap.compress(Bitmap.CompressFormat.JPEG,80,it)};bitmap.recycle()
  val exif=ExifInterface(source.absolutePath);exif.setAttribute(ExifInterface.TAG_ORIENTATION,"6");exif.saveAttributes()
  val path=PhotoStore.importPhoto(context,Uri.fromFile(source));val result=BitmapFactory.decodeFile(path)
  assertTrue(result.width<=1600&&result.height<=1600);assertTrue(result.height>result.width);result.recycle()
  val preview=PhotoStore.load(path)!!;assertTrue(maxOf(preview.width,preview.height)<=800);preview.recycle()
 }
}
