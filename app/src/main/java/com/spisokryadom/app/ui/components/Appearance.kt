package com.spisokryadom.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

// Google Material Icons, Apache 2.0; bundled vectors, available without network.
private val symbols=listOf(
 "milk" to ("Молочка" to Icons.Filled.LocalDrink),"eggs" to ("Яйца" to Icons.Filled.Egg),
 "bread" to ("Выпечка" to Icons.Filled.BakeryDining),"meat" to ("Мясо" to Icons.Filled.KebabDining),
 "fish" to ("Рыба" to Icons.Filled.SetMeal),"vegetables" to ("Овощи и фрукты" to Icons.Filled.Eco),
 "grains" to ("Крупы" to Icons.Filled.Grain),"pasta" to ("Макароны" to Icons.Filled.RamenDining),
 "ready" to ("Готовая еда" to Icons.Filled.Restaurant),"frozen" to ("Заморозка" to Icons.Filled.AcUnit),
 "sweets" to ("Сладости" to Icons.Filled.Cake),"tea" to ("Чай и кофе" to Icons.Filled.Coffee),
 "water" to ("Напитки" to Icons.Filled.WaterDrop),"groceries" to ("Продукты" to Icons.Filled.ShoppingBasket),
 "cleaning" to ("Бытовая химия" to Icons.Filled.CleaningServices),"hygiene" to ("Гигиена" to Icons.Filled.Soap),
 "medicine" to ("Аптека" to Icons.Filled.Medication),"eyes" to ("Линзы" to Icons.Filled.Visibility),
 "pets" to ("Для животных" to Icons.Filled.Pets),"car" to ("Автомобильное" to Icons.Filled.DirectionsCar),
 "clothes" to ("Одежда" to Icons.Filled.Checkroom),"tech" to ("Электроника" to Icons.Filled.Devices),
 "home" to ("Дом" to Icons.Filled.Home),"tools" to ("Инструменты" to Icons.Filled.Build),
 "person" to ("Человек" to Icons.Filled.Person),"gift" to ("Подарки" to Icons.Filled.CardGiftcard),
 "bag" to ("Покупки" to Icons.Filled.ShoppingBag),"label" to ("Группа" to Icons.Filled.Label),
 "canned" to ("Консервы и банки" to ShoppingSymbols.Canned),
 "nuts" to ("Орехи" to ShoppingSymbols.Nuts),
 "cheese" to ("Сыры" to ShoppingSymbols.Cheese),
 "bottle" to ("Соки и газировка" to ShoppingSymbols.Bottle),
 "fruit" to ("Фрукты" to ShoppingSymbols.Fruit),
 "herbs" to ("Зелень и специи" to Icons.Filled.Spa),
 "icecream" to ("Мороженое" to Icons.Filled.Icecream),
 "snacks" to ("Снеки" to Icons.Filled.Fastfood),
 "soup" to ("Супы" to Icons.Filled.SoupKitchen),
 "pizza" to ("Пицца" to Icons.Filled.LocalPizza),
 "alcohol" to ("Алкоголь" to Icons.Filled.Liquor),
 "beer" to ("Пиво" to Icons.Filled.SportsBar),
 "baby" to ("Детские товары" to Icons.Filled.ChildCare),
 "cosmetics" to ("Косметика" to Icons.Filled.Face),
 "firstaid" to ("Медицинские изделия" to Icons.Filled.MedicalServices),
 "vitamins" to ("Здоровье" to Icons.Filled.HealthAndSafety),
 "dental" to ("Уход за зубами" to ShoppingSymbols.Tooth),
 "paper" to ("Бумага и салфетки" to Icons.Filled.ReceiptLong),
 "laundry" to ("Стирка" to Icons.Filled.LocalLaundryService),
 "kitchen" to ("Кухня" to Icons.Filled.Kitchen),
 "bath" to ("Ванная" to Icons.Filled.Bathtub),
 "hair" to ("Уход за волосами" to Icons.Filled.ContentCut),
 "packaging" to ("Упаковки" to Icons.Filled.Inventory2),
 "stationery" to ("Канцелярия" to Icons.Filled.EditNote),
 "garden" to ("Сад и цветы" to Icons.Filled.Yard),
 "sport" to ("Спорт" to Icons.Filled.FitnessCenter),
 "travel" to ("В дорогу" to Icons.Filled.Luggage),
 "lighting" to ("Лампы" to Icons.Filled.Lightbulb),
 "batteries" to ("Батарейки" to Icons.Filled.BatteryFull),
 "cables" to ("Кабели" to Icons.Filled.Cable),
 "power" to ("Электропитание" to Icons.Filled.Power),
 "tableware" to ("Посуда и столовые приборы" to Icons.Filled.DinnerDining),
 "man" to ("Мужчина" to Icons.Filled.Man),"woman" to ("Женщина" to Icons.Filled.Woman),
 "person2" to ("Человек: вариант 2" to Icons.Filled.Person2),"person3" to ("Человек: вариант 3" to Icons.Filled.Person3),
 "person4" to ("Человек: вариант 4" to Icons.Filled.Person4),"child" to ("Ребёнок" to Icons.Filled.ChildFriendly),
 "elderly" to ("Пожилой человек" to Icons.Filled.Elderly),"family" to ("Семья" to Icons.Filled.FamilyRestroom),
 "couple" to ("Пара" to Icons.Filled.People),"shared" to ("Общее / все" to Icons.Filled.Groups),
 "work" to ("Коллеги / работа" to Icons.Filled.Work),"school" to ("Учёба" to Icons.Filled.School),
 "dog" to ("Собака" to ShoppingSymbols.Dog),"cat" to ("Кошка" to ShoppingSymbols.Cat),
 "toys" to ("Игрушки" to Icons.Filled.Toys),"crafts" to ("Творчество" to Icons.Filled.Brush),
 "furniture" to ("Мебель" to Icons.Filled.Chair),"textile" to ("Домашний текстиль" to Icons.Filled.Bed),
 "other" to ("Разное" to Icons.Filled.Category))
