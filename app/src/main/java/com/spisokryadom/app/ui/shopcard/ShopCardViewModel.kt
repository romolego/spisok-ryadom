package com.spisokryadom.app.ui.shopcard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spisokryadom.app.SpisokRyadomApp
import com.spisokryadom.app.data.entity.ShopDepartmentEntity
import com.spisokryadom.app.data.entity.ShopEntity
import com.spisokryadom.app.data.repository.ShopRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Tri-state for per-shop urgency overrides:
 * USE_GLOBAL = inherit the global default,
 * YES / NO = local override.
 */
enum class UrgencyOverride {
    USE_GLOBAL, YES, NO;

    val label: String
        get() = when (this) {
            USE_GLOBAL -> "По общей настройке"
            YES -> "Да"
            NO -> "Нет"
        }
}

data class PendingDepartment(
    val tempId: Long,
    val name: String,
    val displayOrder: Int
)

data class ShopCardUiState(
    val isNew: Boolean = true,
    val name: String = "",
    val address: String = "",
    val note: String = "",
    val displayOrder: String = "0",
    val cityId: Long = 1,
    val networkId:Long?=null,
    val shoppingBackground:String="",
    val purchaseMode:String="inherit",
    val iconKey:String="",val logoUri:String?=null,
    val cities:List<com.spisokryadom.app.data.entity.CityEntity> = emptyList(),
    val networkName:String="",
    val networks:List<com.spisokryadom.app.data.entity.RetailNetworkEntity> = emptyList(),
    val venues:List<com.spisokryadom.app.data.entity.VenueEntity> = emptyList(),
    val venueNames:Map<Long,String> = emptyMap(),
    val branchOrders:Map<Long,List<Long>> = emptyMap(),
    val branches:List<com.spisokryadom.app.data.entity.ShopBranchEntity> = emptyList(),
    val departments: List<ShopDepartmentEntity> = emptyList(),
    val pendingDepartments: List<PendingDepartment> = emptyList(),
    val allDepartmentNames: List<String> = emptyList(),
    val departmentTypes:List<com.spisokryadom.app.data.entity.DepartmentTypeEntity> = emptyList(),
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isPhotoBusy:Boolean=false,
    val error: String? = null,
    /** Per-shop urgency: show urgent in collapsed */
    val showUrgentInCollapsed: UrgencyOverride = UrgencyOverride.USE_GLOBAL,
    /** Per-shop urgency: boost shop with urgent items */
    val boostUrgentShop: UrgencyOverride = UrgencyOverride.USE_GLOBAL
)

