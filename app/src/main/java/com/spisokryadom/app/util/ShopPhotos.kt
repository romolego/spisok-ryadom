package com.spisokryadom.app.util

import com.spisokryadom.app.data.entity.ProductPhotoEntity
import org.json.JSONArray
import org.json.JSONObject

/** Ordered shop gallery; the first image is the primary photo. */
object ShopPhotos {
    fun decode(raw:String):List<ProductPhotoEntity> {
        val rows=JSONArray(raw);require(rows.length()<=5){"Не больше 5 фотографий магазина"}
        return (0 until rows.length()).map{i->val row=rows.getJSONObject(i)
            require(row.get("uri") is String&&row.get("caption") is String){"Неверная фотография магазина"}
            ProductPhotoEntity(0,i,row.getString("uri"),row.getString("caption")).also{
                require(it.uri.isNotBlank()&&it.caption.length<=1000&&'\u0000' !in it.caption){"Неверная фотография магазина"}
            }
        }
    }
    fun encode(photos:List<ProductPhotoEntity>):String {
        require(photos.size<=5)
        return JSONArray(photos.map{JSONObject().put("uri",it.uri).put("caption",it.caption)}).toString().also{decode(it)}
    }
}
