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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.spisokryadom.app.ui.components.ConfirmDialog
import com.spisokryadom.app.util.Quantities

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalListScreen(listId: Long, onBack: () -> Unit, viewModel: GoalListViewModel? = null, onOpenRegular:()->Unit=onBack, onOpenList:(Long)->Unit={},onAddProducts:(()->Unit)?=null,onOpenProduct:((Long)->Unit)?=null) {
 androidx.compose.runtime.CompositionLocalProvider(com.spisokryadom.app.ui.components.LocalFavoriteSurface provides com.spisokryadom.app.util.FavoriteSurface.LISTS) {
    val revision = com.spisokryadom.app.ui.batch.rememberDatabaseRevision()
    key(revision) {
        val model = viewModel ?: androidx.lifecycle.viewmodel.compose.viewModel<GoalListViewModel>(key = "goal-list-$listId-$revision", factory = GoalListViewModel.factory(listId))
        GoalListContent(listId, onBack, model, onOpenRegular, onOpenList,onAddProducts,onOpenProduct)
    }

 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalListContent(listId: Long, onBack: () -> Unit, viewModel: GoalListViewModel, onOpenRegular:()->Unit, onOpenList:(Long)->Unit,onAddProducts:(()->Unit)?,onOpenProduct:((Long)->Unit)?) {
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
    var applySession by rememberSaveable{mutableStateOf<String?>(null)}
    var captureCopy by rememberSaveable{mutableStateOf(false)}
    val snackbar = remember { SnackbarHostState() }
    val file = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) viewModel.writeFile(context.contentResolver, uri) else viewModel.cancelFileExport() }
    BackHandler(enabled = state.selectionMode) { viewModel.selection(false) }
    LaunchedEffect(state.openListId){state.openListId?.let{viewModel.clearNavigation();onOpenList(it)}}
    applySession?.let{session->ListCopySheet(listId,session,{applySession=null}){destination->applySession=null;if(destination==null)onOpenRegular()else onOpenList(destination)}}
    if(captureCopy)com.spisokryadom.app.ui.components.InputDialog("Сохранить копию списка",label="Название списка",onConfirm={captureCopy=false;viewModel.captureList(it)},onDismiss={captureCopy=false})
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() } }
    state.error?.let { GoalErrorDialog(it, viewModel::clearError) }
    state.editor?.let { GoalItemEditorSheet(it, state.shops, state.unitSuggestions, viewModel::editDraft, viewModel::finishEditor) }
    state.shareText?.let { GoalTextSheet(state.list?.name.orEmpty(), it, viewModel::shareText) { viewModel.shareText(null) } }
    if (metadata) state.list?.let { list -> GoalMetadataDialog(list, onSave = { name, visible -> metadata = false; viewModel.metadata(name, visible) }, onDismiss = { metadata = false }) }
    if (delete) ConfirmDialog("Удалить список?", "«${state.list?.name.orEmpty()}» и его позиции будут удалены. Каталог товаров сохранится.", { delete = false; viewModel.deleteList() }, { delete = false })
    if (selectionActions) GoalSelectionActions(state.selectedIds.size, state.rows.any { it.entry.id in state.selectedIds && !it.entry.isBought }, state.rows.any { it.entry.id in state.selectedIds && it.entry.isBought }, { selectionActions = false; viewModel.batchBought(it) }, { selectionActions = false; moveSelection = true }, { selectionActions = false; deleteSelection = true }, { selectionActions = false }, allowMove=true)
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
            IconButton(onClick = viewModel::openShare, enabled = !state.loading && !state.busy && state.list != null) { Icon(Icons.Filled.Share, "Поделиться списком") }
            Box {
                IconButton(onClick = { menu = true }, enabled = state.list != null) { Icon(Icons.Filled.MoreVert, "Действия со списком") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Настройки списка") }, onClick = { menu = false; metadata = true }, leadingIcon = { Icon(Icons.Filled.Settings, null) })
                    DropdownMenuItem(text={Text("Быстрая позиция с ценой")},onClick={menu=false;viewModel.newItem()},leadingIcon={Icon(Icons.Filled.Add,null)})
                    DropdownMenuItem(text = { Text("Экспортировать файл") }, enabled = !state.busy, onClick = { menu = false; viewModel.prepareFile { file.launch(it) } }, leadingIcon = { Icon(Icons.Filled.Upload, null) })
                    DropdownMenuItem(text={Text("Сохранить копию списка")},enabled=!state.busy,onClick={menu=false;captureCopy=true},leadingIcon={Icon(Icons.Filled.ContentCopy,null)})
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Удалить список", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; delete = true }, leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) })
                }
            }
            }
        }) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { if (state.selectionMode) Surface(shadowElevation = 2.dp) { Button(onClick = { selectionActions = true }, enabled = state.selectedIds.isNotEmpty() && !state.busy, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).navigationBarsPadding().testTag("goal-selection-actions")) { Text("Действия · ${state.selectedIds.size}") } } },
        floatingActionButton = { if (!state.selectionMode && state.list != null) FloatingActionButton(onClick = {if(onAddProducts!=null)onAddProducts()else viewModel.newItem()}, modifier = Modifier.testTag("goal-add-item")) { Icon(Icons.Filled.Add, "Добавить позицию") } }
    ) { padding ->
        val groups = remember(state.rows) { goalPurchaseGroups(state.rows) }
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
                        if(!state.selectionMode)Button(onClick={applySession=java.util.UUID.randomUUID().toString()},enabled=!state.busy&&state.rows.isNotEmpty(),modifier=Modifier.fillMaxWidth().testTag("list-open-copy")){Icon(Icons.Filled.PlayArrow,null);Spacer(Modifier.width(8.dp));Text("Добавить позиции списка")}
                        if (!state.selectionMode) OutlinedButton(onClick = {if(onAddProducts!=null)onAddProducts()else viewModel.newItem()}, modifier = Modifier.fillMaxWidth().testTag("goal-open-catalogue")) { Icon(Icons.Filled.Inventory2, null); Spacer(Modifier.width(8.dp)); Text("Добавить товары") }
                    }
                }
                if (state.rows.isEmpty()) item { Text("Список пуст", modifier = Modifier.padding(horizontal = 4.dp, vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                val seenVenues=mutableSetOf<Long>()
                groups.forEach { (key, rows) ->
                    rows.first().venue?.let{venue->if(seenVenues.add(venue.id))item(key="venue-${venue.id}"){
                        Column(Modifier.padding(start=4.dp,end=4.dp,top=14.dp,bottom=2.dp)){
                            Text(venue.name,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.primary)
                            venue.address?.takeIf{it.isNotBlank()}?.let{Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                        }
                    }}
                    item(key = "heading-$key") {
                        val place = rows.first().place
                        val shop = rows.first().shop
                        val venue=rows.first().venue
                        Column(Modifier.goalShopZone(shop?.shoppingBackground.orEmpty()).padding(start = if(venue==null)4.dp else 20.dp, end = 4.dp, top = 10.dp, bottom = 2.dp).testTag("goal-place-$key")) {
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                if(shop!=null)com.spisokryadom.app.ui.components.ShopSymbol(shop.iconKey,shop.logoUri,20.dp)
                                Text(if(venue!=null)shop?.name.orEmpty() else place?.title ?: "Без магазина", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            }
                            (if(venue!=null)rows.first().pointLabel else place?.subtitle)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                    rows.groupBy{it.entry.assignedDepartmentId}.forEach{(departmentId,departmentRows)->
                        departmentRows.first().department?.let{department->item(key="department-$key-$departmentId"){
                            Text(department.name,Modifier.goalShopZone(rows.first().shop?.shoppingBackground.orEmpty()).padding(start=20.dp,top=2.dp,bottom=2.dp),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }}
                        items(departmentRows, key = { "entry-${it.entry.id}" }) { row -> GoalPurchaseCard(row, state.busy, { viewModel.bought(row, it) }, { if(onOpenProduct!=null)onOpenProduct(row.product.id)else viewModel.edit(row) }, { viewModel.edit(row) }, { deleteRowId = row.entry.id }, { viewModel.quantity(row, it) }, { viewModel.urgent(row) }, if (state.selectionMode) row.entry.id in state.selectedIds else null, { viewModel.select(row) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalPurchaseCard(row: GoalPurchaseRow, busy: Boolean, bought: (Boolean) -> Unit, edit: () -> Unit, editPurchase:()->Unit, delete: () -> Unit, quantity: (Double) -> Unit, urgent: () -> Unit, selected: Boolean?, select: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val step = Quantities.step(row.entry.unit, row.product.defaultUnit, row.product.defaultQuantity)
    val cardColor = when {
        selected == true -> MaterialTheme.colorScheme.secondaryContainer
        row.entry.isBought -> MaterialTheme.colorScheme.surfaceVariant
        row.entry.isUrgent -> MaterialTheme.colorScheme.errorContainer
        else -> favoriteBackground(row.product.isFavorite,com.spisokryadom.app.ui.components.purchaseCardColor(row.shop?.shoppingBackground.orEmpty(),row.shop?.purchaseBackground.orEmpty(),MaterialTheme.colorScheme.surface))
    }
    Box(Modifier.goalShopZone(row.shop?.shoppingBackground.orEmpty())){
    OutlinedCard(Modifier.fillMaxWidth().clickable(enabled=!busy,onClick = if (selected != null) select else edit).testTag("goal-entry-${row.entry.id}"), colors = CardDefaults.outlinedCardColors(containerColor = cardColor)) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(selected ?: row.entry.isBought, { if (selected != null) select() else bought(it) }, enabled = !busy, modifier = Modifier.semantics { contentDescription = if (selected != null) "Выбрать: ${row.product.name}" else "Куплено: ${row.product.name}" })
            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (row.entry.isUrgent && !row.entry.isBought) Icon(Icons.Filled.PriorityHigh, "Срочная покупка", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(17.dp))
                    if (row.product.isFavorite&&favoriteStyle().star) Icon(Icons.Filled.Star, "Избранный товар: ${row.product.name}", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                    Text(row.product.name,fontWeight=favoriteWeight(row.product.isFavorite)?:if(row.entry.isUrgent&&!row.entry.isBought)FontWeight.SemiBold else null, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis, textDecoration = if (row.entry.isBought) TextDecoration.LineThrough else TextDecoration.None,color=if(row.entry.isBought)MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                }
                listOfNotNull(row.entry.note, row.entry.comment).filter { it.isNotBlank() }.distinct().forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if(selected==null)com.spisokryadom.app.ui.components.PurchaseQuantityStepper("${goalNumber(row.entry.quantity)} ${row.entry.unit}".trim(),row.entry.isBought,row.entry.quantity>step,{quantity(-step)},{quantity(step)},enabled=!busy,decreaseLabel="Уменьшить: ${row.product.name}",increaseLabel="Увеличить: ${row.product.name}")
            if (selected == null) Box {
                IconButton(onClick = { menu = true },enabled=!busy) { Icon(Icons.Filled.MoreVert, "Действия: ${row.product.name}") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Карточка товара") }, onClick = { menu = false; edit() })
                    DropdownMenuItem(text = { Text("Количество, цена и заметка") }, onClick = { menu = false; editPurchase() })
                    if (!row.entry.isBought) DropdownMenuItem(text = { Text(if (row.entry.isUrgent) "Убрать срочность" else "Сделать срочным") }, onClick = { menu = false; urgent() })
                    DropdownMenuItem(text = { Text("Удалить", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; delete() })
                }
            }
        }
        if(row.total!=null)Text("${goalMoney(row.entry.plannedUnitPrice!!)} / ед. · ${goalMoney(row.total!!)}",Modifier.padding(start=58.dp,end=12.dp,bottom=8.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    }
}

@Composable
private fun GoalMetadataDialog(list: GoalListEntity, onSave: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable(list.id) { mutableStateOf(list.name) }
    var visible by rememberSaveable(list.id) { mutableStateOf(list.showInPurchases) }
    com.spisokryadom.app.ui.components.EditorSheet("Настройки списка",onDismiss=onDismiss,actions={
        TextButton(onClick=onDismiss){Text("Отмена")}
        Button(onClick={onSave(name,visible)},enabled=name.isNotBlank()){Text("Готово")}
    }) {
            OutlinedTextField(name, { name = it }, label = { Text("Название списка") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().clickable { visible = !visible }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Отображать на Главной", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(visible, { visible = it })
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalItemEditorSheet(editor: GoalItemEditor, shops: List<String>, units: List<String>, update: ((GoalItemEditor) -> GoalItemEditor) -> Unit, finish: () -> Unit) {
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
                com.spisokryadom.app.ui.components.QuantityUnitInputWithSuggestions(editor.quantity,editor.unit,
                    {value->update{it.copy(quantity=value)}},{value->update{it.copy(unit=value)}},units,
                    quantityModifier=Modifier.testTag("goal-item-quantity"),unitModifier=Modifier.testTag("goal-item-unit"))
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

/** Length-prefixed raw fields keep different portable places separate even when labels match. */
internal fun goalCustomPlaceKey(name: String, address: String): String = "custom-${name.length}:$name-${address.length}:$address"

/** The same retailer/point and department priorities used by Main; bought rows stay in their shop. */
internal fun goalPurchaseGroups(rows:List<GoalPurchaseRow>):Map<String,List<GoalPurchaseRow>> = rows
    .sortedWith(compareBy<GoalPurchaseRow>{it.entry.assignedShopId==null}.thenBy{it.shop?.displayOrder?:Int.MAX_VALUE}
        .thenBy{it.entry.assignedShopId}.thenBy{it.branchPriority}.thenBy{it.entry.assignedBranchId}
        .thenBy{it.entry.customShopName}.thenBy{it.entry.customShopAddress}
        .thenBy{it.department?.displayOrder?:Int.MAX_VALUE}.thenBy{it.entry.assignedDepartmentId}
        .thenBy{it.entry.isBought}.thenByDescending{it.entry.isUrgent&&!it.entry.isBought}
        .thenBy{it.product.name.lowercase(java.util.Locale.ROOT)}.thenBy{it.entry.id})
    .groupBy{row->row.entry.assignedShopId?.let{"shop-$it-point-${row.entry.assignedBranchId}"}
        ?:goalCustomPlaceKey(row.entry.customShopName.orEmpty(),row.entry.customShopAddress.orEmpty())}
    .entries.groupBy{group->group.value.first().venue?.id?.let{"venue-$it"}?:"place-${group.key}"}
    .values.flatten().associate{it.key to it.value}

@Composable
private fun Modifier.goalShopZone(colorKey:String):Modifier {
    val color=com.spisokryadom.app.ui.components.shoppingBlockColor(colorKey,MaterialTheme.colorScheme.surface)
    return fillMaxWidth().then(if(color==null)Modifier else Modifier.drawBehind{
        val gap=4.dp.toPx();drawRect(color,Offset(0f,-gap),Size(size.width,size.height+gap*2))
    }.padding(horizontal=6.dp))
}
