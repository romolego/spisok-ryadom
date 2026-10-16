package com.spisokryadom.app.ui.productcard

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spisokryadom.app.data.entity.ShopDepartmentEntity
import com.spisokryadom.app.data.entity.ShopEntity
import com.spisokryadom.app.ui.components.ConfirmDialog
import com.spisokryadom.app.ui.components.DropdownSelector
import com.spisokryadom.app.ui.components.EditableSection
import com.spisokryadom.app.ui.components.InputDialog
import com.spisokryadom.app.ui.components.PurchaseTypeSelector
import com.spisokryadom.app.ui.components.QuantityUnitInputWithSuggestions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductCardScreen(
    productId: Long,
    preselectedShopId: Long = -1L,
    onNavigateBack: () -> Unit,
    viewModel: ProductCardViewModel = viewModel(factory = ProductCardViewModel.factory(productId, preselectedShopId))
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val itemScrollPx = with(LocalDensity.current) { 64.dp.toPx() }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onNavigateBack()
    }
    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) onNavigateBack()
    }

    state.error?.let { message -> androidx.compose.material3.AlertDialog(onDismissRequest=viewModel::clearError,title={Text("Проверьте товар")},text={Text(message)},confirmButton={TextButton(onClick=viewModel::clearError){Text("Понятно")}}) }
    var showNewGroupDialog by remember { mutableStateOf(false) }
    var showAddShopLinkDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // For new products, start in edit mode so the user can add shops immediately
    var isShopLinksEditing by remember { mutableStateOf(productId == -1L) }
    // Department editing state
    var editingPendingLinkId by remember { mutableStateOf<Long?>(null) }
    var editingExistingLinkId by remember { mutableStateOf<Long?>(null) }

    // --- Dialogs ---

    if (showNewGroupDialog) {
        InputDialog(
            title = "Новая товарная группа",
            label = "Название",
            onConfirm = { name ->
                viewModel.createGroup(name)
                showNewGroupDialog = false
            },
            onDismiss = { showNewGroupDialog = false }
        )
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "Удалить товар",
            message = "Вы уверены, что хотите удалить товар «${state.name}»? Связи с магазинами и записи в списке покупок будут удалены.",
            onConfirm = {
                viewModel.deleteProduct()
                showDeleteConfirm = false
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    if (showAddShopLinkDialog) {
        val existingShopIds = if (state.isNew) {
            state.pendingShopLinks.map { it.shopId }.toSet()
        } else {
            state.shopLinks.map { it.link.shopId }.toSet()
        }
        AddShopLinkDialog(
            shops = state.shops,
            existingShopIds = existingShopIds,
            onConfirm = { shopId, departmentId ->
                if (state.isNew) {
                    viewModel.addPendingShopLink(shopId, departmentId)
                } else {
                    viewModel.addShopLink(shopId, departmentId)
                }
                showAddShopLinkDialog = false
            },
            onDismiss = { showAddShopLinkDialog = false },
            onLoadDepartments = { shopId -> viewModel.loadDepartmentsForShop(shopId) },
            departmentsForShop = state.departmentsForSelectedShop,
            suggestedDepartmentId = state.suggestedDepartmentId,
            suggestedDepartmentReason = state.suggestedDepartmentReason
        )
    }

    // Department editing dialog for pending links
    editingPendingLinkId?.let { tempId ->
        val pendingLink = state.pendingShopLinks.find { it.tempId == tempId }
        if (pendingLink != null) {
            DepartmentSelectDialog(
                shopName = pendingLink.shopName,
                departments = state.departmentsForSelectedShop,
                currentDepartmentId = pendingLink.departmentId,
                suggestedDepartmentId = state.suggestedDepartmentId,
                suggestedDepartmentReason = state.suggestedDepartmentReason,
                onConfirm = { deptId ->
                    viewModel.updatePendingShopLinkDepartment(tempId, deptId)
                    editingPendingLinkId = null
                },
                onDismiss = { editingPendingLinkId = null }
            )
        }
    }

    // Department editing dialog for existing links
    editingExistingLinkId?.let { linkId ->
        val linkUi = state.shopLinks.find { it.link.id == linkId }
        if (linkUi != null) {
            DepartmentSelectDialog(
                shopName = linkUi.shopName,
                departments = state.departmentsForSelectedShop,
                currentDepartmentId = linkUi.link.departmentId,
                suggestedDepartmentId = state.suggestedDepartmentId,
                suggestedDepartmentReason = state.suggestedDepartmentReason,
                onConfirm = { deptId ->
                    viewModel.updateShopLinkDepartment(linkUi.link, deptId)
                    editingExistingLinkId = null
                },
                onDismiss = { editingExistingLinkId = null }
            )
        }
    }

    // --- Main content ---

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (state.isNew) {
                        Text("Новый товар")
                    } else {
                        Column {
                            Text(
                                text = state.name.ifBlank { "Товар" },
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Редактирование",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Удалить товар",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    TextButton(
                        onClick = { viewModel.save() },
                        enabled = state.name.isNotBlank() && !state.isSaving
                    ) {
                        Text("Сохранить")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if(state.isTemporary)TextButton(onClick=viewModel::keepInCatalog){Text("Сохранить разовую позицию в базе")}
            OutlinedTextField(
                value = state.name,
                onValueChange = { viewModel.updateName(it) },
                label = { Text("Название товара") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            DropdownSelector(
                label = "Товарная группа",
                items = state.groups,
                selectedItem = state.groups.find { it.id == state.productGroupId },
                itemLabel = { it.name },
                onSelect = { viewModel.updateProductGroup(it?.id) },
                modifier = Modifier.fillMaxWidth(),
                allowCreate = true,
                onCreateNew = { showNewGroupDialog = true },
                createLabel = "Новая группа..."
            )

            DropdownSelector(
                label = "Получатель",
                items = state.recipients,
                selectedItem = state.recipients.find { it.id == state.recipientId },
                itemLabel = { it.name },
                onSelect = { viewModel.updateRecipient(it?.id) },
                modifier = Modifier.fillMaxWidth()
            )

            QuantityUnitInputWithSuggestions(
                quantity = state.defaultQuantity,
                unit = state.defaultUnit,
                onQuantityChange = { viewModel.updateDefaultQuantity(it) },
                onUnitChange = { viewModel.updateDefaultUnit(it) },
                unitSuggestions = state.unitSuggestions,
                quantityLabel = "Кол-во по умолч.",
                unitLabel = "Ед. изм."
            )

            OutlinedTextField(
                value = state.note,
                onValueChange = { viewModel.updateNote(it) },
                label = { Text("Заметка") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.priceValue,
                    onValueChange = { value ->
                        if (value.isEmpty() || value.matches(Regex("^\\d*[.,]?\\d*$"))) {
                            viewModel.updatePriceValue(value)
                        }
                    },
                    label = { Text("Цена, ₽") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = state.priceDate,
                    onValueChange = { viewModel.updatePriceDate(it) },
                    label = { Text("Дата цены") },
                    placeholder = { Text("дд.мм.гггг") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            PurchaseTypeSelector(
                selectedType = state.purchaseType,
                onTypeChange = { viewModel.updatePurchaseType(it) }
            )

            if (state.purchaseType == "online") {
                OutlinedTextField(
                    value = state.sellerUrl,
                    onValueChange = { viewModel.updateSellerUrl(it) },
                    label = { Text("Ссылка на продавца") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.productUrl,
                    onValueChange = { viewModel.updateProductUrl(it) },
                    label = { Text("Ссылка на товар") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            PhotoPickerBlock(
                photoPath = state.photoUri,
                onPhotoSelected = { path -> viewModel.updatePhotoUri(path) },
                onPhotoRemoved = { viewModel.updatePhotoUri("") }
            )

            // --- Unified shop links section ---
            val sortedPendingLinks = state.pendingShopLinks.sortedBy { it.displayOrder }
            val sortedExistingLinks = state.shopLinks.sortedBy { it.link.priority }
            val hasAnyLinks = if (state.isNew) sortedPendingLinks.isNotEmpty() else sortedExistingLinks.isNotEmpty()

            EditableSection(
                title = "Привязанные магазины",
                isEditing = isShopLinksEditing,
                onToggleEdit = { isShopLinksEditing = it },
                isEmpty = !hasAnyLinks,
                emptyMessage = "Нет привязанных магазинов",
                viewContent = {
                    if (state.isNew) {
                        sortedPendingLinks.forEachIndexed { index, pendingLink ->
                            ShopLinkViewCard(
                                orderNumber = index + 1,
                                shopName = pendingLink.shopName,
                                departmentName = pendingLink.departmentName
                            )
                        }
                    } else {
                        sortedExistingLinks.forEachIndexed { index, linkUi ->
                            ShopLinkViewCard(
                                orderNumber = index + 1,
                                shopName = linkUi.shopName,
                                departmentName = linkUi.departmentName,
                                onEditDepartment = {
                                    editingExistingLinkId = linkUi.link.id
                                    viewModel.loadDepartmentsForShop(linkUi.link.shopId)
                                }
                            )
                        }
                    }
                },
                editContent = {
                    if (state.isNew) {
                        sortedPendingLinks.forEachIndexed { index, pendingLink ->
                            ShopLinkEditCard(
                                orderNumber = index + 1,
                                shopName = pendingLink.shopName,
                                departmentName = pendingLink.departmentName,
                                isFirst = index == 0,
                                isLast = index == sortedPendingLinks.size - 1,
                                onMoveUp = {
                                    viewModel.movePendingShopLinkUp(pendingLink.tempId)
                                    scope.launch { scrollState.animateScrollBy(-itemScrollPx) }
                                },
                                onMoveDown = {
                                    viewModel.movePendingShopLinkDown(pendingLink.tempId)
                                    scope.launch { scrollState.animateScrollBy(itemScrollPx) }
                                },
                                onRemove = { viewModel.removePendingShopLink(pendingLink.tempId) },
                                onEditDepartment = {
                                    editingPendingLinkId = pendingLink.tempId
                                    viewModel.loadDepartmentsForShop(pendingLink.shopId)
                                }
                            )
                        }
                    } else {
                        sortedExistingLinks.forEachIndexed { index, linkUi ->
                            ShopLinkEditCard(
                                orderNumber = index + 1,
                                shopName = linkUi.shopName,
                                departmentName = linkUi.departmentName,
                                isFirst = index == 0,
                                isLast = index == sortedExistingLinks.size - 1,
                                onMoveUp = {
                                    viewModel.moveShopLinkUp(linkUi.link)
                                    scope.launch { scrollState.animateScrollBy(-itemScrollPx) }
                                },
                                onMoveDown = {
                                    viewModel.moveShopLinkDown(linkUi.link)
                                    scope.launch { scrollState.animateScrollBy(itemScrollPx) }
                                },
                                onRemove = { viewModel.removeShopLink(linkUi.link) },
                                onEditDepartment = {
                                    editingExistingLinkId = linkUi.link.id
                                    viewModel.loadDepartmentsForShop(linkUi.link.shopId)
                                }
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { showAddShopLinkDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text("  Добавить магазин")
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ShopLinkViewCard(
    orderNumber: Int,
    shopName: String,
    departmentName: String?,
    onEditDepartment: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onEditDepartment != null) Modifier.clickable { onEditDepartment() } else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                "$orderNumber. $shopName",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                if (departmentName != null) "Группа магазина: $departmentName"
                else "Нажмите для выбора группы",
                style = MaterialTheme.typography.bodySmall,
                color = if (departmentName != null)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else
                    MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ShopLinkEditCard(
    orderNumber: Int,
    shopName: String,
    departmentName: String?,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onEditDepartment: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onEditDepartment() }
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    "$orderNumber. $shopName",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    if (departmentName != null) "Группа: $departmentName"
                    else "Нажмите для выбора группы",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (departmentName != null)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.primary
                )
            }
            IconButton(
                onClick = onMoveUp,
                enabled = !isFirst,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = "Вверх",
                    tint = if (!isFirst)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
            IconButton(
                onClick = onMoveDown,
                enabled = !isLast,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Вниз",
                    tint = if (!isLast)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Удалить связь"
                )
            }
        }
    }
}

@Composable
private fun AddShopLinkDialog(
    shops: List<ShopEntity>,
    existingShopIds: Set<Long>,
    onConfirm: (shopId: Long, departmentId: Long?) -> Unit,
    onDismiss: () -> Unit,
    onLoadDepartments: (Long) -> Unit,
    departmentsForShop: List<ShopDepartmentEntity>,
    suggestedDepartmentId: Long?,
    suggestedDepartmentReason: String?
) {
    val availableShops = shops.filter { it.id !in existingShopIds }
    var selectedShopId by remember { mutableLongStateOf(availableShops.firstOrNull()?.id ?: 0L) }
    var selectedDepartmentId by remember { mutableStateOf<Long?>(null) }
    var suggestionApplied by remember { mutableStateOf(false) }

    // Load departments when shop changes
    LaunchedEffect(selectedShopId) {
        if (selectedShopId > 0) {
            onLoadDepartments(selectedShopId)
            selectedDepartmentId = null
            suggestionApplied = false
        }
    }

    // Auto-select suggested department when it appears (user can change it)
    LaunchedEffect(suggestedDepartmentId) {
        if (suggestedDepartmentId != null && !suggestionApplied) {
            selectedDepartmentId = suggestedDepartmentId
            suggestionApplied = true
        }
    }

    val alreadyLinkedShops = shops.filter { it.id in existingShopIds }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить магазин") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (availableShops.isEmpty()) {
                    Text("Все магазины уже привязаны")
                } else {
                    DropdownSelector(
                        label = "Магазин",
                        items = availableShops,
                        selectedItem = availableShops.find { it.id == selectedShopId },
                        itemLabel = { it.name },
                        onSelect = { shop ->
                            shop?.let { selectedShopId = it.id }
                        }
                    )

                    if (departmentsForShop.isNotEmpty()) {
                        DropdownSelector(
                            label = "Группа магазина",
                            items = departmentsForShop,
                            selectedItem = departmentsForShop.find { it.id == selectedDepartmentId },
                            itemLabel = { it.name },
                            onSelect = { dept -> selectedDepartmentId = dept?.id }
                        )

                        if (suggestedDepartmentReason != null && selectedDepartmentId == suggestedDepartmentId) {
                            Text(
                                text = "Подсказка: $suggestedDepartmentReason",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (alreadyLinkedShops.isNotEmpty()) {
                    Text(
                        text = "Уже привязаны: ${alreadyLinkedShops.joinToString(", ") { it.name }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (selectedShopId > 0) {
                        onConfirm(selectedShopId, selectedDepartmentId)
                    }
                },
                enabled = availableShops.isNotEmpty() && selectedShopId > 0
            ) { Text("Добавить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun DepartmentSelectDialog(
    shopName: String,
    departments: List<ShopDepartmentEntity>,
    currentDepartmentId: Long?,
    suggestedDepartmentId: Long?,
    suggestedDepartmentReason: String?,
    onConfirm: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedDepartmentId by remember { mutableStateOf(currentDepartmentId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Группа магазина") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    shopName,
                    style = MaterialTheme.typography.titleSmall
                )

                if (departments.isEmpty()) {
                    Text(
                        "В этом магазине нет групп",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    DropdownSelector(
                        label = "Группа магазина",
                        items = departments,
                        selectedItem = departments.find { it.id == selectedDepartmentId },
                        itemLabel = { it.name },
                        onSelect = { dept -> selectedDepartmentId = dept?.id }
                    )

                    if (suggestedDepartmentReason != null && suggestedDepartmentId != null) {
                        val suggestedName = departments.find { it.id == suggestedDepartmentId }?.name
                        if (suggestedName != null) {
                            Text(
                                text = "Подсказка: $suggestedDepartmentReason",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = if (selectedDepartmentId != suggestedDepartmentId) {
                                    Modifier.clickable { selectedDepartmentId = suggestedDepartmentId }
                                } else {
                                    Modifier
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedDepartmentId) }) {
                Text("Применить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun PhotoPickerBlock(
    photoPath: String,
    onPhotoSelected: (String) -> Unit,
    onPhotoRemoved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var photoError by remember { mutableStateOf<String?>(null) }
    var photoBusy by remember { mutableStateOf(false) }
    val imageBitmap by produceState<ImageBitmap?>(null, photoPath) {
        value = if (photoPath.isBlank()) null else {
            withContext(Dispatchers.IO) {
                runCatching { com.spisokryadom.app.util.PhotoStore.load(photoPath)?.asImageBitmap() }.getOrNull()
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            photoBusy=true;photoError=null
            scope.launch(Dispatchers.IO) {
                val newPath = copyImageToLocalStorage(
                    context, selectedUri, photoPath.ifBlank { null }
                )
                withContext(Dispatchers.Main) { photoBusy=false;if(newPath!=null)onPhotoSelected(newPath)else photoError="Не удалось прочитать фото. Выберите другой файл." }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        photoError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        if(photoBusy)androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(
            text = "Фото товара",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap!!,
                contentDescription = "Фото товара",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Фото не выбрано",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { launcher.launch("image/*") },
                enabled = !photoBusy,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                Text(if (photoPath.isBlank()) "  Выбрать фото" else "  Заменить фото")
            }
            if (photoPath.isNotBlank()) {
                OutlinedButton(
                    onClick = { onPhotoRemoved() }
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Удалить фото",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

private fun copyImageToLocalStorage(
    context: Context,
    sourceUri: Uri,
    @Suppress("UNUSED_PARAMETER") oldPath: String?
): String? {
    return runCatching { com.spisokryadom.app.util.PhotoStore.importPhoto(context,sourceUri) }.getOrNull()
}
