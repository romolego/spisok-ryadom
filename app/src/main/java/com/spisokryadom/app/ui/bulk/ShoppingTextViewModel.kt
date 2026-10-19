package com.spisokryadom.app.ui.bulk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spisokryadom.app.SpisokRyadomApp
import com.spisokryadom.app.data.repository.ShoppingListRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ShoppingTextState(val text:String="",val loading:Boolean=true,val dirty:Boolean=false,val error:String?=null)

/** A disposable message, with no write path to the basket or persistent drafts. */
class ShoppingTextViewModel(private val basket:ShoppingListRepository):ViewModel() {
    private val state=MutableStateFlow(ShoppingTextState())
    val uiState=state.asStateFlow()
    init { reloadFromBasket() }
    fun text(value:String) { if(!state.value.loading)state.value=state.value.copy(text=value,dirty=true) }
    fun reloadFromBasket() {
        state.value=state.value.copy(loading=true,error=null)
        viewModelScope.launch {
            try { state.value=ShoppingTextState(text=renderShoppingText(basket.textSnapshot()),loading=false) }
            catch(e:Exception) {
                if(e is CancellationException)throw e
                state.value=state.value.copy(loading=false,error=e.message?:"Не удалось получить список")
            }
        }
    }
    companion object {
        val Factory=viewModelFactory { initializer {
            val app=this[APPLICATION_KEY] as SpisokRyadomApp
            ShoppingTextViewModel(app.container.shoppingListRepository)
        } }
    }
}
