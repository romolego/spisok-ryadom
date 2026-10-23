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
import kotlinx.coroutines.flow.first
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
    val displayOrder: Int,
    val typeId:Long=0
)

data class ShopCardUiState(
    val isNew: Boolean = true,
    val name: String = "",
    val address: String = "",
    val note: String = "",
    val displayOrder: String = "0",
    val cityId: Long = 1,
    val networkId:Long?=null,
    val shoppingBackground:String="",val purchaseBackground:String="",
    val purchaseMode:String="inherit",
    val iconKey:String="",val logoUri:String?=null,
    val isFavorite:Boolean=false,
    val cities:List<com.spisokryadom.app.data.entity.CityEntity> = emptyList(),
    val networkName:String="",
    val networks:List<com.spisokryadom.app.data.entity.RetailNetworkEntity> = emptyList(),
    val venues:List<com.spisokryadom.app.data.entity.VenueEntity> = emptyList(),
    val venueEdits:Map<Long,com.spisokryadom.app.data.entity.VenueEntity> = emptyMap(),
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
    private var cityGroupKey=java.util.UUID.randomUUID().toString()
    private val cityDrafts=linkedMapOf<Long,ShopCardUiState>()
    private val cityProfileIds=mutableMapOf<Long,Long>()
    private var commonDepartments:List<ShopDepartmentEntity>?=null
    private val pendingNetworkNames=mutableMapOf<Long,String>()
    private fun visibleNetworks(values:List<com.spisokryadom.app.data.entity.RetailNetworkEntity>)=
        values.map{it.copy(name=pendingNetworkNames[it.id]?:it.name)}
    private fun departmentSpecs(state:ShopCardUiState):List<ShopDepartmentEntity> =
        if(state.isNew)state.pendingDepartments.sortedBy{it.displayOrder}.map{ShopDepartmentEntity(id=it.tempId,shopId=0,name=it.name,displayOrder=it.displayOrder,typeId=it.typeId)}
        else state.departments.sortedBy{it.displayOrder}
    private fun projectDepartments(state:ShopCardUiState,specs:List<ShopDepartmentEntity>,sameOwner:Boolean=false):ShopCardUiState{
        fun sameType(id:Long,name:String,spec:ShopDepartmentEntity)=
            (spec.typeId>0&&id==spec.typeId)||com.spisokryadom.app.util.FuzzySearch.normalize(name)==com.spisokryadom.app.util.FuzzySearch.normalize(spec.name)
        return if(state.isNew)state.copy(pendingDepartments=specs.mapIndexed{i,spec->
            val old=state.pendingDepartments.find{(sameOwner&&it.tempId==spec.id)||sameType(it.typeId,it.name,spec)}
            PendingDepartment(old?.tempId?:nextTempId++,spec.name,i+1,spec.typeId)
        })else state.copy(departments=specs.mapIndexed{i,spec->
            val old=state.departments.find{(sameOwner&&it.id==spec.id)||sameType(it.typeId,it.name,spec)}
            spec.copy(id=old?.id?:-(nextTempId++),shopId=cityProfileIds[state.cityId]?:currentShopId,displayOrder=i+1)
        })
    }
    private fun shared(state:ShopCardUiState):ShopCardUiState{
        val named=state.copy(networks=visibleNetworks(state.networks),networkName=pendingNetworkNames[state.networkId]?:state.networkName)
        return commonDepartments?.let{projectDepartments(named,it)}?:named
    }
    private fun setDepartments(specs:List<ShopDepartmentEntity>){
        _uiState.value=projectDepartments(_uiState.value,specs,sameOwner=true)
        commonDepartments=departmentSpecs(_uiState.value).map{it.copy(id=0,shopId=0)}
    }
    private suspend fun loadProfile(shop:ShopEntity){
        currentShopId=shop.id;cityProfileIds[shop.cityId]=shop.id
        val networks=visibleNetworks(shopRepository.networks().first());val venues=shopRepository.venues().first()
        val types=shopRepository.departmentTypes().first();val names=shopRepository.getDistinctDepartmentNames().first()
        _uiState.value=_uiState.value.copy(networks=networks,venues=venues,departmentTypes=types,allDepartmentNames=names,isNew=false,name=shop.name,iconKey=shop.iconKey,logoUri=shop.logoUri,
            purchaseMode=shopRepository.purchaseMode(shop.id),networkId=shop.networkId,networkName=pendingNetworkNames[shop.networkId].orEmpty(),
            shoppingBackground=shop.shoppingBackground,purchaseBackground=shop.purchaseBackground,address=shop.address.orEmpty(),
            note=shop.note.orEmpty(),cityId=shop.cityId,isFavorite=shop.isFavorite,displayOrder=shop.displayOrder.toString(),
            showUrgentInCollapsed=booleanToOverride(shop.showUrgentInCollapsed),boostUrgentShop=booleanToOverride(shop.boostUrgentShop),
            branches=shopRepository.branches(shop.id),departments=shopRepository.getDepartmentsByShopIdSync(shop.id),
            pendingDepartments=emptyList(),venueEdits=emptyMap(),isLoading=false,error=null)
        _uiState.value=shared(_uiState.value)
    }
    init {
        viewModelScope.launch{shopRepository.locations.cities.collect{_uiState.value=_uiState.value.copy(cities=it)}}
        viewModelScope.launch{shopRepository.departmentTypes().collect{_uiState.value=_uiState.value.copy(departmentTypes=it)}}
        command {
            viewModelScope.launch { shopRepository.networks().collect{_uiState.value=_uiState.value.copy(networks=visibleNetworks(it))} }
            viewModelScope.launch { shopRepository.venues().collect{_uiState.value=_uiState.value.copy(venues=it)} }
            if(shopId>0){
                val shop=shopRepository.getShopById(shopId)?:error("Магазин удалён")
                if(shop.cityGroupKey.isNotEmpty())cityGroupKey=shop.cityGroupKey
                shopRepository.cityProfiles(shopId).forEach{cityProfileIds[it.cityId]=it.id}
                loadProfile(shop)
            }else {val city=shopRepository.locations.currentId();_uiState.value=_uiState.value.copy(cityId=city,networks=shopRepository.networks().first(),venues=shopRepository.venues().first(),departmentTypes=shopRepository.departmentTypes().first(),allDepartmentNames=shopRepository.getDistinctDepartmentNames().first(),isLoading=false)}
        }
        viewModelScope.launch { shopRepository.getDistinctDepartmentNames().collect{names->_uiState.value=_uiState.value.copy(allDepartmentNames=names)} }
    }
    private fun command(block:suspend()->Unit){viewModelScope.launch{try{com.spisokryadom.app.data.repository.ProductEditorTransactions.inGeneration(sessionGeneration,block)}catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;_uiState.value=_uiState.value.copy(error=e.message?:"Операция не выполнена",isSaving=false,isLoading=false)}}}
    private fun booleanToOverride(value:Boolean?)=when(value){null->UrgencyOverride.USE_GLOBAL;true->UrgencyOverride.YES;false->UrgencyOverride.NO}
    private fun overrideToBoolean(value:UrgencyOverride):Boolean?=when(value){UrgencyOverride.USE_GLOBAL->null;UrgencyOverride.YES->true;UrgencyOverride.NO->false}
    fun clearError(){_uiState.value=_uiState.value.copy(error=null)}
    fun updatePurchaseMode(value:String){if(value in com.spisokryadom.app.util.PurchaseModes.labels)_uiState.value=_uiState.value.copy(purchaseMode=value)}
    fun updateName(value:String){_uiState.value=_uiState.value.copy(name=value)}
    fun updateFavorite(value:Boolean){_uiState.value=_uiState.value.copy(isFavorite=value)}
    fun updateCity(id:Long){
        val before=_uiState.value
        if(id==before.cityId||before.isLoading||before.isSaving||before.isPhotoBusy)return
        cityDrafts[before.cityId]=before
        _uiState.value=before.copy(isLoading=true)
        command{
            require(shopRepository.locations.exists(id)){"Город удалён"}
            val cached=cityDrafts[id]
            if(cached!=null){
                currentShopId=cityProfileIds[id]?:-1L
                _uiState.value=shared(cached.copy(cities=_uiState.value.cities,networks=_uiState.value.networks,venues=_uiState.value.venues,
                    departmentTypes=_uiState.value.departmentTypes,allDepartmentNames=_uiState.value.allDepartmentNames,isLoading=false,error=null))
            }else{
                val peer=if(shopId>0)shopRepository.cityProfiles(shopId).find{it.cityId==id}else null
                if(peer!=null)loadProfile(peer)else{
                    currentShopId=-1L
                    val groups=departmentSpecs(before)
                    _uiState.value=shared(before.copy(isNew=true,cityId=id,address="",branches=emptyList(),venueEdits=emptyMap(),departments=emptyList(),
                        pendingDepartments=groups.mapIndexed{i,group->PendingDepartment(nextTempId++,group.name,i+1,group.typeId)},
                        isLoading=false,error=null,displayOrder="0"))
                }
            }
        }
    }
    fun updateNetwork(id:Long?){_uiState.value=_uiState.value.copy(networkId=id,networkName=pendingNetworkNames[id].orEmpty())}
    fun updateNetworkName(name:String){
        val id=_uiState.value.networkId
        if(id!=null&&name.isNotBlank()){
            pendingNetworkNames[id]=name
            cityDrafts.replaceAll{_,draft->if(draft.networkId==id)draft.copy(networkName=name)else draft}
        }
        _uiState.value=_uiState.value.copy(networkName=pendingNetworkNames[id]?:name,networks=visibleNetworks(_uiState.value.networks))
    }
    fun updateVenue(value:com.spisokryadom.app.data.entity.VenueEntity){_uiState.value=_uiState.value.copy(venueEdits=_uiState.value.venueEdits+(value.id to value.copy(cityId=_uiState.value.cityId)))}
    fun convertToVenue(kind:String=com.spisokryadom.app.data.entity.VenueKinds.UNSPECIFIED){if(!_uiState.value.isNew)saveDraft(convert=true,venueKind=kind)}

    fun updateBranches(value:List<com.spisokryadom.app.data.entity.ShopBranchEntity>){_uiState.value=_uiState.value.copy(branches=value.mapIndexed{i,point->point.copy(displayOrder=i)})}
    fun updatePhotoBusy(value:Boolean){_uiState.value=_uiState.value.copy(isPhotoBusy=value)}
    fun updateNote(value:String){_uiState.value=_uiState.value.copy(note=value)}
    fun updateIcon(value:String){_uiState.value=_uiState.value.copy(iconKey=value,logoUri=null)}
    fun updateLogo(value:String?){_uiState.value=_uiState.value.copy(logoUri=value,iconKey=if(value==null)_uiState.value.iconKey else "")}
    fun updatePurchaseBackground(value:String){if(com.spisokryadom.app.util.PurchaseBackground.valid(value))_uiState.value=_uiState.value.copy(purchaseBackground=value)}
    fun updateShoppingBackground(value:String){if(com.spisokryadom.app.util.AppearanceColors.validOptional(value))_uiState.value=_uiState.value.copy(shoppingBackground=value)}
    fun updateShowUrgentInCollapsed(value:UrgencyOverride){_uiState.value=_uiState.value.copy(showUrgentInCollapsed=value)}
    fun updateBoostUrgentShop(value:UrgencyOverride){_uiState.value=_uiState.value.copy(boostUrgentShop=value)}
    fun save()=saveDraft(convert=false)
    private fun saveDraft(convert:Boolean,venueKind:String=com.spisokryadom.app.data.entity.VenueKinds.UNSPECIFIED){
        val state=_uiState.value;if(state.isSaving||state.isPhotoBusy||state.name.isBlank()||state.isLoading)return
        cityDrafts[state.cityId]=state
        val drafts=cityDrafts.values.filter{!it.isNew||it.branches.isNotEmpty()||(shopId<0&&it.cityId==state.cityId)}.map(::shared)
        _uiState.value=state.copy(isSaving=true,error=null)
        command {
            val values=drafts.map{draft->
                val id=cityProfileIds[draft.cityId]?:0L
                val old=if(id>0)shopRepository.getShopById(id)?:error("Магазин удалён")else null
                val boost=overrideToBoolean(draft.boostUrgentShop)
                val shop=ShopEntity(id=id,networkId=draft.networkId,cityGroupKey=cityGroupKey,
                    shoppingBackground=draft.shoppingBackground,purchaseBackground=draft.purchaseBackground,iconKey=draft.iconKey,logoUri=draft.logoUri,
                    name=draft.name.trim(),address=draft.address.trim().ifBlank{null},note=draft.note.trim().ifBlank{null},cityId=draft.cityId,
                    displayOrder=old?.displayOrder?:0,isFavorite=draft.isFavorite,favoriteOrder=old?.favoriteOrder?:0,
                    showUrgentInCollapsed=overrideToBoolean(draft.showUrgentInCollapsed),boostUrgentShop=boost,
                    manualOrderOverride=if(old?.boostUrgentShop==boost)old?.manualOrderOverride?:false else false)
                val departments=if(draft.isNew)draft.pendingDepartments.sortedBy{it.displayOrder}.map{ShopDepartmentEntity(shopId=0,name=it.name,displayOrder=it.displayOrder)}else draft.departments.sortedBy{it.displayOrder}
                com.spisokryadom.app.data.repository.ShopCityDraft(shop,departments,draft.branches,draft.networkName,draft.purchaseMode,
                    draft.venueEdits.filterKeys{key->key>0||draft.branches.any{it.venueId==key}})
            }
            currentShopId=shopRepository.saveCityProfiles(shopId.takeIf{it>0},cityGroupKey,values,state.cityId,convert,venueKind)
            _uiState.value=_uiState.value.copy(isSaving=false,isSaved=true)
        }
    }
    fun addDepartments(names:List<String>){
        val state=_uiState.value;val old=departmentSpecs(state)
        val keys=old.map{com.spisokryadom.app.util.FuzzySearch.normalize(it.name)}.toSet()
        val added=names.map{it.trim()}.filter{it.isNotBlank()}.distinctBy{com.spisokryadom.app.util.FuzzySearch.normalize(it)}
            .filter{com.spisokryadom.app.util.FuzzySearch.normalize(it) !in keys}.map{name->
                val type=state.departmentTypes.find{com.spisokryadom.app.util.FuzzySearch.normalize(it.name)==com.spisokryadom.app.util.FuzzySearch.normalize(name)}
                ShopDepartmentEntity(id=-(nextTempId++),shopId=currentShopId.coerceAtLeast(0),name=name,typeId=type?.id?:0)
            }
        setDepartments(old+added)
    }
    fun updateDepartment(dept:ShopDepartmentEntity){setDepartments(departmentSpecs(_uiState.value).map{if(it.id==dept.id)dept else it})}
    fun deleteDepartment(dept:ShopDepartmentEntity){setDepartments(departmentSpecs(_uiState.value).filter{it.id!=dept.id})}
    private fun moveDepartmentId(id:Long,delta:Int){
        val list=departmentSpecs(_uiState.value).toMutableList();val i=list.indexOfFirst{it.id==id};val j=i+delta
        if(i<0||j !in list.indices)return
        list.add(j,list.removeAt(i));setDepartments(list)
    }
    fun moveDepartmentUp(dept:ShopDepartmentEntity)=moveDepartmentId(dept.id,-1)
    fun moveDepartmentDown(dept:ShopDepartmentEntity)=moveDepartmentId(dept.id,1)
    fun deletePendingDepartment(id:Long){setDepartments(departmentSpecs(_uiState.value).filter{it.id!=id})}
    fun renamePendingDepartment(id:Long,name:String){setDepartments(departmentSpecs(_uiState.value).map{if(it.id==id)it.copy(name=name)else it})}
    fun movePendingDepartmentUp(id:Long)=moveDepartmentId(id,-1)
    fun movePendingDepartmentDown(id:Long)=moveDepartmentId(id,1)
    fun sortDepartmentsAlphabetically(){setDepartments(departmentSpecs(_uiState.value).sortedBy{it.name.lowercase()})}
    fun sortPendingDepartmentsAlphabetically()=sortDepartmentsAlphabetically()
    fun deleteShop(){if(_uiState.value.isNew||_uiState.value.isSaving)return;_uiState.value=_uiState.value.copy(isSaving=true);command{shopRepository.getShopById(currentShopId)?.let{shopRepository.deleteShop(it)};_uiState.value=_uiState.value.copy(isSaving=false,isDeleted=true)}}
    companion object{fun factory(shopId:Long):ViewModelProvider.Factory=viewModelFactory{initializer{val app=this[APPLICATION_KEY] as SpisokRyadomApp;ShopCardViewModel(shopId,app.container.shopRepository)}}}
}
