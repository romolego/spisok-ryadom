package com.spisokryadom.app.ui.productcard

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spisokryadom.app.ui.components.*
import kotlinx.coroutines.flow.map

fun productVariantSwipe(state:ProductCardUiState,viewModel:ProductCardViewModel):Modifier=if(state.variantOptions.size<2)Modifier else Modifier.pointerInput(state.variantOptions.map{it.product.id},state.activeVariantId,state.isPhotoBusy,state.isSaving){
 var distance=0f
 detectHorizontalDragGestures(onDragStart={distance=0f},onDragEnd={
  if(!state.isPhotoBusy&&!state.isSaving&&kotlin.math.abs(distance)>80f){
   val index=state.variantOptions.indexOfFirst{it.product.id==state.activeVariantId}
   state.variantOptions.getOrNull(index+(if(distance<0)1 else -1))?.let{viewModel.switchVariant(it.product.id)}
  }
 },onHorizontalDrag={change,amount->change.consume();distance+=amount})
}

@Composable
fun ProductVariantsBar(state:ProductCardUiState,viewModel:ProductCardViewModel){
 var create by remember{mutableStateOf(false)};var manage by remember{mutableStateOf(false)}
 val options=state.variantOptions
 val index=options.indexOfFirst{it.product.id==state.activeVariantId}.coerceAtLeast(0)
 val enabled=!state.isSaving&&!state.isPhotoBusy&&!state.isLoading
 Row(Modifier.fillMaxWidth().testTag("product-variants").pointerInput(options,state.activeVariantId,enabled){
  var distance=0f
  detectHorizontalDragGestures(onDragStart={distance=0f},onDragEnd={if(enabled&&kotlin.math.abs(distance)>80f){options.getOrNull(index+(if(distance<0)1 else -1))?.let{viewModel.switchVariant(it.product.id)}}},onHorizontalDrag={change,amount->change.consume();distance+=amount})
 },verticalAlignment=Alignment.CenterVertically){
  if(options.size>1)IconButton(onClick={viewModel.switchVariant(options[index-1].product.id)},enabled=enabled&&index>0){Icon(Icons.Filled.ChevronLeft,"Предыдущий вариант")}
  Column(Modifier.weight(1f)){
   Text(options.getOrNull(index)?.label?:"Варианты товара",style=MaterialTheme.typography.titleSmall)
   if(options.size>1)Text("${index+1} из ${options.size}",style=MaterialTheme.typography.labelSmall)
  }
  if(options.size>1)IconButton(onClick={viewModel.switchVariant(options[index+1].product.id)},enabled=enabled&&index<options.lastIndex){Icon(Icons.Filled.ChevronRight,"Следующий вариант")}
  IconButton(onClick={create=true},enabled=enabled&&state.name.isNotBlank()){Icon(Icons.Filled.Add,"Новый вариант товара")}
  if(options.size>1)IconButton(onClick={manage=true},enabled=enabled){Icon(Icons.Filled.Tune,"Настроить варианты")}
 }
 if(create)InputDialog("Новый вариант",label="Марка или название варианта",onConfirm={viewModel.createVariant(it);create=false},onDismiss={create=false})
 if(manage){
  val cities by viewModel.variantCities.collectAsStateWithLifecycle()
  var city by remember{mutableStateOf(viewModel.variantCityId())}
  val flow=remember(city,state.activeVariantId){viewModel.variantOptionsForCity(city).map{city to it}}
  val loaded by flow.collectAsStateWithLifecycle(initialValue=null)
  val local=loaded?.takeIf{it.first==city}?.second.orEmpty()
  var rows by remember(city){mutableStateOf(emptyList<Pair<Long,Boolean>>())}
  var drafts by remember{mutableStateOf(emptyMap<Long,List<Pair<Long,Boolean>>>())}
  var saving by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
  var rename by remember{mutableStateOf<Long?>(null)}
  var remove by remember{mutableStateOf<Long?>(null)}
  LaunchedEffect(city,local.map{Triple(it.product.id,it.enabled,it.order)}){
   if(local.isNotEmpty()){
    val retained=(rows.ifEmpty{drafts[city].orEmpty()}).filter{row->local.any{it.product.id==row.first}}
    rows=retained+local.filter{v->retained.none{it.first==v.product.id}}.map{it.product.id to it.enabled}
   }
  }
  EditorSheet("Варианты товара",{manage=false},busy=saving,actions={
   TextButton(onClick={manage=false},enabled=!saving){Text("Отмена")}
   Button(onClick={saving=true;error=null;viewModel.configureVariantCities(drafts+(city to rows)){message->saving=false;if(message==null)manage=false else error=message}},enabled=!saving&&loaded?.first==city&&rows.any{it.second}){Text("Применить")}
  }){
   DropdownSelector("Город",cities,cities.find{it.id==city},{it.name},{next->if(next!=null&&!saving){if(loaded?.first==city&&rows.isNotEmpty())drafts=drafts+(city to rows);city=next.id}},allowNone=false,itemEnabled={!saving})
   error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
   rows.forEachIndexed{i,(id,checked)->
    Row(Modifier.fillMaxWidth().testTag("variant-city-row-$id"),verticalAlignment=Alignment.CenterVertically){
     Checkbox(checked,{value->rows=rows.map{if(it.first==id)id to value else it}},enabled=!saving)
     Text(local.find{it.product.id==id}?.label.orEmpty(),Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
     IconButton(onClick={rename=id},enabled=!saving){Icon(Icons.Filled.Edit,"Переименовать вариант")}
     if(id!=local.find{it.product.id==id}?.familyId)IconButton(onClick={remove=id},enabled=!saving){Icon(Icons.Filled.Delete,"Удалить вариант")}
     IconButton(onClick={rows=rows.toMutableList().apply{add(i-1,removeAt(i))}},enabled=!saving&&i>0){Icon(Icons.Filled.ArrowUpward,"Вариант выше")}
     IconButton(onClick={rows=rows.toMutableList().apply{add(i+1,removeAt(i))}},enabled=!saving&&i<rows.lastIndex){Icon(Icons.Filled.ArrowDownward,"Вариант ниже")}
    }
   }
  }
  rename?.let{id->InputDialog("Название варианта",initialValue=local.find{it.product.id==id}?.label.orEmpty(),label="Название",onConfirm={viewModel.renameVariant(id,it);rename=null},onDismiss={rename=null})}
  remove?.let{id->ConfirmDialog("Удалить вариант?","Существующие покупки сохранятся как разовые позиции. Вариант исчезнет из каталога.",{viewModel.removeVariant(id);remove=null},{remove=null})}
 }
}
