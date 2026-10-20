package com.spisokryadom.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Only the body scrolls: long dictionaries cannot push the action buttons off screen. */
@Composable fun SelectionDialog(title:String,onDismiss:()->Unit,confirm:@Composable ()->Unit,content:@Composable ColumnScope.()->Unit){
 val configuration=LocalConfiguration.current
 val width=minOf(560.dp,(configuration.screenWidthDp-32).coerceAtLeast(240).dp)
 val height=(configuration.screenHeightDp-64).coerceAtLeast(200).dp
 Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)){
  Surface(Modifier.width(width).heightIn(max=height),shape=MaterialTheme.shapes.extraLarge){
   Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
    Text(title,style=MaterialTheme.typography.headlineSmall)
    Column(Modifier.fillMaxWidth().weight(1f,false).verticalScroll(rememberScrollState()),content=content)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){
     TextButton(onClick=onDismiss){Text("Отмена")}
     confirm()
    }
   }
  }
 }
}
