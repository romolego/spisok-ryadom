package com.spisokryadom.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable fun FieldHelp(title:String,text:String) {
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick={open=true},modifier=Modifier.size(40.dp)) {
        Icon(Icons.AutoMirrored.Outlined.HelpOutline,"Подсказка: $title")
    }
    if(open)AlertDialog(onDismissRequest={open=false},title={Text(title)},text={Text(text)},
        confirmButton={TextButton(onClick={open=false}){Text("Понятно")}})
}

@Composable fun HelpLabel(title:String,text:String) {
    Row(verticalAlignment=Alignment.CenterVertically) { Text(title,Modifier.weight(1f));FieldHelp(title,text) }
}
