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
@Immutable data class ShoppingListInfo(val entryId:Long,val quantity:Double,val unit:String,val shopName:String?)
data class ProductSection(val name:String,val products:List<ProductEntity>,val key:String=name)
data class ProductDatabaseUiState(val products:List<ProductEntity> = emptyList(),val sections:List<ProductSection> = emptyList(),val viewMode:ProductViewMode=ProductViewMode.ALPHABETICAL,
 val searchQuery:String="",val unitSuggestions:List<String> = emptyList(),val shoppingListMap:Map<Long,ShoppingListInfo> = emptyMap(),val isLoading:Boolean=true,val error:String?=null)
@OptIn(FlowPreview::class,ExperimentalCoroutinesApi::class)
class ProductDatabaseViewModel(private val products:ProductRepository,private val basket:ShoppingListRepository,private val shops:ShopRepository,private val classifiers:ClassifierRepository,private val prefs:SharedPreferences):ViewModel(){
 private val query=MutableStateFlow("");private val error=MutableStateFlow<String?>(null)
 private val mode=MutableStateFlow(runCatching{ProductViewMode.valueOf(prefs.getString("product_db_view_mode",null).orEmpty())}.getOrDefault(ProductViewMode.ALPHABETICAL))
 private data class Data(val products:List<ProductEntity>,val shops:List<ShopEntity>,val links:List<ProductShopLinkEntity>,val entries:List<ShoppingListEntryEntity>,val groups:List<ProductGroupEntity> = emptyList(),val recipients:List<RecipientEntity> = emptyList())
 private val data=combine(products.getAllProducts(),shops.getCityShops(),products.getAllShopLinks(),basket.getAllEntries()){p,s,l,e->Data(p,s,l,e)}
  .combine(classifiers.getAllGroups()){d,g->d.copy(groups=g)}.combine(classifiers.getAllRecipients()){d,r->d.copy(recipients=r)}
 private val results:StateFlow<ProductDatabaseUiState> = combine(data,query.debounce(120),mode,classifiers.getOrderedUnitNames(),error){d,q,m,u,err->
  val list=if(q.isBlank())d.products else FuzzySearch.rank(d.products,q,{it.name})
  val byId=list.associateBy{it.id};val shopMap=d.shops.associateBy{it.id}
  val sections=when(m){
   ProductViewMode.ALPHABETICAL->listOf(ProductSection(if(q.isBlank())"Все товары" else "По релевантности",list,"all"))
   ProductViewMode.BY_SHOPS->{
    val links=d.links.groupBy{it.shopId};val used=mutableSetOf<Long>()
    buildList{
     d.shops.forEach{shop->val ids=links[shop.id].orEmpty().map{it.productId}.toSet();val ps=list.filter{it.id in ids};used.addAll(ids);if(ps.isNotEmpty())add(ProductSection(shop.name,ps,"shop_${shop.id}"))}
     val ps=list.filter{it.id !in used};if(ps.isNotEmpty())add(ProductSection("Не привязаны в этом городе",ps,"unassigned"))
    }
   }
   ProductViewMode.BY_GROUPS->{val grouped=list.groupBy{it.productGroupId};buildList{d.groups.forEach{g->grouped[g.id]?.let{add(ProductSection(g.name,it,"group_${g.id}"))}};grouped[null]?.let{add(ProductSection("Без группы",it,"no_group"))}}}
   ProductViewMode.BY_RECIPIENTS->{val grouped=list.groupBy{it.recipientId};buildList{d.recipients.forEach{g->grouped[g.id]?.let{add(ProductSection(g.name,it,"recipient_${g.id}"))}};grouped[null]?.let{add(ProductSection("Без получателя",it,"no_recipient"))}}}
  }
  val entries=d.entries.asSequence().filter{!it.isBought&&(it.assignedShopId==null||it.assignedShopId in shopMap)}.sortedBy{it.id}.distinctBy{it.productId}
   .associate{it.productId to ShoppingListInfo(it.id,it.quantity,it.unit,shopMap[it.assignedShopId]?.name)}
  ProductDatabaseUiState(list,sections,m,q,u,entries,false,err)
 }.flowOn(Dispatchers.Default).stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),ProductDatabaseUiState())
 val uiState:StateFlow<ProductDatabaseUiState> = combine(results,query){state,q->state.copy(searchQuery=q)}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),ProductDatabaseUiState())
 fun setViewMode(m:ProductViewMode){mode.value=m;prefs.edit().putString("product_db_view_mode",m.name).apply()}
 fun setSearchQuery(q:String){query.value=q}
 private fun command(block:suspend()->Unit){viewModelScope.launch{try{block()}catch(e:Exception){if(e is CancellationException)throw e;error.value=e.message?:"Операция не выполнена"}}}
 fun clearError(){error.value=null}
 fun quickAddToList(p:ProductEntity)=command{basket.addProduct(p.id)}
 fun incrementQuantity(id:Long)=command{val entry=basket.findActiveByProductId(id)?:return@command;val step=products.getProductById(id)?.defaultQuantity?.takeIf{it>0}?:1.0;basket.adjustQuantity(entry.id,step)}
 fun decrementQuantity(id:Long)=command{val entry=basket.findActiveByProductId(id)?:return@command;val step=products.getProductById(id)?.defaultQuantity?.takeIf{it>0}?:1.0;basket.adjustQuantity(entry.id,-step)}
 fun setQuantity(id:Long,quantity:Double)=command{basket.updateQuantityByProductId(id,quantity)}
 companion object{val Factory=viewModelFactory{initializer{val app=this[APPLICATION_KEY] as SpisokRyadomApp;ProductDatabaseViewModel(app.container.productRepository,app.container.shoppingListRepository,app.container.shopRepository,app.container.classifierRepository,app.getSharedPreferences("product_db_prefs",0))}}}
}
