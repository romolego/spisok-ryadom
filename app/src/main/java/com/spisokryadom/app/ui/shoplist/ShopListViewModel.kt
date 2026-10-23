package com.spisokryadom.app.ui.shoplist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spisokryadom.app.SpisokRyadomApp
import com.spisokryadom.app.data.entity.ShopEntity
import com.spisokryadom.app.data.repository.ShopRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ShopListGroup(val key:String,val venue:com.spisokryadom.app.data.entity.VenueEntity?,val shops:List<ShopEntity>)

data class ShopListUiState(
    val shops: List<ShopEntity> = emptyList(),
    val venues:List<com.spisokryadom.app.data.entity.VenueEntity> = emptyList(),
    val groups:List<ShopListGroup> = emptyList(),
    val isLoading: Boolean = true,
    val isEditing: Boolean = false,
    val favoritesOnly:Boolean=false,
    val cityId:Long=1,
    val error:String?=null
)

class ShopListViewModel(
    private val shopRepository: ShopRepository
) : ViewModel() {

    private val favorites=MutableStateFlow(false)
    fun toggleFavorites(){favorites.value=!favorites.value}
    fun startEditing(){favorites.value=false;_uiState.value=_uiState.value.copy(isEditing=true)}
    private val generation=com.spisokryadom.app.data.repository.ProductEditorTransactions.generation.get()
    private val _uiState = MutableStateFlow(ShopListUiState())
    val uiState: StateFlow<ShopListUiState> = _uiState.asStateFlow()

    private val errors=kotlinx.coroutines.CoroutineExceptionHandler{_,e->_uiState.value=_uiState.value.copy(error=e.message?:"Операция не выполнена")}
    fun clearError(){_uiState.value=_uiState.value.copy(error=null)}
    private fun command(block:suspend()->Unit){viewModelScope.launch(errors){com.spisokryadom.app.data.repository.ProductEditorTransactions.inGeneration(generation,block)}}
    init {
        viewModelScope.launch(errors) {
            kotlinx.coroutines.flow.combine(shopRepository.getAllShops(),shopRepository.observeBranches(),shopRepository.venues(),shopRepository.locations.activeCity,favorites){allShops,branches,venues,city,only->
                val shops=allShops.filter{it.cityId==city.id&&(!only||it.isFavorite)}
                val shopIds=shops.map{it.id}.toSet()
                val currentVenues=venues.filter{v->v.cityId==city.id&&(!only||branches.any{it.venueId==v.id&&it.shopId in shopIds})}
                fun address(shop:ShopEntity,points:List<com.spisokryadom.app.data.entity.ShopBranchEntity>)=shop.copy(address=points.minWithOrNull(compareBy<com.spisokryadom.app.data.entity.ShopBranchEntity>{it.displayOrder}.thenBy{it.id})?.let{point->com.spisokryadom.app.domain.PlaceLabels(branches,venues).detail(point).takeIf{it.isNotBlank()}})
                val physical=shops.map{shop->address(shop,branches.filter{it.shopId==shop.id})}
                val knownVenues=currentVenues.map{it.id}.toSet()
                val groups=buildList{
                    shops.forEach{shop->
                        val points=branches.filter{it.shopId==shop.id}
                        val outside=points.filter{it.venueId !in knownVenues}
                        if(points.isEmpty()||outside.isNotEmpty())add(ShopListGroup("shop_${shop.id}",null,listOf(address(shop,outside))))
                    }
                    currentVenues.forEach{venue->
                        val local=shops.mapNotNull{shop->val points=branches.filter{it.shopId==shop.id&&it.venueId==venue.id};if(points.isEmpty())null else address(shop,points)}
                        add(ShopListGroup("venue_${venue.id}",venue,local))
                    }
                }.sortedWith(compareBy<ShopListGroup>{it.shops.minOfOrNull{s->s.displayOrder}?:Int.MAX_VALUE}.thenBy{it.venue?.id?:0L}.thenBy{it.key})
                ShopListUiState(shops=physical,venues=currentVenues,groups=groups,isLoading=false,favoritesOnly=only,cityId=city.id)
            }.collect{projection->_uiState.value=projection.copy(isEditing=_uiState.value.isEditing,error=_uiState.value.error)}
        }
    }


    suspend fun saveVenue(venue:com.spisokryadom.app.data.entity.VenueEntity){com.spisokryadom.app.data.repository.ProductEditorTransactions.inGeneration(generation){shopRepository.updateVenue(venue)}}
    fun removeVenue(id:Long){viewModelScope.launch(errors){com.spisokryadom.app.data.repository.ProductEditorTransactions.inGeneration(generation){shopRepository.deleteVenue(id)}}}
    fun toggleEditing() {
        _uiState.value = _uiState.value.copy(isEditing = !_uiState.value.isEditing)
    }

    fun moveShopUp(shop:ShopEntity){command{shopRepository.moveShop(shop.id,-1)}}
    fun moveShopDown(shop:ShopEntity){command{shopRepository.moveShop(shop.id,1)}}
    fun moveFavoriteUp(shop:ShopEntity){command{shopRepository.moveFavoriteShop(shop.id,-1)}}
    fun moveFavoriteDown(shop:ShopEntity){command{shopRepository.moveFavoriteShop(shop.id,1)}}

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SpisokRyadomApp
                ShopListViewModel(app.container.shopRepository)
            }
        }
    }
}
