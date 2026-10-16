package com.spisokryadom.app.ui.productdb

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spisokryadom.app.SpisokRyadomApp
import com.spisokryadom.app.data.entity.ProductEntity
import com.spisokryadom.app.data.entity.ProductGroupEntity
import com.spisokryadom.app.data.entity.ProductShopLinkEntity
import com.spisokryadom.app.data.entity.RecipientEntity
import com.spisokryadom.app.data.entity.ShopEntity
import com.spisokryadom.app.data.entity.ShoppingListEntryEntity
import com.spisokryadom.app.data.repository.ClassifierRepository
import com.spisokryadom.app.data.repository.ProductRepository
import com.spisokryadom.app.data.repository.ShopRepository
import com.spisokryadom.app.data.repository.ShoppingListRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

enum class ProductViewMode {
    ALPHABETICAL, BY_SHOPS, BY_GROUPS, BY_RECIPIENTS;

    val label: String
        get() = when (this) {
            ALPHABETICAL -> "А–Я"
            BY_SHOPS -> "Магазины"
            BY_GROUPS -> "Группы"
            BY_RECIPIENTS -> "Получатели"
        }
}

data class ShoppingListInfo(
    val entryId: Long,
    val quantity: Double,
    val unit: String,
    val shopName: String?
)

data class ProductSection(
    val name: String,
    val products: List<ProductEntity>
)

data class ProductDatabaseUiState(
    val products: List<ProductEntity> = emptyList(),
    val sections: List<ProductSection> = emptyList(),
    val viewMode: ProductViewMode = ProductViewMode.ALPHABETICAL,
    val searchQuery: String = "",
    val unitSuggestions: List<String> = emptyList(),
    val shoppingListMap: Map<Long, ShoppingListInfo> = emptyMap(),
    val isLoading: Boolean = true
)

