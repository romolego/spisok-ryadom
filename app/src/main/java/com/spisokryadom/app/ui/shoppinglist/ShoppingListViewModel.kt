package com.spisokryadom.app.ui.shoppinglist

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
import com.spisokryadom.app.data.entity.ProductGroupEntity
import com.spisokryadom.app.data.entity.RecipientEntity
import com.spisokryadom.app.data.entity.ShopDepartmentEntity
import com.spisokryadom.app.data.entity.ShopEntity
import com.spisokryadom.app.data.entity.ShoppingListEntryEntity
import com.spisokryadom.app.data.repository.ClassifierRepository
import com.spisokryadom.app.data.repository.ProductRepository
import com.spisokryadom.app.data.repository.ShopRepository
import com.spisokryadom.app.data.repository.ShoppingListRepository
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

enum class ViewMode {
    BY_SHOPS, BY_GROUPS, BY_RECIPIENTS, FLAT;

    val label: String
        get() = when (this) {
            BY_SHOPS -> "Магазины"
            BY_GROUPS -> "Группы"
            BY_RECIPIENTS -> "Получатели"
            FLAT -> "Все"
        }
}

@Immutable
data class ShoppingListEntryUi(
    val entry: ShoppingListEntryEntity,
    val productName: String,
    val shopName: String?,
    val departmentName: String?,
    val groupName: String?,
    val recipientName: String?,
    /** Default quantity from the product card — used as the increment/decrement step */
    val defaultQuantity: Double? = null,
    val productGroupId: Long? = null,
    val recipientId: Long? = null
)

/** Sub-group of entries within a shop, grouped by shop department (группа магазина) */
@Immutable
data class ShopDeptSubGroup(
    val deptId: Long?,
    val deptName: String?,   // null means "no department assigned"
    val entries: List<ShoppingListEntryUi>
)

@Immutable
data class EntryGroup(
    val groupId: Long?,
    val groupName: String,
    val entries: List<ShoppingListEntryUi>,
    val hasBoughtEntries: Boolean,
    val canClearBought: Boolean = false,
    val isCollapsed: Boolean = false,
    val totalCount: Int = 0,
    val boughtCount: Int = 0,
    /** Whether urgent items are shown when this group is collapsed */
    val showUrgentWhenCollapsed: Boolean = true,
    /** Count of urgent non-bought items (for indicator when collapsed and urgent hidden) */
    val urgentCount: Int = 0,
    /** Department sub-groups within a shop block (populated only in BY_SHOPS mode) */
    val deptSubGroups: List<ShopDeptSubGroup> = emptyList()
)

@Immutable
data class ShoppingListUiState(
    val groups: List<EntryGroup> = emptyList(),
    val shops: List<ShopEntity> = emptyList(),
    val totalCount: Int = 0,
    val boughtCount: Int = 0,
    val hasBoughtEntries: Boolean = false,
    val viewMode: ViewMode = ViewMode.BY_SHOPS,
    val foreignPendingCount: Int = 0,
    val error: String? = null,
    val isLoading: Boolean = true
)

