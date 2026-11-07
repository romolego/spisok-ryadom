package com.spisokryadom.app.ui.addtolist

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.spisokryadom.app.SpisokRyadomApp
import com.spisokryadom.app.data.entity.ProductEntity
import com.spisokryadom.app.data.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.*

data class BulkProductState(val text:String="",val rows:List<BulkProductRow> = emptyList(),val preview:Boolean=false,
    val choosing:Int?=null,val query:String="",val candidates:List<ProductEntity> = emptyList(),val searching:Boolean=false,
    val matches:Map<Int,List<ProductEntity>> = emptyMap(),val preparedText:String="",val saveNew:Boolean=true,val busy:Boolean=false,val error:String?=null,val completed:Boolean=false)

class BulkProductViewModel(private val repository:BulkPurchaseRepository,private val handle:SavedStateHandle,
    private val defaultSaveNew:Boolean=true,private val fallbackUnit:()->String={""},private val revision:()->String={""},private val store:android.content.SharedPreferences?=null):ViewModel() {
    private var token=handle.get<String>("token")?:java.util.UUID.randomUUID().toString()
    private var city=handle.get<Long>("city")?:-1L
    private var databaseRevision=handle.get<String>("revision")?:revision()
    private val generation=ProductEditorTransactions.generation.get()
    private val _state=MutableStateFlow(restore())
    val state:StateFlow<BulkProductState> = _state.asStateFlow()
    private var searchJob:Job?=null
    private fun restore():BulkProductState=runCatching{
        val j=JSONObject(handle.get<String>("draft")?:return BulkProductState(saveNew=defaultSaveNew))
        BulkProductState(preparedText=j.optString("preparedText"),text=j.getString("text"),preview=j.getBoolean("preview"),saveNew=j.getBoolean("saveNew"),choosing=if(j.isNull("choosing"))null else j.optInt("choosing"),query=j.optString("query"),rows=j.getJSONArray("rows").let{a->(0 until a.length()).map{i->a.getJSONObject(i).let{o->BulkProductRow(o.getInt("key"),o.getString("source"),o.getString("name"),if(o.isNull("id"))null else o.getLong("id"),if(o.isNull("stable"))null else o.getString("stable"),o.getBoolean("resolved"),o.getString("quantity"))}}})
    }.getOrElse{BulkProductState(saveNew=defaultSaveNew)}
    private fun update(change:(BulkProductState)->BulkProductState){
        val value=change(_state.value);_state.value=value
        handle["token"]=token;handle["city"]=city;handle["revision"]=databaseRevision
        handle["draft"]=JSONObject().put("text",value.text).put("preview",value.preview).put("saveNew",value.saveNew)
            .put("preparedText",value.preparedText).put("choosing",value.choosing?:JSONObject.NULL).put("query",value.query)
            .put("rows",JSONArray(value.rows.map{r->JSONObject().put("key",r.key).put("source",r.source).put("name",r.name).put("id",r.productId?:JSONObject.NULL).put("stable",r.stableId?:JSONObject.NULL).put("resolved",r.resolved).put("quantity",r.quantity)})).toString()
        store?.edit()?.putString("draft",handle.get<String>("draft"))?.putString("token",token)?.putLong("city",city)?.putString("revision",databaseRevision)?.apply()
    }
    init { if(_state.value.choosing!=null)search(_state.value.query,true); if(_state.value.preview)viewModelScope.launch{loadMatches(_state.value.rows)} }
    private suspend fun loadMatches(rows:List<BulkProductRow>){
        val matches=withContext(Dispatchers.IO){rows.associate{it.key to repository.candidates(it.source,city)}}
        update{it.copy(matches=matches)}
    }
    fun text(value:String){if(!_state.value.busy)update{if(value.length>com.spisokryadom.app.domain.BulkProductInput.MAX_TEXT)it.copy(error="Слишком длинный текст: до 16000 символов")else it.copy(text=value,error=null)}}
    fun saveNew(value:Boolean){if(!_state.value.busy)update{it.copy(saveNew=value)}}
    fun prepare(){
        if(_state.value.busy)return
        update{it.copy(busy=true,error=null)}
        viewModelScope.launch{try{
            val currentCity=repository.cityId()
            if(city==currentCity&&databaseRevision==revision()&&_state.value.preparedText==_state.value.text&&_state.value.rows.isNotEmpty()){
                update{it.copy(preview=true,busy=false,error=null)};return@launch
            }
            city=currentCity;databaseRevision=revision();val rows=withContext(Dispatchers.IO){repository.preview(_state.value.text,city)}
            token=java.util.UUID.randomUUID().toString();loadMatches(rows);update{it.copy(rows=rows,preparedText=it.text,preview=true,busy=false,choosing=null)}
        }catch(e:Exception){if(e is CancellationException)throw e;update{it.copy(busy=false,error=e.message)}}}
    }
    fun back(){if(!_state.value.busy){searchJob?.cancel();update{if(it.choosing!=null)it.copy(choosing=null,searching=false)else it.copy(preview=false,error=null)}}}
    fun selectFor(key:Int,product:ProductEntity?){
        if(_state.value.busy)return
        if(product!=null&&_state.value.matches[key].orEmpty().none{it.id==product.id&&it.stableId==product.stableId})return
        update{it.copy(rows=it.rows.map{r->if(r.key==key)r.copy(name=product?.name?:r.source,productId=product?.id,stableId=product?.stableId,resolved=true)else r},error=null)}
    }
    fun newName(key:Int,value:String){if(!_state.value.busy)update{it.copy(rows=it.rows.map{r->if(r.key==key&&r.resolved&&r.productId==null)r.copy(name=value.take(500))else r})}}
    fun quantity(key:Int,value:String){if(!_state.value.busy)update{it.copy(rows=it.rows.map{r->if(r.key==key)r.copy(quantity=value.take(30))else r},error=null)}}
    fun remove(key:Int){if(!_state.value.busy)update{it.copy(rows=it.rows.filter{r->r.key!=key},error=null)}}
    fun choose(key:Int){
        if(_state.value.busy)return
        val row=_state.value.rows.find{it.key==key}?:return
        update{it.copy(choosing=key,query=row.source,error=null)};search(row.source,true)
    }
    fun query(value:String)=search(value,false)
    private fun search(value:String,force:Boolean){
        if(_state.value.busy||_state.value.choosing==null)return
        if(!force&&com.spisokryadom.app.util.FuzzySearch.normalize(value)==com.spisokryadom.app.util.FuzzySearch.normalize(_state.value.query)){
            if(value!=_state.value.query)update{it.copy(query=value.take(500))}
            return
        }
        update{it.copy(query=value.take(500),searching=true,candidates=emptyList())};searchJob?.cancel()
        val query=_state.value.query;val row=_state.value.choosing
        searchJob=viewModelScope.launch{try{
            val matches=withContext(Dispatchers.Default){delay(180);repository.candidates(query,city)}
            if(_state.value.choosing==row&&com.spisokryadom.app.util.FuzzySearch.normalize(_state.value.query)==com.spisokryadom.app.util.FuzzySearch.normalize(query))update{it.copy(candidates=matches,searching=false)}
        }catch(e:Exception){if(e is CancellationException)throw e;update{it.copy(searching=false,error=e.message)}}}
    }
    fun select(product:ProductEntity?){
        val s=_state.value;if(s.busy||s.searching||s.choosing==null||product==null&&s.query.isBlank())return
        if(product!=null&&s.candidates.none{it.id==product.id&&it.stableId==product.stableId})return
        update{it.copy(choosing=null,rows=it.rows.map{r->if(r.key==s.choosing)r.copy(name=product?.name?:s.query.trim(),productId=product?.id,stableId=product?.stableId,resolved=true)else r},matches=if(product==null)it.matches else it.matches+(s.choosing to (listOf(product)+it.matches[s.choosing].orEmpty()).distinctBy{p->p.id}),error=null)}
    }
    fun add(shopId:Long?,branchId:Long?){
        val s=_state.value;if(s.busy||!s.preview||s.choosing!=null)return
        update{it.copy(busy=true,error=null)}
        viewModelScope.launch{try{
            require(databaseRevision==revision()){"База восстановлена. Подготовьте список заново"}
            val choice=when(shopId){-1L->ShopChoice.AUTO;null->ShopChoice.NONE;else->ShopChoice.EXPLICIT}
            withContext(Dispatchers.IO){repository.add(token,s.rows,city,choice,shopId,branchId,s.saveNew,fallbackUnit(),generation)}
            update{it.copy(busy=false,completed=true)}
        }catch(e:Exception){if(e is CancellationException)throw e;update{it.copy(busy=false,error=e.message)}}}
    }
    fun consumeCompletion(){token=java.util.UUID.randomUUID().toString();update{BulkProductState(saveNew=defaultSaveNew)}}
    companion object {
        val Factory=viewModelFactory{initializer{
            val app=this[APPLICATION_KEY] as SpisokRyadomApp
            val prefs=app.getSharedPreferences("shopping_list_prefs",0)
            val handle=createSavedStateHandle()
            val draft=app.getSharedPreferences("bulk_product_draft",0)
            if(!handle.contains("draft")&&draft.contains("draft")){
                handle["draft"]=draft.getString("draft",null);handle["token"]=draft.getString("token",null)
                handle["city"]=draft.getLong("city",-1);handle["revision"]=draft.getString("revision",null)
            }
            BulkProductViewModel(BulkPurchaseRepository(app.container.database,app.container.shoppingListRepository,app.container.locations),handle,prefs.getBoolean(com.spisokryadom.app.ui.shoppinglist.ShoppingListViewModel.PREF_GLOBAL_SAVE_NEW_PRODUCT_TO_DB,true),{
                com.spisokryadom.app.util.ProductCreationDefaults.read(prefs).let{if(it.fillMissing)it.missingUnit else ""}
            },revision={com.spisokryadom.app.util.DatabaseRevision.current(app)},store=draft)
        }}
    }
}
