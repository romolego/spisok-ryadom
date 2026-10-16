package com.spisokryadom.app.ui.shopcard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spisokryadom.app.SpisokRyadomApp
import com.spisokryadom.app.data.entity.ShopDepartmentEntity
import com.spisokryadom.app.data.entity.ShopEntity
import com.spisokryadom.app.data.repository.ShopRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Tri-state for per-shop urgency overrides:
 * USE_GLOBAL = inherit the global default,
 * YES / NO = local override.
 */
enum class UrgencyOverride {
    USE_GLOBAL, YES, NO;

    val label: String
        get() = when (this) {
            USE_GLOBAL -> "По глобальной настройке"
            YES -> "Да"
            NO -> "Нет"
        }
}

data class PendingDepartment(
    val tempId: Long,
    val name: String,
    val displayOrder: Int
)

data class ShopCardUiState(
    val isNew: Boolean = true,
    val name: String = "",
    val address: String = "",
    val note: String = "",
    val displayOrder: String = "0",
    val departments: List<ShopDepartmentEntity> = emptyList(),
    val pendingDepartments: List<PendingDepartment> = emptyList(),
    val allDepartmentNames: List<String> = emptyList(),
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
    val isLoading: Boolean = true,
    /** Per-shop urgency: show urgent in collapsed */
    val showUrgentInCollapsed: UrgencyOverride = UrgencyOverride.USE_GLOBAL,
    /** Per-shop urgency: boost shop with urgent items */
    val boostUrgentShop: UrgencyOverride = UrgencyOverride.USE_GLOBAL
)

