package com.spisokryadom.app.ui.addtolist
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spisokryadom.app.data.entity.ShopEntity
import com.spisokryadom.app.ui.components.CityPicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToListScreen(onNavigateBack:()->Unit,preselectedShopId:Long?=null,viewModel:AddToListViewModel=viewModel(factory=AddToListViewModel.factory(preselectedShopId))){
 val state by viewModel.uiState.collectAsStateWithLifecycle();val snack=remember{SnackbarHostState()}
 val focus=androidx.compose.ui.platform.LocalFocusManager.current
 LaunchedEffect(state.addedMessage){state.addedMessage?.let{snack.showSnackbar(it);viewModel.clearMessage()}}
 Scaffold(topBar={TopAppBar(title={Text("Добавить покупку")},navigationIcon={IconButton(onClick=onNavigateBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Назад")}})},snackbarHost={SnackbarHost(snack)}){padding->
  LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   item(key="city"){CityPicker(enabled=!state.isLoading&&!state.isShopLocked)}
   item(key="shop"){ShopChoiceField(state.shops,state.selectedShopId,viewModel::onShopSelected,enabled=!state.isLoading&&!state.isShopLocked)}
   item(key="query"){OutlinedTextField(state.searchQuery,viewModel::onSearchQueryChanged,label={Text("Название товара")},placeholder={Text("Можно с опечаткой")},singleLine=true,enabled=!state.isLoading,modifier=Modifier.fillMaxWidth())}
   item(key="quantity"){
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
     state.selectedProduct?.let{Text("Выбрано: ${it.name}",color=MaterialTheme.colorScheme.primary)}
     Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
      OutlinedTextField(state.quantity,viewModel::quantity,label={Text("Количество")},singleLine=true,modifier=Modifier.weight(1f),enabled=!state.isLoading,keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Decimal))
      OutlinedTextField(state.unit,viewModel::unit,label={Text("Единица")},placeholder={Text("шт, кг, л")},singleLine=true,modifier=Modifier.weight(1f),enabled=!state.isLoading)
     }
    }
   }
   if(state.selectedProduct==null&&state.isNewProduct)item(key="new"){
    Column{
     Text("Новый товар: ${state.searchQuery.trim()}")
     Row{Checkbox(state.saveToDatabase,viewModel::onSaveToDatabaseChanged,enabled=!state.isLoading);Text("Сохранить в базе товаров",Modifier.padding(top=12.dp))}
    }
   }
   state.error?.let{message->item(key="error"){Text(message,color=MaterialTheme.colorScheme.error)}}
   item(key="add"){
    Button(onClick={focus.clearFocus();viewModel.add()},enabled=!state.isLoading&&!state.isSearching&&state.searchQuery.isNotBlank(),modifier=Modifier.fillMaxWidth()){
     Text(if(state.isLoading)"Добавление…" else "Добавить в список")
    }
   }
   if(state.isSearching)item(key="searching"){LinearProgressIndicator(Modifier.fillMaxWidth())}
   items(state.searchResults,key={"product_${it.id}"},contentType={"candidate"}){p->Card(Modifier.fillMaxWidth().clickable(enabled=!state.isLoading){focus.clearFocus();viewModel.selectProduct(p)}){
    Column(Modifier.padding(12.dp)){Text(p.name);Text(if(p.id==state.selectedProduct?.id)"Выбран" else "Выбрать товар",style=MaterialTheme.typography.bodySmall)}
   }}
   item(key="bottom"){Spacer(Modifier.height(16.dp))}
  }
 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopChoiceField(shops:List<ShopEntity>,selectedId:Long?,onSelected:(Long?)->Unit,enabled:Boolean=true){
 var expanded by remember{mutableStateOf(false)}
 val label=when(selectedId){-1L->"Подобрать автоматически";null->"Без магазина";else->shops.find{it.id==selectedId}?.name?:"Подобрать автоматически"}
 ExposedDropdownMenuBox(expanded=expanded,onExpandedChange={if(enabled)expanded=it}){
  OutlinedTextField(label,{},readOnly=true,enabled=enabled,label={Text("Магазин")},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded)},modifier=Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth())
  ExposedDropdownMenu(expanded,onDismissRequest={expanded=false}){
   DropdownMenuItem(text={Text("Подобрать автоматически")},onClick={onSelected(-1L);expanded=false})
   DropdownMenuItem(text={Text("Без магазина")},onClick={onSelected(null);expanded=false})
   shops.forEach{s->DropdownMenuItem(text={Text(s.name)},onClick={onSelected(s.id);expanded=false})}
  }
 }
}
