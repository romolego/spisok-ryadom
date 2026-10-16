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
import com.spisokryadom.app.data.repository.ClassifierRepository
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
    val isInShoppingList: Boolean,
    val listEntryId: Long? = null,
    val listQuantity: Double? = null,
    val listUnit: String? = null,
    val listShopName: String? = null
)

data class PickProductUi(
    val product: ProductEntity,
    val isLinked: Boolean
)

data class ShopContentUiState(
    val shop: ShopEntity? = null,
    val products: List<ShopProductUi> = emptyList(),
    val unitSuggestions: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val showPickProducts: Boolean = false,
    val pickSearchQuery: String = "",
    val pickProducts: List<PickProductUi> = emptyList()
)

class ShopContentViewModel(
    private val shopId: Long,
    private val productRepository: ProductRepository,
    private val shopRepository: ShopRepository,
    private val shoppingListRepository: ShoppingListRepository,
    private val classifierRepository: ClassifierRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShopContentUiState())
    val uiState: StateFlow<ShopContentUiState> = _uiState.asStateFlow()

    private val _allProducts = MutableStateFlow<Map<Long, ProductEntity>>(emptyMap())
    private val _departments = MutableStateFlow<Map<Long, ShopDepartmentEntity>>(emptyMap())
    private val _shoppingListEntries = MutableStateFlow<Map<Long, ShoppingListEntryEntity>>(emptyMap())
    private val _shops = MutableStateFlow<Map<Long, ShopEntity>>(emptyMap())

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
                _shoppingListEntries.value = entries
                    .filter { !it.isBought }
                    .associateBy { it.productId }
            }
        }
        viewModelScope.launch {
            shopRepository.getAllShops().collect { shops ->
                _shops.value = shops.associateBy { it.id }
            }
        }
        viewModelScope.launch {
            classifierRepository.getOrderedUnitNames().collect { units ->
                _uiState.value = _uiState.value.copy(unitSuggestions = units)
            }
        }
        viewModelScope.launch {
            combine(
                productRepository.getShopLinksForShop(shopId),
                _allProducts,
                _departments,
                _shoppingListEntries,
                _shops
            ) { links, products, departments, entryMap, shopMap ->
                links.mapNotNull { link ->
                    val product = products[link.productId] ?: return@mapNotNull null
                    val entry = entryMap[link.productId]
                    ShopProductUi(
                        product = product,
                        link = link,
                        departmentName = link.departmentId?.let { departments[it]?.name },
                        isInShoppingList = entry != null,
                        listEntryId = entry?.id,
                        listQuantity = entry?.quantity,
                        listUnit = entry?.unit,
                        listShopName = entry?.assignedShopId?.let { shopMap[it]?.name }
                    )
                }
            }.collect { productUis ->
                _uiState.value = _uiState.value.copy(products = productUis, isLoading = false)
            }
        }
    }

    /** Quick-add product to the list with its default quantity/unit (no dialog). */
    fun quickAddToShoppingList(product: ProductEntity) {
        viewModelScope.launch {
            val qty = product.defaultQuantity ?: 1.0
            val unit = product.defaultUnit ?: "шт"
            val link = productRepository.getShopLinksForProductSync(product.id)
                .find { it.shopId == shopId }

            val entry = ShoppingListEntryEntity(
                productId = product.id,
                quantity = qty,
                unit = unit,
                assignedShopId = shopId,
                assignedDepartmentId = link?.departmentId,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            shoppingListRepository.addOrMergeEntry(entry)
        }
    }

    fun incrementQuantity(productId: Long) {
        viewModelScope.launch {
            val entry = shoppingListRepository.findActiveByProductId(productId) ?: return@launch
            val step = _allProducts.value[productId]?.defaultQuantity?.takeIf { it > 0.0 } ?: 1.0
            shoppingListRepository.updateQuantityByProductId(productId, entry.quantity + step)
        }
    }

    fun decrementQuantity(productId: Long) {
        viewModelScope.launch {
            val entry = shoppingListRepository.findActiveByProductId(productId) ?: return@launch
            val step = _allProducts.value[productId]?.defaultQuantity?.takeIf { it > 0.0 } ?: 1.0
            val newQty = entry.quantity - step
            if (newQty <= 0.0) {
                shoppingListRepository.removeByProductId(productId)
            } else {
                shoppingListRepository.updateQuantityByProductId(productId, newQty)
            }
        }
    }

    fun setQuantity(productId: Long, quantity: Double) {
        viewModelScope.launch {
            if (quantity <= 0.0) {
                shoppingListRepository.removeByProductId(productId)
            } else {
                shoppingListRepository.updateQuantityByProductId(productId, quantity)
            }
        }
    }

    // --- Pick existing products mode ---

    private val _linkedProductIds = MutableStateFlow<Set<Long>>(emptySet())

    init {
        // Track which product IDs are linked to this shop (updates reactively)
        viewModelScope.launch {
            productRepository.getShopLinksForShop(shopId).collect { links ->
                _linkedProductIds.value = links.map { it.productId }.toSet()
            }
        }
    }

    fun openPickProducts() {
        _uiState.value = _uiState.value.copy(showPickProducts = true, pickSearchQuery = "")
        refreshPickList()
    }

    fun closePickProducts() {
        _uiState.value = _uiState.value.copy(showPickProducts = false, pickSearchQuery = "", pickProducts = emptyList())
    }

    fun setPickSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(pickSearchQuery = query)
        refreshPickList()
    }

    private fun refreshPickList() {
        viewModelScope.launch {
            val query = _uiState.value.pickSearchQuery
            val allProducts = _allProducts.value.values.toList()
            val linked = _linkedProductIds.value
            val filtered = if (query.isBlank()) {
                allProducts
            } else {
                allProducts.filter { it.name.contains(query, ignoreCase = true) }
            }
            val pickList = filtered
                .map { product ->
                    PickProductUi(
                        product = product,
                        isLinked = product.id in linked
                    )
                }
                .sortedWith(compareBy<PickProductUi> { !it.isLinked }.thenBy { it.product.name.lowercase() })
            _uiState.value = _uiState.value.copy(pickProducts = pickList)
        }
    }

    fun toggleProductLink(productId: Long) {
        viewModelScope.launch {
            val linked = _linkedProductIds.value
            if (productId in linked) {
                // Remove link
                val links = productRepository.getShopLinksForProductSync(productId)
                links.find { it.shopId == shopId }?.let { productRepository.deleteShopLink(it) }
            } else {
                // Add link with next available priority for this product
                val existingLinks = productRepository.getShopLinksForProductSync(productId)
                val nextPriority = (existingLinks.maxOfOrNull { it.priority } ?: 0) + 1
                productRepository.insertShopLink(
                    ProductShopLinkEntity(
                        productId = productId,
                        shopId = shopId,
                        priority = nextPriority,
                        departmentId = null
                    )
                )
            }
            // Refresh pick list after change
            refreshPickList()
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
                    shoppingListRepository = app.container.shoppingListRepository,
                    classifierRepository = app.container.classifierRepository
                )
            }
        }
    }
}
