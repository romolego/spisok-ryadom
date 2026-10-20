package com.spisokryadom.app.ui.batch

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import com.spisokryadom.app.ui.components.favoriteStyle
import com.spisokryadom.app.ui.components.favoriteWeight
import com.spisokryadom.app.ui.components.favoriteBackground
import com.spisokryadom.app.ui.components.favoriteSurface
import com.spisokryadom.app.ui.components.FavoriteBlock
import com.spisokryadom.app.ui.components.LocalFavoriteBlock
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spisokryadom.app.data.entity.ProductEntity
import com.spisokryadom.app.data.repository.ProductBatchChange
import com.spisokryadom.app.ui.components.LocalPlaceLabels
import com.spisokryadom.app.util.DatabaseRevision

@Composable fun rememberDatabaseRevision():String {
 val context=androidx.compose.ui.platform.LocalContext.current.applicationContext
 var revision by remember(context){mutableStateOf(DatabaseRevision.current(context))}
 DisposableEffect(context){
  val stop=DatabaseRevision.observe(context){revision=it}
  onDispose{stop()}
 }
 return revision
}

@Stable class BatchSelection {
 var active by mutableStateOf(false)
 var ids by mutableStateOf(emptySet<Long>())
 internal var loadedScope:String?=null
 internal var databaseRevision:String?=null
 fun toggle(id:Long){ids=if(id in ids)ids-id else ids+id}
 fun toggleAll(scope:Set<Long>){ids=if(scope.isNotEmpty()&&ids.containsAll(scope))ids-scope else ids+scope}
 fun clear(){active=false;ids=emptySet()}
}
private val BatchSelectionSaver=mapSaver(
 save={selection:BatchSelection->mapOf("active" to selection.active,"ids" to selection.ids.toList(),"scope" to selection.loadedScope,"revision" to selection.databaseRevision)},
 restore={saved->BatchSelection().also{selection->
  selection.active=saved["active"] as? Boolean?:false
  selection.ids=(saved["ids"] as? List<*>)?.mapNotNull{(it as? Number)?.toLong()}.orEmpty().toSet()
  selection.loadedScope=saved["scope"] as? String
  selection.databaseRevision=saved["revision"] as? String
 }}
)
@Composable fun rememberBatchSelection(scope:Set<Long>,scopeKey:Any="",loaded:Boolean=true):BatchSelection {
 val revision=rememberDatabaseRevision()
 val selection=key("batch-selection-v121"){rememberSaveable(saver=BatchSelectionSaver){BatchSelection().also{it.databaseRevision=revision}}}
 // Save the selection itself, and compare only loaded scopes. A loading screen can
 // temporarily expose its default mode before the actual saved catalogue mode arrives.
 SideEffect {
  if(selection.databaseRevision!=revision){selection.clear();selection.loadedScope=null;selection.databaseRevision=revision}
  if(loaded){
  val key=scopeKey.toString()
  if(selection.loadedScope!=null&&selection.loadedScope!=key)selection.clear()
  selection.loadedScope=key
  selection.ids=selection.ids.intersect(scope)
 }}
 BackHandler(selection.active){selection.clear()}
 return selection
}
@Composable fun BatchToggle(ids:Set<Long>,selection:BatchSelection,label:String){
 val state=if(ids.isNotEmpty()&&selection.ids.containsAll(ids))ToggleableState.On else if(ids.any{it in selection.ids})ToggleableState.Indeterminate else ToggleableState.Off
 TriStateCheckbox(state,onClick={selection.toggleAll(ids)},modifier=Modifier.semantics{contentDescription="Выбрать раздел: $label"})
}
@Composable fun BatchRow(id:Long,title:String,selected:Boolean,onToggle:()->Unit,subtitle:String?=null,modifier:Modifier=Modifier,favorite:Boolean=false){
 OutlinedCard(modifier.fillMaxWidth().testTag("batch-row-$id").toggleable(value=selected,role=Role.Checkbox,onValueChange={onToggle()}),colors=CardDefaults.outlinedCardColors(containerColor=if(selected)MaterialTheme.colorScheme.secondaryContainer else favoriteBackground(favorite))){
  Row(Modifier.padding(8.dp),verticalAlignment=Alignment.CenterVertically){
   Checkbox(selected,onCheckedChange=null,modifier=Modifier.size(48.dp).semantics{contentDescription="Выбрать: $title"})
   Column(Modifier.weight(1f).padding(start=12.dp)){Row(verticalAlignment=Alignment.CenterVertically){Text(title,fontWeight=favoriteWeight(favorite),style=MaterialTheme.typography.bodyLarge,modifier=Modifier.weight(1f,fill=false));if(favorite&&favoriteStyle().star)Icon(Icons.Default.Star,"Избранный товар",modifier=Modifier.padding(start=6.dp).size(16.dp),tint=MaterialTheme.colorScheme.primary)};subtitle?.takeIf{it.isNotBlank()}?.let{Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
  }
 }
}
@Composable fun BatchTopActions(selection:BatchSelection,scope:Set<Long>,onActions:()->Unit){
 IconButton(onClick={selection.toggleAll(scope)},enabled=scope.isNotEmpty()){Icon(Icons.Default.DoneAll,if(scope.isNotEmpty()&&selection.ids.containsAll(scope))"Снять весь выбор"else "Выбрать все")}
 IconButton(onClick=onActions,enabled=selection.ids.isNotEmpty()){Icon(Icons.Default.MoreVert,"Действия с выбранными")}
}
@Composable fun BatchFeedback(vm:BatchViewModel){
 val error by vm.error.collectAsStateWithLifecycle()
 val notice by vm.notice.collectAsStateWithLifecycle()
 val host=remember{SnackbarHostState()}
 LaunchedEffect(notice){notice?.let{host.showSnackbar(it);vm.notice.value=null}}
 Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.BottomCenter){SnackbarHost(host)}
 error?.let{AlertDialog(onDismissRequest={vm.error.value=null},text={Text(it)},confirmButton={TextButton(onClick={vm.error.value=null}){Text("Понятно")}})}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun BatchProductSheet(ids:Set<Long>,onDismiss:()->Unit,onDone:()->Unit,shopId:Long?=null,stores:Boolean=false,vm:BatchViewModel=viewModel(factory=BatchViewModel.Factory)){
 val revision=rememberDatabaseRevision()
 val openingRevision=remember{revision}
 LaunchedEffect(revision){if(revision!=openingRevision)onDismiss()}
 if(revision!=openingRevision)return
 val options by vm.options.collectAsStateWithLifecycle()
 val busy by vm.busy.collectAsStateWithLifecycle()
 val ready=!busy&&!options.isLoading
 var action by remember{mutableStateOf("")}
 var chosen by remember{mutableStateOf(emptySet<Long>())}
 var none by remember{mutableStateOf(false)}
 var targetShop by remember{mutableStateOf(shopId)}
 var creating by remember{mutableStateOf(false)}
 var name by remember{mutableStateOf("")}
 val commonShops=options.shops.filter{s->ids.all{p->options.links.any{it.productId==p&&it.shopId==s.id}}}
 val branchChoices=options.branches.filter{b->b.shopId==targetShop&&ids.all{p->val link=options.links.find{it.productId==p&&it.shopId==b.shopId};link!=null&&(!link.restrictBranches||options.availability.any{it.productId==p&&it.branchId==b.id})}}
 val labels=LocalPlaceLabels.current
 LaunchedEffect(action,options.isLoading,commonShops.map{it.id}) {
  if(action=="department"&&!stores&&!options.isLoading&&targetShop !in commonShops.map{it.id}) {
   targetShop=commonShops.find{it.id==shopId}?.id?:commonShops.singleOrNull()?.id
  }
 }
 fun choose(a:String){action=a;chosen=emptySet();none=false;creating=false
  if(a=="basket")targetShop=shopId
  if(a=="department"&&!stores)targetShop=commonShops.find{it.id==shopId}?.id?:commonShops.singleOrNull()?.id
 }
 val title=when(action){"basket"->"Место покупки";"recipient"->"Назначить получателя";"group"->"Назначить товарную группу";"shop"->"Добавить магазины к товарам";"department"->if(stores)"Добавить группы в магазины"else "Назначить группу магазина";else->"Выбрано: ${ids.size}"}
 ModalBottomSheet(onDismissRequest={if(!busy)onDismiss()},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)){
  Column(Modifier.fillMaxWidth().padding(horizontal=20.dp).navigationBarsPadding()){
   Text(title,style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(bottom=12.dp))
   Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()).testTag("batch-options")){
    if(options.isLoading)LinearProgressIndicator(Modifier.fillMaxWidth().testTag("batch-options-loading"))
    if(action.isEmpty()){
     if(stores){TextButton(onClick={choose("department")},enabled=ready){Text("Добавить группы магазина")}}
     else{
      TextButton(onClick={if(shopId!=null&&options.branches.count{it.shopId==shopId}>1)choose("basket")else vm.add(ids,shopId,onDone)},enabled=ready){Text("Добавить в список покупок")}
      TextButton(onClick={choose("recipient")},enabled=ready){Text("Назначить получателя")}
      TextButton(onClick={choose("group")},enabled=ready){Text("Назначить товарную группу")}
      TextButton(onClick={choose("shop")},enabled=ready){Text("Добавить магазины к товарам")}
      TextButton(onClick={choose("department")},enabled=ready&&commonShops.isNotEmpty()){Text("Назначить группу магазина")}
      TextButton(onClick={vm.products(ids,ProductBatchChange.Favorite(true),onDone)},enabled=ready){Text("Добавить в избранное")}
      TextButton(onClick={vm.products(ids,ProductBatchChange.Favorite(false),onDone)},enabled=ready){Text("Убрать из избранного")}
     }
    }else if(!options.isLoading){
     if(action=="department"&&!stores){
      Text("Магазин",style=MaterialTheme.typography.labelLarge)
      if(targetShop==null)Text("Выберите магазин для назначения группы",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
      commonShops.forEach{s->OptionRow(labels.shop(s.id,s.name),targetShop==s.id,!busy){targetShop=s.id;chosen=emptySet();none=false}}
      HorizontalDivider(Modifier.padding(vertical=8.dp))
     }
     val values=when(action){
      "basket"->branchChoices.map{it.id to labels.branch(it)}
      "recipient"->options.recipients.map{it.id to it.name}
      "group"->options.groups.map{it.id to it.name}
      "shop"->options.shops.map{it.id to labels.shop(it.id,it.name)}
      else->options.types.map{it.id to it.name}
     }
     val multiple=action=="shop"||stores
     val favoriteIds=when(action){"group"->options.groups.filter{it.isFavorite}.sortedBy{it.favoriteOrder}.map{it.id};"department"->options.types.filter{it.isFavorite}.sortedBy{it.favoriteOrder}.map{it.id};else->emptyList()}
     val dictionary=action=="group"||action=="department"
     androidx.compose.runtime.CompositionLocalProvider(com.spisokryadom.app.ui.components.LocalFavoriteSurface provides com.spisokryadom.app.util.FavoriteSurface.DROPDOWNS){
     val base=if(dictionary)com.spisokryadom.app.util.alphabetical(values){it.second}else values
     val pinned=favoriteIds.mapNotNull{id->base.find{it.first==id}}
     (pinned.map{it to true}+base.map{it to false}).forEachIndexed{index,(pair,pin)->val(id,label)=pair
      if(pinned.isNotEmpty()&&(index==0||index==pinned.size))Text(if(index==0)"Избранные"else "Все значения",style=MaterialTheme.typography.labelMedium)
      val row:@Composable ()->Unit={OptionRow(label,id in chosen,!busy&&(action!="department"||stores||targetShop!=null),multiple,favorite=id in favoriteIds){none=false;chosen=if(multiple){if(id in chosen)chosen-id else chosen+id}else setOf(id)}}
      if(pin)FavoriteBlock(Modifier.fillMaxWidth()){row()}else row()
     }
     }
     if(action=="basket"){
      OptionRow("По приоритету для каждого товара",none,!busy){none=true;chosen=emptySet()}
      OptionRow("Без конкретного адреса",chosen==setOf(0L),!busy){none=false;chosen=setOf(0L)}
     }
     if(action=="recipient"||action=="group"||(action=="department"&&!stores))OptionRow("Не задано",none,!busy){none=true;chosen=emptySet()}
     if(action!="basket"){if(!creating)TextButton(onClick={creating=true},enabled=!busy){Text(when(action){"recipient"->"Новый получатель…";"group"->"Новая товарная группа…";"shop"->"Новый магазин…";else->"Новая группа магазина…"})}
     else{
      OutlinedTextField(name,{name=it},label={Text("Название")},enabled=!busy,modifier=Modifier.fillMaxWidth())
      TextButton(onClick={vm.create(action,name){id->chosen=if(multiple)chosen+id else setOf(id);none=false;creating=false;name=""}},enabled=!busy&&name.isNotBlank()){Text("Создать")}
     }}
    }
   }
   if(action.isNotEmpty()){
    Row(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
     TextButton(onClick={action=""},enabled=!busy){Text("Назад")}
     Button(onClick={
      val id=chosen.singleOrNull()
      if(action=="basket")vm.add(ids,shopId,onDone,if(none)null else id)
      else if(stores)vm.shops(ids,chosen,onDone)
      else{
       val change=when(action){"recipient"->ProductBatchChange.Recipient(id);"group"->ProductBatchChange.Group(id);"shop"->ProductBatchChange.Shops(chosen);else->ProductBatchChange.Department(targetShop!!,id)}
       vm.products(ids,change,onDone)
      }
     },enabled=ready&&(chosen.isNotEmpty()||none)&&(action!="department"||stores||targetShop!=null),modifier=Modifier.weight(1f)){Text(if(busy)"Сохранение…"else "Применить к ${ids.size}")}
    }
   }else Spacer(Modifier.height(20.dp))
  }
 }
}
@Composable private fun OptionRow(label:String,selected:Boolean,enabled:Boolean,multiple:Boolean=false,favorite:Boolean=false,onClick:()->Unit){
 Row(Modifier.fillMaxWidth().heightIn(min=48.dp).then(if(multiple)Modifier.toggleable(selected,enabled=enabled,role=Role.Checkbox,onValueChange={onClick()})else Modifier.selectable(selected,enabled=enabled,role=Role.RadioButton,onClick=onClick)).padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
  if(multiple)Checkbox(selected,null,enabled=enabled,modifier=Modifier.size(48.dp))else RadioButton(selected,null,enabled=enabled,modifier=Modifier.size(48.dp))
  com.spisokryadom.app.ui.components.FavoriteMarkedLabel(label,favorite=favorite,modifier=Modifier.weight(1f).padding(start=12.dp))
 }
}
