package com.spisokryadom.app.ui.productcard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spisokryadom.app.data.entity.ShopDepartmentEntity
import com.spisokryadom.app.data.entity.ShopEntity
import com.spisokryadom.app.ui.components.ConfirmDialog
import com.spisokryadom.app.ui.components.DepartmentChoice
import com.spisokryadom.app.ui.components.DropdownSelector
import com.spisokryadom.app.ui.components.EditableSection
import com.spisokryadom.app.ui.components.InputDialog
import com.spisokryadom.app.ui.components.PurchaseTypeSelector
import com.spisokryadom.app.ui.components.QuantityUnitInputWithSuggestions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductCardScreen(
    productId: Long,
    preselectedShopId: Long = -1L,
    onNavigateBack: () -> Unit,
    onUseExistingProduct:(Long)->Unit = {},
    onProductSaved:((Long)->Unit)?=null,
    initialName:String="",
    viewModel: ProductCardViewModel = viewModel(factory = ProductCardViewModel.factory(productId, preselectedShopId, initialName))
) {
    val rawState by viewModel.uiState.collectAsStateWithLifecycle()
    val places=com.spisokryadom.app.ui.components.LocalPlaceLabels.current
    val state=rawState.copy(shopLinks=rawState.shopLinks.map{it.copy(shopName=places.shop(it.link.shopId,it.shopName))},pendingShopLinks=rawState.pendingShopLinks.map{it.copy(shopName=places.shop(it.shopId,it.shopName))})
    val scrollState = rememberScrollState()
    var nameFocused by remember { mutableStateOf(false) }
    val nameFocusManager=androidx.compose.ui.platform.LocalFocusManager.current
    val leaveName={ nameFocused=false;nameFocusManager.clearFocus() }
    val focus=androidx.compose.ui.platform.LocalFocusManager.current

    LaunchedEffect(state.usedExistingId){state.usedExistingId?.let(onUseExistingProduct)}
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {val id=state.savedProductId;if(id!=null&&onProductSaved!=null)onProductSaved(id)else onNavigateBack()}
    }
    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) onNavigateBack()
    }
    androidx.activity.compose.BackHandler { viewModel.finishEditing() }

    state.error?.let { message -> androidx.compose.material3.AlertDialog(onDismissRequest=viewModel::clearError,title={Text("Проверьте товар")},text={Text(message)},confirmButton={TextButton(onClick=viewModel::clearError){Text("Понятно")}}) }
    var showAppearance by remember { mutableStateOf(false) }
    var createRecipient by remember { mutableStateOf(false) }
    var showNewGroupDialog by remember { mutableStateOf(false) }
    var showAddShopLinkDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // For new products, start in edit mode so the user can add shops immediately
    var isShopLinksEditing by remember { mutableStateOf(productId == -1L) }
    // Department editing state
    var editingPendingLinkId by remember { mutableStateOf<Long?>(null) }
    var editingExistingLinkId by remember { mutableStateOf<Long?>(null) }

    // --- Dialogs ---

    if(showAppearance)com.spisokryadom.app.ui.components.AppearanceSheet("Цвет товара",state.accent,withIcons=false,onSave={color,_->viewModel.accent(color);showAppearance=false},onDismiss={showAppearance=false})
    if (showNewGroupDialog) {
        InputDialog(
            title = "Новая товарная группа",
            label = "Название",
            onConfirm = { name ->
                viewModel.createGroup(name)
                showNewGroupDialog = false
            },
            onDismiss = { showNewGroupDialog = false }
        )
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "Удалить товар",
            message = "Вы уверены, что хотите удалить товар «${state.name}»? Связи с магазинами и записи в списке покупок будут удалены.",
            onConfirm = {
                viewModel.deleteProduct()
                showDeleteConfirm = false
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    if (showAddShopLinkDialog) {
        val existingShopIds = if (state.isNew) {
            state.pendingShopLinks.map { it.shopId }.toSet()
        } else {
            state.shopLinks.map { it.link.shopId }.toSet()
        }
        AddShopLinkDialog(
            shops = state.shops,
            existingShopIds = existingShopIds,
            onConfirm = { shopId, departmentId, points ->
                viewModel.updateBranchSelections(if(points==null)state.branchSelections-shopId else state.branchSelections+(shopId to points))
                if (state.isNew) {
                    viewModel.addPendingShopLink(shopId, departmentId)
                } else {
                    viewModel.addShopLink(shopId, departmentId)
                }
                showAddShopLinkDialog = false
            },
            onDismiss = { showAddShopLinkDialog = false },
            onLoadDepartments = { shopId -> viewModel.loadDepartmentsForShop(shopId) },
            departmentsForShop = state.departmentsForSelectedShop,
            types=state.departmentTypes,
            suggestedDepartmentId = state.suggestedDepartmentId,
            suggestedDepartmentReason = state.suggestedDepartmentReason,
            branches=state.branches,
            onCreateShop=viewModel::createShop,
            onCreateDepartment=viewModel::createShopDepartment,
            creatingDepartment=state.isCreatingDepartment,
            departmentsShopId=state.departmentsShopId
        )
    }

    // Department editing dialog for pending links
    editingPendingLinkId?.let { tempId ->
        val pendingLink = state.pendingShopLinks.find { it.tempId == tempId }
        if (pendingLink != null) {
            DepartmentSelectDialog(
                shopName = pendingLink.shopName,
                departments = state.departmentsForSelectedShop,
                currentDepartmentId = pendingLink.departmentId,
                types=state.departmentTypes,
            suggestedDepartmentId = state.suggestedDepartmentId,
                suggestedDepartmentReason = state.suggestedDepartmentReason,
                onConfirm = { deptId ->
                    viewModel.updatePendingShopLinkDepartment(tempId, deptId)
                    editingPendingLinkId = null
                },
                onCreateDepartment={name,done->viewModel.createShopDepartment(pendingLink.shopId,name,done)},
                creatingDepartment=state.isCreatingDepartment,
                onDismiss = { editingPendingLinkId = null }
            )
        }
    }

    // Department editing dialog for existing links
    editingExistingLinkId?.let { linkId ->
        val linkUi = state.shopLinks.find { it.link.id == linkId }
        if (linkUi != null) {
            DepartmentSelectDialog(
                shopName = linkUi.shopName,
                departments = state.departmentsForSelectedShop,
                currentDepartmentId = linkUi.link.departmentId,
                types=state.departmentTypes,
            suggestedDepartmentId = state.suggestedDepartmentId,
                suggestedDepartmentReason = state.suggestedDepartmentReason,
                onConfirm = { deptId ->
                    viewModel.updateShopLinkDepartment(linkUi.link, deptId)
                    editingExistingLinkId = null
                },
                onCreateDepartment={name,done->viewModel.createShopDepartment(linkUi.link.shopId,name,done)},
                creatingDepartment=state.isCreatingDepartment,
                onDismiss = { editingExistingLinkId = null }
            )
        }
    }

    // --- Main content ---

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (state.isNew) {
                        Text("Новый товар")
                    } else {
                        Column {
                            Text(
                                text = state.name.ifBlank { "Товар" },
                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Редактирование",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {viewModel.finishEditing()}) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (!state.isNew||state.savedProductId!=null) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Удалить товар",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    TextButton(
                        onClick = { viewModel.save() },
                        enabled = state.name.isNotBlank() && !state.isSaving && !state.isPhotoBusy
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
            if(state.isTemporary)TextButton(onClick=viewModel::keepInCatalog){Text("Сохранить разовую позицию в базе")}
            state.autoSaveIssue?.let{Text("$it",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)}
            OutlinedTextField(
                value = state.name,
                onValueChange = { viewModel.updateName(it) },
                label = { Text("Название товара") },
                keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(imeAction=androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions=androidx.compose.foundation.text.KeyboardActions(onDone={leaveName()}),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().onFocusChanged { nameFocused=it.isFocused }.then(Modifier.testTag("product-name"))
            )

            if(state.isNew&&nameFocused&&state.matchingProducts.isNotEmpty()) {
                androidx.compose.material3.OutlinedCard(Modifier.fillMaxWidth()){
                    Column(Modifier.padding(12.dp)){
                        Text("Уже в базе",style=MaterialTheme.typography.titleSmall)
                        state.matchingProducts.forEach{product->
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                                Column(Modifier.weight(1f)){
                                    com.spisokryadom.app.ui.components.ProductCandidateTitle(product)
                                    state.productContexts[product.id]?.takeIf{it.isNotBlank()}?.let{Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                                }
                                TextButton(onClick={viewModel.useExistingProduct(product.id)},enabled=!state.isSaving&&!state.isPhotoBusy){Text("Использовать")}
                            }
                        }
                    }
                }
            }
            if(state.duplicateName)androidx.compose.material3.OutlinedCard(Modifier.fillMaxWidth()){
                Column(Modifier.padding(12.dp)){
                    Text("Товар с таким названием уже есть",color=MaterialTheme.colorScheme.error)
                    TextButton(onClick={viewModel.save(allowDuplicate=true)},enabled=!state.isSaving){Text("Создать отдельный товар")}
                }
            }
            if(state.isNew&&preselectedShopId>0)com.spisokryadom.app.ui.components.BranchAvailabilityEditor(
                state.pendingShopLinks.map{it.shopId to it.shopName},state.branches,state.branchSelections,viewModel::updateBranchSelections,state.branchPriorities,viewModel::updateBranchPriorities)
            DropdownSelector(
                label="Товарная группа",onOpen=leaveName,items=state.groups,selectedItem=state.groups.find{it.id==state.productGroupId},
                itemLabel={it.name},onSelect={viewModel.updateProductGroup(it?.id)},modifier=Modifier.fillMaxWidth(),
                allowCreate=true,onCreateNew={showNewGroupDialog=true},createLabel="Новая группа…",itemIcon={it.iconKey},itemAccent={it.accent})

            DropdownSelector(
                label = "Получатель",onOpen=leaveName,
                items = state.recipients,
                selectedItem = state.recipients.find { it.id == state.recipientId },
                itemLabel = { it.name },
                onSelect = { viewModel.updateRecipient(it?.id) },allowCreate=true,onCreateNew={createRecipient=true},createLabel="Новый получатель…",itemIcon={it.iconKey},itemAccent={it.accent},
                modifier = Modifier.fillMaxWidth()
            )

            if(createRecipient)com.spisokryadom.app.ui.components.InlineCreateValue("Имя получателя",{name,done->viewModel.createRecipient(name){createRecipient=false;done()}},{createRecipient=false})
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                com.spisokryadom.app.ui.components.SelectionRow("Избранное",state.isFavorite,{leaveName();viewModel.favorite(it)},modifier=Modifier.weight(1f))
                IconButton(onClick={showAppearance=true}){Icon(Icons.Filled.Palette,"Цвет товара")}
            }
            com.spisokryadom.app.ui.components.accentColor(state.accent)?.let{androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(2.dp).background(it))}
            QuantityUnitInputWithSuggestions(
                quantity = state.defaultQuantity,
                unit = state.defaultUnit,
                onQuantityChange = { viewModel.updateDefaultQuantity(it) },
                onUnitChange = { viewModel.updateDefaultUnit(it) },
                unitSuggestions = state.unitSuggestions,
                quantityLabel = "Кол-во по умолч.",
                unitLabel = "Ед. изм."
            )

            OutlinedTextField(
                value = state.note,
                onValueChange = { viewModel.updateNote(it) },
                label = { Text("Заметка") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.priceValue,
                    onValueChange = { value ->
                        if (value.isEmpty() || value.matches(Regex("^\\d*[.,]?\\d*$"))) {
                            viewModel.updatePriceValue(value)
                        }
                    },
                    label = { Text("Цена, ₽") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = state.priceDate,
                    onValueChange = { viewModel.updatePriceDate(it) },
                    label = { Text("Дата цены") },
                    placeholder = { Text("дд.мм.гггг") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            PurchaseTypeSelector(
                selectedType = state.purchaseType,allowedTypes=state.allowedPurchaseTypes,
                onTypeChange = { viewModel.updatePurchaseType(it) }
            )

            if (state.purchaseType == "online") {
                OutlinedTextField(
                    value = state.sellerUrl,
                    onValueChange = { viewModel.updateSellerUrl(it) },
                    label = { Text("Ссылка на продавца") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.productUrl,
                    onValueChange = { viewModel.updateProductUrl(it) },
                    label = { Text("Ссылка на товар") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            com.spisokryadom.app.ui.components.BranchPhotoEditor(
                path=state.photoUri,onChange={viewModel.updatePhotoUri(it.orEmpty())},onBusyChange=viewModel::photoBusy,
                title="Фото товара",directory="product_photos"
            )

            // --- Unified shop links section ---
            val sortedPendingLinks = if(state.preferCityShopOrder)state.pendingShopLinks.sortedWith(compareBy<PendingShopLink>{state.shopOrders[it.shopId]?:Int.MAX_VALUE}.thenBy{it.displayOrder})else state.pendingShopLinks.sortedBy { it.displayOrder }
            val sortedExistingLinks = if(state.preferCityShopOrder)state.shopLinks.sortedWith(compareBy<ShopLinkUi>{state.shopOrders[it.link.shopId]?:Int.MAX_VALUE}.thenBy{it.link.priority})else state.shopLinks.sortedBy { it.link.priority }
            val hasAnyLinks = if (state.isNew) sortedPendingLinks.isNotEmpty() else sortedExistingLinks.isNotEmpty()

            EditableSection(
                title = "Привязанные магазины",
                isEditing = isShopLinksEditing,
                onToggleEdit = { isShopLinksEditing = it },
                isEmpty = !hasAnyLinks,
                emptyMessage = "Нет привязанных магазинов",
                viewContent = {
                    if (state.isNew) {
                        sortedPendingLinks.forEachIndexed { index, pendingLink ->
                            ShopLinkViewCard(
                                orderNumber = index + 1,
                                shopName = pendingLink.shopName,
                                departmentName = pendingLink.departmentName
                            )
                        }
                    } else {
                        sortedExistingLinks.forEachIndexed { index, linkUi ->
                            ShopLinkViewCard(
                                orderNumber = index + 1,
                                shopName = linkUi.shopName,
                                departmentName = linkUi.departmentName,
                                onEditDepartment = {
                                    editingExistingLinkId = linkUi.link.id
                                    viewModel.loadDepartmentsForShop(linkUi.link.shopId)
                                }
                            )
                        }
                    }
                },
                editContent = {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                        Text("Свой порядок магазинов",Modifier.weight(1f))
                        androidx.compose.material3.Switch(!state.preferCityShopOrder,{viewModel.useCityShopOrder(!it)})
                    }
                    if (state.isNew) {
                        com.spisokryadom.app.ui.components.AnimatedOrderColumn(sortedPendingLinks,{it.tempId}) { index, pendingLink ->
                            ShopLinkEditCard(
                                orderNumber = index + 1,
                                shopName = pendingLink.shopName,
                                departmentName = pendingLink.departmentName,
                                isFirst = index == 0,
                                isLast = index == sortedPendingLinks.size - 1,
                                onMoveUp = {
                                    viewModel.movePendingShopLinkUp(pendingLink.tempId)
                                },
                                onMoveDown = {
                                    viewModel.movePendingShopLinkDown(pendingLink.tempId)
                                },
                                onRemove = { viewModel.removePendingShopLink(pendingLink.tempId) },
                                onEditDepartment = {
                                    editingPendingLinkId = pendingLink.tempId
                                    viewModel.loadDepartmentsForShop(pendingLink.shopId)
                                }
                            )
                        }
                    } else {
                        com.spisokryadom.app.ui.components.AnimatedOrderColumn(sortedExistingLinks,{it.link.id}) { index, linkUi ->
                            ShopLinkEditCard(
                                orderNumber = index + 1,
                                shopName = linkUi.shopName,
                                departmentName = linkUi.departmentName,
                                isFirst = index == 0,
                                isLast = index == sortedExistingLinks.size - 1,
                                onMoveUp = {
                                    viewModel.moveShopLinkUp(linkUi.link)
                                },
                                onMoveDown = {
                                    viewModel.moveShopLinkDown(linkUi.link)
                                },
                                onRemove = { viewModel.removeShopLink(linkUi.link) },
                                onEditDepartment = {
                                    editingExistingLinkId = linkUi.link.id
                                    viewModel.loadDepartmentsForShop(linkUi.link.shopId)
                                }
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { showAddShopLinkDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text("Добавить магазин")
                    }
                }
            )

            if(!(state.isNew&&preselectedShopId>0))com.spisokryadom.app.ui.components.BranchAvailabilityEditor(
                if(state.isNew)sortedPendingLinks.map{it.shopId to it.shopName}else sortedExistingLinks.map{it.link.shopId to it.shopName},
                state.branches,state.branchSelections,viewModel::updateBranchSelections,state.branchPriorities,viewModel::updateBranchPriorities
            )
            var editingPointGroups by androidx.compose.runtime.saveable.rememberSaveable{mutableStateOf(false)}
            val linkedIds=(if(state.isNew)sortedPendingLinks.map{it.shopId}else sortedExistingLinks.map{it.link.shopId}).toSet()
            val groupPoints=state.branches.filter{it.shopId in linkedIds&&(it.shopId !in state.branchSelections||it.id in state.branchSelections[it.shopId].orEmpty())}
            if(groupPoints.size>1)com.spisokryadom.app.ui.components.EditableSection(
                title="Группы в отдельных точках",isEditing=editingPointGroups,onToggleEdit={editingPointGroups=it},isEmpty=false,emptyMessage="",editDescription="Изменить группы в отдельных точках",
                viewContent={groupPoints.filter{it.id in state.branchDepartments}.forEach{point->
                    Text(point.address+": "+((state.allDepartments+state.pendingDepartments).find{it.id==state.branchDepartments[point.id]}?.name.orEmpty()))
                }},
                editContent={groupPoints.forEach{point->
                    DepartmentChoice((state.allDepartments+state.pendingDepartments).filter{it.shopId==point.shopId},state.branchDepartments[point.id],
                        {viewModel.updateBranchDepartment(point.id,it)},
                        {name,done->viewModel.createShopDepartment(point.shopId,name,done)},state.isCreatingDepartment,state.departmentTypes,label=point.address,noneLabel="Без отдельной группы")
                }})
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ShopLinkViewCard(
    orderNumber: Int,
    shopName: String,
    departmentName: String?,
    onEditDepartment: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onEditDepartment != null) Modifier.clickable { onEditDepartment() } else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                "$orderNumber. $shopName",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                if (departmentName != null) "Группа магазина: $departmentName"
                else "Без группы",
                style = MaterialTheme.typography.bodySmall,
                color = if (departmentName != null)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else
                    MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ShopLinkEditCard(
    orderNumber: Int,
    shopName: String,
    departmentName: String?,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onEditDepartment: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onEditDepartment() }
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    "$orderNumber. $shopName",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    if (departmentName != null) "Группа: $departmentName"
                    else "Без группы",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (departmentName != null)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.primary
                )
            }
            com.spisokryadom.app.ui.components.ReorderButtons(onMoveUp,onMoveDown,!isFirst,!isLast, "Вверх", "Вниз")
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Удалить связь"
                )
            }
        }
    }
}

@Composable
private fun AddShopLinkDialog(
    shops:List<ShopEntity>,existingShopIds:Set<Long>,
    onConfirm:(Long,Long?,Set<Long>?)->Unit,onDismiss:()->Unit,onLoadDepartments:(Long)->Unit,
    departmentsForShop:List<ShopDepartmentEntity>,suggestedDepartmentId:Long?,suggestedDepartmentReason:String?,
    branches:List<com.spisokryadom.app.data.entity.ShopBranchEntity>,
    onCreateDepartment:(Long,String,(Long)->Unit)->Unit,onCreateShop:(String,(Long)->Unit)->Unit,creatingDepartment:Boolean,departmentsShopId:Long?,types:List<com.spisokryadom.app.data.entity.DepartmentTypeEntity>
){
    val available=shops.filter{it.id !in existingShopIds}
    val placeLabels=com.spisokryadom.app.ui.components.LocalPlaceLabels.current
    var shopId by androidx.compose.runtime.saveable.rememberSaveable{mutableLongStateOf(0L)}
    var creatingShop by remember{mutableStateOf(false)}
    var departmentId by remember(shopId){mutableStateOf<Long?>(null)}
    var selections by remember(shopId){mutableStateOf<Map<Long,Set<Long>>>(emptyMap())}
    var userSelected by remember(shopId){mutableStateOf(false)}
    LaunchedEffect(shopId){if(shopId>0)onLoadDepartments(shopId)}
    LaunchedEffect(suggestedDepartmentId,departmentsShopId){if(departmentsShopId==shopId&&!userSelected)departmentId=suggestedDepartmentId}
    val validDepartments=departmentsForShop.filter{it.shopId==shopId}
    val selectedPoints=selections[shopId]
    val dialogWidth=(androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp-48).dp
    AlertDialog(modifier=Modifier.width(dialogWidth),onDismissRequest={if(!creatingDepartment)onDismiss()},title={Text("Добавить магазин")},text={
        Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            DropdownSelector(label="Магазин",noneLabel="Без магазина",items=available,selectedItem=available.find{it.id==shopId},itemLabel={placeLabels.shop(it.id,it.name)},onSelect={if(!creatingDepartment)shopId=it?.id?:0L},allowCreate=true,onCreateNew={creatingShop=true},createLabel="Новый магазин…")
            if(creatingShop)com.spisokryadom.app.ui.components.InlineCreateValue("Название магазина",{name,done->onCreateShop(name){shopId=it;creatingShop=false;done()}},{creatingShop=false})
            if(shopId>0){
                androidx.compose.runtime.key(shopId){DepartmentChoice(validDepartments,departmentId,{departmentId=it;userSelected=true},{name,done->onCreateDepartment(shopId,name,done)},creatingDepartment,types)}
                com.spisokryadom.app.ui.components.BranchAvailabilityEditor(listOf(shopId to (available.find{it.id==shopId}?.name.orEmpty())),branches,selections,{selections=it})
            }

        }
    },confirmButton={TextButton(onClick={if(shopId==0L)onDismiss()else onConfirm(shopId,departmentId,selectedPoints)},enabled=shopId>=0&&!creatingDepartment&&(selectedPoints==null||selectedPoints.isNotEmpty())){Text("Добавить")}},
    dismissButton={TextButton(onClick=onDismiss,enabled=!creatingDepartment){Text("Отмена")}})
}

@Composable
private fun DepartmentSelectDialog(
    shopName:String,departments:List<ShopDepartmentEntity>,currentDepartmentId:Long?,
    suggestedDepartmentId:Long?,suggestedDepartmentReason:String?,onConfirm:(Long?)->Unit,onDismiss:()->Unit,
    onCreateDepartment:(String,(Long)->Unit)->Unit,creatingDepartment:Boolean,types:List<com.spisokryadom.app.data.entity.DepartmentTypeEntity>
){
    var selected by remember{mutableStateOf(currentDepartmentId)}
    val dialogWidth=(androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp-48).dp
    AlertDialog(modifier=Modifier.width(dialogWidth),onDismissRequest={if(!creatingDepartment)onDismiss()},title={Text("Группа магазина")},text={
        Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text(shopName,style=MaterialTheme.typography.titleSmall)
            DepartmentChoice(departments,selected,{selected=it},onCreateDepartment,creatingDepartment,types)
        }
    },confirmButton={TextButton(onClick={onConfirm(selected)},enabled=!creatingDepartment){Text("Применить")}},dismissButton={TextButton(onClick=onDismiss,enabled=!creatingDepartment){Text("Отмена")}})
}
