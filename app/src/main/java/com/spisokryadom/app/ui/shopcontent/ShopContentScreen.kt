package com.spisokryadom.app.ui.shopcontent

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
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
import com.spisokryadom.app.ui.components.InlineQuantityControl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopContentScreen(
    shopId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToProductCard: (Long) -> Unit,
    onCreateNewProduct: () -> Unit,
    viewModel: ShopContentViewModel = viewModel(factory = ShopContentViewModel.factory(shopId))
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var fabExpanded by remember { mutableStateOf(false) }

    // Pick products fullscreen dialog
    if (state.showPickProducts) {
        PickProductsDialog(
            shopName = state.shop?.name ?: "",
            searchQuery = state.pickSearchQuery,
            products = state.pickProducts,
            onSearchQueryChange = { viewModel.setPickSearchQuery(it) },
            onToggleProduct = { viewModel.toggleProductLink(it) },
            onDismiss = { viewModel.closePickProducts() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.shop?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        },
        floatingActionButton = {
            if (state.products.isNotEmpty()) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (fabExpanded) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Добавить из базы",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            SmallFloatingActionButton(
                                onClick = {
                                    fabExpanded = false
                                    viewModel.openPickProducts()
                                }
                            ) {
                                Icon(Icons.Filled.Inventory2, contentDescription = "Добавить из базы")
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Создать новый товар",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            SmallFloatingActionButton(
                                onClick = {
                                    fabExpanded = false
                                    onCreateNewProduct()
                                }
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Создать новый товар")
                            }
                        }
                    }
                    FloatingActionButton(onClick = { fabExpanded = !fabExpanded }) {
                        Icon(
                            if (fabExpanded) Icons.Filled.Close else Icons.Filled.Add,
                            contentDescription = "Добавить товар"
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (state.products.isEmpty() && !state.isLoading) {
            // Empty state with actionable buttons
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Icon(
                        Icons.Filled.Store,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "В этом магазине пока нет товаров",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    OutlinedButton(
                        onClick = onCreateNewProduct,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Создать новый товар")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.openPickProducts() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Inventory2, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Добавить из базы товаров")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }
                items(state.products, key = { it.link.id }) { productUi ->
                    ShopProductItem(
                        productUi = productUi,
                        onCartClick = { viewModel.quickAddToShoppingList(productUi.product) },
                        onIncrement = { viewModel.incrementQuantity(productUi.product.id) },
                        onDecrement = { viewModel.decrementQuantity(productUi.product.id) },
                        onSetQuantity = { qty -> viewModel.setQuantity(productUi.product.id, qty) },
                        onLongPress = { onNavigateToProductCard(productUi.product.id) }
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickProductsDialog(
    shopName: String,
    searchQuery: String,
    products: List<PickProductUi>,
    onSearchQueryChange: (String) -> Unit,
    onToggleProduct: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Добавить товары в «$shopName»") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Поиск по названию...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Очистить")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            val linkedCount = products.count { it.isLinked }
            val totalCount = products.size

            if (totalCount > 0) {
                Text(
                    text = "Привязано: $linkedCount из $totalCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            if (products.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (searchQuery.isNotEmpty()) "Ничего не найдено" else "Нет товаров в базе",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val linked = products.filter { it.isLinked }
                val unlinked = products.filter { !it.isLinked }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (linked.isNotEmpty()) {
                        item {
                            Text(
                                text = "Привязаны к магазину",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                            )
                        }
                        items(linked, key = { it.product.id }) { pickUi ->
                            PickProductItem(
                                pickUi = pickUi,
                                onToggle = { onToggleProduct(pickUi.product.id) }
                            )
                        }
                    }
                    if (unlinked.isNotEmpty()) {
                        item {
                            Text(
                                text = "Доступные для добавления",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                            )
                        }
                        items(unlinked, key = { it.product.id }) { pickUi ->
                            PickProductItem(
                                pickUi = pickUi,
                                onToggle = { onToggleProduct(pickUi.product.id) }
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun PickProductItem(
    pickUi: PickProductUi,
    onToggle: () -> Unit
) {
    val product = pickUi.product

    val bgColor = if (pickUi.isLinked) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (pickUi.isLinked) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(привязан)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Checkbox(
                checked = pickUi.isLinked,
                onCheckedChange = { onToggle() }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShopProductItem(
    productUi: ShopProductUi,
    onCartClick: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onSetQuantity: (Double) -> Unit,
    onLongPress: () -> Unit
) {
    val product = productUi.product
    val isInList = productUi.isInShoppingList

    val bgColor = if (isInList) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { },
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
                    productUi.departmentName?.let { add(it) }
                    if (!product.defaultUnit.isNullOrBlank()) {
                        val qty = product.defaultQuantity?.let { q ->
                            if (q == q.toLong().toDouble()) q.toLong().toString()
                            else q.toString()
                        }
                        val unitStr = if (qty != null) "$qty ${product.defaultUnit}" else product.defaultUnit
                        add(unitStr)
                    }
                    if (product.purchaseType == "online") add("онлайн")
                    add("приоритет: ${productUi.link.priority}")
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

            if (isInList && productUi.listQuantity != null) {
                InlineQuantityControl(
                    quantity = productUi.listQuantity,
                    unit = productUi.listUnit ?: "",
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
