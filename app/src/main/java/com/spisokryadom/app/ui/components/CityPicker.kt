package com.spisokryadom.app.ui.components
import androidx.compose.foundation.layout.*
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
 TextButton(onClick={show=true},enabled=enabled){Text("Город: ${city.name}",maxLines=1)}
 if(show)AlertDialog(onDismissRequest={show=false},title={Text("Город покупок")},text={Column{
  Text("Товары общие. Магазины и новые покупки подбираются для выбранного города.")
  error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
  LazyColumn(Modifier.heightIn(max=320.dp)){items(cities,key={it.id}){item->
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
    TextButton(onClick={repo.select(item.id);show=false}){Text((if(item.id==city.id)"✓ " else "")+item.name)}
    TextButton(onClick={rename=item}){Text("Изменить")}
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
 ExposedDropdownMenuBox(expanded=expanded,onExpandedChange={expanded=it}){
  OutlinedTextField(value=cities.find{it.id==selectedId}?.name?:"Выберите город",onValueChange={},readOnly=true,label={Text("Город магазина")},modifier=Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded)})
  ExposedDropdownMenu(expanded,onDismissRequest={expanded=false}){cities.forEach{c->DropdownMenuItem(text={Text(c.name)},onClick={onSelected(c.id);expanded=false})}}
 }
}
