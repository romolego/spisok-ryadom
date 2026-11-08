package com.spisokryadom.app.ui.addtolist

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.spisokryadom.app.SpisokRyadomApp
import com.spisokryadom.app.data.entity.ProductEntity
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.domain.BulkProductInput
import com.spisokryadom.app.util.Quantities
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.*
import java.util.UUID

data class BulkQueuedDraft(val id:String,val text:String,val draft:String,val token:String,val city:Long,val revision:String)
data class BulkProductState(val text:String="",val rows:List<BulkProductRow> = emptyList(),val preview:Boolean=false,
    val choosing:Int?=null,val query:String="",val candidates:List<ProductEntity> = emptyList(),val searching:Boolean=false,
    val matches:Map<Int,List<ProductEntity>> = emptyMap(),val preparedText:String="",val saveNew:Boolean=true,
    val busy:Boolean=false,val error:String?=null,val completed:Boolean=false,
    val queue:List<BulkQueuedDraft> = emptyList(),val activeQueueId:String?=null)

class BulkProductViewModel(private val repository:BulkPurchaseRepository,private val handle:SavedStateHandle,
    private val defaultSaveNew:Boolean=true,private val fallbackUnit:()->String={""},private val revision:()->String={""},
    private val store:android.content.SharedPreferences?=null):ViewModel() {
    private fun saved(key:String)=handle.get<String>(key)?:store?.getString(key,null)
    private var token=saved("token")?:UUID.randomUUID().toString()
    private var city=handle.get<Long>("city")?:store?.getLong("city",-1L)?:-1L
    private var databaseRevision=saved("revision")?:revision()
    private val generation=ProductEditorTransactions.generation.get()
    private val _state=MutableStateFlow(readDraft(saved("draft")).copy(queue=readQueue(),activeQueueId=saved("activeQueueId")))
    val state:StateFlow<BulkProductState> = _state.asStateFlow()
    private var searchJob:Job?=null
    private var matchesJob:Job?=null
    private fun readDraft(raw:String?):BulkProductState=runCatching{
        val j=JSONObject(raw?:return BulkProductState(saveNew=defaultSaveNew))
        BulkProductState(preparedText=j.optString("preparedText"),text=j.getString("text"),preview=j.getBoolean("preview"),saveNew=j.getBoolean("saveNew"),
            choosing=if(j.isNull("choosing"))null else j.getInt("choosing"),query=j.optString("query"),
            rows=j.getJSONArray("rows").let{a->(0 until a.length()).map{i->a.getJSONObject(i).let{o->
                BulkProductRow(o.getInt("key"),o.getString("source"),o.getString("name"),if(o.isNull("id"))null else o.getLong("id"),
                    if(o.isNull("stable"))null else o.getString("stable"),o.getBoolean("resolved"),o.getString("quantity"))}}})
    }.getOrElse{BulkProductState(saveNew=defaultSaveNew)}
    private fun readQueue():List<BulkQueuedDraft> = runCatching{
        val a=JSONArray(saved("queue")?:"[]")
        (0 until a.length()).map{i->a.getJSONObject(i).let{BulkQueuedDraft(it.getString("id"),it.getString("text"),it.getString("draft"),it.getString("token"),it.getLong("city"),it.getString("revision"))}}
    }.getOrDefault(emptyList())
    private fun encode(s:BulkProductState)=JSONObject().put("text",s.text).put("preview",s.preview).put("saveNew",s.saveNew)
        .put("preparedText",s.preparedText).put("choosing",s.choosing?:JSONObject.NULL).put("query",s.query)
        .put("rows",JSONArray(s.rows.map{r->JSONObject().put("key",r.key).put("source",r.source).put("name",r.name)
            .put("id",r.productId?:JSONObject.NULL).put("stable",r.stableId?:JSONObject.NULL).put("resolved",r.resolved).put("quantity",r.quantity)})).toString()
    private fun batch(s:BulkProductState,id:String)=BulkQueuedDraft(id,s.text,encode(s.copy(choosing=null)),token,city,databaseRevision)
    private fun update(change:(BulkProductState)->BulkProductState){
        var value=change(_state.value)
        value.activeQueueId?.let{id->value=value.copy(queue=value.queue.map{if(it.id==id)batch(value,id)else it})}
        _state.value=value
        val draft=encode(value)
        val queue=JSONArray(value.queue.map{JSONObject().put("id",it.id).put("text",it.text).put("draft",it.draft)
            .put("token",it.token).put("city",it.city).put("revision",it.revision)}).toString()
        handle["token"]=token;handle["city"]=city;handle["revision"]=databaseRevision;handle["draft"]=draft;handle["activeQueueId"]=value.activeQueueId
        // Disk owns the queue in production, keeping the Activity Bundle bounded to one draft.
        if(store==null)handle["queue"]=queue
        store?.edit()?.putString("draft",draft)?.putString("queue",queue)?.putString("token",token)?.putLong("city",city)
            ?.putString("revision",databaseRevision)?.putString("activeQueueId",value.activeQueueId)?.apply()
    }
    init {
        if(_state.value.choosing!=null)search(_state.value.query,true)
        if(_state.value.preview)reloadMatches()
        val pending=_state.value.queue.filter{it.revision==revision()}.map{it.id to it.token}
        val currentToken=token;val currentRevision=databaseRevision
        viewModelScope.launch{try{
            val (done,currentDone)=withContext(Dispatchers.IO){
                pending.filter{repository.committed(it.second)}.toSet() to
                    (currentRevision==revision()&&repository.committed(currentToken))
            }
            val remaining=_state.value.queue.filter{(it.id to it.token) !in done}
            if(token==currentToken&&currentDone||(_state.value.activeQueueId to token) in done){searchJob?.cancel();matchesJob?.cancel();reset(remaining)}
            else if(remaining!=_state.value.queue)update{it.copy(queue=remaining)}
        }catch(e:Exception){if(e is CancellationException)throw e;update{it.copy(error="Не удалось проверить очередь: ${e.message.orEmpty()}")}}}
    }
    private fun reloadMatches(){
        matchesJob?.cancel();val rows=_state.value.rows;val batchToken=token
        matchesJob=viewModelScope.launch{try{
            val found=withContext(Dispatchers.IO){rows.associate{r->
                val options=repository.candidates(r.source,city)
                val selected=r.productId?.let{repository.product(it)}?.takeIf{it.stableId==r.stableId}
                r.key to (listOfNotNull(selected)+options).distinctBy{it.id}
            }}
            if(token==batchToken)update{it.copy(matches=found)}
        }catch(e:Exception){if(e is CancellationException)throw e;update{it.copy(error=e.message)}}}
    }
    fun text(value:String){if(!_state.value.busy)update{if(value.length>BulkProductInput.MAX_TEXT)it.copy(error="Слишком длинный текст: до 16000 символов")else it.copy(text=value,error=null)}}
    fun saveNew(value:Boolean){if(!_state.value.busy)update{it.copy(saveNew=value)}}
    fun prepare(){
        if(_state.value.busy)return
        searchJob?.cancel();matchesJob?.cancel();update{it.copy(busy=true,error=null,choosing=null,searching=false)}
        val source=_state.value.text;val preparingToken=token
        viewModelScope.launch{try{
            val alreadyAdded=databaseRevision==revision()&&withContext(Dispatchers.IO){repository.committed(preparingToken)}
            if(token!=preparingToken)return@launch
            if(alreadyAdded){reset(_state.value.queue.filter{it.id!=_state.value.activeQueueId});return@launch}
            val currentCity=repository.cityId()
            if(city==currentCity&&databaseRevision==revision()&&_state.value.preparedText==source&&source.isNotBlank()){
                update{it.copy(preview=true,busy=false)};reloadMatches();return@launch
            }
            city=currentCity;databaseRevision=revision()
            val rows=withContext(Dispatchers.IO){repository.preview(source,city)}
            val found=withContext(Dispatchers.IO){rows.associate{it.key to repository.candidates(it.source,city)}}
            if(token!=preparingToken)return@launch
            token=UUID.randomUUID().toString()
            update{it.copy(rows=rows,matches=found,preparedText=source,preview=true,busy=false)}
        }catch(e:Exception){if(e is CancellationException)throw e;update{it.copy(busy=false,error=e.message)}}}
    }
    /** An unchecked batch is saved without catalogue or purchase writes. */
    fun enqueue(){
        val s=_state.value;if(s.busy||s.text.isBlank()&&s.rows.isEmpty())return
        searchJob?.cancel();matchesJob?.cancel()
        val id=s.activeQueueId?:UUID.randomUUID().toString()
        val queue=if(s.queue.any{it.id==id})s.queue.map{if(it.id==id)batch(s,id)else it}else s.queue+batch(s,id)
        reset(queue)
    }
    fun openQueued(id:String){
        if(_state.value.busy)return
        if(_state.value.activeQueueId!=id&&(_state.value.text.isNotBlank()||_state.value.rows.isNotEmpty()))enqueue()
        val queued=_state.value.queue.find{it.id==id}?:return
        searchJob?.cancel();matchesJob?.cancel();token=queued.token;city=queued.city;databaseRevision=queued.revision
        val queue=_state.value.queue
        update{readDraft(queued.draft).copy(queue=queue,activeQueueId=id,choosing=null,preview=false)}
        prepare()
    }
    fun removeQueued(id:String){if(!_state.value.busy){if(_state.value.activeQueueId==id)clear()else update{it.copy(queue=it.queue.filter{q->q.id!=id})}}}
    fun clear(){if(!_state.value.busy){searchJob?.cancel();matchesJob?.cancel();reset(_state.value.queue.filter{it.id!=_state.value.activeQueueId})}}
    private fun reset(queue:List<BulkQueuedDraft>,completed:Boolean=false){
        token=UUID.randomUUID().toString();city=-1L;databaseRevision=revision()
        update{BulkProductState(saveNew=defaultSaveNew,queue=queue,completed=completed)}
    }
    fun back(){if(!_state.value.busy){searchJob?.cancel();update{if(it.choosing!=null)it.copy(choosing=null,searching=false)else it.copy(preview=false,error=null)}}}
    fun selectFor(key:Int,product:ProductEntity?){
        if(_state.value.busy)return
        if(product!=null&&_state.value.matches[key].orEmpty().none{it.id==product.id&&it.stableId==product.stableId})return
        update{it.copy(rows=it.rows.map{r->if(r.key==key)r.copy(name=product?.name?:r.source,productId=product?.id,stableId=product?.stableId,resolved=true)else r},error=null)}
    }
    fun newName(key:Int,value:String){if(!_state.value.busy)update{it.copy(rows=it.rows.map{r->if(r.key==key&&r.resolved&&r.productId==null)r.copy(name=value.take(500))else r})}}
    fun quantity(key:Int,value:String){
        if(_state.value.busy)return
        if(value.trim().replace(',','.').toDoubleOrNull()==0.0){remove(key);return}
        update{it.copy(rows=it.rows.map{r->if(r.key==key)r.copy(quantity=value.take(30))else r},error=null)}
    }
    fun step(key:Int,delta:Int){
        val row=_state.value.rows.find{it.key==key}?:return
        val next=((Quantities.parse(row.quantity)?:1.0)+delta).coerceAtMost(1_000_000_000.0)
        if(next<=0)remove(key)else quantity(key,java.math.BigDecimal.valueOf(next).stripTrailingZeros().toPlainString().replace('.',','))
    }
    fun remove(key:Int){
        if(_state.value.busy)return
        val rows=_state.value.rows.filter{it.key!=key}
        if(rows.isEmpty())clear()else update{it.copy(rows=rows,error=null)}
    }
    fun choose(key:Int){
        if(_state.value.busy)return
        val row=_state.value.rows.find{it.key==key}?:return
        update{it.copy(choosing=key,query=row.source,error=null)};search(row.source,true)
    }
    fun query(value:String)=search(value,false)
    private fun search(value:String,force:Boolean){
        if(_state.value.busy||_state.value.choosing==null)return
        if(!force&&com.spisokryadom.app.util.FuzzySearch.normalize(value)==com.spisokryadom.app.util.FuzzySearch.normalize(_state.value.query)){
            if(value!=_state.value.query)update{it.copy(query=value.take(500))};return
        }
        update{it.copy(query=value.take(500),searching=true,candidates=emptyList())};searchJob?.cancel()
        val query=_state.value.query;val row=_state.value.choosing;val batchToken=token
        searchJob=viewModelScope.launch{try{
            val matches=withContext(Dispatchers.Default){delay(180);repository.candidates(query,city)}
            if(token==batchToken&&_state.value.choosing==row&&com.spisokryadom.app.util.FuzzySearch.normalize(_state.value.query)==com.spisokryadom.app.util.FuzzySearch.normalize(query))update{it.copy(candidates=matches,searching=false)}
        }catch(e:Exception){if(e is CancellationException)throw e;update{it.copy(searching=false,error=e.message)}}}
    }
    fun select(product:ProductEntity?){
        val s=_state.value;if(s.busy||s.searching||s.choosing==null||product==null&&s.query.isBlank())return
        if(product!=null&&s.candidates.none{it.id==product.id&&it.stableId==product.stableId})return
        searchJob?.cancel()
        update{it.copy(choosing=null,rows=it.rows.map{r->if(r.key==s.choosing)r.copy(name=product?.name?:s.query.trim(),productId=product?.id,stableId=product?.stableId,resolved=true)else r},
            matches=if(product==null)it.matches else it.matches+(s.choosing to (listOf(product)+it.matches[s.choosing].orEmpty()).distinctBy{p->p.id}),error=null)}
    }
    fun add(shopId:Long?,branchId:Long?){
        val s=_state.value;if(s.busy||!s.preview||s.choosing!=null)return
        update{it.copy(busy=true,error=null)}
        viewModelScope.launch{try{
            require(databaseRevision==revision()){"База восстановлена. Подготовьте список заново"}
            val choice=when(shopId){-1L->ShopChoice.AUTO;null->ShopChoice.NONE;else->ShopChoice.EXPLICIT}
            // A committed token also counts as completed after a process died before draft cleanup.
            withContext(Dispatchers.IO){repository.add(token,s.rows,city,choice,shopId,branchId,s.saveNew,fallbackUnit(),generation)}
            reset(s.queue.filter{it.id!=s.activeQueueId},completed=true)
        }catch(e:Exception){if(e is CancellationException)throw e;update{it.copy(busy=false,error=e.message)}}}
    }
    fun consumeCompletion(){update{it.copy(completed=false)}}
    companion object {
        val Factory=viewModelFactory{initializer{
            val app=this[APPLICATION_KEY] as SpisokRyadomApp
            val prefs=app.getSharedPreferences("shopping_list_prefs",0)
            BulkProductViewModel(BulkPurchaseRepository(app.container.database,app.container.shoppingListRepository,app.container.locations),createSavedStateHandle(),
                prefs.getBoolean(com.spisokryadom.app.ui.shoppinglist.ShoppingListViewModel.PREF_GLOBAL_SAVE_NEW_PRODUCT_TO_DB,true),{
                    com.spisokryadom.app.util.ProductCreationDefaults.read(prefs).let{if(it.fillMissing)it.missingUnit else ""}
                },revision={com.spisokryadom.app.util.DatabaseRevision.current(app)},store=app.getSharedPreferences("bulk_product_draft",0))
        }}
    }
}
