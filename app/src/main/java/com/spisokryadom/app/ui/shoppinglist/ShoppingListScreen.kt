package com.spisokryadom.app.ui.shoppinglist

import com.spisokryadom.app.ui.batch.*
import com.spisokryadom.app.data.repository.PurchaseBatchChange
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import com.spisokryadom.app.ui.components.favoriteStyle
import com.spisokryadom.app.ui.components.favoriteWeight
import com.spisokryadom.app.ui.components.favoriteBackground
import com.spisokryadom.app.ui.components.favoriteSurface
import com.spisokryadom.app.ui.components.FavoriteBlock
import com.spisokryadom.app.ui.components.LocalFavoriteBlock
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spisokryadom.app.data.entity.ShopEntity
import com.spisokryadom.app.ui.components.ConfirmDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(
    onNavigateToAddToList: () -> Unit,
    onNavigateToAddToShop: (Long,Long?) -> Unit = {_,_->},
    onNavigateToProductCard: (Long) -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToBulk: () -> Unit = {},
    viewModel: ShoppingListViewModel = viewModel(factory = ShoppingListViewModel.Factory),
    batchVM:BatchViewModel = viewModel(factory=BatchViewModel.Factory),
    onNavigateToGoalList:(Long)->Unit={}
) {
 androidx.compose.runtime.CompositionLocalProvider(com.spisokryadom.app.ui.components.LocalFavoriteSurface provides com.spisokryadom.app.util.FavoriteSurface.PURCHASES) {
    // A full replacement can reuse numeric IDs for different records. No old
    // selection or open purchase action may survive that database revision.
    val revision=rememberDatabaseRevision()
    androidx.compose.runtime.key(revision){
        ShoppingListContent(onNavigateToAddToList,onNavigateToAddToShop,onNavigateToProductCard,
            onNavigateToSettings,onNavigateToBulk,viewModel,batchVM,onNavigateToGoalList)
    }

 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShoppingListContent(
    onNavigateToAddToList: () -> Unit,
    onNavigateToAddToShop: (Long,Long?) -> Unit = {_,_->},
    onNavigateToProductCard: (Long) -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToBulk: () -> Unit = {},
    viewModel: ShoppingListViewModel = viewModel(factory = ShoppingListViewModel.Factory),
    batchVM:BatchViewModel = viewModel(factory=BatchViewModel.Factory),
    onNavigateToGoalList:(Long)->Unit={}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val expansion=com.spisokryadom.app.ui.components.rememberGroups("shopping_list_prefs","collapsed_other_groups")
    var selectionMode by rememberSaveable{mutableStateOf(false)}
    var selectedIds by rememberSaveable{mutableStateOf(emptyList<Long>())}
    var moveIds by remember{mutableStateOf<Set<Long>?>(null)}
    var moveListOwners by remember{mutableStateOf<Map<Long,Long?>?>(null)}
    moveListOwners?.let{owners->com.spisokryadom.app.ui.components.MoveToListSheet(owners,
        onDone={moveListOwners=null;selectedIds=emptyList();selectionMode=false;batchVM.notice.value="Покупки перенесены"},onDismiss={moveListOwners=null})}
    val pendingIds=state.groups.flatMap{it.entries}.filter{!it.entry.isBought}.map{it.entry.id}.toSet()
    val allIds=state.groups.flatMap{it.entries}.map{it.entry.id}.toSet()
    val effectiveSelection=selectedIds.toSet().intersect(allIds)
    LaunchedEffect(allIds){selectedIds=selectedIds.filter{it in allIds}}
    BackHandler(selectionMode){selectionMode=false;selectedIds=emptyList()}
    var actionsFor by remember{mutableStateOf<Set<Long>?>(null)}
    var deleteBatch by remember{mutableStateOf<Set<Long>?>(null)}
    fun batchDone(){actionsFor=null;selectedIds=emptyList();selectionMode=false}
    deleteBatch?.let{ids->ConfirmDialog("Удалить из списка?","Будет удалено покупок: ${ids.size}. Товары из базы сохранятся.",onConfirm={batchVM.purchases(ids,PurchaseBatchChange.DELETE){deleteBatch=null;batchDone()}},onDismiss={deleteBatch=null})}
    actionsFor?.let{ids->
        androidx.compose.material3.ModalBottomSheet(onDismissRequest={actionsFor=null},sheetState=androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded=true)){
            val selected=state.groups.flatMap{it.entries}.filter{it.entry.id in ids}.distinctBy{it.entry.id}
            val pendingOnly=selected.isNotEmpty()&&selected.none{it.entry.isBought}
            val busy by batchVM.busy.collectAsStateWithLifecycle()
            Column(Modifier.fillMaxWidth().padding(20.dp).verticalScroll(rememberScrollState())){
                Text("Выбрано: ${ids.size}",style=MaterialTheme.typography.titleLarge)
                if(selected.any{!it.entry.isBought})TextButton(onClick={batchVM.purchases(ids,PurchaseBatchChange.BOUGHT,::batchDone)},enabled=!busy){Text("Отметить купленными")}
                if(selected.any{it.entry.isBought})TextButton(onClick={batchVM.purchases(ids,PurchaseBatchChange.RETURN,::batchDone)},enabled=!busy){Text("Вернуть в покупки")}
                if(pendingOnly){
                    TextButton(onClick={actionsFor=null;moveIds=ids},enabled=!busy){Text("Изменить место покупки")}
                    TextButton(onClick={batchVM.purchases(ids,PurchaseBatchChange.URGENT,::batchDone)},enabled=!busy){Text("Сделать срочными")}
                    TextButton(onClick={batchVM.purchases(ids,PurchaseBatchChange.NOT_URGENT,::batchDone)},enabled=!busy){Text("Убрать срочность")}
                }
                TextButton(onClick={moveListOwners=selected.associate{it.entry.id to it.entry.listId};actionsFor=null},enabled=!busy&&selected.isNotEmpty()){Text("Перенести в другой список")}
                TextButton(onClick={actionsFor=null;deleteBatch=ids},enabled=!busy){Text("Удалить из списка",color=MaterialTheme.colorScheme.error)}
            }
        }
    }
    fun select(id:Long){selectedIds=if(id in selectedIds)selectedIds-id else selectedIds+id}
    fun selectSection(ids:Set<Long>){
        selectedIds=if(effectiveSelection.containsAll(ids))selectedIds.filter{it !in ids}else (selectedIds+ids).distinct()
    }
    moveIds?.let { ids ->
        com.spisokryadom.app.ui.components.MovePurchasesDialog(ids,
            onMove={shop,branch,result->viewModel.movePurchases(ids,shop,branch){success->if(success){selectedIds=emptyList();selectionMode=false};result(success)}},
            onDismiss={moveIds=null},loadPlaces=viewModel::purchasePlaces,
            loadAdditionalPlaces=viewModel::additionalPurchasePlaces,onCreateShop=viewModel::createMoveShop,
            onAddAndMove={shop,branch,result->viewModel.addPlaceAndMove(ids,shop,branch){success->if(success){selectedIds=emptyList();selectionMode=false};result(success)}},
            currentPlaces=state.groups.flatMap{it.entries}.filter{it.entry.id in ids}.map{it.entry.assignedShopId to it.entry.assignedBranchId}.toSet())
    }
    var copyFor by remember { mutableStateOf<ShoppingListEntryUi?>(null) }
    copyFor?.let { row ->
        com.spisokryadom.app.ui.components.CopyProductShopSheet(row.entry.productId,{copyFor=null},viewModel::unlinkedShops,
            onCopy={shop,result->viewModel.copyToShop(row.entry.productId,shop,result)})
    }
    val listState = rememberLazyListState()
    val viewport=com.spisokryadom.app.ui.components.rememberReorderViewport(listState,state.groups.map{it.placeKey})
    var showClearDialog by remember { mutableStateOf(false) }
    var showEditEntryFor by remember { mutableStateOf<ShoppingListEntryUi?>(null) }
    var showContextMenuFor by remember { mutableStateOf<ShoppingListEntryUi?>(null) }
    var showDeleteConfirmFor by remember { mutableStateOf<ShoppingListEntryUi?>(null) }

    state.error?.let{message->androidx.compose.material3.AlertDialog(onDismissRequest=viewModel::clearError,title={Text("Не удалось выполнить действие")},text={Text(message)},confirmButton={TextButton(onClick=viewModel::clearError){Text("Понятно")}})}
    showEditEntryFor?.let { entryUi ->
        EditEntryDialog(
            entryUi = entryUi,
            onSave = { qty, note, comment ->
                viewModel.updatePurchaseDetails(entryUi.entry.id, qty, note, comment)
                showEditEntryFor = null
            },
            onDismiss = { showEditEntryFor = null }
        )
    }

    showContextMenuFor?.let { entryUi ->
        EntryContextMenuDialog(
            entryUi = entryUi,
            onOpenProductCard = {
                showContextMenuFor = null
                onNavigateToProductCard(entryUi.entry.productId)
            },
            onChangeShop = {
                showContextMenuFor = null
                moveIds = setOf(entryUi.entry.id)
            },
            onCopy={showContextMenuFor=null;copyFor=entryUi},
            onEditPurchase={showContextMenuFor=null;showEditEntryFor=entryUi},
            onToggleUrgent = {
                viewModel.toggleUrgent(entryUi.entry.id)
                showContextMenuFor = null
            },
            onRemove = {
                showContextMenuFor = null
                showDeleteConfirmFor = entryUi
            },
            onMoveList={moveListOwners=mapOf(entryUi.entry.id to entryUi.entry.listId);showContextMenuFor=null},
            onOpenList=entryUi.entry.listId?.let{id->{showContextMenuFor=null;onNavigateToGoalList(id)}},
            onDismiss = { showContextMenuFor = null }
        )
    }

    if (showClearDialog) {
        ConfirmDialog(
            title = "Очистить купленные",
            message = "Очистить все купленные записи из текущего списка?",
            onConfirm = {
                viewModel.clearBought()
                showClearDialog = false
            },
            onDismiss = { showClearDialog = false }
        )
    }

    showDeleteConfirmFor?.let { entryUi ->
        ConfirmDialog(
            title = "Удалить из списка",
            message = "Удалить «${entryUi.productName}» из списка покупок?",
            onConfirm = {
                viewModel.removeEntry(entryUi.entry.id)
                showDeleteConfirmFor = null
            },
            onDismiss = { showDeleteConfirmFor = null }
        )
    }

    Scaffold(
        snackbarHost={BatchFeedback(batchVM)},
        topBar = {
            TopAppBar(
                title = { Text(if(selectionMode)"Выбрано: ${effectiveSelection.size}"else "Список покупок", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                navigationIcon={if(selectionMode)IconButton(onClick={selectionMode=false;selectedIds=emptyList()}){Icon(Icons.Filled.Close,"Отменить выбор")}},
                actions = {
                    if(selectionMode) {
                        IconButton(onClick={selectedIds=if(effectiveSelection==allIds)emptyList()else allIds.toList()}){Icon(Icons.Filled.DoneAll,if(effectiveSelection==allIds)"Снять весь выбор"else "Выбрать все покупки")}
                        IconButton(onClick={moveIds=effectiveSelection},enabled=effectiveSelection.isNotEmpty()&&pendingIds.containsAll(effectiveSelection)){Icon(Icons.Filled.SwapHoriz,"Переместить выбранные")}
                        IconButton(onClick={actionsFor=effectiveSelection},enabled=effectiveSelection.isNotEmpty()){Icon(Icons.Filled.MoreVert,"Действия с выбранными")}
                    } else if(allIds.isNotEmpty())IconButton(onClick={selectionMode=true}){Icon(Icons.Filled.Checklist,"Выбрать покупки")}
                    if(!selectionMode)IconButton(onClick=onNavigateToBulk){Icon(Icons.AutoMirrored.Filled.Notes,"Текстовый список")}
                    if (!selectionMode&&state.hasBoughtEntries) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                Icons.Filled.DeleteSweep,
                                contentDescription = "Очистить купленные"
                            )
                        }
                    }
                    if(!selectionMode)IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Настройки")
                    }
                }
            )
        },
        floatingActionButton = {
            if(!selectionMode)com.spisokryadom.app.ui.components.ShoppingAddButton(
                onAdd = onNavigateToAddToList
            )
        }
    ) { padding ->
        val totalEntries = state.totalCount
        val isEmpty = totalEntries == 0 && !state.isLoading

        if (isEmpty) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Icon(Icons.Filled.AddShoppingCart, null, Modifier.padding(20.dp).size(32.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "Список пуст",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp).padding(bottom=80.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // View mode switcher
                item(key = "view_mode_switcher", contentType = "switcher") {
                    ViewModeSwitcher(
                        currentMode = state.viewMode,
                        onModeSelected = { viewModel.setViewMode(it) }
                    )
                }

                // Summary line
                if (totalEntries > 0) {
                    item(key = "summary", contentType = "summary") {
                        ListSummary(totalCount = totalEntries, boughtCount = state.boughtCount)
                    }
                }

                // Entry groups
                val showGroupHeaders = state.viewMode != ViewMode.FLAT
                val isByShops = state.viewMode == ViewMode.BY_SHOPS
                if(state.foreignPendingCount>0)item(key="foreign_notice"){
                    Column{Text("В другом городе: ${state.foreignPendingCount} покупок. Они сохранены ниже.");TextButton(onClick={viewModel.movePendingToCurrentCity()}){Text("Подобрать магазины этого города")}}
                }
                state.groups.forEachIndexed { groupIndex, rawGroup ->
                    val groupKey="${state.viewMode}_${if(isByShops)rawGroup.placeKey else rawGroup.groupId}"
                    val group=if(isByShops)rawGroup else rawGroup.copy(isCollapsed=groupKey in expansion.collapsed,showUrgentWhenCollapsed=false,totalCount=rawGroup.entries.size,boughtCount=rawGroup.entries.count{it.entry.isBought})
                    val backgroundKey=if(isByShops)group.shoppingBackground else ""
                    val venueKey="venue_${rawGroup.venueId}"
                    if(isByShops&&rawGroup.venueId!=null&&state.groups.getOrNull(groupIndex-1)?.venueId!=rawGroup.venueId){
                        item(key=venueKey,contentType="venue_header"){
                            Row(Modifier.fillMaxWidth().clickable{expansion.toggle(venueKey)}.padding(top=16.dp,bottom=6.dp),verticalAlignment=Alignment.CenterVertically){
                                if(selectionMode){
                                    val ids=state.groups.filter{it.venueId==rawGroup.venueId}.flatMap{it.entries}.map{it.entry.id}.toSet()
                                    PurchaseSectionToggle(ids,effectiveSelection,{selectSection(ids)},rawGroup.venueName.orEmpty())
                                }
                                com.spisokryadom.app.ui.components.CollapseChevron(venueKey in expansion.collapsed,"Рынок или ТЦ: ${rawGroup.venueName}")
                                Column(Modifier.weight(1f).padding(start=8.dp)){Text(rawGroup.venueName.orEmpty(),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary);rawGroup.venueAddress?.takeIf{it.isNotBlank()}?.let{Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
                                Text(state.groups.filter{it.venueId==rawGroup.venueId}.sumOf{it.totalCount-it.boughtCount}.toString(),style=MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    if(!isByShops||rawGroup.venueId==null||venueKey !in expansion.collapsed){
                    if(isByShops)item(key="place_gap_${group.placeKey}",contentType="place_gap"){
                        Spacer(Modifier.height(if(group.venueId!=null)8.dp else 14.dp))
                    }
                    if (showGroupHeaders) {
                        item(key = "header_${state.viewMode}_${if(isByShops)group.placeKey else group.groupId}", contentType = "group_header") {
                            EntryGroupHeader(
                                placementModifier=Modifier.animateItem(fadeInSpec=androidx.compose.animation.core.tween(220),fadeOutSpec=androidx.compose.animation.core.tween(180),placementSpec=androidx.compose.animation.core.tween(300)),
                                group = group,
                                selectionControl=if(selectionMode)({val ids=group.entries.map{it.entry.id}.toSet();PurchaseSectionToggle(ids,effectiveSelection,{selectSection(ids)},group.displayName)})else null,
                                viewMode = state.viewMode,
                                onClearBought = {
                                    viewModel.clearBoughtInPlace(group)
                                },
                                onSelectPending=if(group.entries.any{!it.entry.isBought})({selectionMode=true;selectedIds=(selectedIds+group.entries.filter{!it.entry.isBought}.map{it.entry.id}).distinct()})else null,
                                onToggleCollapsed = if (isByShops) {
                                    { viewModel.toggleShopCollapsed(group) }
                                } else { {expansion.toggle(groupKey)} },
                                onMoveUp = if (isByShops && group.groupId != null && groupIndex > 0 && state.shops.any{it.id==group.groupId} && state.shops.any{it.id==state.groups[groupIndex-1].groupId}) {
                                    {
                                        viewport.capture();viewModel.movePlace(group,state.groups[groupIndex-1])
                                    }
                                } else null,
                                onMoveDown = if (isByShops && group.groupId != null && groupIndex < state.groups.lastIndex && state.shops.any{it.id==group.groupId}) {
                                    // Don't allow moving below "Без магазина" (groupId == null is always last)
                                    val nextIsNoShop = state.shops.none{it.id==state.groups.getOrNull(groupIndex + 1)?.groupId}
                                    if (!nextIsNoShop) {
                                        {
                                            viewport.capture();viewModel.movePlace(group,state.groups[groupIndex+1])
                                        }
                                    } else null
                                } else null,
                                onAddToShop = if (isByShops && group.groupId != null && state.shops.any{it.id==group.groupId}) {
                                    { onNavigateToAddToShop(group.groupId!!,group.branchId?:0L) }
                                } else null
                            )
                        }
                    }

                    val visibleEntries = if (group.isCollapsed) {
                        if (group.showUrgentWhenCollapsed) {
                            group.entries.filter { it.entry.isUrgent && !it.entry.isBought }
                        } else {
                            emptyList()
                        }
                    } else {
                        group.entries
                    }

                    // Compact urgency indicator: shown when shop is collapsed,
                    // urgent items are hidden, but there ARE urgent items inside
                    if (group.isCollapsed && !group.showUrgentWhenCollapsed && group.urgentCount > 0) {
                        item(key = "urgent_indicator_${group.placeKey}", contentType = "urgent_indicator") {
                            PurchaseBlockPart(backgroundKey){UrgentHiddenIndicator(urgentCount = group.urgentCount)}
                        }
                    }

                    // In shop view (expanded), show dept sub-group headers when there are
                    // multiple dept groups within the shop
                    val useDeptSubGroups = isByShops
                            && !group.isCollapsed
                            && group.deptSubGroups.size > 1

                    if (useDeptSubGroups) {
                        group.deptSubGroups.forEach { subGroup ->
                            if (subGroup.entries.isNotEmpty()) {
                                item(key = "dept_${group.groupId}_${subGroup.branchId}_${subGroup.deptId}", contentType = "dept_header") {
                                    val key="department_${group.groupId}_${subGroup.branchId}_${subGroup.deptId}"
                                    val name=subGroup.deptName ?: "Без группы магазина"
                                    PurchaseBlockPart(backgroundKey,Modifier.animateItem(fadeInSpec=androidx.compose.animation.core.tween(220),fadeOutSpec=androidx.compose.animation.core.tween(180),placementSpec=androidx.compose.animation.core.tween(300))){
                                        Row(Modifier.fillMaxWidth().testTag("purchase-department-$key")
                                            .heightIn(min=36.dp).padding(start=if(group.venueId==null)20.dp else 36.dp)
                                            .clickable{expansion.toggle(key)}.padding(vertical=2.dp),verticalAlignment=Alignment.CenterVertically){
                                            if(selectionMode){
                                                val ids=subGroup.entries.map{it.entry.id}.toSet()
                                                PurchaseSectionToggle(ids,effectiveSelection,{selectSection(ids)},name)
                                            }
                                            com.spisokryadom.app.ui.components.CollapseChevron(key in expansion.collapsed,(if(key in expansion.collapsed)"Развернуть отдел: "else "Свернуть отдел: ")+name,Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant){
                                                com.spisokryadom.app.ui.components.MarkedLabel(name,subGroup.accent,subGroup.iconKey,modifier=Modifier.weight(1f),small=true)
                                            }
                                        }
                                    }
                                }
                                items(
                                    items = if("department_${group.groupId}_${subGroup.branchId}_${subGroup.deptId}" in expansion.collapsed)emptyList()else subGroup.entries,
                                    key = { it.entry.id },
                                    contentType = { "entry" }
                                ) { entryUi ->
                                    ShoppingListItem(
                                        placementModifier=Modifier.animateItem(fadeInSpec=androidx.compose.animation.core.tween(220),fadeOutSpec=androidx.compose.animation.core.tween(180),placementSpec=androidx.compose.animation.core.tween(300)),
                                        entryUi = entryUi,
                                        blockBackground=backgroundKey,purchaseBackground=if(isByShops)group.purchaseBackground else "",
                                        viewMode = state.viewMode,
                                        onToggleBought = {
                                            if (entryUi.entry.isBought) {
                                                viewModel.markNotBought(entryUi.entry.id)
                                            } else {
                                                viewModel.markBought(entryUi.entry.id)
                                            }
                                        },
                                        onToggleUrgent = { viewModel.toggleUrgent(entryUi.entry.id) },
                                        onEditEntry = { showContextMenuFor = entryUi },
                                        onLongPress = { if(selectionMode)select(entryUi.entry.id)else showContextMenuFor=entryUi },
                                        selected=entryUi.entry.id in effectiveSelection,selectionMode=selectionMode,onSelect={select(entryUi.entry.id)},
                                        onIncreaseQuantity = { viewModel.increaseQuantity(entryUi.entry.id) },
                                        onDecreaseQuantity = { viewModel.decreaseQuantity(entryUi.entry.id) }
                                    )
                                }
                            }
                        }
                    } else if (visibleEntries.isNotEmpty()) {
                        items(
                            items = visibleEntries,
                            key = { it.entry.id },
                            contentType = { "entry" }
                        ) { entryUi ->
                            ShoppingListItem(
                                        placementModifier=Modifier.animateItem(fadeInSpec=androidx.compose.animation.core.tween(220),fadeOutSpec=androidx.compose.animation.core.tween(180),placementSpec=androidx.compose.animation.core.tween(300)),
                                entryUi = entryUi,
                                        blockBackground=backgroundKey,purchaseBackground=if(isByShops)group.purchaseBackground else "",
                                viewMode = state.viewMode,
                                onToggleBought = {
                                    if (entryUi.entry.isBought) {
                                        viewModel.markNotBought(entryUi.entry.id)
                                    } else {
                                        viewModel.markBought(entryUi.entry.id)
                                    }
                                },
                                onToggleUrgent = { viewModel.toggleUrgent(entryUi.entry.id) },
                                onEditEntry = { showContextMenuFor = entryUi },
                                onLongPress = { if(selectionMode)select(entryUi.entry.id)else showContextMenuFor=entryUi },
                                        selected=entryUi.entry.id in effectiveSelection,selectionMode=selectionMode,onSelect={select(entryUi.entry.id)},
                                onIncreaseQuantity = { viewModel.increaseQuantity(entryUi.entry.id) },
                                onDecreaseQuantity = { viewModel.decreaseQuantity(entryUi.entry.id) }
                            )
                        }
                    }
                    if(isByShops&&backgroundKey.isNotBlank())item(key="place_end_${group.placeKey}",contentType="place_end"){
                        PurchaseBlockPart(backgroundKey,bottom=true){Spacer(Modifier.height(6.dp))}
                    }
                    }
                }

                item(contentType = "spacer") { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun ViewModeSwitcher(
    currentMode: ViewMode,
    onModeSelected: (ViewMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ViewMode.entries.forEach { mode ->
            FilterChip(
                selected = mode == currentMode,
                onClick = { onModeSelected(mode) },
                label = { Text(mode.label) },
                leadingIcon = {
                    Icon(
                        imageVector = viewModeIcon(mode),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }
    }
}

@Composable
private fun ListSummary(totalCount: Int, boughtCount: Int) {
    val activeCount = totalCount - boughtCount
    val text = if (boughtCount > 0) {
        "В списке: $totalCount · куплено $boughtCount"
    } else {
        "В списке: $totalCount"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    )
}

private fun viewModeIcon(mode: ViewMode): ImageVector = when (mode) {
    ViewMode.BY_SHOPS -> Icons.Filled.Store
    ViewMode.BY_GROUPS -> Icons.Filled.Label
    ViewMode.BY_RECIPIENTS -> Icons.Filled.Person
    ViewMode.FLAT -> Icons.Filled.FormatListBulleted
}

/** Compact indicator shown when a collapsed shop has urgent items but they are hidden */
@Composable
private fun UrgentHiddenIndicator(urgentCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .background(
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.PriorityHigh,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Срочных внутри: $urgentCount",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun PurchaseBlockPart(colorKey:String,modifier:Modifier=Modifier,bottom:Boolean=false,content:@Composable ()->Unit){
    val tint=com.spisokryadom.app.ui.components.shoppingBlockColor(colorKey,MaterialTheme.colorScheme.surface)
    Box(modifier.fillMaxWidth().background(tint?:androidx.compose.ui.graphics.Color.Transparent,
        RoundedCornerShape(bottomStart=if(bottom)14.dp else 0.dp,bottomEnd=if(bottom)14.dp else 0.dp))
        .padding(horizontal=if(tint==null)0.dp else 6.dp)){content()}
}

@Composable
private fun EntryGroupHeader(
    placementModifier:Modifier=Modifier,
    group: EntryGroup,
    viewMode: ViewMode,
    onClearBought: () -> Unit,
    onToggleCollapsed: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onAddToShop: (() -> Unit)? = null, onSelectPending:(()->Unit)?=null,selectionControl:(@Composable ()->Unit)?=null
) {
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        ConfirmDialog(
            title = "Очистить купленные",
            message = "Очистить купленные записи в «${group.displayName}»?",
            onConfirm = {
                onClearBought()
                showClearDialog = false
            },
            onDismiss = { showClearDialog = false }
        )
    }

    val activeCount = group.totalCount - group.boughtCount

    val blockTint=com.spisokryadom.app.ui.components.shoppingBlockColor(group.shoppingBackground,MaterialTheme.colorScheme.surface)
    Column(placementModifier.fillMaxWidth().background(blockTint?:androidx.compose.ui.graphics.Color.Transparent,RoundedCornerShape(topStart=14.dp,topEnd=14.dp))
        .padding(horizontal=if(blockTint==null)0.dp else 6.dp).padding(start=if(group.venueId!=null)20.dp else 0.dp).testTag("purchase-place-${group.placeKey}")) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onToggleCollapsed != null) Modifier.clickable { onToggleCollapsed() }
                else Modifier
            )
            .heightIn(min=64.dp).padding(top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        selectionControl?.invoke()
        // Collapse/expand icon for shop view
        if (onToggleCollapsed != null) {
            com.spisokryadom.app.ui.components.CollapseChevron(group.isCollapsed,
                (if(group.isCollapsed)"Развернуть: "else "Свернуть: ")+group.displayName,Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(4.dp))
        }

        if(viewMode==ViewMode.BY_SHOPS)com.spisokryadom.app.ui.components.ShopSymbol(group.shopIconKey,group.shopLogoUri,20.dp)
        else Icon(
            viewModeIcon(viewMode),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(Modifier.weight(1f).padding(end=6.dp)) {
            if(viewMode==ViewMode.BY_SHOPS)Text(group.groupName,style=MaterialTheme.typography.titleMedium,
                fontSize=if(group.venueId==null)18.sp else 16.sp,fontWeight=FontWeight.SemiBold,
                modifier=Modifier.testTag("purchase-place-title-${group.placeKey}"))
            else com.spisokryadom.app.ui.components.MarkedLabel(group.groupName,group.accent,group.iconKey)
            group.address?.takeIf{it!=group.groupName}?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        }

        // Compact urgent badge in header when collapsed and showing urgent
        if (group.isCollapsed && group.showUrgentWhenCollapsed && group.urgentCount > 0) {
            Icon(
                Icons.Filled.PriorityHigh,
                contentDescription = "Есть срочные",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(2.dp))
        }

        // Count info
        val countText = if (group.boughtCount > 0) {
            "$activeCount / ${group.totalCount}"
        } else {
            "$activeCount"
        }
        Text(
            text = countText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        var menuOpen by remember { mutableStateOf(false) }
        if(onSelectPending!=null || onAddToShop!=null || (viewMode==ViewMode.BY_SHOPS&&group.groupId!=null) || (group.canClearBought&&group.hasBoughtEntries))Box {
            IconButton(onClick={menuOpen=true}) { Icon(Icons.Filled.MoreVert,"Действия магазина: ${group.displayName}") }
            androidx.compose.material3.DropdownMenu(menuOpen,onDismissRequest={menuOpen=false}) {
                if(onSelectPending!=null)DropdownMenuItem(text={Text("Выбрать некупленные")},leadingIcon={Icon(Icons.Filled.Checklist,null)},
                    modifier=Modifier.semantics{contentDescription="Выбрать некупленные: ${group.displayName}"},onClick={menuOpen=false;onSelectPending()})
                if(onAddToShop!=null)DropdownMenuItem(text={Text("Добавить покупку")},leadingIcon={Icon(Icons.Filled.Add,null)},
                    modifier=Modifier.semantics{contentDescription="Добавить товар в ${group.displayName}"},onClick={menuOpen=false;onAddToShop()})
                if(viewMode==ViewMode.BY_SHOPS&&group.groupId!=null) {
                    HorizontalDivider()
                    Row(Modifier.padding(start=12.dp,end=4.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text("Порядок",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                        com.spisokryadom.app.ui.components.ReorderButtons(
                            {menuOpen=false;onMoveUp?.invoke()},{menuOpen=false;onMoveDown?.invoke()},onMoveUp!=null,onMoveDown!=null,
                            "Место выше: ${group.displayName}","Место ниже: ${group.displayName}")
                    }
                }
                if(group.canClearBought&&group.hasBoughtEntries)DropdownMenuItem(text={Text("Очистить купленные")},leadingIcon={Icon(Icons.Filled.DeleteSweep,null)},
                    modifier=Modifier.semantics{contentDescription="Очистить купленные в ${group.displayName}"},onClick={menuOpen=false;showClearDialog=true})
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShoppingListItem(
    placementModifier:Modifier=Modifier,
    blockBackground:String="",
    purchaseBackground:String="",
    entryUi: ShoppingListEntryUi,
    viewMode: ViewMode,
    onToggleBought: () -> Unit,
    onToggleUrgent: () -> Unit,
    onEditEntry: () -> Unit,
    onLongPress: () -> Unit,
    onIncreaseQuantity: () -> Unit,
    onDecreaseQuantity: () -> Unit, selected:Boolean=false, selectionMode:Boolean=false,onSelect:()->Unit={}
) {
    val entry = entryUi.entry
    val isBought = entry.isBought
    val isUrgent = entry.isUrgent
    val quantityStep = entryUi.quantityStep

    val bgColor by animateColorAsState(
        targetValue = when {
            isBought -> MaterialTheme.colorScheme.surfaceVariant
            isUrgent -> MaterialTheme.colorScheme.errorContainer
            else -> favoriteBackground(entryUi.isFavorite,com.spisokryadom.app.ui.components.purchaseCardColor(blockBackground,purchaseBackground,MaterialTheme.colorScheme.surface))
        },
        label = "itemBg"
    )

    // Format quantity display for the right badge
    val qtyDisplay = if (entry.quantity > 0) {
        if (entry.quantity == entry.quantity.toLong().toDouble()) {
            entry.quantity.toLong().toString()
        } else {
            entry.quantity.toString()
        }
    } else null

    val unitDisplay = entry.unit.ifBlank { null }

    val badgeText = when {
        qtyDisplay != null && unitDisplay != null -> "$qtyDisplay $unitDisplay"
        qtyDisplay != null -> qtyDisplay
        unitDisplay != null -> unitDisplay
        else -> null
    }

    PurchaseBlockPart(blockBackground,placementModifier){
    Card(
        modifier = Modifier.padding(vertical=2.dp)
            .fillMaxWidth().testTag("purchase-row-${entry.id}")
            .combinedClickable(
                onClick = if(selectionMode)onSelect else onEditEntry,
                onLongClick = onLongPress
            ),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = if(selectionMode)selected else isBought,
                enabled = true,
                onCheckedChange = { if(selectionMode)onSelect()else onToggleBought() },
                modifier = Modifier.semantics { contentDescription = (if(selectionMode)"Выбрать: "else if(isBought)"Вернуть в покупки: " else "Купить: ")+entryUi.productName }
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 6.dp)
            ) {
                // Product name with urgency indicator
                com.spisokryadom.app.ui.components.accentColor(entryUi.accent)?.let{Box(Modifier.width(36.dp).height(2.dp).background(it))}
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isUrgent && !isBought) {
                        Icon(
                            Icons.Filled.PriorityHigh,
                            contentDescription = "Срочно",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable(enabled=!selectionMode) { onToggleUrgent() }
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                    }
                    if(entryUi.isFavorite&&favoriteStyle().star){Icon(Icons.Filled.Star,"Избранный товар",Modifier.size(16.dp),tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(4.dp))}
                    Text(
                        text = entryUi.productName,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = favoriteWeight(entryUi.isFavorite)?:if (isUrgent && !isBought) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        textDecoration = if (isBought) TextDecoration.LineThrough else null,
                        color = if (isBought) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Details row: department, context info
                val detailParts = buildList {
                    entryUi.listName?.let{add("Список: $it")}
                    entry.plannedUnitPrice?.let{price->add("≈ "+com.spisokryadom.app.util.PurchaseMoney.format(com.spisokryadom.app.util.PurchaseMoney.amount(price,entry.quantity)))}
                    if (!entry.isBought && entry.awaitingShopCityId != null) add("Магазин не настроен")
                    if (entryUi.departmentName != null) add(entryUi.departmentName)
                    val contextInfo = when (viewMode) {
                        ViewMode.BY_SHOPS -> null
                        ViewMode.BY_GROUPS -> entryUi.shopName
                        ViewMode.BY_RECIPIENTS -> entryUi.shopName
                        ViewMode.FLAT -> entryUi.shopName
                    }
                    if (contextInfo != null) add(contextInfo)
                }
                if (detailParts.isNotEmpty()) {
                    Text(
                        text = detailParts.joinToString(" \u2022 "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!entry.note.isNullOrBlank()) {
                    Text(
                        text = entry.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!entry.comment.isNullOrBlank()) {
                    Text(
                        text = "💬 ${entry.comment}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right side: inline quantity control
            Spacer(modifier = Modifier.width(12.dp))
            if(!selectionMode)com.spisokryadom.app.ui.components.PurchaseQuantityStepper(badgeText?:"—",isBought,entry.quantity>0&&(entry.listId==null||entry.quantity>quantityStep),onDecreaseQuantity,onIncreaseQuantity)
        }
    }
}

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditEntryDialog(
    entryUi: ShoppingListEntryUi,
    onSave: (quantity: Double, note: String?, comment: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var quantity by rememberSaveable(entryUi.entry.id) {
        mutableStateOf(
            if (entryUi.entry.quantity > 0) {
                if (entryUi.entry.quantity == entryUi.entry.quantity.toLong().toDouble()) {
                    entryUi.entry.quantity.toLong().toString()
                } else {
                    entryUi.entry.quantity.toString()
                }
            } else ""
        )
    }
    var note by rememberSaveable(entryUi.entry.id) { mutableStateOf(entryUi.entry.note ?: "") }
    var comment by rememberSaveable(entryUi.entry.id) { mutableStateOf(entryUi.entry.comment ?: "") }

    val unitLabel = entryUi.entry.unit.ifBlank { null }

    // A fixed content width prevents intrinsic text-field measurement from repeatedly resizing the window.
    val dialogWidth=(androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp-48).coerceIn(240,560).dp
    AlertDialog(
        modifier=Modifier.width(dialogWidth),
        properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false),
        onDismissRequest = onDismiss,
        title = { Text(entryUi.productName) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value=quantity,
                    onValueChange={value->if(value.isEmpty()||value.matches(Regex("^\\d*[.,]?\\d*$")))quantity=value},
                    label={Text("Количество")},
                    suffix={unitLabel?.let{Text(it)}},
                    singleLine=true,
                    modifier=Modifier.fillMaxWidth(),
                    keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Decimal)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Note
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Заметка") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // One-time comment for this specific purchase
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Комментарий к покупке") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val qty = com.spisokryadom.app.util.Quantities.parse(quantity) ?: return@TextButton
                onSave(qty, note.ifBlank { null }, comment.ifBlank { null })
            }, enabled = com.spisokryadom.app.util.Quantities.parse(quantity) != null) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryContextMenuDialog(
    entryUi:ShoppingListEntryUi,onOpenProductCard:()->Unit,onChangeShop:()->Unit,
    onCopy:()->Unit,onEditPurchase:()->Unit,onToggleUrgent:()->Unit,onRemove:()->Unit,onDismiss:()->Unit,onMoveList:()->Unit,onOpenList:(()->Unit)?=null
) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest=onDismiss,
        sheetState=androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded=true),
        containerColor=MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=20.dp)) {
            Text(entryUi.productName,style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(bottom=12.dp))
            if(!entryUi.entry.isBought)MenuAction(Icons.Filled.PriorityHigh,if(entryUi.entry.isUrgent)"Снять срочность"else "Сделать срочным",onToggleUrgent)
            if(!entryUi.entry.isBought)MenuAction(Icons.Filled.SwapHoriz,"Сменить место покупки",onChangeShop)
            MenuAction(Icons.Filled.AddShoppingCart,"Добавить магазин к товару",onCopy)
            MenuAction(Icons.Filled.Edit,"Параметры покупки",onEditPurchase)
            onOpenList?.let{MenuAction(Icons.Filled.FormatListBulleted,"Открыть список «${entryUi.listName.orEmpty()}»",it)}
            MenuAction(Icons.Filled.FormatListBulleted,"Перенести в другой список",onMoveList)
            MenuAction(Icons.Filled.Label,"Карточка товара",onOpenProductCard)
            HorizontalDivider(Modifier.padding(vertical=8.dp),color=MaterialTheme.colorScheme.outlineVariant)
            MenuAction(Icons.Filled.Delete,"Удалить из списка",onRemove,destructive=true)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PurchaseSectionToggle(ids:Set<Long>,selected:Set<Long>,onToggle:()->Unit,label:String){
    val state=when{
        ids.isNotEmpty()&&selected.containsAll(ids)->androidx.compose.ui.state.ToggleableState.On
        ids.any{it in selected}->androidx.compose.ui.state.ToggleableState.Indeterminate
        else->androidx.compose.ui.state.ToggleableState.Off
    }
    androidx.compose.material3.TriStateCheckbox(state,onClick=onToggle,enabled=ids.isNotEmpty(),modifier=Modifier.semantics{contentDescription="Выбрать раздел: $label"})
}

@Composable
private fun MenuAction(icon:ImageVector,title:String,onClick:()->Unit,destructive:Boolean=false) {
    val color=if(destructive)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    androidx.compose.material3.Surface(onClick=onClick,shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(icon,null,Modifier.size(22.dp),tint=if(destructive)color else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp));Text(title,color=color,style=MaterialTheme.typography.bodyLarge)
        }
    }
}
