package com.spisokryadom.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** One touch target, a reserved control slot and an explicit gap, including multiline labels. */
@Composable
fun SelectionRow(label:String,checked:Boolean,onChange:(Boolean)->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,tag:String?=null) {
    Row(modifier.fillMaxWidth().heightIn(min=48.dp).toggleable(checked,enabled=enabled,role=Role.Checkbox,onValueChange=onChange),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).then(if(tag!=null)Modifier.testTag("$tag-control")else Modifier),contentAlignment=Alignment.Center) {
            Checkbox(checked,onCheckedChange=null,enabled=enabled)
        }
        Text(label,Modifier.weight(1f).padding(start=12.dp,end=4.dp,top=8.dp,bottom=8.dp).then(if(tag!=null)Modifier.testTag("$tag-label")else Modifier),color=if(enabled)MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
