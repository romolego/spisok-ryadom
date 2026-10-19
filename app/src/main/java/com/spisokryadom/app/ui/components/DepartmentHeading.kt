package com.spisokryadom.app.ui.components
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
@Composable fun DepartmentHeading(name:String,accent:String="",iconKey:String="",modifier:Modifier=Modifier){
 androidx.compose.runtime.CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant){
  MarkedLabel(name,accent,iconKey,modifier.fillMaxWidth().padding(start=24.dp,top=10.dp,bottom=6.dp),small=true)
 }
}
