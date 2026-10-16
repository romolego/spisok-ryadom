package com.spisokryadom.app.ui.shoplist

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spisokryadom.app.data.entity.ShopEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopListScreen(
    onNavigateToShopContent: (Long) -> Unit,
    onNavigateToShopCard: (Long) -> Unit,
    onCreateNewShop: () -> Unit,
    viewModel: ShopListViewModel = viewModel(factory = ShopListViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var scrollTargetShopId by remember { mutableStateOf<Long?>(null) }

    // After the shops list updates, scroll to keep the moved shop visible
    LaunchedEffect(state.shops) {
        val targetId = scrollTargetShopId ?: return@LaunchedEffect
        val shopIndex = state.shops.indexOfFirst { it.id == targetId }
        if (shopIndex >= 0) {
            // +1 accounts for the top spacer item
            listState.animateScrollToItem(shopIndex + 1)
        }
        scrollTargetShopId = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Магазины") },
                actions = {
                    if (state.shops.size > 1) {
                        IconButton(onClick = { viewModel.toggleEditing() }) {
                            Icon(
                                imageVector = if (state.isEditing) Icons.Filled.Check else Icons.Filled.Edit,
                                contentDescription = if (state.isEditing) "Готово" else "Изменить порядок",
                                tint = if (state.isEditing)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!state.isEditing) {
                FloatingActionButton(onClick = onCreateNewShop) {
                    Icon(Icons.Filled.Add, contentDescription = "Новый магазин")
                }
            }
        }
    ) { padding ->
        if (state.shops.isEmpty() && !state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Store,
                        contentDescription = null,
                        modifier = Modifier.height(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Нет магазинов",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }
                itemsIndexed(state.shops, key = { _, shop -> shop.id }) { index, shop ->
                    ShopListItem(
                        shop = shop,
                        isEditing = state.isEditing,
                        isFirst = index == 0,
                        isLast = index == state.shops.size - 1,
                        onTap = { onNavigateToShopContent(shop.id) },
                        onLongPress = { onNavigateToShopCard(shop.id) },
                        onMoveUp = {
                            viewModel.moveShopUp(shop)
                            scrollTargetShopId = shop.id
                        },
                        onMoveDown = {
                            viewModel.moveShopDown(shop)
                            scrollTargetShopId = shop.id
                        }
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShopListItem(
    shop: ShopEntity,
    isEditing: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = if (isEditing) ({}) else onTap,
                onLongClick = if (isEditing) null else onLongPress
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = if (isEditing) 4.dp else 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Store,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = shop.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!shop.address.isNullOrBlank()) {
                    Text(
                        text = shop.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (isEditing) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = !isFirst,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = "Переместить вверх",
                        modifier = Modifier.size(20.dp),
                        tint = if (!isFirst)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = !isLast,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Переместить вниз",
                        modifier = Modifier.size(20.dp),
                        tint = if (!isLast)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}
