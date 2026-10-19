// Historical text-editing harness: repository compatibility tests only. Not shipped.
package com.spisokryadom.app.ui.bulk

import android.content.SharedPreferences
import androidx.lifecycle.*
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spisokryadom.app.SpisokRyadomApp
import com.spisokryadom.app.data.entity.ShoppingListEntryEntity
import com.spisokryadom.app.data.entity.ShopBranchEntity
import com.spisokryadom.app.data.backup.BackupJson
import org.json.JSONArray
import com.spisokryadom.app.data.entity.ProductEntity
import com.spisokryadom.app.data.entity.ShopEntity
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class DraftLine(
    val index:Int, val original:String, val name:String, val quantity:String, val unit:String,
    val candidates:List<ProductEntity>, val productId:Long?, val resolved:Boolean,
    val permanent:Boolean=false, val included:Boolean=true, val shopPreview:String="",
    val requestedShop:String?=null, val shopId:Long? = -1L, val shopResolved:Boolean=true,
    val shopMatches:List<Long> = emptyList(), val branchId:Long? = -1L, val entryId:Long?=null, val entryResolved:Boolean=true, val entryCandidates:List<Long> = emptyList(),val entryManual:Boolean=false,val shopTouched:Boolean=false
)
data class BulkState(
    val text:String="", val rows:List<DraftLine> = emptyList(), val review:Boolean=false,
    val busy:Boolean=false, val error:String?=null, val added:Int=0,
    val selectedShopId:Long? = -1L, val globalShopTouched:Boolean=false, val parsing:Boolean=false, val isTextView:Boolean=false,val dirty:Boolean=false,val saved:Boolean=false,val canUndo:Boolean=false,val pendingCount:Int=0,val removedNames:List<String> = emptyList()
)

