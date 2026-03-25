package com.spisokryadom.app.ui.shopcard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spisokryadom.app.data.entity.ShopDepartmentEntity
import com.spisokryadom.app.ui.components.ConfirmDialog
import com.spisokryadom.app.ui.components.EditableSection
import com.spisokryadom.app.ui.components.InputDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopCardScreen(
    shopId: Long,
    onNavigateBack: () -> Unit,
    viewModel: ShopCardViewModel = viewModel(factory = ShopCardViewModel.factory(shopId))
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onNavigateBack()
    }
    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) onNavigateBack()
    }

    var showAddDepartmentDialog by remember { mutableStateOf(false) }
    var editingDepartment by remember { mutableStateOf<ShopDepartmentEntity?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isDepartmentsEditing by remember { mutableStateOf(false) }

    if (showAddDepartmentDialog) {
        InputDialog(
            title = "Новая группа магазина",
            label = "Название группы",
            onConfirm = { name ->
                viewModel.addDepartment(name)
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

    editingDepartment?.let { dept ->
        EditDepartmentDialog(
            department = dept,
            onConfirm = { updatedDept ->
                viewModel.updateDepartment(updatedDept)
                editingDepartment = null
            },
            onDismiss = { editingDepartment = null }
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
                .verticalScroll(rememberScrollState()),
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

            OutlinedTextField(
                value = state.displayOrder,
                onValueChange = { value ->
                    if (value.isEmpty() || value.matches(Regex("^\\d+$"))) {
                        viewModel.updateDisplayOrder(value)
                    }
                },
                label = { Text("Порядок отображения") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Departments section (only for saved shops)
            if (!state.isNew) {
                EditableSection(
                    title = "Группы магазина",
                    isEditing = isDepartmentsEditing,
                    onToggleEdit = { isDepartmentsEditing = it },
                    isEmpty = state.departments.isEmpty(),
                    emptyMessage = "Нет групп",
                    viewContent = {
                        state.departments.forEach { dept ->
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
                                        text = "${dept.displayOrder}. ${dept.name}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    },
                    editContent = {
                        state.departments.forEach { dept ->
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
                                        text = "${dept.displayOrder}. ${dept.name}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { editingDepartment = dept }) {
                                        Icon(
                                            Icons.Filled.Edit,
                                            contentDescription = "Редактировать",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { viewModel.deleteDepartment(dept) }) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Удалить",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { showAddDepartmentDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Text("  Добавить группу")
                        }
                    }
                )
            } else {
                Text(
                    "Группы магазина можно добавить после сохранения магазина",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EditDepartmentDialog(
    department: ShopDepartmentEntity,
    onConfirm: (ShopDepartmentEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(department.name) }
    var order by remember { mutableStateOf(department.displayOrder.toString()) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Редактировать группу") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название группы") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = order,
                    onValueChange = { value ->
                        if (value.isEmpty() || value.matches(Regex("^\\d+$"))) {
                            order = value
                        }
                    },
                    label = { Text("Порядок") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(
                            department.copy(
                                name = name.trim(),
                                displayOrder = order.toIntOrNull() ?: department.displayOrder
                            )
                        )
                    }
                },
                enabled = name.isNotBlank()
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
