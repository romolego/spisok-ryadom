package com.spisokryadom.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spisokryadom.app.util.FuzzySearch

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

/**
 * Диалог ввода текста с подсказками из существующих значений.
 * Подсказки работают через единый механизм приблизительного поиска [FuzzySearch].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputDialogWithSuggestions(
    title: String,
    initialValue: String = "",
    label: String = "",
    suggestions: List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    var expanded by remember { mutableStateOf(false) }
    val filteredSuggestions = remember(text, suggestions) {
        if (text.isBlank()) suggestions
        else FuzzySearch.filterAndSort(suggestions, text)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                ExposedDropdownMenuBox(
                    expanded = expanded && filteredSuggestions.isNotEmpty(),
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = {
                            text = it
                            expanded = true
                        },
                        label = { Text(label) },
                        singleLine = true,
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryEditable)
                            .fillMaxWidth()
                    )
                    if (filteredSuggestions.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = expanded && filteredSuggestions.isNotEmpty(),
                            onDismissRequest = { expanded = false }
                        ) {
                            filteredSuggestions.forEach { suggestion ->
                                DropdownMenuItem(
                                    text = { Text(suggestion) },
                                    onClick = {
                                        text = suggestion
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
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

/**
 * Диалог множественного выбора из существующих элементов с возможностью создания новых.
 * Уже добавленные элементы показываются как неактивные с пометкой «(добавлена)».
 */
@Composable
fun MultiSelectDialog(
    title: String,
    availableItems: List<String>,
    alreadyAddedItems: Set<String>,
    newItemLabel: String = "Новое название",
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedItems by remember { mutableStateOf(setOf<String>()) }
    var newItemText by remember { mutableStateOf("") }
    var customItems by remember { mutableStateOf(listOf<String>()) }

    val selectableItems = availableItems.filter { it !in alreadyAddedItems }
    val hasAnythingToConfirm = selectedItems.isNotEmpty() || customItems.isNotEmpty()

    val addNewItem = {
        val trimmed = newItemText.trim()
        if (trimmed.isNotBlank()) {
            when {
                trimmed in alreadyAddedItems -> { /* already added, ignore */ }
                trimmed in selectableItems -> selectedItems = selectedItems + trimmed
                trimmed !in customItems -> customItems = customItems + trimmed
            }
            newItemText = ""
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // Selectable items (available but not yet added)
                selectableItems.forEach { name ->
                    val isSelected = name in selectedItems
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedItems = if (isSelected) selectedItems - name
                                else selectedItems + name
                            }
                            .padding(vertical = 2.dp)
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                selectedItems = if (checked) selectedItems + name
                                else selectedItems - name
                            }
                        )
                        Text(name, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // Already added items (disabled)
                alreadyAddedItems.forEach { name ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Checkbox(checked = true, onCheckedChange = null, enabled = false)
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "(добавлена)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Custom items added in this session
                customItems.forEach { name ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { customItems = customItems - name }
                            .padding(vertical = 2.dp)
                    ) {
                        Checkbox(
                            checked = true,
                            onCheckedChange = { customItems = customItems - name }
                        )
                        Text(name, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Input for creating brand-new items
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = newItemText,
                        onValueChange = { newItemText = it },
                        label = { Text(newItemLabel) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { addNewItem() }),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { addNewItem() },
                        enabled = newItemText.isNotBlank()
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Добавить")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedItems.toList() + customItems) },
                enabled = hasAnythingToConfirm
            ) { Text("Добавить") }
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
        else FuzzySearch.filterAndSort(unitSuggestions, unit)
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

/**
 * Компактный встроенный блок управления количеством товара в стиле интернет-магазина.
 * Показывает [-] количество [+] с единицей измерения.
 * Нажатие на количество позволяет ввести значение вручную.
 */
@Composable
fun InlineQuantityControl(
    quantity: Double,
    unit: String,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onSetQuantity: (Double) -> Unit,
    modifier: Modifier = Modifier,
    decrementEnabled: Boolean = true
) {
    var isEditing by remember { mutableStateOf(false) }
    val qtyStr = remember(quantity) {
        if (quantity == quantity.toLong().toDouble()) quantity.toLong().toString()
        else quantity.toString()
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
        ) {
            IconButton(
                onClick = onDecrement,
                enabled = decrementEnabled,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Filled.Remove,
                    contentDescription = "Уменьшить",
                    tint = if (decrementEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier.size(18.dp)
                )
            }

            if (isEditing) {
                val focusRequester = remember { FocusRequester() }
                val focusManager = LocalFocusManager.current
                var textFieldValue by remember(qtyStr) {
                    mutableStateOf(TextFieldValue(qtyStr, TextRange(0, qtyStr.length)))
                }

                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }

                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        val filtered = newValue.text.filter { c -> c.isDigit() || c == '.' }
                        if (filtered.isEmpty() || filtered.matches(Regex("^\\d*\\.?\\d*$"))) {
                            textFieldValue = newValue.copy(text = filtered)
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val newQty = textFieldValue.text.toDoubleOrNull()
                            if (newQty != null) {
                                onSetQuantity(newQty)
                            }
                            isEditing = false
                            focusManager.clearFocus()
                        }
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .widthIn(min = 36.dp, max = 56.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged { state ->
                            if (!state.isFocused && isEditing) {
                                val newQty = textFieldValue.text.toDoubleOrNull()
                                if (newQty != null) {
                                    onSetQuantity(newQty)
                                }
                                isEditing = false
                            }
                        }
                )
            } else {
                Text(
                    text = qtyStr,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .widthIn(min = 36.dp)
                        .clickable { isEditing = true }
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }

            IconButton(
                onClick = onIncrement,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Увеличить",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (unit.isNotBlank()) {
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
