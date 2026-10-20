package com.spisokryadom.app.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
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
@Composable fun BranchPhotoEditor(path:String,onChange:(String?)->Unit,onBusyChange:(Boolean)->Unit={},title:String="Фото точки",directory:String="branch_photos",enabled:Boolean=true) {
 val context=LocalContext.current;val scope=rememberCoroutineScope();val latest by rememberUpdatedState(onChange);val latestBusy by rememberUpdatedState(onBusyChange)
 var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
 var capturePath by rememberSaveable{mutableStateOf<String?>(null)}
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
 Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
  Text(title,style=MaterialTheme.typography.labelMedium)
  if(path.isNotBlank())BranchPhotoPreview(path,title)
  error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
  if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
   OutlinedButton(onClick={gallery.launch("image/*")},enabled=enabled&&!busy,modifier=Modifier.weight(1f)){Text(if(path.isBlank())"Выбрать фото"else "Заменить фото")}
   OutlinedButton(onClick={
    try{val dir=File(context.cacheDir,"camera_captures").also{it.mkdirs()};val f=File(dir,"${UUID.randomUUID()}.jpg");capturePath=f.absolutePath;camera.launch(FileProvider.getUriForFile(context,context.packageName+".files",f))}
    catch(e:Exception){android.util.Log.e("BranchPhoto","Camera launch failed",e);capturePath?.let{File(it).delete()};capturePath=null;error="Камера недоступна"}
   },enabled=enabled&&!busy){Text("Камера")}
  }
  if(path.isNotBlank())TextButton(onClick={latest(null)},enabled=enabled&&!busy){Text("Удалить фото")}
 }
}
