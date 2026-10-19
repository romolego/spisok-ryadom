package com.spisokryadom.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spisokryadom.app.SpisokRyadomApp
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/** A move changes ownership of the same rows; it never copies or merges them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveToListSheet(expectedOwners:Map<Long,Long?>,onDone:()->Unit,onDismiss:()->Unit) {
 val app=LocalContext.current.applicationContext as SpisokRyadomApp
 val repo=app.container.goalListRepository
 val generation=remember{com.spisokryadom.app.data.repository.ProductEditorTransactions.generation.get()}
 val lists by repo.observeLists().collectAsStateWithLifecycle(initialValue=emptyList())
 var selected by remember { mutableStateOf<Long?>(null) };var chosen by remember{mutableStateOf(false)}
 var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
 val currentBusy by rememberUpdatedState(busy)
 val sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true,confirmValueChange={it!=SheetValue.Hidden||!currentBusy})
 var creating by remember{mutableStateOf(false)}
 val scope=rememberCoroutineScope()
 val oneOwner=expectedOwners.values.distinct().singleOrNull()
 val allSame=expectedOwners.values.distinct().size==1
 ModalBottomSheet(onDismissRequest={if(!busy)onDismiss()},sheetState=sheetState) {
  androidx.activity.compose.BackHandler(enabled=busy){}
  Column(Modifier.fillMaxWidth().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
   Text("Перенести в другой список",style=MaterialTheme.typography.titleLarge)
   Text("Количество, цены и отметки сохранятся.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
   LazyColumn(Modifier.fillMaxWidth().heightIn(max=320.dp)) {
    if(!allSame||oneOwner!=null)item(key="regular"){ListMoveOption("Текущие покупки",chosen&&selected==null,!busy){selected=null;chosen=true}}
    items(lists.filter{!allSame||it.id!=oneOwner},key={it.id}){list->ListMoveOption(list.name,chosen&&selected==list.id,!busy){selected=list.id;chosen=true}}
   }
   if(creating)InlineCreateValue("Название списка",{name,done->
    scope.launch{try{selected=repo.withSession(generation){repo.create(name)};chosen=true;creating=false;done()}catch(e:Exception){if(e is CancellationException)throw e;error=e.message;done()}}
   },{creating=false})
   else TextButton(onClick={creating=true},enabled=!busy){Text("Новый список…")}
   error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
   Button(onClick={busy=true;error=null;scope.launch{
    try{repo.withSession(generation){repo.moveEntriesExpected(expectedOwners,selected)};onDone()}
    catch(e:Exception){if(e is CancellationException)throw e;error=e.message}
    finally{busy=false}
   }},enabled=chosen&&!busy,modifier=Modifier.fillMaxWidth()){Text(if(busy)"Перенос…"else "Перенести")}
   Spacer(Modifier.height(16.dp))
  }
 }
}
@Composable private fun ListMoveOption(name:String,selected:Boolean,enabled:Boolean,onClick:()->Unit) {
 Row(Modifier.fillMaxWidth().selectable(selected,enabled=enabled,role=androidx.compose.ui.semantics.Role.RadioButton,onClick=onClick).padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
  RadioButton(selected,null,enabled=enabled);Spacer(Modifier.width(12.dp));Text(name,style=MaterialTheme.typography.bodyLarge)
 }
}
