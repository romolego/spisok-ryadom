package com.spisokryadom.app.util
import android.content.Context
import android.net.Uri
import android.graphics.*
import android.media.ExifInterface
import java.io.File
import java.util.UUID
object PhotoStore {
    private val leases=java.util.concurrent.ConcurrentHashMap<String,java.util.concurrent.atomic.AtomicInteger>()
    private val drafts=java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    fun pin(paths:Set<String>){paths.forEach{path->leases.compute(path){_,count->(count?:java.util.concurrent.atomic.AtomicInteger()).also{it.incrementAndGet()}}}}
    fun unpin(paths:Set<String>){paths.forEach{leases.computeIfPresent(it){_,count->if(count.decrementAndGet()<=0)null else count}}}

    fun importPhoto(context:Context,uri:Uri,directory:String="product_photos"):String {
        require(directory in setOf("product_photos","branch_photos","shop_logos","venue_photos"))
        val dir=File(context.filesDir,directory).also{it.mkdirs()}
        val temp=File(context.cacheDir,"photo_${UUID.randomUUID()}.source")
        val target=File(dir,"${UUID.randomUUID()}."+if(directory=="shop_logos")"png"else "jpg")
        try{
            val stream=context.contentResolver.openInputStream(uri)?:error("Фотография недоступна")
            stream.use{input->temp.outputStream().use{out->val buffer=ByteArray(16384);var total=0;while(true){val n=input.read(buffer);if(n<0)break;total+=n;require(total<=32*1024*1024){"Фотография слишком большая"};out.write(buffer,0,n)}}}
            var bitmap=load(temp.absolutePath,if(directory=="shop_logos")256 else 1600)?:error("Не удалось прочитать изображение")
            val orientation=runCatching{ExifInterface(temp.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)}.getOrDefault(1)
            val matrix=Matrix()
            when(orientation){2->matrix.setScale(-1f,1f);3->matrix.setRotate(180f);4->{matrix.setRotate(180f);matrix.postScale(-1f,1f)};5->{matrix.setRotate(90f);matrix.postScale(-1f,1f)};6->matrix.setRotate(90f);7->{matrix.setRotate(-90f);matrix.postScale(-1f,1f)};8->matrix.setRotate(-90f)}
            if(!matrix.isIdentity){val adjusted=Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true);if(adjusted!=bitmap)bitmap.recycle();bitmap=adjusted}
            target.outputStream().use{require(bitmap.compress(if(directory=="shop_logos")Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,85,it)){"Не удалось сохранить фото"}}
            bitmap.recycle();drafts.add(target.absolutePath);return target.absolutePath
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
        val editorPhotos=context.getSharedPreferences("product_card_drafts",0).all.values.mapNotNull{raw->runCatching{org.json.JSONObject(raw as String).optString("photoUri").takeIf{it.isNotBlank()}}.getOrNull()}.toSet()
        val now=System.currentTimeMillis()
        listOf("product_photos","branch_photos","shop_logos","venue_photos").forEach{name->
            val dir=File(context.filesDir,name)
            if(dir.isDirectory)dir.walkTopDown().onEnter{!leases.containsKey(it.absolutePath)}.filter{it.isFile&&it.absolutePath !in retained&&it.absolutePath !in editorPhotos&&!leases.containsKey(it.absolutePath)&&it.absolutePath !in drafts&&now-it.lastModified()>300_000}.forEach{it.delete()}
        }
    }
}
