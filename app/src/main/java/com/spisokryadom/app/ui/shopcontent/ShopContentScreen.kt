package com.spisokryadom.app.ui.shopcontent

import com.spisokryadom.app.ui.batch.*
import androidx.compose.material.icons.filled.Checklist
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import com.spisokryadom.app.ui.components.favoriteStyle
import com.spisokryadom.app.ui.components.favoriteWeight
import com.spisokryadom.app.ui.components.favoriteBackground
import com.spisokryadom.app.ui.components.favoriteSurface
import com.spisokryadom.app.ui.components.FavoriteBlock
import com.spisokryadom.app.ui.components.LocalFavoriteBlock
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
    focusProductId:Long=-1L,
    onFocusHandled:()->Unit={},
    viewModel: ShopContentViewModel = viewModel(factory = ShopContentViewModel.factory(shopId)),
    batchVM:BatchViewModel = viewModel(factory=BatchViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selection=rememberBatchSelection(state.products.map{it.product.id}.toSet(),shopId,loaded=!state.isLoading)
    var batchIds by remember{mutableStateOf<Set<Long>?>(null)}
    batchIds?.let{ids->BatchProductSheet(ids,{batchIds=null},{batchIds=null;selection.clear()},shopId=shopId,vm=batchVM)}
    state.error?.let{message->androidx.compose.material3.AlertDialog(onDismissRequest=viewModel::clearError,text={Text(message)},confirmButton={androidx.compose.material3.TextButton(onClick=viewModel::clearError){Text("Понятно")}})}
    val expansion=com.spisokryadom.app.ui.components.rememberGroups("product_db_prefs","shop_points_$shopId")
    val listState=androidx.compose.foundation.lazy.rememberLazyListState()
    val density=androidx.compose.ui.platform.LocalDensity.current
    var highlighted by remember{mutableStateOf<Long?>(null)}
    androidx.compose.runtime.LaunchedEffect(highlighted){if(highlighted!=null){kotlinx.coroutines.delay(2400);highlighted=null}}
    androidx.compose.runtime.LaunchedEffect(focusProductId,state.sections,expansion.collapsed){
        if(focusProductId>0){
            val target=state.sections.firstOrNull{it.products.any{p->p.product.id==focusProductId}}
            if(target!=null){
                val key="point_${target.id?:0}"
                if(key in expansion.collapsed)expansion.expand(key)
                else {
                    val keys=buildList<String>{add("spacer");state.sections.forEach{section->
                        val k="point_${section.id?:0}"
                        if(section.id!=null||state.sections.size>1)add(k)
                        if(k !in expansion.collapsed)section.products.groupBy{it.departmentName}.forEach{(dep,products)->
                            if(dep!=null||section.products.any{it.departmentName!=null})add("${k}_department_${dep.orEmpty()}")
                            products.forEach{add("${k}_${it.link.id}")}
                        }
                    }}
                    val row=target.products.first{it.product.id==focusProductId}
                    val index=keys.indexOf("${key}_${row.link.id}")
                    if(index>=0){listState.scrollToItem(index,-with(density){24.dp.roundToPx()});highlighted=focusProductId;onFocusHandled()}
                }
            }
        }
    }

    var fabExpanded by remember { mutableStateOf(false) }

    // The picker replaces the screen; it must own both pointer input and Back.
    if (state.showPickProducts) {
        BackHandler(onBack = viewModel::closePickProducts)
        PickProductsDialog(
            shopName = state.shop?.let{com.spisokryadom.app.ui.components.LocalPlaceLabels.current.shop(it.id,it.name)} ?: "",
            searchQuery = state.pickSearchQuery,
            products = state.pickProducts,
            onSearchQueryChange = { viewModel.setPickSearchQuery(it) },
            onToggleProduct = { viewModel.toggleProductLink(it) },
            onDismiss = { viewModel.closePickProducts() }
        )
    } else {
    Scaffold(
        snackbarHost={BatchFeedback(batchVM)},
        topBar = {
            TopAppBar(
                title = { Text(if(selection.active)"Выбрано: ${selection.ids.size}"else state.shop?.let{com.spisokryadom.app.ui.components.LocalPlaceLabels.current.shop(it.id,it.name)} ?: "",maxLines=1,overflow=TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = {if(selection.active)selection.clear()else onNavigateBack()}) {
                        Icon(if(selection.active)Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack, contentDescription = if(selection.active)"Отменить выбор"else "Назад")
                    }
                },
                actions={if(selection.active)BatchTopActions(selection,state.products.map{it.product.id}.toSet(),{batchIds=selection.ids})else IconButton(onClick={selection.active=true},enabled=state.products.isNotEmpty()){Icon(Icons.Default.Checklist,"Выбрать товары")}}
            )
        },
        floatingActionButton = {
            if (state.products.isNotEmpty()&&!selection.active) {
                Box {
                    FloatingActionButton(onClick={fabExpanded=true}){Icon(Icons.Filled.Add,"Добавить товар")}
                    androidx.compose.material3.DropdownMenu(expanded=fabExpanded,onDismissRequest={fabExpanded=false}){
                        androidx.compose.material3.DropdownMenuItem(text={Text("Добавить из базы")},leadingIcon={Icon(Icons.Filled.Inventory2,null)},onClick={fabExpanded=false;viewModel.openPickProducts()})
                        androidx.compose.material3.DropdownMenuItem(text={Text("Создать новый товар")},leadingIcon={Icon(Icons.Filled.Add,null)},onClick={fabExpanded=false;onCreateNewProduct()})
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
                state=listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp).padding(bottom=88.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }
                state.sections.forEach{section->
                    val key="point_${section.id?:0}"
                    if(section.id!=null||state.sections.size>1)item(key=key){
                        val expanded=key !in expansion.collapsed
                        androidx.compose.material3.OutlinedCard(Modifier.fillMaxWidth().animateItem(fadeInSpec=androidx.compose.animation.core.tween(220),fadeOutSpec=androidx.compose.animation.core.tween(180),placementSpec=androidx.compose.animation.core.tween(300))){
                            Column(Modifier.fillMaxWidth().padding(12.dp)){
                                Row(Modifier.fillMaxWidth().clickable{expansion.toggle(key)},verticalAlignment=Alignment.CenterVertically){
                                    if(selection.active)BatchToggle(section.products.map{it.product.id}.toSet(),selection,section.name)
                                    com.spisokryadom.app.ui.components.CollapseChevron(!expanded,if(expanded)"Свернуть точку: ${section.name}"else "Развернуть точку: ${section.name}")
                                    Text(section.name,Modifier.weight(1f).padding(horizontal=8.dp),style=MaterialTheme.typography.titleSmall)
                                    Text(section.products.size.toString(),style=MaterialTheme.typography.labelMedium)
                                }
                                if(expanded){section.note?.takeIf{it.isNotBlank()}?.let{Text(it,style=MaterialTheme.typography.bodyMedium)};section.photoUri?.let{com.spisokryadom.app.ui.components.BranchPhotoPreview(it)}}
                            }
                        }
                    }
                    if(key !in expansion.collapsed)section.products.groupBy{it.departmentName}.forEach{(department,products)->
                      if(department!=null||section.products.any{it.departmentName!=null})item(key="${key}_department_${department.orEmpty()}"){Row(verticalAlignment=Alignment.CenterVertically){
                       if(selection.active)BatchToggle(products.map{it.product.id}.toSet(),selection,department?:"Без группы магазина")
                       com.spisokryadom.app.ui.components.DepartmentHeading(department?:"Без группы магазина",state.departmentTypes[department]?.accent.orEmpty(),state.departmentTypes[department]?.iconKey.orEmpty(),Modifier.weight(1f))
                      }}
                      items(products,key={"${key}_${it.link.id}"}){productUi->
                        if(selection.active)BatchRow(productUi.product.id,productUi.product.name,productUi.product.id in selection.ids,{selection.toggle(productUi.product.id)},productUi.departmentName,Modifier.testTag("shop-product-${section.id?:0}-${productUi.product.id}"),favorite=productUi.product.isFavorite)
                        else ShopProductItem(productUi,
                            modifier=Modifier.testTag("shop-product-${section.id?:0}-${productUi.product.id}").then(if(highlighted==productUi.product.id)Modifier.border(2.dp,MaterialTheme.colorScheme.primary,MaterialTheme.shapes.large)else Modifier).animateItem(fadeInSpec=androidx.compose.animation.core.tween(220),fadeOutSpec=androidx.compose.animation.core.tween(180),placementSpec=androidx.compose.animation.core.tween(300)),
                            onCartClick={viewModel.quickAddToShoppingList(productUi.product,section.id)},
                            onIncrement={productUi.listEntryId?.let(viewModel::incrementEntry)},
                            onDecrement={productUi.listEntryId?.let(viewModel::decrementEntry)},
                            onSetQuantity={qty->productUi.listEntryId?.let{viewModel.setEntryQuantity(it,qty)}},
                            onLongPress={onNavigateToProductCard(productUi.product.id)},
                            onEntryAdjust={id,direction->if(direction>0)viewModel.incrementEntry(id)else viewModel.decrementEntry(id)},onEntrySet=viewModel::setEntryQuantity)
                    }
                    }
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
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
                title = { Text("Добавить товары",maxLines=1,overflow=TextOverflow.Ellipsis) },
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
                trailingIcon = { Row {
                    if (searchQuery.isNotEmpty()) { IconButton(onClick = { onSearchQueryChange("") }) { Icon(Icons.Filled.Clear, contentDescription = "Очистить") } }
                    com.spisokryadom.app.ui.components.VoiceSearchButton(onSearchQueryChange)
                } },
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
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = favoriteBackground(product.isFavorite,bgColor)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (pickUi.isLinked) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.outlineVariant)
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
                        fontWeight=favoriteWeight(product.isFavorite),
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if(product.isFavorite&&favoriteStyle().star){Spacer(Modifier.width(6.dp));Icon(Icons.Filled.Star,"Избранный товар: ${product.name}",Modifier.size(18.dp),tint=MaterialTheme.colorScheme.primary)}
                    if (pickUi.isLinked) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(привязан)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
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
                    if (product.purchaseType == "both") add("онлайн и офлайн")
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
    modifier:Modifier=Modifier,
    onCartClick: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onSetQuantity: (Double) -> Unit,
    onLongPress: () -> Unit,
    onEntryAdjust:(Long,Int)->Unit,
    onEntrySet:(Long,Double)->Unit
) {
    if(productUi.purchases.size>1){
        com.spisokryadom.app.ui.components.MultiplePurchaseCard(productUi.product.name,productUi.purchases,onEntryAdjust,onEntrySet,onLongPress,modifier,favorite=productUi.product.isFavorite,accent=productUi.product.accent)
        return
    }

    val product = productUi.product
    val isInList = productUi.isInShoppingList

    val bgColor = if (isInList) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { },
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
                    if (product.purchaseType == "both") add("онлайн и офлайн")
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

            Spacer(Modifier.width(10.dp))
            if (isInList && productUi.listQuantity != null) {
                androidx.compose.runtime.key(productUi.listEntryId) {
                    InlineQuantityControl(
                        quantity = productUi.listQuantity,
                        unit = productUi.listUnit ?: "",
                        onIncrement = onIncrement,
                        onDecrement = onDecrement,
                        onSetQuantity = onSetQuantity
                    )
                }
            } else {
                IconButton(onClick = onCartClick,modifier=Modifier.testTag("shop-add-${product.id}")) {
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
