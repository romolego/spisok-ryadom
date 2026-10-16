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
    val importSummary:String?=null
)

// ── Department group model (cross-shop) ──────────────────────────────────────

data class DepartmentGroup(
    val name: String,
    val shops: List<ShopEntity>,
    val totalDepartments: Int
)

// ── Urgency defaults state ───────────────────────────────────────────────────

data class UrgencyDefaultsState(
    val showUrgentInCollapsed: Boolean = true,
    val boostUrgentShop: Boolean = true
)

// ── Add-to-list defaults state ──────────────────────────────────────────────

data class AddToListDefaultsState(
    val saveNewProductToDatabase: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(
    private val backupManager: BackupManager,
    private val classifierRepository: ClassifierRepository,
    private val productRepository: ProductRepository,
    private val shopRepository: ShopRepository,
    private val prefs: SharedPreferences,
    private val context:Context
) : ViewModel() {

    // ── Backup ───────────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private val errors=kotlinx.coroutines.CoroutineExceptionHandler{_,e->_uiState.value=_uiState.value.copy(isLoading=false,message=e.message?:"Операция не выполнена")}

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
            _uiState.value=_uiState.value.copy(isLoading=false,message=if(info.full)"Полная копия восстановлена"else "Каталог перенесён. Текущие покупки сохранены")
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
            )
        )
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
        viewModelScope.launch(Dispatchers.IO+errors) {
            val maxOrder = classifierRepository.getMaxRecipientDisplayOrder()
            classifierRepository.insertRecipient(RecipientEntity(name = name, displayOrder = maxOrder + 1))
        }
    }

    fun renameRecipient(recipient: RecipientEntity, newName: String) {
        viewModelScope.launch(Dispatchers.IO+errors) {
            classifierRepository.updateRecipient(recipient.copy(name = newName))
        }
    }

    fun deleteRecipient(recipient: RecipientEntity) {
        viewModelScope.launch(Dispatchers.IO+errors) {
            classifierRepository.deleteRecipient(recipient)
            if (_selectedRecipientId.value == recipient.id) {
                _selectedRecipientId.value = null
            }
        }
    }

    fun moveRecipientUp(value:RecipientEntity) { viewModelScope.launch(Dispatchers.IO+errors){classifierRepository.moveRecipient(value.id,-1)} }

    fun moveRecipientDown(value:RecipientEntity) { viewModelScope.launch(Dispatchers.IO+errors){classifierRepository.moveRecipient(value.id,1)} }

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
        viewModelScope.launch(Dispatchers.IO+errors) {
            val maxOrder = classifierRepository.getMaxGroupDisplayOrder()
            classifierRepository.insertGroup(ProductGroupEntity(name = name, displayOrder = maxOrder + 1))
        }
    }

    fun renameProductGroup(group: ProductGroupEntity, newName: String) {
        viewModelScope.launch(Dispatchers.IO+errors) {
            classifierRepository.updateGroup(group.copy(name = newName))
        }
    }

    fun deleteProductGroup(group: ProductGroupEntity) {
        viewModelScope.launch(Dispatchers.IO+errors) {
            classifierRepository.deleteGroup(group)
            if (_selectedGroupId.value == group.id) {
                _selectedGroupId.value = null
            }
        }
    }

    fun moveProductGroupUp(value:ProductGroupEntity) { viewModelScope.launch(Dispatchers.IO+errors){classifierRepository.moveGroup(value.id,-1)} }

    fun moveProductGroupDown(value:ProductGroupEntity) { viewModelScope.launch(Dispatchers.IO+errors){classifierRepository.moveGroup(value.id,1)} }

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
        viewModelScope.launch(Dispatchers.IO+errors) {
            val maxOrder = classifierRepository.getMaxUnitDisplayOrder()
            classifierRepository.insertUnit(UnitEntity(name = name, displayOrder = maxOrder + 1))
        }
    }

    fun renameUnit(unit: UnitEntity, newName: String) {
        viewModelScope.launch(Dispatchers.IO+errors) {
            classifierRepository.renameUnitAndProducts(unit,newName)
        }
    }

    fun deleteUnit(unit: UnitEntity) {
        viewModelScope.launch(Dispatchers.IO+errors) {
            classifierRepository.deleteUnitAndProducts(unit)
            if (_selectedUnitId.value == unit.id) {
                _selectedUnitId.value = null
            }
        }
    }

    fun moveUnitUp(value:UnitEntity) { viewModelScope.launch(Dispatchers.IO+errors){classifierRepository.moveUnit(value.id,-1)} }

    fun moveUnitDown(value:UnitEntity) { viewModelScope.launch(Dispatchers.IO+errors){classifierRepository.moveUnit(value.id,1)} }

    // ── Shop department groups (cross-shop view) ─────────────────────────────

    private val allDepartments: StateFlow<List<ShopDepartmentEntity>> =
        shopRepository.getAllDepartments()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allShops: StateFlow<List<ShopEntity>> = shopRepository.getAllShops()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val departmentGroups: StateFlow<List<DepartmentGroup>> =
        combine(allDepartments, allShops) { departments, shops ->
            val shopMap = shops.associateBy { it.id }
            departments
                .groupBy { it.name }
                .map { (name, deps) ->
                    val shopIds = deps.map { it.shopId }.distinct()
                    DepartmentGroup(
                        name = name,
                        shops = shopIds.mapNotNull { shopMap[it] },
                        totalDepartments = deps.size
                    )
                }
                .sortedBy { it.name.lowercase() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedDepartmentGroup = MutableStateFlow<String?>(null)
    val selectedDepartmentGroup: StateFlow<String?> = _selectedDepartmentGroup.asStateFlow()

    val selectedDepartmentGroupShops: StateFlow<List<ShopEntity>> =
        combine(_selectedDepartmentGroup, departmentGroups) { name, groups ->
            if (name != null) groups.find { it.name == name }?.shops ?: emptyList()
            else emptyList()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectDepartmentGroup(name: String?) {
        _selectedDepartmentGroup.value = name
    }

    fun renameDepartmentGroup(oldName: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO+errors) {
            shopRepository.renameDepartmentGlobally(oldName, newName)
            if (_selectedDepartmentGroup.value == oldName) {
                _selectedDepartmentGroup.value = newName
            }
        }
    }

    fun deleteDepartmentGroup(name: String) {
        viewModelScope.launch(Dispatchers.IO+errors) {
            shopRepository.deleteDepartmentGlobally(name)
            if (_selectedDepartmentGroup.value == name) {
                _selectedDepartmentGroup.value = null
            }
        }
    }

    fun createDepartmentGroup(name: String) {
        viewModelScope.launch(Dispatchers.IO+errors) {
            shopRepository.addDepartmentInCity(name,shopRepository.locations.currentId())
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
                    context=app
                )
            }
        }
    }
}