class ShopCardViewModel(
    private val shopId: Long,
    private val shopRepository: ShopRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShopCardUiState(isNew = shopId == -1L))
    val uiState: StateFlow<ShopCardUiState> = _uiState.asStateFlow()

    private var currentShopId: Long = shopId
    private var nextTempId: Long = 1L

    init {
        if (shopId != -1L) {
            viewModelScope.launch {
                val shop = shopRepository.getShopById(shopId) ?: return@launch
                _uiState.value = _uiState.value.copy(
                    isNew = false,
                    name = shop.name,
                    address = shop.address ?: "",
                    note = shop.note ?: "",
                    displayOrder = shop.displayOrder.toString(),
                    showUrgentInCollapsed = booleanToOverride(shop.showUrgentInCollapsed),
                    boostUrgentShop = booleanToOverride(shop.boostUrgentShop),
                    isLoading = false
                )
            }
            viewModelScope.launch {
                shopRepository.getDepartments(shopId).collect { departments ->
                    _uiState.value = _uiState.value.copy(departments = departments)
                }
            }
        } else {
            _uiState.value = _uiState.value.copy(isLoading = false)
        }

        // Загружаем все уникальные названия групп для подсказок
        viewModelScope.launch {
            shopRepository.getDistinctDepartmentNames().collect { names ->
                _uiState.value = _uiState.value.copy(allDepartmentNames = names)
            }
        }
    }

    private fun booleanToOverride(value: Boolean?): UrgencyOverride = when (value) {
        null -> UrgencyOverride.USE_GLOBAL
        true -> UrgencyOverride.YES
        false -> UrgencyOverride.NO
    }

    private fun overrideToBoolean(value: UrgencyOverride): Boolean? = when (value) {
        UrgencyOverride.USE_GLOBAL -> null
        UrgencyOverride.YES -> true
        UrgencyOverride.NO -> false
    }

    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun updateAddress(address: String) {
        _uiState.value = _uiState.value.copy(address = address)
    }

    fun updateNote(note: String) {
        _uiState.value = _uiState.value.copy(note = note)
    }

    fun updateShowUrgentInCollapsed(value: UrgencyOverride) {
        _uiState.value = _uiState.value.copy(showUrgentInCollapsed = value)
    }

    fun updateBoostUrgentShop(value: UrgencyOverride) {
        _uiState.value = _uiState.value.copy(boostUrgentShop = value)
    }

    fun save() {
        val state = _uiState.value
        if (state.name.isBlank()) return

        viewModelScope.launch {
            val displayOrder = if (state.isNew) {
                // Auto-assign next displayOrder for new shops
                val existingShops = shopRepository.getAllShopsSync()
                (existingShops.maxOfOrNull { it.displayOrder } ?: 0) + 1
            } else {
                state.displayOrder.toIntOrNull() ?: 0
            }

            val shop = ShopEntity(
                id = if (state.isNew) 0 else currentShopId,
                name = state.name.trim(),
                address = state.address.trim().ifBlank { null },
                note = state.note.trim().ifBlank { null },
                displayOrder = displayOrder,
                showUrgentInCollapsed = overrideToBoolean(state.showUrgentInCollapsed),
                boostUrgentShop = overrideToBoolean(state.boostUrgentShop),
                // Reset manualOrderOverride when user explicitly changes boost settings
                manualOrderOverride = if (state.isNew) false else {
                    val existing = shopRepository.getShopById(currentShopId)
                    if (existing != null &&
                        overrideToBoolean(state.boostUrgentShop) != existing.boostUrgentShop
                    ) {
                        false // Reset override when boost setting changes
                    } else {
                        existing?.manualOrderOverride ?: false
                    }
                }
            )

            if (state.isNew) {
                currentShopId = shopRepository.insertShop(shop)
                // Insert pending departments
                for (pending in state.pendingDepartments.sortedBy { it.displayOrder }) {
                    shopRepository.insertDepartment(
                        ShopDepartmentEntity(
                            shopId = currentShopId,
                            name = pending.name,
                            displayOrder = pending.displayOrder
                        )
                    )
                }
                _uiState.value = _uiState.value.copy(isNew = false, pendingDepartments = emptyList())
                // Start collecting departments for the new shop
                viewModelScope.launch {
                    shopRepository.getDepartments(currentShopId).collect { departments ->
                        _uiState.value = _uiState.value.copy(departments = departments)
                    }
                }
            } else {
                shopRepository.updateShop(shop)
            }
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }

    fun addDepartment(name: String) {
        if (currentShopId <= 0) {
            // New shop: store pending departments locally
            val pending = _uiState.value.pendingDepartments
            val maxOrder = pending.maxOfOrNull { it.displayOrder } ?: 0
            _uiState.value = _uiState.value.copy(
                pendingDepartments = pending + PendingDepartment(
                    tempId = nextTempId++,
                    name = name.trim(),
                    displayOrder = maxOrder + 1
                )
            )
        } else {
            viewModelScope.launch {
                val maxOrder = _uiState.value.departments.maxOfOrNull { it.displayOrder } ?: 0
                shopRepository.insertDepartment(
                    ShopDepartmentEntity(
                        shopId = currentShopId,
                        name = name.trim(),
                        displayOrder = maxOrder + 1
                    )
                )
            }
        }
    }

    fun addDepartments(names: List<String>) {
        if (names.isEmpty()) return
        if (currentShopId <= 0) {
            val pending = _uiState.value.pendingDepartments
            var maxOrder = pending.maxOfOrNull { it.displayOrder } ?: 0
            val newPending = names.map { name ->
                maxOrder++
                PendingDepartment(
                    tempId = nextTempId++,
                    name = name.trim(),
                    displayOrder = maxOrder
                )
            }
            _uiState.value = _uiState.value.copy(
                pendingDepartments = pending + newPending
            )
        } else {
            viewModelScope.launch {
                var maxOrder = _uiState.value.departments.maxOfOrNull { it.displayOrder } ?: 0
                for (name in names) {
                    maxOrder++
                    shopRepository.insertDepartment(
                        ShopDepartmentEntity(
                            shopId = currentShopId,
                            name = name.trim(),
                            displayOrder = maxOrder
                        )
                    )
                }
            }
        }
    }

    fun updateDepartment(department: ShopDepartmentEntity) {
        viewModelScope.launch {
            shopRepository.updateDepartment(department)
        }
    }

    fun deleteDepartment(department: ShopDepartmentEntity) {
        viewModelScope.launch {
            shopRepository.deleteDepartment(department)
        }
    }

    fun moveDepartmentUp(department: ShopDepartmentEntity) {
        viewModelScope.launch {
            val departments = _uiState.value.departments.sortedBy { it.displayOrder }
            val index = departments.indexOfFirst { it.id == department.id }
            if (index <= 0) return@launch
            swapDepartmentOrder(departments, index, index - 1)
        }
    }

    fun moveDepartmentDown(department: ShopDepartmentEntity) {
        viewModelScope.launch {
            val departments = _uiState.value.departments.sortedBy { it.displayOrder }
            val index = departments.indexOfFirst { it.id == department.id }
            if (index < 0 || index >= departments.size - 1) return@launch
            swapDepartmentOrder(departments, index, index + 1)
        }
    }

    // Pending department operations (for new shops before first save)
    fun deletePendingDepartment(tempId: Long) {
        _uiState.value = _uiState.value.copy(
            pendingDepartments = _uiState.value.pendingDepartments.filter { it.tempId != tempId }
        )
    }

    fun renamePendingDepartment(tempId: Long, newName: String) {
        _uiState.value = _uiState.value.copy(
            pendingDepartments = _uiState.value.pendingDepartments.map {
                if (it.tempId == tempId) it.copy(name = newName.trim()) else it
            }
        )
    }

    fun movePendingDepartmentUp(tempId: Long) {
        val sorted = _uiState.value.pendingDepartments.sortedBy { it.displayOrder }
        val index = sorted.indexOfFirst { it.tempId == tempId }
        if (index <= 0) return
        val updated = swapPendingOrder(sorted, index, index - 1)
        _uiState.value = _uiState.value.copy(pendingDepartments = updated)
    }

    fun movePendingDepartmentDown(tempId: Long) {
        val sorted = _uiState.value.pendingDepartments.sortedBy { it.displayOrder }
        val index = sorted.indexOfFirst { it.tempId == tempId }
        if (index < 0 || index >= sorted.size - 1) return
        val updated = swapPendingOrder(sorted, index, index + 1)
        _uiState.value = _uiState.value.copy(pendingDepartments = updated)
    }

    fun sortDepartmentsAlphabetically() {
        viewModelScope.launch {
            val sorted = _uiState.value.departments.sortedBy { it.name.lowercase() }
            val updated = sorted.mapIndexed { index, dept ->
                dept.copy(displayOrder = index + 1)
            }
            shopRepository.updateDepartments(updated)
        }
    }

    fun sortPendingDepartmentsAlphabetically() {
        val sorted = _uiState.value.pendingDepartments.sortedBy { it.name.lowercase() }
        val updated = sorted.mapIndexed { index, dept ->
            dept.copy(displayOrder = index + 1)
        }
        _uiState.value = _uiState.value.copy(pendingDepartments = updated)
    }

    private fun swapPendingOrder(list: List<PendingDepartment>, a: Int, b: Int): List<PendingDepartment> {
        val orderA = list[a].displayOrder
        val orderB = list[b].displayOrder
        return list.mapIndexed { i, item ->
            when (i) {
                a -> item.copy(displayOrder = orderB)
                b -> item.copy(displayOrder = orderA)
                else -> item
            }
        }
    }

    private suspend fun swapDepartmentOrder(
        departments: List<ShopDepartmentEntity>,
        indexA: Int,
        indexB: Int
    ) {
        val deptA = departments[indexA]
        val deptB = departments[indexB]
        val orderA = deptA.displayOrder
        val orderB = deptB.displayOrder
        val newOrderA = if (orderA == orderB) indexB else orderB
        val newOrderB = if (orderA == orderB) indexA else orderA
        shopRepository.updateDepartments(listOf(
            deptA.copy(displayOrder = newOrderA),
            deptB.copy(displayOrder = newOrderB)
        ))
    }

    fun deleteShop() {
        if (_uiState.value.isNew || currentShopId <= 0) return
        viewModelScope.launch {
            val shop = shopRepository.getShopById(currentShopId) ?: return@launch
            shopRepository.deleteShop(shop)
            _uiState.value = _uiState.value.copy(isDeleted = true)
        }
    }

    companion object {
        fun factory(shopId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SpisokRyadomApp
                ShopCardViewModel(
                    shopId = shopId,
                    shopRepository = app.container.shopRepository
                )
            }
        }
    }
}
