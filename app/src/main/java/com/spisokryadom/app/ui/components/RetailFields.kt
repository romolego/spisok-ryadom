package com.spisokryadom.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp

/** Optional relationship: closed by default, creation/renaming stays inside the card draft. */
@Composable
fun OptionalRetailField(label:String,options:List<Pair<Long,String>>,selectedId:Long?,newName:String,onSelect:(Long?)->Unit,onNewName:(String)->Unit,canRename:Boolean=false) {
    val focus=androidx.compose.ui.platform.LocalFocusManager.current
    val network=label=="Сеть"
    val title=if(network)"Сеть магазинов"else "Рынок или ТЦ"
    val create=if(network)"Создать сеть"else "Создать рынок или ТЦ"
    var editing by rememberSaveable { mutableStateOf(false) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var previousId by rememberSaveable { mutableStateOf<Long?>(null) }
    var previousName by rememberSaveable { mutableStateOf("") }
    fun begin() { previousId=selectedId;previousName=newName;editing=true;creating=options.isEmpty()||newName.isNotBlank() }
    val name=newName.ifBlank { options.find{it.first==selectedId}?.second.orEmpty() }
    if(!editing) {
        if(name.isBlank()) {
            TextButton(onClick={begin()}) {
                Icon(Icons.Filled.Add,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp))
                Text(if(network)"Добавить сеть магазинов"else "Добавить рынок или ТЦ")
            }
        } else {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start=12.dp,end=4.dp,top=4.dp,bottom=4.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Filled.Store,null,tint=MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(horizontal=12.dp)) {
                        Text(title,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(name,style=MaterialTheme.typography.bodyLarge)
                    }
                    IconButton(onClick={begin()}) { Icon(Icons.Filled.Edit,"Изменить: $title") }
                }
            }
        }
    } else {
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                if(creating) {
                    OutlinedTextField(newName,onNewName,label={Text(if(network)"Название сети"else "Название рынка или ТЦ")},singleLine=true,modifier=Modifier.fillMaxWidth())
                } else {
                    DropdownSelector(title,options,options.find{it.first==selectedId},{it.second},{onSelect(it?.first);onNewName("")},allowCreate=true,onCreateNew={creating=true;onSelect(null);onNewName("")},createLabel=create)
                    if(canRename&&selectedId!=null)TextButton(onClick={creating=true;onNewName(name)}){Text("Изменить название сети")}
                    if(selectedId!=null)TextButton(onClick={onSelect(null);onNewName("");editing=false}){Text(if(network)"Убрать связь с сетью"else "Убрать связь с рынком или ТЦ")}
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                    TextButton(onClick={focus.clearFocus();onSelect(previousId);onNewName(previousName);editing=false}){Text("Отмена")}
                    TextButton(modifier=Modifier.semantics{contentDescription="Завершить: $title"},onClick={focus.clearFocus();editing=false},enabled=!creating||newName.isNotBlank()){Text("Готово")}
                }
            }
        }
    }
}
