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
 val productContexts:Map<Long,String> = emptyMap(),val productContextsWithoutQuantity:Map<Long,String> = emptyMap(),val selectedShopId:Long? = -1L,val isShopLocked:Boolean=false,val saveToDatabase:Boolean=true,
 val isNewProduct:Boolean=false,val addedMessage:String?=null,val isLoading:Boolean=false,val isSearching:Boolean=false,
 val browseProducts:Boolean=true,val browseMessage:String?=null,val pendingProductLabels:Map<Long,String> = emptyMap(),
 val favoriteResults:List<ProductEntity> = emptyList(),val selectedProduct:ProductEntity?=null,val quantity:String="1",val unit:String="",val unitSuggestions:List<String> = emptyList(),val error:String?=null
)
@OptIn(ExperimentalCoroutinesApi::class)
class AddToListViewModel(private val products:ProductRepository,private val shopRepository:ShopRepository,private val basket:ShoppingListRepository,
 private val prefs:SharedPreferences,preselectedShopId:Long?=null,private val preselectedBranchId:Long?=null,
 private val units:Flow<List<UnitEntity>> = flowOf(emptyList())):ViewModel(){
 private val sessionGeneration=ProductEditorTransactions.generation.get()
 private val _uiState=MutableStateFlow(AddToListUiState(quantity=ProductCreationDefaults.read(prefs).let{if(it.fillMissing)java.math.BigDecimal.valueOf(it.missingQuantity).stripTrailingZeros().toPlainString()else ""},selectedShopId=preselectedShopId ?: -1L,isShopLocked=preselectedShopId!=null,
   browseProducts=prefs.getBoolean(PREF_BROWSE_PRODUCTS,true),saveToDatabase=prefs.getBoolean(ShoppingListViewModel.PREF_GLOBAL_SAVE_NEW_PRODUCT_TO_DB,true)))
 val uiState=_uiState.asStateFlow()
 private data class Query(val text:String,val shopId:Long?,val browse:Boolean)
 private val preferenceListener=SharedPreferences.OnSharedPreferenceChangeListener { _,key ->
  if(key==null||key==PREF_BROWSE_PRODUCTS)_uiState.update{it.copy(browseProducts=prefs.getBoolean(PREF_BROWSE_PRODUCTS,true))}
 }
 init {
  prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
  viewModelScope.launch { units.collect{values->_uiState.update{it.copy(unitSuggestions=values.map{v->v.name})}} }
  viewModelScope.launch {
   basket.getAllEntries().collect { entries ->
    val labels=entries.filter{!it.isBought}.groupBy{it.productId}.mapValues{(_,rows)->
     rows.groupBy{Quantities.unit(it.unit)}.map{(unit,values)->
      val total=values.fold(java.math.BigDecimal.ZERO){sum,row->sum+java.math.BigDecimal.valueOf(row.quantity)}
      "${total.stripTrailingZeros().toPlainString().replace('.',',')} $unit".trim()
     }.joinToString(" + ")
    }
    _uiState.update{it.copy(pendingProductLabels=labels)}
   }
  }
  viewModelScope.launch {
   combine(products.getAllProducts(),products.getAllShopLinks(),shopRepository.getShopsForDisplay()){ps,ls,ss->
    val index=com.spisokryadom.app.ui.components.ProductContextIndex(ls,ss)
    ps.associate{it.id to index.context(it)} to ps.associate{it.id to index.context(it,includeQuantity=false)}
   }.flowOn(Dispatchers.Default).collect{(contexts,withoutQuantity)->_uiState.update{it.copy(productContexts=contexts,productContextsWithoutQuantity=withoutQuantity)}}
  }
  viewModelScope.launch { shopRepository.getCityShops().collect{shops->_uiState.update{it.copy(shops=shops)}} }
  viewModelScope.launch {
   combine(products.getAllProducts(),products.getAllShopLinks(),products.observeAvailability(),
    _uiState.map{Query(it.searchQuery,it.selectedShopId,it.browseProducts)}.distinctUntilChanged()
     .mapLatest { q -> if(q.text.isNotBlank())delay(300);q }) { ps,links,availability,q ->
     val result=addProductCandidates(ps,links,availability,q.shopId,preselectedBranchId,q.text,q.browse)
     q to result
    }.flowOn(Dispatchers.Default).collect{(q,result)->
     _uiState.update { state ->
      if(q!=Query(state.searchQuery,state.selectedShopId,state.browseProducts))state
      else state.copy(searchResults=result,favoriteResults=if(q.text.isBlank())result.filter{it.isFavorite}else emptyList(),
       isSearching=false,isNewProduct=q.text.isNotBlank()&&result.none{FuzzySearch.normalize(it.name)==FuzzySearch.normalize(q.text)})
     }
    }
  }
 }
 fun onSearchQueryChanged(query:String){
  _uiState.update{it.copy(searchQuery=query,selectedProduct=null,error=null,isSearching=it.isSearching||query!=it.searchQuery)}
 }
 override fun onCleared(){prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener);super.onCleared()}
 fun selectProduct(p:ProductEntity){_uiState.value=_uiState.value.copy(selectedProduct=p,quantity=(p.defaultQuantity?.takeIf{it>0}?:1.0).let{if(it%1.0==0.0)it.toLong().toString()else it.toString()},unit=p.defaultUnit.orEmpty(),error=null)}
 fun quantity(value:String){_uiState.value=_uiState.value.copy(quantity=value,error=null)}
 fun unit(value:String){_uiState.value=_uiState.value.copy(unit=value)}
 fun createShop(name:String,done:(Long)->Unit){
  if(_uiState.value.isLoading)return
  _uiState.value=_uiState.value.copy(isLoading=true,error=null)
  viewModelScope.launch{try{ProductEditorTransactions.inGeneration(sessionGeneration){
   val id=shopRepository.createInCurrentCity(name);val shop=shopRepository.getShopById(id)?:error("Магазин удалён")
   _uiState.value=_uiState.value.copy(shops=(_uiState.value.shops.filter{it.id!=id}+shop).sortedBy{it.displayOrder},selectedShopId=id)
   done(id)}
  }catch(e:Exception){if(e is CancellationException)throw e;_uiState.value=_uiState.value.copy(error=e.message)}
   finally{_uiState.value=_uiState.value.copy(isLoading=false)}}
 }
 fun onShopSelected(id:Long?){_uiState.update{it.copy(selectedShopId=id,selectedProduct=null,error=null,isSearching=it.isSearching||id!=it.selectedShopId)}}
 fun onSaveToDatabaseChanged(value:Boolean){_uiState.value=_uiState.value.copy(saveToDatabase=value)}
 fun add()=addPurchase(_uiState.value,false)
 fun quickAdd(product:ProductEntity){
  val current=_uiState.value
  if(current.isLoading||current.isSearching||current.searchResults.none{it.id==product.id})return
  addPurchase(current.copy(selectedProduct=product,quantity=(product.defaultQuantity?.takeIf{it>0}?:1.0).toString(),unit=product.defaultUnit.orEmpty()),true)
 }
 private fun addPurchase(state:AddToListUiState,keepBrowsing:Boolean){
  if(state.isLoading||state.isSearching)return
  val qty=if(state.selectedProduct==null&&state.quantity.isBlank())ProductCreationDefaults.read(prefs).let{if(it.fillMissing)it.missingQuantity else 1.0} else Quantities.parse(state.quantity)
  if(qty==null){_uiState.value=state.copy(error="Введите положительное количество, например 0,5");return}
  if(state.selectedProduct==null&&!state.isNewProduct){_uiState.value=state.copy(error="Выберите товар из найденных");return}
  _uiState.update{it.copy(isLoading=true,error=null)}
  viewModelScope.launch{try{ProductEditorTransactions.inGeneration(sessionGeneration){
   val choice=when(state.selectedShopId){-1L->ShopChoice.AUTO;null->ShopChoice.NONE;else->ShopChoice.EXPLICIT}
   if(state.selectedProduct!=null)basket.addProduct(state.selectedProduct.id,qty,state.unit,choice,state.selectedShopId,preselectedBranchId)
   else {
    val defaults=ProductCreationDefaults.read(prefs)
    val mode=state.selectedShopId?.takeIf{it>0}?.let{shopRepository.purchaseMode(it)}?:"inherit"
    basket.addNewProduct(state.searchQuery,qty,state.unit.ifBlank{if(defaults.fillMissing)defaults.missingUnit else ""},state.saveToDatabase,choice,state.selectedShopId,preselectedBranchId,purchaseType=PurchaseModes.resolve(mode,defaults.purchaseType))
   }
   if(keepBrowsing)_uiState.update{it.copy(isLoading=false,browseMessage="${state.selectedProduct?.name}: добавлено")}
   else _uiState.update{it.copy(isLoading=false,addedMessage="Добавлено в список")}}
  }catch(e:Exception){if(e is CancellationException)throw e;_uiState.value=_uiState.value.copy(isLoading=false,error=e.message?:"Не удалось добавить покупку")}}
 }
 fun clearBrowseMessage(){_uiState.update{it.copy(browseMessage=null)}}
 fun clearMessage(){_uiState.value=_uiState.value.copy(addedMessage=null)}
 companion object{const val PREF_BROWSE_PRODUCTS="browse_products_on_add"
 fun factory(preselectedShopId:Long?=null,preselectedBranchId:Long?=null)=viewModelFactory{initializer{
  val app=this[APPLICATION_KEY] as SpisokRyadomApp
  AddToListViewModel(app.container.productRepository,app.container.shopRepository,app.container.shoppingListRepository,
   app.getSharedPreferences(ShoppingListViewModel.PREFS_NAME_PUBLIC,0),preselectedShopId,preselectedBranchId,app.container.classifierRepository.getAllUnits())
 }}}
}
