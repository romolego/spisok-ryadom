package com.spisokryadom.app.ui.productcard

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
import com.spisokryadom.app.data.entity.ShopDepartmentEntity
import com.spisokryadom.app.data.entity.ShopEntity
import com.spisokryadom.app.data.repository.ClassifierRepository
import com.spisokryadom.app.data.repository.ProductRepository
import com.spisokryadom.app.data.repository.ShopRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ShopLinkUi(
    val link: ProductShopLinkEntity,
    val shopName: String,
    val departmentName: String?
)

data class ProductCardUiState(
    val isNew: Boolean = true,
    val name: String = "",
    val productGroupId: Long? = null,
    val recipientId: Long? = null,
    val defaultUnit: String = "",
    val defaultQuantity: String = "",
    val note: String = "",
    val purchaseType: String = "offline",
    val sellerUrl: String = "",
    val productUrl: String = "",
    val photoUri: String = "",
    val shopLinks: List<ShopLinkUi> = emptyList(),
    val groups: List<ProductGroupEntity> = emptyList(),
    val recipients: List<RecipientEntity> = emptyList(),
    val shops: List<ShopEntity> = emptyList(),
    val unitSuggestions: List<String> = emptyList(),
    val departmentsForSelectedShop: List<ShopDepartmentEntity> = emptyList(),
    val isSaved: Boolean = false,
    val isLoading: Boolean = true
)

class ProductCardViewModel(
    private val productId: Long,
    private val productRepository: ProductRepository,
    private val shopRepository: ShopRepository,
    private val classifierRepository: ClassifierRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductCardUiState(isNew = productId == -1L))
    val uiState: StateFlow<ProductCardUiState> = _uiState.asStateFlow()

    private val _shopMap = MutableStateFlow<Map<Long, ShopEntity>>(emptyMap())
    private val _departmentCache = mutableMapOf<Long, ShopDepartmentEntity?>()

    init {
        viewModelScope.launch {
            classifierRepository.getAllGroups().collect { groups ->
                _uiState.value = _uiState.value.copy(groups = groups)
            }
        }
        viewModelScope.launch {
            classifierRepository.getAllRecipients().collect { recipients ->
                _uiState.value = _uiState.value.copy(recipients = recipients)
            }
        }
        viewModelScope.launch {
            shopRepository.getAllShops().collect { shops ->
                _shopMap.value = shops.associateBy { it.id }
                _uiState.value = _uiState.value.copy(shops = shops)
            }
        }
        viewModelScope.launch {
            productRepository.getDistinctUnits().collect { units ->
                _uiState.value = _uiState.value.copy(unitSuggestions = units)
            }
        }

        if (productId != -1L) {
            viewModelScope.launch {
                val product = productRepository.getProductById(productId) ?: return@launch
                _uiState.value = _uiState.value.copy(
                    isNew = false,
                    name = product.name,
                    productGroupId = product.productGroupId,
                    recipientId = product.recipientId,
                    defaultUnit = product.defaultUnit ?: "",
                    defaultQuantity = product.defaultQuantity?.let { q ->
                        if (q == q.toLong().toDouble()) q.toLong().toString() else q.toString()
                    } ?: "",
                    note = product.note ?: "",
                    purchaseType = product.purchaseType,
                    sellerUrl = product.sellerUrl ?: "",
                    productUrl = product.productUrl ?: "",
                    photoUri = product.photoUri ?: "",
                    isLoading = false
                )
            }
            viewModelScope.launch {
                productRepository.getShopLinksForProduct(productId).collect { links ->
                    val shops = _shopMap.value
                    val linkUis = links.map { link ->
                        val deptName = link.departmentId?.let { deptId ->
                            if (!_departmentCache.containsKey(deptId)) {
                                _departmentCache[deptId] = shopRepository.getDepartmentById(deptId)
                            }
                            _departmentCache[deptId]?.name
                        }
                        ShopLinkUi(
                            link = link,
                            shopName = shops[link.shopId]?.name ?: "?",
                            departmentName = deptName
                        )
                    }
                    _uiState.value = _uiState.value.copy(shopLinks = linkUis)
                }
            }
        } else {
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun updateProductGroup(groupId: Long?) {
        _uiState.value = _uiState.value.copy(productGroupId = groupId)
    }

    fun updateRecipient(recipientId: Long?) {
        _uiState.value = _uiState.value.copy(recipientId = recipientId)
    }

    fun updateDefaultUnit(unit: String) {
        _uiState.value = _uiState.value.copy(defaultUnit = unit)
    }

    fun updateDefaultQuantity(qty: String) {
        _uiState.value = _uiState.value.copy(defaultQuantity = qty)
    }

    fun updateNote(note: String) {
        _uiState.value = _uiState.value.copy(note = note)
    }

    fun updatePurchaseType(type: String) {
        _uiState.value = _uiState.value.copy(purchaseType = type)
    }

    fun updateSellerUrl(url: String) {
        _uiState.value = _uiState.value.copy(sellerUrl = url)
    }

    fun updateProductUrl(url: String) {
        _uiState.value = _uiState.value.copy(productUrl = url)
    }

    fun updatePhotoUri(uri: String) {
        _uiState.value = _uiState.value.copy(photoUri = uri)
    }

    fun loadDepartmentsForShop(shopId: Long) {
        viewModelScope.launch {
            val depts = shopRepository.getDepartmentsByShopIdSync(shopId)
            _uiState.value = _uiState.value.copy(departmentsForSelectedShop = depts)
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.name.isBlank()) return

        viewModelScope.launch {
            val product = ProductEntity(
                id = if (state.isNew) 0 else productId,
                name = state.name.trim(),
                productGroupId = state.productGroupId,
                recipientId = state.recipientId,
                defaultUnit = state.defaultUnit.trim().ifBlank { null },
                defaultQuantity = state.defaultQuantity.toDoubleOrNull(),
                note = state.note.trim().ifBlank { null },
                purchaseType = state.purchaseType,
                sellerUrl = state.sellerUrl.trim().ifBlank { null },
                productUrl = state.productUrl.trim().ifBlank { null },
                photoUri = state.photoUri.trim().ifBlank { null }
            )

            if (state.isNew) {
                productRepository.insertProduct(product)
            } else {
                productRepository.updateProduct(product)
            }
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }

    fun addShopLink(shopId: Long, priority: Int, departmentId: Long?) {
        if (productId == -1L) return
        viewModelScope.launch {
            productRepository.insertShopLink(
                ProductShopLinkEntity(
                    productId = productId,
                    shopId = shopId,
                    priority = priority,
                    departmentId = departmentId
                )
            )
        }
    }

    fun removeShopLink(link: ProductShopLinkEntity) {
        viewModelScope.launch {
            productRepository.deleteShopLink(link)
        }
    }

    fun createGroup(name: String) {
        viewModelScope.launch {
            val id = classifierRepository.insertGroup(ProductGroupEntity(name = name))
            _uiState.value = _uiState.value.copy(productGroupId = id)
        }
    }

    fun createRecipient(name: String) {
        viewModelScope.launch {
            val id = classifierRepository.insertRecipient(RecipientEntity(name = name))
            _uiState.value = _uiState.value.copy(recipientId = id)
        }
    }

    companion object {
        fun factory(productId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SpisokRyadomApp
                ProductCardViewModel(
                    productId = productId,
                    productRepository = app.container.productRepository,
                    shopRepository = app.container.shopRepository,
                    classifierRepository = app.container.classifierRepository
                )
            }
        }
    }
}
