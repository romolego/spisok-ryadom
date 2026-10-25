package com.spisokryadom.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.spisokryadom.app.SpisokRyadomApp
import com.spisokryadom.app.data.entity.ShoppingListEntryEntity
import com.spisokryadom.app.data.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun HasProductVariants(productId:Long,content:@Composable ()->Unit){
 val app=LocalContext.current.applicationContext as SpisokRyadomApp
 val metadata by app.container.productRepository.variants.observeMetadata().collectAsStateWithLifecycle(initialValue=emptyList<com.spisokryadom.app.data.entity.ProductVariantEntity>() to emptyList<com.spisokryadom.app.data.entity.ProductVariantCityEntity>())
 val family=metadata.first.find{it.productId==productId}?.familyId
 if(family!=null&&metadata.first.count{it.familyId==family}>1)content()
}

@Composable
fun PurchaseVariantSheet(entry:ShoppingListEntryEntity,onDismiss:()->Unit){
 val app=LocalContext.current.applicationContext as SpisokRyadomApp
 val generation=remember{ProductEditorTransactions.generation.get()};val scope=rememberCoroutineScope()
 val source=remember(entry.productId){flow{
  emitAll(app.container.productRepository.variants.observeOptions(entry.productId,app.container.locations.currentId())
   .map{Result.success(it.filter{option->option.enabled})})
 }.catch{if(it is CancellationException)throw it;emit(Result.failure(it))}.flowOn(Dispatchers.IO)}
 val loaded by source.collectAsStateWithLifecycle(initialValue=null)
 val options=loaded?.getOrNull().orEmpty()
 var selected by remember{mutableStateOf(entry.productId)};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}

 EditorSheet("Вариант покупки",onDismiss,busy=busy,actions={
  TextButton(onClick=onDismiss,enabled=!busy){Text("Отмена")}
  Button(onClick={busy=true;scope.launch{try{
   ProductEditorTransactions.inGeneration(generation){app.container.shoppingListRepository.replaceVariant(entry.id,selected,entry.updatedAt,entry.listId,app.getSharedPreferences("shopping_list_prefs",0).getBoolean("remember_variant_choice",false))}
   onDismiss()
  }catch(e:Exception){if(e is CancellationException)throw e;error=e.message}finally{busy=false}}},enabled=!busy&&options.any{it.product.id==selected}){Text("Применить")}
 }){
  if(options.isNotEmpty())DropdownSelector("Вариант",options,options.find{it.product.id==selected},{it.label},{it?.let{v->selected=v.product.id}},allowNone=false,itemEnabled={!busy})
  if(loaded==null)LinearProgressIndicator(Modifier.fillMaxWidth())
  else if(options.isEmpty()&&loaded?.exceptionOrNull()==null)Text("Нет доступных вариантов в этом городе")
  loaded?.exceptionOrNull()?.let{Text(it.message?:"Не удалось загрузить варианты",color=MaterialTheme.colorScheme.error)}
  error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
 }
}
