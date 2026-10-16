package com.spisokryadom.app.ui.shopcard

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spisokryadom.app.data.entity.ShopDepartmentEntity
import com.spisokryadom.app.ui.components.ConfirmDialog
import com.spisokryadom.app.ui.components.EditableSection
import com.spisokryadom.app.ui.components.InputDialogWithSuggestions
import com.spisokryadom.app.ui.components.MultiSelectDialog
import com.spisokryadom.app.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopCardScreen(
    shopId: Long,
    onNavigateBack: () -> Unit,
    viewModel: ShopCardViewModel = viewModel(factory = ShopCardViewModel.factory(shopId))
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val itemScrollPx = with(LocalDensity.current) { 52.dp.toPx() }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onNavigateBack()
    }
    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) onNavigateBack()
    }

    var showAddDepartmentDialog by remember { mutableStateOf(false) }
    var renamingDepartment by remember { mutableStateOf<ShopDepartmentEntity?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isDepartmentsEditing by remember { mutableStateOf(false) }

    if (showAddDepartmentDialog) {
        val existingDepartmentNames = if (!state.isNew) {
            state.departments.map { it.name }.toSet()
        } else {
            state.pendingDepartments.map { it.name }.toSet()
        }
        MultiSelectDialog(
            title = "Добавить группы магазина",
            availableItems = state.allDepartmentNames,
            alreadyAddedItems = existingDepartmentNames,
            newItemLabel = "Новая группа",
            onConfirm = { names ->
                viewModel.addDepartments(names)
                showAddDepartmentDialog = false
            },
            onDismiss = { showAddDepartmentDialog = false }
        )
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "Удалить магазин",
            message = "Вы уверены, что хотите удалить магазин «${state.name}»? Все группы магазина будут удалены.",
            onConfirm = {
                viewModel.deleteShop()
                showDeleteConfirm = false
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    renamingDepartment?.let { dept ->
        InputDialogWithSuggestions(
            title = "Переименовать группу",
            initialValue = dept.name,
            label = "Название группы",
            suggestions = state.allDepartmentNames,
            onConfirm = { newName ->
                viewModel.updateDepartment(dept.copy(name = newName.trim()))
                renamingDepartment = null
            },
            onDismiss = { renamingDepartment = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (state.isNew) "Новый магазин" else "Редактирование магазина")
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
                                contentDescription = "Удалить магазин",
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
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = { viewModel.updateName(it) },
                label = { Text("Название магазина") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.address,
                onValueChange = { viewModel.updateAddress(it) },
                label = { Text("Адрес") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.note,
                onValueChange = { viewModel.updateNote(it) },
                label = { Text("Заметка") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )

            // ── Настройки срочности ──────────────────────────────────────
            SectionHeader(title = "Настройки срочности")

            Text(
                "Локальные настройки этого магазина. Имеют приоритет над глобальными.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Show urgent in collapsed
            Text(
                "Срочные товары в свернутом магазине",
                style = MaterialTheme.typography.bodyMedium
            )
            UrgencyOverride.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.updateShowUrgentInCollapsed(option) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = state.showUrgentInCollapsed == option,
                        onClick = { viewModel.updateShowUrgentInCollapsed(option) }
                    )
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Boost urgent shop
            Text(
                "Поднимать магазин при наличии срочных",
                style = MaterialTheme.typography.bodyMedium
            )
            UrgencyOverride.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.updateBoostUrgentShop(option) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = state.boostUrgentShop == option,
                        onClick = { viewModel.updateBoostUrgentShop(option) }
                    )
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Группы магазина
            if (!state.isNew) {
                val sortedDepartments = state.departments.sortedBy { it.displayOrder }

                EditableSection(
                    title = "Группы магазина",
                    isEditing = isDepartmentsEditing,
                    onToggleEdit = { isDepartmentsEditing = it },
                    isEmpty = state.departments.isEmpty(),
                    emptyMessage = "Нет групп",
                    viewContent = {
                        sortedDepartments.forEach { dept ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = dept.name,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    },
                    editContent = {
                        sortedDepartments.forEachIndexed { index, dept ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = dept.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )

                                    // Переместить вверх
                                    if (index > 0) {
                                        IconButton(
                                            onClick = {
                                                viewModel.moveDepartmentUp(dept)
                                                scope.launch { scrollState.animateScrollBy(-itemScrollPx) }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.KeyboardArrowUp,
                                                contentDescription = "Переместить вверх",
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.size(32.dp))
                                    }

                                    // Переместить вниз
                                    if (index < sortedDepartments.size - 1) {
                                        IconButton(
                                            onClick = {
                                                viewModel.moveDepartmentDown(dept)
                                                scope.launch { scrollState.animateScrollBy(itemScrollPx) }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.KeyboardArrowDown,
                                                contentDescription = "Переместить вниз",
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.size(32.dp))
                                    }

                                    // Переименовать
                                    IconButton(
                                        onClick = { renamingDepartment = dept },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Edit,
                                            contentDescription = "Переименовать",
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // Удалить
                                    IconButton(
                                        onClick = { viewModel.deleteDepartment(dept) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Удалить",
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }

                        if (sortedDepartments.size >= 2) {
                            OutlinedButton(
                                onClick = { viewModel.sortDepartmentsAlphabetically() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.SortByAlpha, contentDescription = null)
                                Text("  По алфавиту")
                            }
                        }

                        OutlinedButton(
                            onClick = { showAddDepartmentDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Text("  Добавить группы")
                        }
                    }
                )
            } else {
                // New shop: pending departments
                val sortedPending = state.pendingDepartments.sortedBy { it.displayOrder }

                var renamingPendingId by remember { mutableStateOf<Long?>(null) }

                renamingPendingId?.let { pendingId ->
                    val pendingDept = state.pendingDepartments.find { it.tempId == pendingId }
                    if (pendingDept != null) {
                        InputDialogWithSuggestions(
                            title = "Переименовать группу",
                            initialValue = pendingDept.name,
                            label = "Название группы",
                            suggestions = state.allDepartmentNames,
                            onConfirm = { newName ->
                                viewModel.renamePendingDepartment(pendingId, newName)
                                renamingPendingId = null
                            },
                            onDismiss = { renamingPendingId = null }
                        )
                    }
                }

                EditableSection(
                    title = "Группы магазина",
                    isEditing = isDepartmentsEditing,
                    onToggleEdit = { isDepartmentsEditing = it },
                    isEmpty = sortedPending.isEmpty(),
                    emptyMessage = "Нет групп",
                    viewContent = {
                        sortedPending.forEach { pending ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = pending.name,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    },
                    editContent = {
                        sortedPending.forEachIndexed { index, pending ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = pending.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )

                                    if (index > 0) {
                                        IconButton(
                                            onClick = {
                                                viewModel.movePendingDepartmentUp(pending.tempId)
                                                scope.launch { scrollState.animateScrollBy(-itemScrollPx) }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.KeyboardArrowUp,
                                                contentDescription = "Переместить вверх",
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.size(32.dp))
                                    }

                                    if (index < sortedPending.size - 1) {
                                        IconButton(
                                            onClick = {
                                                viewModel.movePendingDepartmentDown(pending.tempId)
                                                scope.launch { scrollState.animateScrollBy(itemScrollPx) }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.KeyboardArrowDown,
                                                contentDescription = "Переместить вниз",
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.size(32.dp))
                                    }

                                    IconButton(
                                        onClick = { renamingPendingId = pending.tempId },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Edit,
                                            contentDescription = "Переименовать",
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    IconButton(
                                        onClick = { viewModel.deletePendingDepartment(pending.tempId) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Удалить",
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }

                        if (sortedPending.size >= 2) {
                            OutlinedButton(
                                onClick = { viewModel.sortPendingDepartmentsAlphabetically() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.SortByAlpha, contentDescription = null)
                                Text("  По алфавиту")
                            }
                        }

                        OutlinedButton(
                            onClick = { showAddDepartmentDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Text("  Добавить группы")
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
