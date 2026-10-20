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
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Star
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
import com.spisokryadom.app.ui.batch.*
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.border
import androidx.compose.ui.Alignment
import com.spisokryadom.app.ui.components.favoriteStyle
import com.spisokryadom.app.ui.components.favoriteWeight
import com.spisokryadom.app.ui.components.favoriteBackground
import com.spisokryadom.app.ui.components.favoriteSurface
import com.spisokryadom.app.ui.components.FavoriteBlock
import com.spisokryadom.app.ui.components.LocalFavoriteBlock
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
    focusProductId:Long=-1L,
    onFocusHandled:()->Unit={},
    viewModel: ProductDatabaseViewModel = viewModel(factory = ProductDatabaseViewModel.Factory),
    batchVM:BatchViewModel = viewModel(factory=BatchViewModel.Factory),
    onCreateFromSearch:(String)->Unit={onCreateNewProduct()}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selection=rememberBatchSelection(state.products.map{it.id}.toSet(),state.viewMode,loaded=!state.isLoading)
    var batchIds by androidx.compose.runtime.remember{androidx.compose.runtime.mutableStateOf<Set<Long>?>(null)}
    batchIds?.let{ids->BatchProductSheet(ids,{batchIds=null},{batchIds=null;selection.clear()},vm=batchVM)}
    val expansion=com.spisokryadom.app.ui.components.rememberGroups("product_db_prefs","collapsed_sections")
    val listState=androidx.compose.foundation.lazy.rememberLazyListState()
    var highlighted by androidx.compose.runtime.remember{androidx.compose.runtime.mutableStateOf<Long?>(null)}
    val density=androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.LaunchedEffect(focusProductId){if(focusProductId>0)viewModel.revealProduct()}
    androidx.compose.runtime.LaunchedEffect(highlighted){if(highlighted!=null){kotlinx.coroutines.delay(2400);highlighted=null}}
    androidx.compose.runtime.LaunchedEffect(focusProductId,state.sections,expansion.collapsed){
        if(focusProductId>0){
            val target=state.sections.firstOrNull{it.key!="favorites"&&it.products.any{p->p.id==focusProductId}}
            if(target!=null){
                val sectionKey="${state.viewMode}_${target.key}"
                if(sectionKey in expansion.collapsed)expansion.expand(sectionKey)
                else {
                    val keys=buildList<String>{state.sections.forEach{section->
                        add("header_${section.key}")
                        if("${state.viewMode}_${section.key}" !in expansion.collapsed||state.searchQuery.isNotBlank())section.departments.ifEmpty{listOf(ProductDepartmentSection("",section.products,"all"))}.forEach{sub->
                            if(sub.name.isNotBlank())add("${section.key}_${sub.key}_header")
                            sub.products.forEach{add("${section.key}_${it.id}")}
                        }
                    }}
                    val index=keys.indexOf("${target.key}_$focusProductId")
                    if(index>=0){listState.scrollToItem(index,-with(density){64.dp.roundToPx()});highlighted=focusProductId;onFocusHandled()}
                }
            }
        }
    }


    state.error?.let { message -> androidx.compose.material3.AlertDialog(onDismissRequest=viewModel::clearError,text={Text(message)},confirmButton={androidx.compose.material3.TextButton(onClick=viewModel::clearError){Text("Понятно")}}) }
    Scaffold(
        snackbarHost={BatchFeedback(batchVM)},
        topBar = {
            TopAppBar(title={Text(if(selection.active)"Выбрано: ${selection.ids.size}"else "База товаров",maxLines=1,overflow=TextOverflow.Ellipsis)},
                navigationIcon={if(selection.active)IconButton(onClick=selection::clear){Icon(Icons.Default.Close,"Отменить выбор")}},
                actions={if(selection.active)BatchTopActions(selection,state.products.map{it.id}.toSet(),{batchIds=selection.ids})else{
                    IconButton(onClick={selection.active=true},enabled=state.products.isNotEmpty()){Icon(Icons.Default.Checklist,"Выбрать товары")}
                    IconButton(onClick=viewModel::toggleFavorites){Icon(Icons.Default.Star,if(state.favoritesOnly)"Показать все товары"else "Только избранное",tint=if(state.favoritesOnly)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)}
                }})
        },
        floatingActionButton = {
            if(!selection.active)FloatingActionButton(onClick = onCreateNewProduct) {
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
                trailingIcon = { Row {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) { Icon(Icons.Filled.Clear, contentDescription = "Очистить") }
                    }
                    com.spisokryadom.app.ui.components.VoiceSearchButton(viewModel::setSearchQuery)
                } },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if(!selection.active && !state.isLoading && state.searchQuery.isNotBlank() && state.searchQuery==state.searchedQuery && !state.exactNameExists) {
                androidx.compose.material3.TextButton(onClick={onCreateFromSearch(state.searchQuery.trim())},modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp).testTag("create-product-from-search")) {
                    Icon(Icons.Filled.Add,null);Spacer(Modifier.width(8.dp));Text("Создать «${state.searchQuery.trim()}»",maxLines=2,overflow=TextOverflow.Ellipsis)
                }
            }

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
                    state=listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp).padding(bottom=80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.sections.forEachIndexed { sectionIndex, section ->
                        val sectionKey="${state.viewMode}_${section.key}"
                        val collapsed=state.searchQuery.isBlank()&&sectionKey in expansion.collapsed
                        // Section header — key includes index to prevent duplicates if two sections share the same name
                        stickyHeader(key = "header_${section.key}") {
                            val header:@Composable ()->Unit={SectionHeader(
                                modifier=Modifier.animateItem(fadeInSpec=androidx.compose.animation.core.tween(220),fadeOutSpec=androidx.compose.animation.core.tween(180),placementSpec=androidx.compose.animation.core.tween(300)),
                                title = section.shopId?.let{com.spisokryadom.app.ui.components.LocalPlaceLabels.current.shop(it,section.name)}?:section.name,accent=section.accent,iconKey=section.iconKey,
                                count = section.products.size, collapsed=collapsed, onToggle={expansion.toggle(sectionKey)},
                                selection=selection.takeIf{it.active},ids=section.products.map{it.id}.toSet()
                            )}
                            if(section.key=="favorites")FavoriteBlock(Modifier.fillMaxWidth()){header()}else header()
                        }

                        // Key includes section name to prevent duplicate keys when the same product
                        // appears in multiple sections (e.g. BY_SHOPS mode with multi-shop products)
                        val subSections=section.departments.ifEmpty{listOf(ProductDepartmentSection("",section.products,"all"))}
                        if(!collapsed)subSections.forEach{sub->
                          if(sub.name.isNotBlank())item(key="${section.key}_${sub.key}_header"){
                            Row(verticalAlignment=Alignment.CenterVertically){
                              if(selection.active)BatchToggle(sub.products.map{it.id}.toSet(),selection,sub.name)
                              com.spisokryadom.app.ui.components.DepartmentHeading(sub.name,sub.accent,sub.iconKey,Modifier.weight(1f).animateItem(placementSpec=androidx.compose.animation.core.tween(300)))
                            }
                          }
                          items(sub.products,key={"${section.key}_${it.id}"},contentType={"product"}){product->
                            val listInfo=if(section.shopId!=null&&state.viewMode==ProductViewMode.BY_SHOPS)state.shopShoppingListMap[section.shopId?:0L]?.get(product.id)else state.shoppingListMap[product.id]
                            val row:@Composable ()->Unit={if(selection.active)BatchRow(product.id,product.name,product.id in selection.ids,{selection.toggle(product.id)},product.defaultUnit,Modifier.testTag("catalogue-${section.key}-${product.id}"),favorite=product.isFavorite)
                            else ProductListItem(
                              modifier=Modifier.testTag("catalogue-${section.key}-${product.id}").then(if(highlighted==product.id&&section.key!="favorites")Modifier.border(2.dp,MaterialTheme.colorScheme.primary,MaterialTheme.shapes.large)else Modifier).animateItem(fadeInSpec=androidx.compose.animation.core.tween(220),fadeOutSpec=androidx.compose.animation.core.tween(180),placementSpec=androidx.compose.animation.core.tween(300)),
                              product=product,shoppingListInfo=listInfo,onCartClick={viewModel.quickAddToList(product,section.shopId,state.viewMode==ProductViewMode.BY_SHOPS)},
                              onIncrement={listInfo?.let{viewModel.incrementEntry(it.entryId)}},onDecrement={listInfo?.let{viewModel.decrementEntry(it.entryId)}},
                              onSetQuantity={qty->listInfo?.let{viewModel.setEntryQuantity(it.entryId,qty)}},onLongPress={onNavigateToProductCard(product.id)},
                              onEntryAdjust={id,direction->if(direction>0)viewModel.incrementEntry(id)else viewModel.decrementEntry(id)},onEntrySet=viewModel::setEntryQuantity)}
                            if(section.key=="favorites")FavoriteBlock(Modifier.fillMaxWidth()){row()}else row()
                          }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SectionHeader(title: String, count: Int, collapsed:Boolean, onToggle:()->Unit,modifier:Modifier=Modifier,accent:String="",iconKey:String="",selection:BatchSelection?=null,ids:Set<Long> = emptySet()) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if(LocalFavoriteBlock.current)favoriteBackground(true,MaterialTheme.colorScheme.background)else MaterialTheme.colorScheme.background)
            .combinedClickable(onClick=onToggle,onLongClick=null)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if(selection!=null)BatchToggle(ids,selection,title)
        com.spisokryadom.app.ui.components.CollapseChevron(collapsed,(if(collapsed)"Развернуть: "else "Свернуть: ")+title,Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        com.spisokryadom.app.ui.components.MarkedLabel(title,accent,iconKey,Modifier.weight(1f))
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
    modifier:Modifier=Modifier,
    product: ProductEntity,
    shoppingListInfo: ShoppingListInfo?,
    onCartClick: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onSetQuantity: (Double) -> Unit,
    onLongPress: () -> Unit,
    onEntryAdjust:(Long,Int)->Unit,
    onEntrySet:(Long,Double)->Unit
) {
    if(shoppingListInfo!=null&&shoppingListInfo.purchases.size>1){
        com.spisokryadom.app.ui.components.MultiplePurchaseCard(product.name,shoppingListInfo.purchases,onEntryAdjust,onEntrySet,onLongPress,modifier,favorite=product.isFavorite,accent=product.accent)
        return
    }
    val isInList = shoppingListInfo != null

    val bgColor = if (isInList) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onLongPress,
                onLongClick = onLongPress
            ),
        colors = CardDefaults.cardColors(containerColor = favoriteBackground(product.isFavorite,bgColor)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isInList) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.outlineVariant)
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
                        fontWeight=favoriteWeight(product.isFavorite),
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if(product.isFavorite&&favoriteStyle().star){Spacer(Modifier.width(6.dp));Icon(Icons.Filled.Star,"Избранный товар",Modifier.size(16.dp),tint=MaterialTheme.colorScheme.primary)}
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
                com.spisokryadom.app.ui.components.accentColor(product.accent)?.let{Box(Modifier.padding(top=3.dp).width(36.dp).height(2.dp).background(it))}
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

            Spacer(Modifier.width(10.dp))
            if (shoppingListInfo != null) {
                androidx.compose.runtime.key(shoppingListInfo.entryId) {
                    InlineQuantityControl(
                        quantity = shoppingListInfo.quantity,
                        unit = shoppingListInfo.unit,
                        onIncrement = onIncrement,
                        onDecrement = onDecrement,
                        onSetQuantity = onSetQuantity
                    )
                }
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
