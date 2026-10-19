package com.spisokryadom.app.ui.productdb
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import androidx.lifecycle.*
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spisokryadom.app.SpisokRyadomApp
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.util.FuzzySearch
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
enum class ProductViewMode(val label:String){ALPHABETICAL("А-Я"),BY_SHOPS("Магазины"),BY_GROUPS("Группы"),BY_RECIPIENTS("Получатели")}
@Immutable data class ShoppingListInfo(val entryId:Long,val quantity:Double,val unit:String,val shopName:String?,val purchases:List<com.spisokryadom.app.ui.components.PendingPurchase> = emptyList())
data class ProductDepartmentSection(val name:String,val products:List<ProductEntity>,val key:String,val accent:String="",val iconKey:String="")
data class ProductSection(val name:String,val products:List<ProductEntity>,val key:String=name,val shopId:Long?=null,val departments:List<ProductDepartmentSection> = emptyList(),val accent:String="",val iconKey:String="")
data class ProductDatabaseUiState(val products:List<ProductEntity> = emptyList(),val sections:List<ProductSection> = emptyList(),val viewMode:ProductViewMode=ProductViewMode.ALPHABETICAL,
 val favoritesOnly:Boolean=false,val shopShoppingListMap:Map<Long,Map<Long,ShoppingListInfo>> = emptyMap(),
 val searchQuery:String="",val unitSuggestions:List<String> = emptyList(),val shoppingListMap:Map<Long,ShoppingListInfo> = emptyMap(),val isLoading:Boolean=true,val error:String?=null,val searchedQuery:String="",val exactNameExists:Boolean=false)
