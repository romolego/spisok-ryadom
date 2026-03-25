package com.spisokryadom.app.ui.shopcontent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spisokryadom.app.SpisokRyadomApp
import com.spisokryadom.app.data.entity.ProductEntity
import com.spisokryadom.app.data.entity.ProductShopLinkEntity
import com.spisokryadom.app.data.entity.ShopDepartmentEntity
import com.spisokryadom.app.data.entity.ShopEntity
import com.spisokryadom.app.data.entity.ShoppingListEntryEntity
import com.spisokryadom.app.data.repository.ProductRepository
import com.spisokryadom.app.data.repository.ShopRepository
import com.spisokryadom.app.data.repository.ShoppingListRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Товар, доступный в магазине (из базы привязок ProductShopLink).
 */
data class ShopProductUi(
    val product: ProductEntity,
    val link: ProductShopLinkEntity,
    val departmentName: String?,
    val isInShoppingList: Boolean
)

data class ShopContentUiState(
    val shop: ShopEntity? = null,
    val products: List<ShopProductUi> = emptyList(),
    val unitSuggestions: List<String> = emptyList(),
    val isLoading: Boolean = true
)

class ShopContentViewModel(
    private val shopId: Long,
    private val productRepository: ProductRepository,
    private val shopRepository: ShopRepository,
    private val shoppingListRepository: ShoppingListRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShopContentUiState())
    val uiState: StateFlow<ShopContentUiState> = _uiState.asStateFlow()

    private val _allProducts = MutableStateFlow<Map<Long, ProductEntity>>(emptyMap())
    private val _departments = MutableStateFlow<Map<Long, ShopDepartmentEntity>>(emptyMap())
    private val _shoppingListProductIds = MutableStateFlow<Set<Long>>(emptySet())

    init {
        viewModelScope.launch {
            val shop = shopRepository.getShopById(shopId)
            _uiState.value = _uiState.value.copy(shop = shop)
        }
        viewModelScope.launch {
            productRepository.getAllProducts().collect { products ->
                _allProducts.value = products.associateBy { it.id }
            }
        }
        viewModelScope.launch {
            shopRepository.getAllDepartments().collect { departments ->
                _departments.value = departments.associateBy { it.id }
            }
        }
        viewModelScope.launch {
            shoppingListRepository.getAllEntries().collect { entries ->
                _shoppingListProductIds.value = entries.map { it.productId }.toSet()
            }
        }
        viewModelScope.launch {
            productRepository.getDistinctUnits().collect { units ->
                _uiState.value = _uiState.value.copy(unitSuggestions = units)
            }
        }
        viewModelScope.launch {
            combine(
                productRepository.getShopLinksForShop(shopId),
                _allProducts,
                _departments,
                _shoppingListProductIds
            ) { links, products, departments, inListIds ->
                links.mapNotNull { link ->
                    val product = products[link.productId] ?: return@mapNotNull null
                    ShopProductUi(
                        product = product,
                        link = link,
                        departmentName = link.departmentId?.let { departments[it]?.name },
                        isInShoppingList = link.productId in inListIds
                    )
                }
            }.collect { productUis ->
                _uiState.value = _uiState.value.copy(products = productUis, isLoading = false)
            }
        }
    }

    fun addToShoppingList(product: ProductEntity, quantity: Double, unit: String) {
        viewModelScope.launch {
            val link = productRepository.getShopLinksForProductSync(product.id)
                .find { it.shopId == shopId }

            val entry = ShoppingListEntryEntity(
                productId = product.id,
                quantity = quantity,
                unit = unit,
                assignedShopId = shopId,
                assignedDepartmentId = link?.departmentId,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            shoppingListRepository.addEntry(entry)
        }
    }

    companion object {
        fun factory(shopId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SpisokRyadomApp
                ShopContentViewModel(
                    shopId = shopId,
                    productRepository = app.container.productRepository,
                    shopRepository = app.container.shopRepository,
                    shoppingListRepository = app.container.shoppingListRepository
                )
            }
        }
    }
}
