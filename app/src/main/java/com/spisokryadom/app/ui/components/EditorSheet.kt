package com.spisokryadom.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** A common editor/selector surface: the body scrolls, actions stay above the keyboard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorSheet(
    title: String,
    onDismiss: () -> Unit,
    busy: Boolean = false,
    scrollContent: Boolean = true,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val locked by rememberUpdatedState(busy)
    val maxHeight = (LocalConfiguration.current.screenHeightDp - 64).coerceAtLeast(200).dp
    ModalBottomSheet(
        onDismissRequest = { if (!locked) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
            confirmValueChange = { it != SheetValue.Hidden || !locked })
    ) {
        androidx.activity.compose.BackHandler(enabled = busy) {}
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).imePadding()
            .testTag("editor-sheet").padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            val body = Modifier.fillMaxWidth().weight(1f, false)
            Column(if (scrollContent) body.verticalScroll(rememberScrollState()) else body,
                verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
            Row(Modifier.fillMaxWidth().testTag("editor-sheet-actions"),
                horizontalArrangement = Arrangement.End, content = actions)
        }
    }
}
