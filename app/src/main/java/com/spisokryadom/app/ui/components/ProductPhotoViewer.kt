package com.spisokryadom.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.spisokryadom.app.data.entity.ProductPhotoEntity
import com.spisokryadom.app.util.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun ProductPhotoViewer(photos:List<ProductPhotoEntity>,initialIndex:Int,onDismiss:()->Unit) {
    if(photos.isEmpty())return
    var page by rememberSaveable{mutableIntStateOf(initialIndex.coerceIn(photos.indices))}
    val photo=photos[page.coerceIn(photos.indices)]
    var scale by rememberSaveable(photo.uri){mutableFloatStateOf(1f)}
    var x by rememberSaveable(photo.uri){mutableFloatStateOf(0f)}
    var y by rememberSaveable(photo.uri){mutableFloatStateOf(0f)}
    var size by remember{mutableStateOf(IntSize.Zero)}
    val loaded by produceState<Pair<Boolean,android.graphics.Bitmap?>>(false to null,photo.uri){
        value=false to null
        value=true to withContext(Dispatchers.IO){PhotoStore.load(photo.uri,3200)}
    }
    val transform=rememberTransformableState{zoom,pan,_->
        scale=(scale*zoom).coerceIn(1f,8f)
        x=(x+pan.x).coerceIn(-size.width*(scale-1)/2,size.width*(scale-1)/2)
        y=(y+pan.y).coerceIn(-size.height*(scale-1)/2,size.height*(scale-1)/2)
    }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){
            Column(Modifier.fillMaxSize().systemBarsPadding()){
                Row(Modifier.fillMaxWidth().padding(4.dp),verticalAlignment=Alignment.CenterVertically){
                    IconButton(onClick=onDismiss){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Закрыть просмотр фото")}
                    Text(photo.caption.ifBlank{"Фото ${page+1} из ${photos.size}"},Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,maxLines=2)
                    TextButton(onClick={scale=1f;x=0f;y=0f}){Text("Сбросить масштаб")}
                }
                Box(Modifier.fillMaxWidth().weight(1f).onSizeChanged{size=it}.clipToBounds().testTag("photo-zoom-area")
                    .transformable(transform).pointerInput(photo.uri){detectTapGestures(onDoubleTap={scale=if(scale>1f)1f else 2.5f;x=0f;y=0f})},contentAlignment=Alignment.Center){
                    if(!loaded.first)CircularProgressIndicator()
                    else loaded.second?.let{image->Image(image.asImageBitmap(),photo.caption.ifBlank{"Фото товара"},
                        Modifier.fillMaxSize().graphicsLayer(scaleX=scale,scaleY=scale,translationX=x,translationY=y),contentScale=ContentScale.Fit)}
                        ?:Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Filled.BrokenImage,null);Text("Фото недоступно")}
                }
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center){
                    IconButton(onClick={page--;scale=1f;x=0f;y=0f},enabled=page>0){Icon(Icons.Filled.ChevronLeft,"Предыдущее фото")}
                    Text("${page+1} / ${photos.size} · ${(scale*100).toInt()}%",Modifier.testTag("photo-zoom-status"),style=MaterialTheme.typography.labelLarge)
                    IconButton(onClick={page++;scale=1f;x=0f;y=0f},enabled=page<photos.lastIndex){Icon(Icons.Filled.ChevronRight,"Следующее фото")}
                }
            }
        }
    }
}
