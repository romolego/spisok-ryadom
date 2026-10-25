package com.spisokryadom.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.spisokryadom.app.data.entity.VenueEntity
import com.spisokryadom.app.data.entity.VenueKinds
import kotlinx.coroutines.launch

/** The same fixed top actions for outlets and venues; only the body scrolls, including with IME. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PlaceEditorDialog(title:String,onDismiss:()->Unit,onSave:()->Unit,canSave:Boolean=true,busy:Boolean=false,content:@Composable ColumnScope.()->Unit){
 Dialog(onDismissRequest={if(!busy)onDismiss()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){
  Scaffold(modifier=Modifier.fillMaxSize().testTag("place-editor"),topBar={TopAppBar(title={Text(title,maxLines=1)},navigationIcon={IconButton(onClick=onDismiss,enabled=!busy){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Назад")}},actions={TextButton(onClick=onSave,enabled=canSave&&!busy){Text("Сохранить")}})}){padding->
   Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
  }
 }
}

@Composable fun VenueEditorDialog(venue:VenueEntity,onDismiss:()->Unit,onSave:suspend(VenueEntity)->Unit,onDelete:(()->Unit)?=null,allowCityChange:Boolean=true){
 var cityId by rememberSaveable(venue.id){mutableStateOf(venue.cityId)}
 var kind by rememberSaveable(venue.id){mutableStateOf(if(venue.id<=0)VenueKinds.MARKET else venue.kind)}
 var cityConfirm by remember{mutableStateOf(false)}
 var name by rememberSaveable(venue.id){mutableStateOf(venue.name)}
 var address by rememberSaveable(venue.id){mutableStateOf(venue.address.orEmpty())}
 var note by rememberSaveable(venue.id){mutableStateOf(venue.note.orEmpty())}
 var photo by rememberSaveable(venue.id){mutableStateOf(venue.photoUri)}
 var photoBusy by remember{mutableStateOf(false)};var saving by remember{mutableStateOf(false)}
 var error by remember{mutableStateOf<String?>(null)};var deleting by remember{mutableStateOf(false)}
 val scope=rememberCoroutineScope()
 if(deleting)ConfirmDialog("Удалить торговое место?","Магазины, точки и покупки сохранятся. Точки получат текущий общий адрес.",onConfirm={deleting=false;onDelete?.invoke()},onDismiss={deleting=false})
 fun save(){
  saving=true;error=null;scope.launch{
   try{onSave(venue.copy(cityId=cityId,kind=kind,name=name.trim(),address=address.trim().ifBlank{null},note=note.trim().ifBlank{null},photoUri=photo));onDismiss()}
   catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;error=e.message?:"Не удалось сохранить"}
   finally{saving=false}
  }
 }
 if(cityConfirm)ConfirmDialog("Изменить город места?","Все точки внутри переедут вместе с ним. Другие адреса сетевых магазинов останутся в прежнем городе. Товары и покупки сохранятся.",onConfirm={cityConfirm=false;save()},onDismiss={cityConfirm=false})
 PlaceEditorDialog(if(venue.id>0)VenueKinds.label(kind)else "Новое торговое место",onDismiss,{
  if(venue.id>0&&cityId!=venue.cityId)cityConfirm=true else save()
 },canSave=name.isNotBlank(),busy=photoBusy||saving){
  DropdownSelector("Тип места",VenueKinds.choices,kind,VenueKinds::label,{it?.let{kind=it}},allowNone=false)
  if(allowCityChange)CityField(cityId,{cityId=it},label="Город места")
  else {
   val locations=(androidx.compose.ui.platform.LocalContext.current.applicationContext as com.spisokryadom.app.SpisokRyadomApp).container.locations
   val cities by locations.cities.collectAsState()
   AppTextField(cities.find{it.id==cityId}?.name.orEmpty(),{},readOnly=true,label={Text("Город места")},modifier=Modifier.fillMaxWidth())
  }
  AppTextField(name,{name=it},label={Text("Название")},modifier=Modifier.fillMaxWidth().testTag("venue-name"))
  AppTextField(address,{address=it},label={Text("Общий адрес")},modifier=Modifier.fillMaxWidth().testTag("venue-address"))
  AppTextField(note,{note=it},label={Text("Заметка")},maxLines=3,modifier=Modifier.fillMaxWidth())
  BranchPhotoEditor(photo.orEmpty(),{photo=it},{photoBusy=it},title="Рынок или ТЦ",directory="venue_photos",compact=true,showTitle=false,enabled=!saving)
  error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
  if(onDelete!=null)TextButton(onClick={deleting=true},enabled=!photoBusy&&!saving){Text("Удалить торговое место",color=MaterialTheme.colorScheme.error)}
 }
}
