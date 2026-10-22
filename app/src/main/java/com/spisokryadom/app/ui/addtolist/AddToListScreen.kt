package com.spisokryadom.app.ui.addtolist
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spisokryadom.app.data.entity.ShopEntity
import com.spisokryadom.app.data.entity.ProductEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToListScreen(onNavigateBack:()->Unit,preselectedShopId:Long?=null,preselectedBranchId:Long?=null,targetListId:Long?=null,viewModel:AddToListViewModel=viewModel(factory=AddToListViewModel.factory(preselectedShopId,preselectedBranchId,targetListId)),onCreateProduct:((String,Long?)->Unit)?=null,returnedProductId:Long=-1L,onReturnHandled:()->Unit={}){
 val state by viewModel.uiState.collectAsStateWithLifecycle();val snack=remember{SnackbarHostState()}
 LaunchedEffect(returnedProductId){if(returnedProductId>0){viewModel.selectSavedProduct(returnedProductId);onReturnHandled()}}
 val focus=androidx.compose.ui.platform.LocalFocusManager.current
 LaunchedEffect(state.addedMessage){state.addedMessage?.let{viewModel.clearMessage();onNavigateBack()}}
 LaunchedEffect(state.browseMessage){state.browseMessage?.let{snack.showSnackbar(it);viewModel.clearBrowseMessage()}}
 Scaffold(topBar={TopAppBar(title={Text(if(targetListId==null)"Добавить покупку"else state.targetListName?.let{"Добавить в «$it»"}?:"Список удалён",maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)},navigationIcon={IconButton(onClick=onNavigateBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Назад")}})},snackbarHost={SnackbarHost(snack)}){padding->
  BoxWithConstraints(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
   val listHeight=(maxHeight*0.55f).coerceIn(120.dp,360.dp)
   LazyColumn(Modifier.testTag("add-screen-list").fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
    if(targetListId!=null)item(key="contents"){
     OutlinedCard(Modifier.fillMaxWidth().testTag("add-list-contents")){
      Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
       Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
        Text("В списке · ${state.addedItems.size}",Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
        TextButton(onClick=onNavigateBack){Text("Открыть")}
       }
       if(state.addedItems.isEmpty())Text("Пока пусто",style=MaterialTheme.typography.bodySmall)
       else LazyColumn(Modifier.fillMaxWidth().heightIn(max=130.dp).testTag("add-list-current-items")){
        items(state.addedItems,key={it.id}){row->Row(Modifier.fillMaxWidth().padding(vertical=3.dp)){
         Text(row.name,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
         Text(row.quantity,style=MaterialTheme.typography.labelLarge)
        }}
       }
      }
     }
    }
    item(key="shop"){ShopChoiceField(state.shops,state.selectedShopId,viewModel::onShopSelected,enabled=!state.isLoading&&!state.isShopLocked,onCreate=viewModel::createShop)}
    item(key="query"){OutlinedTextField(state.searchQuery,viewModel::onSearchQueryChanged,label={Text("Название товара")},singleLine=true,enabled=!state.isLoading,modifier=Modifier.fillMaxWidth())}
    if(state.selectedProduct==null&&(state.searchResults.isNotEmpty()||state.searchQuery.isBlank()&&state.browseProducts))item(key="candidates"){
     OutlinedCard(Modifier.fillMaxWidth()) {
      LazyColumn(Modifier.fillMaxWidth().heightIn(max=listHeight).testTag("add-candidates")) {
       if(state.favoriteResults.isNotEmpty()) {
        item(key="favorites-title"){com.spisokryadom.app.ui.components.FavoriteBlock(Modifier.fillMaxWidth()){Text("Избранные",Modifier.padding(horizontal=12.dp,vertical=8.dp),style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary)}}
        items(state.favoriteResults,key={"favorite_${it.id}"}){p->com.spisokryadom.app.ui.components.FavoriteBlock(Modifier.fillMaxWidth()){AddCandidate(p,state,"favorite",{focus.clearFocus();viewModel.selectProduct(p)},{viewModel.quickAdd(p)})}}
       }
       if(state.searchQuery.isBlank()&&state.searchResults.isNotEmpty())item(key="all-title"){Text("Все товары",Modifier.padding(horizontal=12.dp,vertical=8.dp),style=MaterialTheme.typography.titleSmall)}
       items(state.searchResults,key={"product_${it.id}"}){p->AddCandidate(p,state,"all",{focus.clearFocus();viewModel.selectProduct(p)},{viewModel.quickAdd(p)})}
       if(state.searchResults.isEmpty()&&!state.isSearching)item(key="empty"){Text("Пока нет подходящих товаров. Введите название, чтобы добавить новый.",Modifier.padding(12.dp),style=MaterialTheme.typography.bodyMedium)}
      }
     }
    }
    state.selectedProduct?.let{p->item(key="selected"){
     OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp)){
      com.spisokryadom.app.ui.components.ProductCandidateTitle(p,style=MaterialTheme.typography.titleMedium)
      Text("Выбран",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
      TextButton(onClick={viewModel.onSearchQueryChanged(state.searchQuery)}){Text("Выбрать другой товар")}
     }}
    }}
    if(state.selectedProduct!=null||state.searchQuery.isNotBlank()) {
     item(key="quantity"){
      com.spisokryadom.app.ui.components.QuantityUnitInputWithSuggestions(state.quantity,state.unit,viewModel::quantity,viewModel::unit,state.unitSuggestions,unitLabel="Единица",enabled=!state.isLoading)
     }
     if(state.selectedProduct==null&&state.isNewProduct)item(key="new"){
      Column{
       if(onCreateProduct!=null)TextButton(onClick={focus.clearFocus();onCreateProduct(state.searchQuery.trim(),state.selectedShopId?.takeIf{it>0})},enabled=!state.isSearching&&!state.isLoading,modifier=Modifier.testTag("add-create-product")){Text("Создать карточку товара")}
       Text("Новый товар: ${state.searchQuery.trim()}");com.spisokryadom.app.ui.components.SelectionRow("Сохранить в базе товаров",state.saveToDatabase,viewModel::onSaveToDatabaseChanged,enabled=!state.isLoading)}
     }
     item(key="add"){
      Button(onClick={focus.clearFocus();viewModel.add()},enabled=state.targetAvailable&&!state.isLoading&&!state.isSearching&&(state.selectedProduct!=null||state.isNewProduct),modifier=Modifier.fillMaxWidth()){
       Text(if(state.isLoading)"Добавление…" else "Добавить в список")
      }
     }
    }
    state.error?.let{message->item(key="error"){Text(message,color=MaterialTheme.colorScheme.error)}}
    item(key="bottom"){Spacer(Modifier.height(16.dp))}
   }
  }
 }
}
@Composable
private fun AddCandidate(p:ProductEntity,state:AddToListUiState,section:String,onSelect:()->Unit,onAdd:()->Unit) {
 val pending=state.pendingProductLabels[p.id]
 val background by animateColorAsState(if(pending!=null)MaterialTheme.colorScheme.primary.copy(alpha=.08f)else androidx.compose.ui.graphics.Color.Transparent,label="purchase-added")
 Row(Modifier.fillMaxWidth().background(background).animateContentSize().testTag("candidate-$section-${p.id}").clickable(enabled=!state.isLoading&&!state.isSearching,onClick=onSelect).padding(start=12.dp,top=6.dp,bottom=6.dp),verticalAlignment=Alignment.CenterVertically) {
  Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)){
   com.spisokryadom.app.ui.components.ProductCandidateTitle(p,style=MaterialTheme.typography.bodyLarge)
   AnimatedVisibility(pending!=null){
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp),modifier=Modifier.testTag("candidate-added-$section-${p.id}")){
     Icon(Icons.Filled.CheckCircle,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(16.dp))
     Text("В списке · ${pending.orEmpty()}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
    }
   }
   (if(pending==null)state.productContexts else state.productContextsWithoutQuantity)[p.id]?.takeIf{it.isNotBlank()}?.let{Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)}
  }
  IconButton(onClick=onAdd,enabled=!state.isLoading&&!state.isSearching){Icon(if(pending!=null)Icons.Filled.Add else Icons.Filled.AddShoppingCart,if(pending!=null)"Добавить ещё: ${p.name}"else "Добавить: ${p.name}")}
 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopChoiceField(shops:List<ShopEntity>,selectedId:Long?,onSelected:(Long?)->Unit,enabled:Boolean=true,allowAutomatic:Boolean=true,onCreate:((String,(Long)->Unit)->Unit)?=null){
 var expanded by remember{mutableStateOf(false)};var creating by remember{mutableStateOf(false)}
 if(creating)com.spisokryadom.app.ui.components.InlineCreateValue("Название магазина",{name,done->onCreate?.invoke(name){id->onSelected(id);creating=false;done()}},{creating=false})
 val label=when(selectedId){-1L->"Подобрать автоматически";null->"Без магазина";else->shops.find{it.id==selectedId}?.let{com.spisokryadom.app.ui.components.LocalPlaceLabels.current.shop(it.id,it.name)}?:"Выберите магазин"}
 ExposedDropdownMenuBox(expanded=expanded,onExpandedChange={if(enabled)expanded=it}){
  OutlinedTextField(label,{},readOnly=true,enabled=enabled,label={Text("Магазин")},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded)},modifier=Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth())
  ExposedDropdownMenu(expanded,onDismissRequest={expanded=false}){
   shops.forEach{s->DropdownMenuItem(text={Text(com.spisokryadom.app.ui.components.LocalPlaceLabels.current.shop(s.id,s.name))},onClick={onSelected(s.id);expanded=false})}
   if(allowAutomatic)DropdownMenuItem(text={Text("Подобрать автоматически")},onClick={onSelected(-1L);expanded=false})
   DropdownMenuItem(text={Text("Без магазина")},onClick={onSelected(null);expanded=false})
   if(onCreate!=null)DropdownMenuItem(text={Text("Новый магазин…")},onClick={expanded=false;creating=true})
  }
 }
}
