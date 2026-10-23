package com.spisokryadom.app.ui.components

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spisokryadom.app.util.FavoriteAppearance

val LocalFavoriteAppearance=staticCompositionLocalOf{FavoriteAppearance()}
val LocalFavoriteBlock=staticCompositionLocalOf{false}
val LocalFavoriteSurface=staticCompositionLocalOf{com.spisokryadom.app.util.FavoriteSurface.LISTS}
@Composable fun FavoriteAppearanceProvider(content:@Composable ()->Unit){
 val context=LocalContext.current
 val prefs=remember(context){context.getSharedPreferences("shopping_list_prefs",Context.MODE_PRIVATE)}
 var appearance by remember(prefs){mutableStateOf(FavoriteAppearance.read(prefs))}
 DisposableEffect(prefs){
  val listener=SharedPreferences.OnSharedPreferenceChangeListener{_,_->appearance=FavoriteAppearance.read(prefs)}
  prefs.registerOnSharedPreferenceChangeListener(listener)
  onDispose{prefs.unregisterOnSharedPreferenceChangeListener(listener)}
 }
 CompositionLocalProvider(LocalFavoriteAppearance provides appearance,content=content)
}
@Composable fun favoriteStyle()=LocalFavoriteAppearance.current.style(LocalFavoriteSurface.current,LocalFavoriteBlock.current)
@Composable fun favoriteWeight(favorite:Boolean)=if(favorite&&favoriteStyle().bold)FontWeight.Bold else null
@Composable fun favoriteBackground(favorite:Boolean,base:Color=MaterialTheme.colorScheme.surface):Color=
 if(favorite&&favoriteStyle().background)shoppingBlockColor(favoriteStyle().color,base)?:base else base
@Composable fun Modifier.favoriteSurface(favorite:Boolean):Modifier=if(favorite&&favoriteStyle().background)background(favoriteBackground(true),MaterialTheme.shapes.small)else this
/** Extends into LazyColumn spacing, so the pinned section reads as one zone. */
@Composable fun Modifier.favoriteBlock():Modifier{
 val style=LocalFavoriteAppearance.current.style(LocalFavoriteSurface.current,true)
 val color=shoppingBlockColor(style.color,MaterialTheme.colorScheme.surface)?:MaterialTheme.colorScheme.secondaryContainer
 return if(style.background)drawBehind{val gap=4.dp.toPx();drawRect(color,Offset(0f,-gap),Size(size.width,size.height+gap*2))}else this
}
@Composable fun FavoriteBlock(modifier:Modifier=Modifier,content:@Composable ()->Unit){
 CompositionLocalProvider(LocalFavoriteBlock provides true){Box(modifier.favoriteBlock()){content()}}
}
@Composable fun FavoriteStar(favorite:Boolean,description:String="Избранный товар",modifier:Modifier=Modifier){
 if(favorite&&favoriteStyle().star)Icon(Icons.Filled.Star,description,modifier.size(17.dp),tint=MaterialTheme.colorScheme.primary)
}
@Composable fun FavoriteMarkedLabel(name:String,accent:String="",iconKey:String="",favorite:Boolean=false,modifier:Modifier=Modifier,small:Boolean=false){
 Row(modifier.favoriteSurface(favorite),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
  ProvideTextStyle(LocalTextStyle.current.copy(fontWeight=favoriteWeight(favorite))){MarkedLabel(name,accent,iconKey,Modifier.weight(1f,fill=false),small,fontWeight=favoriteWeight(favorite))}
  if(favorite&&favoriteStyle().star){Spacer(Modifier.width(6.dp));FavoriteStar(true,"Избранное: $name")}
 }
}

/** Editing affordance stays visible regardless of the user's display policy for favorite rows. */
@Composable fun FavoriteToggle(checked:Boolean,onChange:(Boolean)->Unit,enabled:Boolean=true){
 FilledTonalIconToggleButton(checked=checked,onCheckedChange=onChange,enabled=enabled,
  modifier=Modifier.testTag("favorite-toggle").then(if(checked)Modifier.border(2.dp,MaterialTheme.colorScheme.primary,CircleShape)else Modifier)){
  Icon(if(checked)Icons.Filled.Star else Icons.Filled.StarBorder,if(checked)"Убрать из избранного"else "Добавить в избранное",
   tint=if(checked)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
 }
}

/** Reserve the filter suffix so a long heading cannot hide the active filter. */
@Composable fun FavoriteListTitle(title:String,filtered:Boolean){
 if(!filtered)Text(title,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
 else Row(Modifier.fillMaxWidth().then(Modifier.clearAndSetSemantics{text=androidx.compose.ui.text.AnnotatedString("$title · избранные")})){
  Text(title,Modifier.weight(1f,fill=false),maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
  Text(" · избранные",style=MaterialTheme.typography.titleMedium,maxLines=1)
 }
}
