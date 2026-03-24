package com.spisokryadom.app.ui.productdb

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spisokryadom.app.data.entity.ProductEntity
import com.spisokryadom.app.ui.components.QuantityUnitInputWithSuggestions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDatabaseScreen(
    onNavigateToProductCard: (Long) -> Unit,
    onCreateNewProduct: () -> Unit,
    viewModel: ProductDatabaseViewModel = viewModel(factory = ProductDatabaseViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var addToListProduct by remember { mutableStateOf<ProductEntity?>(null) }

    addToListProduct?.let { product ->
        AddToListDialog(
            product = product,
            unitSuggestions = state.unitSuggestions,
            onConfirm = { qty, unit ->
                viewModel.addToList(product, qty, unit)
                addToListProduct = null
            },
            onDismiss = { addToListProduct = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("База товаров") })
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
                    items(state.products, key = { it.id }) { product ->
                        ProductListItem(
                            product = product,
                            onTap = { onNavigateToProductCard(product.id) },
                            onAddToList = { addToListProduct = product }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ProductListItem(
    product: ProductEntity,
    onTap: () -> Unit,
    onAddToList: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap() },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
            IconButton(onClick = onAddToList) {
                Icon(
                    Icons.Filled.AddShoppingCart,
                    contentDescription = "Добавить в список",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun AddToListDialog(
    product: ProductEntity,
    unitSuggestions: List<String>,
    onConfirm: (Double, String) -> Unit,
    onDismiss: () -> Unit
) {
    var quantity by remember {
        mutableStateOf(
            product.defaultQuantity?.let { q ->
                if (q == q.toLong().toDouble()) q.toLong().toString()
                else q.toString()
            } ?: ""
        )
    }
    var unit by remember { mutableStateOf(product.defaultUnit ?: "") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("В список: ${product.name}") },
        text = {
            QuantityUnitInputWithSuggestions(
                quantity = quantity,
                unit = unit,
                onQuantityChange = { quantity = it },
                onUnitChange = { unit = it },
                unitSuggestions = unitSuggestions
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val qty = quantity.toDoubleOrNull() ?: 0.0
                onConfirm(qty, unit.trim())
            }) { Text("Добавить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
