package com.spisokryadom.app.util

import android.content.SharedPreferences

/** One policy for catalogue and dictionary favorites; pinned copies have their own presentation. */
data class FavoriteStyle(val background:Boolean=true,val color:String="#E8B83F",val star:Boolean=true,val bold:Boolean=true)
enum class FavoriteSurface(val title:String) { LISTS("Списки"), PURCHASES("Покупки"), DROPDOWNS("Выпадающие списки") }
data class FavoriteAppearance(val block:FavoriteStyle=FavoriteStyle(),val row:FavoriteStyle=FavoriteStyle(color="#F1CF78"),
 val purchaseBlock:FavoriteStyle=block,val purchaseRow:FavoriteStyle=row,val dropdownBlock:FavoriteStyle=block,val dropdownRow:FavoriteStyle=row) {
 fun style(surface:FavoriteSurface,pinned:Boolean)=when(surface){FavoriteSurface.LISTS->if(pinned)block else row;FavoriteSurface.PURCHASES->if(pinned)purchaseBlock else purchaseRow;FavoriteSurface.DROPDOWNS->if(pinned)dropdownBlock else dropdownRow}
 fun change(surface:FavoriteSurface,pinned:Boolean,value:FavoriteStyle)=when(surface){
  FavoriteSurface.LISTS->if(pinned)copy(block=value)else copy(row=value)
  FavoriteSurface.PURCHASES->if(pinned)copy(purchaseBlock=value)else copy(purchaseRow=value)
  FavoriteSurface.DROPDOWNS->if(pinned)copy(dropdownBlock=value)else copy(dropdownRow=value)
 }
 fun save(prefs:SharedPreferences){prefs.edit().apply{
  scopes.forEach{(surface,pinned,scope)->val s=style(surface,pinned)
   putBoolean("favorite_${scope}_background",s.background);putString("favorite_${scope}_color",s.color)
   putBoolean("favorite_${scope}_star",s.star);putBoolean("favorite_${scope}_bold",s.bold)
  }
 }.apply()}
 companion object {
  val scopes=FavoriteSurface.entries.flatMap{surface->listOf(true,false).map{pinned->Triple(surface,pinned,(if(surface==FavoriteSurface.LISTS)""else surface.name.lowercase()+"_")+(if(pinned)"block"else "row"))}}
  val booleanKeys=scopes.flatMap{(_,_,s)->listOf("background","star","bold").map{"favorite_${s}_$it"}}.toSet()
  val colorKeys=scopes.map{(_,_,s)->"favorite_${s}_color"}.toSet()
  fun read(prefs:SharedPreferences):FavoriteAppearance{
   fun style(scope:String,default:FavoriteStyle)=FavoriteStyle(
    prefs.getBoolean("favorite_${scope}_background",default.background),
    prefs.getString("favorite_${scope}_color",default.color).orEmpty().takeIf{AppearanceColors.argb(it)!=null}?:default.color,
    prefs.getBoolean("favorite_${scope}_star",default.star),prefs.getBoolean("favorite_${scope}_bold",default.bold))
   val block=style("block",FavoriteStyle());val row=style("row",FavoriteStyle(color="#F1CF78"))
   return FavoriteAppearance(block,row,style("purchases_block",block),style("purchases_row",row),style("dropdowns_block",block),style("dropdowns_row",row))
  }
 }
}
