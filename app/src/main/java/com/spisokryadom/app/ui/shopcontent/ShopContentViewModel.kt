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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
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
    val listShopName: String? = null,
    val purchases:List<com.spisokryadom.app.ui.components.PendingPurchase> = emptyList()
)

private data class PointData(val products:List<ShopProductUi>,val points:List<com.spisokryadom.app.data.entity.ShopBranchEntity>,val availability:List<com.spisokryadom.app.data.entity.ProductBranchAvailabilityEntity>,val entries:List<ShoppingListEntryEntity>,val departments:Map<Long,ShopDepartmentEntity> = emptyMap(),val shops:Map<Long,ShopEntity> = emptyMap())
data class ShopPointSection(val id:Long?,val name:String,val note:String?,val photoUri:String?,val products:List<ShopProductUi>)

data class PickProductUi(
    val product: ProductEntity,
    val isLinked: Boolean
)

data class ShopContentUiState(
    val shop: ShopEntity? = null,
    val departmentTypes:Map<String,com.spisokryadom.app.data.entity.DepartmentTypeEntity> = emptyMap(),
    val products: List<ShopProductUi> = emptyList(),
    val unitSuggestions: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val showPickProducts: Boolean = false,
    val pickSearchQuery: String = "",
    val pickProducts: List<PickProductUi> = emptyList(),
    val sections:List<ShopPointSection> = emptyList(),
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
    private val _shoppingListEntries = MutableStateFlow<Map<Long, List<ShoppingListEntryEntity>>>(emptyMap())
    private val _shops = MutableStateFlow<Map<Long, ShopEntity>>(emptyMap())

    private val errors=kotlinx.coroutines.CoroutineExceptionHandler{_,e->_uiState.value=_uiState.value.copy(error=e.message?:"Операция не выполнена")}
    fun clearError(){_uiState.value=_uiState.value.copy(error=null)}
    private var searchJob:Job?=null
    init {
        viewModelScope.launch(errors){shopRepository.departmentTypes().collect{_uiState.value=_uiState.value.copy(departmentTypes=it.associateBy{t->t.name})}}
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
                    .filter { !it.isBought && it.assignedShopId==shopId }.sortedBy{it.id}.groupBy{it.productId}
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
                    val rows=entryMap[link.productId].orEmpty();val entry = rows.singleOrNull()
                    ShopProductUi(
                        product = product,
                        link = link,
                        departmentName = link.departmentId?.let { departments[it]?.name },
                        isInShoppingList = rows.isNotEmpty(),
                        purchases=com.spisokryadom.app.ui.components.pendingPurchases(rows,shopMap,emptyMap()),
                        listEntryId = entry?.id,
                        listQuantity = entry?.quantity,
                        listUnit = entry?.unit,
                        listShopName = entry?.assignedShopId?.let { shopMap[it]?.name }
                    )
                }
            }.flowOn(Dispatchers.Default).collect { productUis ->
                _uiState.value = _uiState.value.copy(products = productUis.sortedWith(compareBy<ShopProductUi>{_departments.value[it.link.departmentId]?.displayOrder?:Int.MAX_VALUE}.thenByDescending{it.product.isFavorite}.thenBy{FuzzySearch.normalize(it.product.name)}), isLoading = false)
            }
        }
    }

    init {
        viewModelScope.launch(errors){
            combine(_uiState.map{it.products}.distinctUntilChanged(),shopRepository.observeBranches(),productRepository.observeBranchAvailability(),shoppingListRepository.getAllEntries()){products,allPoints,availability,entries->PointData(products,allPoints,availability,entries)}
                .combine(_departments){data,departments->data.copy(departments=departments)}
                .combine(_shops){data,shops->data.copy(shops=shops)}
                .combine(productRepository.observeBranchDepartments()){data,overrides->data to overrides}
                .combine(shopRepository.observeBranchOrders()){(data,overrides),orders->
                val products=data.products;val allPoints=data.points;val availability=data.availability;val entries=data.entries
                val overrideMap=overrides.associateBy{it.branchId to it.productId}
                val pointRoutes=overrides.any{it.shopId==shopId}||orders.any{order->allPoints.any{it.id==order.branchId&&it.shopId==shopId}}
                val orderMap=orders.associateBy{it.branchId to it.typeId}
                val branchMap=allPoints.associateBy{it.id}
                val departmentByType=data.departments.values.filter{it.shopId==shopId}.associateBy{it.typeId}
                val pendingByProduct=entries.filter{!it.isBought&&it.assignedShopId==shopId}.groupBy{it.productId}
                val points=allPoints.filter{it.shopId==shopId}.sortedWith(compareBy<com.spisokryadom.app.data.entity.ShopBranchEntity>{it.displayOrder}.thenBy{it.id})
                val idsByPoint=availability.groupBy{it.branchId}.mapValues{(_,rows)->rows.map{it.productId}.toSet()}
                val pendingByPoint=entries.filter{!it.isBought&&it.assignedShopId==shopId}.groupBy{it.assignedBranchId to it.productId}
                if(points.isEmpty())listOf(ShopPointSection(null,"Все товары",null,null,products)) else {
                    val result=points.map{point->
                        val rows=products.filter{(pointRoutes&&!it.link.restrictBranches)||(it.link.restrictBranches&&it.product.id in idsByPoint[point.id].orEmpty())}.map{row->
                            val rows=pendingByPoint[point.id to row.product.id].orEmpty();val entry=rows.singleOrNull()
                            val group=overrideMap[point.id to row.product.id]?.typeId?.let{type->departmentByType[type]}
                            row.copy(departmentName=group?.name?:row.departmentName,isInShoppingList=rows.isNotEmpty(),listEntryId=entry?.id,listQuantity=entry?.quantity,listUnit=entry?.unit,purchases=com.spisokryadom.app.ui.components.pendingPurchases(rows,data.shops,branchMap))
                        }
                        val sorted=rows.sortedWith(compareBy<ShopProductUi>{row->
                            val type=overrideMap[point.id to row.product.id]?.typeId?:data.departments[row.link.departmentId]?.typeId
                            orderMap[point.id to type]?.displayOrder?:departmentByType[type]?.displayOrder?:Int.MAX_VALUE
                        }.thenByDescending{it.product.isFavorite}.thenBy{FuzzySearch.normalize(it.product.name)})
                        ShopPointSection(point.id,point.address,point.note,point.photoUri,sorted)
                    }.filter{it.products.isNotEmpty()}.toMutableList()
                    val general=if(pointRoutes)emptyList()else products.filter{!it.link.restrictBranches}.map{row->row.copy(purchases=com.spisokryadom.app.ui.components.pendingPurchases(pendingByProduct[row.product.id].orEmpty(),data.shops,branchMap))}
                    if(general.isNotEmpty())result.add(ShopPointSection(null,"Во всех точках",null,null,general))
                    result
                }
            }.flowOn(Dispatchers.Default).collect{sections->_uiState.value=_uiState.value.copy(sections=sections)}
        }
    }

    fun quickAddToShoppingList(product:ProductEntity,branchId:Long?=null){viewModelScope.launch(errors){shoppingListRepository.addProduct(product.id,choice=com.spisokryadom.app.data.repository.ShopChoice.EXPLICIT,shopId=shopId,branchId=branchId)}}
    fun incrementEntry(entryId:Long){viewModelScope.launch(errors){shoppingListRepository.adjustByDefaultStep(entryId,1)}}
    fun decrementEntry(entryId:Long){viewModelScope.launch(errors){shoppingListRepository.adjustByDefaultStep(entryId,-1)}}
    fun setEntryQuantity(entryId:Long,quantity:Double){viewModelScope.launch(errors){if(quantity<=0)shoppingListRepository.removeEntry(entryId)else shoppingListRepository.updateQuantity(entryId,quantity)}}

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
