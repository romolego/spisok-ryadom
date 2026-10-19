package com.spisokryadom.app.ui.bulk

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spisokryadom.app.data.entity.ShopEntity
import com.spisokryadom.app.ui.addtolist.ShopChoiceField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkScreen(onBack:()->Unit,viewModel:BulkViewModel=viewModel(factory=BulkViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val shops by viewModel.locations.cityShops.collectAsStateWithLifecycle(initialValue=emptyList())
    val branches by viewModel.branchOptions.collectAsStateWithLifecycle()
    var reload by remember{mutableStateOf(false)}
    if(reload)com.spisokryadom.app.ui.components.ConfirmDialog("Обновить текст?","Несохранённые правки будут заменены текущими покупками из корзины.",{viewModel.reloadFromBasket();reload=false},{reload=false})
    val focus=LocalFocusManager.current
    val context=androidx.compose.ui.platform.LocalContext.current
    BackHandler(enabled=state.busy){if(state.parsing)viewModel.cancelReview()}
    Scaffold(topBar={TopAppBar(title={Text(if(state.isTextView)"Текстовый список"else "Ввести списком",maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)},navigationIcon={
        IconButton(onClick={if(state.parsing)viewModel.cancelReview()else onBack()},enabled=!state.busy||state.parsing){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Назад")}
    },actions={if(state.isTextView) {
        IconButton(onClick={shareShoppingList(context,state.text)},enabled=!state.busy&&state.text.isNotBlank()){Icon(androidx.compose.material.icons.Icons.Filled.Share,"Поделиться списком")}
        IconButton(onClick={if(state.dirty)reload=true else viewModel.reloadFromBasket()},enabled=!state.busy){Icon(androidx.compose.material.icons.Icons.Filled.Refresh,"Обновить из корзины")}
    }})}){padding->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            val compact=maxHeight<300.dp
            Column(Modifier.fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                if(state.busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    if(state.parsing)TextButton(onClick=viewModel::cancelReview){Text("Отменить разбор")}
                }
                state.error?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
                if(state.added>0&&!state.isTextView) {
                    Icon(Icons.Filled.CheckCircle,null,Modifier.padding(top=24.dp).size(40.dp),tint=MaterialTheme.colorScheme.secondary)
                    Text("Добавлено покупок: ${state.added}",style=MaterialTheme.typography.headlineSmall)
                    Button(onClick=onBack,modifier=Modifier.fillMaxWidth()){Text("Перейти к покупкам")}
                    OutlinedButton(onClick=viewModel::undo,enabled=!state.busy,modifier=Modifier.fillMaxWidth()){Text("Отменить добавление в список")}
                } else if(!state.review) {
                    if(state.saved)Row(verticalAlignment=Alignment.CenterVertically){Text("Изменения сохранены",Modifier.weight(1f),color=MaterialTheme.colorScheme.secondary,style=MaterialTheme.typography.bodySmall);if(state.canUndo)TextButton(onClick=viewModel::undo,enabled=!state.busy){Text("Отменить")}}
                    OutlinedTextField(
                        state.text,viewModel::text,label={Text("Ваш список")},
                        enabled=!state.busy,minLines=4,
                        shape=MaterialTheme.shapes.large,
                        modifier=Modifier.fillMaxWidth().weight(1f).testTag("bulk-editor")
                    )
                    Button(onClick={focus.clearFocus();viewModel.review()},enabled=(state.text.isNotBlank()||(state.isTextView&&state.pendingCount>0))&&!state.busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                        Text(if(state.isTextView)"Проверить изменения"else "Сопоставить товары и магазины")
                    }
                } else {
                    LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        item(key="default-shop") {
                            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                Text("Проверьте список",style=MaterialTheme.typography.titleMedium)
                                Text("Общий магазин",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                ShopChoiceField(shops,state.selectedShopId,viewModel::shop,enabled=!state.busy,onCreate=viewModel::createShop)
                            }
                        }
                        if(state.isTextView&&state.removedNames.isNotEmpty())item(key="removed") {
                            OutlinedCard {Column(Modifier.padding(12.dp)){Text("Будет удалено из некупленного: ${state.removedNames.size}",color=MaterialTheme.colorScheme.error);state.removedNames.take(8).forEach{Text(it,style=MaterialTheme.typography.bodySmall)};if(state.removedNames.size>8)Text("И ещё ${state.removedNames.size-8}")}}
                        }
                        items(state.rows,key={it.index},contentType={"draft"}){line->DraftLineCard(line,shops,branches,viewModel,state.busy,state.isTextView,state.selectedShopId)}
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick={focus.clearFocus();viewModel.edit()},enabled=!state.busy,modifier=Modifier.weight(1f)){Text("Исправить текст")}
                        Button(onClick={focus.clearFocus();viewModel.add()},enabled=!state.busy&&(state.isTextView||state.rows.any{it.included}),modifier=Modifier.weight(1f)){Text(if(state.isTextView)"Сохранить изменения"else "В корзину (${state.rows.count{it.included}})")}
                    }
                }
                Spacer(Modifier.height(if(compact)2.dp else 8.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DraftLineCard(line:DraftLine,shops:List<ShopEntity>,branches:List<com.spisokryadom.app.data.entity.ShopBranchEntity>,vm:BulkViewModel,busy:Boolean,isTextView:Boolean,selectedShopId:Long?) {
    var expanded by remember(line.index){mutableStateOf(false)}
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Checkbox(line.included,{v->vm.update(line.index){it.copy(included=v)}},enabled=!busy)
                Text(line.original,Modifier.weight(1f).padding(start=8.dp),style=MaterialTheme.typography.titleSmall)
            }
            if(line.included) {
                if(isTextView) {
                    if(!line.entryResolved) {
                        var originExpanded by remember(line.index){mutableStateOf(false)}
                        TextButton(onClick={originExpanded=true}){Text("Какая покупка изменяется?",color=MaterialTheme.colorScheme.error)}
                        DropdownMenu(originExpanded,onDismissRequest={originExpanded=false}) {
                            line.entryCandidates.forEach{id->DropdownMenuItem(text={Text(vm.entryLabel(id))},onClick={vm.chooseEntry(line.index,id);originExpanded=false})}
                            DropdownMenuItem(text={Text("Добавить новой покупкой")},onClick={vm.chooseEntry(line.index,null);originExpanded=false})
                        }
                    }else Text(if(line.entryId==null)"Новая покупка"else "Изменение покупки",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val label=if(!line.resolved)"Выберите похожий товар" else line.candidates.find{it.id==line.productId}?.name ?: "Новая позиция: ${line.name}"
                ExposedDropdownMenuBox(expanded,onExpandedChange={if(!busy)expanded=it}) {
                    OutlinedTextField(label,{},readOnly=true,isError=!line.resolved,label={Text("Товар")},modifier=Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded)})
                    ExposedDropdownMenu(expanded,onDismissRequest={expanded=false}) {
                        line.candidates.forEach{p->DropdownMenuItem(text={com.spisokryadom.app.ui.components.ProductCandidateTitle(p)},onClick={vm.choose(line.index,p.id);expanded=false})}
                        DropdownMenuItem(text={Text("Оставить новой позицией")},onClick={vm.choose(line.index,null);expanded=false})
                    }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(line.quantity,{v->vm.update(line.index){it.copy(quantity=v)}},label={Text("Количество")},singleLine=true,modifier=Modifier.weight(1f),enabled=!busy,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
                    OutlinedTextField(line.unit,{v->vm.update(line.index){it.copy(unit=v)}},label={Text("Ед.")},singleLine=true,modifier=Modifier.weight(1f),enabled=!busy)
                }
                LineShopField(line,shops,{vm.chooseShop(line.index,it)},!busy,vm::createShop)
                val selected=if(line.shopId == -1L)selectedShopId else line.shopId
                val points=branches.filter{it.shopId==selected}
                if(selected!=null&&selected>0)com.spisokryadom.app.ui.components.BranchChoiceField(points,line.branchId,{vm.chooseBranch(line.index,it)},!busy,onCreate={name,done->vm.createPoint(selected,name,line.productId,done)})
                if(line.productId==null&&line.resolved)Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(line.permanent,{v->vm.update(line.index){it.copy(permanent=v)}},enabled=!busy)
                    Text("Сохранить в базе",Modifier.weight(1f).padding(start=8.dp),style=MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LineShopField(line:DraftLine,shops:List<ShopEntity>,onSelected:(Long?)->Unit,enabled:Boolean,onCreate:(String,(Long)->Unit)->Unit) {
    var expanded by remember(line.index){mutableStateOf(false)};var creating by remember(line.index){mutableStateOf(false)}
    if(creating)com.spisokryadom.app.ui.components.InlineCreateValue("Название магазина",{name,done->onCreate(name){onSelected(it);creating=false;done()}},{creating=false})
    val ordered=remember(shops,line.shopMatches){shops.sortedBy{shop->line.shopMatches.indexOf(shop.id).takeIf{it>=0}?:Int.MAX_VALUE}}
    val value=if(!line.shopResolved)"Выберите магазин" else line.shopPreview.ifBlank{"Подбор…"}
    ExposedDropdownMenuBox(expanded,onExpandedChange={if(enabled)expanded=it}) {
        OutlinedTextField(value,{},readOnly=true,enabled=enabled,isError=!line.shopResolved,
            label={Text("Магазин покупки")},
            supportingText={if(!line.shopResolved)Text("Магазин не найден: «${line.requestedShop}»")},
            trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded)},
            modifier=Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth())
        ExposedDropdownMenu(expanded,onDismissRequest={expanded=false}) {
            ordered.forEach{shop->DropdownMenuItem(text={Text(com.spisokryadom.app.ui.components.LocalPlaceLabels.current.shop(shop.id,shop.name))},onClick={onSelected(shop.id);expanded=false})}
            DropdownMenuItem(text={Text("Использовать общий выбор")},onClick={onSelected(-1L);expanded=false})
            DropdownMenuItem(text={Text("Без магазина")},onClick={onSelected(null);expanded=false})
            DropdownMenuItem(text={Text("Новый магазин…")},onClick={expanded=false;creating=true})
        }
    }
}
