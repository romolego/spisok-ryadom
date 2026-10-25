package com.spisokryadom.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.spisokryadom.app.data.entity.ProductPhotoEntity
import com.spisokryadom.app.util.PhotoStore
import kotlinx.coroutines.*

@Composable
fun ProductPhotos(photos:List<ProductPhotoEntity>,enabled:Boolean,onPhotos:(List<ProductPhotoEntity>)->Unit,onBusy:(Boolean)->Unit,
 toolbar:@Composable RowScope.(()->Unit,()->Unit)->Unit){
 var editing by remember{mutableStateOf<Int?>(null)}
 BranchPhotoEditor("",onChange={uri->if(uri!=null&&photos.size<5&&photos.none{it.uri==uri})onPhotos(photos+ProductPhotoEntity(0,photos.size,uri))},onBusyChange=onBusy,directory="product_photos",compact=true,showTitle=false,enabled=enabled,toolbar={gallery,camera->
  toolbar({if(photos.size<5)gallery()},{if(photos.size<5)camera()})
 })
 if(photos.isNotEmpty())Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("product-photo-strip"),horizontalArrangement=Arrangement.spacedBy(10.dp)){
  photos.forEachIndexed{index,photo->
   val bitmap by produceState<android.graphics.Bitmap?>(null,photo.uri){value=withContext(Dispatchers.IO){PhotoStore.load(photo.uri,480)}}
   val photoWidth=bitmap?.let{(160f*it.width/it.height).coerceIn(96f,360f).dp}?:160.dp
   OutlinedCard(Modifier.width(photoWidth).clickable(enabled=enabled){editing=index}.testTag("product-photo-$index")){
    val image=bitmap
    if(image!=null)Image(image.asImageBitmap(),photo.caption.ifBlank{if(index==0)"Фото товара"else "Фото ${index+1}"},Modifier.fillMaxWidth().height(160.dp),contentScale=ContentScale.Fit)
    else Box(Modifier.size(160.dp),contentAlignment=Alignment.Center){Icon(Icons.Filled.BrokenImage,"Фото недоступно")}
    Row(Modifier.padding(horizontal=8.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
     Text(photo.caption.ifBlank{if(index==0)"Основное фото"else "Фото ${index+1}"},Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,maxLines=2)
     Icon(Icons.Filled.Edit,"Изменить фото ${index+1}",Modifier.size(16.dp))
    }
   }
  }
 }
 editing?.takeIf{it in photos.indices}?.let{index->
  var draft by remember(index,photos[index]){mutableStateOf(photos[index])}
  var deleted by remember{mutableStateOf(false)};var busy by remember{mutableStateOf(false)}
  var position by remember(index){mutableStateOf(index)}
  EditorSheet("Фото ${index+1}",{if(!busy)editing=null},busy=busy,actions={
   TextButton(onClick={editing=null},enabled=!busy){Text("Отмена")}
   Button(onClick={val next=photos.toMutableList();next.removeAt(index);if(!deleted)next.add(position.coerceAtMost(next.size),draft);onPhotos(next);editing=null},enabled=!busy){Text("Применить")}
  }){
   if(!deleted){
    BranchPhotoEditor(draft.uri,{if(it==null)deleted=true else draft=draft.copy(uri=it)},onBusyChange={busy=it;onBusy(it)},title="Фото товара",directory="product_photos",compact=true)
    AppTextField(draft.caption,{draft=draft.copy(caption=it)},label={Text("Примечание к фото")},modifier=Modifier.fillMaxWidth())
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
     IconButton(onClick={position--},enabled=position>0&&!busy){Icon(Icons.Filled.ArrowBack,"Фото раньше")}
     Text(if(position==0)"Основное фото"else "Позиция ${position+1}",Modifier.weight(1f))
     IconButton(onClick={position++},enabled=position<photos.lastIndex&&!busy){Icon(Icons.Filled.ArrowForward,"Фото позже")}
    }
   }else Text("Фото будет удалено после применения")
  }
 }
}
