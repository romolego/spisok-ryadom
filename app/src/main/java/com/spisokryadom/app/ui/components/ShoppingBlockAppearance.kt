package com.spisokryadom.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Soft tint keeps text/urgent and bought cards legible in both application themes. */
fun shoppingBlockColor(key:String,surface:Color):Color?=accentColor(key)?.copy(alpha=.16f)?.compositeOver(surface)

@Composable
fun ShoppingBlockAppearance(value:String,shopName:String,onChange:(String)->Unit){
    var showColors by remember{mutableStateOf(false)}
    var lastColor by rememberSaveable{mutableStateOf(value.ifEmpty{"rose"})}
    if(showColors)AppearanceSheet("Фон блока магазина",value,withIcons=false,
        onSave={color,_->if(color.isNotEmpty())lastColor=color;onChange(color);showColors=false},onDismiss={showColors=false})
    OutlinedCard(Modifier.fillMaxWidth().testTag("shop-background-settings"),colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface)){
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("Оформление в списке покупок",style=MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth().testTag("shop-background-switch").toggleable(value.isNotEmpty(),role=Role.Switch){enabled->
                if(!enabled){lastColor=value;onChange("")}else onChange(lastColor)
            },verticalAlignment=Alignment.CenterVertically){
                Text("Выделять блок магазина фоном",Modifier.weight(1f).padding(end=12.dp))
                Switch(value.isNotEmpty(),null)
            }
            if(value.isNotEmpty()){
                Box(Modifier.fillMaxWidth().background(shoppingBlockColor(value,MaterialTheme.colorScheme.surface)?:Color.Transparent,RoundedCornerShape(12.dp)).padding(12.dp)){
                    Text(shopName.ifBlank{"Магазин"},style=MaterialTheme.typography.titleSmall)
                }
                OutlinedButton(onClick={showColors=true},modifier=Modifier.testTag("shop-background-color")){Text("Цвет фона")}
            }
        }
    }
}
