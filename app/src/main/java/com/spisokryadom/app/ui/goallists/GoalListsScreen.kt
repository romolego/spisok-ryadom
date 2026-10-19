package com.spisokryadom.app.ui.goallists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spisokryadom.app.ui.components.InputDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalListsScreen(
    onOpenRegular: () -> Unit, onOpenList: (Long) -> Unit,
    viewModel: GoalListsViewModel? = null
) {
    val revision = com.spisokryadom.app.ui.batch.rememberDatabaseRevision()
    key(revision) {
        val model = viewModel ?: androidx.lifecycle.viewmodel.compose.viewModel<GoalListsViewModel>(key = "goal-lists-$revision", factory = GoalListsViewModel.Factory)
        GoalListsContent(onOpenRegular, onOpenList, model)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalListsContent(
    onOpenRegular: () -> Unit, onOpenList: (Long) -> Unit, viewModel: GoalListsViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var create by rememberSaveable { mutableStateOf(false) }
    var importSession by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state.createdId) { state.createdId?.let { viewModel.clearNavigation(); onOpenList(it) } }
    if (create) InputDialog("Новый список", label = "Название списка", onConfirm = { create = false; viewModel.create(it) }, onDismiss = { create = false })
    importSession?.let { session -> GoalImportSheet(session, onDismiss = { importSession = null }, onImported = { importSession = null; onOpenList(it) }) }
    state.error?.let { GoalErrorDialog(it, viewModel::clearError) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Списки") }, actions = {
            IconButton(onClick = { importSession = java.util.UUID.randomUUID().toString() }, enabled = !state.busy) { Icon(Icons.Filled.Download, "Импортировать список") }
        }) },
        floatingActionButton = { FloatingActionButton(onClick = { create = true }) { Icon(Icons.Filled.Add, "Создать список") } }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).testTag("goal-lists"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "regular") {
                Card(Modifier.fillMaxWidth().clickable(onClick = onOpenRegular).testTag("goal-regular"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(Icons.Filled.ShoppingCart, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Column(Modifier.weight(1f)) {
                            Text("Текущие покупки", style = MaterialTheme.typography.titleMedium)
                            Text("Основной список", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                    }
                }
            }
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (!state.loading && state.lists.isEmpty()) item {
                Text("Отдельные списки", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                Text("Пока нет списков", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
            items(state.lists, key = { it.id }) { list ->
                OutlinedCard(Modifier.fillMaxWidth().clickable { onOpenList(list.id) }.testTag("goal-list-${list.id}")) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(Icons.Filled.ListAlt, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(list.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(if (list.showInPurchases) "Отображается в покупках" else "Отдельный список", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
internal fun GoalErrorDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, text = { Text(message) }, confirmButton = { TextButton(onClick = onDismiss) { Text("Понятно") } })
}
