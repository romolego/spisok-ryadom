package com.spisokryadom.app.ui.shoplist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spisokryadom.app.SpisokRyadomApp
import com.spisokryadom.app.data.entity.ShopEntity
import com.spisokryadom.app.data.repository.ShopRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ShopListUiState(
    val shops: List<ShopEntity> = emptyList(),
    val isLoading: Boolean = true,
    val isEditing: Boolean = false
)

class ShopListViewModel(
    private val shopRepository: ShopRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShopListUiState())
    val uiState: StateFlow<ShopListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            shopRepository.getAllShops().collect { shops ->
                _uiState.value = _uiState.value.copy(shops = shops, isLoading = false)
            }
        }
    }

    fun deleteShop(shop: ShopEntity) {
        viewModelScope.launch { shopRepository.deleteShop(shop) }
    }

    fun toggleEditing() {
        _uiState.value = _uiState.value.copy(isEditing = !_uiState.value.isEditing)
    }

    fun moveShopUp(shop: ShopEntity) {
        viewModelScope.launch {
            val shops = _uiState.value.shops
            val index = shops.indexOfFirst { it.id == shop.id }
            if (index <= 0) return@launch
            swapShopOrder(shops, index, index - 1)
        }
    }

    fun moveShopDown(shop: ShopEntity) {
        viewModelScope.launch {
            val shops = _uiState.value.shops
            val index = shops.indexOfFirst { it.id == shop.id }
            if (index < 0 || index >= shops.size - 1) return@launch
            swapShopOrder(shops, index, index + 1)
        }
    }

    private suspend fun swapShopOrder(
        shops: List<ShopEntity>,
        indexA: Int,
        indexB: Int
    ) {
        val shopA = shops[indexA]
        val shopB = shops[indexB]
        val orderA = shopA.displayOrder
        val orderB = shopB.displayOrder
        val newOrderA = if (orderA == orderB) indexB else orderB
        val newOrderB = if (orderA == orderB) indexA else orderA
        shopRepository.updateShops(listOf(
            shopA.copy(displayOrder = newOrderA),
            shopB.copy(displayOrder = newOrderB)
        ))
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SpisokRyadomApp
                ShopListViewModel(app.container.shopRepository)
            }
        }
    }
}
