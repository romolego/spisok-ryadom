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

data class ShopCardUiState(
    val isNew: Boolean = true,
    val name: String = "",
    val address: String = "",
    val note: String = "",
    val displayOrder: String = "0",
    val departments: List<ShopDepartmentEntity> = emptyList(),
    val isSaved: Boolean = false,
    val isLoading: Boolean = true
)

class ShopCardViewModel(
    private val shopId: Long,
    private val shopRepository: ShopRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShopCardUiState(isNew = shopId == -1L))
    val uiState: StateFlow<ShopCardUiState> = _uiState.asStateFlow()

    private var currentShopId: Long = shopId

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

    fun updateDisplayOrder(order: String) {
        _uiState.value = _uiState.value.copy(displayOrder = order)
    }

    fun save() {
        val state = _uiState.value
        if (state.name.isBlank()) return

        viewModelScope.launch {
            val shop = ShopEntity(
                id = if (state.isNew) 0 else currentShopId,
                name = state.name.trim(),
                address = state.address.trim().ifBlank { null },
                note = state.note.trim().ifBlank { null },
                displayOrder = state.displayOrder.toIntOrNull() ?: 0
            )

            if (state.isNew) {
                currentShopId = shopRepository.insertShop(shop)
                _uiState.value = _uiState.value.copy(isNew = false)
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
        if (currentShopId <= 0) return
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
