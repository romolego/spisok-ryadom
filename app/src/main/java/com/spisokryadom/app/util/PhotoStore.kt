package com.spisokryadom.app.util
import android.content.Context
import android.net.Uri
import android.graphics.*
import android.media.ExifInterface
import java.io.File
import java.util.UUID
object PhotoStore {
    fun importPhoto(context:Context,uri:Uri):String {
        val dir=File(context.filesDir,"product_photos").also{it.mkdirs()}
        val temp=File(context.cacheDir,"photo_${UUID.randomUUID()}.source")
        val target=File(dir,"${UUID.randomUUID()}.jpg")
        try{
            val stream=context.contentResolver.openInputStream(uri)?:error("Фотография недоступна")
            stream.use{input->temp.outputStream().use{out->val buffer=ByteArray(16384);var total=0;while(true){val n=input.read(buffer);if(n<0)break;total+=n;require(total<=32*1024*1024){"Фотография слишком большая"};out.write(buffer,0,n)}}}
            var bitmap=load(temp.absolutePath,1600)?:error("Не удалось прочитать изображение")
            val orientation=runCatching{ExifInterface(temp.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)}.getOrDefault(1)
            val matrix=Matrix()
            when(orientation){2->matrix.setScale(-1f,1f);3->matrix.setRotate(180f);4->{matrix.setRotate(180f);matrix.postScale(-1f,1f)};5->{matrix.setRotate(90f);matrix.postScale(-1f,1f)};6->matrix.setRotate(90f);7->{matrix.setRotate(-90f);matrix.postScale(-1f,1f)};8->matrix.setRotate(-90f)}
            if(!matrix.isIdentity){val adjusted=Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true);if(adjusted!=bitmap)bitmap.recycle();bitmap=adjusted}
            target.outputStream().use{require(bitmap.compress(Bitmap.CompressFormat.JPEG,85,it)){"Не удалось сохранить фото"}}
            bitmap.recycle();return target.absolutePath
        }catch(e:Exception){target.delete();throw e}finally{temp.delete()}
    }
    fun load(path:String,maxSize:Int=800):Bitmap?{
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(path,bounds)
        if(bounds.outWidth<=0||bounds.outHeight<=0)return null
        var sample=1
        while(maxOf(bounds.outWidth,bounds.outHeight)/sample>maxSize)sample*=2
        return BitmapFactory.decodeFile(path,BitmapFactory.Options().apply{inSampleSize=sample})
    }
    fun cleanup(context:Context,retained:Set<String>){
        val dir=File(context.filesDir,"product_photos");val now=System.currentTimeMillis()
        if(dir.isDirectory)dir.walkTopDown().filter{it.isFile&&it.absolutePath !in retained&&now-it.lastModified()>300_000}.forEach{it.delete()}
    }
}
