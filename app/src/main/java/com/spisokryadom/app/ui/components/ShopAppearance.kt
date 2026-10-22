package com.spisokryadom.app.ui.components

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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

/** Compact entry point; changes in the sheet remain a draft until Apply. */
@Composable
fun ShopAppearance(background:String,name:String,iconKey:String,logoUri:String?,onBackground:(String)->Unit,onIcon:(String)->Unit,onLogo:(String?)->Unit,onBusy:(Boolean)->Unit,externalPhotoBusy:Boolean=false,purchaseBackground:String="",onPurchaseBackground:(String)->Unit={}){
    var open by rememberSaveable{mutableStateOf(false)}
    IconButton(onClick={open=true},enabled=!externalPhotoBusy,modifier=Modifier.testTag("shop-appearance-open")){
        Icon(Icons.Filled.Palette,"Оформление магазина")
    }
    if(open)ShopAppearanceEditor(background,name,iconKey,logoUri,purchaseBackground,onDismiss={open=false},onBusy=onBusy,externalPhotoBusy=externalPhotoBusy){color,items,icon,logo->
        onBackground(color);onPurchaseBackground(items);onIcon(icon);if(logo!=null)onLogo(logo);open=false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShopAppearanceEditor(initialBackground:String,name:String,initialIcon:String,initialLogo:String?,initialItems:String,onDismiss:()->Unit,onBusy:(Boolean)->Unit,externalPhotoBusy:Boolean,onApply:(String,String,String,String?)->Unit){
    var background by rememberSaveable{mutableStateOf(initialBackground)}
    var items by rememberSaveable{mutableStateOf(initialItems)}
    var icon by rememberSaveable{mutableStateOf(initialIcon)}
    var logo by rememberSaveable{mutableStateOf(initialLogo)}
    var lastColor by rememberSaveable{mutableStateOf(initialBackground.ifBlank{"rose"})}
    var picker by rememberSaveable{mutableStateOf("")}
    var busy by remember{mutableStateOf(false)}
    val busyCallback by rememberUpdatedState(onBusy)
    var lastItems by rememberSaveable{mutableStateOf(initialItems.ifBlank{"auto"})}
    val locked=busy||externalPhotoBusy
    val latestLocked by rememberUpdatedState(locked)
    ModalBottomSheet(onDismissRequest={if(!locked)onDismiss()},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true,confirmValueChange={it!=SheetValue.Hidden||!latestLocked})){
        Column(Modifier.fillMaxWidth().heightIn(max=(androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp-64).coerceAtLeast(190).dp).imePadding().padding(horizontal=20.dp).testTag("shop-appearance-editor"),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text("Оформление магазина",style=MaterialTheme.typography.titleLarge)
            ShopBlockPreview(background,name,icon,logo,compact=true,purchaseBackground=items)
            Column(Modifier.weight(1f,fill=false).verticalScroll(androidx.compose.foundation.rememberScrollState()).testTag("shop-appearance-controls"),verticalArrangement=Arrangement.spacedBy(12.dp)){
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Icon(Icons.Filled.Layers,null,tint=MaterialTheme.colorScheme.primary)
                    Text("Подложка",Modifier.weight(1f).padding(horizontal=10.dp),style=MaterialTheme.typography.titleSmall)
                    if(background.isNotBlank())IconButton(onClick={picker="block"},enabled=!locked,modifier=Modifier.testTag("shop-background-color")){
                        Icon(Icons.Filled.Circle,"Цвет подложки",tint=accentColor(background)?:MaterialTheme.colorScheme.primary)
                    }
                    Checkbox(background.isNotBlank(),{if(it)background=lastColor else{lastColor=background;background=""}},enabled=!locked,modifier=Modifier.testTag("shop-background-switch"))
                }
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Icon(Icons.Filled.ViewAgenda,null,tint=MaterialTheme.colorScheme.primary)
                    Text("Выделение товаров",Modifier.weight(1f).padding(horizontal=10.dp),style=MaterialTheme.typography.titleSmall)
                    Checkbox(items.isNotBlank(),{checked->if(checked)items=lastItems else{lastItems=items;items=""}},enabled=!locked,modifier=Modifier.testTag("shop-items-switch"))
                }
                if(items.isNotBlank()){
                    DropdownSelector("Фон товаров",listOf("auto","custom"),if(items=="auto")"auto"else "custom",
                        {if(it=="auto")"В тон подложки"else "Свой цвет"},
                        {mode->if(!locked)when(mode){"auto"->items="auto";"custom"->picker="items"}},allowNone=false)
                    if(items!="auto")Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                        Text("Цвет товаров",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                        IconButton(onClick={picker="items"},enabled=!locked){Icon(Icons.Filled.Circle,"Цвет фона товаров",tint=accentColor(items)?:MaterialTheme.colorScheme.primary)}
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    FilledTonalIconButton(onClick={picker="icon"},enabled=!locked,modifier=Modifier.size(64.dp).testTag("shop-symbol-edit")){
                        Box(Modifier.semantics{contentDescription="Изменить значок или логотип магазина"}){ShopSymbol(icon,logo,40.dp)}
                    }
                    Text(if(logo!=null)"Логотип магазина"else "Значок магазина",style=MaterialTheme.typography.bodyMedium)
                }
                if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                TextButton(onClick=onDismiss,enabled=!locked,modifier=Modifier.weight(1f)){Text("Отмена")}
                Button(onClick={onApply(background,items,icon,logo)},enabled=!locked,modifier=Modifier.weight(1f).testTag("shop-appearance-apply")){Text("Применить")}
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    when(picker){
        "block"->AppearanceSheet("Цвет подложки",background,withIcons=false,onSave={c,_->background=c;if(c.isNotBlank())lastColor=c;picker=""},onDismiss={picker=""},preview={c,_->ShopBlockPreview(c,name,icon,logo,compact=true,purchaseBackground=items)})
        "items"->AppearanceSheet("Цвет товаров",items.takeUnless{it=="auto"}.orEmpty(),withIcons=false,onSave={c,_->items=c;picker=""},onDismiss={picker=""},preview={c,_->ShopBlockPreview(background,name,icon,logo,compact=true,purchaseBackground=c)})
        "icon"->ShopSymbolPicker(icon,logo,name,onDismiss={picker=""},onBusy={busy=it;busyCallback(it)},onApply={i,l->icon=i;logo=l;picker=""})
    }
}
