package com.spisokryadom.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.ui.components.DropdownSelector

@Composable fun ProductInputSettingsCard(value:ProductInputSettings,recipients:List<RecipientEntity>,onChange:((ProductInputSettings)->ProductInputSettings)->Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Предзаполнение новых товаров",style=MaterialTheme.typography.titleMedium)
            SettingSwitch("Распознавать названия товаров",value.prefillEnabled,{enabled->onChange{it.copy(prefillEnabled=enabled)}},
                help="Встроенный русский справочник заполняет параметры новых товаров. Его позиции не появляются в вашем каталоге. Ручной выбор сохраняется.")
            if(value.prefillEnabled){
                SettingSwitch("Товарная группа",value.prefillCategory,{enabled->onChange{it.copy(prefillCategory=enabled)}})
                SettingSwitch("Единица измерения",value.prefillUnit,{enabled->onChange{it.copy(prefillUnit=enabled)}})
                SettingSwitch("Группа магазинов",value.prefillDepartment,{enabled->onChange{it.copy(prefillDepartment=enabled)}})
            }
            HorizontalDivider()
            SettingSwitch("Подставлять общую группу в новых магазинах",value.inheritDepartment,{enabled->onChange{it.copy(inheritDepartment=enabled)}},
                help="Использует группу товара по умолчанию и при необходимости создаёт её в добавленном магазине. Уже настроенные магазины не меняются.")
            HorizontalDivider()
            DropdownSelector("Получатель по умолчанию",recipients,recipients.find{it.id==value.recipientId},{it.name},
                {recipient->onChange{it.copy(recipientId=recipient?.id,recipientEnabled=it.recipientEnabled&&recipient!=null)}},noneLabel="Не выбран")
            SettingSwitch("Подставлять получателя, если не выбран",value.recipientEnabled&&value.recipientId!=null,
                {enabled->onChange{it.copy(recipientEnabled=enabled&&it.recipientId!=null)}},
                help="Сначала выберите получателя. Явный выбор в карточке и преднастройка товарной группы важнее общей настройки.",enabled=value.recipientId!=null)
        }
    }
}
