package com.spisokryadom.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShoppingAddButton(onAdd: () -> Unit, onBulk: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    Box {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(20.dp),
            shadowElevation = 3.dp,
            modifier = Modifier.size(56.dp).combinedClickable(
                role = Role.Button,
                onClickLabel = "Добавить покупку",
                onClick = onAdd,
                onLongClickLabel = "Открыть способы добавления",
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); expanded = true }
            )
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Add, "Добавить в список") }
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Текстовый список") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, null) },
                onClick = { expanded = false; onBulk() }
            )
        }
    }
}
