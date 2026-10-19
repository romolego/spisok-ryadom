package com.spisokryadom.app.ui.components
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.spisokryadom.app.data.entity.ShopBranchEntity

@Composable fun BranchesEditor(branches:List<ShopBranchEntity>,onChange:(List<ShopBranchEntity>)->Unit,onBusyChange:(Boolean)->Unit={},venues:List<com.spisokryadom.app.data.entity.VenueEntity> = emptyList(),venueNames:Map<Long,String> = emptyMap(),onVenueName:(Long,String)->Unit={_,_->},pointGroups:@Composable (ShopBranchEntity)->Unit={}) {
 var photoBusy by remember{mutableStateOf(false)}
 var editingId by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<Long?>(null) }
 val focus=androidx.compose.ui.platform.LocalFocusManager.current
 Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
  Text("Адреса и точки магазина",style=MaterialTheme.typography.titleMedium)
  AnimatedOrderColumn(branches,{it.id}) { index,branch ->
   val editing=editingId==branch.id
   val duplicate=branches.any{it.id!=branch.id&&it.address.trim().equals(branch.address.trim(),ignoreCase=true)}
   OutlinedCard(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
     Row(verticalAlignment=Alignment.CenterVertically) {
      Column(Modifier.weight(1f).then(if(!editing)Modifier.clickable(enabled=!photoBusy){editingId=branch.id}else Modifier)) {
       Text(if(index==0)"Основная точка"else "Приоритет ${index+1}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
       if(!editing){
        Text(branch.address,style=MaterialTheme.typography.bodyLarge)
        venues.find{it.id==branch.venueId}?.let{Text(it.name,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
       }
      }
      IconButton(onClick={if(editing){focus.clearFocus();editingId=null}else editingId=branch.id},enabled=!photoBusy&&(!editing||branch.address.isNotBlank()&&!duplicate)) {
       Icon(if(editing)Icons.Filled.Check else Icons.Filled.Edit,if(editing)"Завершить редактирование адреса"else "Изменить адрес: ${branch.address}")
      }
     }
     if(!editing){
      branch.note?.takeIf{it.isNotBlank()}?.let{Text(it,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
      branch.photoUri?.takeIf{it.isNotBlank()}?.let{BranchPhotoPreview(it)}
     }
     if(editing) {
      OutlinedTextField(branch.address,{value->onChange(branches.map{if(it.id==branch.id)it.copy(address=value)else it})},
       label={Text("Адрес")},minLines=1,maxLines=4,isError=branch.address.isBlank()||duplicate,
       supportingText=if(duplicate)({Text("Адрес добавлен дважды")})else null,
       modifier=Modifier.fillMaxWidth().testTag("branch-address-${branch.id}"))
      OutlinedTextField(branch.note.orEmpty(),{value->onChange(branches.map{if(it.id==branch.id)it.copy(note=value)else it})},label={Text("Заметка точки")},minLines=2,maxLines=5,modifier=Modifier.fillMaxWidth().testTag("branch-note-${branch.id}"))
      OptionalRetailField("Рынок или ТЦ",venues.map{it.id to it.name},branch.venueId,venueNames[branch.id].orEmpty(),
       onSelect={id->onChange(branches.map{if(it.id==branch.id)it.copy(venueId=id)else it})},onNewName={onVenueName(branch.id,it)})
      BranchPhotoEditor(branch.photoUri.orEmpty(),onChange={path->onChange(branches.map{if(it.id==branch.id)it.copy(photoUri=path)else it})},onBusyChange={value->photoBusy=value;onBusyChange(value)})
      pointGroups(branch)
      Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
       ReorderButtons(
        onUp={focus.clearFocus();val list=branches.toMutableList();list.add(index-1,list.removeAt(index));onChange(list)},
        onDown={focus.clearFocus();val list=branches.toMutableList();list.add(index+1,list.removeAt(index));onChange(list)},
        canMoveUp=!photoBusy&&index>0,canMoveDown=!photoBusy&&index<branches.lastIndex,
        upDescription="Точка выше: ${branch.address}",downDescription="Точка ниже: ${branch.address}")
       IconButton(onClick={onChange(branches.filter{it.id!=branch.id});editingId=null},enabled=!photoBusy){Icon(Icons.Filled.Delete,"Удалить точку: ${branch.address}")}
      }
     }
    }
   }
  }
  OutlinedButton(onClick={
   val id=minOf(-1L,(branches.minOfOrNull{it.id}?:0L)-1L)
   onChange(branches+ShopBranchEntity(id=id,shopId=0,address=""));editingId=id
  },enabled=!photoBusy,modifier=Modifier.fillMaxWidth()){Icon(Icons.Filled.Add,null);Text("Добавить адрес")}
 }
}

@Composable fun BranchAvailabilityEditor(shops:List<Pair<Long,String>>,branches:List<ShopBranchEntity>,selected:Map<Long,Set<Long>>,onChange:(Map<Long,Set<Long>>)->Unit,priorities:Map<Long,List<Long>> = emptyMap(),onPriorityChange:((Map<Long,List<Long>>)->Unit)?=null) {
 if(shops.none{pair->branches.count{it.shopId==pair.first}>1})return
 Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
  Text("Точки продажи товара",style=MaterialTheme.typography.titleMedium)
  shops.forEach{(shopId,name)->val points=branches.filter{it.shopId==shopId}.sortedWith(compareBy<ShopBranchEntity>{it.displayOrder}.thenBy{it.id})
   val ranked=points.filter{shopId !in selected||it.id in selected[shopId].orEmpty()}.sortedWith(compareBy<ShopBranchEntity>{priorities[shopId]?.indexOf(it.id)?.takeIf{it>=0}?:Int.MAX_VALUE}.thenBy{it.displayOrder}.thenBy{it.id})
   if(points.size>1)OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)) {
    Text(name,style=MaterialTheme.typography.titleSmall)
    Row(Modifier.fillMaxWidth().toggleable(shopId in selected,role=androidx.compose.ui.semantics.Role.Switch){restricted->onChange(if(restricted)selected+(shopId to setOf(points.first().id))else selected-shopId)},verticalAlignment=Alignment.CenterVertically){
     Switch(shopId in selected,null);Text("Только выбранные точки",Modifier.weight(1f).padding(start=12.dp,top=8.dp,bottom=8.dp))
    }
    if(shopId in selected) {
     points.forEach{branch->SelectionRow(branch.address,branch.id in selected[shopId].orEmpty(),{checked->
      val ids=selected[shopId].orEmpty().toMutableSet();if(checked)ids.add(branch.id)else ids.remove(branch.id);onChange(selected+(shopId to ids))
     },tag="branch-choice-${branch.id}")}
     if(selected[shopId].isNullOrEmpty())Text("Выберите хотя бы одну точку",color=MaterialTheme.colorScheme.error)
    }else Text("Во всех точках",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    if(onPriorityChange!=null&&ranked.size>1){
     var editing by remember(shopId){mutableStateOf(false)}
     TextButton(onClick={editing=!editing}){Text(if(editing)"Готово: приоритет точек"else "Приоритет точек")}
     if(editing)AnimatedOrderColumn(ranked,{it.id},spacing=0.dp){index,point->
      fun move(delta:Int){val next=ranked.map{it.id}.toMutableList();val to=index+delta;if(to in next.indices){val other=next[to];next[to]=next[index];next[index]=other;onPriorityChange(priorities+(shopId to next))}}
      Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(point.address,Modifier.weight(1f));ReorderButtons({move(-1)},{move(1)},index>0,index<ranked.lastIndex,"Выше приоритет точки: ${point.address}","Ниже приоритет точки: ${point.address}")}
     }
    }
   }}
  }
 }
}