fun appearanceIcon(key:String):ImageVector?=symbols.find{it.first==key}?.second?.second
fun appearanceOptions():List<Pair<String,String>> = symbols.map{it.first to it.second.first}
fun accentColor(key:String):Color?=com.spisokryadom.app.util.AppearanceColors.argb(key)?.let{Color(it)}
fun colorHex(color:Color):String="#%06X".format(java.util.Locale.ROOT,color.toArgb() and 0xFFFFFF)
@Composable fun MarkedLabel(name:String,accent:String="",iconKey:String="",modifier:Modifier=Modifier,small:Boolean=false,fontWeight:androidx.compose.ui.text.font.FontWeight?=null){
 Row(modifier,verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
  appearanceIcon(iconKey)?.let{Icon(it,null,Modifier.size(if(small)18.dp else 22.dp),tint=accentColor(accent)?:MaterialTheme.colorScheme.primary)}
  Column {Text(name,fontWeight=fontWeight,style=if(small)MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge);accentColor(accent)?.let{Spacer(Modifier.height(3.dp));Box(Modifier.width(36.dp).height(2.dp).background(it))}}
 }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AppearanceSheet(title:String,accent:String,iconKey:String="",withIcons:Boolean=true,onSave:(String,String)->Unit,onDismiss:()->Unit,preview:(@Composable (String,String)->Unit)?=null,withColors:Boolean=true,noneIconLabel:String="Без значка"){
 var color by remember{mutableStateOf(accent)};var icon by remember{mutableStateOf(iconKey)}
 var custom by remember{mutableStateOf(false)};var hex by remember{mutableStateOf(colorHex(accentColor(accent)?:Color(0xFF4B81A5)))}
 var hue by remember{mutableStateOf(0f)};var saturation by remember{mutableStateOf(1f)};var brightness by remember{mutableStateOf(1f)}
 var query by remember{mutableStateOf("")}
 fun channels(value:Color){val hsv=FloatArray(3);android.graphics.Color.colorToHSV(value.toArgb(),hsv);hue=hsv[0];saturation=hsv[1];brightness=hsv[2]}
 fun select(key:String){color=key;accentColor(key)?.let{hex=colorHex(it);channels(it)}}
 fun slide(h:Float=hue,s:Float=saturation,v:Float=brightness){hue=h;saturation=s;brightness=v;color=colorHex(Color.hsv(h.coerceAtMost(359.999f),s,v));hex=color}
 val valid=!custom||accentColor(hex)!=null
 val filtered=remember(query){if(query.isBlank())symbols else com.spisokryadom.app.util.FuzzySearch.rank(symbols,query,{it.second.first})}
 EditorSheet(title,onDismiss,scrollContent=false,actions={
  Button(onClick={onSave(color,icon)},enabled=valid,modifier=Modifier.fillMaxWidth()){Text("Применить оформление")}
 }){
   LazyColumn(Modifier.testTag("appearance-options").fillMaxWidth()){
    preview?.let{item{it(color,icon)}}
    if(withColors){
    if(!custom||preview==null)item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
     listOf("" to "Без цвета","green" to "Зелёный","yellow" to "Жёлтый","blue" to "Синий","rose" to "Розовый","violet" to "Фиолетовый").forEach{(key,name)->
      IconButton(onClick={select(key);if(key.isBlank())custom=false}){Icon(if(key==color)Icons.Filled.CheckCircle else Icons.Filled.Circle,name,tint=accentColor(key)?:MaterialTheme.colorScheme.outline)}
     }
    }}
    item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
     Box(Modifier.testTag("appearance-color-preview").size(28.dp).background(accentColor(color)?:MaterialTheme.colorScheme.surfaceVariant,androidx.compose.foundation.shape.CircleShape).border(1.dp,MaterialTheme.colorScheme.outline,androidx.compose.foundation.shape.CircleShape))
     Text(if(color.startsWith("#"))color.uppercase()else "",Modifier.weight(1f).padding(start=10.dp),style=MaterialTheme.typography.labelLarge)
     TextButton(modifier=Modifier.testTag("appearance-custom-toggle"),enabled=valid,onClick={custom=!custom;if(custom){val c=accentColor(color)?:Color(0xFF4B81A5);hex=colorHex(c);channels(c)}}){Text(if(custom)"Палитра"else "Свой цвет")}
    }}
    if(custom){
     item{OutlinedTextField(hex,{raw->hex=raw.trim();accentColor(hex)?.let{color=colorHex(it);channels(it)}},label={Text("HEX")},singleLine=true,isError=accentColor(hex)==null,modifier=Modifier.testTag("appearance-hex").fillMaxWidth())}
     item{ColorChannel("Тон",hue,0f..360f,"appearance-hue",listOf(Color.Red,Color.Yellow,Color.Green,Color.Cyan,Color.Blue,Color.Magenta,Color.Red)){slide(h=it)}}
     item{ColorField(hue,saturation,brightness){s,v->slide(s=s,v=v)}}
    }
    }
    if(withIcons){
     item{OutlinedTextField(query,{query=it},label={Text("Найти значок")},singleLine=true,modifier=Modifier.testTag("appearance-icon-search").fillMaxWidth())}
     item{ListItem(headlineContent={Text(noneIconLabel)},leadingContent={RadioButton(icon.isBlank(),null)},modifier=Modifier.clickable{icon=""})}
     items(filtered.chunked(2)){pair->Row(Modifier.fillMaxWidth()){pair.forEach{(key,value)->
      ListItem(headlineContent={Text(value.first,style=MaterialTheme.typography.bodySmall)},leadingContent={Icon(value.second,null,tint=if(key==icon)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)},modifier=Modifier.testTag("appearance-icon-$key").weight(1f).clickable{icon=key},colors=ListItemDefaults.colors(containerColor=if(key==icon)MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface))
     }}}
    }
   }
 }
}
@Composable private fun ColorChannel(label:String,value:Float,range:ClosedFloatingPointRange<Float>,tag:String,gradient:List<Color>,onChange:(Float)->Unit){
 Column{Text(label,style=MaterialTheme.typography.labelMedium)
  Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){
   Box(Modifier.fillMaxWidth().height(12.dp).background(Brush.horizontalGradient(gradient),RoundedCornerShape(50)).border(1.dp,MaterialTheme.colorScheme.outlineVariant,RoundedCornerShape(50)))
   Slider(value,onChange,valueRange=range,modifier=Modifier.testTag(tag).fillMaxWidth(),colors=SliderDefaults.colors(thumbColor=MaterialTheme.colorScheme.primary,activeTrackColor=Color.Transparent,inactiveTrackColor=Color.Transparent,activeTickColor=Color.Transparent,inactiveTickColor=Color.Transparent))
  }
 }
}
