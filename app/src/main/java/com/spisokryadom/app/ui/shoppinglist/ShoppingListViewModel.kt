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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

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
    val isFavorite:Boolean=false,val accent:String="",
    val shopName: String?,
    val departmentName: String?,
    val groupName: String?,
    val recipientName: String?,
    /** Default quantity from the product card — used as the increment/decrement step */
    val defaultQuantity: Double? = null,
    val productGroupId: Long? = null,
    val recipientId: Long? = null, val branchAddress:String?=null,val branchPriority:Int=0,val localDepartmentOrder:Int=Int.MAX_VALUE,val listName:String?=null,val quantityStep:Double=1.0
)

/** Sub-group of entries within a shop, grouped by shop department (группа магазина) */
@Immutable
data class ShopDeptSubGroup(
    val deptId: Long?,
    val deptName: String?,   // null means "no department assigned"
    val entries: List<ShoppingListEntryUi>,
    val branchId:Long?=null,val accent:String="",val iconKey:String=""
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
    val deptSubGroups: List<ShopDeptSubGroup> = emptyList(),
    val branchId: Long? = null,
    val address: String? = null,
    val venueId:Long?=null,val venueName:String?=null,val venueAddress:String?=null,val accent:String="",val iconKey:String="",
    val shoppingBackground:String="",val shopIconKey:String="",val shopLogoUri:String?=null,val customShopName:String?=null,val customShopAddress:String?=null
) {
    /** A retailer ID is a command target; a place key identifies one visible purchase block. */
    val placeKey: String get() = if(customShopName!=null||customShopAddress!=null) "hint:"+java.security.MessageDigest.getInstance("SHA-256").digest((customShopName.orEmpty()+"\u0000"+customShopAddress.orEmpty()).toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)} else "${groupId ?: 0}:${branchId ?: 0}"
    val displayName: String get() = listOfNotNull(groupName, address).joinToString(" · ")
}

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
    val branches=shoppingListRepository.observeBranches().stateIn(viewModelScope,kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),emptyList())
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
                CombinedData(entries=entries, products=products, shops=shops, departments=departments)
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
                .combine(branches){data,points->data.copy(branches=points.associateBy{it.id})}
                .combine(shopRepository.observeBranchOrders()){data,orders->data.copy(branchOrders=orders.associateBy{it.branchId to it.typeId})}
                .combine(shopRepository.venues()){data,venues->data.copy(venues=venues.associateBy{it.id})}
                .combine(shopRepository.departmentTypes()){data,types->data.copy(types=types.associateBy{it.id})}
                .combine(shoppingListRepository.observeGoalLists()){data,lists->data.copy(goalListNames=lists.associate{it.id to it.name})}
                .map(::buildUiState).flowOn(Dispatchers.Default).collect { state ->
                _uiState.value = state.copy(error=_uiState.value.error)
            }
        }
    }

    private data class CombinedData(
        val goalListNames:Map<Long,String> = emptyMap(),
        val venues:Map<Long,com.spisokryadom.app.data.entity.VenueEntity> = emptyMap(),val types:Map<Long,com.spisokryadom.app.data.entity.DepartmentTypeEntity> = emptyMap(),
        val entries: List<ShoppingListEntryEntity> = emptyList(),
        val products: Map<Long, ProductEntity> = emptyMap(),
        val shops: Map<Long, ShopEntity> = emptyMap(),
        val departments: Map<Long, ShopDepartmentEntity> = emptyMap(),
        val groups: Map<Long, ProductGroupEntity> = emptyMap(),
        val recipients: Map<Long, RecipientEntity> = emptyMap(),
        val viewMode: ViewMode = ViewMode.BY_SHOPS,
        val collapsedShopIds: Set<String> = emptySet(),
        val globalShowUrgent: Boolean = true,
        val globalBoostUrgent: Boolean = true,
        val cityId: Long = 1,
        val branchOrders:Map<Pair<Long,Long>,com.spisokryadom.app.data.entity.BranchDepartmentOrderEntity> = emptyMap(),
        val cityNames: Map<Long,String> = emptyMap(), val branches:Map<Long,com.spisokryadom.app.data.entity.ShopBranchEntity> = emptyMap()
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
        val placeLabels=com.spisokryadom.app.domain.PlaceLabels(data.branches.values.toList(),data.venues.values.toList())
        val allEntryUis = data.entries.map { entry ->
            val product = data.products[entry.productId]
            ShoppingListEntryUi(
                entry = entry,
                productName = product?.name ?: "?",listName=entry.listId?.let{data.goalListNames[it]},isFavorite=product?.isFavorite?:false,accent=product?.accent.orEmpty(),
                shopName = entry.assignedShopId?.let { id->data.shops[id]?.let{placeLabels.shop(id,it.name)} } ?: entry.customShopName,
                departmentName = entry.assignedDepartmentId?.let { data.departments[it]?.name },
                groupName = product?.productGroupId?.let { data.groups[it]?.name },
                recipientName = product?.recipientId?.let { data.recipients[it]?.name },
                defaultQuantity = product?.defaultQuantity,
                quantityStep = com.spisokryadom.app.util.Quantities.step(entry.unit, product?.defaultUnit, product?.defaultQuantity),
                branchAddress=data.branches[entry.assignedBranchId]?.address,branchPriority=data.branches[entry.assignedBranchId]?.displayOrder?:0,
                localDepartmentOrder=data.departments[entry.assignedDepartmentId]?.let{dept->data.branchOrders[entry.assignedBranchId to dept.typeId]?.displayOrder?:dept.displayOrder}?:Int.MAX_VALUE,
                productGroupId = product?.productGroupId, recipientId=product?.recipientId
            )
        }

        val entryGroups = when (data.viewMode) {
            ViewMode.BY_SHOPS -> buildShopGroups(allEntryUis, data.shops, data.departments, data.collapsedShopIds, data.globalShowUrgent, data.globalBoostUrgent, data.cityId, data.cityNames)
            ViewMode.BY_GROUPS -> buildProductGroupGroups(allEntryUis, data.groups)
            ViewMode.BY_RECIPIENTS -> buildRecipientGroups(allEntryUis, data.recipients)
            ViewMode.FLAT -> buildFlatGroup(allEntryUis)
        }

        val styled=entryGroups.map { group ->
            val venue=data.branches[group.branchId]?.venueId?.let{data.venues[it]}
            val accent=when(data.viewMode){ViewMode.BY_GROUPS->data.groups[group.groupId]?.accent;ViewMode.BY_RECIPIENTS->data.recipients[group.groupId]?.accent;else->null}.orEmpty()
            val icon=when(data.viewMode){ViewMode.BY_GROUPS->data.groups[group.groupId]?.iconKey;ViewMode.BY_RECIPIENTS->data.recipients[group.groupId]?.iconKey;else->null}.orEmpty()
            group.copy(venueId=venue?.id,venueName=venue?.name,venueAddress=venue?.address,accent=accent,iconKey=icon,
                deptSubGroups=group.deptSubGroups.map{sub->val type=data.departments[sub.deptId]?.typeId?.let{data.types[it]};sub.copy(accent=type?.accent.orEmpty(),iconKey=type?.iconKey.orEmpty())})
        }
        // A market/mall is one trip containing physical outlets, with no duplicated purchases.
        val trips=if(data.viewMode==ViewMode.BY_SHOPS)styled.groupBy{it.venueId?.let{v->"venue_$v"}?:"place_${it.placeKey}"}.values.flatten()else styled

        val boughtCount = allEntryUis.count { it.entry.isBought }

        return ShoppingListUiState(
            groups = trips,
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
        collapsedShopIds: Set<String>,
        globalShowUrgent: Boolean,
        globalBoostUrgent: Boolean,
        cityId: Long, cityNames: Map<Long,String>
    ): List<EntryGroup> {
        val grouped = entries.groupBy { it.entry.assignedShopId }
        val shopOrder = shops.values.sortedWith(compareBy<ShopEntity>{it.cityId!=cityId}.thenBy{it.displayOrder})
        val result = mutableListOf<EntryGroup>()
        for (shop in shopOrder) {
            val points = grouped[shop.id].orEmpty().groupBy { it.entry.assignedBranchId }.values
                .sortedWith(compareBy<List<ShoppingListEntryUi>> { it.first().branchPriority }.thenBy { it.first().entry.assignedBranchId })
            for (rows in points) {
                val branch = rows.first().entry.assignedBranchId
                val placeKey = "${shop.id}:${branch ?: 0}"
                val subs = buildDeptSubGroups(rows, departments)
                val sorted = subs.flatMap { it.entries }
                val collapsed = when {
                    "open:$placeKey" in collapsedShopIds -> false
                    "closed:$placeKey" in collapsedShopIds -> true
                    else -> shop.id.toString() in collapsedShopIds // inherit the old retailer preference
                }
                result.add(EntryGroup(
                    groupId=shop.id,
                    groupName=if(shop.cityId==cityId)shop.name else "${shop.name} · ${cityNames[shop.cityId] ?: "Другой город"}",
                    entries=sorted, deptSubGroups=subs, branchId=branch, address=rows.first().branchAddress,
                    shoppingBackground=shop.shoppingBackground,shopIconKey=shop.iconKey,shopLogoUri=shop.logoUri,
                    hasBoughtEntries=sorted.any { it.entry.isBought }, canClearBought=true,
                    isCollapsed=collapsed, totalCount=sorted.size, boughtCount=sorted.count { it.entry.isBought },
                    showUrgentWhenCollapsed=resolveShowUrgentInCollapsed(shop,globalShowUrgent),
                    urgentCount=sorted.count { it.entry.isUrgent && !it.entry.isBought }
                ))
            }
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

        val noShopEntries = grouped[null].orEmpty()
        noShopEntries.groupBy { it.entry.customShopName to it.entry.customShopAddress }.entries
            .sortedWith(compareBy<Map.Entry<Pair<String?,String?>,List<ShoppingListEntryUi>>>{it.key.first==null&&it.key.second==null}.thenBy{it.key.first}.thenBy{it.key.second})
            .forEach { (hint,rows) ->
                val deptSubGroups=buildDeptSubGroups(rows,departments)
                val sorted=deptSubGroups.flatMap{it.entries}
                val group=EntryGroup(groupId=null,groupName=hint.first?:if(hint.second!=null)"Место покупки"else "Без магазина",
                    address=hint.second?.takeIf{it!=hint.first},entries=sorted,deptSubGroups=deptSubGroups,
                    hasBoughtEntries=sorted.any{it.entry.isBought},canClearBought=true,totalCount=sorted.size,
                    boughtCount=sorted.count{it.entry.isBought},showUrgentWhenCollapsed=globalShowUrgent,
                    urgentCount=sorted.count{it.entry.isUrgent&&!it.entry.isBought},customShopName=hint.first,customShopAddress=hint.second)
                val collapsed=when {
                    "open:${group.placeKey}" in collapsedShopIds->false
                    "closed:${group.placeKey}" in collapsedShopIds->true
                    hint.first==null&&hint.second==null->COLLAPSED_NO_SHOP_ID.toString() in collapsedShopIds
                    else->false
                }
                result.add(group.copy(isCollapsed=collapsed))
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
        return entries.groupBy{it.entry.assignedBranchId to it.entry.assignedDepartmentId}.values
            .sortedWith(compareBy<List<ShoppingListEntryUi>>{it.first().branchPriority}.thenBy{it.first().localDepartmentOrder}.thenBy{it.first().entry.assignedDepartmentId})
            .map{rows->
                val first=rows.first();val dept=departments[first.entry.assignedDepartmentId]?.name
                val name=dept
                ShopDeptSubGroup(first.entry.assignedDepartmentId,name,sortEntries(rows),first.entry.assignedBranchId)
            }
    }

    private fun buildProductGroupGroups(
        entries: List<ShoppingListEntryUi>, classifiers: Map<Long,ProductGroupEntity>
    ): List<EntryGroup> {
        val grouped = entries.groupBy { it.productGroupId }

        val result = mutableListOf<EntryGroup>()

        // Named groups follow the configured priority.
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

        // Named recipients follow the configured priority.
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
                .thenBy { it.branchPriority }.thenBy { it.productName.lowercase() }
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

    private fun purchaseCommand(onRejected: (() -> Unit)? = null, block: suspend () -> Unit) {
        val generation = com.spisokryadom.app.data.repository.ProductEditorTransactions.generation.get()
        viewModelScope.launch(errors) {
            try { com.spisokryadom.app.data.repository.ProductEditorTransactions.inGeneration(generation, block) }
            catch (error: Exception) { if (error is kotlinx.coroutines.CancellationException) throw error; onRejected?.invoke(); throw error }
        }
    }

    fun markBought(entryId: Long) {
        purchaseCommand { shoppingListRepository.markBought(entryId, strictVisible = true) }
    }

    fun markNotBought(entryId: Long) {
        purchaseCommand { shoppingListRepository.markNotBought(entryId, strictVisible = true) }
    }

    fun removeEntry(entryId: Long) {
        purchaseCommand { shoppingListRepository.removeEntry(entryId, strictVisible = true) }
    }

    fun clearBought() {
        purchaseCommand { shoppingListRepository.clearBoughtEntries() }
    }

    fun clearBoughtByShop(shopId: Long?) {
        purchaseCommand {
            if (shopId != null) {
                shoppingListRepository.clearBoughtByShop(shopId)
            } else {
                shoppingListRepository.clearBoughtWithoutShop()
            }
        }
    }

    fun toggleUrgent(entryId: Long) { purchaseCommand{shoppingListRepository.toggleUrgent(entryId,strictPending=true)} }
    suspend fun moveOptions(ids:Set<Long>,shopId:Long)=shoppingListRepository.moveOptions(ids,shopId)
    fun clearError(){_uiState.value=_uiState.value.copy(error=null)}
    fun movePendingToCurrentCity(){purchaseCommand{shoppingListRepository.movePendingToCurrentCity()}}

    fun movePurchases(ids:Set<Long>,shopId:Long?,branchId:Long?,onResult:(Boolean)->Unit) {
        purchaseCommand(onRejected = { onResult(false) }) {try{shoppingListRepository.movePending(ids,shopId,branchId,prefs.getBoolean("remember_shop_priority_on_move",false),strictPending=true);onResult(true)}catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;_uiState.value=_uiState.value.copy(error=e.message);onResult(false)}}
    }
    fun changeShop(entryId: Long, newShopId: Long?, newDepartmentId: Long?) {
        purchaseCommand {
            shoppingListRepository.changeShop(entryId, newShopId, newDepartmentId, strictVisible = true)
        }
    }

    suspend fun getLinkedShopsForProduct(productId: Long): List<ShopEntity> {
        val links = productRepository.getShopLinksForProductSync(productId)
        val shopMap = _shops.value
        val city=shopRepository.locations.currentId()
        return links.mapNotNull { link -> shopMap[link.shopId] }.filter{it.cityId==city}
    }

    fun updatePurchaseDetails(entryId: Long, quantity: Double, note: String?, comment: String?) {
        purchaseCommand {
            shoppingListRepository.updatePurchaseDetails(entryId, quantity, note, comment, strictVisible = true)
        }
    }

    fun increaseQuantity(entryId: Long) {
        purchaseCommand { shoppingListRepository.adjustByDefaultStep(entryId, 1, strictVisible = true) }
    }
    fun decreaseQuantity(entryId: Long) {
        purchaseCommand { shoppingListRepository.adjustByDefaultStep(entryId, -1, strictVisible = true) }
    }

    fun toggleShopCollapsed(group: EntryGroup) {
        val current = _collapsedShopIds.value.toMutableSet()
        current.remove("open:${group.placeKey}")
        current.remove("closed:${group.placeKey}")
        current.add("${if(group.isCollapsed) "open" else "closed"}:${group.placeKey}")
        _collapsedShopIds.value = current
        saveCollapsedShopIds(current)
    }

    fun clearBoughtInPlace(group: EntryGroup) {
        purchaseCommand { shoppingListRepository.clearBoughtInPlace(group.groupId,group.branchId,group.customShopName,group.customShopAddress) }
    }
    suspend fun purchasePlaces(ids:Set<Long>) = shoppingListRepository.purchasePlaces(ids)
    suspend fun additionalPurchasePlaces(ids:Set<Long>)=shoppingListRepository.additionalPurchasePlaces(ids)
    fun createMoveShop(name:String,done:(com.spisokryadom.app.data.repository.ShoppingListRepository.PurchasePlace?)->Unit){purchaseCommand(onRejected = { done(null) }){
        try{val id=shopRepository.createInCurrentCity(name);val shop=shopRepository.getShopById(id)?:error("Магазин удалён");val point=shopRepository.branches(id).firstOrNull();val venueName=point?.venueId?.let{v->shopRepository.venues().first().find{it.id==v}?.name};done(com.spisokryadom.app.data.repository.ShoppingListRepository.PurchasePlace(id,point?.id,shop.name,point?.address,venueName))}
        catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;_uiState.value=_uiState.value.copy(error=e.message);done(null)}
    }}
    fun addPlaceAndMove(ids:Set<Long>,shopId:Long,branchId:Long?,onResult:(Boolean)->Unit){purchaseCommand(onRejected = { onResult(false) }){
        try{shoppingListRepository.movePending(ids,shopId,branchId,prefs.getBoolean("remember_shop_priority_on_move",false),extendAvailability=true,strictPending=true);onResult(true)}
        catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;_uiState.value=_uiState.value.copy(error=e.message);onResult(false)}
    }}
    suspend fun unlinkedShops(productId:Long):List<ShopEntity> {
        val linked=productRepository.getShopLinksForProductSync(productId).map{it.shopId}.toSet()
        return uiState.value.shops.filter{it.id !in linked}
    }
    fun copyToShop(productId:Long,shopId:Long,onResult:(Boolean)->Unit) {
        purchaseCommand(onRejected = { onResult(false) }) {
            try { productRepository.ensureShopLink(productId,shopId);onResult(true) }
            catch(e:Exception) { if(e is kotlinx.coroutines.CancellationException)throw e;_uiState.value=_uiState.value.copy(error=e.message);onResult(false) }
        }
    }

    fun movePlace(group:EntryGroup,target:EntryGroup) {
        val shop=group.groupId?:return;val targetShop=target.groupId?:return
        purchaseCommand{shopRepository.movePurchasePlace(shop,group.branchId,targetShop,target.branchId)}
    }
    fun moveShopUp(shopId:Long){purchaseCommand{shopRepository.moveShop(shopId,-1)}}
    fun moveShopDown(shopId:Long){purchaseCommand{shopRepository.moveShop(shopId,1)}}

    private fun loadCollapsedShopIds(): Set<String> {
        val raw = prefs.getStringSet(PREF_COLLAPSED_SHOPS, null) ?: return emptySet()
        return raw.toSet()
    }

    private fun saveCollapsedShopIds(ids: Set<String>) {
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
