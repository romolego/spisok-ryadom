package com.spisokryadom.app.ui.components
import androidx.compose.runtime.CompositionLocalProvider

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
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Store
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
    EditorSheet(title, onDismiss, actions = {
        TextButton(onClick = onDismiss) { Text("Отмена") }
        TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
            enabled = text.isNotBlank()) { Text("Сохранить") }
    }) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
    }
}

/**
 * Диалог множественного выбора из существующих элементов с возможностью создания новых.
 * Уже добавленные элементы показываются как неактивные с пометкой «(добавлена)».
 */
@Composable
fun MultiSelectDialog(title:String,availableItems:List<String>,alreadyAddedItems:Set<String>,newItemLabel:String="Новое название",onConfirm:(List<String>)->Unit,onDismiss:()->Unit,favorites:List<String> = emptyList(),alphabetical:Boolean=false) {
 androidx.compose.runtime.CompositionLocalProvider(com.spisokryadom.app.ui.components.LocalFavoriteSurface provides com.spisokryadom.app.util.FavoriteSurface.DROPDOWNS) {
 var selected by remember{mutableStateOf(setOf<String>())};var input by remember{mutableStateOf("")};var custom by remember{mutableStateOf(listOf<String>())}
 val normalize=com.spisokryadom.app.util.FuzzySearch::normalize
 val added=alreadyAddedItems.map(normalize).toSet()
 val rawRows=(availableItems+alreadyAddedItems+custom).distinctBy(normalize)
 val rows=if(alphabetical)com.spisokryadom.app.util.alphabetical(rawRows){it}else rawRows
 val pinned=favorites.mapNotNull{fav->rows.find{normalize(it)==normalize(fav)}}.distinctBy(normalize)
 fun add(){val value=input.trim();if(value.isNotBlank()){
  val known=rows.find{normalize(it)==normalize(value)}
  if(normalize(value) !in added){if(known!=null)selected=selected+known else {custom=custom+value;selected=selected+value}}
  input=""
 }}
 SelectionDialog(title,onDismiss,confirm={TextButton(onClick={onConfirm(rows.filter{it in selected&&normalize(it) !in added})},enabled=selected.isNotEmpty()){Text("Добавить")}}){
   (pinned.map{it to true}+rows.map{it to false}).forEachIndexed{index,(name,pin)->key(name,pin){
    if(pinned.isNotEmpty()&&(index==0||index==pinned.size))Text(if(index==0)"Избранные"else "Все значения",style=MaterialTheme.typography.labelMedium)
    val row:@Composable ()->Unit={
    val locked=normalize(name) in added;val checked=locked||name in selected
    Row(Modifier.fillMaxWidth().toggleable(value=checked,enabled=!locked,role=Role.Checkbox,onValueChange={v->selected=if(v)selected+name else selected-name}).padding(vertical=2.dp),verticalAlignment=Alignment.CenterVertically){
     Box(Modifier.size(48.dp),contentAlignment=Alignment.Center){Checkbox(checked,onCheckedChange=null,enabled=!locked)}
     Column(Modifier.weight(1f)){FavoriteMarkedLabel(name,favorite=pinned.any{normalize(it)==normalize(name)},small=true);if(locked)Text("Добавлена",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
    }
    if(pin)FavoriteBlock(Modifier.fillMaxWidth()){row()}else row()
   }}
   Spacer(Modifier.height(8.dp))
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
    OutlinedTextField(input,{input=it},label={Text(newItemLabel)},singleLine=true,keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={add()}),modifier=Modifier.weight(1f))
    IconButton(onClick={add()},enabled=input.isNotBlank()){Icon(Icons.Filled.Add,"Добавить название")}
   }
 }

 }
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
    itemIcon:(T)->String={""},itemAccent:(T)->String={""},noneLabel:String="Не задано",allowNone:Boolean=true,onOpen:()->Unit={},itemVector:(T)->androidx.compose.ui.graphics.vector.ImageVector?={null},
    itemFavorite:(T)->Boolean={false},favoriteOrder:(T)->Int={0},alphabetical:Boolean=false,
    itemEnabled:(T)->Boolean={true}
) {
 androidx.compose.runtime.CompositionLocalProvider(com.spisokryadom.app.ui.components.LocalFavoriteSurface provides com.spisokryadom.app.util.FavoriteSurface.DROPDOWNS) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if(it)onOpen();expanded=it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedItem?.let { itemLabel(it) } ?: noneLabel,
            leadingIcon=selectedItem?.let { item -> (itemVector(item)?:appearanceIcon(itemIcon(item)))?.let{vector->{Icon(vector,null,tint=accentColor(itemAccent(item))?:MaterialTheme.colorScheme.primary)}}},
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
            val base=if(alphabetical)com.spisokryadom.app.util.alphabetical(items,itemLabel)else items
            val pinned=base.filter(itemFavorite).sortedBy(favoriteOrder)
            val rows=if(pinned.isEmpty())base.map{it to false}else pinned.map{it to true}+base.map{it to false}
            rows.forEachIndexed { index,(item,pinnedCopy) ->
                if(pinned.isNotEmpty()&&(index==0||index==pinned.size)){
                    if(index==0)FavoriteBlock{Text("Избранные",Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),style=MaterialTheme.typography.labelMedium)}
                    else Text("Все значения",Modifier.padding(horizontal=16.dp,vertical=8.dp),style=MaterialTheme.typography.labelMedium)
                }
                CompositionLocalProvider(LocalFavoriteBlock provides pinnedCopy){
                DropdownMenuItem(
                    enabled = itemEnabled(item),
                    modifier=if(pinnedCopy)Modifier.favoriteBlock()else Modifier,
                    text = { FavoriteMarkedLabel(itemLabel(item),itemAccent(item),if(itemVector(item)==null)itemIcon(item)else "",itemFavorite(item),small=true) },
                    leadingIcon=itemVector(item)?.let{v->{Icon(v,null,tint=MaterialTheme.colorScheme.primary)}},
                    onClick = {
                        onSelect(item)
                        expanded = false
                    }
                )
                }
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
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    quantityModifier: Modifier = Modifier,
    unitModifier: Modifier = Modifier
) {
    var unitExpanded by remember { mutableStateOf(false) }
    val filteredSuggestions = remember(unit, unitSuggestions) {
        if (unit.isBlank()) unitSuggestions
        else com.spisokryadom.app.util.UnitChoices.ranked(unitSuggestions, unit)
    }

    Row(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = quantity,
            // Keep the raw draft, including incomplete/invalid input. The owning editor
            // validates before writing; silently rejecting text would restore an old value.
            onValueChange = onQuantityChange,
            label = { Text(quantityLabel) },
            enabled = enabled,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f).then(quantityModifier)
        )
        Spacer(modifier = Modifier.width(8.dp))

        ExposedDropdownMenuBox(
            expanded = unitExpanded && filteredSuggestions.isNotEmpty(),
            onExpandedChange = { if(enabled)unitExpanded = it },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = unit,
                onValueChange = { value ->
                    onUnitChange(value)
                    unitExpanded = true
                },
                label = { Text(unitLabel) },
                enabled = enabled,
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(unitExpanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryEditable)
                    .fillMaxWidth()
                    .then(unitModifier)
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
    allowedTypes:Set<String> = com.spisokryadom.app.util.ProductPurchaseTypes.labels.keys
) {
    DropdownSelector("Тип покупки", com.spisokryadom.app.util.ProductPurchaseTypes.labels.keys.toList(), selectedType,
        { com.spisokryadom.app.util.ProductPurchaseTypes.labels.getValue(it) },
        { if (it != null && it in allowedTypes) onTypeChange(it) },
        modifier = modifier.fillMaxWidth(), allowNone = false,
        itemVector = { when(it){"online"->Icons.Filled.Language;"both"->Icons.Filled.SwapHoriz;else->Icons.Filled.Store} },
        itemEnabled = { it in allowedTypes })
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
