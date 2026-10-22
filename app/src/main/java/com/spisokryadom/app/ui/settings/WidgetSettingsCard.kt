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
import kotlin.math.roundToInt

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
    OutlinedCard(modifier = Modifier.testTag("widget-settings"),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Виджет списка покупок", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                HelpButton("Виджет списка покупок", "Эти настройки действуют для всех виджетов. Группировка виджета не меняет вид Главной. Скрытие купленного убирает строки только из виджета: покупки сохраняются в приложении и могут быть показаны снова.")
            }
            DropdownSelector("Группировка в виджете", ViewMode.entries.toList(), value.mode,
                ::widgetModeLabel, { mode -> mode?.let { onChange(value.copy(mode = it)) } },
                modifier = Modifier.testTag("widget-grouping"), allowNone = false,
                itemVector = { mode -> when (mode) {
                    ViewMode.BY_SHOPS -> Icons.Filled.Store
                    ViewMode.BY_GROUPS -> Icons.Filled.Category
                    ViewMode.BY_RECIPIENTS -> Icons.Filled.Person
                    ViewMode.FLAT -> Icons.AutoMirrored.Filled.FormatListBulleted
                } })
            if (value.mode == ViewMode.BY_SHOPS) WidgetSwitch("Группы внутри магазинов", value.showDepartments,
                "widget-departments") { onChange(value.copy(showDepartments = it)) }
            WidgetSwitch("Показывать купленные", value.showBought, "widget-show-bought") {
                onChange(value.copy(showBought = it))
            }
            WidgetSwitch("Кнопка скрытия купленных", value.showBoughtToggle, "widget-bought-toggle") {
                onChange(value.copy(showBoughtToggle = it))
            }
            OutlinedCard(onClick = { appearance = true }, modifier = Modifier.fillMaxWidth().testTag("widget-appearance-open")) {
                ListItem(headlineContent = { Text("Оформление виджета") },
                    leadingContent = { Icon(Icons.Filled.Palette, null) },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, "Открыть оформление виджета") })
            }
            Text("Размер текста: ${value.textSize}", style = MaterialTheme.typography.bodyMedium)
            Slider(value.textSize.toFloat(), { size ->
                val rounded = size.roundToInt()
                if (rounded != value.textSize) onChange(value.copy(textSize = rounded))
            }, valueRange = WidgetSettings.MIN_TEXT_SIZE.toFloat()..WidgetSettings.MAX_TEXT_SIZE.toFloat(),
                steps = 9, modifier = Modifier.testTag("widget-font-size"))
        }
    }
    if (appearance) com.spisokryadom.app.ui.components.EditorSheet("Оформление виджета",{appearance=false},actions={
        TextButton(onClick={appearance=false}){Text("Готово")}
    }) {
                SettingWithHelp("Цвета виджета", "Подложки магазинов используют цвета из их карточек и видны при группировке по магазинам. Подложки товаров используют оформление покупок: цвет магазина, избранное и статусы. Эти два вида подложек включаются независимо. Изменения применяются сразу."){
                    Text("Подложки",style=MaterialTheme.typography.labelLarge)
                }
                WidgetSwitch("Подложки магазинов", value.showShopBackground, "widget-shop-background") {
                    onChange(value.copy(showShopBackground = it))
                }
                WidgetSwitch("Подложки товаров", value.showProductBackground, "widget-product-background") {
                    onChange(value.copy(showProductBackground = it))
                }
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
