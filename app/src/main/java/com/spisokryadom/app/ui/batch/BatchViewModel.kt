package com.spisokryadom.app.ui.batch

import android.content.Context
import androidx.lifecycle.*
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spisokryadom.app.SpisokRyadomApp
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.ui.productcard.ProductDraftStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock

data class BatchOptions(val recipients:List<RecipientEntity> = emptyList(),val groups:List<ProductGroupEntity> = emptyList(),val shops:List<ShopEntity> = emptyList(),val types:List<DepartmentTypeEntity> = emptyList(),val links:List<ProductShopLinkEntity> = emptyList(),val branches:List<ShopBranchEntity> = emptyList(),val availability:List<ProductBranchAvailabilityEntity> = emptyList(),val isLoading:Boolean=true)
class BatchViewModel(private val context:Context,
 private val productRepository:ProductRepository,private val shopRepository:ShopRepository,
 private val shoppingListRepository:ShoppingListRepository,private val classifierRepository:ClassifierRepository,
 private val batchRepository:BatchRepository):ViewModel(){
 val busy=MutableStateFlow(false)
 val error=MutableStateFlow<String?>(null)
 val notice=MutableStateFlow<String?>(null)
 val options=combine(classifierRepository.getAllRecipients(),classifierRepository.getAllGroups(),shopRepository.getCityShops(),shopRepository.departmentTypes(),productRepository.getAllShopLinks()){r,g,s,t,l->BatchOptions(r,g,s,t,l)}
  .combine(shopRepository.observeBranches()){v,b->v.copy(branches=b)}
  .combine(productRepository.observeBranchAvailability()){v,a->v.copy(availability=a,isLoading=false)}
  // An empty initial snapshot is not an empty catalogue. Reopening after collection stops
  // must also wait for all live sources instead of choosing a destination from stale data.
  .stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000,replayExpirationMillis=0),BatchOptions())
 fun run(done:()->Unit,block:suspend()->Unit){
  if(busy.value)return
  busy.value=true;error.value=null
  viewModelScope.launch {try{withContext(Dispatchers.IO){block()};notice.value="Изменения сохранены";done()}catch(e:Exception){if(e is CancellationException)throw e;error.value=e.message?:"Операция не выполнена"}finally{busy.value=false}}
 }
 fun products(ids:Set<Long>,change:ProductBatchChange,done:()->Unit)=run(done){ProductDraftStore.applyBatch(context,productRepository,ids,change){batchRepository.changeProducts(ids,change)}}
 fun shops(ids:Set<Long>,types:Set<Long>,done:()->Unit)=run(done){batchRepository.addDepartments(ids,types)}
 fun purchases(ids:Set<Long>,change:PurchaseBatchChange,done:()->Unit)=run(done){batchRepository.changePurchases(ids,change)}
 fun add(ids:Set<Long>,shop:Long?,done:()->Unit,branchId:Long?=null)=run(done){batchRepository.addToBasket(ids,shop,branchId)}
 fun create(kind:String,name:String,result:(Long)->Unit)=run({}){
  require(name.isNotBlank()){ "Введите название" }
  val key=com.spisokryadom.app.util.FuzzySearch.normalize(name)
  val id=when(kind){
   "recipient"->classifierRepository.getAllRecipients().first().find{com.spisokryadom.app.util.FuzzySearch.normalize(it.name)==key}?.id?:classifierRepository.insertRecipient(RecipientEntity(name=name.trim(),displayOrder=classifierRepository.getMaxRecipientDisplayOrder()+1))
   "group"->classifierRepository.getAllGroups().first().find{com.spisokryadom.app.util.FuzzySearch.normalize(it.name)==key}?.id?:classifierRepository.insertGroup(ProductGroupEntity(name=name.trim(),displayOrder=classifierRepository.getMaxGroupDisplayOrder()+1))
   "shop"->shopRepository.createInCurrentCity(name)
   else->shopRepository.createDepartmentType(name)
  }
  withContext(Dispatchers.Main){result(id)}
 }
 companion object{val Factory=viewModelFactory{initializer{val app=this[APPLICATION_KEY] as SpisokRyadomApp;val c=app.container
  BatchViewModel(app,c.productRepository,c.shopRepository,c.shoppingListRepository,c.classifierRepository,c.batchRepository)}}}
}
