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
import com.spisokryadom.app.util.FuzzySearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ShopLinkUi(
    val link: ProductShopLinkEntity,
    val shopName: String,
    val departmentName: String?
)

data class PendingShopLink(
    val tempId: Long,
    val shopId: Long,
    val shopName: String,
    val departmentId: Long?,
    val departmentName: String?,
    val displayOrder: Int
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
    val priceValue: String = "",
    val priceDate: String = "",
    val shopLinks: List<ShopLinkUi> = emptyList(),
    val pendingShopLinks: List<PendingShopLink> = emptyList(),
    val groups: List<ProductGroupEntity> = emptyList(),
    val recipients: List<RecipientEntity> = emptyList(),
    val shops: List<ShopEntity> = emptyList(),
    val unitSuggestions: List<String> = emptyList(),
    val departmentsForSelectedShop: List<ShopDepartmentEntity> = emptyList(),
    val suggestedDepartmentId: Long? = null,
    val suggestedDepartmentReason: String? = null,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
    val isLoading: Boolean = true
)

class ProductCardViewModel(
    private val productId: Long,
    private val preselectedShopId: Long = -1L,
    private val productRepository: ProductRepository,
    private val shopRepository: ShopRepository,
    private val classifierRepository: ClassifierRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductCardUiState(isNew = productId == -1L))
    val uiState: StateFlow<ProductCardUiState> = _uiState.asStateFlow()

    private val _shopMap = MutableStateFlow<Map<Long, ShopEntity>>(emptyMap())
    private val _departmentCache = mutableMapOf<Long, ShopDepartmentEntity?>()
    /** Original photoUri loaded from DB — used to detect changes and clean up old files on save */
    private var originalPhotoUri: String? = null
    private var nextTempId: Long = 1L
    private var preselectedShopAdded = false

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

                // Auto-add preselected shop as first pending link for new products
                if (_uiState.value.isNew && preselectedShopId > 0 && !preselectedShopAdded) {
                    val shop = shops.find { it.id == preselectedShopId }
                    if (shop != null) {
                        preselectedShopAdded = true
                        _uiState.value = _uiState.value.copy(
                            pendingShopLinks = listOf(
                                PendingShopLink(
                                    tempId = nextTempId++,
                                    shopId = shop.id,
                                    shopName = shop.name,
                                    departmentId = null,
                                    departmentName = null,
                                    displayOrder = 1
                                )
                            )
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            classifierRepository.getOrderedUnitNames().collect { units ->
                _uiState.value = _uiState.value.copy(unitSuggestions = units)
            }
        }

        if (productId != -1L) {
            viewModelScope.launch {
                val product = productRepository.getProductById(productId) ?: return@launch
                originalPhotoUri = product.photoUri
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
                    priceValue = product.priceValue?.let { v ->
                        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
                    } ?: "",
                    priceDate = product.priceDate ?: "",
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

    fun updatePriceValue(value: String) {
        _uiState.value = _uiState.value.copy(priceValue = value)
    }

    fun updatePriceDate(date: String) {
        _uiState.value = _uiState.value.copy(priceDate = date)
    }

    fun loadDepartmentsForShop(shopId: Long) {
        viewModelScope.launch {
            val depts = shopRepository.getDepartmentsByShopIdSync(shopId)

            // Compute smart suggestion based on departments already used in other links
            val usedDeptInfo = getUsedDepartmentInfo()
            var suggestedId: Long? = null
            var suggestedReason: String? = null

            if (usedDeptInfo.isNotEmpty() && depts.isNotEmpty()) {
                // Try exact match first (case-insensitive)
                for ((deptName, shopName) in usedDeptInfo) {
                    val match = depts.find { it.name.equals(deptName, ignoreCase = true) }
                    if (match != null) {
                        suggestedId = match.id
                        suggestedReason = "Совпадает с «$deptName» в $shopName"
                        break
                    }
                }
                // Try fuzzy match if no exact match found
                if (suggestedId == null) {
                    for ((deptName, shopName) in usedDeptInfo) {
                        val matches = FuzzySearch.filterAndSort(depts.map { it.name }, deptName, 0.3)
                        if (matches.isNotEmpty()) {
                            val matchedDept = depts.find { it.name == matches.first() }
                            if (matchedDept != null) {
                                suggestedId = matchedDept.id
                                suggestedReason = "Похоже на «$deptName» в $shopName"
                                break
                            }
                        }
                    }
                }
            }

            _uiState.value = _uiState.value.copy(
                departmentsForSelectedShop = depts,
                suggestedDepartmentId = suggestedId,
                suggestedDepartmentReason = suggestedReason
            )
        }
    }

    /**
     * Returns list of (departmentName, shopName) pairs from all current links
     * for smart department suggestion computation.
     */
    private fun getUsedDepartmentInfo(): List<Pair<String, String>> {
        val state = _uiState.value
        val result = mutableListOf<Pair<String, String>>()

        state.shopLinks.forEach { linkUi ->
            linkUi.departmentName?.let { deptName ->
                result.add(deptName to linkUi.shopName)
            }
        }
        state.pendingShopLinks.forEach { pending ->
            pending.departmentName?.let { deptName ->
                result.add(deptName to pending.shopName)
            }
        }

        return result.distinctBy { it.first.lowercase() }
    }

    // --- Pending shop links (new product) ---

    fun addPendingShopLink(shopId: Long, departmentId: Long?) {
        val shops = _shopMap.value
        val shop = shops[shopId] ?: return
        val pending = _uiState.value.pendingShopLinks
        val maxOrder = pending.maxOfOrNull { it.displayOrder } ?: 0
        val deptName = departmentId?.let { dId ->
            _uiState.value.departmentsForSelectedShop.find { it.id == dId }?.name
        }

        _uiState.value = _uiState.value.copy(
            pendingShopLinks = pending + PendingShopLink(
                tempId = nextTempId++,
                shopId = shopId,
                shopName = shop.name,
                departmentId = departmentId,
                departmentName = deptName,
                displayOrder = maxOrder + 1
            )
        )
    }

    fun removePendingShopLink(tempId: Long) {
        val pending = _uiState.value.pendingShopLinks.filter { it.tempId != tempId }
        // Renumber display orders to keep them sequential
        val renumbered = pending.sortedBy { it.displayOrder }.mapIndexed { index, link ->
            link.copy(displayOrder = index + 1)
        }
        _uiState.value = _uiState.value.copy(pendingShopLinks = renumbered)
    }

    fun updatePendingShopLinkDepartment(tempId: Long, departmentId: Long?) {
        val deptName = departmentId?.let { dId ->
            _uiState.value.departmentsForSelectedShop.find { it.id == dId }?.name
        }
        _uiState.value = _uiState.value.copy(
            pendingShopLinks = _uiState.value.pendingShopLinks.map {
                if (it.tempId == tempId) it.copy(departmentId = departmentId, departmentName = deptName) else it
            }
        )
    }

    fun movePendingShopLinkUp(tempId: Long) {
        val sorted = _uiState.value.pendingShopLinks.sortedBy { it.displayOrder }
        val index = sorted.indexOfFirst { it.tempId == tempId }
        if (index <= 0) return
        val orderA = sorted[index].displayOrder
        val orderB = sorted[index - 1].displayOrder
        val updated = sorted.mapIndexed { i, item ->
            when (i) {
                index -> item.copy(displayOrder = orderB)
                index - 1 -> item.copy(displayOrder = orderA)
                else -> item
            }
        }
        _uiState.value = _uiState.value.copy(pendingShopLinks = updated)
    }

    fun movePendingShopLinkDown(tempId: Long) {
        val sorted = _uiState.value.pendingShopLinks.sortedBy { it.displayOrder }
        val index = sorted.indexOfFirst { it.tempId == tempId }
        if (index < 0 || index >= sorted.size - 1) return
        val orderA = sorted[index].displayOrder
        val orderB = sorted[index + 1].displayOrder
        val updated = sorted.mapIndexed { i, item ->
            when (i) {
                index -> item.copy(displayOrder = orderB)
                index + 1 -> item.copy(displayOrder = orderA)
                else -> item
            }
        }
        _uiState.value = _uiState.value.copy(pendingShopLinks = updated)
    }

    // --- Existing product shop links ---

    fun addShopLink(shopId: Long, departmentId: Long?) {
        if (productId == -1L) return
        viewModelScope.launch {
            val nextPriority = (_uiState.value.shopLinks.maxOfOrNull { it.link.priority } ?: 0) + 1
            productRepository.insertShopLink(
                ProductShopLinkEntity(
                    productId = productId,
                    shopId = shopId,
                    priority = nextPriority,
                    departmentId = departmentId
                )
            )
            renumberShopLinkPriorities()
        }
    }

    fun removeShopLink(link: ProductShopLinkEntity) {
        viewModelScope.launch {
            productRepository.deleteShopLink(link)
            renumberShopLinkPriorities()
        }
    }

    fun updateShopLinkDepartment(link: ProductShopLinkEntity, departmentId: Long?) {
        viewModelScope.launch {
            productRepository.updateShopLink(link.copy(departmentId = departmentId))
            if (departmentId != null) {
                _departmentCache[departmentId] = shopRepository.getDepartmentById(departmentId)
            }
        }
    }

    fun moveShopLinkUp(link: ProductShopLinkEntity) {
        viewModelScope.launch {
            val sorted = _uiState.value.shopLinks.sortedBy { it.link.priority }
            val index = sorted.indexOfFirst { it.link.id == link.id }
            if (index <= 0) return@launch
            val other = sorted[index - 1].link
            // Swap priorities: give each the other's position
            productRepository.updateShopLink(link.copy(priority = index))
            productRepository.updateShopLink(other.copy(priority = index + 1))
            renumberShopLinkPriorities()
        }
    }

    fun moveShopLinkDown(link: ProductShopLinkEntity) {
        viewModelScope.launch {
            val sorted = _uiState.value.shopLinks.sortedBy { it.link.priority }
            val index = sorted.indexOfFirst { it.link.id == link.id }
            if (index < 0 || index >= sorted.size - 1) return@launch
            val other = sorted[index + 1].link
            // Swap priorities: give each the other's position
            productRepository.updateShopLink(link.copy(priority = index + 2))
            productRepository.updateShopLink(other.copy(priority = index + 1))
            renumberShopLinkPriorities()
        }
    }

    /**
     * Renumber all shop link priorities sequentially (1, 2, 3, ...)
     * to guarantee no duplicates and no gaps.
     */
    private suspend fun renumberShopLinkPriorities() {
        if (productId == -1L) return
        val links = productRepository.getShopLinksForProductSync(productId)
        links.sortedBy { it.priority }.forEachIndexed { index, link ->
            val newPriority = index + 1
            if (link.priority != newPriority) {
                productRepository.updateShopLink(link.copy(priority = newPriority))
            }
        }
    }

    // --- General ---

    fun save() {
        val state = _uiState.value
        if (state.name.isBlank()) return

        viewModelScope.launch {
            val newPhotoUri = state.photoUri.trim().ifBlank { null }
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
                photoUri = newPhotoUri,
                priceValue = state.priceValue.trim().ifBlank { null }?.toDoubleOrNull(),
                priceDate = state.priceDate.trim().ifBlank { null }
            )

            if (state.isNew) {
                val newId = productRepository.insertProduct(product)
                // Save all pending shop links with sequential priorities
                for (pending in state.pendingShopLinks.sortedBy { it.displayOrder }) {
                    productRepository.insertShopLink(
                        ProductShopLinkEntity(
                            productId = newId,
                            shopId = pending.shopId,
                            priority = pending.displayOrder,
                            departmentId = pending.departmentId
                        )
                    )
                }
            } else {
                productRepository.updateProduct(product)
            }

            // Clean up old photo file if photoUri changed (replaced or removed)
            val oldPath = originalPhotoUri
            if (oldPath != null && oldPath != newPhotoUri) {
                withContext(Dispatchers.IO) {
                    runCatching { File(oldPath).delete() }
                }
            }

            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }

    fun createGroup(name: String) {
        viewModelScope.launch {
            val id = classifierRepository.insertGroup(ProductGroupEntity(name = name))
            _uiState.value = _uiState.value.copy(productGroupId = id)
        }
    }

    fun deleteProduct() {
        if (_uiState.value.isNew || productId <= 0) return
        viewModelScope.launch {
            val product = productRepository.getProductById(productId) ?: return@launch
            // Удаляем файл фотографии с диска, если он есть
            product.photoUri?.let { path ->
                withContext(Dispatchers.IO) {
                    runCatching { File(path).delete() }
                }
            }
            productRepository.deleteProduct(product)
            _uiState.value = _uiState.value.copy(isDeleted = true)
        }
    }

    companion object {
        fun factory(productId: Long, preselectedShopId: Long = -1L): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SpisokRyadomApp
                ProductCardViewModel(
                    productId = productId,
                    preselectedShopId = preselectedShopId,
                    productRepository = app.container.productRepository,
                    shopRepository = app.container.shopRepository,
                    classifierRepository = app.container.classifierRepository
                )
            }
        }
    }
}
