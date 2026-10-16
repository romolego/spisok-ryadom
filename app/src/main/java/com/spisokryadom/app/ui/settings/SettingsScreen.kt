package com.spisokryadom.app.ui.settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spisokryadom.app.ui.components.*
import com.spisokryadom.app.data.entity.ProductEntity
import com.spisokryadom.app.data.entity.ShopEntity
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigateBack:()->Unit,viewModel:SettingsViewModel=viewModel(factory=SettingsViewModel.Factory)){
 val state by viewModel.uiState.collectAsStateWithLifecycle();val snack=remember{SnackbarHostState()}
 val recipients by viewModel.recipients.collectAsStateWithLifecycle();val groups by viewModel.productGroups.collectAsStateWithLifecycle();val units by viewModel.units.collectAsStateWithLifecycle();val depts by viewModel.departmentGroups.collectAsStateWithLifecycle()
 val recipientEdit by viewModel.recipientsEditMode.collectAsStateWithLifecycle();val groupEdit by viewModel.groupsEditMode.collectAsStateWithLifecycle();val unitEdit by viewModel.unitsEditMode.collectAsStateWithLifecycle();val deptEdit by viewModel.deptGroupsEditMode.collectAsStateWithLifecycle()
 val recipientId by viewModel.selectedRecipientId.collectAsStateWithLifecycle();val groupId by viewModel.selectedGroupId.collectAsStateWithLifecycle();val unitId by viewModel.selectedUnitId.collectAsStateWithLifecycle();val deptName by viewModel.selectedDepartmentGroup.collectAsStateWithLifecycle()
 val recipientProducts by viewModel.selectedRecipientProducts.collectAsStateWithLifecycle();val groupProducts by viewModel.selectedGroupProducts.collectAsStateWithLifecycle();val unitProducts by viewModel.selectedUnitProducts.collectAsStateWithLifecycle();val deptShops by viewModel.selectedDepartmentGroupShops.collectAsStateWithLifecycle()
 val urgency by viewModel.urgencyDefaults.collectAsStateWithLifecycle();val defaults by viewModel.addToListDefaults.collectAsStateWithLifecycle()
 var editKind by rememberSaveable{mutableStateOf<String?>(null)};var editId by rememberSaveable{mutableStateOf("")};var editName by rememberSaveable{mutableStateOf("")}
 var deleteAction by remember{mutableStateOf<(() -> Unit)?>(null)};var deleteName by remember{mutableStateOf("")}
 fun edit(kind:String,id:String,name:String){editKind=kind;editId=id;editName=name}
 fun remove(name:String,action:()->Unit){deleteName=name;deleteAction=action}
 val importLauncher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->uri?.let(viewModel::receiveImport)}
 val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip"),viewModel::saveExport)
 LaunchedEffect(state.pendingExportPath,state.exportLaunched){if(state.pendingExportPath!=null&&!state.exportLaunched){viewModel.exportLaunched();export.launch("spisok_ryadom_${LocalDate.now()}.zip")}}
 LaunchedEffect(state.message){state.message?.let{snack.showSnackbar(it);viewModel.clearMessage()}}
 if(state.pendingImportPath!=null)ConfirmDialog("Восстановление",state.importSummary.orEmpty()+" Продолжить?",viewModel::importSelected,viewModel::cancelImport)
 deleteAction?.let{action->ConfirmDialog("Удалить $deleteName?","Связи с этим справочником будут сняты. Сами товары сохранятся.",onConfirm={action();deleteAction=null},onDismiss={deleteAction=null})}
 editKind?.let{kind->InputDialog(title=if(editId.isBlank())"Добавить" else "Переименовать",initialValue=editName,label="Название",onConfirm={name->
  when(kind){
   "recipient"->if(editId.isBlank())viewModel.createRecipient(name)else recipients.find{it.id.toString()==editId}?.let{viewModel.renameRecipient(it,name)}
   "group"->if(editId.isBlank())viewModel.createProductGroup(name)else groups.find{it.id.toString()==editId}?.let{viewModel.renameProductGroup(it,name)}
   "unit"->if(editId.isBlank())viewModel.createUnit(name)else units.find{it.id.toString()==editId}?.let{viewModel.renameUnit(it,name)}
   "dept"->if(editId.isBlank())viewModel.createDepartmentGroup(name)else viewModel.renameDepartmentGroup(editId,name)
  };editKind=null
 },onDismiss={editKind=null})}
 if(recipientId!=null)UsageDialog("Товары получателя",recipientProducts.map{it.name}){viewModel.selectRecipient(null)}
 if(groupId!=null)UsageDialog("Товары группы",groupProducts.map{it.name}){viewModel.selectProductGroup(null)}
 if(unitId!=null)UsageDialog("Используют единицу",unitProducts.map{it.name}){viewModel.selectUnit(null)}
 if(deptName!=null)UsageDialog("Магазины группы",deptShops.map{it.name}){viewModel.selectDepartmentGroup(null)}
 Scaffold(topBar={TopAppBar(title={Text("Настройки")},navigationIcon={IconButton(onClick=onNavigateBack,enabled=!state.isLoading){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Назад")}})},snackbarHost={SnackbarHost(snack)}){padding->
  LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   item(key="location"){CityPicker(enabled=!state.isLoading)}
   item(key="backup"){
    Text("Резервные копии и перенос",style=MaterialTheme.typography.titleLarge)
    Text("Полная ZIP-копия сохраняет товары, фото, города и текущие покупки. Старый JSON-каталог можно перенести без удаления существующих данных.")
    Button(onClick=viewModel::prepareExport,enabled=!state.isLoading,modifier=Modifier.fillMaxWidth()){Text("Сохранить полную копию")}
    OutlinedButton(onClick={importLauncher.launch(arrayOf("application/zip","application/json","application/octet-stream","*/*"))},enabled=!state.isLoading,modifier=Modifier.fillMaxWidth()){Text("Импорт / Восстановление")}
    if(state.isLoading)LinearProgressIndicator(Modifier.fillMaxWidth())
   }
   item(key="defaults"){
    HorizontalDivider();Text("Поведение списка",style=MaterialTheme.typography.titleLarge)
    SettingSwitch("Показывать срочные в свёрнутых магазинах",urgency.showUrgentInCollapsed,viewModel::setGlobalShowUrgentInCollapsed)
    SettingSwitch("Поднимать магазины со срочными покупками",urgency.boostUrgentShop,viewModel::setGlobalBoostUrgentShop)
    SettingSwitch("Новые товары по умолчанию сохранять в базе",defaults.saveNewProductToDatabase,viewModel::setGlobalSaveNewProductToDatabase)
   }
   item(key="recipients_title"){ManagerTitle("Получатели",recipientEdit,viewModel::toggleRecipientsEditMode)}
   itemsIndexed(recipients,key={_,v->"recipient_${v.id}"},contentType={_,_->"manager"}){index,item->ManagerRow(item.name,recipientEdit,{viewModel.selectRecipient(item.id)},{edit("recipient",item.id.toString(),item.name)},{remove(item.name){viewModel.deleteRecipient(item)}},{viewModel.moveRecipientUp(item)},{viewModel.moveRecipientDown(item)},index==0,index==recipients.lastIndex)}
   item(key="recipient_add"){TextButton(onClick={edit("recipient","","")}){Text("Добавить получателя")}}
   item(key="groups_title"){ManagerTitle("Группы товаров",groupEdit,viewModel::toggleGroupsEditMode)}
   itemsIndexed(groups,key={_,v->"group_${v.id}"},contentType={_,_->"manager"}){index,item->ManagerRow(item.name,groupEdit,{viewModel.selectProductGroup(item.id)},{edit("group",item.id.toString(),item.name)},{remove(item.name){viewModel.deleteProductGroup(item)}},{viewModel.moveProductGroupUp(item)},{viewModel.moveProductGroupDown(item)},index==0,index==groups.lastIndex)}
   item(key="group_add"){TextButton(onClick={edit("group","","")}){Text("Добавить группу товаров")}}
   item(key="units_title"){ManagerTitle("Единицы измерения",unitEdit,viewModel::toggleUnitsEditMode)}
   itemsIndexed(units,key={_,v->"unit_${v.id}"},contentType={_,_->"manager"}){index,item->ManagerRow(item.name,unitEdit,{viewModel.selectUnit(item.id)},{edit("unit",item.id.toString(),item.name)},{remove(item.name){viewModel.deleteUnit(item)}},{viewModel.moveUnitUp(item)},{viewModel.moveUnitDown(item)},index==0,index==units.lastIndex)}
   item(key="unit_add"){TextButton(onClick={edit("unit","","")}){Text("Добавить единицу")}}
   item(key="dept_title"){ManagerTitle("Группы магазинов",deptEdit,viewModel::toggleDeptGroupsEditMode)}
   itemsIndexed(depts,key={_,v->"dept_${v.name}"},contentType={_,_->"manager"}){_,item->ManagerRow(item.name,deptEdit,{viewModel.selectDepartmentGroup(item.name)},{edit("dept",item.name,item.name)},{remove(item.name){viewModel.deleteDepartmentGroup(item.name)}},{},{},true,true)}
   item(key="dept_add"){TextButton(onClick={edit("dept","","")}){Text("Добавить группу магазинам этого города")}}
   item{Spacer(Modifier.height(32.dp))}
  }
 }
}
@Composable private fun SettingSwitch(label:String,value:Boolean,onChange:(Boolean)->Unit){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f));Switch(value,onChange)}}
@Composable private fun ManagerTitle(title:String,edit:Boolean,onEdit:()->Unit){HorizontalDivider();Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);TextButton(onClick=onEdit){Text(if(edit)"Готово" else "Править")}}}
@Composable private fun ManagerRow(name:String,editing:Boolean,onSelect:()->Unit,onRename:()->Unit,onDelete:()->Unit,onUp:()->Unit,onDown:()->Unit,first:Boolean,last:Boolean){
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
  Text(name,Modifier.weight(1f).clickable(onClick=onSelect).padding(vertical=12.dp))
  if(editing){
   if(!first||!last){IconButton(onClick=onUp,enabled=!first){Icon(Icons.Filled.ArrowUpward,"Вверх")};IconButton(onClick=onDown,enabled=!last){Icon(Icons.Filled.ArrowDownward,"Вниз")}}
   IconButton(onClick=onRename){Icon(Icons.Filled.Edit,"Переименовать")};IconButton(onClick=onDelete){Icon(Icons.Filled.Delete,"Удалить")}
  }
 }
}
@Composable private fun UsageDialog(title:String,names:List<String>,onDismiss:()->Unit){AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={LazyColumn(Modifier.heightIn(max=400.dp)){if(names.isEmpty())item{Text("Пока не используется")};itemsIndexed(names,key={i,_->i}){_,name->Text(name,Modifier.fillMaxWidth().padding(vertical=8.dp))}}},confirmButton={TextButton(onClick=onDismiss){Text("Закрыть")}})}
