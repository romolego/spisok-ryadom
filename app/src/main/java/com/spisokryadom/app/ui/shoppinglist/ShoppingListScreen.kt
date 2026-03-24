package com.spisokryadom.app.ui.shoppinglist

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Store
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spisokryadom.app.data.entity.ShopEntity
import com.spisokryadom.app.ui.components.ConfirmDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(
    onNavigateToProducts: () -> Unit,
    onNavigateToProductCard: (Long) -> Unit,
    viewModel: ShoppingListViewModel = viewModel(factory = ShoppingListViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }
    var showEditEntryFor by remember { mutableStateOf<ShoppingListEntryUi?>(null) }
    var showContextMenuFor by remember { mutableStateOf<ShoppingListEntryUi?>(null) }
    var showChangeShopFor by remember { mutableStateOf<ShoppingListEntryUi?>(null) }

    showEditEntryFor?.let { entryUi ->
        EditEntryDialog(
            entryUi = entryUi,
            allShops = state.shops,
            viewModel = viewModel,
            onSave = { qty, shopId, note ->
                viewModel.updateEntryDetails(entryUi.entry.id, qty, shopId, note)
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
                showChangeShopFor = entryUi
            },
            onToggleUrgent = {
                viewModel.toggleUrgent(entryUi.entry.id)
                showContextMenuFor = null
            },
            onRemove = {
                viewModel.removeEntry(entryUi.entry.id)
                showContextMenuFor = null
            },
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

    showChangeShopFor?.let { entryUi ->
        ChangeShopDialog(
            entryUi = entryUi,
            allShops = state.shops,
            viewModel = viewModel,
            onConfirm = { newShopId ->
                viewModel.changeShop(entryUi.entry.id, newShopId, null)
                showChangeShopFor = null
            },
            onDismiss = { showChangeShopFor = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Список покупок") },
                actions = {
                    if (state.hasBoughtEntries) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                Icons.Filled.DeleteSweep,
                                contentDescription = "Очистить купленные"
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToProducts) {
                Icon(Icons.Filled.Add, contentDescription = "Добавить товар")
            }
        }
    ) { padding ->
        val totalEntries = state.allEntries.size
        val isEmpty = totalEntries == 0 && !state.isLoading

        if (isEmpty) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Список пуст",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Нажмите +, чтобы добавить товар",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // View mode switcher
                item(key = "view_mode_switcher") {
                    ViewModeSwitcher(
                        currentMode = state.viewMode,
                        onModeSelected = { viewModel.setViewMode(it) }
                    )
                }

                // Urgent filter chip
                item(key = "urgent_filter") {
                    val urgentCount = state.allEntries.count { it.entry.isUrgent && !it.entry.isBought }
                    if (urgentCount > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            FilterChip(
                                selected = state.showOnlyUrgent,
                                onClick = { viewModel.toggleUrgentFilter() },
                                label = { Text("Только срочные ($urgentCount)") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.FilterList,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                // Entry groups
                val showGroupHeaders = state.viewMode != ViewMode.FLAT
                val isByShops = state.viewMode == ViewMode.BY_SHOPS
                state.groups.forEachIndexed { groupIndex, group ->
                    if (showGroupHeaders) {
                        item(key = "header_${state.viewMode}_${group.groupName}") {
                            EntryGroupHeader(
                                group = group,
                                viewMode = state.viewMode,
                                onClearBought = {
                                    viewModel.clearBoughtByShop(group.groupId)
                                },
                                onToggleCollapsed = if (isByShops) {
                                    { viewModel.toggleShopCollapsed(group.groupId) }
                                } else null,
                                onMoveUp = if (isByShops && group.groupId != null && groupIndex > 0) {
                                    { viewModel.moveShopUp(group.groupId) }
                                } else null,
                                onMoveDown = if (isByShops && group.groupId != null && groupIndex < state.groups.lastIndex) {
                                    // Don't allow moving below "Без магазина" (groupId == null is always last)
                                    val nextIsNoShop = state.groups.getOrNull(groupIndex + 1)?.groupId == null
                                    if (!nextIsNoShop) {
                                        { viewModel.moveShopDown(group.groupId) }
                                    } else null
                                } else null
                            )
                        }
                    }

                    if (!group.isCollapsed) {
                        items(
                            items = group.entries,
                            key = { it.entry.id }
                        ) { entryUi ->
                            ShoppingListItem(
                                entryUi = entryUi,
                                viewMode = state.viewMode,
                                onToggleBought = {
                                    if (entryUi.entry.isBought) {
                                        viewModel.markNotBought(entryUi.entry.id)
                                    } else {
                                        viewModel.markBought(entryUi.entry.id)
                                    }
                                },
                                onToggleUrgent = { viewModel.toggleUrgent(entryUi.entry.id) },
                                onEditEntry = { showEditEntryFor = entryUi },
                                onLongPress = { showContextMenuFor = entryUi }
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
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

private fun viewModeIcon(mode: ViewMode): ImageVector = when (mode) {
    ViewMode.BY_SHOPS -> Icons.Filled.Store
    ViewMode.BY_GROUPS -> Icons.Filled.Label
    ViewMode.BY_RECIPIENTS -> Icons.Filled.Person
    ViewMode.FLAT -> Icons.Filled.FormatListBulleted
}

@Composable
private fun EntryGroupHeader(
    group: EntryGroup,
    viewMode: ViewMode,
    onClearBought: () -> Unit,
    onToggleCollapsed: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null
) {
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        ConfirmDialog(
            title = "Очистить купленные",
            message = "Очистить купленные записи в «${group.groupName}»?",
            onConfirm = {
                onClearBought()
                showClearDialog = false
            },
            onDismiss = { showClearDialog = false }
        )
    }

    val activeCount = group.totalCount - group.boughtCount

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onToggleCollapsed != null) Modifier.clickable { onToggleCollapsed() }
                else Modifier
            )
            .padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Collapse/expand icon for shop view
        if (onToggleCollapsed != null) {
            Icon(
                if (group.isCollapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                contentDescription = if (group.isCollapsed) "Развернуть" else "Свернуть",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
        }

        Icon(
            viewModeIcon(viewMode),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = group.groupName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )

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

        // Reorder buttons (only in shop view for real shops)
        if (onMoveUp != null) {
            IconButton(
                onClick = onMoveUp,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = "Переместить вверх",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (onMoveDown != null) {
            IconButton(
                onClick = onMoveDown,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Переместить вниз",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (group.canClearBought && group.hasBoughtEntries) {
            IconButton(
                onClick = { showClearDialog = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Filled.DeleteSweep,
                    contentDescription = "Очистить купленные в ${group.groupName}",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShoppingListItem(
    entryUi: ShoppingListEntryUi,
    viewMode: ViewMode,
    onToggleBought: () -> Unit,
    onToggleUrgent: () -> Unit,
    onEditEntry: () -> Unit,
    onLongPress: () -> Unit
) {
    val entry = entryUi.entry
    val isBought = entry.isBought
    val isUrgent = entry.isUrgent

    val bgColor by animateColorAsState(
        targetValue = when {
            isBought -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            isUrgent -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
            else -> MaterialTheme.colorScheme.surface
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isBought) 0.6f else 1f)
            .combinedClickable(
                onClick = onEditEntry,
                onLongClick = onLongPress
            ),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isBought) 0.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isBought,
                onCheckedChange = { onToggleBought() }
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 6.dp)
            ) {
                // Product name with urgency indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isUrgent && !isBought) {
                        Icon(
                            Icons.Filled.PriorityHigh,
                            contentDescription = "Срочно",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { onToggleUrgent() }
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                    }
                    Text(
                        text = entryUi.productName,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = if (isUrgent && !isBought) FontWeight.SemiBold else FontWeight.Normal
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
                        style = MaterialTheme.typography.labelSmall,
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
            }

            // Right side: quantity badge
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .widthIn(min = 44.dp)
                    .background(
                        if (!isBought) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeText ?: "\u2014",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (badgeText != null) FontWeight.Medium else FontWeight.Normal
                    ),
                    color = if (badgeText != null) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    maxLines = 1
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditEntryDialog(
    entryUi: ShoppingListEntryUi,
    allShops: List<ShopEntity>,
    viewModel: ShoppingListViewModel,
    onSave: (quantity: Double, shopId: Long?, note: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var linkedShops by remember { mutableStateOf<List<ShopEntity>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    if (!loaded) {
        scope.launch {
            linkedShops = viewModel.getLinkedShopsForProduct(entryUi.entry.productId)
            loaded = true
        }
    }

    var quantity by remember {
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
    var selectedShopId by remember { mutableStateOf(entryUi.entry.assignedShopId) }
    var note by remember { mutableStateOf(entryUi.entry.note ?: "") }
    var shopExpanded by remember { mutableStateOf(false) }

    val unitLabel = entryUi.entry.unit.ifBlank { null }

    // Build ordered shop list: linked shops first, then others
    val shopList = remember(linkedShops, allShops) {
        val linkedIds = linkedShops.map { it.id }.toSet()
        linkedShops + allShops.filter { it.id !in linkedIds }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entryUi.productName) },
        text = {
            Column {
                // Quantity with unit label
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { value ->
                            if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) {
                                quantity = value
                            }
                        },
                        label = { Text("Количество") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    if (unitLabel != null) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = unitLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Shop selector
                ExposedDropdownMenuBox(
                    expanded = shopExpanded,
                    onExpandedChange = { shopExpanded = it }
                ) {
                    val selectedName = selectedShopId?.let { id ->
                        shopList.find { it.id == id }?.name
                    } ?: "Не задан"
                    OutlinedTextField(
                        value = selectedName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Магазин") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = shopExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = shopExpanded,
                        onDismissRequest = { shopExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Не задан",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                selectedShopId = null
                                shopExpanded = false
                            }
                        )
                        shopList.forEach { shop ->
                            DropdownMenuItem(
                                text = { Text(shop.name) },
                                onClick = {
                                    selectedShopId = shop.id
                                    shopExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Note
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Заметка") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val qty = quantity.toDoubleOrNull() ?: 0.0
                onSave(qty, selectedShopId, note.ifBlank { null })
            }) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun EntryContextMenuDialog(
    entryUi: ShoppingListEntryUi,
    onOpenProductCard: () -> Unit,
    onChangeShop: () -> Unit,
    onToggleUrgent: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entryUi.productName) },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenProductCard() }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Карточка товара")
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChangeShop() }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Перенести в магазин")
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleUrgent() }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.PriorityHigh,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (entryUi.entry.isUrgent) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        if (entryUi.entry.isUrgent) "Снять срочность" else "Сделать срочным"
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onRemove() }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Удалить из списка",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

@Composable
private fun ChangeShopDialog(
    entryUi: ShoppingListEntryUi,
    allShops: List<ShopEntity>,
    viewModel: ShoppingListViewModel,
    onConfirm: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var linkedShops by remember { mutableStateOf<List<ShopEntity>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    if (!loaded) {
        scope.launch {
            linkedShops = viewModel.getLinkedShopsForProduct(entryUi.entry.productId)
            loaded = true
        }
    }

    val currentShopId = entryUi.entry.assignedShopId
    val linkedShopIds = linkedShops.map { it.id }.toSet()
    val otherShops = allShops.filter { it.id !in linkedShopIds && it.id != currentShopId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Перенести: ${entryUi.productName}") },
        text = {
            Column {
                if (entryUi.shopName != null) {
                    Text(
                        "Сейчас: ${entryUi.shopName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                val recommendedShops = linkedShops.filter { it.id != currentShopId }
                if (recommendedShops.isNotEmpty()) {
                    Text(
                        "Допустимые магазины:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    recommendedShops.forEach { shop ->
                        Text(
                            text = shop.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onConfirm(shop.id) }
                                .padding(vertical = 8.dp, horizontal = 8.dp)
                        )
                    }
                }

                if (otherShops.isNotEmpty()) {
                    if (recommendedShops.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                    Text(
                        "Другие магазины:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    otherShops.forEach { shop ->
                        Text(
                            text = shop.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onConfirm(shop.id) }
                                .padding(vertical = 8.dp, horizontal = 8.dp)
                        )
                    }
                }

                if (currentShopId != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "Без магазина",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onConfirm(null) }
                            .padding(vertical = 8.dp, horizontal = 8.dp)
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
