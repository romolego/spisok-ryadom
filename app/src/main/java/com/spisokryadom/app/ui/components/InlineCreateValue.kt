package com.spisokryadom.app.ui.components
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
@Composable fun InlineCreateValue(label:String,onCreate:(String,()->Unit)->Unit,onCancel:()->Unit){
 var name by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)}
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
  OutlinedTextField(name,{name=it},label={Text(label)},singleLine=true,enabled=!busy,modifier=Modifier.weight(1f))
  IconButton(onClick={busy=true;onCreate(name.trim()){busy=false}},enabled=name.isNotBlank()&&!busy){Icon(Icons.Filled.Add,"Создать: $label")}
  IconButton(onClick=onCancel,enabled=!busy){Icon(Icons.Filled.Close,"Отмена: $label")}
 }
}
