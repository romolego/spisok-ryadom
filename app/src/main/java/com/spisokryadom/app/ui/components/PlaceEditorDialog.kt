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

@Composable fun VenueEditorDialog(venue:VenueEntity,onDismiss:()->Unit,onSave:suspend(VenueEntity)->Unit,onDelete:(()->Unit)?=null){
 var name by rememberSaveable(venue.id){mutableStateOf(venue.name)}
 var address by rememberSaveable(venue.id){mutableStateOf(venue.address.orEmpty())}
 var note by rememberSaveable(venue.id){mutableStateOf(venue.note.orEmpty())}
 var photo by rememberSaveable(venue.id){mutableStateOf(venue.photoUri)}
 var photoBusy by remember{mutableStateOf(false)};var saving by remember{mutableStateOf(false)}
 var error by remember{mutableStateOf<String?>(null)};var deleting by remember{mutableStateOf(false)}
 val scope=rememberCoroutineScope()
 if(deleting)ConfirmDialog("Удалить рынок или ТЦ?","Магазины, точки и покупки сохранятся. Точки получат текущий адрес рынка или ТЦ.",onConfirm={deleting=false;onDelete?.invoke()},onDismiss={deleting=false})
 PlaceEditorDialog(if(venue.id>0)"Рынок или ТЦ"else "Новый рынок или ТЦ",onDismiss,{
  saving=true;error=null;scope.launch{
   try{onSave(venue.copy(name=name.trim(),address=address.trim().ifBlank{null},note=note.trim().ifBlank{null},photoUri=photo));onDismiss()}
   catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;error=e.message?:"Не удалось сохранить"}
   finally{saving=false}
  }
 },canSave=name.isNotBlank(),busy=photoBusy||saving){
  OutlinedTextField(name,{name=it},label={Text("Название")},modifier=Modifier.fillMaxWidth().testTag("venue-name"))
  OutlinedTextField(address,{address=it},label={Text("Общий адрес")},modifier=Modifier.fillMaxWidth().testTag("venue-address"))
  Text("Один адрес для всех магазинов и павильонов внутри. Изменение применяется ко всем точкам этого рынка или ТЦ.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
  OutlinedTextField(note,{note=it},label={Text("Заметка")},maxLines=3,modifier=Modifier.fillMaxWidth())
  BranchPhotoEditor(photo.orEmpty(),{photo=it},{photoBusy=it},title="Рынок или ТЦ",directory="venue_photos",compact=true,showTitle=false,enabled=!saving)
  error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
  if(onDelete!=null)TextButton(onClick={deleting=true},enabled=!photoBusy&&!saving){Text("Удалить рынок или ТЦ",color=MaterialTheme.colorScheme.error)}
 }
}
