package com.spisokryadom.app.ui.goallists

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import com.spisokryadom.app.ui.components.favoriteStyle
import com.spisokryadom.app.ui.components.favoriteWeight
import com.spisokryadom.app.ui.components.favoriteBackground
import com.spisokryadom.app.ui.components.favoriteSurface
import com.spisokryadom.app.ui.components.FavoriteBlock
import com.spisokryadom.app.ui.components.LocalFavoriteBlock
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spisokryadom.app.data.entity.GoalListEntity
import com.spisokryadom.app.data.entity.ProductEntity
import com.spisokryadom.app.ui.components.ConfirmDialog
import com.spisokryadom.app.util.Quantities

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalListScreen(listId: Long, onBack: () -> Unit, viewModel: GoalListViewModel? = null) {
    val revision = com.spisokryadom.app.ui.batch.rememberDatabaseRevision()
    key(revision) {
        val model = viewModel ?: androidx.lifecycle.viewmodel.compose.viewModel<GoalListViewModel>(key = "goal-list-$listId-$revision", factory = GoalListViewModel.factory(listId))
        GoalListContent(listId, onBack, model)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalListContent(listId: Long, onBack: () -> Unit, viewModel: GoalListViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP) { viewModel.flushEditor() }
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var metadata by rememberSaveable { mutableStateOf(false) }
    var delete by rememberSaveable { mutableStateOf(false) }
    var deleteRowId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectionActions by rememberSaveable { mutableStateOf(false) }
    var moveSelection by rememberSaveable { mutableStateOf(false) }
    var deleteSelection by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val file = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) viewModel.writeFile(context.contentResolver, uri) else viewModel.cancelFileExport() }
    BackHandler(enabled = state.selectionMode) { viewModel.selection(false) }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() } }
    state.error?.let { GoalErrorDialog(it, viewModel::clearError) }
    state.editor?.let { GoalItemEditorSheet(it, state.shops, viewModel::editDraft, viewModel::finishEditor) }
    if (state.catalogueVisible) GoalCatalogueSheet(state, viewModel::catalogueQuery, viewModel::cataloguePriority, viewModel::addCatalogue, { viewModel.showCatalogue(false) })
    state.shareText?.let { GoalTextSheet(state.list?.name.orEmpty(), it, viewModel::shareText) { viewModel.shareText(null) } }
    if (metadata) state.list?.let { list -> GoalMetadataDialog(list, onSave = { name, visible -> metadata = false; viewModel.metadata(name, visible) }, onDismiss = { metadata = false }) }
    if (delete) ConfirmDialog("Удалить список?", "«${state.list?.name.orEmpty()}» и его покупки будут удалены. Каталог товаров сохранится.", { delete = false; viewModel.deleteList() }, { delete = false })
    if (selectionActions) GoalSelectionActions(state.selectedIds.size, state.rows.any { it.entry.id in state.selectedIds && !it.entry.isBought }, state.rows.any { it.entry.id in state.selectedIds && it.entry.isBought }, { selectionActions = false; viewModel.batchBought(it) }, { selectionActions = false; moveSelection = true }, { selectionActions = false; deleteSelection = true }, { selectionActions = false })
    if (moveSelection) GoalMoveSelectionDialog(listId, state.allLists, { moveSelection = false; viewModel.moveSelected(it) }, { moveSelection = false })
    if (deleteSelection) ConfirmDialog("Удалить выбранное?", "Позиций: ${state.selectedIds.size}", { deleteSelection = false; viewModel.batchDelete() }, { deleteSelection = false })
    deleteRowId?.let { id -> state.rows.find { it.entry.id == id }?.let { row ->
        ConfirmDialog("Удалить позицию?", row.product.name, { deleteRowId = null; viewModel.remove(row) }, { deleteRowId = null })
    } }
    Scaffold(
        topBar = { TopAppBar(title = { Text(if (state.selectionMode) "Выбрано: ${state.selectedIds.size}" else state.list?.name ?: "Список", maxLines = 1, overflow = TextOverflow.Ellipsis) }, navigationIcon = {
            IconButton(onClick = { if (state.selectionMode) viewModel.selection(false) else onBack() }) { Icon(if (state.selectionMode) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack, if (state.selectionMode) "Завершить выбор" else "Назад") }
        }, actions = {
            if (state.selectionMode) {
                IconButton(onClick = viewModel::selectAll, enabled = !state.busy) { Icon(Icons.Filled.DoneAll, "Выбрать всё") }
            } else {
            IconButton(onClick = { viewModel.selection(true) }, enabled = state.rows.isNotEmpty()) { Icon(Icons.Filled.Checklist, "Выбрать позиции") }
            IconButton(onClick = viewModel::openShare, enabled = !state.loading && !state.busy && state.list != null) { Icon(Icons.Filled.Share, "Поделиться целевым списком") }
            Box {
                IconButton(onClick = { menu = true }, enabled = state.list != null) { Icon(Icons.Filled.MoreVert, "Действия со списком") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Настройки списка") }, onClick = { menu = false; metadata = true }, leadingIcon = { Icon(Icons.Filled.Settings, null) })
                    DropdownMenuItem(text = { Text("Экспортировать файл") }, enabled = !state.busy, onClick = { menu = false; viewModel.prepareFile { file.launch(it) } }, leadingIcon = { Icon(Icons.Filled.Upload, null) })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Удалить список", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; delete = true }, leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) })
                }
            }
            }
        }) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { if (state.selectionMode) Surface(shadowElevation = 2.dp) { Button(onClick = { selectionActions = true }, enabled = state.selectedIds.isNotEmpty() && !state.busy, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).navigationBarsPadding().testTag("goal-selection-actions")) { Text("Действия · ${state.selectedIds.size}") } } },
        floatingActionButton = { if (!state.selectionMode && state.list != null) FloatingActionButton(onClick = viewModel::newItem, modifier = Modifier.testTag("goal-add-item")) { Icon(Icons.Filled.Add, "Добавить позицию") } }
    ) { padding ->
        val groups = remember(state.rows) { state.rows.sortedWith(compareBy<GoalPurchaseRow> { it.entry.isBought }.thenBy { it.entry.createdAt }.thenBy { it.entry.id }).groupBy {
            (it.entry.assignedShopId?.let { id -> "shop-$id-point-${it.entry.assignedBranchId}" } ?: goalCustomPlaceKey(it.entry.customShopName.orEmpty(), it.entry.customShopAddress.orEmpty())) to it.entry.isBought
        } }
        LazyColumn(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).testTag("goal-items"), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (!state.loading && state.list == null) item { Text("Список удалён", modifier = Modifier.padding(12.dp)) }
            if (state.list != null) {
                item(key = "summary") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${state.rows.count { !it.entry.isBought }} к покупке · ${state.rows.count { it.entry.isBought }} куплено", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.rows.any { it.entry.plannedUnitPrice != null }) {
                            Text("Ориентировочно: ${goalMoney(state.plannedTotal)}", style = MaterialTheme.typography.titleMedium)
                            Text("Осталось: ${goalMoney(state.remainingTotal)} · Куплено: ${goalMoney(state.boughtTotal)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (state.unpricedCount > 0) Text("Без цены: ${state.unpricedCount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!state.selectionMode) OutlinedButton(onClick = { viewModel.showCatalogue(true) }, modifier = Modifier.fillMaxWidth().testTag("goal-open-catalogue")) { Icon(Icons.Filled.Inventory2, null); Spacer(Modifier.width(8.dp)); Text("Добавить из базы товаров") }
                    }
                }
                if (state.rows.isEmpty()) item { Text("Список пуст", modifier = Modifier.padding(horizontal = 4.dp, vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                groups.forEach { (key, rows) ->
                    item(key = "heading-${key.first}-${key.second}") {
                        val place = rows.first().place
                        Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 2.dp)) {
                            Text(if (key.second) "Куплено${place?.title?.let { " · $it" }.orEmpty()}" else place?.title ?: "Без магазина", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            place?.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                    items(rows, key = { "entry-${it.entry.id}" }) { row -> GoalPurchaseCard(row, state.busy, { viewModel.bought(row, it) }, { viewModel.edit(row) }, { deleteRowId = row.entry.id }, { viewModel.quantity(row, it) }, { viewModel.urgent(row) }, if (state.selectionMode) row.entry.id in state.selectedIds else null, { viewModel.select(row) }) }
                }
            }
        }
    }
}

@Composable
private fun GoalPurchaseCard(row: GoalPurchaseRow, busy: Boolean, bought: (Boolean) -> Unit, edit: () -> Unit, delete: () -> Unit, quantity: (Double) -> Unit, urgent: () -> Unit, selected: Boolean?, select: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val step = Quantities.step(row.entry.unit, row.product.defaultUnit, row.product.defaultQuantity)
    OutlinedCard(Modifier.fillMaxWidth().clickable(onClick = if (selected != null) select else edit).testTag("goal-entry-${row.entry.id}"), colors = CardDefaults.outlinedCardColors(containerColor = if (selected == true) MaterialTheme.colorScheme.secondaryContainer else if (row.entry.isBought) MaterialTheme.colorScheme.surfaceContainerLow else favoriteBackground(row.product.isFavorite))) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(selected ?: row.entry.isBought, { if (selected != null) select() else bought(it) }, enabled = !busy, modifier = Modifier.semantics { contentDescription = if (selected != null) "Выбрать: ${row.product.name}" else "Куплено: ${row.product.name}" })
            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (row.entry.isUrgent && !row.entry.isBought) Icon(Icons.Filled.PriorityHigh, "Срочная покупка", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(17.dp))
                    if (row.product.isFavorite&&favoriteStyle().star) Icon(Icons.Filled.Star, "Избранный товар: ${row.product.name}", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                    Text(row.product.name,fontWeight=favoriteWeight(row.product.isFavorite), style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis, textDecoration = if (row.entry.isBought) TextDecoration.LineThrough else TextDecoration.None)
                }
                listOfNotNull(row.entry.note, row.entry.comment).filter { it.isNotBlank() }.distinct().forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (selected == null) Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Действия: ${row.product.name}") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Изменить") }, onClick = { menu = false; edit() })
                    if (!row.entry.isBought) DropdownMenuItem(text = { Text(if (row.entry.isUrgent) "Убрать срочность" else "Сделать срочным") }, onClick = { menu = false; urgent() })
                    DropdownMenuItem(text = { Text("Удалить", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; delete() })
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 64.dp, end = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                row.entry.plannedUnitPrice?.let { Text("${goalMoney(it)} / ед.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                row.total?.let { Text(goalMoney(it), style = MaterialTheme.typography.labelLarge) }
            }
            if (selected == null) IconButton(onClick = { quantity(-step) }, enabled = !busy && !row.entry.isBought && row.entry.quantity > step) { Icon(Icons.Filled.Remove, "Уменьшить: ${row.product.name}", modifier = Modifier.size(20.dp)) }
            Text("${goalNumber(row.entry.quantity)} ${row.entry.unit}".trim(), style = MaterialTheme.typography.labelLarge, modifier = Modifier.widthIn(max = 120.dp), maxLines = 2)
            if (selected == null) IconButton(onClick = { quantity(step) }, enabled = !busy && !row.entry.isBought) { Icon(Icons.Filled.Add, "Увеличить: ${row.product.name}", modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun GoalMetadataDialog(list: GoalListEntity, onSave: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable(list.id) { mutableStateOf(list.name) }
    var visible by rememberSaveable(list.id) { mutableStateOf(list.showInPurchases) }
    val width = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp - 48).coerceIn(240, 560).dp
    AlertDialog(modifier = Modifier.width(width), properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false), onDismissRequest = { if (name.isNotBlank()) onSave(name, visible) else onDismiss() }, title = { Text("Настройки списка") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Название списка") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().clickable { visible = !visible }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Отображать в текущих покупках", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(visible, { visible = it })
            }
        }
    }, confirmButton = { TextButton(onClick = { onSave(name, visible) }, enabled = name.isNotBlank()) { Text("Готово") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalItemEditorSheet(editor: GoalItemEditor, shops: List<String>, update: ((GoalItemEditor) -> GoalItemEditor) -> Unit, finish: () -> Unit) {
    var suggestions by remember { mutableStateOf(false) }
    val latestFinish by rememberUpdatedState(finish)
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { target ->
        if (target == SheetValue.Hidden) { latestFinish(); false } else true
    })
    ModalBottomSheet(onDismissRequest = finish, sheetState = sheet) {
        BackHandler { latestFinish() }
        Column(Modifier.fillMaxWidth().fillMaxHeight(.95f).imePadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (editor.entryId == null) "Новая позиция" else "Позиция списка", style = MaterialTheme.typography.headlineSmall)
            editor.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(editor.name, { value -> update { it.copy(name = value) } }, readOnly = !editor.editableName, label = { Text("Название") }, modifier = Modifier.fillMaxWidth().testTag("goal-item-name"), maxLines = 3)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(editor.quantity, { value -> update { it.copy(quantity = value) } }, label = { Text("Количество") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f).testTag("goal-item-quantity"), singleLine = true)
                    OutlinedTextField(editor.unit, { value -> update { it.copy(unit = value) } }, label = { Text("Ед. изм.") }, modifier = Modifier.weight(1f).testTag("goal-item-unit"), singleLine = true)
                }
                OutlinedTextField(editor.price, { value -> update { it.copy(price = value) } }, label = { Text("Цена за единицу, ₽") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("goal-item-price"), singleLine = true)
                ExposedDropdownMenuBox(expanded = suggestions && shops.isNotEmpty(), onExpandedChange = { suggestions = it }) {
                    OutlinedTextField(editor.shop, { value -> update { it.copy(shop = value) }; suggestions = false }, label = { Text("Магазин (необязательно)") }, modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable).testTag("goal-item-shop"), singleLine = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(suggestions) })
                    ExposedDropdownMenu(suggestions && shops.isNotEmpty(), { suggestions = false }) {
                        shops.filter { editor.shop.isBlank() || it.contains(editor.shop, true) }.forEach { name -> DropdownMenuItem(text = { Text(name) }, onClick = { update { it.copy(shop = name) }; suggestions = false }) }
                        DropdownMenuItem(text = { Text("Без магазина") }, onClick = { update { it.copy(shop = "", address = "") }; suggestions = false })
                    }
                }
                OutlinedTextField(editor.address, { value -> update { it.copy(address = value) } }, label = { Text("Адрес (необязательно)") }, modifier = Modifier.fillMaxWidth().testTag("goal-item-address"), maxLines = 3)
                OutlinedTextField(editor.note, { value -> update { it.copy(note = value) } }, label = { Text("Заметка") }, modifier = Modifier.fillMaxWidth().testTag("goal-item-note"), minLines = 2, maxLines = 5)
                OutlinedTextField(editor.comment, { value -> update { it.copy(comment = value) } }, label = { Text("Комментарий к покупке") }, modifier = Modifier.fillMaxWidth().testTag("goal-item-comment"), minLines = 2, maxLines = 5)
                if (editor.saving) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("goal-item-saving"))
            }
            Button(onClick = finish, modifier = Modifier.fillMaxWidth().testTag("goal-item-done")) { Text("Готово") }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalCatalogueSheet(state: GoalListUiState, query: (String) -> Unit, priority: (Boolean) -> Unit, add: (ProductEntity) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.95f).imePadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Из базы товаров", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(state.catalogueQuery, query, label = { Text("Поиск по названию") }, modifier = Modifier.fillMaxWidth().testTag("goal-catalogue-search"), singleLine = true, leadingIcon = { Icon(Icons.Filled.Search, null) }, trailingIcon = { com.spisokryadom.app.ui.components.VoiceSearchButton(query) })
            Row(Modifier.fillMaxWidth().clickable { priority(!state.catalogueUsePriority) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("По приоритету магазинов", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(state.catalogueUsePriority, priority)
            }
            state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("goal-catalogue"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (state.catalogueQuery.isBlank() && state.catalogue.any { it.isFavorite }) {
                    item { FavoriteBlock(Modifier.fillMaxWidth()){Text("Избранные", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 4.dp))} }
                    items(state.catalogue.filter { it.isFavorite }, key = { "favorite-${it.id}" }) { product -> FavoriteBlock(Modifier.fillMaxWidth()){GoalCatalogueRow(product, state.busy, { add(product) }, "goal-catalogue-favorite-${product.id}")} }
                    item { Text("Все товары", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)) }
                }
                items(state.catalogue, key = { "all-${it.id}" }) { product -> GoalCatalogueRow(product, state.busy, { add(product) }, "goal-catalogue-all-${product.id}") }
                if (state.catalogue.isEmpty()) item { Text("Товары не найдены", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp)) }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Готово") }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun GoalCatalogueRow(product: ProductEntity, busy: Boolean, add: () -> Unit, tag: String) {
    OutlinedCard(Modifier.fillMaxWidth().clickable(enabled = !busy, onClick = add).testTag(tag),colors=CardDefaults.outlinedCardColors(containerColor=favoriteBackground(product.isFavorite))) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (product.isFavorite&&favoriteStyle().star) Icon(Icons.Filled.Star, "Избранный товар: ${product.name}", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
            Column(Modifier.weight(1f)) {
                Text(product.name,fontWeight=favoriteWeight(product.isFavorite), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${goalNumber(product.defaultQuantity?.takeIf { it > 0 } ?: 1.0)} ${product.defaultUnit.orEmpty()}".trim(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = add, enabled = !busy) { Icon(Icons.Filled.AddShoppingCart, "Добавить в целевой список: ${product.name}") }
        }
    }
}

/** Length-prefixed raw fields keep different portable places separate even when labels match. */
internal fun goalCustomPlaceKey(name: String, address: String): String = "custom-${name.length}:$name-${address.length}:$address"
