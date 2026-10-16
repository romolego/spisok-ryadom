package com.spisokryadom.app.ui.addtolist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spisokryadom.app.data.entity.ProductEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToListScreen(
    onNavigateBack: () -> Unit,
    preselectedShopId: Long? = null,
    viewModel: AddToListViewModel = viewModel(factory = AddToListViewModel.factory(preselectedShopId))
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.shouldNavigateBack) {
        if (state.shouldNavigateBack) {
            onNavigateBack()
        }
    }

    LaunchedEffect(state.addedMessage) {
        state.addedMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.isShopLocked) "Добавить в магазин"
                        else "Добавить в список"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
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
                .padding(horizontal = 16.dp)
        ) {
            // Shop selector
            if (state.isShopLocked) {
                LockedShopLabel(
                    shopName = state.selectedShopId?.let { id ->
                        state.shops.find { it.id == id }?.name
                    } ?: "Магазин"
                )
            } else {
                ShopSelector(
                    shops = state.shops,
                    selectedShopId = state.selectedShopId,
                    onShopSelected = { viewModel.onShopSelected(it) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search field
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                label = { Text("Название товара") },
                placeholder = { Text("Найти или создать товар") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // New product option — shown when query doesn't match any existing product
            if (state.isNewProduct && state.searchQuery.isNotBlank()) {
                NewProductCard(
                    name = state.searchQuery.trim(),
                    saveToDatabase = state.saveToDatabase,
                    onSaveToDatabaseChanged = { viewModel.onSaveToDatabaseChanged(it) },
                    onAdd = { viewModel.addNewProduct(state.searchQuery) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Search results
            if (state.searchResults.isNotEmpty()) {
                Text(
                    text = "Товары из базы",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(
                        items = state.searchResults,
                        key = { it.id }
                    ) { product ->
                        ProductSearchItem(
                            product = product,
                            onClick = { viewModel.addExistingProduct(product) }
                        )
                    }
                }
            } else if (state.searchQuery.isNotBlank() && !state.isNewProduct) {
                Text(
                    text = "Товар уже в базе",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun LockedShopLabel(shopName: String) {
    OutlinedTextField(
        value = shopName,
        onValueChange = {},
        readOnly = true,
        enabled = false,
        label = { Text("Магазин") },
        leadingIcon = {
            Icon(
                Icons.Filled.Store,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShopSelector(
    shops: List<com.spisokryadom.app.data.entity.ShopEntity>,
    selectedShopId: Long?,
    onShopSelected: (Long?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = selectedShopId?.let { id ->
        shops.find { it.id == id }?.name
    } ?: "Без магазина"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Магазин") },
            leadingIcon = {
                Icon(
                    Icons.Filled.Store,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        "Без магазина",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = {
                    onShopSelected(null)
                    expanded = false
                }
            )
            shops.forEach { shop ->
                DropdownMenuItem(
                    text = { Text(shop.name) },
                    onClick = {
                        onShopSelected(shop.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun NewProductCard(
    name: String,
    saveToDatabase: Boolean,
    onSaveToDatabaseChanged: (Boolean) -> Unit,
    onAdd: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = "Новый товар: $name",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSaveToDatabaseChanged(!saveToDatabase) }
            ) {
                Checkbox(
                    checked = saveToDatabase,
                    onCheckedChange = onSaveToDatabaseChanged
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Сохранить в базе товаров",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (!saveToDatabase) {
                Text(
                    text = "Товар будет создан только для этой покупки",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Добавить в список")
            }
        }
    }
}

@Composable
private fun ProductSearchItem(
    product: ProductEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
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
                    product.defaultUnit?.let { if (it.isNotBlank()) add(it) }
                    product.note?.let { if (it.isNotBlank()) add(it) }
                }
                if (details.isNotEmpty()) {
                    Text(
                        text = details.joinToString(" \u2022 "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                Icons.Filled.Add,
                contentDescription = "Добавить",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