class ShopCardViewModel(private val shopId:Long,private val shopRepository:ShopRepository):ViewModel(){
    private val sessionGeneration=com.spisokryadom.app.data.repository.ProductEditorTransactions.generation.get()
    private val _uiState=MutableStateFlow(ShopCardUiState(isNew=shopId==-1L,cityId=shopRepository.locations.activeCity.value.id))
    val uiState:StateFlow<ShopCardUiState> = _uiState.asStateFlow()
    private var currentShopId=shopId
    private var nextTempId=1L
    init {
        viewModelScope.launch{shopRepository.locations.cities.collect{_uiState.value=_uiState.value.copy(cities=it)}}
        viewModelScope.launch{shopRepository.departmentTypes().collect{_uiState.value=_uiState.value.copy(departmentTypes=it)}}
        command {
            viewModelScope.launch { shopRepository.networks().collect{_uiState.value=_uiState.value.copy(networks=it)} }
            viewModelScope.launch { shopRepository.venues().collect{_uiState.value=_uiState.value.copy(venues=it)} }
            if(shopId>0){
                val shop=shopRepository.getShopById(shopId)?:error("Магазин удалён")
                val branches=shopRepository.branches(shopId)
                val departments=shopRepository.getDepartmentsByShopIdSync(shopId)
                val orders=shopRepository.branchOrders().groupBy{it.branchId}.mapValues{it.value.sortedBy{it.displayOrder}.map{it.typeId}}
                _uiState.value=_uiState.value.copy(isNew=false,name=shop.name,iconKey=shop.iconKey,logoUri=shop.logoUri,purchaseMode=shopRepository.purchaseMode(shop.id),networkId=shop.networkId,shoppingBackground=shop.shoppingBackground,branchOrders=orders,address=shop.address.orEmpty(),note=shop.note.orEmpty(),cityId=shop.cityId,
                    displayOrder=shop.displayOrder.toString(),showUrgentInCollapsed=booleanToOverride(shop.showUrgentInCollapsed),boostUrgentShop=booleanToOverride(shop.boostUrgentShop),
                    branches=branches,departments=departments,isLoading=false)
            }else {val city=shopRepository.locations.currentId();_uiState.value=_uiState.value.copy(cityId=city,isLoading=false)}
        }
        viewModelScope.launch { shopRepository.getDistinctDepartmentNames().collect{names->_uiState.value=_uiState.value.copy(allDepartmentNames=names)} }
    }
    private fun command(block:suspend()->Unit){viewModelScope.launch{try{com.spisokryadom.app.data.repository.ProductEditorTransactions.inGeneration(sessionGeneration,block)}catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;_uiState.value=_uiState.value.copy(error=e.message?:"Операция не выполнена",isSaving=false,isLoading=false)}}}
    private fun booleanToOverride(value:Boolean?)=when(value){null->UrgencyOverride.USE_GLOBAL;true->UrgencyOverride.YES;false->UrgencyOverride.NO}
    private fun overrideToBoolean(value:UrgencyOverride):Boolean?=when(value){UrgencyOverride.USE_GLOBAL->null;UrgencyOverride.YES->true;UrgencyOverride.NO->false}
    fun clearError(){_uiState.value=_uiState.value.copy(error=null)}
    fun updatePurchaseMode(value:String){if(value in com.spisokryadom.app.util.PurchaseModes.labels)_uiState.value=_uiState.value.copy(purchaseMode=value)}
    fun updateName(value:String){_uiState.value=_uiState.value.copy(name=value)}
    fun updateCity(id:Long){if(id==_uiState.value.cityId)return;_uiState.value=_uiState.value.copy(cityId=id,branches=_uiState.value.branches.map{it.copy(venueId=null)},venueNames=emptyMap())}
    fun updateNetwork(id:Long?){_uiState.value=_uiState.value.copy(networkId=id)}
    fun updateNetworkName(name:String){_uiState.value=_uiState.value.copy(networkName=name)}
    fun updateVenueName(branchId:Long,name:String){_uiState.value=_uiState.value.copy(venueNames=_uiState.value.venueNames+(branchId to name))}
    fun convertToVenue(){if(!_uiState.value.isNew)saveDraft(convert=true)}

    fun updateBranches(value:List<com.spisokryadom.app.data.entity.ShopBranchEntity>){_uiState.value=_uiState.value.copy(branches=value)}
    fun updateBranchOrder(branchId:Long,values:List<Long>){_uiState.value=_uiState.value.copy(branchOrders=_uiState.value.branchOrders+(branchId to values))}
    fun updatePhotoBusy(value:Boolean){_uiState.value=_uiState.value.copy(isPhotoBusy=value)}
    fun updateNote(value:String){_uiState.value=_uiState.value.copy(note=value)}
    fun updateIcon(value:String){_uiState.value=_uiState.value.copy(iconKey=value,logoUri=null)}
    fun updateLogo(value:String?){_uiState.value=_uiState.value.copy(logoUri=value,iconKey=if(value==null)_uiState.value.iconKey else "")}
    fun updateShoppingBackground(value:String){if(com.spisokryadom.app.util.AppearanceColors.validOptional(value))_uiState.value=_uiState.value.copy(shoppingBackground=value)}
    fun updateShowUrgentInCollapsed(value:UrgencyOverride){_uiState.value=_uiState.value.copy(showUrgentInCollapsed=value)}
    fun updateBoostUrgentShop(value:UrgencyOverride){_uiState.value=_uiState.value.copy(boostUrgentShop=value)}
    fun save()=saveDraft(convert=false)
    private fun saveDraft(convert:Boolean){
        val state=_uiState.value;if(state.isSaving||state.isPhotoBusy||state.name.isBlank()||state.isLoading)return
        _uiState.value=state.copy(isSaving=true,error=null)
        command {
            val old=if(state.isNew)null else shopRepository.getShopById(currentShopId)?:error("Магазин удалён")
            val order=if(state.isNew)(shopRepository.getAllShopsSync().filter{it.cityId==state.cityId}.maxOfOrNull{it.displayOrder}?:0)+1 else state.displayOrder.toIntOrNull()?:0
            val shop=ShopEntity(id=old?.id?:0,networkId=state.networkId,shoppingBackground=state.shoppingBackground,iconKey=state.iconKey,logoUri=state.logoUri,name=state.name.trim(),address=state.address.trim().ifBlank{null},note=state.note.trim().ifBlank{null},cityId=state.cityId,displayOrder=order,
                showUrgentInCollapsed=overrideToBoolean(state.showUrgentInCollapsed),boostUrgentShop=overrideToBoolean(state.boostUrgentShop),
                manualOrderOverride=if(old?.boostUrgentShop==overrideToBoolean(state.boostUrgentShop))old?.manualOrderOverride?:false else false)
            val departments=if(state.isNew)state.pendingDepartments.sortedBy{it.displayOrder}.map{ShopDepartmentEntity(shopId=0,name=it.name,displayOrder=it.displayOrder)}else state.departments.sortedBy{it.displayOrder}
            currentShopId=shopRepository.saveWithDepartments(shop,departments,state.branches,state.networkName,state.venueNames,state.branchOrders.filterKeys{id->state.branches.any{it.id==id}}.mapValues{(_,types)->types.filter{type->state.departments.any{it.typeId==type}}},convertToVenue=convert,purchaseMode=state.purchaseMode)
            _uiState.value=_uiState.value.copy(isSaving=false,isSaved=true)
        }
    }
    fun addDepartments(names:List<String>){
        val state=_uiState.value;val unique=names.map{it.trim()}.filter{it.isNotBlank()}.distinctBy{com.spisokryadom.app.util.FuzzySearch.normalize(it)}
        if(state.isNew){val old=state.pendingDepartments;val keys=old.map{com.spisokryadom.app.util.FuzzySearch.normalize(it.name)}.toSet();_uiState.value=state.copy(pendingDepartments=old+unique.filter{com.spisokryadom.app.util.FuzzySearch.normalize(it) !in keys}.mapIndexed{i,name->PendingDepartment(nextTempId++,name,old.size+i+1)})}
        else {val old=state.departments;val keys=old.map{com.spisokryadom.app.util.FuzzySearch.normalize(it.name)}.toSet();_uiState.value=state.copy(departments=old+unique.filter{com.spisokryadom.app.util.FuzzySearch.normalize(it) !in keys}.mapIndexed{i,name->ShopDepartmentEntity(id=-(nextTempId++),shopId=currentShopId,name=name,displayOrder=old.size+i+1)})}
    }
    fun updateDepartment(dept:ShopDepartmentEntity){_uiState.value=_uiState.value.copy(departments=_uiState.value.departments.map{if(it.id==dept.id)dept else it})}
    fun deleteDepartment(dept:ShopDepartmentEntity){_uiState.value=_uiState.value.copy(departments=_uiState.value.departments.filter{it.id!=dept.id})}
    private fun moveDepartment(dept:ShopDepartmentEntity,delta:Int){val list=_uiState.value.departments.sortedBy{it.displayOrder}.toMutableList();val i=list.indexOfFirst{it.id==dept.id};val j=i+delta;if(i<0||j !in list.indices)return;list.add(j,list.removeAt(i));_uiState.value=_uiState.value.copy(departments=list.mapIndexed{idx,d->d.copy(displayOrder=idx+1)})}
    fun moveDepartmentUp(dept:ShopDepartmentEntity)=moveDepartment(dept,-1)
    fun moveDepartmentDown(dept:ShopDepartmentEntity)=moveDepartment(dept,1)
    fun deletePendingDepartment(id:Long){_uiState.value=_uiState.value.copy(pendingDepartments=_uiState.value.pendingDepartments.filter{it.tempId!=id})}
    fun renamePendingDepartment(id:Long,name:String){_uiState.value=_uiState.value.copy(pendingDepartments=_uiState.value.pendingDepartments.map{if(it.tempId==id)it.copy(name=name)else it})}
    private fun movePending(id:Long,delta:Int){val list=_uiState.value.pendingDepartments.sortedBy{it.displayOrder}.toMutableList();val i=list.indexOfFirst{it.tempId==id};val j=i+delta;if(i<0||j !in list.indices)return;list.add(j,list.removeAt(i));_uiState.value=_uiState.value.copy(pendingDepartments=list.mapIndexed{idx,d->d.copy(displayOrder=idx+1)})}
    fun movePendingDepartmentUp(id:Long)=movePending(id,-1)
    fun movePendingDepartmentDown(id:Long)=movePending(id,1)
    fun sortDepartmentsAlphabetically(){_uiState.value=_uiState.value.copy(departments=_uiState.value.departments.sortedBy{it.name.lowercase()}.mapIndexed{i,d->d.copy(displayOrder=i+1)})}
    fun sortPendingDepartmentsAlphabetically(){_uiState.value=_uiState.value.copy(pendingDepartments=_uiState.value.pendingDepartments.sortedBy{it.name.lowercase()}.mapIndexed{i,d->d.copy(displayOrder=i+1)})}
    fun deleteShop(){if(_uiState.value.isNew||_uiState.value.isSaving)return;_uiState.value=_uiState.value.copy(isSaving=true);command{shopRepository.getShopById(currentShopId)?.let{shopRepository.deleteShop(it)};_uiState.value=_uiState.value.copy(isSaving=false,isDeleted=true)}}
    companion object{fun factory(shopId:Long):ViewModelProvider.Factory=viewModelFactory{initializer{val app=this[APPLICATION_KEY] as SpisokRyadomApp;ShopCardViewModel(shopId,app.container.shopRepository)}}}
}
