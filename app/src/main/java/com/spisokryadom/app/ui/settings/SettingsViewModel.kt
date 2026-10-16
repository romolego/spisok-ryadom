package com.spisokryadom.app.ui.settings

import android.content.Context
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
    val pendingExportJson: String? = null
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
    private val prefs: SharedPreferences
) : ViewModel() {

    // ── Backup ───────────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun prepareExport() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, message = null)
            try {
                val json = backupManager.exportToJson()
                _uiState.value = _uiState.value.copy(isLoading = false, pendingExportJson = json)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Ошибка экспорта: ${e.message}"
                )
            }
        }
    }

    fun exportConsumed(success: Boolean) {
        _uiState.value = _uiState.value.copy(
            pendingExportJson = null,
            message = if (success) "База экспортирована" else null
        )
    }

    fun importFromJson(json: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, message = null)
            try {
                backupManager.importFromJson(json)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "База успешно импортирована"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Ошибка импорта: ${e.message}"
                )
            }
        }
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
        viewModelScope.launch(Dispatchers.IO) {
            val maxOrder = classifierRepository.getMaxRecipientDisplayOrder()
            classifierRepository.insertRecipient(RecipientEntity(name = name, displayOrder = maxOrder + 1))
        }
    }

    fun renameRecipient(recipient: RecipientEntity, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            classifierRepository.updateRecipient(recipient.copy(name = newName))
        }
    }

    fun deleteRecipient(recipient: RecipientEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            classifierRepository.deleteRecipient(recipient)
            if (_selectedRecipientId.value == recipient.id) {
                _selectedRecipientId.value = null
            }
        }
    }

    fun moveRecipientUp(recipient: RecipientEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = recipients.value.toMutableList()
            val idx = list.indexOfFirst { it.id == recipient.id }
            if (idx > 0) {
                val item = list.removeAt(idx)
                list.add(idx - 1, item)
                list.forEachIndexed { i, r ->
                    if (r.displayOrder != i) {
                        classifierRepository.updateRecipient(r.copy(displayOrder = i))
                    }
                }
            }
        }
    }

    fun moveRecipientDown(recipient: RecipientEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = recipients.value.toMutableList()
            val idx = list.indexOfFirst { it.id == recipient.id }
            if (idx >= 0 && idx < list.size - 1) {
                val item = list.removeAt(idx)
                list.add(idx + 1, item)
                list.forEachIndexed { i, r ->
                    if (r.displayOrder != i) {
                        classifierRepository.updateRecipient(r.copy(displayOrder = i))
                    }
                }
            }
        }
    }

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
        viewModelScope.launch(Dispatchers.IO) {
            val maxOrder = classifierRepository.getMaxGroupDisplayOrder()
            classifierRepository.insertGroup(ProductGroupEntity(name = name, displayOrder = maxOrder + 1))
        }
    }

    fun renameProductGroup(group: ProductGroupEntity, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            classifierRepository.updateGroup(group.copy(name = newName))
        }
    }

    fun deleteProductGroup(group: ProductGroupEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            classifierRepository.deleteGroup(group)
            if (_selectedGroupId.value == group.id) {
                _selectedGroupId.value = null
            }
        }
    }

    fun moveProductGroupUp(group: ProductGroupEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = productGroups.value.toMutableList()
            val idx = list.indexOfFirst { it.id == group.id }
            if (idx > 0) {
                val item = list.removeAt(idx)
                list.add(idx - 1, item)
                list.forEachIndexed { i, g ->
                    if (g.displayOrder != i) {
                        classifierRepository.updateGroup(g.copy(displayOrder = i))
                    }
                }
            }
        }
    }

    fun moveProductGroupDown(group: ProductGroupEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = productGroups.value.toMutableList()
            val idx = list.indexOfFirst { it.id == group.id }
            if (idx >= 0 && idx < list.size - 1) {
                val item = list.removeAt(idx)
                list.add(idx + 1, item)
                list.forEachIndexed { i, g ->
                    if (g.displayOrder != i) {
                        classifierRepository.updateGroup(g.copy(displayOrder = i))
                    }
                }
            }
        }
    }

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
        viewModelScope.launch(Dispatchers.IO) {
            val maxOrder = classifierRepository.getMaxUnitDisplayOrder()
            classifierRepository.insertUnit(UnitEntity(name = name, displayOrder = maxOrder + 1))
        }
    }

    fun renameUnit(unit: UnitEntity, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val oldName = unit.name
            classifierRepository.updateUnit(unit.copy(name = newName))
            productRepository.renameUnit(oldName, newName)
        }
    }

    fun deleteUnit(unit: UnitEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            productRepository.clearUnit(unit.name)
            classifierRepository.deleteUnit(unit)
            if (_selectedUnitId.value == unit.id) {
                _selectedUnitId.value = null
            }
        }
    }

    fun moveUnitUp(unit: UnitEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = units.value.toMutableList()
            val idx = list.indexOfFirst { it.id == unit.id }
            if (idx > 0) {
                val item = list.removeAt(idx)
                list.add(idx - 1, item)
                list.forEachIndexed { i, u ->
                    if (u.displayOrder != i) {
                        classifierRepository.updateUnit(u.copy(displayOrder = i))
                    }
                }
            }
        }
    }

    fun moveUnitDown(unit: UnitEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = units.value.toMutableList()
            val idx = list.indexOfFirst { it.id == unit.id }
            if (idx >= 0 && idx < list.size - 1) {
                val item = list.removeAt(idx)
                list.add(idx + 1, item)
                list.forEachIndexed { i, u ->
                    if (u.displayOrder != i) {
                        classifierRepository.updateUnit(u.copy(displayOrder = i))
                    }
                }
            }
        }
    }

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
        viewModelScope.launch(Dispatchers.IO) {
            shopRepository.renameDepartmentGlobally(oldName, newName)
            if (_selectedDepartmentGroup.value == oldName) {
                _selectedDepartmentGroup.value = newName
            }
        }
    }

    fun deleteDepartmentGroup(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            shopRepository.deleteDepartmentGlobally(name)
            if (_selectedDepartmentGroup.value == name) {
                _selectedDepartmentGroup.value = null
            }
        }
    }

    fun createDepartmentGroup(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val shops = allShops.value
            if (shops.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    message = "Сначала добавьте хотя бы один магазин"
                )
                return@launch
            }
            // Add the department to every shop
            shops.forEach { shop ->
                val existingDepts = shopRepository.getDepartmentsByShopIdSync(shop.id)
                val maxOrder = existingDepts.maxOfOrNull { it.displayOrder } ?: 0
                shopRepository.insertDepartment(
                    ShopDepartmentEntity(
                        shopId = shop.id,
                        name = name,
                        displayOrder = maxOrder + 1
                    )
                )
            }
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
                    prefs = prefs
                )
            }
        }
    }
}
