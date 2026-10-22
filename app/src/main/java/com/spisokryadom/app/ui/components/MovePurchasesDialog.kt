package com.spisokryadom.app.ui.components
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.spisokryadom.app.data.entity.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MovePurchasesDialog(
 ids:Set<Long>,onMove:(Long?,Long?,(Boolean)->Unit)->Unit,onDismiss:()->Unit,
 loadPlaces:suspend(Set<Long>)->List<com.spisokryadom.app.data.repository.ShoppingListRepository.PurchasePlace>,currentPlaces:Set<Pair<Long?,Long?>> = emptySet(),
 loadAdditionalPlaces:(suspend(Set<Long>)->List<com.spisokryadom.app.data.repository.ShoppingListRepository.PurchasePlace>)?=null,
 onAddAndMove:((Long,Long?,(Boolean)->Unit)->Unit)?=null,
 onCreateShop:((String,(com.spisokryadom.app.data.repository.ShoppingListRepository.PurchasePlace?)->Unit)->Unit)?=null
) {
 var places by remember(ids){mutableStateOf<List<com.spisokryadom.app.data.repository.ShoppingListRepository.PurchasePlace>?>(null)}
 var additional by remember(ids){mutableStateOf<List<com.spisokryadom.app.data.repository.ShoppingListRepository.PurchasePlace>?>(null)}
 var selected by remember(ids){mutableStateOf<com.spisokryadom.app.data.repository.ShoppingListRepository.PurchasePlace?>(null)}
 val focus=androidx.compose.ui.platform.LocalFocusManager.current
 var moving by remember{mutableStateOf(false)};var adding by remember{mutableStateOf(false)};var creating by remember{mutableStateOf(false)}
 var error by remember{mutableStateOf<String?>(null)}
 LaunchedEffect(ids,currentPlaces){try{places=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){loadPlaces(ids)}.filter{currentPlaces!=setOf(it.shopId to it.branchId)}}catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;error=e.message}}
 LaunchedEffect(adding,ids){if(adding&&additional==null)try{additional=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){loadAdditionalPlaces?.invoke(ids).orEmpty()}}catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;error=e.message}}
 val options=if(adding)additional else places
 EditorSheet(if(adding)"Добавить место покупки"else "Сменить место покупки",onDismiss,busy=moving,scrollContent=false,actions={
   Button(onClick={selected?.let{place->moving=true;val completed:(Boolean)->Unit={success->moving=false;if(success)onDismiss()};if(adding)onAddAndMove?.invoke(place.shopId,place.branchId,completed)else onMove(place.shopId,place.branchId,completed)}},enabled=selected!=null&&!moving&&!creating,modifier=Modifier.fillMaxWidth()){Text(if(moving)"Смена места…"else if(adding)"Добавить и перенести"else "Сменить")}
 }){
  Column(Modifier.fillMaxWidth().testTag("purchase-place-picker"),verticalArrangement=Arrangement.spacedBy(12.dp)){
   if(options==null&&error==null)LinearProgressIndicator(Modifier.fillMaxWidth())
   if(options?.isEmpty()==true)Text(if(adding)"Все существующие места уже доступны"else "Других мест покупки нет",color=MaterialTheme.colorScheme.onSurfaceVariant)
   androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().weight(1f,fill=false).heightIn(max=360.dp)) {
    items(options.orEmpty().size,key={i->"${options!![i].shopId}:${options[i].branchId}"}){i->val place=options!![i]
     ListItem(headlineContent={Text(place.presentation.title)},supportingContent=place.presentation.subtitle?.let{address->{Text(address)}},
      leadingContent={Box(Modifier.size(48.dp),contentAlignment=androidx.compose.ui.Alignment.Center){RadioButton(selected==place,null,enabled=!moving)}},
      modifier=Modifier.fillMaxWidth().testTag("place-option-${place.shopId}:${place.branchId}").selectable(selected==place,enabled=!moving,role=androidx.compose.ui.semantics.Role.RadioButton){selected=place},
      colors=ListItemDefaults.colors(containerColor=if(selected==place)MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface))
    }
   }
   if(loadAdditionalPlaces!=null&&onAddAndMove!=null)TextButton(onClick={adding=!adding;creating=false;selected=null},enabled=!moving){Text(if(adding)"К доступным местам"else "Добавить место покупки")}
   if(adding&&onCreateShop!=null){
    if(creating)InlineCreateValue("Название магазина",{name,done->onCreateShop(name){place->if(place!=null){additional=additional.orEmpty().filterNot{it.shopId==place.shopId&&it.branchId==place.branchId}+place;selected=place;creating=false;focus.clearFocus()};done()}},{creating=false})
    else TextButton(onClick={creating=true},enabled=!moving){Text("Новый магазин…")}
   }
   error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
  }
 }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CopyProductShopSheet(productId:Long,onDismiss:()->Unit,loadShops:suspend(Long)->List<ShopEntity>,onCopy:(Long,(Boolean)->Unit)->Unit) {
 var shops by remember(productId){mutableStateOf<List<ShopEntity>?>(null)}
 var saving by remember{mutableStateOf(false)}
 var error by remember{mutableStateOf<String?>(null)}
 LaunchedEffect(productId){try{shops=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){loadShops(productId)}}catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;error=e.message}}
 EditorSheet("Добавить магазин к товару",onDismiss,busy=saving,scrollContent=false,actions={
  TextButton(onClick=onDismiss,enabled=!saving){Text("Закрыть")}
 }){
  Column(Modifier.fillMaxWidth().testTag("copy-product-shop-picker"),verticalArrangement=Arrangement.spacedBy(12.dp)){
   if(shops==null&&error==null)LinearProgressIndicator(Modifier.fillMaxWidth())
   if(shops?.isEmpty()==true)Text("Товар уже привязан ко всем магазинам города",color=MaterialTheme.colorScheme.onSurfaceVariant)
   androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().weight(1f,fill=false).heightIn(max=360.dp)) {
    val all=shops.orEmpty()
    val pinned=all.filter{it.isFavorite}.sortedBy{it.favoriteOrder}
    val rows=pinned.map{it to true}+all.map{it to false}
    items(rows.size,key={i->"${rows[i].first.id}:${rows[i].second}"}){i->val(shop,pin)=rows[i]
     CompositionLocalProvider(LocalFavoriteSurface provides com.spisokryadom.app.util.FavoriteSurface.DROPDOWNS,LocalFavoriteBlock provides pin){
      ListItem(headlineContent={FavoriteMarkedLabel(LocalPlaceLabels.current.shop(shop.id,shop.name),favorite=shop.isFavorite)},leadingContent={ShopSymbol(shop.iconKey,shop.logoUri)},modifier=(if(pin)Modifier.favoriteBlock()else Modifier).fillMaxWidth().clickable(enabled=!saving){saving=true;onCopy(shop.id){success->saving=false;if(success)onDismiss()}})
     }
    }
   }
   error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
   if(saving)LinearProgressIndicator(Modifier.fillMaxWidth())
  }
 }
}
