package com.spisokryadom.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable

/** Only the body scrolls: long dictionaries cannot push the action buttons off screen. */
@Composable fun SelectionDialog(title:String,onDismiss:()->Unit,confirm:@Composable ()->Unit,content:@Composable ColumnScope.()->Unit){
 EditorSheet(title,onDismiss,actions={
  TextButton(onClick=onDismiss){Text("Отмена")}
  confirm()
 },content=content)
}
