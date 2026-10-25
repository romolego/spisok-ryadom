package com.spisokryadom.app.ui.components
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.platform.LocalFocusManager
import com.spisokryadom.app.data.entity.*

/** A dictionary option is offered for adding, never shown as an existing placement. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable fun DepartmentChoice(
 departments:List<ShopDepartmentEntity>,selectedId:Long?,onSelect:(Long?)->Unit,
 onCreate:(String,(Long)->Unit)->Unit,busy:Boolean,types:List<DepartmentTypeEntity> = emptyList(),
 label:String="Группа магазина",noneLabel:String="Не задано"
){
 var adding by remember{mutableStateOf(false)};var name by remember{mutableStateOf("")}
 val focus=LocalFocusManager.current
 val requester=remember{FocusRequester()};val bring=remember{BringIntoViewRequester()}
 val missing=types.filter{type->departments.none{it.typeId==type.id||com.spisokryadom.app.util.FuzzySearch.normalize(it.name)==type.normalizedName}}
 fun add(value:String){onCreate(value){id->onSelect(id);adding=false;name="";focus.clearFocus()}}
 DropdownSelector(label=label,noneLabel=noneLabel,items=departments,selectedItem=departments.find{it.id==selectedId},
  itemLabel={it.name},onSelect={onSelect(it?.id)},itemIcon={d->types.find{it.id==d.typeId}?.iconKey.orEmpty()},itemAccent={d->types.find{it.id==d.typeId}?.accent.orEmpty()},
  allowCreate=true,onCreateNew={adding=true},createLabel="Добавить группу…",alphabetical=true,itemFavorite={d->types.find{it.id==d.typeId}?.isFavorite==true},favoriteOrder={d->types.find{it.id==d.typeId}?.favoriteOrder?:0})
 if(adding){
  LaunchedEffect(Unit){requester.requestFocus();bring.bringIntoView()}
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
   AppTextField(name,{name=it},label={Text("Новая группа")},singleLine=true,enabled=!busy,modifier=Modifier.weight(1f).focusRequester(requester).bringIntoViewRequester(bring))
   IconButton(onClick={add(name.trim())},enabled=name.isNotBlank()&&!busy){Icon(Icons.Filled.Add,"Создать группу магазина")}
   IconButton(onClick={adding=false;name="";focus.clearFocus()},enabled=!busy){Icon(Icons.Filled.Close,"Отменить новую группу")}
  }
  if(missing.isNotEmpty())DropdownSelector(label="Общая группа",items=missing,selectedItem=null,itemLabel={it.name},itemIcon={it.iconKey},itemAccent={it.accent},
   onSelect={if(it!=null)add(it.name)},noneLabel="Не выбрана",alphabetical=true,itemFavorite={it.isFavorite},favoriteOrder={it.favoriteOrder})
 }
}
