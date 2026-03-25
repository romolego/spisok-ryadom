package com.spisokryadom.app.ui.productcard

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
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
    onNavigateBack: () -> Unit,
    viewModel: ProductCardViewModel = viewModel(factory = ProductCardViewModel.factory(productId))
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onNavigateBack()
    }
    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) onNavigateBack()
    }

    var showNewGroupDialog by remember { mutableStateOf(false) }
    var showNewRecipientDialog by remember { mutableStateOf(false) }
    var showAddShopLinkDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isShopLinksEditing by remember { mutableStateOf(false) }

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

    if (showNewRecipientDialog) {
        InputDialog(
            title = "Новый получатель",
            label = "Название",
            onConfirm = { name ->
                viewModel.createRecipient(name)
                showNewRecipientDialog = false
            },
            onDismiss = { showNewRecipientDialog = false }
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
        AddShopLinkDialog(
            shops = state.shops,
            existingShopIds = state.shopLinks.map { it.link.shopId }.toSet(),
            onConfirm = { shopId, priority, departmentId ->
                viewModel.addShopLink(shopId, priority, departmentId)
                showAddShopLinkDialog = false
            },
            onDismiss = { showAddShopLinkDialog = false },
            onLoadDepartments = { shopId -> viewModel.loadDepartmentsForShop(shopId) },
            departmentsForShop = state.departmentsForSelectedShop
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (state.isNew) "Новый товар" else "Редактирование товара")
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
                        enabled = state.name.isNotBlank()
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                modifier = Modifier.fillMaxWidth(),
                allowCreate = true,
                onCreateNew = { showNewRecipientDialog = true },
                createLabel = "Новый получатель..."
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
                        if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) {
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

            // Shop links section (only for existing products)
            if (!state.isNew) {
                EditableSection(
                    title = "Привязанные магазины",
                    isEditing = isShopLinksEditing,
                    onToggleEdit = { isShopLinksEditing = it },
                    isEmpty = state.shopLinks.isEmpty(),
                    emptyMessage = "Нет привязанных магазинов",
                    viewContent = {
                        state.shopLinks.forEach { linkUi ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
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
                                        linkUi.shopName,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    val details = buildList {
                                        add("Приоритет: ${linkUi.link.priority}")
                                        linkUi.departmentName?.let { add("Группа магазина: $it") }
                                    }
                                    Text(
                                        details.joinToString(" \u2022 "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    },
                    editContent = {
                        state.shopLinks.forEach { linkUi ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            linkUi.shopName,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        val details = buildList {
                                            add("Приоритет: ${linkUi.link.priority}")
                                            linkUi.departmentName?.let { add("Группа магазина: $it") }
                                        }
                                        Text(
                                            details.joinToString(" \u2022 "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { viewModel.removeShopLink(linkUi.link) }) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Удалить связь"
                                        )
                                    }
                                }
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
            } else {
                Text(
                    "Привязка к магазинам будет доступна после сохранения товара",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PhotoPickerBlock(
    photoPath: String,
    onPhotoSelected: (String) -> Unit,
    onPhotoRemoved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val imageBitmap by produceState<ImageBitmap?>(null, photoPath) {
        value = if (photoPath.isBlank()) null else {
            withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(photoPath)?.asImageBitmap() }.getOrNull()
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            scope.launch(Dispatchers.IO) {
                val newPath = copyImageToLocalStorage(
                    context, selectedUri, photoPath.ifBlank { null }
                )
                newPath?.let { withContext(Dispatchers.Main) { onPhotoSelected(it) } }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                Text(if (photoPath.isBlank()) "  Выбрать фото" else "  Заменить фото")
            }
            if (photoPath.isNotBlank()) {
                OutlinedButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            File(photoPath).delete()
                            withContext(Dispatchers.Main) { onPhotoRemoved() }
                        }
                    }
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
    oldPath: String?
): String? {
    return try {
        oldPath?.let { File(it).delete() }
        val photosDir = File(context.filesDir, "product_photos")
        photosDir.mkdirs()
        val destFile = File(photosDir, "${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        destFile.absolutePath
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun AddShopLinkDialog(
    shops: List<ShopEntity>,
    existingShopIds: Set<Long>,
    onConfirm: (shopId: Long, priority: Int, departmentId: Long?) -> Unit,
    onDismiss: () -> Unit,
    onLoadDepartments: (Long) -> Unit,
    departmentsForShop: List<ShopDepartmentEntity>
) {
    val availableShops = shops.filter { it.id !in existingShopIds }
    var selectedShopId by remember { mutableLongStateOf(availableShops.firstOrNull()?.id ?: 0L) }
    var priority by remember { mutableStateOf("1") }
    var selectedDepartmentId by remember { mutableStateOf<Long?>(null) }

    // Load departments when shop changes
    LaunchedEffect(selectedShopId) {
        if (selectedShopId > 0) {
            onLoadDepartments(selectedShopId)
            selectedDepartmentId = null
        }
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить магазин") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (availableShops.isEmpty()) {
                    Text("Нет доступных магазинов для привязки")
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

                    OutlinedTextField(
                        value = priority,
                        onValueChange = { value ->
                            if (value.isEmpty() || value.matches(Regex("^\\d+$"))) {
                                priority = value
                            }
                        },
                        label = { Text("Приоритет (1 = наивысший)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (departmentsForShop.isNotEmpty()) {
                        DropdownSelector(
                            label = "Группа магазина",
                            items = departmentsForShop,
                            selectedItem = departmentsForShop.find { it.id == selectedDepartmentId },
                            itemLabel = { it.name },
                            onSelect = { dept -> selectedDepartmentId = dept?.id }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (selectedShopId > 0) {
                        onConfirm(
                            selectedShopId,
                            priority.toIntOrNull() ?: 1,
                            selectedDepartmentId
                        )
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
