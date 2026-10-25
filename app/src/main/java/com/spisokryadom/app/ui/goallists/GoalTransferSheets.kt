package com.spisokryadom.app.ui.goallists
import com.spisokryadom.app.ui.components.AppTextField

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GoalTextSheet(title: String, text: String, onText: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.94f).imePadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Поделиться списком", style = MaterialTheme.typography.headlineSmall)
            Text("Правки сообщения не меняют список.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppTextField(text, onText, label = { Text("Текст списка") }, modifier = Modifier.fillMaxWidth().weight(1f).testTag("goal-share-editor"), shape = MaterialTheme.shapes.large)
            if (goalShareUsesFile(text)) Text("Большой список будет отправлен текстовым файлом. Его можно импортировать в приложении.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                try {
                    context.startActivity(Intent.createChooser(goalShareIntent(context, title, text), "Поделиться списком"))
                } catch (e: Exception) { error = e.message ?: "Не удалось открыть отправку" }
            }, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth().testTag("goal-share-send")) {
                Icon(Icons.Filled.Share, null); Spacer(Modifier.width(8.dp)); Text("Поделиться")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Готово") }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GoalImportSheet(session: String, onDismiss: () -> Unit, onImported: (Long) -> Unit,
    viewModel: GoalImportViewModel = viewModel(key = "goal-import-$session-${com.spisokryadom.app.ui.batch.rememberDatabaseRevision()}", factory = GoalImportViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val file = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { viewModel.file(context.contentResolver, it) } }
    LaunchedEffect(state.importedId) { state.importedId?.let(onImported) }
    fun dismiss() { if (!state.busy) { viewModel.discard(); onDismiss() } }
    val busy by rememberUpdatedState(state.busy)
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != SheetValue.Hidden || !busy })
    ModalBottomSheet(onDismissRequest = ::dismiss, sheetState = sheet) {
        BackHandler(enabled = state.busy) { }
        Column(Modifier.fillMaxWidth().fillMaxHeight(.95f).imePadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Импорт списка", style = MaterialTheme.typography.headlineSmall)
            LazyColumn(Modifier.weight(1f).testTag("goal-import-preview"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item(key = "file") { OutlinedButton(onClick = { file.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Download, null); Spacer(Modifier.width(8.dp)); Text("Выбрать файл")
                } }
                item(key = "text") { AppTextField(state.text, viewModel::text, enabled = !state.busy, label = { Text("Вставьте текст списка") }, minLines = 5, maxLines = 10, modifier = Modifier.fillMaxWidth().testTag("goal-import-editor"), shape = MaterialTheme.shapes.large) }
                state.error?.let { item(key = "error") { Text(it, color = MaterialTheme.colorScheme.error) } }
                state.preview?.let { preview ->
                    item(key = "problems") { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        preview.errors.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                        preview.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } }
                    preview.document?.let { document ->
                        item(key = "summary") { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(document.name, style = MaterialTheme.typography.titleLarge)
                            Text("Позиций: ${document.items.size}", style = MaterialTheme.typography.bodyMedium)
                            Text("Будет создан отдельный список, скрытый из текущих покупок. Отметки купленного сбросятся, срочность сохранится; магазины — подписи без привязок.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } }
                        itemsIndexed(document.items, key = { index, _ -> "item-$index" }) { index, item ->
                            OutlinedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("${index + 1}. ${item.name}", style = MaterialTheme.typography.bodyLarge)
                                    if (item.isUrgent) Text("Срочно", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                    Text("${goalNumber(item.quantity)} ${item.unit}".trim(), style = MaterialTheme.typography.bodySmall)
                                    listOf(item.shopName, item.shopAddress).filter { it.isNotBlank() }.joinToString(" · ").takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    item.unitPrice?.let { Text("${goalMoney(it)} / ед. · ${goalMoney(com.spisokryadom.app.util.PurchaseMoney.total(it, item.quantity))}", style = MaterialTheme.typography.bodySmall) }
                                    item.note.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    item.comment.takeIf { it.isNotBlank() }?.let { Text("Комментарий: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                            }
                        }
                    }
                }
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.preview?.canImport == true) Button(onClick = viewModel::confirm, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("goal-import-confirm")) { Text("Импортировать новый список") }
            else Button(onClick = viewModel::preview, enabled = !state.busy && state.text.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Проверить список") }
            TextButton(onClick = ::dismiss, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Отмена") }
            Spacer(Modifier.height(8.dp))
        }
    }
}
