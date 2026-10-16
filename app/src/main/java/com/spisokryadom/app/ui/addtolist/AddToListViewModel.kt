package com.spisokryadom.app.ui.addtolist
import android.content.SharedPreferences
import androidx.lifecycle.*
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spisokryadom.app.SpisokRyadomApp
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.util.*
import com.spisokryadom.app.ui.shoppinglist.ShoppingListViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
data class AddToListUiState(
 val searchQuery:String="",val searchResults:List<ProductEntity> = emptyList(),val shops:List<ShopEntity> = emptyList(),
 val selectedShopId:Long? = -1L,val isShopLocked:Boolean=false,val saveToDatabase:Boolean=true,
 val isNewProduct:Boolean=false,val addedMessage:String?=null,val isLoading:Boolean=false,val isSearching:Boolean=false,
 val selectedProduct:ProductEntity?=null,val quantity:String="1",val unit:String="",val error:String?=null
)
class AddToListViewModel(private val products:ProductRepository,private val shopRepository:ShopRepository,private val basket:ShoppingListRepository,
 prefs:SharedPreferences,preselectedShopId:Long?=null):ViewModel(){
 private val _uiState=MutableStateFlow(AddToListUiState(selectedShopId=preselectedShopId ?: -1L,isShopLocked=preselectedShopId!=null,
   saveToDatabase=prefs.getBoolean(ShoppingListViewModel.PREF_GLOBAL_SAVE_NEW_PRODUCT_TO_DB,true)))
 val uiState=_uiState.asStateFlow();private var searchJob:Job?=null
 init{viewModelScope.launch{shopRepository.getCityShops().collect{shops->
   val selected=_uiState.value.selectedShopId
   _uiState.value=_uiState.value.copy(shops=shops,selectedShopId=if(selected!=null&&selected>0&&shops.none{it.id==selected})-1L else selected)
 }}}
 fun onSearchQueryChanged(query:String){
  searchJob?.cancel();_uiState.value=_uiState.value.copy(searchQuery=query,selectedProduct=null,error=null,isSearching=query.isNotBlank(),isNewProduct=false)
  if(query.isBlank()){_uiState.value=_uiState.value.copy(searchResults=emptyList(),isSearching=false);return}
  searchJob=viewModelScope.launch{delay(120);products.searchProducts(query.trim()).collect{results->
   _uiState.value=_uiState.value.copy(searchResults=results,isSearching=false,isNewProduct=results.none{FuzzySearch.normalize(it.name)==FuzzySearch.normalize(query)})
  }}
 }
 fun selectProduct(p:ProductEntity){_uiState.value=_uiState.value.copy(selectedProduct=p,quantity=(p.defaultQuantity?.takeIf{it>0}?:1.0).let{if(it%1.0==0.0)it.toLong().toString()else it.toString()},unit=p.defaultUnit.orEmpty(),error=null)}
 fun quantity(value:String){_uiState.value=_uiState.value.copy(quantity=value,error=null)}
 fun unit(value:String){_uiState.value=_uiState.value.copy(unit=value)}
 fun onShopSelected(id:Long?){_uiState.value=_uiState.value.copy(selectedShopId=id)}
 fun onSaveToDatabaseChanged(value:Boolean){_uiState.value=_uiState.value.copy(saveToDatabase=value)}
 fun add(){
  val state=_uiState.value;if(state.isLoading)return
  val qty=Quantities.parse(state.quantity)
  if(qty==null){_uiState.value=state.copy(error="Введите положительное количество, например 0,5");return}
  if(state.selectedProduct==null&&!state.isNewProduct){_uiState.value=state.copy(error="Выберите товар из найденных");return}
  _uiState.value=state.copy(isLoading=true,error=null)
  viewModelScope.launch{try{
   val choice=when(state.selectedShopId){-1L->ShopChoice.AUTO;null->ShopChoice.NONE;else->ShopChoice.EXPLICIT}
   if(state.selectedProduct!=null)basket.addProduct(state.selectedProduct.id,qty,state.unit,choice,state.selectedShopId)
   else basket.addNewProduct(state.searchQuery,qty,state.unit,state.saveToDatabase,choice,state.selectedShopId)
   searchJob?.cancel()
   _uiState.value=_uiState.value.copy(searchQuery="",searchResults=emptyList(),selectedProduct=null,isNewProduct=false,isLoading=false,isSearching=false,quantity="1",unit="",addedMessage="Добавлено в список")
  }catch(e:Exception){if(e is CancellationException)throw e;_uiState.value=_uiState.value.copy(isLoading=false,error=e.message?:"Не удалось добавить покупку")}}
 }
 fun clearMessage(){_uiState.value=_uiState.value.copy(addedMessage=null)}
 companion object{fun factory(preselectedShopId:Long?=null)=viewModelFactory{initializer{
  val app=this[APPLICATION_KEY] as SpisokRyadomApp
  AddToListViewModel(app.container.productRepository,app.container.shopRepository,app.container.shoppingListRepository,
   app.getSharedPreferences(ShoppingListViewModel.PREFS_NAME_PUBLIC,0),preselectedShopId)
 }}}
}
