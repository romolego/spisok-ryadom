package com.spisokryadom.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spisokryadom.app.ui.components.DropdownSelector
import com.spisokryadom.app.ui.shoppinglist.ViewMode
import com.spisokryadom.app.widget.WidgetSettings
import kotlin.math.roundToInt

@Composable
fun WidgetSettingsCard(value:WidgetSettings,onChange:(WidgetSettings)->Unit){
    OutlinedCard(modifier=Modifier.testTag("widget-settings"),colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface)){
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text("Виджет списка покупок",style=MaterialTheme.typography.titleMedium)
            DropdownSelector("Группировка в виджете",ViewMode.entries.toList(),value.mode,
                {if(it==ViewMode.FLAT)"Общий список"else it.label},{mode->mode?.let{onChange(value.copy(mode=it))}},
                modifier=Modifier.testTag("widget-grouping"),allowNone=false)
            if(value.mode==ViewMode.BY_SHOPS)Row(Modifier.fillMaxWidth().testTag("widget-departments")
                .toggleable(value.showDepartments,role=Role.Switch){onChange(value.copy(showDepartments=it))},verticalAlignment=Alignment.CenterVertically){
                Text("Показывать группы внутри магазинов",Modifier.weight(1f).padding(end=12.dp))
                Switch(value.showDepartments,null)
            }
            Text("Размер текста: ${value.textSize}",style=MaterialTheme.typography.bodyMedium)
            Slider(value.textSize.toFloat(),{size->val rounded=size.roundToInt();if(rounded!=value.textSize)onChange(value.copy(textSize=rounded))},
                valueRange=WidgetSettings.MIN_TEXT_SIZE.toFloat()..WidgetSettings.MAX_TEXT_SIZE.toFloat(),steps=9,
                modifier=Modifier.testTag("widget-font-size"))
            Text("Молоко · 1 л",fontSize=value.textSize.sp,modifier=Modifier.testTag("widget-font-preview"))
            TextButton(onClick={onChange(value.copy(textSize=16))},enabled=value.textSize!=16){Text("Обычный размер")}
        }
    }
}