class ShoppingListViewModel(
    private val shoppingListRepository: ShoppingListRepository,
    private val productRepository: ProductRepository,
    private val shopRepository: ShopRepository,
    private val classifierRepository: ClassifierRepository,
    private val prefs: SharedPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShoppingListUiState())
    val uiState: StateFlow<ShoppingListUiState> = _uiState.asStateFlow()
    private val errors=CoroutineExceptionHandler{_,e->_uiState.value=_uiState.value.copy(error=e.message?:"Операция не выполнена")}

    private val _products = MutableStateFlow<Map<Long, ProductEntity>>(emptyMap())
    private val _shops = MutableStateFlow<Map<Long, ShopEntity>>(emptyMap())
    private val _departments = MutableStateFlow<Map<Long, ShopDepartmentEntity>>(emptyMap())
    private val _groups = MutableStateFlow<Map<Long, ProductGroupEntity>>(emptyMap())
    private val _recipients = MutableStateFlow<Map<Long, RecipientEntity>>(emptyMap())
    private val _viewMode = MutableStateFlow(loadViewMode())
    private val _collapsedShopIds = MutableStateFlow(loadCollapsedShopIds())
    private val _cachedGlobalShowUrgent = MutableStateFlow(prefs.getBoolean(PREF_GLOBAL_SHOW_URGENT_IN_COLLAPSED, true))
    private val _cachedGlobalBoostUrgent = MutableStateFlow(prefs.getBoolean(PREF_GLOBAL_BOOST_URGENT_SHOP, true))

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            PREF_VIEW_MODE -> _viewMode.value=loadViewMode()
            PREF_COLLAPSED_SHOPS -> _collapsedShopIds.value=loadCollapsedShopIds()
            PREF_GLOBAL_SHOW_URGENT_IN_COLLAPSED ->
                _cachedGlobalShowUrgent.value = prefs.getBoolean(PREF_GLOBAL_SHOW_URGENT_IN_COLLAPSED, true)
            PREF_GLOBAL_BOOST_URGENT_SHOP ->
                _cachedGlobalBoostUrgent.value = prefs.getBoolean(PREF_GLOBAL_BOOST_URGENT_SHOP, true)
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        viewModelScope.launch(errors) {
            productRepository.getAllProductsIncludingTemporary().collect { products ->
                _products.value = products.associateBy { it.id }
            }
        }
        viewModelScope.launch(errors) {
            shopRepository.getAllShops().collect { shops ->
                _shops.value = shops.associateBy { it.id }
            }
        }
        viewModelScope.launch(errors) {
            shopRepository.getAllDepartments().collect { departments ->
                _departments.value = departments.associateBy { it.id }
            }
        }
        viewModelScope.launch(errors) {
            classifierRepository.getAllGroups().collect { groups ->
                _groups.value = groups.associateBy { it.id }
            }
        }
        viewModelScope.launch(errors) {
            classifierRepository.getAllRecipients().collect { recipients ->
                _recipients.value = recipients.associateBy { it.id }
            }
        }
        viewModelScope.launch(errors) {
            combine(
                shoppingListRepository.getAllEntries(),
                _products,
                _shops,
                _departments
            ) { entries, products, shops, departments ->
                CombinedData(entries, products, shops, departments)
            }.combine(_groups) { data, groups ->
                data.copy(groups = groups)
            }.combine(_recipients) { data, recipients ->
                data.copy(recipients = recipients)
            }.combine(_viewMode) { data, viewMode ->
                data.copy(viewMode = viewMode)
            }.combine(_collapsedShopIds) { data, collapsedIds ->
                data.copy(collapsedShopIds = collapsedIds)
            }.combine(_cachedGlobalShowUrgent) { data, showUrgent ->
                data.copy(globalShowUrgent = showUrgent)
            }.combine(_cachedGlobalBoostUrgent) { data, boostUrgent ->
                data.copy(globalBoostUrgent = boostUrgent)
            }.combine(shopRepository.locations.activeCity) { data,city -> data.copy(cityId=city.id) }
                .combine(shopRepository.locations.cities){data,cities->data.copy(cityNames=cities.associate{it.id to it.name})}
                .map(::buildUiState).flowOn(Dispatchers.Default).collect { state ->
                _uiState.value = state.copy(error=_uiState.value.error)
            }
        }
    }

    private data class CombinedData(
        val entries: List<ShoppingListEntryEntity> = emptyList(),
        val products: Map<Long, ProductEntity> = emptyMap(),
        val shops: Map<Long, ShopEntity> = emptyMap(),
        val departments: Map<Long, ShopDepartmentEntity> = emptyMap(),
        val groups: Map<Long, ProductGroupEntity> = emptyMap(),
        val recipients: Map<Long, RecipientEntity> = emptyMap(),
        val viewMode: ViewMode = ViewMode.BY_SHOPS,
        val collapsedShopIds: Set<Long> = emptySet(),
        val globalShowUrgent: Boolean = true,
        val globalBoostUrgent: Boolean = true,
        val cityId: Long = 1,
        val cityNames: Map<Long,String> = emptyMap()
    )

    /** Resolve whether to show urgent items in collapsed state for a given shop */
    private fun resolveShowUrgentInCollapsed(shop: ShopEntity, globalDefault: Boolean): Boolean {
        return shop.showUrgentInCollapsed ?: globalDefault
    }

    /** Resolve whether to boost a shop with urgent items */
    private fun resolveBoostUrgentShop(shop: ShopEntity, globalDefault: Boolean): Boolean {
        return shop.boostUrgentShop ?: globalDefault
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    private fun buildUiState(data: CombinedData): ShoppingListUiState {
        val allEntryUis = data.entries.map { entry ->
            val product = data.products[entry.productId]
            ShoppingListEntryUi(
                entry = entry,
                productName = product?.name ?: "?",
                shopName = entry.assignedShopId?.let { data.shops[it]?.name },
                departmentName = entry.assignedDepartmentId?.let { data.departments[it]?.name },
                groupName = product?.productGroupId?.let { data.groups[it]?.name },
                recipientName = product?.recipientId?.let { data.recipients[it]?.name },
                defaultQuantity = product?.defaultQuantity,
                productGroupId = product?.productGroupId, recipientId=product?.recipientId
            )
        }

        val entryGroups = when (data.viewMode) {
            ViewMode.BY_SHOPS -> buildShopGroups(allEntryUis, data.shops, data.departments, data.collapsedShopIds, data.globalShowUrgent, data.globalBoostUrgent, data.cityId, data.cityNames)
            ViewMode.BY_GROUPS -> buildProductGroupGroups(allEntryUis, data.groups)
            ViewMode.BY_RECIPIENTS -> buildRecipientGroups(allEntryUis, data.recipients)
            ViewMode.FLAT -> buildFlatGroup(allEntryUis)
        }

        val boughtCount = allEntryUis.count { it.entry.isBought }

        return ShoppingListUiState(
            groups = entryGroups,
            shops = data.shops.values.filter{it.cityId==data.cityId}.sortedBy { it.displayOrder },
            foreignPendingCount=allEntryUis.count{!it.entry.isBought&&it.entry.assignedShopId!=null&&data.shops[it.entry.assignedShopId]?.cityId!=data.cityId},
            totalCount = allEntryUis.size,
            boughtCount = boughtCount,
            hasBoughtEntries = boughtCount > 0,
            viewMode = data.viewMode,
            isLoading = false
        )
    }

    private fun buildShopGroups(
        entries: List<ShoppingListEntryUi>,
        shops: Map<Long, ShopEntity>,
        departments: Map<Long, ShopDepartmentEntity>,
        collapsedShopIds: Set<Long>,
        globalShowUrgent: Boolean,
        globalBoostUrgent: Boolean,
        cityId: Long, cityNames: Map<Long,String>
    ): List<EntryGroup> {
        val grouped = entries.groupBy { it.entry.assignedShopId }
        val shopOrder = shops.values.sortedWith(compareBy<ShopEntity>{it.cityId!=cityId}.thenBy{it.displayOrder})
        val result = mutableListOf<EntryGroup>()

        for (shop in shopOrder) {
            val groupEntries = grouped[shop.id] ?: continue
            val deptSubGroups = buildDeptSubGroups(groupEntries, departments)
            val sorted = deptSubGroups.flatMap { it.entries }
            val boughtCount = sorted.count { it.entry.isBought }
            val urgentCount = sorted.count { it.entry.isUrgent && !it.entry.isBought }
            val showUrgentWhenCollapsed = resolveShowUrgentInCollapsed(shop, globalShowUrgent)
            result.add(EntryGroup(
                groupId = shop.id,
                groupName = if(shop.cityId==cityId)shop.name else "${shop.name} · ${cityNames[shop.cityId] ?: "Другой город"}",
                entries = sorted,
                deptSubGroups = deptSubGroups,
                hasBoughtEntries = boughtCount > 0,
                canClearBought = true,
                isCollapsed = shop.id in collapsedShopIds,
                totalCount = sorted.size,
                boughtCount = boughtCount,
                showUrgentWhenCollapsed = showUrgentWhenCollapsed,
                urgentCount = urgentCount
            ))
        }

        // Apply safe boost: shops with urgent items and boost enabled (and not manually overridden)
        // get sorted before non-boosted shops, preserving relative order within each group
        val boosted = mutableListOf<EntryGroup>()
        val normal = mutableListOf<EntryGroup>()
        for (group in result) {
            val shop = group.groupId?.let { shops[it] }
            val shouldBoost = shop != null
                    && shop.cityId == cityId
                    && group.urgentCount > 0
                    && resolveBoostUrgentShop(shop, globalBoostUrgent)
                    && !shop.manualOrderOverride
            if (shouldBoost) boosted.add(group) else normal.add(group)
        }
        result.clear()
        result.addAll(boosted)
        result.addAll(normal)

        val noShopEntries = grouped[null]
        if (!noShopEntries.isNullOrEmpty()) {
            val deptSubGroups = buildDeptSubGroups(noShopEntries, departments)
            val sorted = deptSubGroups.flatMap { it.entries }
            val boughtCount = sorted.count { it.entry.isBought }
            val urgentCount = sorted.count { it.entry.isUrgent && !it.entry.isBought }
            result.add(EntryGroup(
                groupId = null,
                groupName = "Без магазина",
                entries = sorted,
                deptSubGroups = deptSubGroups,
                hasBoughtEntries = boughtCount > 0,
                canClearBought = true,
                isCollapsed = COLLAPSED_NO_SHOP_ID in collapsedShopIds,
                totalCount = sorted.size,
                boughtCount = boughtCount,
                showUrgentWhenCollapsed = globalShowUrgent,
                urgentCount = urgentCount
            ))
        }
        return result
    }

    /**
     * Groups shop entries by their assigned department, ordered by department displayOrder.
     * Entries without a department are placed last in a separate sub-group.
     */
    private fun buildDeptSubGroups(
        entries: List<ShoppingListEntryUi>,
        departments: Map<Long, ShopDepartmentEntity>
    ): List<ShopDeptSubGroup> {
        val byDept = entries.groupBy { it.entry.assignedDepartmentId }
        val result = mutableListOf<ShopDeptSubGroup>()

        // Named depts sorted by displayOrder, then by id for stability
        val sortedDeptIds = byDept.keys
            .filterNotNull()
            .sortedWith(compareBy({ departments[it]?.displayOrder ?: Int.MAX_VALUE }, { it }))

        for (deptId in sortedDeptIds) {
            val deptEntries = byDept[deptId] ?: continue
            result.add(ShopDeptSubGroup(
                deptId = deptId,
                deptName = departments[deptId]?.name,
                entries = sortEntries(deptEntries)
            ))
        }

        // Entries without a department go last
        val noDeptEntries = byDept[null]
        if (!noDeptEntries.isNullOrEmpty()) {
            result.add(ShopDeptSubGroup(
                deptId = null,
                deptName = null,
                entries = sortEntries(noDeptEntries)
            ))
        }

        return result
    }

    private fun buildProductGroupGroups(
        entries: List<ShoppingListEntryUi>, classifiers: Map<Long,ProductGroupEntity>
    ): List<EntryGroup> {
        val grouped = entries.groupBy { it.productGroupId }

        val result = mutableListOf<EntryGroup>()

        // Named groups sorted alphabetically
        val namedGroups = grouped.filterKeys { it != null }.entries.sortedWith(compareBy({ classifiers[it.key]?.displayOrder ?: Int.MAX_VALUE }, { it.key }))
        for ((groupId, groupEntries) in namedGroups) {
            val sorted = sortEntries(groupEntries)
            result.add(EntryGroup(
                groupId = groupId,
                groupName = classifiers[groupId]?.name ?: "Без группы",
                entries = sorted,
                hasBoughtEntries = sorted.any { it.entry.isBought }
            ))
        }

        // "Без группы"
        val noGroupEntries = grouped[null]
        if (!noGroupEntries.isNullOrEmpty()) {
            val sorted = sortEntries(noGroupEntries)
            result.add(EntryGroup(
                groupId = null,
                groupName = "Без группы",
                entries = sorted,
                hasBoughtEntries = sorted.any { it.entry.isBought }
            ))
        }
        return result
    }

    private fun buildRecipientGroups(
        entries: List<ShoppingListEntryUi>, classifiers: Map<Long,RecipientEntity>
    ): List<EntryGroup> {
        val grouped = entries.groupBy { it.recipientId }
        val result = mutableListOf<EntryGroup>()

        // Named recipients sorted alphabetically
        val namedGroups = grouped.filterKeys { it != null }.entries.sortedWith(compareBy({ classifiers[it.key]?.displayOrder ?: Int.MAX_VALUE }, { it.key }))
        for ((recipientId, groupEntries) in namedGroups) {
            val sorted = sortEntries(groupEntries)
            result.add(EntryGroup(
                groupId = recipientId,
                groupName = classifiers[recipientId]?.name ?: "Без получателя",
                entries = sorted,
                hasBoughtEntries = sorted.any { it.entry.isBought }
            ))
        }

        // "Без получателя"
        val noRecipientEntries = grouped[null]
        if (!noRecipientEntries.isNullOrEmpty()) {
            val sorted = sortEntries(noRecipientEntries)
            result.add(EntryGroup(
                groupId = null,
                groupName = "Без получателя",
                entries = sorted,
                hasBoughtEntries = sorted.any { it.entry.isBought }
            ))
        }
        return result
    }

    private fun buildFlatGroup(entries: List<ShoppingListEntryUi>): List<EntryGroup> {
        if (entries.isEmpty()) return emptyList()
        val sorted = sortEntries(entries)
        return listOf(EntryGroup(
            groupId = null,
            groupName = "Все товары",
            entries = sorted,
            hasBoughtEntries = sorted.any { it.entry.isBought }
        ))
    }

    private fun sortEntries(entries: List<ShoppingListEntryUi>): List<ShoppingListEntryUi> {
        return entries.sortedWith(
            compareBy<ShoppingListEntryUi> { it.entry.isBought }
                .thenByDescending { it.entry.isUrgent }
                .thenBy { it.productName.lowercase() }
        )
    }

    fun setViewMode(mode: ViewMode) {
        _viewMode.value = mode
        prefs.edit().putString(PREF_VIEW_MODE, mode.name).apply()
    }

    private fun loadViewMode(): ViewMode {
        val saved = prefs.getString(PREF_VIEW_MODE, null)
        return try {
            saved?.let { ViewMode.valueOf(it) } ?: ViewMode.BY_SHOPS
        } catch (_: IllegalArgumentException) {
            ViewMode.BY_SHOPS
        }
    }

    fun markBought(entryId: Long) {
        viewModelScope.launch(errors) { shoppingListRepository.markBought(entryId) }
    }

    fun markNotBought(entryId: Long) {
        viewModelScope.launch(errors) { shoppingListRepository.markNotBought(entryId) }
    }

    fun removeEntry(entryId: Long) {
        viewModelScope.launch(errors) { shoppingListRepository.removeEntry(entryId) }
    }

    fun clearBought() {
        viewModelScope.launch(errors) { shoppingListRepository.clearBoughtEntries() }
    }

    fun clearBoughtByShop(shopId: Long?) {
        viewModelScope.launch(errors) {
            if (shopId != null) {
                shoppingListRepository.clearBoughtByShop(shopId)
            } else {
                shoppingListRepository.clearBoughtWithoutShop()
            }
        }
    }

    fun toggleUrgent(entryId: Long) { viewModelScope.launch(errors){shoppingListRepository.toggleUrgent(entryId)} }
    fun clearError(){_uiState.value=_uiState.value.copy(error=null)}
    fun movePendingToCurrentCity(){viewModelScope.launch(errors){shoppingListRepository.movePendingToCurrentCity()}}

    fun changeShop(entryId: Long, newShopId: Long?, newDepartmentId: Long?) {
        viewModelScope.launch(errors) {
            val entry = shoppingListRepository.getEntryById(entryId) ?: return@launch

            var resolvedDeptId = newDepartmentId
            if (resolvedDeptId == null && newShopId != null) {
                val links = productRepository.getShopLinksForProductSync(entry.productId)
                val linkForShop = links.find { it.shopId == newShopId }
                if (linkForShop?.departmentId != null) {
                    resolvedDeptId = linkForShop.departmentId
                } else {
                    val oldDeptName = entry.assignedDepartmentId?.let { oldId ->
                        _departments.value[oldId]?.name
                    }
                    if (oldDeptName != null) {
                        val newShopDepts = shopRepository.getDepartmentsByShopIdSync(newShopId)
                        resolvedDeptId = newShopDepts.find {
                            it.name.equals(oldDeptName, ignoreCase = true)
                        }?.id
                    }
                }
            }

            shoppingListRepository.updateEntry(
                entry.copy(
                    assignedShopId = newShopId,
                    assignedDepartmentId = resolvedDeptId,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun getLinkedShopsForProduct(productId: Long): List<ShopEntity> {
        val links = productRepository.getShopLinksForProductSync(productId)
        val shopMap = _shops.value
        val city=shopRepository.locations.currentId()
        return links.mapNotNull { link -> shopMap[link.shopId] }.filter{it.cityId==city}
    }

    fun updateEntryDetails(entryId: Long, quantity: Double, shopId: Long?, note: String?, comment: String?) {
        viewModelScope.launch(errors) {
            val entry = shoppingListRepository.getEntryById(entryId) ?: return@launch

            var resolvedDeptId = entry.assignedDepartmentId
            if (shopId != entry.assignedShopId) {
                resolvedDeptId = null
                if (shopId != null) {
                    val links = productRepository.getShopLinksForProductSync(entry.productId)
                    val linkForShop = links.find { it.shopId == shopId }
                    if (linkForShop?.departmentId != null) {
                        resolvedDeptId = linkForShop.departmentId
                    } else {
                        val oldDeptName = entry.assignedDepartmentId?.let { oldId ->
                            _departments.value[oldId]?.name
                        }
                        if (oldDeptName != null) {
                            val newShopDepts = shopRepository.getDepartmentsByShopIdSync(shopId)
                            resolvedDeptId = newShopDepts.find {
                                it.name.equals(oldDeptName, ignoreCase = true)
                            }?.id
                        }
                    }
                }
            }

            shoppingListRepository.updateEntry(
                entry.copy(
                    quantity = quantity,
                    assignedShopId = shopId,
                    assignedDepartmentId = resolvedDeptId,
                    note = if (note.isNullOrBlank()) null else note.trim(),
                    comment = if (comment.isNullOrBlank()) null else comment.trim(),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun updateEntry(entry: ShoppingListEntryEntity) {
        viewModelScope.launch(errors) {
            shoppingListRepository.updateEntry(
                entry.copy(updatedAt = System.currentTimeMillis())
            )
        }
    }

    fun increaseQuantity(entryId: Long) { viewModelScope.launch(errors) {
        val entry=shoppingListRepository.getEntryById(entryId) ?: return@launch
        val step=_products.value[entry.productId]?.defaultQuantity?.takeIf{it>0}?:1.0
        shoppingListRepository.adjustQuantity(entryId,step)
    } }
    fun decreaseQuantity(entryId: Long) { viewModelScope.launch(errors) {
        val entry=shoppingListRepository.getEntryById(entryId) ?: return@launch
        val step=_products.value[entry.productId]?.defaultQuantity?.takeIf{it>0}?:1.0
        shoppingListRepository.adjustQuantity(entryId,-step)
    } }

    fun toggleShopCollapsed(groupId: Long?) {
        val key = groupId ?: COLLAPSED_NO_SHOP_ID
        val current = _collapsedShopIds.value.toMutableSet()
        if (key in current) current.remove(key) else current.add(key)
        _collapsedShopIds.value = current
        saveCollapsedShopIds(current)
    }

    fun moveShopUp(shopId:Long){viewModelScope.launch(errors){shopRepository.moveShop(shopId,-1)}}
    fun moveShopDown(shopId:Long){viewModelScope.launch(errors){shopRepository.moveShop(shopId,1)}}

    private fun loadCollapsedShopIds(): Set<Long> {
        val raw = prefs.getStringSet(PREF_COLLAPSED_SHOPS, null) ?: return emptySet()
        return raw.mapNotNull { it.toLongOrNull() }.toSet()
    }

    private fun saveCollapsedShopIds(ids: Set<Long>) {
        prefs.edit().putStringSet(PREF_COLLAPSED_SHOPS, ids.map { it.toString() }.toSet()).apply()
    }

    companion object {
        private const val PREF_VIEW_MODE = "shopping_list_view_mode"
        private const val PREF_COLLAPSED_SHOPS = "shopping_list_collapsed_shops"
        const val PREF_GLOBAL_SHOW_URGENT_IN_COLLAPSED =
            "global_default_show_urgent_in_collapsed"
        const val PREF_GLOBAL_BOOST_URGENT_SHOP =
            "global_default_boost_urgent_shop"
        const val PREF_GLOBAL_SAVE_NEW_PRODUCT_TO_DB =
            "global_default_save_new_product_to_db"
        private const val PREFS_NAME = "shopping_list_prefs"
        const val PREFS_NAME_PUBLIC = PREFS_NAME
        private const val COLLAPSED_NO_SHOP_ID = -999L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SpisokRyadomApp
                val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ShoppingListViewModel(
                    app.container.shoppingListRepository,
                    app.container.productRepository,
                    app.container.shopRepository,
                    app.container.classifierRepository,
                    prefs
                )
            }
        }
    }
}
