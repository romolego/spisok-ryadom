package com.spisokryadom.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.spisokryadom.app.ui.components.*
import com.spisokryadom.app.util.FavoriteStyle

@Composable fun HelpButton(title:String,message:String){
 var show by remember{mutableStateOf(false)}
 IconButton(onClick={show=true}){Icon(Icons.Outlined.HelpOutline,"Подсказка: $title")}
 if(show)AlertDialog(onDismissRequest={show=false},title={Text(title)},text={Text(message)},confirmButton={TextButton(onClick={show=false}){Text("Понятно")}})
}
@Composable fun SettingWithHelp(title:String,message:String,content:@Composable ()->Unit){
 Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.weight(1f)){content()};HelpButton(title,message)}
}
@Composable fun FavoriteSettingsCard(){
 var open by androidx.compose.runtime.saveable.rememberSaveable{mutableStateOf(false)}
 OutlinedCard(onClick={open=true},modifier=Modifier.fillMaxWidth()){
  ListItem(headlineContent={Text("Оформление избранного")},leadingContent={Icon(androidx.compose.material.icons.Icons.Filled.Palette,null)},trailingContent={Icon(androidx.compose.material.icons.Icons.Filled.ChevronRight,"Открыть оформление избранного")})
 }
 if(open)FavoriteSettingsDialog{open=false}
}

@Composable fun FavoriteSettingsDialog(onDismiss:()->Unit){
 val prefs=androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("shopping_list_prefs",0)
 val value=LocalFavoriteAppearance.current
 var selected by androidx.compose.runtime.saveable.rememberSaveable{mutableStateOf(com.spisokryadom.app.util.FavoriteSurface.PURCHASES.name)}
 val surface=com.spisokryadom.app.util.FavoriteSurface.valueOf(selected)
 fun update(pinned:Boolean,change:(FavoriteStyle)->FavoriteStyle){val current=com.spisokryadom.app.util.FavoriteAppearance.read(prefs);current.change(surface,pinned,change(current.style(surface,pinned))).save(prefs)}
 var colorPinned by remember{mutableStateOf<Boolean?>(null)}
 colorPinned?.let{pinned->val style=value.style(surface,pinned)
  AppearanceSheet("Фон избранного",style.color,withIcons=false,onSave={color,_->update(pinned){it.copy(background=color.isNotBlank(),color=color.ifBlank{it.color})};colorPinned=null},onDismiss={colorPinned=null})
 }
 EditorSheet("Оформление избранного",onDismiss,actions={TextButton(onClick=onDismiss){Text("Готово")}}){
   SettingWithHelp("Оформление избранного","Выберите контекст. Верхний блок избранного и его повторения в полном перечне имеют независимые фон, цвет, звёздочку и жирность. «Покупки» — главный экран; «Списки» — целевые списки, каталог и подбор товаров."){
   DropdownSelector("Где отображать",com.spisokryadom.app.util.FavoriteSurface.entries.toList(),surface,{it.title},{it?.let{v->selected=v.name}},allowNone=false)
   }
   listOf(true,false).forEach{pinned->
    val s=value.style(surface,pinned);val title=if(pinned)"Блок избранного"else "В полном списке";val key=surface.title+": "+title
    Text(title,style=MaterialTheme.typography.titleSmall)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){
     IconToggleButton(checked=s.background,onCheckedChange={v->update(pinned){it.copy(background=v)}}){Icon(Icons.Filled.FormatColorFill,"Фон: $key")}
     IconButton(onClick={colorPinned=pinned}){Icon(Icons.Filled.Palette,"Цвет фона: $key",tint=accentColor(s.color)?:MaterialTheme.colorScheme.primary)}
     IconToggleButton(checked=s.star,onCheckedChange={v->update(pinned){it.copy(star=v)}}){Icon(Icons.Filled.Star,"Звёздочка: $key")}
     IconToggleButton(checked=s.bold,onCheckedChange={v->update(pinned){it.copy(bold=v)}}){Icon(Icons.Filled.FormatBold,"Жирное название: $key")}
    }
    CompositionLocalProvider(LocalFavoriteSurface provides surface,LocalFavoriteBlock provides pinned){FavoriteMarkedLabel("Банан",favorite=true,modifier=Modifier.fillMaxWidth().padding(12.dp))}
   }
 }
}
