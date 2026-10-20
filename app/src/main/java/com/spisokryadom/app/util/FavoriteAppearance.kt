package com.spisokryadom.app.util

import android.content.SharedPreferences

/** One policy for catalogue and dictionary favorites; pinned copies have their own presentation. */
data class FavoriteStyle(val background:Boolean=true,val color:String="#E8B83F",val star:Boolean=true,val bold:Boolean=true)
data class FavoriteAppearance(val block:FavoriteStyle=FavoriteStyle(),val row:FavoriteStyle=FavoriteStyle(color="#F1CF78")) {
 fun save(prefs:SharedPreferences){prefs.edit().apply{
  listOf("block" to block,"row" to row).forEach{(scope,s)->
   putBoolean("favorite_${scope}_background",s.background);putString("favorite_${scope}_color",s.color)
   putBoolean("favorite_${scope}_star",s.star);putBoolean("favorite_${scope}_bold",s.bold)
  }
 }.apply()}
 companion object {
  val booleanKeys=(listOf("block","row").flatMap{s->listOf("background","star","bold").map{"favorite_${s}_$it"}}).toSet()
  val colorKeys=setOf("favorite_block_color","favorite_row_color")
  fun read(prefs:SharedPreferences):FavoriteAppearance{
   fun style(scope:String,default:FavoriteStyle)=FavoriteStyle(
    prefs.getBoolean("favorite_${scope}_background",default.background),
    prefs.getString("favorite_${scope}_color",default.color).orEmpty().takeIf{AppearanceColors.argb(it)!=null}?:default.color,
    prefs.getBoolean("favorite_${scope}_star",true),prefs.getBoolean("favorite_${scope}_bold",true))
   return FavoriteAppearance(style("block",FavoriteStyle()),style("row",FavoriteStyle(color="#F1CF78")))
  }
 }
}
