package com.spisokryadom.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics

@Composable
fun RetailRelationSelector(label:String,options:List<Pair<Long,String>>,selectedId:Long?,newName:String,onSelect:(Long?)->Unit,onNewName:(String)->Unit){
    var creating by rememberSaveable{mutableStateOf(newName.isNotBlank())}
    if(creating){
        OutlinedTextField(newName,onNewName,label={Text(if(label=="Сеть")"Название сети"else "Название рынка или ТЦ")},singleLine=true,modifier=Modifier.fillMaxWidth())
        TextButton(onClick={creating=false;onNewName("")}){Text("Отмена нового названия")}
    }else {
        DropdownSelector(label,options,options.find{it.first==selectedId},{it.second},{onSelect(it?.first);onNewName("")},allowCreate=true,
            createLabel=if(label=="Сеть")"Новая сеть…"else "Новый рынок или ТЦ…",onCreateNew={creating=true})
        if(label=="Сеть"&&selectedId!=null)TextButton(onClick={onNewName(options.find{it.first==selectedId}?.second.orEmpty());creating=true}){Text("Изменить название сети")}
    }
}
