package com.spisokryadom.app.ui.settings

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spisokryadom.app.SpisokRyadomApp
import com.spisokryadom.app.data.backup.BackupManager
import com.spisokryadom.app.data.entity.ProductEntity
import com.spisokryadom.app.data.entity.ProductGroupEntity
import com.spisokryadom.app.data.entity.RecipientEntity
import com.spisokryadom.app.data.entity.ShopDepartmentEntity
import com.spisokryadom.app.data.entity.ShopEntity
import com.spisokryadom.app.data.entity.UnitEntity
import com.spisokryadom.app.data.repository.ClassifierRepository
import com.spisokryadom.app.data.repository.ProductRepository
import com.spisokryadom.app.data.repository.ShopRepository
import com.spisokryadom.app.ui.shoppinglist.ShoppingListViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ── Backup state ─────────────────────────────────────────────────────────────

data class SettingsUiState(
    val isLoading: Boolean = false,
    val message: String? = null,
    val pendingExportPath: String? = null,
    val exportLaunched:Boolean=false,
    val pendingImportPath:String?=null,
    val importSummary:String?=null,
    val resetCompleted:Boolean=false,
    val resetInProgress:Boolean=false
)

// ── Department group model (cross-shop) ──────────────────────────────────────

data class DepartmentGroup(
    val id:Long,
    val name: String,
    val shops: List<ShopEntity>,
    val accent:String="",val iconKey:String="",
    val isFavorite:Boolean=false,val favoriteOrder:Int=0,
    val totalDepartments: Int
)

// ── Urgency defaults state ───────────────────────────────────────────────────

data class UrgencyDefaultsState(
    val showUrgentInCollapsed: Boolean = true,
    val boostUrgentShop: Boolean = true
)

// ── Add-to-list defaults state ──────────────────────────────────────────────