class BulkViewModel(
    private val products:ProductRepository, private val basket:ShoppingListRepository,
    val locations:LocationRepository, private val prefs:SharedPreferences, private val textView:Boolean=false,private val shopRepository:ShopRepository?=null
):ViewModel() {
    private val state=MutableStateFlow(BulkState(text=if(textView)""else prefs.getString("bulk_text","").orEmpty(),isTextView=textView,busy=textView))
    val uiState=state.asStateFlow()
    private var receipt:ShoppingListRepository.BatchReceipt?=null
    private var reviewCity=1L
    private var reviewJob:Job?=null
    private var previewJob:Job?=null

    private var baseEntries:List<ShoppingListEntryEntity> = emptyList()
    private var snapshot:ShoppingListRepository.TextSnapshot?=null
    val branchOptions:StateFlow<List<ShopBranchEntity>> = basket.observeBranches().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    init { if(textView)viewModelScope.launch {try{loadText(true)}catch(e:Exception){if(e is CancellationException)throw e;state.value=state.value.copy(busy=false,error=e.message)}} }
    private fun render(s:ShoppingListRepository.TextSnapshot)=renderShoppingText(s)
    private suspend fun loadText(restoreDraft:Boolean) {
        val current=basket.textSnapshot();snapshot=current
        var draft=if(restoreDraft)prefs.getString("text_view_draft",null)else null
        if(restoreDraft&&draft==null&&!prefs.getString("bulk_text",null).isNullOrBlank()) {
            draft=listOf(render(current),"Автоматически:\n"+prefs.getString("bulk_text","")).filter{it.isNotBlank()}.joinToString("\n\n")
            prefs.edit().putString("text_view_draft",draft).putString("text_view_base",JSONArray().also{a->current.entries.forEach{a.put(BackupJson.encode(it))}}.toString()).remove("bulk_text").apply()
        }
        baseEntries=if(draft!=null){val json=JSONArray(prefs.getString("text_view_base",null)?:error("Не удалось прочитать основу черновика. Обновите текст из корзины"));(0 until json.length()).map{BackupJson.entries(json.getJSONObject(it))}}else current.entries
        state.value=state.value.copy(text=draft?:render(current),rows=emptyList(),review=false,busy=false,parsing=false,dirty=draft!=null,added=0,pendingCount=baseEntries.size,removedNames=emptyList(),selectedShopId=-1L,globalShopTouched=false)
    }
    fun reloadFromBasket() { if(state.value.busy)return;state.value=state.value.copy(busy=true,error=null);viewModelScope.launch{try{prefs.edit().remove("text_view_draft").remove("text_view_base").apply();loadText(false);state.value=state.value.copy(saved=false,canUndo=false)}catch(e:Exception){if(e is CancellationException)throw e;state.value=state.value.copy(busy=false,error=e.message)}} }
    fun entryLabel(id:Long):String {
        val e=baseEntries.firstOrNull{it.id==id}?:return "Покупка удалена"
        val p=snapshot?.products?.find{it.id==e.productId}
        return "${p?.name} · ${e.quantity} ${e.unit}"+e.note?.let{" · $it"}.orEmpty()+e.comment?.let{" · $it"}.orEmpty()
    }
    fun chooseEntry(index:Int,id:Long?) { update(index){it.copy(entryId=id,entryResolved=true,entryManual=true)};updateRemoved() }
    private fun updateRemoved(){if(!textView)return;val retained=state.value.rows.filter{it.included}.mapNotNull{it.entryId}.toSet();state.value=state.value.copy(removedNames=baseEntries.filter{it.id !in retained}.map{entryLabel(it.id)})}
    private fun matchEntries() {
        if(!textView)return
        val source=snapshot?:return;val productNames=source.products.associate{it.id to FuzzySearch.normalize(it.name)}
        val used=state.value.rows.filter{it.entryManual}.mapNotNull{it.entryId}.toMutableSet()
        var rows=state.value.rows.map { line->if(line.entryManual)line else line.copy(entryId=null,entryCandidates=emptyList(),entryResolved=true) }
        fun candidates(line:DraftLine,quantity:Boolean,target:Boolean=true,unit:Boolean=true)=baseEntries.filter{old->old.id !in used && (line.productId?.let{it==old.productId}?: (productNames[old.productId]==FuzzySearch.normalize(line.name))) && (!unit||Quantities.unit(old.unit)==Quantities.unit(line.unit)) && (!quantity||Quantities.parse(line.quantity)==old.quantity) && (!target||line.shopId == -1L || old.assignedShopId==line.shopId) && (!target||line.branchId==null||line.branchId == -1L||old.assignedBranchId==line.branchId)}
        // Exact rows reserve their identities before edited quantities consume any candidates.
        for(pass in 0..3)rows=rows.map{line->if(line.entryManual||line.entryId!=null)line else {
            val options=candidates(line,pass==0,pass<2,pass<3)
            if(options.size==1){used+=options.single().id;line.copy(entryId=options.single().id,entryResolved=true)}else if(pass>0&&options.size>1)line.copy(entryResolved=false,entryCandidates=options.map{it.id})else line
        }}
        state.value=state.value.copy(rows=rows);updateRemoved()
    }
    fun text(value:String) {
        if(state.value.busy)return
        state.value=state.value.copy(text=value,error=null,dirty=true,saved=false)
        if(textView)prefs.edit().putString("text_view_draft",value).putString("text_view_base",JSONArray().also{a->baseEntries.forEach{a.put(BackupJson.encode(it))}}.toString()).apply()else prefs.edit().putString("bulk_text",value).apply()
    }
    fun shop(id:Long?) { state.value=state.value.copy(selectedShopId=id,globalShopTouched=true); refreshPreviews() }
    fun edit() { if(!state.value.busy)state.value=state.value.copy(review=false,error=null) }
    fun update(index:Int,transform:(DraftLine)->DraftLine) {
        state.value=state.value.copy(rows=state.value.rows.map{if(it.index==index)transform(it)else it},error=null)
        updateRemoved()
    }
    fun choose(index:Int,id:Long?) {
        update(index){line->val p=line.candidates.find{it.id==id};line.copy(productId=id,resolved=true,unit=line.unit.ifBlank{p?.defaultUnit.orEmpty()})}
        matchEntries();refreshPreviews()
    }
    fun chooseShop(index:Int,id:Long?) {
        update(index){it.copy(shopId=id,shopResolved=true,branchId=-1L,shopTouched=true)}
        refreshPreviews()
    }

    fun chooseBranch(index:Int,id:Long?){update(index){it.copy(branchId=id,shopTouched=true)};refreshPreviews()}
    fun review() {
        if(state.value.busy)return
        previewJob?.cancel()
        state.value=state.value.copy(busy=true,parsing=true,error=null)
        reviewJob=viewModelScope.launch {
            try {
                reviewCity=locations.currentId()
                val catalog=if(textView)products.getAllProductsIncludingTemporary().first()else products.getAllProducts().first()
                val cityShops=if(textView)basket.textSnapshot().shops.filter{it.cityId==reviewCity||baseEntries.any{e->e.assignedShopId==it.id}}else locations.shopsForCity(reviewCity)
                val placeSnapshot=basket.textSnapshot()
                val branches=placeSnapshot.branches
                val labels=com.spisokryadom.app.domain.PlaceLabels(branches,placeSnapshot.venues)
                data class Target(val shopId:Long,val branchId:Long?,val label:String)
                val targets=cityShops.flatMap{shop->
                    listOf(Target(shop.id,-1L,shop.name),Target(shop.id,-1L,labels.shop(shop.id,shop.name)))+
                    branches.filter{it.shopId==shop.id}.flatMap{listOf(Target(shop.id,it.id,shop.name+" · "+it.address),Target(shop.id,it.id,labels.place(shop.name,it).text))}
                }.distinctBy{Triple(it.shopId,it.branchId,FuzzySearch.normalize(it.label))}
                val raw=state.value.text
                val rows=withContext(Dispatchers.Default) {
                    val context=currentCoroutineContext()
                    val index=FuzzySearch.index(catalog){it.name}
                    val shopIndex=FuzzySearch.index(targets){it.label}
                    ShoppingTextParser.parse(raw,if(textView)2000 else 200,if(textView)500_000 else 50_000).mapIndexed{i,p->
                        context.ensureActive()
                        val matches=index.search(p.name,limit=5,checkCancelled={context.ensureActive()})
                        val exact=matches.filter{FuzzySearch.normalize(it.name)==FuzzySearch.normalize(p.name)}.singleOrNull()
                        val requested=p.shopName?.let(FuzzySearch::normalize)
                        val noShop=requested=="без магазина"
                        val auto=requested==null||requested=="автоматически"
                        val shopMatches=if(auto||noShop)emptyList()else shopIndex.search(p.shopName.orEmpty(),limit=5,checkCancelled={context.ensureActive()})
                        val exactTargets=shopMatches.filter{FuzzySearch.normalize(it.label)==requested}
                        val exactShop=exactTargets.filter{it.branchId!= -1L}.singleOrNull()?:exactTargets.singleOrNull()
                        DraftLine(
                            i,p.original,p.name,
                            if(!p.explicitQuantity&&exact?.defaultQuantity!=null)exact.defaultQuantity.toString()else p.quantity,
                            p.unit.ifBlank{exact?.defaultUnit.orEmpty()},matches,exact?.id,exact!=null||matches.isEmpty(),
                            requestedShop=p.shopName,shopId=if(noShop)null else exactShop?.shopId?:-1L,
                            shopResolved=auto||noShop||exactShop!=null,shopMatches=shopMatches.map{it.shopId}.distinct(),branchId=exactShop?.branchId?:-1L
                        )
                    }
                }
                require(textView||rows.isNotEmpty()) { "Введите покупки: по одной в строке" }
                state.value=state.value.copy(rows=rows,review=true,busy=false,parsing=false)
                matchEntries();refreshPreviews()
            } catch(e:Exception) {
                if(e is CancellationException)throw e
                state.value=state.value.copy(busy=false,parsing=false,error=e.message)
            } finally { if(reviewJob===coroutineContext.job)reviewJob=null }
        }
    }
    fun cancelReview() {
        if(!state.value.parsing)return
        reviewJob?.cancel();reviewJob=null
        state.value=state.value.copy(busy=false,parsing=false,error=null)
    }
    private fun effectiveShop(line:DraftLine,s:BulkState):Long? = if(line.shopId == -1L)s.selectedShopId else line.shopId
    private fun choice(id:Long?)=when(id){-1L->ShopChoice.AUTO;null->ShopChoice.NONE;else->ShopChoice.EXPLICIT}
    private fun shopLabel(shop:ShopEntity)=shop.name
    private fun refreshPreviews() {
        previewJob?.cancel()
        previewJob=viewModelScope.launch {
            try {
                val snapshot=state.value
                val assignment=basket.assignmentPreview(snapshot.rows.mapNotNull{it.productId}.toSet(),reviewCity)
                val labels=basket.textSnapshot().let{com.spisokryadom.app.domain.PlaceLabels(it.branches,it.venues)}
                val shops=assignment.shops.mapValues{(_,shop)->shop.copy(name=labels.shop(shop.id,shop.name))}
                val previews=snapshot.rows.associate{line->
                    val id=effectiveShop(line,snapshot)
                    val name=when {
                        !line.shopResolved->"Выберите магазин для «${line.requestedShop}»"
                        !line.resolved->"Сначала выберите товар"
                        choice(id)==ShopChoice.EXPLICIT->shops[id]?.let(::shopLabel)?:"Магазин недоступен"
                        choice(id)==ShopChoice.NONE||line.productId==null->"Без магазина"
                        else->assignment.automaticShops[line.productId]?.let{shops[it]}?.let(::shopLabel)?:"Без магазина"
                    }
                    line.index to name
                }
                state.update{current->current.copy(rows=current.rows.map{it.copy(shopPreview=previews[it.index].orEmpty())})}
            } catch(e:Exception) { if(e is CancellationException)throw e }
        }
    }
    fun add() {
        val s=state.value
        if(s.busy||s.added>0)return
        val selected=s.rows.filter{it.included}
        if((!textView&&selected.isEmpty())||selected.any{!it.entryResolved||!it.resolved||!it.shopResolved||Quantities.parse(it.quantity)==null||it.name.isBlank()}) {
            state.value=s.copy(error="Проверьте товары, магазины и положительные количества в выбранных строках")
            return
        }
        state.value=s.copy(busy=true,error=null)
        viewModelScope.launch {
            try {
                val requests=selected.map{line->
                    val shopId=effectiveShop(line,s)
                    ShoppingListRepository.PurchaseRequest(line.productId,line.name,Quantities.parse(line.quantity)!!,line.unit,line.permanent,choice(shopId),shopId,line.entryId,line.branchId,assignmentExplicit=line.shopTouched||(line.shopId == -1L&&s.globalShopTouched))
                }
                receipt=if(textView)basket.replacePendingText(baseEntries,requests)else basket.addBatch(requests,reviewCity)
                if(textView){prefs.edit().remove("text_view_draft").remove("text_view_base").apply();loadText(false);state.value=state.value.copy(saved=true,canUndo=receipt?.let{it.before.isNotEmpty()||it.after.isNotEmpty()}?:false,error=null)}
                else {prefs.edit().remove("bulk_text").apply();state.value=state.value.copy(busy=false,added=selected.size)}
            } catch(e:Exception) {
                if(e is CancellationException)throw e
                state.value=state.value.copy(busy=false,error=e.message)
            }
        }
    }
    fun createPoint(shopId:Long,name:String,productId:Long?,done:(Long)->Unit){viewModelScope.launch{try{done(requireNotNull(shopRepository).createPoint(shopId,name,productId))}catch(e:Exception){if(e is CancellationException)throw e;state.value=state.value.copy(error=e.message)}}}
    fun createShop(name:String,done:(Long)->Unit){viewModelScope.launch{try{done(requireNotNull(shopRepository).createInCurrentCity(name))}catch(e:Exception){if(e is CancellationException)throw e;state.value=state.value.copy(error=e.message)}}}
    fun undo() {
        val r=receipt?:return
        if(state.value.busy)return
        state.value=state.value.copy(busy=true)
        viewModelScope.launch {
            try {
                basket.undoBatch(r);receipt=null
                state.value=state.value.copy(added=0,busy=false)
                if(textView){prefs.edit().remove("text_view_draft").remove("text_view_base").apply();loadText(false);state.value=state.value.copy(saved=false,canUndo=false)}else prefs.edit().putString("bulk_text",state.value.text).apply()
            } catch(e:Exception) {
                if(e is CancellationException)throw e
                state.value=state.value.copy(busy=false,error=e.message)
            }
        }
    }
    companion object {
        val Factory=viewModelFactory{initializer{val app=this[APPLICATION_KEY] as SpisokRyadomApp;BulkViewModel(app.container.productRepository,app.container.shoppingListRepository,app.container.locations,app.getSharedPreferences("drafts",0),textView=true,shopRepository=app.container.shopRepository)}}
    }
}
