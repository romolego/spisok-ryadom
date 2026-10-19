package com.spisokryadom.app.ui.goallists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spisokryadom.app.data.entity.GoalListEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GoalSelectionActions(count: Int, hasPending: Boolean, hasBought: Boolean, onBought: (Boolean) -> Unit, onMove: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("Выбрано: $count", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(12.dp))
            if (hasPending) ListItem(headlineContent = { Text("Отметить купленными") }, leadingContent = { Icon(Icons.Filled.DoneAll, null) }, modifier = Modifier.clickable { onBought(true) })
            if (hasBought) ListItem(headlineContent = { Text("Вернуть в покупки") }, leadingContent = { Icon(Icons.Filled.Undo, null) }, modifier = Modifier.clickable { onBought(false) })
            ListItem(headlineContent = { Text("В другой список") }, leadingContent = { Icon(Icons.Filled.DriveFileMove, null) }, modifier = Modifier.clickable(onClick = onMove))
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            ListItem(headlineContent = { Text("Удалить выбранное", color = MaterialTheme.colorScheme.error) }, leadingContent = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) }, modifier = Modifier.clickable(onClick = onDelete))
        }
    }
}

@Composable
internal fun GoalMoveSelectionDialog(currentId: Long, lists: List<GoalListEntity>, onMove: (Long?) -> Unit, onDismiss: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("В другой список") }, text = {
        Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            GoalDestinationRow("Текущие покупки", selected == null) { selected = null }
            lists.filter { it.id != currentId }.forEach { list -> GoalDestinationRow(list.name, selected == list.id) { selected = list.id } }
        }
    }, confirmButton = { TextButton(onClick = { onMove(selected) }) { Text("Переместить") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}

@Composable
private fun GoalDestinationRow(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RadioButton(selected, onClick); Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}
