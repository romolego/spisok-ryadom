package com.spisokryadom.app.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spisokryadom.app.util.PhotoStore
import kotlinx.coroutines.*

/** Local immutable thumbnail, shared by store list, purchase heading and preview. */
@Composable
fun ShopSymbol(iconKey:String="",logoUri:String?=null,size:Dp=24.dp){
    val bitmap by produceState<android.graphics.Bitmap?>(null,logoUri){value=null;value=logoUri?.let{withContext(Dispatchers.IO){PhotoStore.load(it,128)}}}
    val image=bitmap
    if(image!=null)Image(image.asImageBitmap(),null,Modifier.size(size).clip(RoundedCornerShape(4.dp)),contentScale=ContentScale.Fit)
    else Icon(appearanceIcon(iconKey)?:Icons.Filled.Store,null,Modifier.size(size),tint=MaterialTheme.colorScheme.primary)
}

@Composable
fun ShopBlockPreview(background:String,name:String,iconKey:String,logoUri:String?,compact:Boolean=false,purchaseBackground:String=""){
    val short=compact&&androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp<500
    Column(Modifier.fillMaxWidth().testTag("shop-appearance-preview")
        .background(shoppingBlockColor(background,MaterialTheme.colorScheme.surface)?:Color.Transparent,RoundedCornerShape(12.dp))
        .padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
            ShopSymbol(iconKey,logoUri)
            Text(name.ifBlank{"Магазин"},style=MaterialTheme.typography.titleSmall,maxLines=2)
        }
        if(!compact)Text("Группа магазина",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(!short)Surface(shape=RoundedCornerShape(10.dp),color=purchaseCardColor(background,purchaseBackground,MaterialTheme.colorScheme.surface)){
            Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Icon(Icons.Filled.CheckBoxOutlineBlank,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Пример товара",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                Text("1 шт",style=MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun ShopAppearance(background:String,name:String,iconKey:String,logoUri:String?,onBackground:(String)->Unit,onIcon:(String)->Unit,onLogo:(String?)->Unit,onBusy:(Boolean)->Unit,externalPhotoBusy:Boolean=false,purchaseBackground:String="",onPurchaseBackground:(String)->Unit={}){
    var itemColors by remember{mutableStateOf(false)}
    var colors by remember{mutableStateOf(false)};var icons by remember{mutableStateOf(false)}
    var lastColor by rememberSaveable{mutableStateOf(background.ifEmpty{"rose"})}
    var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    val latestLogo by rememberUpdatedState(onLogo);val latestBusy by rememberUpdatedState(onBusy)
    val gallery=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->if(uri!=null){
        busy=true;latestBusy(true);error=null
        scope.launch{
            try{latestLogo(withContext(Dispatchers.IO){PhotoStore.importPhoto(context,uri,"shop_logos")})}
            catch(e:Exception){if(e is CancellationException)throw e;error=e.message?:"Не удалось загрузить логотип"}
            finally{busy=false;latestBusy(false)}
        }
    }}
    if(colors)AppearanceSheet("Фон блока магазина",background,withIcons=false,onSave={c,_->if(c.isNotEmpty())lastColor=c;onBackground(c);colors=false},onDismiss={colors=false},
        preview={c,_->ShopBlockPreview(c,name,iconKey,logoUri,compact=true,purchaseBackground=purchaseBackground)})
    if(icons)AppearanceSheet("Значок магазина","",iconKey,withColors=false,noneIconLabel="Обычный значок магазина",onSave={_,i->onIcon(i);icons=false},onDismiss={icons=false})
    if(itemColors)AppearanceSheet("Фон товаров магазина",purchaseBackground.takeUnless{it=="auto"}.orEmpty(),withIcons=false,onSave={c,_->onPurchaseBackground(c);itemColors=false},onDismiss={itemColors=false},preview={c,_->ShopBlockPreview(background,name,iconKey,logoUri,compact=true,purchaseBackground=c)})
    OutlinedCard(Modifier.fillMaxWidth().testTag("shop-background-settings"),colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface)){
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text("Оформление в списке покупок",style=MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                ShopSymbol(iconKey,logoUri,32.dp)
                Text(if(logoUri!=null)"Логотип магазина"else if(iconKey.isNotEmpty())"Значок магазина"else "Обычный значок",Modifier.weight(1f).padding(start=10.dp),style=MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedButton(onClick={icons=true},enabled=!busy&&!externalPhotoBusy,modifier=Modifier.weight(1f)){Text("Значок")}
                OutlinedButton(onClick={gallery.launch("image/*")},enabled=!busy&&!externalPhotoBusy,modifier=Modifier.weight(1f)){Text("Логотип")}
            }
            if(logoUri!=null||iconKey.isNotEmpty())TextButton(onClick={onLogo(null);onIcon("")},enabled=!busy&&!externalPhotoBusy){Text("Вернуть обычный значок")}
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().testTag("shop-background-switch")
                .toggleable(background.isNotEmpty(),role=Role.Switch){if(it)onBackground(lastColor)else{lastColor=background;onBackground("")}},verticalAlignment=Alignment.CenterVertically){
                Text("Выделять блок магазина фоном",Modifier.weight(1f).padding(end=12.dp),style=MaterialTheme.typography.bodyMedium)
                Switch(background.isNotEmpty(),null)
            }
            if(background.isNotEmpty())OutlinedButton(onClick={colors=true},modifier=Modifier.testTag("shop-background-color")){Text("Цвет фона")}
            DropdownSelector("Фон товаров",listOf("","auto","custom"),if(purchaseBackground.isBlank()||purchaseBackground=="auto")purchaseBackground else "custom",
                {when(it){"auto"->"В тон подложки";"custom"->"Свой цвет";else->"Обычный фон"}},
                {mode->when(mode){"auto"->onPurchaseBackground("auto");"custom"->{onPurchaseBackground(purchaseBackground.takeUnless{it.isBlank()||it=="auto"}?:background.ifBlank{"rose"});itemColors=true};""->onPurchaseBackground("")}},allowNone=false)
            if(purchaseBackground.isNotBlank()&&purchaseBackground!="auto")OutlinedButton(onClick={itemColors=true}){Text("Цвет фона товаров")}
            Text("Предпросмотр",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            ShopBlockPreview(background,name,iconKey,logoUri,purchaseBackground=purchaseBackground)
        }
    }
}

