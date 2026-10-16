package com.spisokryadom.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditOff
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spisokryadom.app.data.backup.BackupManager
import com.spisokryadom.app.data.entity.ProductEntity
import com.spisokryadom.app.data.entity.ShopEntity
import com.spisokryadom.app.ui.components.ConfirmDialog
import com.spisokryadom.app.ui.components.InputDialog
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val itemScrollPx = with(LocalDensity.current) { 64.dp.toPx() }
    var showImportConfirm by remember { mutableStateOf(false) }
    var pendingImportJson by remember { mutableStateOf<String?>(null) }

    // ── Recipients state ─────────────────────────────────────────────────────
    val recipients by viewModel.recipients.collectAsStateWithLifecycle()
    val selectedRecipientId by viewModel.selectedRecipientId.collectAsStateWithLifecycle()
    val selectedRecipientProducts by viewModel.selectedRecipientProducts.collectAsStateWithLifecycle()
    val recipientsEditMode by viewModel.recipientsEditMode.collectAsStateWithLifecycle()

    // ── Product groups state ─────────────────────────────────────────────────
    val productGroups by viewModel.productGroups.collectAsStateWithLifecycle()
    val selectedGroupId by viewModel.selectedGroupId.collectAsStateWithLifecycle()
    val selectedGroupProducts by viewModel.selectedGroupProducts.collectAsStateWithLifecycle()
    val groupsEditMode by viewModel.groupsEditMode.collectAsStateWithLifecycle()

    // ── Units state ──────────────────────────────────────────────────────────
    val units by viewModel.units.collectAsStateWithLifecycle()
    val selectedUnitId by viewModel.selectedUnitId.collectAsStateWithLifecycle()
    val selectedUnitProducts by viewModel.selectedUnitProducts.collectAsStateWithLifecycle()
    val unitsEditMode by viewModel.unitsEditMode.collectAsStateWithLifecycle()

    // ── Department groups state ──────────────────────────────────────────────
    val departmentGroups by viewModel.departmentGroups.collectAsStateWithLifecycle()
    val selectedDeptGroup by viewModel.selectedDepartmentGroup.collectAsStateWithLifecycle()
    val selectedDeptGroupShops by viewModel.selectedDepartmentGroupShops.collectAsStateWithLifecycle()
    val deptGroupsEditMode by viewModel.deptGroupsEditMode.collectAsStateWithLifecycle()

    // ── Urgency defaults state ────────────────────────────────────────────────
    val urgencyDefaults by viewModel.urgencyDefaults.collectAsStateWithLifecycle()

    // ── Add-to-list defaults state ───────────────────────────────────────────
    val addToListDefaults by viewModel.addToListDefaults.collectAsStateWithLifecycle()

    // ── Показываем сообщения через Snackbar ──────────────────────────────────
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    // ── Запуск выбора файла для импорта ─────────────────────────────────────
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val json = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            }
            if (json != null) {
                pendingImportJson = json
                showImportConfirm = true
            }
        } catch (_: Exception) {}
    }

    // ── Запуск выбора места сохранения для экспорта ──────────────────────────
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(BackupManager.BACKUP_FILE_MIME)
    ) { uri ->
        val json = state.pendingExportJson
        if (uri != null && json != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(json.toByteArray(Charsets.UTF_8))
                }
                viewModel.exportConsumed(success = true)
            } catch (_: Exception) {
                viewModel.exportConsumed(success = false)
            }
        } else {
            viewModel.exportConsumed(success = false)
        }
    }

    // ── Когда JSON готов — открываем file picker для сохранения ─────────────
    LaunchedEffect(state.pendingExportJson) {
        if (state.pendingExportJson != null) {
            val date = LocalDate.now().toString().replace("-", "")
            exportLauncher.launch("${BackupManager.BACKUP_FILE_NAME_PREFIX}_$date.json")
        }
    }

    // ── Диалог подтверждения импорта ────────────────────────────────────────
    if (showImportConfirm) {
        ConfirmDialog(
            title = "Восстановить данные",
            message = "Постоянная база товаров и магазинов будет полностью заменена данными из файла. Текущий список покупок не затрагивается. Продолжить?",
            onConfirm = {
                showImportConfirm = false
                pendingImportJson?.let { viewModel.importFromJson(it) }
                pendingImportJson = null
            },
            onDismiss = {
                showImportConfirm = false
                pendingImportJson = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Служебные настройки") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ════════════════════════════════════════════════════════════════
            // SECTION: Экспорт / Импорт
            // ════════════════════════════════════════════════════════════════
            CollapsibleSection(title = "Экспорт / Импорт") {
                Text(
                    text = "Сохраняет постоянную базу в JSON-файл: товары (с фотографиями), магазины, отделы, группы, получателей и привязки. Текущий список покупок не включается.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { viewModel.prepareExport() },
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Экспортировать базу")
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Восстанавливает постоянную базу из ранее сохранённого файла. Товары, магазины и все связи будут полностью заменены. Список покупок не затрагивается.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Импортировать из файла")
                }
            }

            HorizontalDivider()

            // ════════════════════════════════════════════════════════════════
            // SECTION: Настройки срочности
            // ════════════════════════════════════════════════════════════════
            UrgencyDefaultsSection(
                state = urgencyDefaults,
                onShowUrgentInCollapsedChanged = viewModel::setGlobalShowUrgentInCollapsed,
                onBoostUrgentShopChanged = viewModel::setGlobalBoostUrgentShop
            )

            HorizontalDivider()

            // ════════════════════════════════════════════════════════════════
            // SECTION: Настройки быстрого добавления
            // ════════════════════════════════════════════════════════════════
            AddToListDefaultsSection(
                state = addToListDefaults,
                onSaveNewProductToDatabaseChanged = viewModel::setGlobalSaveNewProductToDatabase
            )

            HorizontalDivider()

            // ════════════════════════════════════════════════════════════════
            // SECTION: Получатели
            // ════════════════════════════════════════════════════════════════
            ReferenceSection(
                title = "Получатели",
                count = recipients.size,
                emptyText = "Нет получателей",
                isEditMode = recipientsEditMode,
                onToggleEditMode = viewModel::toggleRecipientsEditMode,
                createDialogTitle = "Новый получатель",
                createDialogLabel = "Имя получателя",
                addButtonText = "Добавить получателя",
                onCreate = viewModel::createRecipient,
                items = recipients,
                itemKey = { it.id },
                itemName = { it.name },
                isSelected = { it.id == selectedRecipientId },
                onSelect = { viewModel.selectRecipient(if (it.id == selectedRecipientId) null else it.id) },
                renameDialogTitle = "Переименовать получателя",
                renameDialogLabel = "Новое имя",
                onRename = { item, newName -> viewModel.renameRecipient(item, newName) },
                deleteDialogTitle = "Удалить получателя",
                deleteDialogMessage = { "Получатель \"${it.name}\" будет удалён. У связанных товаров получатель станет \"не задан\". Продолжить?" },
                onDelete = viewModel::deleteRecipient,
                onMoveUp = { item ->
                    viewModel.moveRecipientUp(item)
                    scope.launch { scrollState.animateScrollBy(-itemScrollPx) }
                },
                onMoveDown = { item ->
                    viewModel.moveRecipientDown(item)
                    scope.launch { scrollState.animateScrollBy(itemScrollPx) }
                },
                isFirst = { recipients.firstOrNull()?.id == it.id },
                isLast = { recipients.lastOrNull()?.id == it.id },
                usageContent = { item ->
                    val isSelected = item.id == selectedRecipientId
                    if (isSelected) {
                        UsageProductList(selectedRecipientProducts)
                    }
                }
            )

            HorizontalDivider()

            // ════════════════════════════════════════════════════════════════
            // SECTION: Товарные группы
            // ════════════════════════════════════════════════════════════════
            ReferenceSection(
                title = "Товарные группы",
                count = productGroups.size,
                emptyText = "Нет товарных групп",
                isEditMode = groupsEditMode,
                onToggleEditMode = viewModel::toggleGroupsEditMode,
                createDialogTitle = "Новая товарная группа",
                createDialogLabel = "Название группы",
                addButtonText = "Добавить товарную группу",
                onCreate = viewModel::createProductGroup,
                items = productGroups,
                itemKey = { it.id },
                itemName = { it.name },
                isSelected = { it.id == selectedGroupId },
                onSelect = { viewModel.selectProductGroup(if (it.id == selectedGroupId) null else it.id) },
                renameDialogTitle = "Переименовать товарную группу",
                renameDialogLabel = "Новое название",
                onRename = { item, newName -> viewModel.renameProductGroup(item, newName) },
                deleteDialogTitle = "Удалить товарную группу",
                deleteDialogMessage = { "Группа \"${it.name}\" будет удалена. У связанных товаров группа станет \"не задана\". Продолжить?" },
                onDelete = viewModel::deleteProductGroup,
                onMoveUp = { item ->
                    viewModel.moveProductGroupUp(item)
                    scope.launch { scrollState.animateScrollBy(-itemScrollPx) }
                },
                onMoveDown = { item ->
                    viewModel.moveProductGroupDown(item)
                    scope.launch { scrollState.animateScrollBy(itemScrollPx) }
                },
                isFirst = { productGroups.firstOrNull()?.id == it.id },
                isLast = { productGroups.lastOrNull()?.id == it.id },
                usageContent = { item ->
                    val isSelected = item.id == selectedGroupId
                    if (isSelected) {
                        UsageProductList(selectedGroupProducts)
                    }
                }
            )

            HorizontalDivider()

            // ════════════════════════════════════════════════════════════════
            // SECTION: Единицы измерения
            // ════════════════════════════════════════════════════════════════
            ReferenceSection(
                title = "Единицы измерения",
                count = units.size,
                emptyText = "Нет единиц измерения",
                isEditMode = unitsEditMode,
                onToggleEditMode = viewModel::toggleUnitsEditMode,
                createDialogTitle = "Новая единица измерения",
                createDialogLabel = "Название единицы",
                addButtonText = "Добавить единицу измерения",
                onCreate = viewModel::createUnit,
                items = units,
                itemKey = { it.id },
                itemName = { it.name },
                isSelected = { it.id == selectedUnitId },
                onSelect = { viewModel.selectUnit(if (it.id == selectedUnitId) null else it.id) },
                renameDialogTitle = "Переименовать единицу измерения",
                renameDialogLabel = "Новое название",
                onRename = { item, newName -> viewModel.renameUnit(item, newName) },
                deleteDialogTitle = "Удалить единицу измерения",
                deleteDialogMessage = { "Единица \"${it.name}\" будет убрана из всех товаров, где она используется. Продолжить?" },
                onDelete = viewModel::deleteUnit,
                onMoveUp = { item ->
                    viewModel.moveUnitUp(item)
                    scope.launch { scrollState.animateScrollBy(-itemScrollPx) }
                },
                onMoveDown = { item ->
                    viewModel.moveUnitDown(item)
                    scope.launch { scrollState.animateScrollBy(itemScrollPx) }
                },
                isFirst = { units.firstOrNull()?.id == it.id },
                isLast = { units.lastOrNull()?.id == it.id },
                usageContent = { item ->
                    val isSelected = item.id == selectedUnitId
                    if (isSelected) {
                        UsageProductList(selectedUnitProducts)
                    }
                }
            )

            HorizontalDivider()

            // ════════════════════════════════════════════════════════════════
            // SECTION: Группы магазинов (отделы)
            // ════════════════════════════════════════════════════════════════
            DeptGroupReferenceSection(
                groups = departmentGroups,
                selectedGroup = selectedDeptGroup,
                selectedGroupShops = selectedDeptGroupShops,
                isEditMode = deptGroupsEditMode,
                onToggleEditMode = viewModel::toggleDeptGroupsEditMode,
                onSelect = viewModel::selectDepartmentGroup,
                onRename = viewModel::renameDepartmentGroup,
                onDelete = viewModel::deleteDepartmentGroup,
                onCreate = viewModel::createDepartmentGroup
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Usage product list (shared)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun UsageProductList(products: List<ProductEntity>) {
    if (products.isEmpty()) {
        Text(
            "Не используется ни в одном товаре",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        Text(
            "Используется в товарах (${products.size}):",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        products.forEach { product ->
            Text(
                "  \u2022 ${product.name}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Generic ReferenceSection for entities with ID (Recipients, Product Groups)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun <T> ReferenceSection(
    title: String,
    count: Int,
    emptyText: String,
    isEditMode: Boolean,
    onToggleEditMode: () -> Unit,
    createDialogTitle: String,
    createDialogLabel: String,
    addButtonText: String,
    onCreate: (String) -> Unit,
    items: List<T>,
    itemKey: (T) -> Any,
    itemName: (T) -> String,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    renameDialogTitle: String,
    renameDialogLabel: String,
    onRename: (T, String) -> Unit,
    deleteDialogTitle: String,
    deleteDialogMessage: (T) -> String,
    onDelete: (T) -> Unit,
    onMoveUp: (T) -> Unit,
    onMoveDown: (T) -> Unit,
    isFirst: (T) -> Boolean,
    isLast: (T) -> Boolean,
    usageContent: @Composable (T) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var itemToRename by remember { mutableStateOf<T?>(null) }
    var itemToDelete by remember { mutableStateOf<T?>(null) }

    if (showCreateDialog) {
        InputDialog(
            title = createDialogTitle,
            label = createDialogLabel,
            onConfirm = { name ->
                onCreate(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    itemToRename?.let { item ->
        InputDialog(
            title = renameDialogTitle,
            initialValue = itemName(item),
            label = renameDialogLabel,
            onConfirm = { newName ->
                onRename(item, newName)
                itemToRename = null
            },
            onDismiss = { itemToRename = null }
        )
    }

    itemToDelete?.let { item ->
        ConfirmDialog(
            title = deleteDialogTitle,
            message = deleteDialogMessage(item),
            onConfirm = {
                onDelete(item)
                itemToDelete = null
            },
            onDismiss = { itemToDelete = null }
        )
    }

    CollapsibleSectionWithEditToggle(
        title = title,
        count = count,
        isEditMode = isEditMode,
        onToggleEditMode = onToggleEditMode
    ) {
        if (items.isEmpty()) {
            Text(
                emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items.forEach { item ->
            val selected = isSelected(item)
            EntityCard(
                name = itemName(item),
                isSelected = selected,
                isEditMode = isEditMode,
                onClick = { onSelect(item) },
                onRename = { itemToRename = item },
                onDelete = { itemToDelete = item },
                onMoveUp = { onMoveUp(item) },
                onMoveDown = { onMoveDown(item) },
                isFirst = isFirst(item),
                isLast = isLast(item)
            ) {
                if (selected) {
                    usageContent(item)
                }
            }
        }

        if (isEditMode) {
            OutlinedButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(addButtonText)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Department groups section (string-based, cross-shop)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun DeptGroupReferenceSection(
    groups: List<DepartmentGroup>,
    selectedGroup: String?,
    selectedGroupShops: List<ShopEntity>,
    isEditMode: Boolean,
    onToggleEditMode: () -> Unit,
    onSelect: (String?) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onCreate: (String) -> Unit
) {
    var groupToRename by remember { mutableStateOf<String?>(null) }
    var groupToDelete by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    if (showCreateDialog) {
        InputDialog(
            title = "Новая группа магазина",
            label = "Название группы",
            onConfirm = { name ->
                onCreate(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    groupToRename?.let { name ->
        InputDialog(
            title = "Переименовать группу магазина",
            initialValue = name,
            label = "Новое название",
            onConfirm = { newName ->
                onRename(name, newName)
                groupToRename = null
            },
            onDismiss = { groupToRename = null }
        )
    }

    groupToDelete?.let { name ->
        val group = groups.find { it.name == name }
        val shopCount = group?.shops?.size ?: 0
        ConfirmDialog(
            title = "Удалить группу магазина",
            message = "Группа \"$name\" будет удалена из всех магазинов ($shopCount). У связанных привязок товаров отдел станет \"не задан\". Продолжить?",
            onConfirm = {
                onDelete(name)
                groupToDelete = null
            },
            onDismiss = { groupToDelete = null }
        )
    }

    CollapsibleSectionWithEditToggle(
        title = "Группы магазинов",
        count = groups.size,
        isEditMode = isEditMode,
        onToggleEditMode = onToggleEditMode
    ) {
        Text(
            "Группы (отделы) магазинов как самостоятельные наименования. Переименование применяется во всех магазинах, где группа используется.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (groups.isEmpty()) {
            Text(
                "Нет групп магазинов",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        groups.forEachIndexed { index, group ->
            val isSelected = selectedGroup == group.name
            EntityCard(
                name = group.name,
                badge = "в ${group.shops.size} маг.",
                isSelected = isSelected,
                isEditMode = isEditMode,
                onClick = { onSelect(if (isSelected) null else group.name) },
                onRename = { groupToRename = group.name },
                onDelete = { groupToDelete = group.name },
                onMoveUp = null,
                onMoveDown = null,
                isFirst = index == 0,
                isLast = index == groups.size - 1
            ) {
                if (isSelected && selectedGroupShops.isNotEmpty()) {
                    Text(
                        "Используется в магазинах (${selectedGroupShops.size}):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    selectedGroupShops.forEach { shop ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        ) {
                            Icon(
                                Icons.Filled.Store,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                shop.name,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                if (isSelected && selectedGroupShops.isEmpty()) {
                    Text(
                        "Не используется ни в одном магазине",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (isEditMode) {
            OutlinedButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Добавить группу магазина")
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Urgency defaults section
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun AddToListDefaultsSection(
    state: AddToListDefaultsState,
    onSaveNewProductToDatabaseChanged: (Boolean) -> Unit
) {
    CollapsibleSection(title = "Быстрое добавление") {
        Text(
            "Настройка поведения по умолчанию при быстром добавлении нового товара в список.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Сохранять новый товар в базе",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    if (state.saveNewProductToDatabase)
                        "Новый быстрый товар по умолчанию сохраняется в постоянную базу товаров"
                    else
                        "Новый быстрый товар по умолчанию создаётся только для текущей покупки",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.saveNewProductToDatabase,
                onCheckedChange = onSaveNewProductToDatabaseChanged
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Urgency defaults section
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun UrgencyDefaultsSection(
    state: UrgencyDefaultsState,
    onShowUrgentInCollapsedChanged: (Boolean) -> Unit,
    onBoostUrgentShopChanged: (Boolean) -> Unit
) {
    CollapsibleSection(title = "Настройки срочности") {
        Text(
            "Глобальные настройки по умолчанию для новых магазинов. " +
                    "Для каждого конкретного магазина можно задать своё поведение в его карточке.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Show urgent in collapsed
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Срочные в свернутом магазине",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    if (state.showUrgentInCollapsed)
                        "Отображать срочные товары в свернутом магазине"
                    else
                        "Не отображать срочные товары в свернутом магазине",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.showUrgentInCollapsed,
                onCheckedChange = onShowUrgentInCollapsedChanged
            )
        }

        // Boost urgent shop
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Поднимать магазин со срочными",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    if (state.boostUrgentShop)
                        "Автоматически поднимать магазин выше, если в нём есть срочные товары"
                    else
                        "Ничего не делать с положением магазина при наличии срочных товаров",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.boostUrgentShop,
                onCheckedChange = onBoostUrgentShopChanged
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Collapsible Section wrapper
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun CollapsibleSection(
    title: String,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Свернуть" else "Развернуть",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Collapsible Section with edit mode toggle (for reference sections)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun CollapsibleSectionWithEditToggle(
    title: String,
    count: Int,
    isEditMode: Boolean,
    onToggleEditMode: () -> Unit,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$title ($count)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            if (expanded) {
                IconButton(
                    onClick = onToggleEditMode,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isEditMode) Icons.Filled.EditOff else Icons.Filled.Edit,
                        contentDescription = if (isEditMode) "Завершить правку" else "Режим правок",
                        modifier = Modifier.size(20.dp),
                        tint = if (isEditMode) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Свернуть" else "Развернуть",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Reusable entity card with edit mode support
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun EntityCard(
    name: String,
    isSelected: Boolean,
    isEditMode: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    isFirst: Boolean,
    isLast: Boolean,
    badge: String? = null,
    expandedContent: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (badge != null) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isEditMode) {
                    // Move up
                    if (onMoveUp != null) {
                        IconButton(
                            onClick = onMoveUp,
                            enabled = !isFirst,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.ArrowUpward,
                                contentDescription = "Вверх",
                                modifier = Modifier.size(18.dp),
                                tint = if (!isFirst) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        }
                    }
                    // Move down
                    if (onMoveDown != null) {
                        IconButton(
                            onClick = onMoveDown,
                            enabled = !isLast,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.ArrowDownward,
                                contentDescription = "Вниз",
                                modifier = Modifier.size(18.dp),
                                tint = if (!isLast) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        }
                    }
                    // Rename
                    IconButton(
                        onClick = onRename,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Переименовать",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Delete
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Удалить",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }

                // Expand/collapse toggle (always visible)
                IconButton(
                    onClick = onClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (isSelected) "Свернуть" else "Подробнее",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = isSelected,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    expandedContent()
                }
            }
        }
    }
}
