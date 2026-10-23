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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.testTag
import com.spisokryadom.app.ui.components.*
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
    val validBranches=com.spisokryadom.app.domain.OutletLocations.valid(state.branches)
    val placeVenues=state.venues.filter{it.id !in state.venueEdits}+state.venueEdits.values
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
    var conversionKind by rememberSaveable{mutableStateOf(com.spisokryadom.app.data.entity.VenueKinds.MARKET)}
    if(showConvertConfirm)androidx.compose.material3.ModalBottomSheet(onDismissRequest={showConvertConfirm=false},sheetState=androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded=true)){
        Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text("Преобразовать в торговое место",style=MaterialTheme.typography.titleLarge)
            DropdownSelector("Тип места",com.spisokryadom.app.data.entity.VenueKinds.choices,conversionKind,com.spisokryadom.app.data.entity.VenueKinds::label,{it?.let{conversionKind=it}},allowNone=false)
            Text("Каждая точка станет отдельным магазином внутри «${state.name}». Товары и покупки сохранятся.",style=MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){
                TextButton(onClick={showConvertConfirm=false}){Text("Отмена")}
                TextButton(onClick={viewModel.convertToVenue(conversionKind);showConvertConfirm=false}){Text("Преобразовать")}
            }
        }
    }
    var isPlaceEditing by rememberSaveable { mutableStateOf(false) }
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
            availableItems = state.allDepartmentNames,alphabetical=true,favorites=state.departmentTypes.filter{it.isFavorite}.sortedBy{it.favoriteOrder}.map{it.name},
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
            message = "Удалить магазин «${state.name}» в городе ${state.cities.find { it.id == state.cityId }?.name.orEmpty()} и его местные адреса? Товары каталога и адреса в других городах сохранятся.",
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
                    Text(if (shopId<0) "Новый магазин" else "Магазин",maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
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
                        enabled = state.name.isNotBlank() && !state.isSaving && !state.isPhotoBusy && !state.isLoading && validBranches && state.departments.all{it.name.isNotBlank()} && state.pendingDepartments.all{it.name.isNotBlank()}
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
                .verticalScroll(scrollState).testTag("shop-card-scroll"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = { viewModel.updateName(it) },
                label = { Text("Название магазина") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth().testTag("shop-favorite-row"),verticalAlignment=Alignment.CenterVertically){
            FavoriteToggle(state.isFavorite,viewModel::updateFavorite,enabled=!state.isSaving&&!state.isPhotoBusy)
            Spacer(Modifier.weight(1f))
            ShopAppearance(state.shoppingBackground,state.name,state.iconKey,state.logoUri,
                viewModel::updateShoppingBackground,viewModel::updateIcon,viewModel::updateLogo,viewModel::updatePhotoBusy,externalPhotoBusy=state.isPhotoBusy,purchaseBackground=state.purchaseBackground,onPurchaseBackground=viewModel::updatePurchaseBackground)
            }

            ShopCardBlock("Место магазина",action={
                TextButton(onClick={isPlaceEditing=!isPlaceEditing},enabled=!state.isPhotoBusy&&(!isPlaceEditing||validBranches)){
                    Text(if(isPlaceEditing)"Готово"else "Править")
                }
            }) {
                if(isPlaceEditing){
                    CityField(state.cityId,viewModel::updateCity)
                    RetailRelationSelector("Сеть",state.networks.map{it.id to it.name},state.networkId,state.networkName,viewModel::updateNetwork,viewModel::updateNetworkName)
            com.spisokryadom.app.ui.components.BranchesEditor(state.branches,viewModel::updateBranches,viewModel::updatePhotoBusy,placeVenues.filter{it.cityId==state.cityId},externalPhotoBusy=state.isPhotoBusy,onVenueChange=viewModel::updateVenue,cityId=state.cityId)

                    if(!state.isNew&&state.branches.isNotEmpty()&&state.branches.all{it.venueId==null}){
                        HorizontalDivider()
                        TextButton(onClick={showConvertConfirm=true},enabled=validBranches&&!state.isSaving&&!state.isPhotoBusy){Text("Преобразовать в торговое место")}
                    }
                }else ShopPlaceSummary(state.copy(venues=placeVenues))
            }

            ShopCardBlock {
                DropdownSelector(label="Тип покупок в магазине",items=com.spisokryadom.app.util.PurchaseModes.labels.keys.toList(),
                    selectedItem=state.purchaseMode,itemLabel={com.spisokryadom.app.util.PurchaseModes.labels.getValue(it)},
                    itemVector={when(it){"online"->Icons.Filled.Language;"offline"->Icons.Filled.Store;"both"->Icons.Filled.SwapHoriz;else->Icons.Filled.Settings}},
                    onSelect={it?.let(viewModel::updatePurchaseMode)},allowNone=false,modifier=Modifier.fillMaxWidth())
                OutlinedTextField(value=state.note,onValueChange=viewModel::updateNote,label={Text("Короткая заметка")},
                    modifier=Modifier.fillMaxWidth(),minLines=1,maxLines=3)
                DropdownSelector("Срочные в свёрнутом магазине",UrgencyOverride.entries,state.showUrgentInCollapsed,{it.label},
                    {it?.let(viewModel::updateShowUrgentInCollapsed)},allowNone=false)
                DropdownSelector("Поднимать магазин со срочными",UrgencyOverride.entries,state.boostUrgentShop,{it.label},
                    {it?.let(viewModel::updateBoostUrgentShop)},allowNone=false)
            }

            val departments = if(state.isNew)state.pendingDepartments.map {
                ShopDepartmentEntity(id=it.tempId,shopId=0,name=it.name,displayOrder=it.displayOrder,typeId=it.typeId)
            }.sortedBy{it.displayOrder} else state.departments.sortedBy{it.displayOrder}
            androidx.compose.material3.OutlinedCard(Modifier.fillMaxWidth(),colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface)){
            Column(Modifier.padding(16.dp)){
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

            }}
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
