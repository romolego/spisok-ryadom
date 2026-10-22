package com.spisokryadom.app.ui.productcard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spisokryadom.app.SpisokRyadomApp
import com.spisokryadom.app.data.entity.ProductEntity
import com.spisokryadom.app.data.entity.ProductGroupEntity
import com.spisokryadom.app.data.entity.ProductShopLinkEntity
import com.spisokryadom.app.data.entity.RecipientEntity
import com.spisokryadom.app.data.entity.ShopDepartmentEntity
import com.spisokryadom.app.data.entity.ShopEntity
import com.spisokryadom.app.data.repository.ClassifierRepository
import com.spisokryadom.app.data.repository.ProductRepository
import com.spisokryadom.app.data.repository.ShopRepository
import com.spisokryadom.app.util.FuzzySearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ShopLinkUi(
    val link: ProductShopLinkEntity,
    val shopName: String,
    val departmentName: String?
)

data class PendingShopLink(
    val tempId: Long,
    val shopId: Long,
    val shopName: String,
    val departmentId: Long?,
    val departmentName: String?,
    val displayOrder: Int
)

data class ProductCardUiState(
    val isNew: Boolean = true,
    val recipientManuallySet:Boolean=false,val purchaseTypeManuallySet:Boolean=false,
    val allowedPurchaseTypes:Set<String> = setOf("offline","online"),
    val savedProductId:Long?=null,
    val autoSaveIssue:String?=null,
    val matchingProducts:List<ProductEntity> = emptyList(),
    val duplicateName:Boolean=false,
    val productContexts:Map<Long,String> = emptyMap(),
    val isPhotoBusy:Boolean=false,
    val departmentsShopId:Long?=null,
    val pendingDepartments:List<ShopDepartmentEntity> = emptyList(),
    val isCreatingDepartment:Boolean=false,
    val usedExistingId:Long?=null,
    val isFavorite:Boolean=false,val accent:String="",
    val departmentTypes:List<com.spisokryadom.app.data.entity.DepartmentTypeEntity> = emptyList(),
    val name: String = "",
    val productGroupId: Long? = null,
    val recipientId: Long? = null,
    val defaultUnit: String = "",
    val defaultQuantity: String = "",
    val note: String = "",
    val purchaseType: String = "offline",
    val sellerUrl: String = "",
    val productUrl: String = "",
    val photoUri: String = "",
    val priceValue: String = "",
    val priceDate: String = "",
    val branches:List<com.spisokryadom.app.data.entity.ShopBranchEntity> = emptyList(),
    val preferCityShopOrder:Boolean=true,
    val branchDepartments:Map<Long,Long> = emptyMap(),
    val branchSelections:Map<Long,Set<Long>> = emptyMap(),
    val branchPriorities:Map<Long,List<Long>> = emptyMap(),
    val shopLinks: List<ShopLinkUi> = emptyList(),
    val pendingShopLinks: List<PendingShopLink> = emptyList(),
    val groups: List<ProductGroupEntity> = emptyList(),
    val recipients: List<RecipientEntity> = emptyList(),
    val shops: List<ShopEntity> = emptyList(),
    val shopOrders:Map<Long,Int> = emptyMap(),
    val unitSuggestions: List<String> = emptyList(),
    val allDepartments:List<ShopDepartmentEntity> = emptyList(),
    val departmentsForSelectedShop: List<ShopDepartmentEntity> = emptyList(),
    val suggestedDepartmentId: Long? = null,
    val suggestedDepartmentReason: String? = null,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
    val isTemporary: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val isLoading: Boolean = true
)

