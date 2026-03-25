package com.spisokryadom.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Да") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
fun InputDialog(
    title: String,
    initialValue: String = "",
    label: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
                enabled = text.isNotBlank()
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownSelector(
    label: String,
    items: List<T>,
    selectedItem: T?,
    itemLabel: (T) -> String,
    onSelect: (T?) -> Unit,
    modifier: Modifier = Modifier,
    allowCreate: Boolean = false,
    onCreateNew: (() -> Unit)? = null,
    createLabel: String = "Создать..."
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedItem?.let { itemLabel(it) } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (selectedItem != null) {
                DropdownMenuItem(
                    text = { Text("Не задано", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = {
                        onSelect(null)
                        expanded = false
                    }
                )
            }
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(itemLabel(item)) },
                    onClick = {
                        onSelect(item)
                        expanded = false
                    }
                )
            }
            if (allowCreate && onCreateNew != null) {
                DropdownMenuItem(
                    text = {
                        Text(
                            createLabel,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    onClick = {
                        expanded = false
                        onCreateNew()
                    }
                )
            }
        }
    }
}

/**
 * Поле количества и единицы измерения.
 * Простая версия без подсказок — используется там, где подсказки не нужны.
 */
@Composable
fun QuantityUnitInput(
    quantity: String,
    unit: String,
    onQuantityChange: (String) -> Unit,
    onUnitChange: (String) -> Unit,
    quantityLabel: String = "Количество",
    unitLabel: String = "Ед. изм.",
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = quantity,
            onValueChange = { value ->
                if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) {
                    onQuantityChange(value)
                }
            },
            label = { Text(quantityLabel) },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedTextField(
            value = unit,
            onValueChange = onUnitChange,
            label = { Text(unitLabel) },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Поле количества и единицы измерения с подсказками единиц измерения.
 * Рекомендует уже существующие значения, но не запрещает ввод нового.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuantityUnitInputWithSuggestions(
    quantity: String,
    unit: String,
    onQuantityChange: (String) -> Unit,
    onUnitChange: (String) -> Unit,
    unitSuggestions: List<String>,
    quantityLabel: String = "Количество",
    unitLabel: String = "Ед. изм.",
    modifier: Modifier = Modifier
) {
    var unitExpanded by remember { mutableStateOf(false) }
    val filteredSuggestions = remember(unit, unitSuggestions) {
        if (unit.isBlank()) unitSuggestions
        else unitSuggestions.filter { it.contains(unit, ignoreCase = true) }
    }

    Row(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = quantity,
            onValueChange = { value ->
                if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) {
                    onQuantityChange(value)
                }
            },
            label = { Text(quantityLabel) },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))

        ExposedDropdownMenuBox(
            expanded = unitExpanded && filteredSuggestions.isNotEmpty(),
            onExpandedChange = { unitExpanded = it },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = unit,
                onValueChange = { value ->
                    onUnitChange(value)
                    unitExpanded = true
                },
                label = { Text(unitLabel) },
                singleLine = true,
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryEditable)
                    .fillMaxWidth()
            )
            if (filteredSuggestions.isNotEmpty()) {
                ExposedDropdownMenu(
                    expanded = unitExpanded && filteredSuggestions.isNotEmpty(),
                    onDismissRequest = { unitExpanded = false }
                ) {
                    filteredSuggestions.forEach { suggestion ->
                        DropdownMenuItem(
                            text = { Text(suggestion) },
                            onClick = {
                                onUnitChange(suggestion)
                                unitExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PurchaseTypeSelector(
    selectedType: String,
    onTypeChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text("Тип покупки", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = selectedType == "offline",
                onClick = { onTypeChange("offline") }
            )
            Text(
                "Оффлайн",
                modifier = Modifier.clickable { onTypeChange("offline") }
            )
            Spacer(modifier = Modifier.width(16.dp))
            RadioButton(
                selected = selectedType == "online",
                onClick = { onTypeChange("online") }
            )
            Text(
                "Онлайн",
                modifier = Modifier.clickable { onTypeChange("online") }
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(vertical = 8.dp)
    )
}

/**
 * Унифицированный блок с режимами просмотра и редактирования.
 *
 * В обычном режиме отображается только [viewContent] — без управляющих действий.
 * По нажатию кнопки-карандаша пользователь входит в режим редактирования,
 * где отображается [editContent] с полным набором действий.
 */
@Composable
fun EditableSection(
    title: String,
    isEditing: Boolean,
    onToggleEdit: (Boolean) -> Unit,
    viewContent: @Composable () -> Unit,
    editContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    emptyMessage: String? = null,
    isEmpty: Boolean = false
) {
    Column(modifier = modifier) {
        // Header with edit toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { onToggleEdit(!isEditing) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isEditing) Icons.Filled.Check else Icons.Filled.Edit,
                    contentDescription = if (isEditing) "Готово" else "Редактировать",
                    tint = if (isEditing)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (isEmpty && !isEditing && emptyMessage != null) {
            Text(
                emptyMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // View mode content
        AnimatedVisibility(
            visible = !isEditing,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                viewContent()
            }
        }

        // Edit mode content
        AnimatedVisibility(
            visible = isEditing,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                editContent()
            }
        }
    }
}
