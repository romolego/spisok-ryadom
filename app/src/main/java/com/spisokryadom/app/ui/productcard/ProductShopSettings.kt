package com.spisokryadom.app.ui.productcard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.spisokryadom.app.data.entity.ShopBranchEntity
import com.spisokryadom.app.ui.components.*

/** Shop choices use the same scrollable editor sheet as other multi-field settings. */
@Composable
internal fun ProductShopDialog(
    title:String, confirmLabel:String, onConfirm:()->Unit, onDismiss:()->Unit,
    canConfirm:Boolean, busy:Boolean, content:@Composable ColumnScope.()->Unit
) {
    EditorSheet(title,onDismiss,busy=busy,actions={
        TextButton(onClick=onDismiss,enabled=!busy){Text("Отмена")}
        Spacer(Modifier.width(8.dp))
        TextButton(onClick=onConfirm,enabled=canConfirm&&!busy){Text(confirmLabel)}
    },content=content)
}

/** Null selection means all current and future outlets; priority never restricts availability. */
@Composable
internal fun ProductOutletOptions(
    branches: List<ShopBranchEntity>,
    selected: Set<Long>?,
    onSelection: (Set<Long>?) -> Unit,
    priority: List<Long>,
    onPriority: (List<Long>) -> Unit,
    enabled: Boolean = true
) {
    if (branches.size < 2) return
    val labels=LocalPlaceLabels.current
    val points = branches.sortedWith(compareBy<ShopBranchEntity> { it.displayOrder }.thenBy { it.id })
    val ranked = points.filter { selected == null || it.id in selected }.sortedWith(
        compareBy<ShopBranchEntity> { priority.indexOf(it.id).takeIf { it >= 0 } ?: Int.MAX_VALUE }
            .thenBy { it.displayOrder }.thenBy { it.id })
    var editingPriority by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DropdownSelector(
            label = "Доступность товара", items = listOf(false, true),
            selectedItem = selected != null, allowNone = false,
            itemLabel = { if (it) "Только выбранные точки" else "Во всех точках" },
            onSelect = { if (enabled) onSelection(if (it == true) points.map { p -> p.id }.toSet() else null) },
            modifier = Modifier.fillMaxWidth().testTag("product-outlet-mode")
        )
        if (selected != null) {
            points.forEach { point ->
                SelectionRow(labels.branch(point), point.id in selected, { checked ->
                    onSelection(if (checked) selected + point.id else selected - point.id)
                }, tag = "branch-choice-${point.id}", enabled = enabled)
            }
            if (selected.isEmpty()) Text("Выберите хотя бы одну точку", color = MaterialTheme.colorScheme.error)
        }
        if (ranked.size > 1) {
            TextButton(onClick = { editingPriority = !editingPriority }, enabled = enabled) {
                Text(if (editingPriority) "Готово: приоритет точек" else "Приоритет точек")
            }
            if (editingPriority) AnimatedOrderColumn(ranked, { it.id }, spacing = 0.dp) { index, point ->
                fun move(delta: Int) {
                    val ids = ranked.map { it.id }.toMutableList()
                    val target = index + delta
                    if (target in ids.indices) {
                        ids.add(target, ids.removeAt(index))
                        // Keep priorities of temporarily excluded outlets for a later return to all outlets.
                        onPriority(ids + priority.filter { it !in ids && points.any { p -> p.id == it } })
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(labels.branch(point), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    ReorderButtons({ move(-1) }, { move(1) }, enabled && index > 0, enabled && index < ranked.lastIndex,
                        "Выше приоритет точки: ${labels.branch(point)}", "Ниже приоритет точки: ${labels.branch(point)}")
                }
            }
        }
    }
}
