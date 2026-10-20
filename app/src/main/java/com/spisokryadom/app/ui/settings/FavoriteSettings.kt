package com.spisokryadom.app.ui.settings

import androidx.compose.foundation.layout.*
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
 val prefs=androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("shopping_list_prefs",0)
 val value=LocalFavoriteAppearance.current
 fun update(scope:String,change:(FavoriteStyle)->FavoriteStyle){
  val current=com.spisokryadom.app.util.FavoriteAppearance.read(prefs)
  (if(scope=="block")current.copy(block=change(current.block))else current.copy(row=change(current.row))).save(prefs)
 }
 var colorScope by remember{mutableStateOf<String?>(null)}
 colorScope?.let{scope->val s=if(scope=="block")value.block else value.row
  AppearanceSheet("Фон избранного",s.color,withIcons=false,onSave={color,_->
   update(scope){it.copy(background=color.isNotBlank(),color=color.ifBlank{it.color})}
   colorScope=null
  },onDismiss={colorScope=null},preview={color,_->Surface(color=shoppingBlockColor(color,MaterialTheme.colorScheme.surface)?:MaterialTheme.colorScheme.surface,shape=MaterialTheme.shapes.medium){Column(Modifier.fillMaxWidth().padding(12.dp)){Text("Избранные");MarkedLabel("Банан",fontWeight=if(s.bold)androidx.compose.ui.text.font.FontWeight.Bold else null)}}})
 }
 OutlinedCard{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){Text("Оформление избранного",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);HelpButton("Оформление избранного","Верхний блок и избранные позиции в полном списке настраиваются независимо. Изменение оформления не меняет состав и порядок товаров или групп.")}
  listOf("block" to "Блок избранного","row" to "В полном списке").forEach{(scope,title)->
   val s=if(scope=="block")value.block else value.row
   Text(title,style=MaterialTheme.typography.titleSmall)
   SettingSwitch("Выделять фоном",s.background,{checked->update(scope){it.copy(background=checked)}})
   if(s.background)TextButton(onClick={colorScope=scope}){Text("Цвет фона: $title")}
   SettingSwitch("Показывать звёздочку",s.star,{checked->update(scope){it.copy(star=checked)}})
   SettingSwitch("Жирное название",s.bold,{checked->update(scope){it.copy(bold=checked)}})
   if(scope=="block")FavoriteBlock(Modifier.fillMaxWidth()){FavoriteMarkedLabel("Банан",favorite=true,modifier=Modifier.padding(12.dp))}
   else FavoriteMarkedLabel("Банан",favorite=true,modifier=Modifier.fillMaxWidth().padding(12.dp))
  }
 }}
}
