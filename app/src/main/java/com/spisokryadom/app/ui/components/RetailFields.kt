package com.spisokryadom.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RetailRelationSelector(label:String,options:List<Pair<Long,String>>,selectedId:Long?,newName:String,onSelect:(Long?)->Unit,onNewName:(String)->Unit){
    var editor by rememberSaveable{mutableStateOf<String?>(null)}
    val selected=if(newName.isNotBlank())(selectedId?:0L) to newName else options.find{it.first==selectedId}
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
        DropdownSelector(label,options,selected,{it.second},{onSelect(it?.first);onNewName("")},modifier=Modifier.weight(1f),allowCreate=true,
            createLabel=if(label=="Сеть")"Новая сеть…"else "Новый рынок или ТЦ…",onCreateNew={editor="new"})
        if(selected!=null)IconButton(onClick={editor="edit"}){Icon(Icons.Filled.Edit,if(label=="Сеть")"Изменить название сети"else "Изменить название рынка или ТЦ")}
    }
    editor?.let{mode->
        var draft by rememberSaveable(mode){mutableStateOf(if(mode=="new")""else selected?.second.orEmpty())}
        EditorSheet(if(mode=="new")"Новая сеть"else "Изменить сеть",{editor=null},actions={
                    TextButton(onClick={editor=null}){Text("Отмена")}
                    TextButton(onClick={if(mode=="new")onSelect(null);onNewName(draft.trim());editor=null},enabled=draft.isNotBlank()){Text("Сохранить")}
        }){
            OutlinedTextField(draft,{draft=it},label={Text("Название сети")},singleLine=true,modifier=Modifier.fillMaxWidth())
        }
    }
}
