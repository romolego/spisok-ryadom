package com.spisokryadom.app.ui.components
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.spisokryadom.app.data.entity.ShopBranchEntity

private val OutletDraftSaver=androidx.compose.runtime.saveable.listSaver<ShopBranchEntity,Any>(
 save={listOf(it.id,it.shopId,it.address,it.displayOrder,it.note.orEmpty(),it.photoUri.orEmpty(),it.venueId?:0L,it.label)},
 restore={ShopBranchEntity(it[0] as Long,it[1] as Long,it[2] as String,it[3] as Int,(it[4] as String).ifBlank{null},(it[5] as String).ifBlank{null},(it[6] as Long).takeIf{v->v!=0L},it[7] as String)})

@Composable fun BranchesEditor(branches:List<ShopBranchEntity>,onChange:(List<ShopBranchEntity>)->Unit,onBusyChange:(Boolean)->Unit={},venues:List<com.spisokryadom.app.data.entity.VenueEntity> = emptyList(),externalPhotoBusy:Boolean=false,onVenueChange:(com.spisokryadom.app.data.entity.VenueEntity)->Unit={},cityId:Long=1) {
 var editingId by androidx.compose.runtime.saveable.rememberSaveable{mutableStateOf<Long?>(null)}
 var adding by androidx.compose.runtime.saveable.rememberSaveable{mutableStateOf(false)}
 val labels=com.spisokryadom.app.domain.PlaceLabels(branches,venues)
 Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
   Text("Точки магазина",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
   IconButton(onClick={adding=true}){Icon(Icons.Filled.Add,"Добавить точку")}
  }
  branches.forEachIndexed{index,point->key(point.id){
   OutlinedCard(Modifier.fillMaxWidth().testTag("branch-summary-${point.id}")){
    Column(Modifier.fillMaxWidth().padding(top=4.dp,bottom=12.dp)){
     Row(Modifier.fillMaxWidth().padding(start=12.dp),verticalAlignment=Alignment.CenterVertically){
      Text(if(index==0)"Основная точка"else "Приоритет ${index+1}",Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
      ReorderButtons({onChange(com.spisokryadom.app.domain.OutletLocations.move(branches,point.id,-1))},{onChange(com.spisokryadom.app.domain.OutletLocations.move(branches,point.id,1))},index>0,index<branches.lastIndex,"Точка выше: ${labels.branch(point)}","Точка ниже: ${labels.branch(point)}")
      IconButton(onClick={editingId=point.id}){Icon(Icons.Filled.Edit,"Изменить точку: ${labels.branch(point)}")}
     }
     Text(labels.branch(point),Modifier.fillMaxWidth().clickable{editingId=point.id}.padding(horizontal=12.dp).testTag("branch-summary-text-${point.id}"),style=MaterialTheme.typography.bodyMedium)
    }
   }
  }}
 }
 val point=if(adding)ShopBranchEntity(id=minOf(-1L,(branches.minOfOrNull{it.id}?:0L)-1L),shopId=0,address="")else branches.find{it.id==editingId}
 if(point!=null)key(point.id){
  var draft by androidx.compose.runtime.saveable.rememberSaveable(point.id,stateSaver=OutletDraftSaver){mutableStateOf(point)}
  var venueEditingId by androidx.compose.runtime.saveable.rememberSaveable{mutableStateOf<Long?>(null)}
  val venueEditing=venueEditingId?.let{id->venues.find{it.id==id}?:com.spisokryadom.app.data.entity.VenueEntity(id,cityId,"")}
  var photoBusy by remember{mutableStateOf(false)}
  var deleting by remember{mutableStateOf(false)}
  val venue=venues.find{it.id==draft.venueId}
  val candidate=if(venue==null)draft else draft.copy(address="")
  val duplicate=branches.any{it.id!=point.id&&com.spisokryadom.app.domain.OutletLocations.key(it)==com.spisokryadom.app.domain.OutletLocations.key(candidate)}
  fun close(){adding=false;editingId=null}
  if(deleting)ConfirmDialog("Удалить точку?","Ограничения доступности товаров будут проверены при сохранении магазина.",onConfirm={onChange(branches.filter{it.id!=point.id});deleting=false;close()},onDismiss={deleting=false})
  PlaceEditorDialog(if(adding)"Новая точка"else "Точка магазина",::close,{
   val saved=candidate.copy(displayOrder=if(adding)branches.size else point.displayOrder)
   onChange(if(adding)branches+saved else branches.map{if(it.id==saved.id)saved else it});close()
  },canSave=!duplicate&&(venue!=null||draft.address.isNotBlank()||draft.label.isNotBlank()),busy=photoBusy){
   DropdownSelector("Торговое место",venues,venue,{"${com.spisokryadom.app.data.entity.VenueKinds.label(it.kind)} · ${it.name}"},{selected->draft=draft.copy(venueId=selected?.id)},allowCreate=true,createLabel="Новое торговое место…",noneLabel="Отдельный адрес",alphabetical=true,onCreateNew={
    venueEditingId=minOf(-1L,(venues.minOfOrNull{it.id}?:0L)-1L)
   })
   if(venue!=null){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
     Column(Modifier.weight(1f)){
      Text("Общий адрес",style=MaterialTheme.typography.labelSmall)
      Text(venue.address?.takeIf{it.isNotBlank()}?:"Адрес пока не указан")
     }
     IconButton(onClick={venueEditingId=venue.id}){Icon(Icons.Filled.Edit,"Изменить рынок или ТЦ")}
    }
    venue.photoUri?.let{BranchPhotoPreview(it,"Рынок или ТЦ")}
    OutlinedTextField(draft.label,{draft=draft.copy(label=it)},label={Text("Павильон, этаж или место внутри")},modifier=Modifier.fillMaxWidth().testTag("branch-label-${point.id}"))
   }else {
    OutlinedTextField(draft.label,{draft=draft.copy(label=it)},label={Text("Название точки (необязательно)")},modifier=Modifier.fillMaxWidth())
    OutlinedTextField(draft.address,{draft=draft.copy(address=it)},label={Text("Адрес")},modifier=Modifier.fillMaxWidth().testTag("branch-address-${point.id}"))
   }
   if(duplicate)Text("Эта точка уже добавлена",color=MaterialTheme.colorScheme.error)
   OutlinedTextField(draft.note.orEmpty(),{draft=draft.copy(note=it)},label={Text("Заметка точки")},maxLines=3,modifier=Modifier.fillMaxWidth().testTag("branch-note-${point.id}"))
   BranchPhotoEditor(draft.photoUri.orEmpty(),{draft=draft.copy(photoUri=it)},{photoBusy=it;onBusyChange(it)},compact=true,showTitle=false,enabled=!externalPhotoBusy||photoBusy)
   if(!adding)IconButton(onClick={deleting=true}){Icon(Icons.Filled.Delete,"Удалить точку")}
  }
  venueEditing?.let{value->VenueEditorDialog(value,onDismiss={venueEditingId=null},onSave={saved->onVenueChange(saved);draft=draft.copy(venueId=saved.id)},allowCityChange=false)}
 }
}
