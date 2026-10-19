package com.spisokryadom.app.ui.components
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.spisokryadom.app.data.entity.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun BranchChoiceField(branches:List<ShopBranchEntity>,selected:Long?,onSelected:(Long?)->Unit,enabled:Boolean=true,onCreate:((String,(Long)->Unit)->Unit)?=null) {
 var expanded by remember{mutableStateOf(false)};var creating by remember{mutableStateOf(false)}
 if(creating)InlineCreateValue("Адрес точки",{name,done->onCreate?.invoke(name){id->onSelected(id);creating=false;done()}},{creating=false})
 ExposedDropdownMenuBox(expanded,onExpandedChange={if(enabled)expanded=it}) {
  OutlinedTextField(if(selected==0L)"Без конкретного адреса"else if(selected==null||selected == -1L)"По приоритету"else branches.find{it.id==selected}?.let{LocalPlaceLabels.current.branch(it)}?:"Выберите адрес",{},readOnly=true,enabled=enabled,label={Text("Точка / адрес")},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded)},modifier=Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable))
  ExposedDropdownMenu(expanded,onDismissRequest={expanded=false}) {
   branches.forEach{b->DropdownMenuItem(text={Text(LocalPlaceLabels.current.branch(b))},onClick={onSelected(b.id);expanded=false})}
   DropdownMenuItem(text={Text("По приоритету для каждого товара")},onClick={onSelected(-1L);expanded=false})
   DropdownMenuItem(text={Text("Без конкретного адреса")},onClick={onSelected(0L);expanded=false})
   if(onCreate!=null)DropdownMenuItem(text={Text("Новый адрес…")},onClick={expanded=false;creating=true})
  }
 }
}
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
 ModalBottomSheet(onDismissRequest={if(!moving)onDismiss()},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true,confirmValueChange={!moving}),containerColor=MaterialTheme.colorScheme.surface) {
  Column(Modifier.fillMaxWidth().heightIn(max=(androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp*.86f).dp).testTag("purchase-place-picker").padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
   Text(if(adding)"Добавить место покупки"else "Сменить место покупки",style=MaterialTheme.typography.titleLarge)
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
   Button(onClick={selected?.let{place->moving=true;val completed:(Boolean)->Unit={success->moving=false;if(success)onDismiss()};if(adding)onAddAndMove?.invoke(place.shopId,place.branchId,completed)else onMove(place.shopId,place.branchId,completed)}},enabled=selected!=null&&!moving&&!creating,modifier=Modifier.fillMaxWidth()){Text(if(moving)"Смена места…"else if(adding)"Добавить и перенести"else "Сменить")}
   Spacer(Modifier.height(12.dp))
  }
 }
}

private fun Modifier.selectPlace(enabled:Boolean,onClick:()->Unit)=this.then(Modifier.clickable(enabled=enabled,onClick=onClick))

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CopyProductShopSheet(productId:Long,onDismiss:()->Unit,loadShops:suspend(Long)->List<ShopEntity>,onCopy:(Long,(Boolean)->Unit)->Unit) {
 var shops by remember(productId){mutableStateOf<List<ShopEntity>?>(null)}
 var saving by remember{mutableStateOf(false)}
 var error by remember{mutableStateOf<String?>(null)}
 LaunchedEffect(productId){try{shops=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){loadShops(productId)}}catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;error=e.message}}
 ModalBottomSheet(onDismissRequest={if(!saving)onDismiss()},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true,confirmValueChange={!saving}),containerColor=MaterialTheme.colorScheme.surface) {
  Column(Modifier.fillMaxWidth().heightIn(max=(androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp*.86f).dp).testTag("copy-product-shop-picker").padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
   Text("Добавить магазин к товару",style=MaterialTheme.typography.titleLarge)
   if(shops==null&&error==null)LinearProgressIndicator(Modifier.fillMaxWidth())
   if(shops?.isEmpty()==true)Text("Товар уже привязан ко всем магазинам города",color=MaterialTheme.colorScheme.onSurfaceVariant)
   androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().weight(1f,fill=false).heightIn(max=360.dp)) {
    items(shops.orEmpty().size,key={i->shops!![i].id}){i->val shop=shops!![i]
     ListItem(headlineContent={Text(LocalPlaceLabels.current.shop(shop.id,shop.name))},leadingContent={Icon(androidx.compose.material.icons.Icons.Filled.Store,null)},modifier=Modifier.fillMaxWidth().selectPlace(!saving){saving=true;onCopy(shop.id){success->saving=false;if(success)onDismiss()}})
    }
   }
   error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
   if(saving)LinearProgressIndicator(Modifier.fillMaxWidth())
   Spacer(Modifier.height(20.dp))
  }
 }
}
