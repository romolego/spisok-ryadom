package com.spisokryadom.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ReorderButtons(
    onUp: () -> Unit,
    onDown: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    upDescription: String = "Переместить вверх",
    downDescription: String = "Переместить вниз"
) {
    Row {
        listOf(
            Triple(Icons.Filled.ArrowUpward, upDescription, canMoveUp) to onUp,
            Triple(Icons.Filled.ArrowDownward, downDescription, canMoveDown) to onDown
        ).forEach { (action, onClick) ->
            IconButton(onClick = onClick, enabled = action.third, modifier = Modifier.size(48.dp)) {
                Icon(
                    action.first,
                    contentDescription = action.second,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (action.third) 1f else .3f)
                )
            }
        }
    }
}
