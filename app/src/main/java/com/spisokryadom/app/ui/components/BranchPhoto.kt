package com.spisokryadom.app.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.spisokryadom.app.util.PhotoStore
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

@Composable fun BranchPhotoPreview(path:String,title:String="Фото точки") {
 val bitmap by produceState<android.graphics.Bitmap?>(null,path){value=withContext(Dispatchers.IO){PhotoStore.load(path)}}
 bitmap?.let{Image(it.asImageBitmap(),title,Modifier.fillMaxWidth().heightIn(max=220.dp),contentScale=ContentScale.Fit)}
}
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable fun BranchPhotoEditor(path:String,onChange:(String?)->Unit,onBusyChange:(Boolean)->Unit={},title:String="Фото точки",directory:String="branch_photos",enabled:Boolean=true,compact:Boolean=false) {
 val context=LocalContext.current;val scope=rememberCoroutineScope();val latest by rememberUpdatedState(onChange);val latestBusy by rememberUpdatedState(onBusyChange)
 var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
 var capturePath by rememberSaveable{mutableStateOf<String?>(null)}
 var editing by remember{mutableStateOf(false)}
 var confirmDelete by rememberSaveable(path){mutableStateOf(false)}
 fun import(uri:android.net.Uri,raw:File?=null){busy=true;latestBusy(true);error=null;scope.launch{
  try{val local=withContext(Dispatchers.IO){PhotoStore.importPhoto(context,uri,directory)};latest(local)}
  catch(e:Exception){if(e is CancellationException)throw e;error=e.message?:"Не удалось сохранить фото"}
  finally{raw?.delete();busy=false;latestBusy(false)}
 }}
 val gallery=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->uri?.let{import(it)}}
 val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){ok->
  val file=capturePath?.let(::File);capturePath=null
  if(ok&&file!=null)import(FileProvider.getUriForFile(context,context.packageName+".files",file),file)else file?.delete()
 }
 fun openGallery(){try{gallery.launch("image/*")}catch(e:Exception){error="Выбор фото недоступен"}}
 fun openCamera(){
  try{val dir=File(context.cacheDir,"camera_captures").also{it.mkdirs()};val f=File(dir,"${UUID.randomUUID()}.jpg");capturePath=f.absolutePath;camera.launch(FileProvider.getUriForFile(context,context.packageName+".files",f))}
  catch(e:Exception){android.util.Log.e("BranchPhoto","Camera launch failed",e);capturePath?.let{File(it).delete()};capturePath=null;error="Камера недоступна"}
 }
 if(compact){
  val bring=remember{BringIntoViewRequester()}
  val initialPath=remember{path}
  if(confirmDelete)AlertDialog(onDismissRequest={confirmDelete=false},title={Text("Удалить фото?")},text={Text("Снимок будет убран из карточки товара.")},
   confirmButton={TextButton(onClick={confirmDelete=false;latest(null)},enabled=enabled&&!busy){Text("Удалить")}},
   dismissButton={TextButton(onClick={confirmDelete=false}){Text("Отмена")}})
  Column(Modifier.fillMaxWidth().testTag("product-photo").bringIntoViewRequester(bring),verticalArrangement=Arrangement.spacedBy(8.dp)){
   if(path.isBlank()){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
     Text(title,Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
     IconButton(onClick=::openGallery,enabled=enabled&&!busy){Icon(Icons.Filled.PhotoLibrary,"Выбрать фото")}
     IconButton(onClick=::openCamera,enabled=enabled&&!busy){Icon(Icons.Filled.PhotoCamera,"Камера")}
    }
   }else Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)){
    val bitmap by produceState<android.graphics.Bitmap?>(null,path){value=withContext(Dispatchers.IO){PhotoStore.load(path)}}
    LaunchedEffect(path,bitmap){if(bitmap!=null&&path!=initialPath)bring.bringIntoView()}
    if(bitmap!=null)Image(bitmap!!.asImageBitmap(),title,Modifier.fillMaxWidth().heightIn(min=100.dp,max=220.dp),contentScale=ContentScale.Fit)
    else Box(Modifier.fillMaxWidth().height(100.dp),contentAlignment=Alignment.Center){Text("Фото недоступно",style=MaterialTheme.typography.bodySmall)}
    Box(Modifier.align(Alignment.TopEnd).padding(6.dp)){
     FilledTonalIconButton(onClick={editing=true},enabled=enabled&&!busy){Icon(Icons.Filled.Edit,"Изменить фото")}
     DropdownMenu(expanded=editing,onDismissRequest={editing=false}){
      DropdownMenuItem(text={Text("Галерея")},leadingIcon={Icon(Icons.Filled.PhotoLibrary,null)},enabled=enabled&&!busy,onClick={editing=false;openGallery()})
      DropdownMenuItem(text={Text("Камера")},leadingIcon={Icon(Icons.Filled.PhotoCamera,null)},enabled=enabled&&!busy,onClick={editing=false;openCamera()})
      DropdownMenuItem(text={Text("Удалить")},leadingIcon={Icon(Icons.Filled.Delete,null)},enabled=enabled&&!busy,onClick={editing=false;confirmDelete=true})
     }
    }
   }
   error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
   if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
  }
  return
 }
 Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
  Text(title,style=MaterialTheme.typography.labelMedium)
  if(path.isNotBlank())BranchPhotoPreview(path,title)
  error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
  if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
   OutlinedButton(onClick=::openGallery,enabled=enabled&&!busy,modifier=Modifier.weight(1f)){Text(if(path.isBlank())"Выбрать фото"else "Заменить фото")}
   OutlinedButton(onClick=::openCamera,enabled=enabled&&!busy){Text("Камера")}
  }
  if(path.isNotBlank())TextButton(onClick={latest(null)},enabled=enabled&&!busy){Text("Удалить фото")}
 }
}
