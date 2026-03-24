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
    val isLoading: Boolean = true
)

class ShopListViewModel(
    private val shopRepository: ShopRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShopListUiState())
    val uiState: StateFlow<ShopListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            shopRepository.getAllShops().collect { shops ->
                _uiState.value = ShopListUiState(shops = shops, isLoading = false)
            }
        }
    }

    fun deleteShop(shop: ShopEntity) {
        viewModelScope.launch { shopRepository.deleteShop(shop) }
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
