package com.spisokryadom.app.ui.shopcard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spisokryadom.app.data.entity.ShopDepartmentEntity
import com.spisokryadom.app.ui.components.ConfirmDialog
import com.spisokryadom.app.ui.components.EditableSection
import com.spisokryadom.app.ui.components.MultiSelectDialog
import com.spisokryadom.app.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopCardScreen(
    shopId: Long,
    onNavigateBack: () -> Unit,
    viewModel: ShopCardViewModel = viewModel(factory = ShopCardViewModel.factory(shopId))
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    state.error?.let { message -> androidx.compose.material3.AlertDialog(onDismissRequest=viewModel::clearError,text={Text(message)},confirmButton={TextButton(onClick=viewModel::clearError){Text("Понятно")}}) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val itemScrollPx = with(LocalDensity.current) { 52.dp.toPx() }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onNavigateBack()
    }
    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) onNavigateBack()
    }

    var showAddDepartmentDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showConvertConfirm by remember { mutableStateOf(false) }
    if(showConvertConfirm)ConfirmDialog("Преобразовать в рынок или ТЦ?","Каждая точка станет отдельным магазином внутри «${state.name}». Товары и покупки сохранятся.",onConfirm={viewModel.convertToVenue();showConvertConfirm=false},onDismiss={showConvertConfirm=false})
    var isDepartmentsEditing by remember { mutableStateOf(false) }

    var resetDepartmentOrder by remember{mutableStateOf(false)}
    if(resetDepartmentOrder)com.spisokryadom.app.ui.components.ConfirmDialog("Сортировать по алфавиту?","Текущий порядок групп магазина будет сброшен.",{if(state.isNew)viewModel.sortPendingDepartmentsAlphabetically()else viewModel.sortDepartmentsAlphabetically();resetDepartmentOrder=false},{resetDepartmentOrder=false})
    if (showAddDepartmentDialog) {
        val existingDepartmentNames = if (!state.isNew) {
            state.departments.map { it.name }.toSet()
        } else {
            state.pendingDepartments.map { it.name }.toSet()
        }
        MultiSelectDialog(
            title = "Добавить группы магазина",
            availableItems = state.allDepartmentNames,
            alreadyAddedItems = existingDepartmentNames,
            newItemLabel = "Новая группа",
            onConfirm = { names ->
                viewModel.addDepartments(names)
                showAddDepartmentDialog = false
            },
            onDismiss = { showAddDepartmentDialog = false }
        )
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "Удалить магазин",
            message = "Вы уверены, что хотите удалить магазин «${state.name}»? Все группы магазина будут удалены.",
            onConfirm = {
                viewModel.deleteShop()
                showDeleteConfirm = false
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (state.isNew) "Новый магазин" else "Магазин",maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Удалить магазин",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    TextButton(
                        onClick = { viewModel.save() },
                        enabled = state.name.isNotBlank() && !state.isSaving && !state.isPhotoBusy && !state.isLoading && state.branches.all{it.address.isNotBlank()} && state.departments.all{it.name.isNotBlank()} && state.pendingDepartments.all{it.name.isNotBlank()}
                    ) {
                        Text("Сохранить")
                    }
                }
            )
        }
    ) { padding ->
        if(state.isLoading){
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().padding(padding),contentAlignment=Alignment.Center){androidx.compose.material3.CircularProgressIndicator()}
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = { viewModel.updateName(it) },
                label = { Text("Название магазина") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            com.spisokryadom.app.ui.components.CityField(state.cityId,viewModel::updateCity)
            com.spisokryadom.app.ui.components.BranchesEditor(state.branches,viewModel::updateBranches,viewModel::updatePhotoBusy,state.venues.filter{it.cityId==state.cityId},state.venueNames,viewModel::updateVenueName,pointGroups={point->
                val types=state.departments.distinctBy{it.typeId}.filter{it.typeId>0}
                if(point.id>0&&types.size>1){
                    Text("Порядок групп в точке",style=MaterialTheme.typography.titleSmall)
                    val order=state.branchOrders[point.id].orEmpty()
                    val sorted=types.sortedWith(compareBy<ShopDepartmentEntity>{order.indexOf(it.typeId).takeIf{idx->idx>=0}?:Int.MAX_VALUE}.thenBy{it.displayOrder})
                    com.spisokryadom.app.ui.components.AnimatedOrderColumn(sorted,{it.typeId}){index,group->
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                            Text(group.name,Modifier.weight(1f))
                            fun move(delta:Int){val ids=sorted.map{it.typeId}.toMutableList();ids.add(index+delta,ids.removeAt(index));viewModel.updateBranchOrder(point.id,ids)}
                            com.spisokryadom.app.ui.components.ReorderButtons({move(-1)},{move(1)},index>0,index<sorted.lastIndex)
                        }
                    }
                }
            })
            if(!state.isNew&&state.branches.isNotEmpty())TextButton(onClick={showConvertConfirm=true},enabled=!state.isSaving&&!state.isPhotoBusy){Text("Преобразовать в рынок или ТЦ")}

            com.spisokryadom.app.ui.components.DropdownSelector(
                label="Тип покупок в магазине",items=com.spisokryadom.app.util.PurchaseModes.labels.keys.toList(),
                selectedItem=state.purchaseMode,itemLabel={com.spisokryadom.app.util.PurchaseModes.labels.getValue(it)},
                onSelect={it?.let(viewModel::updatePurchaseMode)},allowNone=false,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(
                value = state.note,
                onValueChange = { viewModel.updateNote(it) },
                label = { Text("Заметка") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )

            com.spisokryadom.app.ui.components.OptionalRetailField("Сеть",state.networks.map{it.id to it.name},state.networkId,state.networkName,viewModel::updateNetwork,viewModel::updateNetworkName,canRename=true)

            com.spisokryadom.app.ui.components.ShoppingBlockAppearance(state.shoppingBackground,state.name,viewModel::updateShoppingBackground)

            // ── Настройки срочности ──────────────────────────────────────
            SectionHeader(title = "Настройки срочности")

            // Show urgent in collapsed
            Text(
                "Срочные товары в свернутом магазине",
                style = MaterialTheme.typography.bodyMedium
            )
            UrgencyOverride.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.updateShowUrgentInCollapsed(option) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = state.showUrgentInCollapsed == option,
                        onClick = { viewModel.updateShowUrgentInCollapsed(option) }
                    )
                    Text(
                        text = option.label,
                        modifier = Modifier.padding(start=8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Boost urgent shop
            Text(
                "Поднимать магазин при наличии срочных",
                style = MaterialTheme.typography.bodyMedium
            )
            UrgencyOverride.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.updateBoostUrgentShop(option) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = state.boostUrgentShop == option,
                        onClick = { viewModel.updateBoostUrgentShop(option) }
                    )
                    Text(
                        text = option.label,
                        modifier = Modifier.padding(start=8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            val departments = if(state.isNew)state.pendingDepartments.map {
                ShopDepartmentEntity(id=it.tempId,shopId=0,name=it.name,displayOrder=it.displayOrder)
            }.sortedBy{it.displayOrder} else state.departments.sortedBy{it.displayOrder}
            EditableSection(
                title="Группы магазина",isEditing=isDepartmentsEditing,onToggleEdit={isDepartmentsEditing=it},
                isEmpty=departments.isEmpty(),emptyMessage="Нет групп",
                viewContent={departments.forEach{dept->key(dept.id){
                    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){
                        com.spisokryadom.app.ui.components.MarkedLabel(dept.name,state.departmentTypes.find{it.id==dept.typeId}?.accent.orEmpty(),state.departmentTypes.find{it.id==dept.typeId}?.iconKey.orEmpty(),Modifier.padding(12.dp),small=true)
                    }
                }}},
                editContent={
                    val focus=androidx.compose.ui.platform.LocalFocusManager.current
                    com.spisokryadom.app.ui.components.AnimatedOrderColumn(departments,{it.id}){index,dept->
                        Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){
                            Column(Modifier.padding(12.dp)){
                                OutlinedTextField(dept.name,{value->
                                    if(state.isNew)viewModel.renamePendingDepartment(dept.id,value)
                                    else viewModel.updateDepartment(dept.copy(name=value))
                                },label={Text("Название группы")},singleLine=true,isError=dept.name.isBlank(),modifier=Modifier.fillMaxWidth())
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){
                                    com.spisokryadom.app.ui.components.ReorderButtons(
                                        onUp={focus.clearFocus();if(state.isNew)viewModel.movePendingDepartmentUp(dept.id)else viewModel.moveDepartmentUp(dept)},
                                        onDown={focus.clearFocus();if(state.isNew)viewModel.movePendingDepartmentDown(dept.id)else viewModel.moveDepartmentDown(dept)},
                                        canMoveUp=index>0,canMoveDown=index<departments.lastIndex)
                                    IconButton(onClick={if(state.isNew)viewModel.deletePendingDepartment(dept.id)else viewModel.deleteDepartment(dept)}){
                                        Icon(Icons.Filled.Delete,"Удалить группу: ${dept.name}")
                                    }
                                }
                            }
                        }
                    }
                    if(departments.size>=2)OutlinedButton(onClick={resetDepartmentOrder=true},modifier=Modifier.fillMaxWidth()){
                        Icon(Icons.Filled.SortByAlpha,null);Text("По алфавиту")
                    }
                    OutlinedButton(onClick={showAddDepartmentDialog=true},modifier=Modifier.fillMaxWidth()){
                        Icon(Icons.Filled.Add,null);Text("Добавить группы")
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