@OptIn(kotlinx.coroutines.FlowPreview::class)
class ProductCardViewModel(
    private val productId: Long,
    private val preselectedShopId: Long = -1L,
    private val productRepository: ProductRepository,
    private val shopRepository: ShopRepository,
    private val classifierRepository: ClassifierRepository,
    private val draftPreferences:android.content.SharedPreferences?=null,
    private val editingPreferences:android.content.SharedPreferences?=null,
    initialName:String=""
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductCardUiState(name=if(productId<0)initialName.trim()else "",isNew = productId == -1L,recipientManuallySet=productId>0,purchaseTypeManuallySet=productId>0,isLoading=productId != -1L||preselectedShopId>0))
    val uiState: StateFlow<ProductCardUiState> = _uiState.asStateFlow()

    private val _shopMap = MutableStateFlow<Map<Long, ShopEntity>>(emptyMap())
    private val _departmentCache = mutableMapOf<Long, ShopDepartmentEntity?>()
    private var nextTempId: Long = 1L
    private var preselectedShopAdded = false
    private var productLoaded = productId == -1L
    private var linksLoaded = productId == -1L
    private val errors=kotlinx.coroutines.CoroutineExceptionHandler{_,e->_uiState.update { latest->latest.copy(error=e.message?:"Операция не выполнена",isLoading=false,isSaving=false) }}

    private fun autoSaveEnabled()=com.spisokryadom.app.util.ProductEditingPreferences.enabled(editingPreferences)
    private val draftKey=if(productId<0)"new_$preselectedShopId"+(if(initialName.isBlank())""else "_search_"+java.util.UUID.nameUUIDFromBytes(initialName.trim().toByteArray(Charsets.UTF_8)))else "product_$productId"
    private val restoredDraft=if(autoSaveEnabled())draftPreferences?.getString(draftKey,null)?.let{runCatching{ProductEditDraft.decode(it)}.getOrNull()}?.takeIf{it.autoPersist}else null
    private var editorStableId=restoredDraft?.stableId?:java.util.UUID.randomUUID().toString()
    private var duplicateAllowed=restoredDraft?.allowDuplicate?:false
    private var closed=false
    private val editorGeneration=com.spisokryadom.app.data.repository.ProductEditorTransactions.generation.get()
    private val editSignals=kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
    private var lastCommitted:String?=null
    private var draftBase:ProductEditDraft?=null
    private fun snapshot()=ProductEditDraft(editorStableId,productId,productId<0,duplicateAllowed,_uiState.value,autoSaveEnabled())
    private fun currentStoredDraft()=ProductDraftStore.find(draftPreferences,editorStableId)?.takeIf{autoSaveEnabled()||!it.autoPersist}
    private fun refreshExternalDraft(){
        if(closed||_uiState.value.isLoading||editorGeneration!=com.spisokryadom.app.data.repository.ProductEditorTransactions.generation.get())return
        val remote=currentStoredDraft()?:return
        if(remote.encode()==draftBase?.encode())return
        val merged=snapshot().rebaseExternal(draftBase,remote)
        draftBase=remote
        _uiState.update{merged.editableOn(it)}
    }
    private var shopCreationRules=emptyList<com.spisokryadom.app.data.entity.ShopCreationPreset>()
    private var groupCreationRules=emptyList<com.spisokryadom.app.data.entity.GroupCreationPreset>()
    private fun edit(change:(ProductCardUiState)->ProductCardUiState){refreshExternalDraft();_uiState.update(change);applyCreationDefaults(shopCreationRules,groupCreationRules)}
    // Every raw write shares the catalogue/editor lock; the listener itself only reads.
    private suspend fun keepDraftLocked():Boolean{
        if(closed||_uiState.value.isLoading||editorGeneration!=com.spisokryadom.app.data.repository.ProductEditorTransactions.generation.get())return false
        if(draftBase!=null&&currentStoredDraft()==null&&(productId>0||_uiState.value.savedProductId!=null)&&productRepository.getProductByStableId(editorStableId)==null){
            closed=true;_uiState.update{it.copy(isDeleted=true)};return false
        }
        refreshExternalDraft()
        val draft=snapshot();draftBase=draft
        ProductDraftStore.write(draftPreferences,draftKey,draft)
        return true
    }
    private val draftListener=android.content.SharedPreferences.OnSharedPreferenceChangeListener{_,_->
        if(!closed)viewModelScope.launch{com.spisokryadom.app.data.repository.ProductEditorTransactions.locked{refreshExternalDraft()}}
    }
    private suspend fun persistCurrent():Long {
        require(editorGeneration==com.spisokryadom.app.data.repository.ProductEditorTransactions.generation.get()){ "База была восстановлена. Откройте товар заново" }
        require(keepDraftLocked()){ "Товар удалён или заменён" }
        applyCreationDefaults(shopRepository.creationPresets().first(),classifierRepository.creationPresets().first())
        val draft=snapshot();val encoded=draft.encode()
        val id=if(lastCommitted==encoded)_uiState.value.savedProductId!! else draft.persist(productRepository,editingPreferences?.let{com.spisokryadom.app.util.ProductCreationDefaults.read(it)})
        lastCommitted=encoded
        _uiState.update { latest->latest.copy(savedProductId=id,autoSaveIssue=null,duplicateName=false) }
        return id
    }
    private fun startAutosave(){
        viewModelScope.launch(errors){
            _uiState.filter{!it.isLoading&&productLoaded&&linksLoaded}.first()
            com.spisokryadom.app.data.repository.ProductEditorTransactions.locked{
                val recovered=currentStoredDraft()
                if(autoSaveEnabled()&&recovered?.autoPersist==true&&(productId<0||recovered.stableId==editorStableId)){
                    var restored=recovered.applyTo(_uiState.value)
                    if(productId>0&&recovered.newSession)restored=restored.copy(isNew=false,shopLinks=restored.pendingShopLinks.map{ShopLinkUi(ProductShopLinkEntity(id=-it.tempId,productId=productId,shopId=it.shopId,priority=it.displayOrder,departmentId=it.departmentId),it.shopName,it.departmentName)},pendingShopLinks=emptyList())
                    _uiState.value=restored
                    preselectedShopAdded=true
                    nextTempId=(_uiState.value.pendingShopLinks.map{it.tempId}+_uiState.value.pendingDepartments.map{-it.id}+_uiState.value.shopLinks.map{-it.link.id}).maxOrNull()?.coerceAtLeast(0)?.plus(1)?:1
                    nameQuery.value=_uiState.value.name
                    draftBase=recovered
                }
                val existingEditorId=productRepository.getProductByStableId(editorStableId)?.id
                _uiState.update { latest->latest.copy(savedProductId=existingEditorId) }
                keepDraftLocked()
            }
            _uiState.map{snapshot().encode()}.distinctUntilChanged().collect{
                if(!closed)com.spisokryadom.app.data.repository.ProductEditorTransactions.locked{if(keepDraftLocked())editSignals.trySend(Unit)}
            }
        }
        viewModelScope.launch(errors){editSignals.receiveAsFlow().debounce(400).collect{
            com.spisokryadom.app.data.repository.ProductEditorTransactions.locked{
                if(!closed&&autoSaveEnabled()&&!_uiState.value.isLoading&&!_uiState.value.isPhotoBusy)try{
                    if(_uiState.value.name.isNotBlank())persistCurrent()else withdrawUnnamedNewProduct()
                }catch(e:Exception){
                    if(e is kotlinx.coroutines.CancellationException)throw e
                    _uiState.update { latest->latest.copy(duplicateName=e is ProductRepository.DuplicateName,autoSaveIssue=e.message) }
                }
            }
        }}
    }

    private fun applyCreationDefaults(modes:List<com.spisokryadom.app.data.entity.ShopCreationPreset>,groups:List<com.spisokryadom.app.data.entity.GroupCreationPreset>){
        _uiState.update{s->
            if(!s.isNew||s.isLoading)s else {
                val links=s.pendingShopLinks.sortedWith(if(s.preferCityShopOrder)compareBy<PendingShopLink>{s.shopOrders[it.shopId]?:Int.MAX_VALUE}.thenBy{it.displayOrder}else compareBy{it.displayOrder})
                val mode=modes.find{it.shopId==links.firstOrNull()?.shopId}?.purchaseMode?:"inherit"
                val allowed=com.spisokryadom.app.util.PurchaseModes.allowed(mode)
                val global=editingPreferences?.let{com.spisokryadom.app.util.ProductCreationDefaults.read(it).purchaseType}?:"offline"
                s.copy(allowedPurchaseTypes=allowed,purchaseType=if(allowed.size==1)allowed.single()else if(s.purchaseTypeManuallySet)s.purchaseType else global,
                    recipientId=if(s.recipientManuallySet)s.recipientId else groups.find{it.groupId==s.productGroupId}?.recipientId)
            }
        }
    }
    private val nameQuery=MutableStateFlow(if(productId<0)initialName.trim()else "")
    init {
        viewModelScope.launch(errors){combine(shopRepository.creationPresets(),classifierRepository.creationPresets(),_uiState.map{listOf(it.isNew,it.isLoading,it.productGroupId,it.pendingShopLinks,it.shopOrders,it.preferCityShopOrder,it.recipientManuallySet,it.purchaseTypeManuallySet)}.distinctUntilChanged()){a,b,_->a to b}.collect{(a,b)->shopCreationRules=a;groupCreationRules=b;applyCreationDefaults(a,b)}}
        viewModelScope.launch(errors){shopRepository.departmentTypes().collect{_uiState.update { latest->latest.copy(departmentTypes=it) }}}
        viewModelScope.launch(errors){
            combine(nameQuery,productRepository.getAllProducts()){query,products->query to products}.debounce(120).collectLatest{(query,products)->
                val matches=withContext(Dispatchers.Default){if(productId != -1L||query.isBlank())emptyList()else FuzzySearch.rank(products.filter{it.stableId!=editorStableId},query,{it.name}).take(8)}
                _uiState.update { latest->latest.copy(matchingProducts=matches) }
            }
        }
        viewModelScope.launch(errors){combine(productRepository.getAllProducts(),productRepository.getAllShopLinks(),shopRepository.getShopsForDisplay()){ps,ls,ss->com.spisokryadom.app.ui.components.productContexts(ps,ls,ss)}.flowOn(kotlinx.coroutines.Dispatchers.Default).collect{_uiState.update { latest->latest.copy(productContexts=it) }}}
        viewModelScope.launch(errors){shopRepository.getAllDepartments().collect{_uiState.update { latest->latest.copy(allDepartments=it) }}}
        viewModelScope.launch(errors){shopRepository.observeBranches().collect{_uiState.update { latest->latest.copy(branches=it) }}}
        viewModelScope.launch(errors) {
            classifierRepository.getAllGroups().collect { groups ->
                _uiState.update { latest->latest.copy(groups = groups) }
            }
        }
        viewModelScope.launch(errors) {
            classifierRepository.getAllRecipients().collect { recipients ->
                _uiState.update { latest->latest.copy(recipients = recipients) }
            }
        }
        viewModelScope.launch(errors) {
            shopRepository.getAllShops().combine(shopRepository.locations.activeCity){shops,city->shops to city}.collect { (shops,city) ->
                _shopMap.value = shops.associateBy { it.id }
                _uiState.update { latest->latest.copy(shops = shops.filter{it.cityId==city.id},shopOrders=shops.associate{it.id to it.displayOrder}) }

                // Auto-add preselected shop as first pending link for new products
                if (_uiState.value.isNew && preselectedShopId > 0 && !preselectedShopAdded) {
                    val shop = shops.find { it.id == preselectedShopId }
                    if (shop != null) {
                        preselectedShopAdded = true
                        _uiState.update { latest->latest.copy(
                            pendingShopLinks = listOf(
                                PendingShopLink(
                                    tempId = nextTempId++,
                                    shopId = shop.id,
                                    shopName = shop.name,
                                    departmentId = null,
                                    departmentName = null,
                                    displayOrder = 1
                                )
                            )
                        ) }
                    }
                    _uiState.update { latest->latest.copy(isLoading=false) }
                }
            }
        }
        viewModelScope.launch(errors) {
            classifierRepository.getOrderedUnitNames().collect { units ->
                _uiState.update { latest->latest.copy(unitSuggestions = units) }
            }
        }

        if (productId != -1L) {
            viewModelScope.launch(errors) {
                val product = productRepository.getProductById(productId) ?: error("Товар удалён")
                editorStableId=product.stableId
                productLoaded=true
                _uiState.update { latest->latest.copy(
                    isNew = false,
                    isTemporary = product.isTemporary,preferCityShopOrder=product.preferCityShopOrder,
                    name = product.name,isFavorite=product.isFavorite,accent=product.accent,
                    productGroupId = product.productGroupId,
                    recipientId = product.recipientId,
                    defaultUnit = product.defaultUnit ?: "",
                    defaultQuantity = product.defaultQuantity?.let { q ->
                        if (q == q.toLong().toDouble()) q.toLong().toString() else q.toString()
                    } ?: "",
                    note = product.note ?: "",
                    purchaseType = product.purchaseType,
                    sellerUrl = product.sellerUrl ?: "",
                    productUrl = product.productUrl ?: "",
                    photoUri = product.photoUri ?: "",
                    priceValue = product.priceValue?.let { v ->
                        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
                    } ?: "",
                    priceDate = product.priceDate ?: "",
                    isLoading = !linksLoaded
                ) }
            }
            viewModelScope.launch(errors) {
                productRepository.getShopLinksForProduct(productId).take(1).collect { links ->
                    val shops = shopRepository.getAllShopsSync().associateBy{it.id}
                    val linkUis = links.map { link ->
                        val deptName = link.departmentId?.let { deptId ->
                            if (!_departmentCache.containsKey(deptId)) {
                                _departmentCache[deptId] = shopRepository.getDepartmentById(deptId)
                            }
                            _departmentCache[deptId]?.name
                        }
                        ShopLinkUi(
                            link = link,
                            shopName = shops[link.shopId]?.name ?: "?",
                            departmentName = deptName
                        )
                    }
                    val availability=productRepository.branchAvailability(productId).map{it.branchId}.toSet()
                    val allPoints=shopRepository.getAllShopsSync().flatMap{shopRepository.branches(it.id)}
                    val selected=links.filter{it.restrictBranches}.associate{link->link.shopId to availability.filter{id->allPoints.any{it.id==id&&it.shopId==link.shopId}}.toSet()}
                    val localDepartments=shopRepository.getAllDepartments().first()
                    val byType=localDepartments.associateBy{it.shopId to it.typeId}
                    val overrides=productRepository.branchDepartments(productId).mapNotNull{d->byType[d.shopId to d.typeId]?.let{d.branchId to it.id}}.toMap()
                    val priorities=productRepository.branchPriorities(productId).groupBy{it.shopId}.mapValues{(_,v)->v.sortedBy{it.displayOrder}.map{it.branchId}}
                    _uiState.update { latest->latest.copy(branchSelections=selected,branchDepartments=overrides,branchPriorities=priorities) }
                    linksLoaded=true
                    _uiState.update { latest->latest.copy(shopLinks = linkUis,isLoading = !productLoaded) }
                }
            }
        } else if(preselectedShopId<=0) {
            _uiState.update { latest->latest.copy(isLoading = false) }
        }
        draftPreferences?.registerOnSharedPreferenceChangeListener(draftListener)
        startAutosave()
    }


    /** Apply one shop dialog as one raw edit: no autosave of half-updated availability/group. */
    fun configureShop(shopId:Long,departmentId:Long?,points:Set<Long>?,priority:List<Long>) {
        edit { s ->
            if(s.pendingShopLinks.none{it.shopId==shopId}&&s.shopLinks.none{it.link.shopId==shopId})return@edit s.copy(error="Магазин больше не привязан к товару")
            val branches=s.branches.filter{it.shopId==shopId}.map{it.id}.toSet()
            if(points!=null&&(points.isEmpty()||!branches.containsAll(points)))return@edit s.copy(error="Выберите хотя бы одну доступную точку магазина")
            val departments=(s.allDepartments+s.pendingDepartments+s.departmentsForSelectedShop).filter{it.shopId==shopId}
            if(departmentId!=null&&departments.none{it.id==departmentId})return@edit s.copy(error="Группа магазина больше недоступна")
            val name=departments.find{it.id==departmentId}?.name
            s.copy(
                pendingShopLinks=s.pendingShopLinks.map{if(it.shopId==shopId)it.copy(departmentId=departmentId,departmentName=name)else it},
                shopLinks=s.shopLinks.map{if(it.link.shopId==shopId)it.copy(link=it.link.copy(departmentId=departmentId),departmentName=name)else it},
                branchSelections=if(points==null)s.branchSelections-shopId else s.branchSelections+(shopId to points),
                branchPriorities=if(priority.isEmpty())s.branchPriorities-shopId else s.branchPriorities+(shopId to priority.distinct().filter{it in branches}),
                // A confirmed shop group applies everywhere. Untouched legacy data stays backup-compatible.
                branchDepartments=s.branchDepartments.filterKeys{id->s.branches.none{it.id==id&&it.shopId==shopId}}
            )
        }
    }

    fun useCityShopOrder(value:Boolean){
        refreshExternalDraft()
        val s=_uiState.value
        val pending=if(s.preferCityShopOrder&&!value)s.pendingShopLinks.sortedWith(compareBy<PendingShopLink>{s.shopOrders[it.shopId]?:Int.MAX_VALUE}.thenBy{it.displayOrder}).mapIndexed{i,v->v.copy(displayOrder=i+1)}else s.pendingShopLinks
        val links=if(s.preferCityShopOrder&&!value)s.shopLinks.sortedWith(compareBy<ShopLinkUi>{s.shopOrders[it.link.shopId]?:Int.MAX_VALUE}.thenBy{it.link.priority}).mapIndexed{i,v->v.copy(link=v.link.copy(priority=i+1))}else s.shopLinks
        edit { latest->latest.copy(preferCityShopOrder=value,pendingShopLinks=pending,shopLinks=links) }
    }

    fun favorite(value:Boolean){edit { latest->latest.copy(isFavorite=value) }}
    fun accent(value:String){edit { latest->latest.copy(accent=value) }}
    fun createShop(name:String,done:(Long)->Unit)=createShop(name,done,{})
    fun createShop(name:String,done:(Long)->Unit,onFinished:()->Unit){viewModelScope.launch(errors){
        try{com.spisokryadom.app.data.repository.ProductEditorTransactions.inGeneration(editorGeneration){
            require(keepDraftLocked()){"Товар удалён или заменён"}
            val id=shopRepository.createInCurrentCity(name);val shop=shopRepository.getShopById(id)?:error("Магазин удалён")
            _shopMap.value=_shopMap.value+(id to shop);edit { latest->latest.copy(shops=(_uiState.value.shops.filter{it.id!=id}+shop).sortedBy{it.displayOrder},shopOrders=_uiState.value.shopOrders+(id to shop.displayOrder)) };done(id)
        }}finally{onFinished()}
    }}
    fun createRecipient(name:String,done:()->Unit)=createRecipient(name,done,{})
    fun createRecipient(name:String,done:()->Unit,onFinished:()->Unit){viewModelScope.launch(errors){
        try{com.spisokryadom.app.data.repository.ProductEditorTransactions.inGeneration(editorGeneration){
            require(keepDraftLocked()){"Товар удалён или заменён"}
            val id=classifierRepository.insertRecipient(RecipientEntity(name=name.trim(),displayOrder=classifierRepository.getMaxRecipientDisplayOrder()+1));updateRecipient(id);done()
        }}finally{onFinished()}
    }}
    fun updateName(name: String) {
        edit { latest->latest.copy(name = name,duplicateName=false) }
        nameQuery.value=name
    }


    fun updateProductGroup(groupId: Long?) {
        edit { latest->latest.copy(productGroupId = groupId) }
    }

    fun updateRecipient(recipientId: Long?) {
        edit { latest->latest.copy(recipientId = recipientId,recipientManuallySet=true) }
    }

    fun updateDefaultUnit(unit: String) {
        edit { latest->latest.copy(defaultUnit = unit) }
    }

    fun updateDefaultQuantity(qty: String) {
        edit { latest->latest.copy(defaultQuantity = qty) }
    }

    fun updateNote(note: String) {
        edit { latest->latest.copy(note = note) }
    }

    fun updatePurchaseType(type: String) {
        edit { latest->latest.copy(purchaseType = type,purchaseTypeManuallySet=true) }
    }

    fun updateSellerUrl(url: String) {
        edit { latest->latest.copy(sellerUrl = url) }
    }

    fun updateProductUrl(url: String) {
        edit { latest->latest.copy(productUrl = url) }
    }

    fun photoBusy(value:Boolean){edit { latest->latest.copy(isPhotoBusy=value) }}
    fun updatePhotoUri(uri: String) {
        edit { latest->latest.copy(photoUri = uri) }
    }

    fun updatePriceValue(value: String) {
        edit { latest->latest.copy(priceValue = value) }
    }

    fun updatePriceDate(date: String) {
        edit { latest->latest.copy(priceDate = date) }
    }

    private var departmentJob:kotlinx.coroutines.Job?=null
    fun createShopDepartment(shopId:Long,name:String,onCreated:(Long)->Unit){
        refreshExternalDraft()
        val value=name.trim();if(value.isBlank())return
        val state=_uiState.value
        val local=(state.pendingDepartments+state.allDepartments).filter{it.shopId==shopId}
        val existing=local.find{FuzzySearch.normalize(it.name)==FuzzySearch.normalize(value)}
        if(existing!=null){onCreated(existing.id);return}
        val sharedType=state.departmentTypes.find{it.normalizedName==FuzzySearch.normalize(value)}
        val dept=ShopDepartmentEntity(id=-(nextTempId++),shopId=shopId,name=value,displayOrder=(local.maxOfOrNull{it.displayOrder}?:0)+1,typeId=sharedType?.id?:0L)
        edit { latest->latest.copy(pendingDepartments=state.pendingDepartments+dept,departmentsForSelectedShop=if(state.departmentsShopId==shopId)state.departmentsForSelectedShop+dept else state.departmentsForSelectedShop) }
        onCreated(dept.id)
    }
    fun loadDepartmentsForShop(shopId: Long) {
        departmentJob?.cancel()
        edit { latest->latest.copy(departmentsShopId=shopId,departmentsForSelectedShop=emptyList(),suggestedDepartmentId=null,suggestedDepartmentReason=null) }
        departmentJob=viewModelScope.launch(errors) {
            val depts = (_uiState.value.pendingDepartments.filter{it.shopId==shopId}+shopRepository.getDepartmentsByShopIdSync(shopId)).distinctBy{FuzzySearch.normalize(it.name)}

            // Compute smart suggestion based on departments already used in other links
            val usedDeptInfo = getUsedDepartmentInfo()
            var suggestedId: Long? = null
            var suggestedReason: String? = null

            if (usedDeptInfo.isNotEmpty() && depts.isNotEmpty()) {
                // Try exact match first (case-insensitive)
                for ((deptName, shopName) in usedDeptInfo) {
                    val match = depts.find { it.name.equals(deptName, ignoreCase = true) }
                    if (match != null) {
                        suggestedId = match.id
                        suggestedReason = "Совпадает с «$deptName» в $shopName"
                        break
                    }
                }
                // Try fuzzy match if no exact match found
                if (suggestedId == null) {
                    for ((deptName, shopName) in usedDeptInfo) {
                        val matches = FuzzySearch.filterAndSort(depts.map { it.name }, deptName, 0.3)
                        if (matches.isNotEmpty()) {
                            val matchedDept = depts.find { it.name == matches.first() }
                            if (matchedDept != null) {
                                suggestedId = matchedDept.id
                                suggestedReason = "Похоже на «$deptName» в $shopName"
                                break
                            }
                        }
                    }
                }
            }

            edit { latest->latest.copy(
                departmentsForSelectedShop = depts,
                suggestedDepartmentId = suggestedId,
                suggestedDepartmentReason = suggestedReason
            ) }
        }
    }

    /**
     * Returns list of (departmentName, shopName) pairs from all current links
     * for smart department suggestion computation.
     */
    private fun getUsedDepartmentInfo(): List<Pair<String, String>> {
        val state = _uiState.value
        val result = mutableListOf<Pair<String, String>>()

        state.shopLinks.forEach { linkUi ->
            linkUi.departmentName?.let { deptName ->
                result.add(deptName to linkUi.shopName)
            }
        }
        state.pendingShopLinks.forEach { pending ->
            pending.departmentName?.let { deptName ->
                result.add(deptName to pending.shopName)
            }
        }

        return result.distinctBy { it.first.lowercase() }
    }

    // --- Pending shop links (new product) ---

    fun addPendingShopLink(shopId: Long, departmentId: Long?) {
        refreshExternalDraft()
        val shops = _shopMap.value
        val shop = shops[shopId] ?: return
        val pending = _uiState.value.pendingShopLinks
        val maxOrder = pending.maxOfOrNull { it.displayOrder } ?: 0
        val deptName = departmentId?.let { dId ->
            _uiState.value.departmentsForSelectedShop.find { it.id == dId }?.name
        }

        edit { latest->latest.copy(
            pendingShopLinks = pending + PendingShopLink(
                tempId = nextTempId++,
                shopId = shopId,
                shopName = shop.name,
                departmentId = departmentId,
                departmentName = deptName,
                displayOrder = maxOrder + 1
            )
        ) }
    }

    fun removePendingShopLink(tempId: Long) {
        refreshExternalDraft()
        val pending = _uiState.value.pendingShopLinks.filter { it.tempId != tempId }
        // Renumber display orders to keep them sequential
        val renumbered = pending.sortedBy { it.displayOrder }.mapIndexed { index, link ->
            link.copy(displayOrder = index + 1)
        }
        edit { latest->latest.copy(pendingShopLinks = renumbered) }
    }

    fun movePendingShopLinkUp(tempId: Long) {
        useCityShopOrder(false)
        val sorted = _uiState.value.pendingShopLinks.sortedBy { it.displayOrder }
        val index = sorted.indexOfFirst { it.tempId == tempId }
        if (index <= 0) return
        val orderA = sorted[index].displayOrder
        val orderB = sorted[index - 1].displayOrder
        val updated = sorted.mapIndexed { i, item ->
            when (i) {
                index -> item.copy(displayOrder = orderB)
                index - 1 -> item.copy(displayOrder = orderA)
                else -> item
            }
        }
        edit { latest->latest.copy(pendingShopLinks = updated) }
    }

    fun movePendingShopLinkDown(tempId: Long) {
        useCityShopOrder(false)
        val sorted = _uiState.value.pendingShopLinks.sortedBy { it.displayOrder }
        val index = sorted.indexOfFirst { it.tempId == tempId }
        if (index < 0 || index >= sorted.size - 1) return
        val orderA = sorted[index].displayOrder
        val orderB = sorted[index + 1].displayOrder
        val updated = sorted.mapIndexed { i, item ->
            when (i) {
                index -> item.copy(displayOrder = orderB)
                index + 1 -> item.copy(displayOrder = orderA)
                else -> item
            }
        }
        edit { latest->latest.copy(pendingShopLinks = updated) }
    }

    // --- Existing product shop links ---

    fun addShopLink(shopId: Long, departmentId: Long?) {
        refreshExternalDraft()
        if(_uiState.value.shopLinks.any{it.link.shopId==shopId})return
        val name=_shopMap.value[shopId]?.name ?: return
        val dept=_uiState.value.departmentsForSelectedShop.find{it.id==departmentId}?.name
        val link=ProductShopLinkEntity(id=-(nextTempId++),productId=productId,shopId=shopId,priority=_uiState.value.shopLinks.size+1,departmentId=departmentId)
        edit { latest->latest.copy(shopLinks=_uiState.value.shopLinks+ShopLinkUi(link,name,dept)) }
    }
    fun removeShopLink(link: ProductShopLinkEntity) {
        edit { latest->latest.copy(shopLinks=_uiState.value.shopLinks.filter{it.link.id!=link.id}) }
    }

    fun moveShopLinkUp(link: ProductShopLinkEntity)=moveLink(link,-1)
    fun moveShopLinkDown(link: ProductShopLinkEntity)=moveLink(link,1)
    private fun moveLink(link: ProductShopLinkEntity, delta: Int) {
        useCityShopOrder(false)
        val links=_uiState.value.shopLinks.sortedBy{it.link.priority}.toMutableList()
        val i=links.indexOfFirst{it.link.id==link.id};val j=i+delta
        if(i<0||j !in links.indices)return
        val item=links.removeAt(i);links.add(j,item)
        edit { latest->latest.copy(shopLinks=links.mapIndexed{idx,ui->ui.copy(link=ui.link.copy(priority=idx+1))}) }
    }

    // --- General ---

    fun useExistingProduct(id:Long){
        val state=_uiState.value;if(state.isSaving||state.isPhotoBusy||!state.isNew)return
        edit { latest->latest.copy(isSaving=true,error=null) }
        viewModelScope.launch(errors){com.spisokryadom.app.data.repository.ProductEditorTransactions.inGeneration(editorGeneration){
            require(keepDraftLocked()){"Товар удалён или заменён"}
            val state=_uiState.value
            require(productRepository.getProductById(id)!=null){"Товар удалён"}
            productRepository.adoptEditorProduct(id,editorStableId,state.pendingShopLinks.sortedBy{it.displayOrder}.map{it.shopId}.distinct(),
                state.pendingShopLinks.map{ProductShopLinkEntity(productId=id,shopId=it.shopId,departmentId=it.departmentId)},state.branchSelections,state.pendingDepartments,state.branchDepartments)
            closed=true;ProductDraftStore.remove(draftPreferences,editorStableId)
            edit { latest->latest.copy(isSaving=false,usedExistingId=id) }
        }}
    }
    // A new editor owns its auto-created row, but must not delete it after a purchase uses it.
    private suspend fun withdrawUnnamedNewProduct()=com.spisokryadom.app.data.repository.ProductEditorTransactions.inGeneration(editorGeneration){
        if(productId>=0)return@inGeneration
        if(productRepository.getProductByStableId(editorStableId)!=null)productRepository.discardNewEditorProduct(editorStableId)
        draftBase=null;lastCommitted=null
        _uiState.update{it.copy(savedProductId=null,autoSaveIssue=null)}
        keepDraftLocked()
    }
    fun save(allowDuplicate:Boolean=false)=finishEditing(false,allowDuplicate)
    fun finishEditing(leaving:Boolean=true,allowDuplicate:Boolean=false){
        if(closed||_uiState.value.isSaving||_uiState.value.isLoading||_uiState.value.isPhotoBusy)return
        duplicateAllowed=duplicateAllowed||allowDuplicate
        edit { latest->latest.copy(isSaving=true,error=null) }
        viewModelScope.launch{com.spisokryadom.app.data.repository.ProductEditorTransactions.locked{
            try{
                if(editorGeneration!=com.spisokryadom.app.data.repository.ProductEditorTransactions.generation.get()){
                    if(!leaving)error("База была восстановлена. Откройте товар заново")
                    // Leaving a stale form must not delete/write the restored raw draft.
                    closed=true;_uiState.update{it.copy(isSaving=false,isSaved=true)}
                    return@locked
                }
                if(leaving&&!autoSaveEnabled()){
                    closed=true;ProductDraftStore.remove(draftPreferences,editorStableId)
                    _uiState.update{it.copy(isSaving=false,isSaved=true)}
                    return@locked
                }
                require(keepDraftLocked()){"Товар удалён или заменён"}
                if(_uiState.value.name.isNotBlank())persistCurrent()else {
                    if(!leaving||productId>=0)error("Введите название товара")
                    withdrawUnnamedNewProduct()
                }
                closed=true;ProductDraftStore.remove(draftPreferences,editorStableId)
                // Only unnamed new drafts keep their remaining raw fields for the next visit.
                if(_uiState.value.name.isBlank()){closed=false;keepDraftLocked();closed=true}
                edit { latest->latest.copy(isSaving=false,isSaved=true) }
            }catch(e:Exception){
                if(e is kotlinx.coroutines.CancellationException)throw e
                keepDraftLocked()
                if(leaving&&autoSaveEnabled()&&_uiState.value.name.isNotBlank()){closed=true;edit { latest->latest.copy(isSaving=false,isSaved=true) }}
                else edit { latest->latest.copy(isSaving=false,duplicateName=e is ProductRepository.DuplicateName,error=if(e is ProductRepository.DuplicateName)null else e.message) }
            }
        }}
    }
    fun keepInCatalog(){edit { latest->latest.copy(isTemporary=false) }}
    fun clearError(){edit { latest->latest.copy(error=null) }}

    fun createGroup(name: String) {
        viewModelScope.launch(errors) {
          com.spisokryadom.app.data.repository.ProductEditorTransactions.inGeneration(editorGeneration){
            require(keepDraftLocked()){"Товар удалён или заменён"}
            val id = classifierRepository.insertGroup(ProductGroupEntity(name = name.trim(),displayOrder=classifierRepository.getMaxGroupDisplayOrder()+1))
            edit { latest->latest.copy(productGroupId = id) }
          }
        }
    }

    fun deleteProduct() {
        if(_uiState.value.isSaving||_uiState.value.isPhotoBusy)return
        edit { latest->latest.copy(isSaving=true) }
        viewModelScope.launch(errors){com.spisokryadom.app.data.repository.ProductEditorTransactions.inGeneration(editorGeneration){
            require(keepDraftLocked()){"Товар удалён или заменён"}
            val product=productRepository.getProductByStableId(editorStableId)
            if(product!=null)productRepository.deleteProduct(product)
            closed=true;ProductDraftStore.remove(draftPreferences,editorStableId)
            edit { latest->latest.copy(isSaving=false,isDeleted=true) }
        }}
    }

    override fun onCleared(){
        draftPreferences?.unregisterOnSharedPreferenceChangeListener(draftListener)
        refreshExternalDraft()
        val draft=if(!closed&&!_uiState.value.isLoading)snapshot()else null
        val base=draftBase
        closed=true
        if(draft!=null)ActiveProductDraftWrites.complete{
            if(editorGeneration==com.spisokryadom.app.data.repository.ProductEditorTransactions.generation.get()){
                val remote=ProductDraftStore.find(draftPreferences,draft.stableId)
                val deleted=base!=null&&remote==null&&(productId>0||draft.state.savedProductId!=null)&&productRepository.getProductByStableId(draft.stableId)==null
                if(!autoSaveEnabled())ProductDraftStore.remove(draftPreferences,draft.stableId)
                else if(!deleted){
                    val merged=draft.rebaseExternal(base,remote)
                    if(productId<0&&merged.state.name.isBlank()){
                        // Direct navigation can clear the VM before the autosave debounce fires.
                        runCatching{productRepository.discardNewEditorProduct(merged.stableId)}
                    }
                    ProductDraftStore.write(draftPreferences,draftKey,merged)
                }
            }
        }
        super.onCleared()
    }

    companion object {
        fun factory(productId: Long, preselectedShopId: Long = -1L, initialName:String=""): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SpisokRyadomApp
                ProductCardViewModel(
                    productId = productId,
                    preselectedShopId = preselectedShopId,
                    productRepository = app.container.productRepository,
                    shopRepository = app.container.shopRepository,
                    classifierRepository = app.container.classifierRepository,
                    draftPreferences = ProductDraftStore.preferences(app),
                    editingPreferences=com.spisokryadom.app.util.ProductEditingPreferences.preferences(app),
                    initialName=initialName
                )
            }
        }
    }
}
