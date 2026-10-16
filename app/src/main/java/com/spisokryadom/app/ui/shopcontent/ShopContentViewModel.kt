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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flowOn
import com.spisokryadom.app.util.FuzzySearch

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
    val pickProducts: List<PickProductUi> = emptyList(),
    val error:String?=null
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

    private val errors=kotlinx.coroutines.CoroutineExceptionHandler{_,e->_uiState.value=_uiState.value.copy(error=e.message?:"Операция не выполнена")}
    fun clearError(){_uiState.value=_uiState.value.copy(error=null)}
    private var searchJob:Job?=null
    init {
        viewModelScope.launch(errors) {
            val shop = shopRepository.getShopById(shopId)
            _uiState.value = _uiState.value.copy(shop = shop)
        }
        viewModelScope.launch(errors) {
            productRepository.getAllProducts().collect { products ->
                _allProducts.value = products.associateBy { it.id }
            }
        }
        viewModelScope.launch(errors) {
            shopRepository.getAllDepartments().collect { departments ->
                _departments.value = departments.associateBy { it.id }
            }
        }
        viewModelScope.launch(errors) {
            shoppingListRepository.getAllEntries().collect { entries ->
                _shoppingListEntries.value = entries
                    .filter { !it.isBought && it.assignedShopId==shopId }.sortedBy{it.id}.distinctBy{it.productId}
                    .associateBy { it.productId }
            }
        }
        viewModelScope.launch(errors) {
            shopRepository.getAllShops().collect { shops ->
                _shops.value = shops.associateBy { it.id }
            }
        }
        viewModelScope.launch(errors) {
            classifierRepository.getOrderedUnitNames().collect { units ->
                _uiState.value = _uiState.value.copy(unitSuggestions = units)
            }
        }
        viewModelScope.launch(errors) {
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
            }.flowOn(Dispatchers.Default).collect { productUis ->
                _uiState.value = _uiState.value.copy(products = productUis, isLoading = false)
            }
        }
    }

    fun quickAddToShoppingList(product:ProductEntity){viewModelScope.launch(errors){shoppingListRepository.addProduct(product.id,choice=com.spisokryadom.app.data.repository.ShopChoice.EXPLICIT,shopId=shopId)}}
    fun incrementQuantity(productId:Long){viewModelScope.launch(errors){val entry=_shoppingListEntries.value[productId]?:return@launch;shoppingListRepository.adjustQuantity(entry.id,_allProducts.value[productId]?.defaultQuantity?:1.0)}}
    fun decrementQuantity(productId:Long){viewModelScope.launch(errors){val entry=_shoppingListEntries.value[productId]?:return@launch;shoppingListRepository.adjustQuantity(entry.id,-(_allProducts.value[productId]?.defaultQuantity?:1.0))}}
    fun setQuantity(productId:Long,quantity:Double){viewModelScope.launch(errors){val entry=_shoppingListEntries.value[productId]?:return@launch;if(quantity<=0)shoppingListRepository.removeEntry(entry.id)else shoppingListRepository.updateQuantity(entry.id,quantity)}}

    // --- Pick existing products mode ---

    private val _linkedProductIds = MutableStateFlow<Set<Long>>(emptySet())

    init {
        // Track which product IDs are linked to this shop (updates reactively)
        viewModelScope.launch(errors) {
            productRepository.getShopLinksForShop(shopId).collect { links ->
                _linkedProductIds.value = links.map { it.productId }.toSet()
                if(_uiState.value.showPickProducts)refreshPickList()
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
        searchJob?.cancel()
        searchJob=viewModelScope.launch(errors){
            val query=_uiState.value.pickSearchQuery;val all=_allProducts.value.values.toList();val linked=_linkedProductIds.value
            delay(120)
            val result=withContext(Dispatchers.Default){
                val filtered=if(query.isBlank())all.sortedWith(compareBy<ProductEntity>{it.id !in linked}.thenBy{FuzzySearch.normalize(it.name)})else FuzzySearch.rank(all,query,{it.name})
                filtered.map{PickProductUi(it,it.id in linked)}
            }
            _uiState.value=_uiState.value.copy(pickProducts=result)
        }
    }
    fun toggleProductLink(productId:Long){viewModelScope.launch(errors){productRepository.toggleShopLink(productId,shopId)}}

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
