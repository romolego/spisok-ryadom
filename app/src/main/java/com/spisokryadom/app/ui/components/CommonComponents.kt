package com.spisokryadom.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.spisokryadom.app.util.Quantities

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
    var text by rememberSaveable(title,initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        modifier=Modifier.width(minOf(560.dp,androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp-48.dp)),
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
    var text by rememberSaveable(title,initialValue) { mutableStateOf(initialValue) }
    var expanded by remember { mutableStateOf(false) }
    val filteredSuggestions = remember(text, suggestions) {
        if (text.isBlank()) suggestions
        else FuzzySearch.filterAndSort(suggestions, text)
    }

    AlertDialog(
        modifier=Modifier.width(minOf(560.dp,androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp-48.dp)),
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
fun MultiSelectDialog(title:String,availableItems:List<String>,alreadyAddedItems:Set<String>,newItemLabel:String="Новое название",onConfirm:(List<String>)->Unit,onDismiss:()->Unit) {
 var selected by remember{mutableStateOf(setOf<String>())};var input by remember{mutableStateOf("")};var custom by remember{mutableStateOf(listOf<String>())}
 val normalize=com.spisokryadom.app.util.FuzzySearch::normalize
 val added=alreadyAddedItems.map(normalize).toSet()
 val rows=(availableItems+alreadyAddedItems+custom).distinctBy(normalize)
 fun add(){val value=input.trim();if(value.isNotBlank()){
  val known=rows.find{normalize(it)==normalize(value)}
  if(normalize(value) !in added){if(known!=null)selected=selected+known else {custom=custom+value;selected=selected+value}}
  input=""
 }}
 val width=minOf(560.dp,androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp-32.dp)
 AlertDialog(modifier=Modifier.width(width),properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false),onDismissRequest=onDismiss,title={Text(title)},text={
  Column(Modifier.verticalScroll(rememberScrollState())){
   rows.forEach{name->key(name){
    val locked=normalize(name) in added;val checked=locked||name in selected
    Row(Modifier.fillMaxWidth().toggleable(value=checked,enabled=!locked,role=Role.Checkbox,onValueChange={v->selected=if(v)selected+name else selected-name}).padding(vertical=2.dp),verticalAlignment=Alignment.CenterVertically){
     Box(Modifier.size(48.dp),contentAlignment=Alignment.Center){Checkbox(checked,onCheckedChange=null,enabled=!locked)}
     Column(Modifier.weight(1f)){Text(name,style=MaterialTheme.typography.bodyMedium,color=if(locked)MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface);if(locked)Text("Добавлена",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
   }}
   Spacer(Modifier.height(8.dp))
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
    OutlinedTextField(input,{input=it},label={Text(newItemLabel)},singleLine=true,keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={add()}),modifier=Modifier.weight(1f))
    IconButton(onClick={add()},enabled=input.isNotBlank()){Icon(Icons.Filled.Add,"Добавить название")}
   }
  }
 },confirmButton={TextButton(onClick={onConfirm(rows.filter{it in selected&&normalize(it) !in added})},enabled=selected.isNotEmpty()){Text("Добавить")}},dismissButton={TextButton(onClick=onDismiss){Text("Отмена")}})
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
    createLabel: String = "Создать...",
    itemIcon:(T)->String={""},itemAccent:(T)->String={""},noneLabel:String="Не задано",allowNone:Boolean=true,onOpen:()->Unit={}
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if(it)onOpen();expanded=it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedItem?.let { itemLabel(it) } ?: noneLabel,
            leadingIcon=selectedItem?.let { item -> appearanceIcon(itemIcon(item))?.let{vector->{Icon(vector,null,tint=accentColor(itemAccent(item))?:MaterialTheme.colorScheme.primary)}}},
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
            items.forEach { item ->
                DropdownMenuItem(
                    text = { MarkedLabel(itemLabel(item),itemAccent(item),itemIcon(item),small=true) },
                    onClick = {
                        onSelect(item)
                        expanded = false
                    }
                )
            }
            if(allowNone) {
                DropdownMenuItem(
                    text = { Text(noneLabel, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = {
                        onSelect(null)
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
                if (value.isEmpty() || value.matches(Regex("^\\d*[.,]?\\d*$"))) {
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
                if (value.isEmpty() || value.matches(Regex("^\\d*[.,]?\\d*$"))) {
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
    modifier: Modifier = Modifier,
    allowedTypes:Set<String> = setOf("offline","online")
) {
    Column(modifier = modifier) {
        Text("Тип покупки", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = selectedType == "offline",
                onClick = { onTypeChange("offline") },enabled="offline" in allowedTypes
            )
            Text(
                "Оффлайн",
                modifier = Modifier.padding(start=8.dp).clickable(enabled="offline" in allowedTypes) { onTypeChange("offline") }
            )
            Spacer(modifier = Modifier.width(16.dp))
            RadioButton(
                selected = selectedType == "online",
                onClick = { onTypeChange("online") },enabled="online" in allowedTypes
            )
            Text(
                "Онлайн",
                modifier = Modifier.padding(start=8.dp).clickable(enabled="online" in allowedTypes) { onTypeChange("online") }
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
    isEmpty: Boolean = false,
    editDescription:String = "Редактировать"
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
                    contentDescription = if (isEditing) "Готово" else editDescription,
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
    var quantityError by remember { mutableStateOf(false) }
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
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            IconButton(
                onClick = onDecrement,
                enabled = decrementEnabled,
                modifier = Modifier.size(40.dp)
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
                var textFieldValue by remember {
                    mutableStateOf(TextFieldValue(qtyStr, TextRange(0, qtyStr.length)))
                }
                var hasReceivedFocus by remember { mutableStateOf(false) }
                var finished by remember { mutableStateOf(false) }

                // Initial unfocused notifications do not mean the editor lost focus.
                // Done can also trigger focus loss: both paths share one commit guard.
                val finishEditing = {
                    if (!finished) {
                        val parsed = Quantities.parse(textFieldValue.text)
                        if (parsed == null) {
                            quantityError = true
                        } else {
                            finished = true
                            quantityError = false
                            isEditing = false
                            onSetQuantity(parsed)
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }

                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        // Preserve the input, including decimal commas and invalid signs,
                        // so an invalid -5 can never silently become a valid 5.
                        textFieldValue = newValue
                        quantityError = false
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            finishEditing()
                            if (finished) {
                                focusManager.clearFocus()
                            }
                        }
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        textAlign = TextAlign.Center,
                        color = if (quantityError) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .widthIn(min = 36.dp, max = 56.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged { state ->
                            if (state.isFocused) {
                                hasReceivedFocus = true
                            } else if (hasReceivedFocus && !finished) {
                                finishEditing()
                            }
                        }
                )
            } else {
                Text(
                    text = qtyStr,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .widthIn(min = 36.dp, max = 72.dp)
                        .clickable { quantityError = false; isEditing = true }
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }

            IconButton(
                onClick = onIncrement,
                modifier = Modifier.size(40.dp)
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
        if (isEditing && quantityError) {
            Text(
                text = "Некорректное количество",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
