package com.spisokryadom.app.ui.addtolist

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spisokryadom.app.SpisokRyadomApp
import com.spisokryadom.app.data.entity.ProductEntity
import com.spisokryadom.app.data.entity.ShopEntity
import com.spisokryadom.app.data.entity.ShoppingListEntryEntity
import com.spisokryadom.app.data.repository.ProductRepository
import com.spisokryadom.app.data.repository.ShopRepository
import com.spisokryadom.app.data.repository.ShoppingListRepository
import com.spisokryadom.app.ui.shoppinglist.ShoppingListViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class AddToListUiState(
    val searchQuery: String = "",
    val searchResults: List<ProductEntity> = emptyList(),
    val shops: List<ShopEntity> = emptyList(),
    val selectedShopId: Long? = null,
    val isShopLocked: Boolean = false,
    val saveToDatabase: Boolean = true,
    val isNewProduct: Boolean = false,
    val addedMessage: String? = null,
    val isLoading: Boolean = false,
    val shouldNavigateBack: Boolean = false
)

class AddToListViewModel(
    private val productRepository: ProductRepository,
    private val shopRepository: ShopRepository,
    private val shoppingListRepository: ShoppingListRepository,
    prefs: SharedPreferences,
    preselectedShopId: Long? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AddToListUiState(
            selectedShopId = preselectedShopId,
            isShopLocked = preselectedShopId != null,
            saveToDatabase = prefs.getBoolean(
                ShoppingListViewModel.PREF_GLOBAL_SAVE_NEW_PRODUCT_TO_DB, true
            )
        )
    )
    val uiState: StateFlow<AddToListUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            shopRepository.getAllShops().collect { shops ->
                _uiState.value = _uiState.value.copy(
                    shops = shops.sortedBy { it.displayOrder }
                )
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        val trimmed = query.trim()
        val hasExactMatch = _uiState.value.searchResults.any {
            it.name.equals(trimmed, ignoreCase = true)
        }
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            isNewProduct = trimmed.isNotEmpty() && !hasExactMatch
        )

        searchJob?.cancel()
        if (trimmed.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                searchResults = emptyList(),
                isNewProduct = false
            )
            return
        }

        searchJob = viewModelScope.launch {
            val results = productRepository.searchProducts(trimmed).first()
            val exactMatch = results.any { it.name.equals(trimmed, ignoreCase = true) }
            _uiState.value = _uiState.value.copy(
                searchResults = results,
                isNewProduct = !exactMatch
            )
        }
    }

    fun onShopSelected(shopId: Long?) {
        _uiState.value = _uiState.value.copy(selectedShopId = shopId)
    }

    fun onSaveToDatabaseChanged(save: Boolean) {
        _uiState.value = _uiState.value.copy(saveToDatabase = save)
    }

    fun addExistingProduct(product: ProductEntity) {
        viewModelScope.launch {
            val shopId = _uiState.value.selectedShopId

            // Resolve department from product-shop link if shop is selected
            var departmentId: Long? = null
            if (shopId != null) {
                val links = productRepository.getShopLinksForProductSync(product.id)
                val linkForShop = links.find { it.shopId == shopId }
                departmentId = linkForShop?.departmentId
            }

            val entry = ShoppingListEntryEntity(
                productId = product.id,
                quantity = product.defaultQuantity?.takeIf { it > 0.0 } ?: 1.0,
                unit = product.defaultUnit ?: "шт",
                assignedShopId = shopId,
                assignedDepartmentId = departmentId
            )
            shoppingListRepository.addOrMergeEntry(entry)
            _uiState.value = _uiState.value.copy(
                addedMessage = "${product.name} добавлен в список",
                searchQuery = "",
                searchResults = emptyList(),
                isNewProduct = false,
                shouldNavigateBack = true
            )
        }
    }

    fun addNewProduct(name: String) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return

        viewModelScope.launch {
            val saveToDb = _uiState.value.saveToDatabase
            val shopId = _uiState.value.selectedShopId

            val product = ProductEntity(
                name = trimmedName,
                isTemporary = !saveToDb
            )
            val productId = productRepository.insertProduct(product)

            val entry = ShoppingListEntryEntity(
                productId = productId,
                quantity = 1.0,
                unit = "",
                assignedShopId = shopId,
                assignedDepartmentId = null
            )
            shoppingListRepository.addEntry(entry)

            _uiState.value = _uiState.value.copy(
                addedMessage = "$trimmedName добавлен в список",
                searchQuery = "",
                searchResults = emptyList(),
                isNewProduct = false,
                saveToDatabase = true,
                shouldNavigateBack = true
            )
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(addedMessage = null)
    }

    companion object {
        fun factory(preselectedShopId: Long? = null): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SpisokRyadomApp
                val prefs = app.getSharedPreferences(
                    ShoppingListViewModel.PREFS_NAME_PUBLIC,
                    Context.MODE_PRIVATE
                )
                AddToListViewModel(
                    app.container.productRepository,
                    app.container.shopRepository,
                    app.container.shoppingListRepository,
                    prefs = prefs,
                    preselectedShopId = preselectedShopId
                )
            }
        }
    }
}
