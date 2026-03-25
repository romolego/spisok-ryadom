package com.spisokryadom.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spisokryadom.app.data.backup.BackupManager
import com.spisokryadom.app.ui.components.ConfirmDialog
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
    var showImportConfirm by remember { mutableStateOf(false) }
    var pendingImportJson by remember { mutableStateOf<String?>(null) }

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
        } catch (e: Exception) {
            // file read error handled silently; user sees no change
        }
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
            } catch (e: Exception) {
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
                title = { Text("Экспорт / Импорт") },
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
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(
                text = "Экспорт",
                style = MaterialTheme.typography.titleMedium
            )
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

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Импорт",
                style = MaterialTheme.typography.titleMedium
            )
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
    }
}
