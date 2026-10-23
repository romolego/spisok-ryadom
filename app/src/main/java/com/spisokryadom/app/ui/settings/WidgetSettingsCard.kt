package com.spisokryadom.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.spisokryadom.app.ui.components.DropdownSelector
import com.spisokryadom.app.ui.shoppinglist.ViewMode
import com.spisokryadom.app.widget.WidgetSettings

internal fun widgetModeLabel(mode: ViewMode): String = when (mode) {
    ViewMode.BY_SHOPS -> "По магазинам"
    ViewMode.BY_GROUPS -> "По товарным группам"
    ViewMode.BY_RECIPIENTS -> "По получателям"
    ViewMode.FLAT -> "Общий список"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSettingsCard(value: WidgetSettings, onChange: (WidgetSettings) -> Unit) {
    var appearance by rememberSaveable { mutableStateOf(false) }
    OutlinedCard(onClick={appearance=true},modifier=Modifier.fillMaxWidth().testTag("widget-settings")) {
        ListItem(headlineContent={Text("Настройки виджета")},
            leadingContent={Icon(Icons.Filled.Widgets,null)},
            trailingContent={Icon(Icons.Filled.ChevronRight,"Открыть настройки виджета")})
    }
    if (appearance) com.spisokryadom.app.ui.components.EditorSheet("Настройки виджета",{appearance=false},actions={
        TextButton(onClick={appearance=false}){Text("Готово")}
    }) {
        DropdownSelector("Группировка в виджете", ViewMode.entries.toList(), value.mode,
            ::widgetModeLabel, { mode -> mode?.let { onChange(value.copy(mode=it)) } },
            modifier=Modifier.testTag("widget-grouping"),allowNone=false,
            itemVector={when(it){ViewMode.BY_SHOPS->Icons.Filled.Store;ViewMode.BY_GROUPS->Icons.Filled.Category;ViewMode.BY_RECIPIENTS->Icons.Filled.Person;ViewMode.FLAT->Icons.AutoMirrored.Filled.FormatListBulleted}})
        if(value.mode==ViewMode.BY_SHOPS)WidgetSwitch("Группы внутри магазинов",value.showDepartments,"widget-departments"){onChange(value.copy(showDepartments=it))}
        WidgetSwitch("Показывать купленные",value.showBought,"widget-show-bought"){onChange(value.copy(showBought=it))}
        WidgetSwitch("Кнопка скрытия купленных",value.showBoughtToggle,"widget-bought-toggle"){onChange(value.copy(showBoughtToggle=it))}
        DropdownSelector("Размер текста",(WidgetSettings.MIN_TEXT_SIZE..WidgetSettings.MAX_TEXT_SIZE).toList(),value.textSize,
            {"$it"},{it?.let{size->onChange(value.copy(textSize=size))}},allowNone=false,modifier=Modifier.testTag("widget-font-size"))
        WidgetSwitch("Подложки магазинов",value.showShopBackground,"widget-shop-background"){onChange(value.copy(showShopBackground=it))}
        WidgetSwitch("Подложки товаров",value.showProductBackground,"widget-product-background"){onChange(value.copy(showProductBackground=it))}
    }
}

@Composable
private fun WidgetSwitch(label: String, value: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag)
        .toggleable(value, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f).padding(end = 12.dp))
        Switch(value, null)
    }
}
