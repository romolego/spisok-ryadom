package com.spisokryadom.app.ui.shoplist

import com.spisokryadom.app.ui.batch.*
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spisokryadom.app.data.entity.ShopEntity
import com.spisokryadom.app.ui.components.FavoriteBlock
import com.spisokryadom.app.ui.components.FavoriteStar
import com.spisokryadom.app.ui.components.favoriteBackground
import com.spisokryadom.app.ui.components.favoriteWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopListScreen(
    onNavigateToShopContent: (Long) -> Unit,
    onNavigateToShopCard: (Long) -> Unit,
    onCreateNewShop: () -> Unit,
    viewModel: ShopListViewModel = viewModel(factory = ShopListViewModel.Factory),
    batchVM:BatchViewModel = viewModel(factory=BatchViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    state.error?.let{message->androidx.compose.material3.AlertDialog(onDismissRequest=viewModel::clearError,text={Text(message)},confirmButton={androidx.compose.material3.TextButton(onClick=viewModel::clearError){Text("Понятно")}})}
    val selection=rememberBatchSelection(state.shops.map{it.id}.toSet(),loaded=!state.isLoading)
    var batchIds by remember{mutableStateOf<Set<Long>?>(null)}
    batchIds?.let{ids->BatchProductSheet(ids,{batchIds=null},{batchIds=null;selection.clear()},stores=true,vm=batchVM)}
    val listState = rememberLazyListState()
    val favoriteShops=state.shops.filter{it.isFavorite}.sortedWith(compareBy<ShopEntity>{it.favoriteOrder}.thenBy{it.id})
    val viewport=com.spisokryadom.app.ui.components.rememberReorderViewport(listState,state.shops.map{it.id})
    Scaffold(
        snackbarHost={BatchFeedback(batchVM)},
        topBar = {
          Column {
            TopAppBar(
                title = { Text(if(selection.active)"Выбрано: ${selection.ids.size}"else "Магазины", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                navigationIcon={if(selection.active)IconButton(onClick=selection::clear){Icon(Icons.Default.Close,"Отменить выбор")}},
                actions = {
                    if(selection.active)BatchTopActions(selection,state.shops.map{it.id}.toSet(),{batchIds=selection.ids})
                    else if(!state.isEditing)IconButton(onClick={selection.active=true},enabled=state.shops.isNotEmpty()){Icon(Icons.Default.Checklist,"Выбрать магазины")}
                    if (!selection.active&&state.shops.size > 1) {
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
            com.spisokryadom.app.ui.components.CityPicker(enabled=!state.isLoading&&!selection.active&&!state.isEditing,compact=true)
          }
        },
        floatingActionButton = {
            if (!state.isEditing&&!selection.active) {
                FloatingActionButton(onClick = onCreateNewShop) {
                    Icon(Icons.Filled.Add, contentDescription = "Новый магазин")
                }
            }
        }
    ) { padding ->
        if (state.shops.isEmpty() && state.venues.isEmpty() && !state.isLoading) {
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
                if(favoriteShops.isNotEmpty()&&!selection.active){
                    item(key="favorite-shops-heading"){FavoriteBlock(Modifier.fillMaxWidth()){
                        Text("Избранные",Modifier.padding(horizontal=12.dp,vertical=8.dp),style=MaterialTheme.typography.titleSmall)
                    }}
                    itemsIndexed(favoriteShops,key={_,shop->"favorite-shop-${shop.id}"}){index,shop->
                        FavoriteBlock(Modifier.fillMaxWidth().testTag("favorite-shop-${shop.id}")){
                            ShopListItem(shop,state.isEditing,index==0,index==favoriteShops.lastIndex,
                                onTap={onNavigateToShopContent(shop.id)},onLongPress={onNavigateToShopCard(shop.id)},
                                onMoveUp={viewModel.moveFavoriteUp(shop)},onMoveDown={viewModel.moveFavoriteDown(shop)})
                        }
                    }
                    item(key="all-shops-heading"){Text("Все магазины",Modifier.padding(top=8.dp,bottom=4.dp),style=MaterialTheme.typography.titleSmall)}
                }
                state.groups.forEach{group->
                    group.venue?.let{venue->item(key="${group.key}_header"){
                        if(selection.active)Row(verticalAlignment=Alignment.CenterVertically){BatchToggle(group.shops.map{it.id}.toSet(),selection,venue.name);Text(venue.name,style=MaterialTheme.typography.titleMedium)}
                        else VenueHeading(venue,viewModel::saveVenue,viewModel::removeVenue)
                    }}
                    itemsIndexed(group.shops,key={_,shop->"${group.key}_${shop.id}"}){_,shop->
                        val index=state.shops.indexOfFirst{it.id==shop.id}
                        Box(Modifier.testTag("shop-order-${shop.id}-$index").padding(start=if(group.venue==null)0.dp else 12.dp).animateItem(fadeInSpec=null,fadeOutSpec=null,placementSpec=androidx.compose.animation.core.tween(300))){
                         if(selection.active)BatchRow(shop.id,shop.name,shop.id in selection.ids,{selection.toggle(shop.id)},shop.address)
                         else ShopListItem(
                            shop=shop,isEditing=state.isEditing,isFirst=index==0,isLast=index==state.shops.lastIndex,
                            onTap={onNavigateToShopContent(shop.id)},onLongPress={onNavigateToShopCard(shop.id)},
                            onMoveUp={viewport.capture();viewModel.moveShopUp(shop)},onMoveDown={viewport.capture();viewModel.moveShopDown(shop)}
                        )}
                    }
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
        colors = CardDefaults.cardColors(containerColor = favoriteBackground(shop.isFavorite)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = if (isEditing) 4.dp else 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            com.spisokryadom.app.ui.components.ShopSymbol(shop.iconKey,shop.logoUri)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Row(verticalAlignment=Alignment.CenterVertically){
                Text(
                    text = shop.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight=favoriteWeight(shop.isFavorite),
                    modifier=Modifier.weight(1f,fill=false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                FavoriteStar(shop.isFavorite,"Избранный магазин: ${shop.name}",Modifier.padding(start=6.dp))
                }
                if (!shop.address.isNullOrBlank()&&shop.address!=shop.name) {
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
                com.spisokryadom.app.ui.components.ReorderButtons(onMoveUp,onMoveDown,!isFirst,!isLast)
            }
        }
    }
}

@Composable private fun VenueHeading(venue:com.spisokryadom.app.data.entity.VenueEntity,onSave:suspend(com.spisokryadom.app.data.entity.VenueEntity)->Unit,onDelete:(Long)->Unit){
 var editing by androidx.compose.runtime.saveable.rememberSaveable(venue.id){mutableStateOf(false)}
 if(editing)com.spisokryadom.app.ui.components.VenueEditorDialog(venue,{editing=false},onSave,onDelete={onDelete(venue.id);editing=false})
 Column(Modifier.fillMaxWidth().testTag("venue-group-${venue.id}").padding(top=12.dp,bottom=4.dp)){
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
   Icon(if(venue.kind==com.spisokryadom.app.data.entity.VenueKinds.MARKET)Icons.Filled.Storefront else Icons.Filled.Domain,null,tint=MaterialTheme.colorScheme.primary)
   Column(Modifier.weight(1f).padding(start=10.dp)){
    Text(com.spisokryadom.app.data.entity.VenueKinds.label(venue.kind),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Text(venue.name,style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)
    venue.address?.takeIf{it.isNotBlank()}?.let{Text(it,style=MaterialTheme.typography.bodySmall)}
   }
   IconButton(onClick={editing=true}){Icon(Icons.Filled.Edit,"Изменить рынок или ТЦ: ${venue.name}")}
  }
 }
}
