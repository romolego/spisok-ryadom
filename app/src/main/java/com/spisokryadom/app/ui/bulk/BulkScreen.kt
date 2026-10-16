package com.spisokryadom.app.ui.bulk
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spisokryadom.app.ui.components.CityPicker
import com.spisokryadom.app.ui.addtolist.ShopChoiceField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkScreen(onBack:()->Unit,viewModel:BulkViewModel=viewModel(factory=BulkViewModel.Factory)){
 val state by viewModel.uiState.collectAsStateWithLifecycle();val shops by viewModel.locations.cityShops.collectAsStateWithLifecycle(initialValue=emptyList())
 BackHandler(enabled=state.busy){}
 Scaffold(topBar={TopAppBar(title={Text("Ввести списком")},navigationIcon={IconButton(onClick=onBack,enabled=!state.busy){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Назад")}})}){padding->
  Column(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   CityPicker(enabled=!state.busy&&state.added==0&&!state.review)
   if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
   state.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
   if(state.added>0){
    Text("Добавлено покупок: ${state.added}",style=MaterialTheme.typography.headlineSmall)
    Text("Количество и магазины можно менять в основном списке.")
    Button(onClick=onBack,modifier=Modifier.fillMaxWidth()){Text("Перейти к покупкам")}
    OutlinedButton(onClick=viewModel::undo,enabled=!state.busy,modifier=Modifier.fillMaxWidth()){Text("Отменить добавление в список")}
   }else if(!state.review){
    Text("По одной покупке в строке. Можно вставить готовый текст.")
    OutlinedTextField(state.text,viewModel::text,label={Text("Ваш список")},placeholder={Text("бананы 4\nяблоки Гала 0,5 кг\nмолоко 2 л")},enabled=!state.busy,modifier=Modifier.fillMaxWidth().weight(1f))
    Button(onClick=viewModel::review,enabled=state.text.isNotBlank()&&!state.busy,modifier=Modifier.fillMaxWidth()){Text("Разобрать и проверить")}
   }else{
    ShopChoiceField(shops,state.selectedShopId,viewModel::shop,enabled=!state.busy)
    Text("Похожие названия требуют выбора. Совместимые покупки суммируются; разные единицы и магазины остаются отдельными строками.",style=MaterialTheme.typography.bodySmall)
    LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){
     items(state.rows,key={it.index},contentType={"draft"}){line->DraftLineCard(line,viewModel,state.busy)}
    }
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
     OutlinedButton(onClick=viewModel::edit,enabled=!state.busy,modifier=Modifier.weight(1f)){Text("Исправить текст")}
     Button(onClick=viewModel::add,enabled=!state.busy&&state.rows.any{it.included},modifier=Modifier.weight(1f)){Text("В корзину (${state.rows.count{it.included}})")}
    }
   }
   Spacer(Modifier.height(8.dp))
  }
 }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DraftLineCard(line:DraftLine,vm:BulkViewModel,busy:Boolean){
 var expanded by remember(line.index){mutableStateOf(false)}
 Card(Modifier.fillMaxWidth()){
  Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   Row{Checkbox(line.included,{v->vm.update(line.index){it.copy(included=v)}},enabled=!busy);Text(line.original,Modifier.padding(top=12.dp))}
   if(line.included){
    val label=if(!line.resolved)"Выберите похожий товар" else line.candidates.find{it.id==line.productId}?.name ?: "Новая позиция: ${line.name}"
    ExposedDropdownMenuBox(expanded,onExpandedChange={if(!busy)expanded=it}){
     OutlinedTextField(label,{},readOnly=true,isError=!line.resolved,label={Text("Товар")},modifier=Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded)})
     ExposedDropdownMenu(expanded,onDismissRequest={expanded=false}){
      line.candidates.forEach{p->DropdownMenuItem(text={Text(p.name)},onClick={vm.choose(line.index,p.id);expanded=false})}
      DropdownMenuItem(text={Text("Оставить новой позицией")},onClick={vm.choose(line.index,null);expanded=false})
     }
    }
    Text("Магазин: ${line.shopPreview.ifBlank{ "Подбор…" }}",style=MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
     OutlinedTextField(line.quantity,{v->vm.update(line.index){it.copy(quantity=v)}},label={Text("Количество")},singleLine=true,modifier=Modifier.weight(1f),enabled=!busy)
     OutlinedTextField(line.unit,{v->vm.update(line.index){it.copy(unit=v)}},label={Text("Ед.")},singleLine=true,modifier=Modifier.weight(1f),enabled=!busy)
    }
    if(line.productId==null&&line.resolved)Row{Checkbox(line.permanent,{v->vm.update(line.index){it.copy(permanent=v)}},enabled=!busy);Text("Сохранить в базе",Modifier.padding(top=12.dp))}
   }
  }
 }
}
