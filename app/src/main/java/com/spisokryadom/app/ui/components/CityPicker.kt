package com.spisokryadom.app.ui.components
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
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
 val repo=(LocalContext.current.applicationContext as SpisokRyadomApp).container.locations
 val city by repo.activeCity.collectAsStateWithLifecycle();val cities by repo.cities.collectAsStateWithLifecycle()
 val scope=rememberCoroutineScope();var show by rememberSaveable{mutableStateOf(false)}
 var add by rememberSaveable{mutableStateOf(false)};var rename by remember{mutableStateOf<CityEntity?>(null)};var error by remember{mutableStateOf<String?>(null)}
 OutlinedCard(Modifier.fillMaxWidth().clickable(enabled=enabled){show=true}.semantics{contentDescription="Изменить город"},colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface)) {
  Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
   Icon(Icons.Filled.LocationOn,null,tint=MaterialTheme.colorScheme.primary)
   Column(Modifier.weight(1f)) {
    Text("Город покупок",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Text(city.name,style=MaterialTheme.typography.titleMedium)
   }
   Icon(Icons.Filled.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
  }
 }
 if(show)AlertDialog(onDismissRequest={show=false},title={Text("Город покупок")},text={Column{
  error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
  LazyColumn(Modifier.heightIn(max=320.dp)){items(cities,key={it.id}){item->
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
    Row(Modifier.weight(1f).clickable{repo.select(item.id);show=false}.padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
     RadioButton(item.id==city.id,onClick={repo.select(item.id);show=false})
     Text(item.name,Modifier.weight(1f).padding(start=8.dp))
    }
    IconButton(onClick={rename=item}){Icon(Icons.Filled.Edit,"Изменить ${item.name}")}
   }
  }}
  TextButton(onClick={add=true}){Text("Добавить город")}
 }},confirmButton={TextButton(onClick={show=false}){Text("Готово")}})
 if(add)InputDialog(title="Новый город",label="Название",onConfirm={name->scope.launch{try{repo.add(name);add=false;show=false}catch(e:Exception){error=e.message;add=false}}},onDismiss={add=false})
 rename?.let{item->InputDialog(title="Название города",label="Город",initialValue=item.name,onConfirm={name->scope.launch{try{repo.rename(item,name);rename=null}catch(e:Exception){error=e.message;rename=null}}},onDismiss={rename=null})}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityField(selectedId:Long,onSelected:(Long)->Unit){
 val locations=(LocalContext.current.applicationContext as SpisokRyadomApp).container.locations
 val cities by locations.cities.collectAsStateWithLifecycle();var expanded by remember{mutableStateOf(false)}
 var creating by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)};val scope=rememberCoroutineScope()
 Column{
  ExposedDropdownMenuBox(expanded=expanded,onExpandedChange={expanded=it}){
   OutlinedTextField(value=cities.find{it.id==selectedId}?.name?:"Текущий город",onValueChange={},readOnly=true,label={Text("Город магазина")},modifier=Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded)})
   ExposedDropdownMenu(expanded,onDismissRequest={expanded=false}){
    cities.forEach{c->DropdownMenuItem(text={Text(c.name)},onClick={onSelected(c.id);expanded=false})}
    DropdownMenuItem(text={Text("Текущий город")},onClick={onSelected(locations.activeCity.value.id);expanded=false})
    DropdownMenuItem(text={Text("Новый город…")},onClick={expanded=false;creating=true})
   }
  }
  if(creating)InlineCreateValue("Название города",{name,done->scope.launch{try{val id=locations.add(name);onSelected(id);creating=false}catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;error=e.message}finally{done()}}},{creating=false})
  error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
 }
}