data class AddToListDefaultsState(
    val saveNewProductToDatabase: Boolean = true,
    val rememberShopPriorityOnMove:Boolean=false,
    val browseProductsOnAdd:Boolean=true,
    val autoSaveProductCards:Boolean=true,
    val rememberVariantChoice:Boolean=false,
    val defaultPurchaseType:String="offline",val missingUnit:String="шт",val missingQuantity:Double=1.0,val fillMissing:Boolean=false
)

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(
    private val backupManager: BackupManager,
    private val classifierRepository: ClassifierRepository,
    private val productRepository: ProductRepository,
    private val shopRepository: ShopRepository,
    private val prefs: SharedPreferences,
    private val context:Context,
    private val resetRepository:com.spisokryadom.app.data.repository.ResetRepository?=null
) : ViewModel() {

    // ── Backup ───────────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private val errors=kotlinx.coroutines.CoroutineExceptionHandler{_,e->_uiState.value=_uiState.value.copy(isLoading=false,message=e.message?:"Операция не выполнена")}
    private val sessionGeneration=com.spisokryadom.app.data.repository.ProductEditorTransactions.generation.get()
    private fun editCatalogue(block:suspend()->Unit){viewModelScope.launch(Dispatchers.IO+errors){
        com.spisokryadom.app.data.repository.ProductEditorTransactions.inGeneration(sessionGeneration,block)
    }}

    fun prepareExport() {
        if(_uiState.value.isLoading||_uiState.value.pendingExportPath!=null)return
        _uiState.value=_uiState.value.copy(isLoading=true,message=null)
        viewModelScope.launch(Dispatchers.IO+errors){try{
            val file=backupManager.exportFile()
            _uiState.value=_uiState.value.copy(isLoading=false,pendingExportPath=file.absolutePath,exportLaunched=false)
        }catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;_uiState.value=_uiState.value.copy(isLoading=false,message="Ошибка резервной копии: ${e.message}")}}
    }
    fun exportLaunched(){_uiState.value=_uiState.value.copy(exportLaunched=true)}
    fun saveExport(uri:Uri?){
        val file=_uiState.value.pendingExportPath?.let{File(it)}?:return
        _uiState.value=_uiState.value.copy(pendingExportPath=null,isLoading=uri!=null)
        viewModelScope.launch(Dispatchers.IO+errors){try{
            if(uri!=null){val output=context.contentResolver.openOutputStream(uri)?:error("Не удалось открыть файл для записи");output.use{out->file.inputStream().use{it.copyTo(out)}}}
            _uiState.value=_uiState.value.copy(isLoading=false,message=if(uri!=null){val missing=backupManager.inspect(file).missingPhotos;"Полная резервная копия сохранена"+(if(missing>0)". Не найдены фото: $missing"else "")}else null)
        }catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;_uiState.value=_uiState.value.copy(isLoading=false,message="Не удалось записать копию: ${e.message}")}
        finally{backupManager.discard(file)}}
    }
    fun receiveImport(uri:Uri){
        if(_uiState.value.isLoading)return
        _uiState.value=_uiState.value.copy(isLoading=true,message=null)
        viewModelScope.launch(Dispatchers.IO+errors){val file=File(File(context.cacheDir,"backups").also{it.mkdirs()},"input_${UUID.randomUUID()}.data")
            try{
                val input=context.contentResolver.openInputStream(uri)?:error("Не удалось прочитать файл")
                input.use{source->file.outputStream().use{out->val buffer=ByteArray(16384);var total=0L;while(true){val n=source.read(buffer);if(n<0)break;total+=n;require(total<=512L*1024*1024){"Копия слишком большая"};out.write(buffer,0,n)}}}
                val info=backupManager.inspect(file)
                _uiState.value=_uiState.value.copy(isLoading=false,pendingImportPath=file.absolutePath,importSummary=info.description)
            }catch(e:Exception){file.delete();if(e is kotlinx.coroutines.CancellationException)throw e;_uiState.value=_uiState.value.copy(isLoading=false,message="Не удалось прочитать копию: ${e.message}")}
        }
    }
    fun cancelImport(){_uiState.value.pendingImportPath?.let{backupManager.discard(File(it))};_uiState.value=_uiState.value.copy(pendingImportPath=null,importSummary=null)}
    fun importSelected(){
        val path=_uiState.value.pendingImportPath?:return
        if(_uiState.value.isLoading)return
        _uiState.value=_uiState.value.copy(pendingImportPath=null,importSummary=null,isLoading=true)
        viewModelScope.launch(Dispatchers.IO+errors){try{
            val info=backupManager.importFile(File(path))
            _uiState.value=_uiState.value.copy(isLoading=false,resetCompleted=info.full,message=if(info.full)"Полная копия восстановлена"else "Каталог перенесён. Текущие покупки сохранены")
        }catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;_uiState.value=_uiState.value.copy(isLoading=false,message="Восстановление не выполнено: ${e.message}")}
        finally{backupManager.discard(File(path))}}
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    // ── Urgency global defaults ──────────────────────────────────────────────

    private val _urgencyDefaults = MutableStateFlow(loadUrgencyDefaults())
    val urgencyDefaults: StateFlow<UrgencyDefaultsState> = _urgencyDefaults.asStateFlow()

    private fun loadUrgencyDefaults(): UrgencyDefaultsState {
        return UrgencyDefaultsState(
            showUrgentInCollapsed = prefs.getBoolean(
                ShoppingListViewModel.PREF_GLOBAL_SHOW_URGENT_IN_COLLAPSED, true
            ),
            boostUrgentShop = prefs.getBoolean(
                ShoppingListViewModel.PREF_GLOBAL_BOOST_URGENT_SHOP, true
            )
        )
    }

    fun setGlobalShowUrgentInCollapsed(value: Boolean) {
        prefs.edit().putBoolean(
            ShoppingListViewModel.PREF_GLOBAL_SHOW_URGENT_IN_COLLAPSED, value
        ).apply()
        _urgencyDefaults.value = _urgencyDefaults.value.copy(showUrgentInCollapsed = value)
    }

    fun setGlobalBoostUrgentShop(value: Boolean) {
        prefs.edit().putBoolean(
            ShoppingListViewModel.PREF_GLOBAL_BOOST_URGENT_SHOP, value
        ).apply()
        _urgencyDefaults.value = _urgencyDefaults.value.copy(boostUrgentShop = value)
    }

    // ── Add-to-list global defaults ─────────────────────────────────────────

    private val _addToListDefaults = MutableStateFlow(loadAddToListDefaults())
    val addToListDefaults: StateFlow<AddToListDefaultsState> = _addToListDefaults.asStateFlow()

    private fun loadAddToListDefaults(): AddToListDefaultsState {
        return AddToListDefaultsState(
            saveNewProductToDatabase = prefs.getBoolean(
                ShoppingListViewModel.PREF_GLOBAL_SAVE_NEW_PRODUCT_TO_DB, true
            ),
            rememberShopPriorityOnMove=prefs.getBoolean("remember_shop_priority_on_move",false),
            browseProductsOnAdd=prefs.getBoolean("browse_products_on_add",true),
            autoSaveProductCards=com.spisokryadom.app.util.ProductEditingPreferences.enabled(prefs),
            rememberVariantChoice=prefs.getBoolean("remember_variant_choice",false),
            defaultPurchaseType=com.spisokryadom.app.util.ProductCreationDefaults.read(prefs).purchaseType,
            missingUnit=com.spisokryadom.app.util.ProductCreationDefaults.read(prefs).missingUnit,
            missingQuantity=com.spisokryadom.app.util.ProductCreationDefaults.read(prefs).missingQuantity,
            fillMissing=com.spisokryadom.app.util.ProductCreationDefaults.read(prefs).fillMissing
        )
    }

    val groupCreationPresets=classifierRepository.creationPresets().stateIn(viewModelScope,kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),emptyList())
    fun setGroupDefaultRecipient(groupId:Long,recipientId:Long?,done:(Boolean)->Unit={}){viewModelScope.launch{
        try{com.spisokryadom.app.data.repository.ProductEditorTransactions.inGeneration(sessionGeneration){classifierRepository.defaultRecipient(groupId,recipientId)};done(true)}catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;_uiState.value=_uiState.value.copy(message=e.message?:"Преднастройка не сохранена");done(false)}
    }}
    fun setRememberVariantChoice(value:Boolean){prefs.edit().putBoolean("remember_variant_choice",value).apply()}
    fun setDefaultPurchaseType(value:String){if(value in setOf("offline","online"))prefs.edit().putString(com.spisokryadom.app.util.ProductCreationDefaults.TYPE,value).apply()}
    fun setMissingQuantity(value:String){com.spisokryadom.app.util.Quantities.parse(value)?.let{prefs.edit().putString(com.spisokryadom.app.util.ProductCreationDefaults.QUANTITY,value.trim()).apply()}}
    fun setFillMissing(value:Boolean){prefs.edit().putBoolean(com.spisokryadom.app.util.ProductCreationDefaults.ENABLED,value).apply()}
    fun reset(mode:com.spisokryadom.app.data.repository.ResetMode){
        if(_uiState.value.isLoading)return
        _uiState.value=_uiState.value.copy(isLoading=true,resetInProgress=true)
        viewModelScope.launch(Dispatchers.IO){try{
            requireNotNull(resetRepository){"Сброс недоступен"}.reset(mode)
            _selectedRecipientId.value=null;_selectedGroupId.value=null;_selectedUnitId.value=null;_selectedDepartmentGroup.value=null
            _uiState.value=_uiState.value.copy(isLoading=false,resetInProgress=false,resetCompleted=mode!=com.spisokryadom.app.data.repository.ResetMode.BUILT_INS,message="Сброс выполнен")
        }catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;_uiState.value=_uiState.value.copy(isLoading=false,resetInProgress=false,message=e.message?:"Не удалось выполнить сброс")}}
    }
    fun favoriteGroup(id:Long,value:Boolean){editCatalogue{classifierRepository.favoriteGroup(id,value)}}
    fun favoriteDepartment(id:Long,value:Boolean){editCatalogue{shopRepository.favoriteDepartmentType(id,value)}}
    fun setMissingUnit(value:String){prefs.edit().putString(com.spisokryadom.app.util.ProductCreationDefaults.UNIT,com.spisokryadom.app.util.Quantities.unit(value)).apply()}
    fun setAutoSaveProductCards(value:Boolean){
        prefs.edit().putBoolean(com.spisokryadom.app.util.ProductEditingPreferences.KEY,value).commit()
        if(!value)viewModelScope.launch{com.spisokryadom.app.data.repository.ProductEditorTransactions.locked{
            com.spisokryadom.app.ui.productcard.ProductDraftStore.preferences(context).edit().clear().commit()
        }}
    }
    fun setBrowseProductsOnAdd(value:Boolean){prefs.edit().putBoolean("browse_products_on_add",value).apply()}

    fun setRememberShopPriorityOnMove(value:Boolean){prefs.edit().putBoolean("remember_shop_priority_on_move",value).apply()}

    private val _widgetSettings=MutableStateFlow(com.spisokryadom.app.widget.WidgetSettings.load(prefs))
    val widgetSettings:StateFlow<com.spisokryadom.app.widget.WidgetSettings> = _widgetSettings.asStateFlow()
    fun updateWidgetSettings(value:com.spisokryadom.app.widget.WidgetSettings){
        value.save(prefs)
        _widgetSettings.value=com.spisokryadom.app.widget.WidgetSettings.load(prefs)
    }

    fun setGlobalSaveNewProductToDatabase(value: Boolean) {
        prefs.edit().putBoolean(
            ShoppingListViewModel.PREF_GLOBAL_SAVE_NEW_PRODUCT_TO_DB, value
        ).apply()
        _addToListDefaults.value = _addToListDefaults.value.copy(saveNewProductToDatabase = value)
    }

    // ── Edit mode per section ────────────────────────────────────────────────

    private val _recipientsEditMode = MutableStateFlow(false)
    val recipientsEditMode: StateFlow<Boolean> = _recipientsEditMode.asStateFlow()
    fun toggleRecipientsEditMode() { _recipientsEditMode.value = !_recipientsEditMode.value }

    private val _groupsEditMode = MutableStateFlow(false)
    val groupsEditMode: StateFlow<Boolean> = _groupsEditMode.asStateFlow()
    fun toggleGroupsEditMode() { _groupsEditMode.value = !_groupsEditMode.value }

    private val _unitsEditMode = MutableStateFlow(false)
    val unitsEditMode: StateFlow<Boolean> = _unitsEditMode.asStateFlow()
    fun toggleUnitsEditMode() { _unitsEditMode.value = !_unitsEditMode.value }

    private val _deptGroupsEditMode = MutableStateFlow(false)
    val deptGroupsEditMode: StateFlow<Boolean> = _deptGroupsEditMode.asStateFlow()
    fun toggleDeptGroupsEditMode() { _deptGroupsEditMode.value = !_deptGroupsEditMode.value }

    private val preferenceListener=SharedPreferences.OnSharedPreferenceChangeListener{_,_->
        _urgencyDefaults.value=loadUrgencyDefaults();_addToListDefaults.value=loadAddToListDefaults()
        _widgetSettings.value=com.spisokryadom.app.widget.WidgetSettings.load(prefs)
    }
    init { prefs.registerOnSharedPreferenceChangeListener(preferenceListener) }
    override fun onCleared(){prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener);super.onCleared()}

    // ── Recipients ───────────────────────────────────────────────────────────

    val recipients: StateFlow<List<RecipientEntity>> = classifierRepository.getAllRecipients()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedRecipientId = MutableStateFlow<Long?>(null)
    val selectedRecipientId: StateFlow<Long?> = _selectedRecipientId.asStateFlow()

    val selectedRecipientProducts: StateFlow<List<ProductEntity>> =
        _selectedRecipientId.flatMapLatest { id ->
            if (id != null) productRepository.getProductsByRecipientId(id)
            else flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectRecipient(id: Long?) {
        _selectedRecipientId.value = id
    }

    fun createRecipient(name: String) {
        editCatalogue {
            val maxOrder = classifierRepository.getMaxRecipientDisplayOrder()
            classifierRepository.insertRecipient(RecipientEntity(name = name, displayOrder = maxOrder + 1))
        }
    }

    fun renameRecipient(recipient: RecipientEntity, newName: String) {
        editCatalogue {
            classifierRepository.updateRecipient(recipient.copy(name = newName))
        }
    }

    fun deleteRecipient(recipient: RecipientEntity) {
        editCatalogue {
            classifierRepository.deleteRecipient(recipient)
            if (_selectedRecipientId.value == recipient.id) {
                _selectedRecipientId.value = null
            }
        }
    }

    fun moveRecipientUp(value:RecipientEntity) { editCatalogue{classifierRepository.moveRecipient(value.id,-1)} }

    fun moveRecipientDown(value:RecipientEntity) { editCatalogue{classifierRepository.moveRecipient(value.id,1)} }

    // ── Product Groups ──────────────────────────────────────────────────────

    val productGroups: StateFlow<List<ProductGroupEntity>> = classifierRepository.getAllGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedGroupId = MutableStateFlow<Long?>(null)
    val selectedGroupId: StateFlow<Long?> = _selectedGroupId.asStateFlow()

    val selectedGroupProducts: StateFlow<List<ProductEntity>> =
        _selectedGroupId.flatMapLatest { id ->
            if (id != null) productRepository.getProductsByGroupId(id)
            else flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectProductGroup(id: Long?) {
        _selectedGroupId.value = id
    }

    fun createProductGroup(name: String) {
        editCatalogue {
            val maxOrder = classifierRepository.getMaxGroupDisplayOrder()
            classifierRepository.insertGroup(ProductGroupEntity(name = name, displayOrder = maxOrder + 1))
        }
    }

    fun renameProductGroup(group: ProductGroupEntity, newName: String) {
        editCatalogue {
            classifierRepository.renameGroup(group.id,newName)
        }
    }

    fun deleteProductGroup(group: ProductGroupEntity) {
        editCatalogue {
            classifierRepository.deleteGroup(group)
            if (_selectedGroupId.value == group.id) {
                _selectedGroupId.value = null
            }
        }
    }

    fun moveProductGroupUp(value:ProductGroupEntity) { editCatalogue{classifierRepository.moveGroup(value.id,-1)} }

    fun moveProductGroupDown(value:ProductGroupEntity) { editCatalogue{classifierRepository.moveGroup(value.id,1)} }

    // ── Units of measurement ─────────────────────────────────────────────────

    val units: StateFlow<List<UnitEntity>> = classifierRepository.getAllUnits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedUnitId = MutableStateFlow<Long?>(null)
    val selectedUnitId: StateFlow<Long?> = _selectedUnitId.asStateFlow()

    val selectedUnitProducts: StateFlow<List<ProductEntity>> =
        combine(_selectedUnitId, units) { id, unitList ->
            unitList.find { it.id == id }?.name
        }.flatMapLatest { unitName ->
            if (unitName != null) productRepository.getProductsByUnit(unitName)
            else flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectUnit(id: Long?) {
        _selectedUnitId.value = id
    }

    fun createUnit(name: String) {
        editCatalogue {
            val maxOrder = classifierRepository.getMaxUnitDisplayOrder()
            classifierRepository.insertUnit(UnitEntity(name = name, displayOrder = maxOrder + 1))
        }
    }

    fun renameUnit(unit: UnitEntity, newName: String) {
        editCatalogue {
            classifierRepository.renameUnitAndProducts(unit,newName)
            if(com.spisokryadom.app.util.ProductCreationDefaults.read(prefs).missingUnit==com.spisokryadom.app.util.Quantities.unit(unit.name))setMissingUnit(newName)
        }
    }

    fun deleteUnit(unit: UnitEntity) {
        editCatalogue {
            classifierRepository.deleteUnitAndProducts(unit)
            if(com.spisokryadom.app.util.ProductCreationDefaults.read(prefs).missingUnit==com.spisokryadom.app.util.Quantities.unit(unit.name))setMissingUnit("")
            if (_selectedUnitId.value == unit.id) {
                _selectedUnitId.value = null
            }
        }
    }

    fun moveUnitUp(value:UnitEntity) { editCatalogue{classifierRepository.moveUnit(value.id,-1)} }

    fun moveUnitDown(value:UnitEntity) { editCatalogue{classifierRepository.moveUnit(value.id,1)} }

    // ── Shop department groups (cross-shop view) ─────────────────────────────

    private val allDepartments: StateFlow<List<ShopDepartmentEntity>> =
        shopRepository.getAllDepartments()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allShops: StateFlow<List<ShopEntity>> = shopRepository.getAllShops()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val departmentGroups: StateFlow<List<DepartmentGroup>> =
        combine(allDepartments, allShops,shopRepository.departmentTypes()) { departments, shops,types ->
            val shopMap = shops.associateBy { it.id }
            departments
                .groupBy { it.typeId }.let{byType->types.map{type->
                    val deps=byType[type.id].orEmpty()
                    val shopIds = deps.map { it.shopId }.distinct()
                    DepartmentGroup(
                        id=type.id,name = type.name,accent=type.accent,iconKey=type.iconKey,isFavorite=type.isFavorite,favoriteOrder=type.favoriteOrder,
                        shops = shopIds.mapNotNull { shopMap[it] },
                        totalDepartments = deps.size
                    )
                }}

        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedDepartmentGroup = MutableStateFlow<Long?>(null)
    val selectedDepartmentGroup: StateFlow<Long?> = _selectedDepartmentGroup.asStateFlow()

    data class DepartmentUsage(val shop:ShopEntity,val products:List<ProductEntity>)
    val selectedDepartmentProducts=combine(_selectedDepartmentGroup,productRepository.getAllProductsIncludingTemporary(),combine(productRepository.getAllShopLinks(),productRepository.observeBranchDepartments()){links,overrides->links to overrides},allDepartments,shopRepository.getShopsForDisplay()){type,products,relations,departments,shops->
        val (links,overrides)=relations
        val ids=departments.filter{it.typeId==type}.map{it.id}.toSet()
        shops.filter{s->departments.any{it.shopId==s.id&&it.typeId==type}}.map{s->
            val productIds=(links.filter{it.shopId==s.id&&it.departmentId in ids}.map{it.productId}+overrides.filter{it.shopId==s.id&&it.typeId==type}.map{it.productId}).toSet()
            DepartmentUsage(s,com.spisokryadom.app.util.alphabetical(products.filter{it.id in productIds}){it.name})
        }.let{com.spisokryadom.app.util.alphabetical(it){v->v.shop.name}}
    }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())

    fun selectDepartmentGroup(name: Long?) {
        _selectedDepartmentGroup.value = name
    }

    fun renameDepartmentGroup(id:Long,newName:String){editCatalogue{shopRepository.renameDepartmentGlobally(id,newName)}}
    fun deleteDepartmentGroup(id:Long){editCatalogue{shopRepository.deleteDepartmentGlobally(id);if(_selectedDepartmentGroup.value==id)_selectedDepartmentGroup.value=null}}

    fun moveDepartment(id:Long,delta:Int){editCatalogue{shopRepository.moveDepartmentType(id,delta)}}
    fun alphabetize(kind:String){editCatalogue{classifierRepository.alphabetize(kind)}}
    fun style(kind:String,id:Long,accent:String,icon:String){editCatalogue{if(kind=="dept")shopRepository.styleDepartmentType(id,accent,icon)else classifierRepository.style(kind,id,accent,icon)}}
    fun createDepartmentGroup(name: String) {
        editCatalogue {
            shopRepository.createDepartmentType(name)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SpisokRyadomApp
                val prefs = app.getSharedPreferences(
                    ShoppingListViewModel.PREFS_NAME_PUBLIC,
                    Context.MODE_PRIVATE
                )
                SettingsViewModel(
                    backupManager = app.container.backupManager,
                    classifierRepository = app.container.classifierRepository,
                    productRepository = app.container.productRepository,
                    shopRepository = app.container.shopRepository,
                    prefs = prefs,
                    context=app,resetRepository=app.container.resetRepository
                )
            }
        }
    }
}

