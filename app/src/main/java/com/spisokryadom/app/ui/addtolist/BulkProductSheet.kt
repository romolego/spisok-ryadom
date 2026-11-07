package com.spisokryadom.app.ui.addtolist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spisokryadom.app.ui.components.*
import com.spisokryadom.app.util.Quantities

@Composable
fun BulkProductSheet(shopId:Long?,branchId:Long?,onDismiss:()->Unit,onAdded:()->Unit,
    viewModel:BulkProductViewModel=viewModel(factory=BulkProductViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus=androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(state.completed){if(state.completed){viewModel.consumeCompletion();onAdded()}}
    val choosing=state.choosing!=null
    EditorSheet(if(choosing)"Выбрать товар"else if(state.preview)"Проверить товары"else "Добавить несколько товаров",onDismiss,
        busy=state.busy,scrollContent=false,actions={
            TextButton(onClick=onDismiss,enabled=!state.busy){Text("Отмена")}
            if(state.preview||choosing)TextButton(onClick={focus.clearFocus();viewModel.back()},enabled=!state.busy){Text("Назад")}
            if(!choosing)Button(onClick={focus.clearFocus();if(state.preview)viewModel.add(shopId,branchId)else viewModel.prepare()},
                enabled=!state.busy&&(!state.preview||state.rows.isNotEmpty()&&state.rows.all{it.resolved&&Quantities.parse(it.quantity)!=null}),
                modifier=Modifier.testTag("bulk-confirm")){
                Text(if(state.busy)"Подождите…"else if(state.preview)"Добавить все"else "Проверить")
            }
        }) {
        if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("bulk-error"))}
        when {
            choosing->{
                AppTextField(state.query,viewModel::query,label={Text("Найти товар или создать новый")},singleLine=true,
                    modifier=Modifier.fillMaxWidth().testTag("bulk-candidate-query"))
                if(state.searching)LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(Modifier.fillMaxWidth().heightIn(max=440.dp).testTag("bulk-candidates")) {
                    item{Text("Из каталога",style=MaterialTheme.typography.titleSmall,modifier=Modifier.padding(vertical=8.dp))}
                    if(state.candidates.isEmpty()&&!state.searching)item{Text("Подходящих товаров нет",style=MaterialTheme.typography.bodyMedium)}
                    items(state.candidates,key={it.id}){product->
                        ListItem(headlineContent={ProductCandidateTitle(product)},supportingContent={Text(product.defaultUnit?.let{"Единица: $it"}?:"Единица не задана")},
                            modifier=Modifier.fillMaxWidth().clickable(enabled=!state.searching){focus.clearFocus();viewModel.select(product)}.testTag("bulk-candidate-${product.id}"))
                    }
                    item{TextButton(onClick={focus.clearFocus();viewModel.select(null)},enabled=state.query.isNotBlank()&&!state.searching,
                        modifier=Modifier.fillMaxWidth().testTag("bulk-create-new")){
                        Icon(Icons.Filled.Add,null);Spacer(Modifier.width(8.dp));Text("Создать новый: ${state.query.trim()}")
                    }}
                }
            }
            state.preview->{
                Text("Выберите соответствие для каждой позиции. Количество можно изменить до добавления.",style=MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max=460.dp).testTag("bulk-preview"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(state.rows,key={it.key}){row->
                        OutlinedCard(Modifier.fillMaxWidth().testTag("bulk-row-${row.key}")){
                            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                                    Column(Modifier.weight(1f)){
                                        Text("Вы ввели",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(row.source,style=MaterialTheme.typography.bodyMedium)
                                    }
                                    Column(Modifier.weight(1f).clickable(enabled=!state.busy){focus.clearFocus();viewModel.choose(row.key)}){
                                        Text("Будет добавлено",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(if(row.resolved)row.name else "Выберите товар",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.bodyMedium)
                                        Text(if(!row.resolved)"Нужен выбор"else if(row.productId==null)"Новый товар"else "Из каталога",style=MaterialTheme.typography.labelSmall)
                                    }
                                }
                                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                    AppTextField(row.quantity,{viewModel.quantity(row.key,it)},label={Text("Количество")},singleLine=true,
                                        enabled=!state.busy,isError=Quantities.parse(row.quantity)==null,
                                        keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=KeyboardType.Decimal),
                                        modifier=Modifier.width(116.dp).testTag("bulk-quantity-${row.key}"))
                                    TextButton(onClick={focus.clearFocus();viewModel.choose(row.key)},enabled=!state.busy){Text("Выбрать")}
                                    Spacer(Modifier.weight(1f))
                                    IconButton(onClick={viewModel.remove(row.key)},enabled=!state.busy){Icon(Icons.Filled.Close,"Убрать: ${row.source}")}
                                }
                            }
                        }
                    }
                    item{SelectionRow("Сохранить новые товары в базе",state.saveNew,viewModel::saveNew,enabled=!state.busy)}
                }
            }
            else->{
                Text("Напишите или надиктуйте названия. Разделители: запятая, точка, точка с запятой и новая строка.",style=MaterialTheme.typography.bodyMedium)
                AppTextField(state.text,viewModel::text,label={Text("Товары")},minLines=4,maxLines=9,
                    modifier=Modifier.fillMaxWidth().testTag("bulk-input"),enabled=!state.busy)
                Text("Единицы и количество в тексте указывать не нужно. Перед добавлением появится предпросмотр.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