@OptIn(FlowPreview::class,ExperimentalCoroutinesApi::class)
class ProductDatabaseViewModel(private val products:ProductRepository,private val basket:ShoppingListRepository,private val shops:ShopRepository,private val classifiers:ClassifierRepository,private val prefs:SharedPreferences):ViewModel(){
 private val favorites=MutableStateFlow(prefs.getBoolean("favorites_only",false))
 private val query=MutableStateFlow("");private val error=MutableStateFlow<String?>(null)
 private val mode=MutableStateFlow(runCatching{ProductViewMode.valueOf(prefs.getString("product_db_view_mode",null).orEmpty())}.getOrDefault(ProductViewMode.ALPHABETICAL))
 private val preferenceListener=SharedPreferences.OnSharedPreferenceChangeListener{_,key->
  if(key==null||key=="favorites_only")favorites.value=prefs.getBoolean("favorites_only",false)
  if(key==null||key=="product_db_view_mode")mode.value=runCatching{ProductViewMode.valueOf(prefs.getString("product_db_view_mode",null).orEmpty())}.getOrDefault(ProductViewMode.ALPHABETICAL)
 }
 init{prefs.registerOnSharedPreferenceChangeListener(preferenceListener)}
 override fun onCleared(){prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener);super.onCleared()}
 private data class Data(val products:List<ProductEntity>,val shops:List<ShopEntity>,val links:List<ProductShopLinkEntity>,val entries:List<ShoppingListEntryEntity>,val groups:List<ProductGroupEntity> = emptyList(),val recipients:List<RecipientEntity> = emptyList(),val branches:List<ShopBranchEntity> = emptyList(),val departments:List<ShopDepartmentEntity> = emptyList(),val types:List<DepartmentTypeEntity> = emptyList(),val favoritesOnly:Boolean=false)
 private val data=combine(products.getAllProducts(),shops.getCityShops(),products.getAllShopLinks(),basket.getAllEntries()){p,s,l,e->Data(p,s,l,e)}
  .combine(classifiers.getAllGroups()){d,g->d.copy(groups=g)}.combine(classifiers.getAllRecipients()){d,r->d.copy(recipients=r)}.combine(shops.observeBranches()){d,b->d.copy(branches=b)}.combine(shops.getAllDepartments()){d,deps->d.copy(departments=deps)}.combine(shops.departmentTypes()){d,t->d.copy(types=t)}.combine(favorites){d,f->d.copy(favoritesOnly=f)}
 private val results:StateFlow<ProductDatabaseUiState> = combine(data,query.debounce(120),mode,classifiers.getOrderedUnitNames(),error){d,q,m,u,err->
  val available=if(d.favoritesOnly)d.products.filter{it.isFavorite}else d.products
  val list=if(q.isBlank())available.sortedWith(compareBy<ProductEntity>{FuzzySearch.normalize(it.name)}.thenBy{it.id})else FuzzySearch.rank(available,q,{it.name})
  val shopMap=d.shops.associateBy{it.id};val branchMap=d.branches.associateBy{it.id}
  val sections=when(m){
   ProductViewMode.ALPHABETICAL->listOf(ProductSection(if(q.isBlank())"Все товары" else "По релевантности",list,"all"))
   ProductViewMode.BY_SHOPS->{
    val links=d.links.groupBy{it.shopId};val used=mutableSetOf<Long>()
    buildList{
     d.shops.forEach{shop->
      val localLinks=links[shop.id].orEmpty().associateBy{it.productId};val ids=localLinks.keys;val ps=list.filter{it.id in ids};used.addAll(ids)
      if(ps.isNotEmpty()){
       val grouped=ps.groupBy{localLinks[it.id]?.departmentId};val byType=d.types.associateBy{it.id}
       val departments=buildList {
        d.departments.filter{it.shopId==shop.id}.sortedBy{it.displayOrder}.forEach{dep->grouped[dep.id]?.let{rows->val style=byType[dep.typeId];add(ProductDepartmentSection(dep.name,rows,"dept_${dep.id}",style?.accent.orEmpty(),style?.iconKey.orEmpty()))}}
        grouped[null]?.let{add(ProductDepartmentSection("Без группы магазина",it,"no_dept"))}
       }
       add(ProductSection(shop.name,departments.flatMap{it.products},"shop_${shop.id}",shop.id,departments))
      }
     }
     val ps=list.filter{it.id !in used};if(ps.isNotEmpty())add(ProductSection("Не привязаны в этом городе",ps,"unassigned"))
    }
   }
   ProductViewMode.BY_GROUPS->{val grouped=list.groupBy{it.productGroupId};buildList{d.groups.sortedBy{it.displayOrder}.forEach{g->grouped[g.id]?.let{add(ProductSection(g.name,it,"group_${g.id}",accent=g.accent,iconKey=g.iconKey))}};grouped[null]?.let{add(ProductSection("Без группы",it,"no_group"))}}}
   ProductViewMode.BY_RECIPIENTS->{val grouped=list.groupBy{it.recipientId};buildList{d.recipients.sortedBy{it.displayOrder}.forEach{g->grouped[g.id]?.let{add(ProductSection(g.name,it,"recipient_${g.id}",accent=g.accent,iconKey=g.iconKey))}};grouped[null]?.let{add(ProductSection("Без получателя",it,"no_recipient"))}}}
  }
  fun info(rows:List<ShoppingListEntryEntity>)=rows.groupBy{it.productId}.mapValues{(_,purchases)->val first=purchases.minBy{it.id};ShoppingListInfo(first.id,first.quantity,first.unit,shopMap[first.assignedShopId]?.name,com.spisokryadom.app.ui.components.pendingPurchases(purchases,shopMap,branchMap))}
  val pending=d.entries.filter{it.listId==null&&!it.isBought&&(it.assignedShopId==null||it.assignedShopId in shopMap)}
  val entries=info(pending)
  ProductDatabaseUiState(searchedQuery=q,exactNameExists=d.products.any{FuzzySearch.normalize(it.name)==FuzzySearch.normalize(q)},products=list,sections=(if(!d.favoritesOnly&&list.any{it.isFavorite})listOf(ProductSection("Избранное",list.filter{it.isFavorite},"favorites"))else emptyList())+sections,viewMode=m,searchQuery=q,unitSuggestions=u,shoppingListMap=entries,isLoading=false,error=err,
   favoritesOnly=d.favoritesOnly,shopShoppingListMap=pending.groupBy{it.assignedShopId?:0L}.mapValues{info(it.value)})
 }.flowOn(Dispatchers.Default).stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),ProductDatabaseUiState())
 val uiState:StateFlow<ProductDatabaseUiState> = combine(results,query){state,q->state.copy(searchQuery=q)}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),ProductDatabaseUiState())
 fun setViewMode(m:ProductViewMode){mode.value=m;prefs.edit().putString("product_db_view_mode",m.name).apply()}
 fun setSearchQuery(q:String){query.value=q}
 fun revealProduct(){query.value="";favorites.value=false;prefs.edit().putBoolean("favorites_only",false).apply()}
 private fun command(block:suspend()->Unit){val generation=ProductEditorTransactions.generation.get();viewModelScope.launch{try{ProductEditorTransactions.inGeneration(generation,block)}catch(e:Exception){if(e is CancellationException)throw e;error.value=e.message?:"Операция не выполнена"}}}
 fun clearError(){error.value=null}
 fun toggleFavorites(){favorites.value=!favorites.value;prefs.edit().putBoolean("favorites_only",favorites.value).apply()}
 fun quickAddToList(p:ProductEntity,shopId:Long?=null,scoped:Boolean=false)=command{basket.addProduct(p.id,choice=if(scoped&&shopId!=null)ShopChoice.EXPLICIT else ShopChoice.AUTO,shopId=shopId)}
 fun incrementEntry(entryId:Long)=command{basket.adjustByDefaultStep(entryId,1,regularOnly=true)}
 fun decrementEntry(entryId:Long)=command{basket.adjustByDefaultStep(entryId,-1,regularOnly=true)}
 fun setEntryQuantity(entryId:Long,quantity:Double)=command{if(quantity<=0)basket.removeEntry(entryId,regularOnly=true)else basket.updateQuantity(entryId,quantity,regularOnly=true)}
 // Capture the displayed identity before launching; never reselect a different row by product.
 fun incrementQuantity(productId:Long){uiState.value.shoppingListMap[productId]?.takeIf{it.purchases.size<=1}?.let{incrementEntry(it.entryId)}}
 fun decrementQuantity(productId:Long){uiState.value.shoppingListMap[productId]?.takeIf{it.purchases.size<=1}?.let{decrementEntry(it.entryId)}}
 fun setQuantity(productId:Long,quantity:Double){uiState.value.shoppingListMap[productId]?.takeIf{it.purchases.size<=1}?.let{setEntryQuantity(it.entryId,quantity)}}
 companion object{val Factory=viewModelFactory{initializer{val app=this[APPLICATION_KEY] as SpisokRyadomApp;ProductDatabaseViewModel(app.container.productRepository,app.container.shoppingListRepository,app.container.shopRepository,app.container.classifierRepository,app.getSharedPreferences("product_db_prefs",0))}}}
}