class ProductDatabaseViewModel(
    private val productRepository: ProductRepository,
    private val shoppingListRepository: ShoppingListRepository,
    private val shopRepository: ShopRepository,
    private val classifierRepository: ClassifierRepository,
    private val prefs: SharedPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductDatabaseUiState())
    val uiState: StateFlow<ProductDatabaseUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    // Raw data flows collected into local state for regrouping
    private var allProducts: List<ProductEntity> = emptyList()
    private var allShops: List<ShopEntity> = emptyList()
    private var allLinks: List<ProductShopLinkEntity> = emptyList()
    private var allGroups: List<ProductGroupEntity> = emptyList()
    private var allRecipients: List<RecipientEntity> = emptyList()

    init {
        val savedMode = prefs.getString(PREF_VIEW_MODE, null)
        val initialMode = savedMode?.let {
            try { ProductViewMode.valueOf(it) } catch (_: Exception) { null }
        } ?: ProductViewMode.ALPHABETICAL
        _uiState.value = _uiState.value.copy(viewMode = initialMode)

        @OptIn(ExperimentalCoroutinesApi::class)
        viewModelScope.launch {
            _searchQuery.flatMapLatest { query ->
                if (query.isBlank()) {
                    productRepository.getAllProducts()
                } else {
                    productRepository.searchProducts(query)
                }
            }.collect { products ->
                allProducts = products
                _uiState.value = _uiState.value.copy(
                    products = products,
                    isLoading = false
                )
                rebuildSections()
            }
        }
        viewModelScope.launch {
            classifierRepository.getOrderedUnitNames().collect { units ->
                _uiState.value = _uiState.value.copy(unitSuggestions = units)
            }
        }
        viewModelScope.launch {
            combine(
                shoppingListRepository.getAllEntries(),
                shopRepository.getAllShops()
            ) { entries, shops ->
                val shopMap = shops.associateBy { it.id }
                entries.filter { !it.isBought }.associate { entry ->
                    entry.productId to ShoppingListInfo(
                        entryId = entry.id,
                        quantity = entry.quantity,
                        unit = entry.unit,
                        shopName = entry.assignedShopId?.let { shopMap[it]?.name }
                    )
                }
            }.collect { map ->
                _uiState.value = _uiState.value.copy(shoppingListMap = map)
            }
        }
        // Collect classifiers and shop links for grouping
        viewModelScope.launch {
            shopRepository.getAllShops().collect { shops ->
                allShops = shops
                rebuildSections()
            }
        }
        viewModelScope.launch {
            productRepository.getAllShopLinks().collect { links ->
                allLinks = links
                rebuildSections()
            }
        }
        viewModelScope.launch {
            classifierRepository.getAllGroups().collect { groups ->
                allGroups = groups
                rebuildSections()
            }
        }
        viewModelScope.launch {
            classifierRepository.getAllRecipients().collect { recipients ->
                allRecipients = recipients
                rebuildSections()
            }
        }
    }

    fun setViewMode(mode: ProductViewMode) {
        _uiState.value = _uiState.value.copy(viewMode = mode)
        prefs.edit().putString(PREF_VIEW_MODE, mode.name).apply()
        rebuildSections()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    private fun rebuildSections() {
        val products = allProducts
        val mode = _uiState.value.viewMode
        val sections = when (mode) {
            ProductViewMode.ALPHABETICAL -> buildAlphabeticalSections(products)
            ProductViewMode.BY_SHOPS -> buildShopSections(products)
            ProductViewMode.BY_GROUPS -> buildGroupSections(products)
            ProductViewMode.BY_RECIPIENTS -> buildRecipientSections(products)
        }
        _uiState.value = _uiState.value.copy(sections = sections)
    }

    private fun buildAlphabeticalSections(products: List<ProductEntity>): List<ProductSection> {
        if (products.isEmpty()) return emptyList()
        // Group by first letter
        return products
            .sortedBy { it.name.lowercase() }
            .groupBy { it.name.firstOrNull()?.uppercaseChar()?.toString() ?: "#" }
            .map { (letter, items) -> ProductSection(name = letter, products = items) }
    }

    private fun buildShopSections(products: List<ProductEntity>): List<ProductSection> {
        val shopMap = allShops.associateBy { it.id }
        // productId -> list of shopIds
        val productShops = allLinks.groupBy { it.productId }
            .mapValues { (_, links) -> links.map { it.shopId } }

        val sections = mutableListOf<ProductSection>()
        val assignedProductIds = mutableSetOf<Long>()

        // Sections for each shop (ordered by displayOrder)
        for (shop in allShops.sortedBy { it.displayOrder }) {
            val shopProducts = products.filter { product ->
                productShops[product.id]?.contains(shop.id) == true
            }.sortedBy { it.name.lowercase() }
            if (shopProducts.isNotEmpty()) {
                sections.add(ProductSection(name = shop.name, products = shopProducts))
                assignedProductIds.addAll(shopProducts.map { it.id })
            }
        }

        // Products without any shop link
        val unassigned = products.filter { it.id !in assignedProductIds }
            .sortedBy { it.name.lowercase() }
        if (unassigned.isNotEmpty()) {
            sections.add(ProductSection(name = "Без магазина", products = unassigned))
        }

        return sections
    }

    private fun buildGroupSections(products: List<ProductEntity>): List<ProductSection> {
        val groupMap = allGroups.associateBy { it.id }

        val sections = mutableListOf<ProductSection>()

        // Sections for each group (ordered by displayOrder)
        for (group in allGroups.sortedBy { it.displayOrder }) {
            val groupProducts = products.filter { it.productGroupId == group.id }
                .sortedBy { it.name.lowercase() }
            if (groupProducts.isNotEmpty()) {
                sections.add(ProductSection(name = group.name, products = groupProducts))
            }
        }

        // Products without a group
        val ungrouped = products.filter { it.productGroupId == null || groupMap[it.productGroupId] == null }
            .sortedBy { it.name.lowercase() }
        if (ungrouped.isNotEmpty()) {
            sections.add(ProductSection(name = "Без группы", products = ungrouped))
        }

        return sections
    }

    private fun buildRecipientSections(products: List<ProductEntity>): List<ProductSection> {
        val recipientMap = allRecipients.associateBy { it.id }

        val sections = mutableListOf<ProductSection>()

        // Sections for each recipient (ordered by displayOrder)
        for (recipient in allRecipients.sortedBy { it.displayOrder }) {
            val recipientProducts = products.filter { it.recipientId == recipient.id }
                .sortedBy { it.name.lowercase() }
            if (recipientProducts.isNotEmpty()) {
                sections.add(ProductSection(name = recipient.name, products = recipientProducts))
            }
        }

        // Products without a recipient
        val unassigned = products.filter { it.recipientId == null || recipientMap[it.recipientId] == null }
            .sortedBy { it.name.lowercase() }
        if (unassigned.isNotEmpty()) {
            sections.add(ProductSection(name = "Без получателя", products = unassigned))
        }

        return sections
    }

    fun quickAddToList(product: ProductEntity) {
        viewModelScope.launch {
            val qty = product.defaultQuantity ?: 1.0
            val unit = product.defaultUnit ?: "шт"
            val priorityLink: ProductShopLinkEntity? =
                productRepository.getPriorityLink(product.id)

            val entry = ShoppingListEntryEntity(
                productId = product.id,
                quantity = qty,
                unit = unit,
                assignedShopId = priorityLink?.shopId,
                assignedDepartmentId = priorityLink?.departmentId,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            shoppingListRepository.addOrMergeEntry(entry)
        }
    }

    fun incrementQuantity(productId: Long) {
        viewModelScope.launch {
            val entry = shoppingListRepository.findActiveByProductId(productId) ?: return@launch
            val step = allProducts.find { it.id == productId }?.defaultQuantity?.takeIf { it > 0.0 } ?: 1.0
            shoppingListRepository.updateQuantityByProductId(productId, entry.quantity + step)
        }
    }

    fun decrementQuantity(productId: Long) {
        viewModelScope.launch {
            val entry = shoppingListRepository.findActiveByProductId(productId) ?: return@launch
            val step = allProducts.find { it.id == productId }?.defaultQuantity?.takeIf { it > 0.0 } ?: 1.0
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

    companion object {
        private const val PREF_VIEW_MODE = "product_db_view_mode"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SpisokRyadomApp
                val prefs = app.getSharedPreferences("product_db_prefs", 0)
                ProductDatabaseViewModel(
                    app.container.productRepository,
                    app.container.shoppingListRepository,
                    app.container.shopRepository,
                    app.container.classifierRepository,
                    prefs
                )
            }
        }
    }
}
