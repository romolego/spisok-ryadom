package com.spisokryadom.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.ResetMode
import com.spisokryadom.app.ui.components.*

@Composable fun CreationDefaultsCard(value:AddToListDefaultsState,units:List<UnitEntity>,onEnabled:(Boolean)->Unit,onQuantity:(String)->Unit,onUnit:(String)->Unit,onType:(String)->Unit){
 Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
  OutlinedCard{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   Text("Количество и единица нового товара",style=MaterialTheme.typography.titleMedium)
   SettingSwitch("Подставлять, если не заполнено",value.fillMissing,onEnabled,help="Применяется к пустым полям новых карточек при сохранении. Введённые вручную значения сохраняются. Если выключено, пустые поля карточки остаются пустыми. У конкретной покупки количество всегда должно быть положительным; без ввода это одна покупка.")
   if(value.fillMissing){
    var quantity by rememberSaveable{mutableStateOf(java.math.BigDecimal.valueOf(value.missingQuantity).stripTrailingZeros().toPlainString())};var focused by remember{mutableStateOf(false)}
    LaunchedEffect(value.missingQuantity){if(!focused)quantity=java.math.BigDecimal.valueOf(value.missingQuantity).stripTrailingZeros().toPlainString()}
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
     OutlinedTextField(quantity,{quantity=it;onQuantity(it)},label={Text("Количество")},singleLine=true,isError=com.spisokryadom.app.util.Quantities.parse(quantity)==null,keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Decimal),modifier=Modifier.weight(1f).onFocusChanged{focused=it.isFocused})
     DropdownSelector("Единица",(units.map{com.spisokryadom.app.util.Quantities.unit(it.name)}+value.missingUnit).filter{it.isNotBlank()}.distinct(),value.missingUnit.ifBlank{null},{it},{onUnit(it.orEmpty())},modifier=Modifier.weight(1f),noneLabel="Не подставлять")
    }
   }
  }}
  OutlinedCard{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   Text("Тип новой покупки",style=MaterialTheme.typography.titleMedium)
   DropdownSelector("Тип покупки по умолчанию",listOf("offline","online"),value.defaultPurchaseType,{if(it=="online")"Онлайн"else "Офлайн"},{it?.let(onType)},allowNone=false,itemVector={if(it=="online")Icons.Filled.Language else Icons.Filled.Store})
  }}
 }
}

@Composable fun ResetSettingsCard(busy:Boolean,onReset:(ResetMode)->Unit){
 var open by rememberSaveable{mutableStateOf(false)};var selected by remember{mutableStateOf<ResetMode?>(null)}
 OutlinedCard(onClick={if(!busy)open=true},modifier=Modifier.fillMaxWidth()){
  ListItem(headlineContent={Text("Сброс")},leadingContent={Icon(Icons.Filled.RestartAlt,null)},trailingContent={Icon(Icons.Filled.ChevronRight,"Открыть сброс")})
 }
 if(open)EditorSheet("Сброс",{open=false},busy=busy,actions={TextButton(onClick={open=false},enabled=!busy){Text("Закрыть")}}){
  ResetMode.entries.forEach{mode->TextButton(onClick={selected=mode;open=false},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(mode.title,Modifier.fillMaxWidth())}}
 }
 selected?.let{mode->ConfirmDialog(mode.title+"?",mode.explanation,onConfirm={selected=null;onReset(mode)},onDismiss={selected=null})}
}

@Composable fun ProductUsageDialog(title:String,products:List<ProductEntity>,onOpen:(Long)->Unit,onDismiss:()->Unit){
 EditorSheet(title,onDismiss,scrollContent=false,actions={TextButton(onClick=onDismiss){Text("Закрыть")}}){LazyColumn(Modifier.testTag("product-usage-list")){
  if(products.isEmpty())item{Text("Пока не используется")}
  items(com.spisokryadom.app.util.alphabetical(products){it.name},key={it.id}){p->FavoriteMarkedLabel(p.name,accent=p.accent,favorite=p.isFavorite,modifier=Modifier.fillMaxWidth().clickable{onDismiss();onOpen(p.id)}.padding(vertical=12.dp))}
 }}
}

@Composable fun DepartmentUsageDialog(groups:List<SettingsViewModel.DepartmentUsage>,onOpen:(Long)->Unit,onShop:(Long)->Unit,onDismiss:()->Unit){
 val labels=LocalPlaceLabels.current
 EditorSheet("Товары и магазины группы",onDismiss,scrollContent=false,actions={TextButton(onClick=onDismiss){Text("Закрыть")}}){LazyColumn{
  if(groups.isEmpty())item{Text("Пока не используется")}
  groups.forEach{group->
   item(key="shop_${group.shop.id}"){Text(labels.shop(group.shop.id,group.shop.name),style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary,modifier=Modifier.fillMaxWidth().clickable{onDismiss();onShop(group.shop.id)}.padding(vertical=12.dp))}
   if(group.products.isEmpty())item(key="empty_${group.shop.id}"){Text("Нет привязанных товаров",style=MaterialTheme.typography.bodySmall)}
   items(group.products,key={"${group.shop.id}_${it.id}"}){p->FavoriteMarkedLabel(p.name,accent=p.accent,favorite=p.isFavorite,modifier=Modifier.fillMaxWidth().clickable{onDismiss();onOpen(p.id)}.padding(start=12.dp,top=10.dp,bottom=10.dp))}
  }
 }}
}
