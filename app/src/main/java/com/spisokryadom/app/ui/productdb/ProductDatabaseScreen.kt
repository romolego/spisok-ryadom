package com.spisokryadom.app.ui.productdb

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spisokryadom.app.data.entity.ProductEntity
import com.spisokryadom.app.ui.components.InlineQuantityControl

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProductDatabaseScreen(
    onNavigateToProductCard: (Long) -> Unit,
    onCreateNewProduct: () -> Unit,
    viewModel: ProductDatabaseViewModel = viewModel(factory = ProductDatabaseViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    state.error?.let { message -> androidx.compose.material3.AlertDialog(onDismissRequest=viewModel::clearError,text={Text(message)},confirmButton={androidx.compose.material3.TextButton(onClick=viewModel::clearError){Text("Понятно")}}) }
    Scaffold(
        topBar = {
            TopAppBar(title = { Column { Text("База товаров"); com.spisokryadom.app.ui.components.CityPicker() } })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateNewProduct) {
                Icon(Icons.Filled.Add, contentDescription = "Новый товар")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Поиск по названию...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Очистить")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            ProductViewModeSwitcher(
                currentMode = state.viewMode,
                onModeSelected = { viewModel.setViewMode(it) },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // Summary
            val totalCount = state.products.size
            if (totalCount > 0) {
                Text(
                    text = "Товаров: $totalCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
                )
            }

            if (state.products.isEmpty() && !state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (state.searchQuery.isNotEmpty()) "Ничего не найдено"
                        else "Нет товаров",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.sections.forEachIndexed { sectionIndex, section ->
                        // Section header — key includes index to prevent duplicates if two sections share the same name
                        stickyHeader(key = "header_${section.key}") {
                            SectionHeader(
                                title = section.name,
                                count = section.products.size
                            )
                        }

                        // Key includes section name to prevent duplicate keys when the same product
                        // appears in multiple sections (e.g. BY_SHOPS mode with multi-shop products)
                        items(section.products, key = { "${section.key}_${it.id}" }, contentType = { "product" }) { product ->
                            ProductListItem(
                                product = product,
                                shoppingListInfo = state.shoppingListMap[product.id],
                                onCartClick = { viewModel.quickAddToList(product) },
                                onIncrement = { viewModel.incrementQuantity(product.id) },
                                onDecrement = { viewModel.decrementQuantity(product.id) },
                                onSetQuantity = { qty -> viewModel.setQuantity(product.id, qty) },
                                onLongPress = { onNavigateToProductCard(product.id) }
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProductViewModeSwitcher(
    currentMode: ProductViewMode,
    onModeSelected: (ProductViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ProductViewMode.entries.forEach { mode ->
            FilterChip(
                selected = mode == currentMode,
                onClick = { onModeSelected(mode) },
                label = { Text(mode.label) },
                leadingIcon = {
                    Icon(
                        imageVector = productViewModeIcon(mode),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }
    }
}

private fun productViewModeIcon(mode: ProductViewMode): ImageVector = when (mode) {
    ProductViewMode.ALPHABETICAL -> Icons.Filled.SortByAlpha
    ProductViewMode.BY_SHOPS -> Icons.Filled.Store
    ProductViewMode.BY_GROUPS -> Icons.Filled.Label
    ProductViewMode.BY_RECIPIENTS -> Icons.Filled.Person
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProductListItem(
    product: ProductEntity,
    shoppingListInfo: ShoppingListInfo?,
    onCartClick: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onSetQuantity: (Double) -> Unit,
    onLongPress: () -> Unit
) {
    val isInList = shoppingListInfo != null

    val bgColor = if (isInList) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onLongPress,
                onLongClick = onLongPress
            ),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isInList) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "В списке",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                val details = buildList {
                    if (!product.defaultUnit.isNullOrBlank()) {
                        val qty = product.defaultQuantity?.let { q ->
                            if (q == q.toLong().toDouble()) q.toLong().toString()
                            else q.toString()
                        }
                        val unitStr = if (qty != null) "$qty ${product.defaultUnit}" else product.defaultUnit
                        add(unitStr)
                    }
                    if (product.purchaseType == "online") add("онлайн")
                }
                if (details.isNotEmpty()) {
                    Text(
                        text = details.joinToString(" \u2022 "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (shoppingListInfo != null) {
                InlineQuantityControl(
                    quantity = shoppingListInfo.quantity,
                    unit = shoppingListInfo.unit,
                    onIncrement = onIncrement,
                    onDecrement = onDecrement,
                    onSetQuantity = onSetQuantity
                )
            } else {
                IconButton(onClick = onCartClick) {
                    Icon(
                        Icons.Filled.AddShoppingCart,
                        contentDescription = "Добавить в список",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
