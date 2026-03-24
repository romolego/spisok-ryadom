package com.spisokryadom.app.ui.productdb

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spisokryadom.app.SpisokRyadomApp
import com.spisokryadom.app.data.entity.ProductEntity
import com.spisokryadom.app.data.entity.ProductShopLinkEntity
import com.spisokryadom.app.data.entity.ShoppingListEntryEntity
import com.spisokryadom.app.data.repository.ProductRepository
import com.spisokryadom.app.data.repository.ShoppingListRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

data class ProductDatabaseUiState(
    val products: List<ProductEntity> = emptyList(),
    val searchQuery: String = "",
    val unitSuggestions: List<String> = emptyList(),
    val isLoading: Boolean = true
)

class ProductDatabaseViewModel(
    private val productRepository: ProductRepository,
    private val shoppingListRepository: ShoppingListRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductDatabaseUiState())
    val uiState: StateFlow<ProductDatabaseUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    init {
        @OptIn(ExperimentalCoroutinesApi::class)
        viewModelScope.launch {
            _searchQuery.flatMapLatest { query ->
                if (query.isBlank()) {
                    productRepository.getAllProducts()
                } else {
                    productRepository.searchProducts(query)
                }
            }.collect { products ->
                _uiState.value = _uiState.value.copy(
                    products = products,
                    isLoading = false
                )
            }
        }
        viewModelScope.launch {
            productRepository.getDistinctUnits().collect { units ->
                _uiState.value = _uiState.value.copy(unitSuggestions = units)
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun addToList(product: ProductEntity, quantity: Double, unit: String) {
        viewModelScope.launch {
            val priorityLink: ProductShopLinkEntity? =
                productRepository.getPriorityLink(product.id)

            val entry = ShoppingListEntryEntity(
                productId = product.id,
                quantity = quantity,
                unit = unit,
                assignedShopId = priorityLink?.shopId,
                assignedDepartmentId = priorityLink?.departmentId,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            shoppingListRepository.addEntry(entry)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SpisokRyadomApp
                ProductDatabaseViewModel(
                    app.container.productRepository,
                    app.container.shoppingListRepository
                )
            }
        }
    }
}
