package com.spisokryadom.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Same inline quantity control in the main basket and saved lists. */
@Composable
fun PurchaseQuantityStepper(text:String,bought:Boolean,canDecrease:Boolean,onDecrease:()->Unit,onIncrease:()->Unit,enabled:Boolean=true,decreaseLabel:String="Уменьшить",increaseLabel:String="Увеличить",onTextChange:((String)->Unit)?=null,inputModifier:Modifier=Modifier){
 Row(Modifier.background(if(!bought)MaterialTheme.colorScheme.primaryContainer.copy(alpha=.5f)else MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.5f),RoundedCornerShape(8.dp)),verticalAlignment=Alignment.CenterVertically){
  IconButton(onClick=onDecrease,enabled=enabled&&!bought&&canDecrease,modifier=Modifier.size(32.dp)){Icon(Icons.Filled.Remove,decreaseLabel,Modifier.size(16.dp))}
  if(onTextChange==null)Text(text,style=MaterialTheme.typography.bodyMedium.copy(fontWeight=FontWeight.Medium),color=MaterialTheme.colorScheme.onPrimaryContainer,maxLines=2,modifier=Modifier.widthIn(min=24.dp,max=96.dp).padding(horizontal=2.dp),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
  else androidx.compose.foundation.text.BasicTextField(text,onTextChange,enabled=enabled&&!bought,singleLine=true,
   keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Decimal),
   textStyle=MaterialTheme.typography.bodyMedium.copy(fontWeight=FontWeight.Medium,color=MaterialTheme.colorScheme.onPrimaryContainer,textAlign=androidx.compose.ui.text.style.TextAlign.Center),
   modifier=Modifier.width(48.dp).padding(vertical=8.dp).then(inputModifier))
  IconButton(onClick=onIncrease,enabled=enabled&&!bought,modifier=Modifier.size(32.dp)){Icon(Icons.Filled.Add,increaseLabel,Modifier.size(16.dp))}
 }
}
