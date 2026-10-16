package com.spisokryadom.app.ui.bulk
import android.content.SharedPreferences
import androidx.lifecycle.*
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spisokryadom.app.SpisokRyadomApp
import com.spisokryadom.app.data.entity.ProductEntity
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
data class DraftLine(val index:Int,val original:String,val name:String,val quantity:String,val unit:String,val candidates:List<ProductEntity>,val productId:Long?,val resolved:Boolean,val permanent:Boolean=false,val included:Boolean=true,val shopPreview:String="")
data class BulkState(val text:String="",val rows:List<DraftLine> = emptyList(),val review:Boolean=false,val busy:Boolean=false,val error:String?=null,val added:Int=0,val selectedShopId:Long? = -1L)
class BulkViewModel(private val products:ProductRepository,private val basket:ShoppingListRepository,val locations:LocationRepository,private val prefs:SharedPreferences):ViewModel(){
 private val state=MutableStateFlow(BulkState(text=prefs.getString("bulk_text","").orEmpty()))
 val uiState=state.asStateFlow();private var receipt:ShoppingListRepository.BatchReceipt?=null;private var reviewCity=1L
 fun text(value:String){if(!state.value.busy){state.value=state.value.copy(text=value,error=null);prefs.edit().putString("bulk_text",value).apply()}}
 fun shop(id:Long?){state.value=state.value.copy(selectedShopId=id);refreshPreviews()}
 fun edit(){state.value=state.value.copy(review=false,error=null)}
 fun update(index:Int,transform:(DraftLine)->DraftLine){state.value=state.value.copy(rows=state.value.rows.map{if(it.index==index)transform(it)else it},error=null)}
 fun choose(index:Int,id:Long?){update(index){line->val p=line.candidates.find{it.id==id};line.copy(productId=id,resolved=true,unit=line.unit.ifBlank{p?.defaultUnit.orEmpty()})};refreshPreviews()}
 fun review(){
  if(state.value.busy)return
  state.value=state.value.copy(busy=true,error=null)
  viewModelScope.launch{try{
   reviewCity=locations.currentId();val catalog=products.getAllProducts().first();val text=state.value.text
   val rows=withContext(Dispatchers.Default){ShoppingTextParser.parse(text).mapIndexed{i,p->
    val matches=FuzzySearch.rank(catalog,p.name,{it.name}).take(5)
    val exact=matches.filter{FuzzySearch.normalize(it.name)==FuzzySearch.normalize(p.name)}.singleOrNull()
    DraftLine(i,p.original,p.name,if(!p.explicitQuantity&&exact?.defaultQuantity!=null)exact.defaultQuantity.toString()else p.quantity,
      p.unit.ifBlank{exact?.defaultUnit.orEmpty()},matches,exact?.id,exact!=null||matches.isEmpty())
   }}
   require(rows.isNotEmpty()){ "Введите покупки: по одной в строке" }
   state.value=state.value.copy(rows=rows,review=true,busy=false);refreshPreviews()
  }catch(e:Exception){if(e is CancellationException)throw e;state.value=state.value.copy(busy=false,error=e.message)}}
 }
 private var previewJob:Job?=null
 private fun refreshPreviews(){
  previewJob?.cancel();previewJob=viewModelScope.launch{try{
   val snapshot=state.value;val city=reviewCity;val shops=locations.cityShops.first().associateBy{it.id}
   val choice=when(snapshot.selectedShopId){-1L->ShopChoice.AUTO;null->ShopChoice.NONE;else->ShopChoice.EXPLICIT}
   val previews=snapshot.rows.associate{line->
    val name=if(!line.resolved)"Сначала выберите товар" else if(choice==ShopChoice.EXPLICIT)shops[snapshot.selectedShopId]?.name?:"Магазин недоступен"
     else if(choice==ShopChoice.NONE||line.productId==null)"Без магазина" else basket.resolveShop(line.productId,city,choice,null).first?.let{shops[it]?.name}?:"Без магазина"
    line.index to name
   }
   state.update{current->current.copy(rows=current.rows.map{line->line.copy(shopPreview=previews[line.index].orEmpty())})}
  }catch(e:Exception){if(e is CancellationException)throw e}}
 }
 fun add(){
  val s=state.value;if(s.busy||s.added>0)return
  val selected=s.rows.filter{it.included}
  if(selected.any{!it.resolved||Quantities.parse(it.quantity)==null||it.name.isBlank()}){state.value=s.copy(error="Проверьте похожие товары и положительные количества во всех выбранных строках");return}
  state.value=s.copy(busy=true,error=null)
  viewModelScope.launch{try{
   val choice=when(s.selectedShopId){-1L->ShopChoice.AUTO;null->ShopChoice.NONE;else->ShopChoice.EXPLICIT}
   receipt=basket.addBatch(selected.map{ShoppingListRepository.PurchaseRequest(it.productId,it.name,Quantities.parse(it.quantity)!!,it.unit,it.permanent,choice,s.selectedShopId)},reviewCity)
   prefs.edit().remove("bulk_text").apply();state.value=state.value.copy(busy=false,added=selected.size)
  }catch(e:Exception){if(e is CancellationException)throw e;state.value=state.value.copy(busy=false,error=e.message)}}
 }
 fun undo(){val r=receipt?:return;if(state.value.busy)return;state.value=state.value.copy(busy=true)
  viewModelScope.launch{try{basket.undoBatch(r);receipt=null;state.value=state.value.copy(added=0,busy=false);prefs.edit().putString("bulk_text",state.value.text).apply()}
  catch(e:Exception){if(e is CancellationException)throw e;state.value=state.value.copy(busy=false,error=e.message)}}
 }
 companion object{val Factory=viewModelFactory{initializer{val app=this[APPLICATION_KEY] as SpisokRyadomApp;BulkViewModel(app.container.productRepository,app.container.shoppingListRepository,app.container.locations,app.getSharedPreferences("drafts",0))}}}
}
