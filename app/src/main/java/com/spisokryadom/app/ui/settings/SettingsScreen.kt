package com.spisokryadom.app.ui.settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spisokryadom.app.ui.components.*
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigateBack:()->Unit,viewModel:SettingsViewModel=viewModel(factory=SettingsViewModel.Factory)){
 val listState=androidx.compose.foundation.lazy.rememberLazyListState()
 val state by viewModel.uiState.collectAsStateWithLifecycle();val snack=remember{SnackbarHostState()}
 val recipients by viewModel.recipients.collectAsStateWithLifecycle();val groups by viewModel.productGroups.collectAsStateWithLifecycle();val units by viewModel.units.collectAsStateWithLifecycle();val depts by viewModel.departmentGroups.collectAsStateWithLifecycle()
 val viewport=rememberReorderViewport(listState,recipients.map{"r${it.id}"}+groups.map{"g${it.id}:${it.isFavorite}:${it.favoriteOrder}"}+units.map{"u${it.id}"}+depts.map{"d${it.id}:${it.isFavorite}:${it.favoriteOrder}"})
 val recipientEdit by viewModel.recipientsEditMode.collectAsStateWithLifecycle();val groupEdit by viewModel.groupsEditMode.collectAsStateWithLifecycle();val unitEdit by viewModel.unitsEditMode.collectAsStateWithLifecycle();val deptEdit by viewModel.deptGroupsEditMode.collectAsStateWithLifecycle()
 val recipientId by viewModel.selectedRecipientId.collectAsStateWithLifecycle();val groupId by viewModel.selectedGroupId.collectAsStateWithLifecycle();val unitId by viewModel.selectedUnitId.collectAsStateWithLifecycle();val deptName by viewModel.selectedDepartmentGroup.collectAsStateWithLifecycle()
 val recipientProducts by viewModel.selectedRecipientProducts.collectAsStateWithLifecycle();val groupProducts by viewModel.selectedGroupProducts.collectAsStateWithLifecycle();val unitProducts by viewModel.selectedUnitProducts.collectAsStateWithLifecycle();val deptShops by viewModel.selectedDepartmentGroupShops.collectAsStateWithLifecycle()
 val urgency by viewModel.urgencyDefaults.collectAsStateWithLifecycle();val defaults by viewModel.addToListDefaults.collectAsStateWithLifecycle()
 val groupPresets by viewModel.groupCreationPresets.collectAsStateWithLifecycle()
 var presetGroup by remember{mutableStateOf<com.spisokryadom.app.data.entity.ProductGroupEntity?>(null)}
 var presetBusy by remember{mutableStateOf(false)}
 var presetRecipient by remember{mutableStateOf<Long?>(null)}
 presetGroup?.let{group->AlertDialog(modifier=Modifier.width(minOf(560.dp,androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp-48.dp)),onDismissRequest={if(!presetBusy)presetGroup=null},title={Text("Преднастройка группы")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text(group.name)
  DropdownSelector(label="Получатель по умолчанию",items=recipients,selectedItem=recipients.find{it.id==presetRecipient},itemLabel={it.name},onSelect={if(!presetBusy)presetRecipient=it?.id},noneLabel="Не назначать автоматически")
  HelpButton("Получатель по умолчанию","Применяется к новым товарам. Явный выбор получателя в карточке сохраняется.")
 }},confirmButton={TextButton(enabled=!presetBusy,onClick={presetBusy=true;viewModel.setGroupDefaultRecipient(group.id,presetRecipient){saved->presetBusy=false;if(saved)presetGroup=null}}){Text(if(presetBusy)"Сохранение…"else "Сохранить")}},dismissButton={TextButton(enabled=!presetBusy,onClick={presetGroup=null}){Text("Отмена")}})}
 val widgetSettings by viewModel.widgetSettings.collectAsStateWithLifecycle()
 var editKind by rememberSaveable{mutableStateOf<String?>(null)};var editId by rememberSaveable{mutableStateOf("")};var editName by rememberSaveable{mutableStateOf("")}
 var sortKind by remember{mutableStateOf<String?>(null)}
 var styleTarget by remember{mutableStateOf<Triple<String,Long,Pair<String,String>>?>(null)}
 var deleteAction by remember{mutableStateOf<(() -> Unit)?>(null)};var deleteName by remember{mutableStateOf("")}
 fun edit(kind:String,id:String,name:String){editKind=kind;editId=id;editName=name}
 fun remove(name:String,action:()->Unit){deleteName=name;deleteAction=action}
 val importLauncher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->uri?.let(viewModel::receiveImport)}
 val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip"),viewModel::saveExport)
 LaunchedEffect(state.pendingExportPath,state.exportLaunched){if(state.pendingExportPath!=null&&!state.exportLaunched){viewModel.exportLaunched();export.launch("spisok_ryadom_${LocalDate.now()}.zip")}}
 LaunchedEffect(state.message){state.message?.let{snack.showSnackbar(it);viewModel.clearMessage()}}
 sortKind?.let{kind->ConfirmDialog("Сортировать по алфавиту?","Текущий порядок будет сброшен. Позиции будут расположены по алфавиту.",{viewModel.alphabetize(kind);sortKind=null},{sortKind=null})}
 styleTarget?.let{(kind,id,style)->AppearanceSheet("Оформление группы",style.first,style.second,onSave={accent,icon->viewModel.style(kind,id,accent,icon);styleTarget=null},onDismiss={styleTarget=null})}
 if(state.pendingImportPath!=null)ConfirmDialog("Восстановление",state.importSummary.orEmpty()+" Продолжить?",viewModel::importSelected,viewModel::cancelImport)
 deleteAction?.let{action->ConfirmDialog("Удалить $deleteName?","Связи с этим справочником будут сняты. Сами товары сохранятся.",onConfirm={action();deleteAction=null},onDismiss={deleteAction=null})}
 editKind?.let{kind->InputDialog(title=if(editId.isBlank())"Добавить" else "Переименовать",initialValue=editName,label="Название",onConfirm={name->
  when(kind){
   "recipient"->if(editId.isBlank())viewModel.createRecipient(name)else recipients.find{it.id.toString()==editId}?.let{viewModel.renameRecipient(it,name)}
   "group"->if(editId.isBlank())viewModel.createProductGroup(name)else groups.find{it.id.toString()==editId}?.let{viewModel.renameProductGroup(it,name)}
   "unit"->if(editId.isBlank())viewModel.createUnit(name)else units.find{it.id.toString()==editId}?.let{viewModel.renameUnit(it,name)}
   "dept"->if(editId.isBlank())viewModel.createDepartmentGroup(name)else viewModel.renameDepartmentGroup(editId.toLong(),name)
  };editKind=null
 },onDismiss={editKind=null})}
 if(recipientId!=null)UsageDialog("Товары получателя",recipientProducts.map{it.name}){viewModel.selectRecipient(null)}
 if(groupId!=null)UsageDialog("Товары группы",groupProducts.map{it.name}){viewModel.selectProductGroup(null)}
 if(unitId!=null)UsageDialog("Используют единицу",unitProducts.map{it.name}){viewModel.selectUnit(null)}
 if(deptName!=null)UsageDialog("Магазины группы",deptShops.map{it.name}){viewModel.selectDepartmentGroup(null)}
 Scaffold(topBar={TopAppBar(title={Text("Настройки")},navigationIcon={IconButton(onClick=onNavigateBack,enabled=!state.isLoading){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Назад")}})},snackbarHost={SnackbarHost(snack)}){padding->
  LazyColumn(state=listState,modifier=Modifier.testTag("settings-list").fillMaxSize().padding(padding).consumeWindowInsets(padding).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   item(key="location"){CityPicker(enabled=!state.isLoading)}
   item(key="backup"){
    OutlinedCard(colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Filled.Backup,null,tint=MaterialTheme.colorScheme.primary); Text("Резервные копии и перенос",style=MaterialTheme.typography.titleMedium) }
    Button(onClick=viewModel::prepareExport,enabled=!state.isLoading,modifier=Modifier.fillMaxWidth()){Text("Сохранить полную копию")}
    OutlinedButton(onClick={importLauncher.launch(arrayOf("application/zip","application/json","application/octet-stream","*/*"))},enabled=!state.isLoading,modifier=Modifier.fillMaxWidth()){Text("Импорт / Восстановление")}
    if(state.isLoading)LinearProgressIndicator(Modifier.fillMaxWidth())
    }}
   }
   item(key="defaults"){
    OutlinedCard(colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(16.dp)) {
    Text("Поведение списка",style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(bottom=8.dp))
    SettingSwitch("Показывать срочные в свёрнутых магазинах",urgency.showUrgentInCollapsed,viewModel::setGlobalShowUrgentInCollapsed)
    SettingSwitch("Поднимать магазины со срочными покупками",urgency.boostUrgentShop,viewModel::setGlobalBoostUrgentShop)
    SettingSwitch("Сохранять приоритет магазина при переносе товара",defaults.rememberShopPriorityOnMove,viewModel::setRememberShopPriorityOnMove)
    SettingSwitch("Новые товары по умолчанию сохранять в базе",defaults.saveNewProductToDatabase,viewModel::setGlobalSaveNewProductToDatabase)
    SettingSwitch("Показывать товары до ввода названия",defaults.browseProductsOnAdd,viewModel::setBrowseProductsOnAdd)
    SettingSwitch("Автосохранение карточек товаров",defaults.autoSaveProductCards,viewModel::setAutoSaveProductCards,help="Если выключено, изменения карточки сохраняются только кнопкой «Сохранить». Если включено, незавершённый ввод сохраняется при выходе.")
    }}
   }
   item(key="creation_defaults"){
    OutlinedCard(colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
     Text("Новые товары",style=MaterialTheme.typography.titleMedium)
     var quantity by rememberSaveable{mutableStateOf(java.math.BigDecimal.valueOf(defaults.missingQuantity).stripTrailingZeros().toPlainString())}
     var quantityFocused by remember{mutableStateOf(false)}
     LaunchedEffect(defaults.missingQuantity){if(!quantityFocused)quantity=java.math.BigDecimal.valueOf(defaults.missingQuantity).stripTrailingZeros().toPlainString()}
     SettingWithHelp("Количество, если не заполнено","Подставляется при сохранении новой карточки, если количество не введено. Явно введённое количество сохраняется без замены."){
      OutlinedTextField(value=quantity,onValueChange={quantity=it;viewModel.setMissingQuantity(it)},label={Text("Количество, если не заполнено")},singleLine=true,isError=com.spisokryadom.app.util.Quantities.parse(quantity)==null,keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Decimal),modifier=Modifier.fillMaxWidth().onFocusChanged{quantityFocused=it.isFocused})
     }
     DropdownSelector(label="Тип покупки по умолчанию",items=listOf("offline","online"),selectedItem=defaults.defaultPurchaseType,itemLabel={if(it=="online")"Онлайн"else "Офлайн"},onSelect={it?.let(viewModel::setDefaultPurchaseType)},allowNone=false)
     SettingWithHelp("Если единица не заполнена","Единица подставляется при сохранении новой карточки. До сохранения поле остаётся свободным для ввода."){DropdownSelector(label="Если единица не заполнена",items=(units.map{com.spisokryadom.app.util.Quantities.unit(it.name)}+defaults.missingUnit).filter{it.isNotBlank()}.distinct(),selectedItem=defaults.missingUnit.ifBlank{null},itemLabel={it},onSelect={viewModel.setMissingUnit(it.orEmpty())},noneLabel="Оставлять пустой")}

    }}
   }
   item(key="widget_settings"){WidgetSettingsCard(widgetSettings,viewModel::updateWidgetSettings)}
   item(key="favorite_settings"){FavoriteSettingsCard()}
   item(key="dropdowns_title"){Column{
    Text("Выпадающие списки",style=MaterialTheme.typography.headlineSmall,modifier=Modifier.padding(top=16.dp))
    Row(verticalAlignment=Alignment.CenterVertically){OutlinedButton(onClick=viewModel::addStandardCategories,modifier=Modifier.weight(1f)){Text("Добавить недостающие группы")};HelpButton("Готовые группы","Добавляет недостающие товарные группы и группы магазинов из встроенного набора. Ваши названия, товары, маршруты и связи не меняются. Отделы не назначаются автоматически всем магазинам. Если набор уже полный, повторное нажатие ничего не добавляет.")}
   }}
   item(key="recipient_title"){ManagerTitle("Получатели",recipientEdit,viewModel::toggleRecipientsEditMode)}
   itemsIndexed(recipients,key={_,v->"recipient_${v.id}"},contentType={_,_->"manager"}){index,item->
    Box(Modifier.animateItem(fadeInSpec=null,fadeOutSpec=null,placementSpec=androidx.compose.animation.core.tween(300))){ManagerRow(item.name,recipientEdit,{viewModel.selectRecipient(item.id)},{edit("recipient",item.id.toString(),item.name)},{remove(item.name){viewModel.deleteRecipient(item)}},{viewport.capture();viewModel.moveRecipientUp(item)},{viewport.capture();viewModel.moveRecipientDown(item)},index==0,index==recipients.lastIndex,accent=item.accent,iconKey=item.iconKey,onStyle={styleTarget=Triple("recipient",item.id,item.accent to item.iconKey)})}
   }
   if(recipientEdit)item(key="recipient_add"){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){TextButton(onClick={edit("recipient","","")},modifier=Modifier.weight(1f)){Icon(Icons.Filled.Add,null);Spacer(Modifier.width(8.dp));Text("Добавить получателя")};IconButton(onClick={sortKind="recipient"}){Icon(Icons.Filled.SortByAlpha,"По алфавиту: Получатели")}}}
   item(key="group_title"){ManagerTitle("Товарные группы",groupEdit,viewModel::toggleGroupsEditMode)}
   val favoriteGroups=groups.filter{it.isFavorite}.sortedWith(compareBy<com.spisokryadom.app.data.entity.ProductGroupEntity>{it.favoriteOrder}.thenBy{it.name})
   if(favoriteGroups.isNotEmpty())item(key="group_favorites_title"){FavoriteBlock(Modifier.fillMaxWidth()){Text("Избранные",Modifier.padding(8.dp),style=MaterialTheme.typography.titleSmall)}}
   itemsIndexed(favoriteGroups+groups,key={i,v->if(i<favoriteGroups.size)"group_favorite_${v.id}"else "group_${v.id}"},contentType={_,_->"manager"}){index,item->
    val pinned=index<favoriteGroups.size
    val row:@Composable ()->Unit={ManagerRow(item.name,groupEdit,{viewModel.selectProductGroup(item.id)},{edit("group",item.id.toString(),item.name)},{remove(item.name){viewModel.deleteProductGroup(item)}},{viewport.capture();viewModel.moveProductGroupUp(item)},{viewport.capture();viewModel.moveProductGroupDown(item)},index==0,index==favoriteGroups.lastIndex,accent=item.accent,iconKey=item.iconKey,onStyle={styleTarget=Triple("group",item.id,item.accent to item.iconKey)},
     subtitle=groupPresets.find{it.groupId==item.id}?.recipientId?.let{id->recipients.find{it.id==id}?.name?.let{"Получатель по умолчанию: $it"}},
     onDefaults={presetRecipient=groupPresets.find{it.groupId==item.id}?.recipientId;presetGroup=item},reorderable=pinned,favorite=item.isFavorite,onFavorite={viewModel.favoriteGroup(item.id,!item.isFavorite)})}
    if(pinned)FavoriteBlock(Modifier.fillMaxWidth()){row()}else Column{if(index==favoriteGroups.size&&favoriteGroups.isNotEmpty())Text("Все группы · А–Я",Modifier.padding(vertical=8.dp),style=MaterialTheme.typography.titleSmall);row()}
   }
   if(groupEdit)item(key="group_add"){TextButton(onClick={edit("group","","")}){Icon(Icons.Filled.Add,null);Text("Добавить группу товаров")}}
   item(key="unit_title"){ManagerTitle("Единицы измерения",unitEdit,viewModel::toggleUnitsEditMode)}
   itemsIndexed(units,key={_,v->"unit_${v.id}"},contentType={_,_->"manager"}){index,item->
    Box(Modifier.animateItem(fadeInSpec=null,fadeOutSpec=null,placementSpec=androidx.compose.animation.core.tween(300))){ManagerRow(item.name,unitEdit,{viewModel.selectUnit(item.id)},{edit("unit",item.id.toString(),item.name)},{remove(item.name){viewModel.deleteUnit(item)}},{viewport.capture();viewModel.moveUnitUp(item)},{viewport.capture();viewModel.moveUnitDown(item)},index==0,index==units.lastIndex)}
   }
   if(unitEdit)item(key="unit_add"){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){TextButton(onClick={edit("unit","","")},modifier=Modifier.weight(1f)){Icon(Icons.Filled.Add,null);Spacer(Modifier.width(8.dp));Text("Добавить единицу")};IconButton(onClick={sortKind="unit"}){Icon(Icons.Filled.SortByAlpha,"По алфавиту: Единицы измерения")}}}
   item(key="dept_title"){ManagerTitle("Группы магазинов",deptEdit,viewModel::toggleDeptGroupsEditMode)}
   val favoriteDepts=depts.filter{it.isFavorite}.sortedWith(compareBy<DepartmentGroup>{it.favoriteOrder}.thenBy{it.name})
   if(favoriteDepts.isNotEmpty())item(key="dept_favorites_title"){FavoriteBlock(Modifier.fillMaxWidth()){Text("Избранные",Modifier.padding(8.dp),style=MaterialTheme.typography.titleSmall)}}
   itemsIndexed(favoriteDepts+depts,key={i,v->if(i<favoriteDepts.size)"dept_favorite_${v.id}"else "dept_${v.id}"},contentType={_,_->"manager"}){index,item->
    val pinned=index<favoriteDepts.size
    val row:@Composable ()->Unit={ManagerRow(item.name,deptEdit,{viewModel.selectDepartmentGroup(item.id)},{edit("dept",item.id.toString(),item.name)},{remove(item.name){viewModel.deleteDepartmentGroup(item.id)}},{viewport.capture();viewModel.moveDepartment(item.id,-1)},{viewport.capture();viewModel.moveDepartment(item.id,1)},index==0,index==favoriteDepts.lastIndex,accent=item.accent,iconKey=item.iconKey,onStyle={styleTarget=Triple("dept",item.id,item.accent to item.iconKey)},reorderable=pinned,favorite=item.isFavorite,onFavorite={viewModel.favoriteDepartment(item.id,!item.isFavorite)})}
    if(pinned)FavoriteBlock(Modifier.fillMaxWidth()){row()}else Column{if(index==favoriteDepts.size&&favoriteDepts.isNotEmpty())Text("Все группы · А–Я",Modifier.padding(vertical=8.dp),style=MaterialTheme.typography.titleSmall);row()}
   }
   if(deptEdit)item(key="dept_add"){TextButton(onClick={edit("dept","","")}){Icon(Icons.Filled.Add,null);Text("Добавить группу магазина")}}
   item{Spacer(Modifier.height(32.dp))}
  }
 }
}
@Composable internal fun SettingSwitch(label:String,value:Boolean,onChange:(Boolean)->Unit,help:String?=null){Row(Modifier.fillMaxWidth().padding(vertical=6.dp).heightIn(min=48.dp).toggleable(value,role=androidx.compose.ui.semantics.Role.Switch,onValueChange=onChange).padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f).padding(end=16.dp),style=MaterialTheme.typography.bodyLarge.copy(lineHeight=21.sp));if(help!=null)HelpButton(label,help);Switch(value,null)}}
@Composable private fun ManagerTitle(title:String,edit:Boolean,onEdit:()->Unit){HorizontalDivider();Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);TextButton(onClick=onEdit,modifier=Modifier.testTag("manager-edit-$title")){Text(if(edit)"Готово" else "Править")}}}
@Composable private fun ManagerRow(name:String,editing:Boolean,onSelect:()->Unit,onRename:()->Unit,onDelete:()->Unit,onUp:()->Unit,onDown:()->Unit,first:Boolean,last:Boolean,accent:String="",iconKey:String="",onStyle:(()->Unit)?=null,subtitle:String?=null,onDefaults:(()->Unit)?=null,reorderable:Boolean=true,favorite:Boolean=false,onFavorite:(()->Unit)?=null){
 Column(Modifier.fillMaxWidth()){
  FavoriteMarkedLabel(name,accent,iconKey,favorite,Modifier.fillMaxWidth().clickable(onClick=onSelect).padding(vertical=12.dp))
  subtitle?.let{Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
  if(editing)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically){
   if(reorderable)ReorderButtons(onUp,onDown,!first,!last,"Вверх: $name","Вниз: $name")
   if(onFavorite!=null)IconButton(onClick=onFavorite){Icon(if(favorite)Icons.Filled.Star else Icons.Filled.StarBorder,if(favorite)"Убрать из избранного: $name"else "В избранное: $name")}
   if((onFavorite==null||!reorderable)&&onDefaults!=null)IconButton(onClick=onDefaults){Icon(Icons.Filled.PersonAdd,"Преднастройка: $name")}
   if((onFavorite==null||!reorderable)&&onStyle!=null)IconButton(onClick=onStyle){Icon(Icons.Filled.Palette,"Оформление: $name")}
   if(onFavorite==null||!reorderable){IconButton(onClick=onRename){Icon(Icons.Filled.Edit,"Переименовать: $name")};IconButton(onClick=onDelete){Icon(Icons.Filled.Delete,"Удалить: $name")}}
  }
 }
}
@Composable private fun UsageDialog(title:String,names:List<String>,onDismiss:()->Unit){AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={LazyColumn(Modifier.heightIn(max=400.dp)){if(names.isEmpty())item{Text("Пока не используется")};itemsIndexed(names,key={i,_->i}){_,name->Text(name,Modifier.fillMaxWidth().padding(vertical=8.dp))}}},confirmButton={TextButton(onClick=onDismiss){Text("Закрыть")}})}
