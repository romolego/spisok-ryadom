package com.spisokryadom.app.ui.addtolist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spisokryadom.app.ui.components.*
import com.spisokryadom.app.util.Quantities
import kotlinx.coroutines.launch

@Composable
fun BulkProductSheet(shopId:Long?,branchId:Long?,onDismiss:()->Unit,onAdded:()->Unit,
    viewModel:BulkProductViewModel=viewModel(factory=BulkProductViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus=androidx.compose.ui.platform.LocalFocusManager.current
    val snack=remember{SnackbarHostState()}
    val scope=rememberCoroutineScope()
    LaunchedEffect(state.completed){if(state.completed){viewModel.consumeCompletion();scope.launch{snack.showSnackbar("Товары добавлены")}}}
    val choosing=state.choosing!=null
    val close={focus.clearFocus();viewModel.enqueue();onDismiss()}
    EditorSheet(if(choosing)"Выбрать товар"else if(state.preview)"Проверить товары"else "Добавить несколько товаров",close,
        busy=state.busy,scrollContent=!state.preview&&!choosing,
        onBack={focus.clearFocus();if(state.preview||choosing)viewModel.back()else if(state.activeQueueId!=null)viewModel.enqueue()else close()},actions={
            if(state.preview||choosing)IconButton(onClick={focus.clearFocus();viewModel.back()},enabled=!state.busy){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Назад")}
            else TextButton(onClick=close,enabled=!state.busy){Text("Закрыть")}
            if(!choosing){
                TextButton(onClick={focus.clearFocus();viewModel.enqueue()},enabled=!state.busy&&state.text.isNotBlank(),modifier=Modifier.testTag("bulk-enqueue")){Text("В очередь")}
                Button(onClick={focus.clearFocus();if(state.preview)viewModel.add(shopId,branchId)else viewModel.prepare()},
                    enabled=!state.busy&&(if(state.preview)state.rows.isNotEmpty()&&state.rows.all{it.resolved&&Quantities.parse(it.quantity)!=null&&it.name.isNotBlank()}else state.text.isNotBlank()),
                    modifier=Modifier.testTag("bulk-confirm")){
                    Text(if(state.busy)"Подождите…"else if(state.preview)"Добавить все"else "Проверить")
                }
            }else TextButton(onClick=close,enabled=!state.busy){Text("Закрыть")}
        }) {
        if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("bulk-error"))}
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.weight(1f)){
                HelpLabel(if(choosing)"Каталог"else "Товары",if(choosing)"Найдите и выберите товар. Он появится в исходной строке предпросмотра."else
                    "Разделители: запятая, точка, точка с запятой и новая строка. Количество и единицы в тексте не разбираются. Можно отложить текст в очередь без проверки. В предпросмотре выберите товары и количество; минус до нуля убирает строку.")
            }
            if(state.text.isNotBlank()||state.rows.isNotEmpty())IconButton(onClick={focus.clearFocus();viewModel.clear()},enabled=!state.busy,modifier=Modifier.testTag("bulk-clear")){Icon(Icons.Filled.DeleteOutline,"Удалить текущий список")}
            IconButton(onClick=close,enabled=!state.busy){Icon(Icons.Filled.Close,"Закрыть массовый ввод")}
        }
        when {
            choosing->{
                AppTextField(state.query,viewModel::query,label={Text("Найти товар")},singleLine=true,
                    modifier=Modifier.fillMaxWidth().testTag("bulk-candidate-query"))
                if(state.searching)LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(Modifier.fillMaxWidth().heightIn(max=440.dp).testTag("bulk-candidates")) {
                    if(state.candidates.isEmpty()&&!state.searching)item{Text("Подходящих товаров нет",style=MaterialTheme.typography.bodyMedium)}
                    items(state.candidates,key={it.id}){product->
                        ListItem(headlineContent={ProductCandidateTitle(product)},
                            modifier=Modifier.fillMaxWidth().clickable(enabled=!state.searching){focus.clearFocus();viewModel.select(product)}.testTag("bulk-candidate-${product.id}"))
                    }
                }
            }
            state.preview->{
                LazyColumn(Modifier.fillMaxWidth().heightIn(max=460.dp).testTag("bulk-preview"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(state.rows.isEmpty())item{Text("Список пуст",Modifier.padding(vertical=12.dp))}
                    items(state.rows,key={it.key}){row->
                        OutlinedCard(Modifier.fillMaxWidth().testTag("bulk-row-${row.key}")){
                            Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                                Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){
                                    Text(row.source,Modifier.fillMaxWidth().padding(horizontal=32.dp),style=MaterialTheme.typography.titleSmall,textAlign=TextAlign.Center)
                                    IconButton(onClick={viewModel.remove(row.key)},enabled=!state.busy,modifier=Modifier.align(Alignment.CenterEnd).size(32.dp)){
                                        Icon(Icons.Filled.Close,"Убрать: ${row.source}",Modifier.size(20.dp))
                                    }
                                }
                                val options=state.matches[row.key].orEmpty().let{matches->
                                    (listOfNotNull(matches.find{it.id==row.productId})+matches).distinctBy{it.id}.take(5)
                                }
                                if(options.isNotEmpty())Text("В каталоге",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                else if(!row.resolved)Text("Совпадений нет",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                options.forEach{product->
                                    val selected=row.resolved&&row.productId==product.id
                                    Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically){
                                        Row(Modifier.weight(1f).testTag("bulk-option-${row.key}-${product.id}").clickable(enabled=!state.busy){viewModel.selectFor(row.key,product)},verticalAlignment=Alignment.CenterVertically){
                                            RadioButton(selected,onClick=null,modifier=Modifier.size(32.dp));Spacer(Modifier.width(4.dp))
                                            ProductCandidateTitle(product)
                                        }
                                        if(selected)BulkQuantity(row,viewModel,!state.busy)
                                    }
                                }
                                if(row.resolved&&row.productId==null){
                                    Row(verticalAlignment=Alignment.CenterVertically){
                                        AppTextField(row.name,{viewModel.newName(row.key,it)},label={Text("Новый товар")},enabled=!state.busy,singleLine=true,
                                            allowVoice=false,modifier=Modifier.weight(1f).testTag("bulk-new-name-${row.key}"))
                                        Spacer(Modifier.width(6.dp));BulkQuantity(row,viewModel,!state.busy)
                                    }
                                }
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                                    TextButton(onClick={focus.clearFocus();viewModel.choose(row.key)},enabled=!state.busy,modifier=Modifier.testTag("bulk-choose-${row.key}")){Text("Из каталога")}
                                    TextButton(onClick={focus.clearFocus();viewModel.selectFor(row.key,null)},enabled=!state.busy,modifier=Modifier.testTag("bulk-new-${row.key}")){Text("Создать новый")}
                                }
                            }
                        }
                    }
                    item{SelectionRow("Сохранить новые товары в базе",state.saveNew,viewModel::saveNew,enabled=!state.busy)}
                }
            }
            else->{
                AppTextField(state.text,viewModel::text,minLines=4,maxLines=9,
                    modifier=Modifier.fillMaxWidth(),inputModifier=Modifier.testTag("bulk-input"),enabled=!state.busy,topActions=true)
                if(state.queue.isNotEmpty()){
                    Text("В очереди · ${state.queue.size}",style=MaterialTheme.typography.titleSmall)
                    state.queue.forEachIndexed{index,batch->
                        OutlinedCard(Modifier.fillMaxWidth().testTag("bulk-queued-${batch.id}")){
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                                Column(Modifier.weight(1f).clickable(enabled=!state.busy){focus.clearFocus();viewModel.openQueued(batch.id)}.padding(12.dp)){
                                    Text("Список ${index+1}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                                    Text(batch.text,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                                IconButton(onClick={viewModel.removeQueued(batch.id)},enabled=!state.busy){Icon(Icons.Filled.DeleteOutline,"Удалить список ${index+1}")}
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snack)
    }
}

@Composable
private fun BulkQuantity(row:com.spisokryadom.app.data.repository.BulkProductRow,vm:BulkProductViewModel,enabled:Boolean){
    PurchaseQuantityStepper(row.quantity,false,true,{vm.step(row.key,-1)},{vm.step(row.key,1)},enabled,
        decreaseLabel="Уменьшить: ${row.source}",increaseLabel="Увеличить: ${row.source}",
        onTextChange={vm.quantity(row.key,it)},inputModifier=Modifier.testTag("bulk-quantity-${row.key}"))
}
