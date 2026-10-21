package com.spisokryadom.app.ui.goallists

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spisokryadom.app.SpisokRyadomApp
import com.spisokryadom.app.data.entity.GoalListEntity
import com.spisokryadom.app.data.repository.GoalListRepository
import com.spisokryadom.app.data.repository.ProductEditorTransactions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TemplateApplyState(
 val template:GoalListEntity?=null,val lists:List<GoalListEntity> = emptyList(),val count:Int=0,
 val loading:Boolean=true,val busy:Boolean=false,val error:String?=null,
 val completed:Boolean=false,val destinationId:Long?=null,val rowVersions:Map<Long,Long> = emptyMap()
)

class TemplateApplyViewModel(private val templateId:Long,private val repository:GoalListRepository):ViewModel(){
 private val generation=ProductEditorTransactions.generation.get()
 private val state=MutableStateFlow(TemplateApplyState())
 val uiState=state.asStateFlow()
 init{viewModelScope.launch{
  combine(repository.observeLists(),repository.observeEntries(templateId)){lists,rows->Triple(lists,rows.size,rows.associate{it.id to it.updatedAt})}
   .collect{(lists,count,versions)->state.update{it.copy(template=lists.find{l->l.id==templateId&&l.isTemplate},lists=lists.filterNot{l->l.isTemplate},count=count,rowVersions=versions,loading=false)}}
 }}
 fun apply(targetId:Long?,newName:String?){
  if(state.value.busy||state.value.completed||state.value.loading)return
  val expected=state.value.rowVersions
  state.update{it.copy(busy=true,error=null)}
  viewModelScope.launch{try{
   repository.withSession(generation){
    val destination=repository.applyTemplate(templateId,targetId,newName,expected)
    state.update{it.copy(completed=true,destinationId=destination)}
   }
  }catch(e:Exception){if(e is CancellationException)throw e;state.update{it.copy(error=e.message?:"Не удалось применить шаблон")}}
  finally{state.update{it.copy(busy=false)}}}
 }
 companion object{fun factory(id:Long)=viewModelFactory{initializer{TemplateApplyViewModel(id,(this[APPLICATION_KEY] as SpisokRyadomApp).container.goalListRepository)}}}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TemplateApplySheet(templateId:Long,session:String,onDismiss:()->Unit,model:TemplateApplyViewModel?=null,onApplied:(Long?)->Unit){
 val revision=com.spisokryadom.app.ui.batch.rememberDatabaseRevision()
 val vm=model?:androidx.lifecycle.viewmodel.compose.viewModel<TemplateApplyViewModel>(key="template-apply-$templateId-$session-$revision",factory=TemplateApplyViewModel.factory(templateId))
 val state by vm.uiState.collectAsStateWithLifecycle()
 var target by rememberSaveable(session){mutableStateOf<Long?>(null)}
 var name by rememberSaveable(session){mutableStateOf("")}
 val maxHeight=(androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp*.9f).dp
 LaunchedEffect(state.completed){if(state.completed)onApplied(state.destinationId)}
 val latestBusy by rememberUpdatedState(state.busy)
 ModalBottomSheet(onDismissRequest={if(!state.busy)onDismiss()},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true,confirmValueChange={it!=SheetValue.Hidden||!latestBusy})){
  Column(Modifier.fillMaxWidth().heightIn(max=maxHeight).imePadding().padding(horizontal=20.dp).testTag("template-apply-sheet"),verticalArrangement=Arrangement.spacedBy(12.dp)){
   Text("Применить шаблон",style=MaterialTheme.typography.headlineSmall)
   Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
    Text(state.template?.name.orEmpty(),style=MaterialTheme.typography.titleMedium)
    if(state.loading)LinearProgressIndicator(Modifier.fillMaxWidth())
    else if(state.template==null)Text("Шаблон удалён",color=MaterialTheme.colorScheme.error)
    Text("Куда добавить позиции",style=MaterialTheme.typography.titleSmall)
    GoalDestinationRow("Главная",target==null){if(!state.busy)target=null}
    GoalDestinationRow("Новый список",target==-1L){if(!state.busy)target=-1L}
    if(target==-1L)OutlinedTextField(name,{name=it},enabled=!state.busy,label={Text("Название нового списка")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("template-target-name"))
    state.lists.forEach{list->GoalDestinationRow(list.name,target==list.id){if(!state.busy)target=list.id}}
    Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=MaterialTheme.shapes.medium){
     Text(if(target==null)"Все позиции шаблона (${state.count}) будут добавлены на «Главную» и появятся в виджете. Шаблон сохранится."
      else "Все позиции шаблона (${state.count}) будут добавлены в выбранный список. Шаблон сохранится.",Modifier.padding(12.dp),style=MaterialTheme.typography.bodyMedium)
    }
    if(state.count==0&&!state.loading)Text("Сначала добавьте позиции в шаблон",color=MaterialTheme.colorScheme.onSurfaceVariant)
    state.error?.let{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("template-apply-error"))}
   }
   Button(onClick={vm.apply(target?.takeIf{it>0},if(target==-1L)name else null)},enabled=!state.loading&&!state.busy&&state.template!=null&&state.count>0&&(target!=-1L||name.isNotBlank())&&(target==null||target==-1L||state.lists.any{it.id==target}),modifier=Modifier.fillMaxWidth().testTag("template-apply-confirm")){
    Text(if(state.busy)"Добавление…"else if(target==null)"Добавить на Главную · ${state.count}"else "Добавить в список · ${state.count}")
   }
   TextButton(onClick=onDismiss,enabled=!state.busy,modifier=Modifier.fillMaxWidth()){Text("Отмена")}
   Spacer(Modifier.height(8.dp))
  }
 }
}
