package com.spisokryadom.app.ui.components
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spisokryadom.app.SpisokRyadomApp
import com.spisokryadom.app.data.entity.CityEntity
import kotlinx.coroutines.launch

@Composable
fun CityPicker(enabled:Boolean=true){
 val container=(LocalContext.current.applicationContext as SpisokRyadomApp).container
 val repo=container.locations
 val generation=remember{com.spisokryadom.app.data.repository.ProductEditorTransactions.generation.get()}
 val city by repo.activeCity.collectAsStateWithLifecycle();val cities by repo.cities.collectAsStateWithLifecycle()
 val scope=rememberCoroutineScope();var show by rememberSaveable{mutableStateOf(false)}
 var help by remember{mutableStateOf(false)}
 var busy by remember{mutableStateOf(false)}
 var add by rememberSaveable{mutableStateOf(false)};var rename by remember{mutableStateOf<CityEntity?>(null)};var error by remember{mutableStateOf<String?>(null)}
 fun select(id:Long){if(busy)return;busy=true;scope.launch{try{
  com.spisokryadom.app.data.repository.ProductEditorTransactions.inGeneration(generation){container.shoppingListRepository.changeShoppingCity(id);show=false}
 }catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;error=e.message}finally{busy=false}}}

 OutlinedCard(Modifier.fillMaxWidth().testTag("city-settings-selector").clickable(enabled=enabled){show=true}.semantics{contentDescription="Изменить город"},colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface)) {
  Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
   Icon(Icons.Filled.LocationOn,null,tint=MaterialTheme.colorScheme.primary)
   Column(Modifier.weight(1f)) {
    Text("Город покупок",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Text(city.name,style=MaterialTheme.typography.titleMedium)
   }
   IconButton(onClick={help=true}){Icon(Icons.AutoMirrored.Outlined.HelpOutline,"Подсказка: Город покупок")}
   Icon(Icons.Filled.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
  }
 }
 if(help)AlertDialog(onDismissRequest={help=false},title={Text("Город покупок")},text={Text("Город определяет магазины и адреса, доступные при добавлении товаров. При смене города некупленные регулярные покупки подбирают местные магазины; без подходящей привязки остаются без магазина. Купленное и отдельные списки сохраняют свои места. Адреса других городов и товары сохраняются.")},confirmButton={TextButton(onClick={help=false}){Text("Понятно")}})
 if(show)EditorSheet("Город покупок",{show=false},busy=busy,scrollContent=false,actions={
  TextButton(onClick={add=true},enabled=!busy){Text("Добавить город")}
  TextButton(onClick={show=false},enabled=!busy){Text("Готово")}
 }){
  error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
  LazyColumn(Modifier.weight(1f,false)){items(com.spisokryadom.app.util.alphabetical(cities){it.name}.sortedBy{it.id!=city.id},key={it.id}){item->
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
    Row(Modifier.weight(1f).clickable(enabled=!busy){select(item.id)}.padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
     RadioButton(item.id==city.id,onClick={select(item.id)},enabled=!busy)
     Text(item.name,Modifier.weight(1f).padding(start=8.dp))
    }
    IconButton(onClick={rename=item},enabled=!busy){Icon(Icons.Filled.Edit,"Изменить ${item.name}")}
   }
  }}
 }
 if(add)InputDialog(title="Новый город",label="Название",onConfirm={name->scope.launch{try{com.spisokryadom.app.data.repository.ProductEditorTransactions.inGeneration(generation){val id=repo.add(name,selectAfterCreate=false);container.shoppingListRepository.changeShoppingCity(id);add=false;show=false}}catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;error=e.message;add=false}}},onDismiss={add=false})
 rename?.let{item->InputDialog(title="Название города",label="Город",initialValue=item.name,onConfirm={name->scope.launch{try{com.spisokryadom.app.data.repository.ProductEditorTransactions.inGeneration(generation){repo.rename(item,name);rename=null}}catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;error=e.message;rename=null}}},onDismiss={rename=null})}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityField(selectedId:Long,onSelected:(Long)->Unit,label:String="Город магазина"){
 val locations=(LocalContext.current.applicationContext as SpisokRyadomApp).container.locations
 val generation=remember{com.spisokryadom.app.data.repository.ProductEditorTransactions.generation.get()}
 val activeCity by locations.activeCity.collectAsStateWithLifecycle()
 val cities by locations.cities.collectAsStateWithLifecycle();var expanded by remember{mutableStateOf(false)}
 var creating by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)};val scope=rememberCoroutineScope()
 Column{
  ExposedDropdownMenuBox(expanded=expanded,onExpandedChange={expanded=it}){
   AppTextField(value=cities.find{it.id==selectedId}?.name?:"Выберите город",onValueChange={},readOnly=true,label={Text(label)},modifier=Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded)})
   ExposedDropdownMenu(expanded,onDismissRequest={expanded=false}){
    com.spisokryadom.app.util.alphabetical(cities){it.name}.sortedBy{it.id!=activeCity.id}.forEach{c->
     DropdownMenuItem(text={Column{Text(c.name);if(c.id==activeCity.id)Text("Город покупок",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)}},
      trailingIcon={if(c.id==selectedId)Icon(Icons.Filled.Check,null)},onClick={scope.launch{try{com.spisokryadom.app.data.repository.ProductEditorTransactions.inGeneration(generation){onSelected(c.id);expanded=false}}catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;error=e.message;expanded=false}}})
    }
    DropdownMenuItem(text={Text("Новый город…")},onClick={expanded=false;creating=true})
   }
  }
  if(creating)InlineCreateValue("Название города",{name,done->scope.launch{try{com.spisokryadom.app.data.repository.ProductEditorTransactions.inGeneration(generation){val id=locations.add(name,selectAfterCreate=false);onSelected(id);creating=false}}catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;error=e.message}finally{done()}}},{creating=false})
  error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
 }
}
