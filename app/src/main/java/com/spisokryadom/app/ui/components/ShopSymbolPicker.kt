package com.spisokryadom.app.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.spisokryadom.app.util.PhotoStore
import kotlinx.coroutines.*

/** One entry for built-in symbols and uploaded logos; cancelling preserves the outer draft. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShopSymbolPicker(initialIcon:String,initialLogo:String?,name:String,onDismiss:()->Unit,onBusy:(Boolean)->Unit,onApply:(String,String?)->Unit){
    var icon by rememberSaveable{mutableStateOf(initialIcon)}
    var logo by rememberSaveable{mutableStateOf(initialLogo)}
    var busy by remember{mutableStateOf(false)}
    var deleting by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope();val context=LocalContext.current
    val busyCallback by rememberUpdatedState(onBusy)
    val gallery=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->if(uri!=null){
        busy=true;busyCallback(true);error=null
        scope.launch{
            try{logo=withContext(Dispatchers.IO){PhotoStore.importPhoto(context,uri,"shop_logos")};icon=""}
            catch(e:Exception){if(e is CancellationException)throw e;error=e.message?:"Не удалось загрузить логотип"}
            finally{busy=false;busyCallback(false)}
        }
    }}
    val locked by rememberUpdatedState(busy)
    if(deleting)ConfirmDialog("Удалить логотип?","Будет использован обычный значок магазина. Изменение сохранится после применения оформления и сохранения карточки.",onConfirm={logo=null;icon="";deleting=false},onDismiss={deleting=false})
    ModalBottomSheet(onDismissRequest={if(!busy)onDismiss()},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true,confirmValueChange={it!=SheetValue.Hidden||!locked})){
        Column(Modifier.fillMaxWidth().heightIn(max=(androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp-64).coerceAtLeast(190).dp).imePadding().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text("Значок или логотип",style=MaterialTheme.typography.titleLarge)
            Column(Modifier.weight(1f,false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    ShopSymbol(icon,logo,56.dp)
                    Text(name.ifBlank{"Магазин"},Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                    IconButton(onClick={gallery.launch("image/*")},enabled=!busy){Icon(Icons.Filled.AddPhotoAlternate,if(logo==null)"Загрузить логотип магазина"else "Заменить логотип магазина")}
                    if(logo!=null)IconButton(onClick={deleting=true},enabled=!busy){Icon(Icons.Filled.Delete,"Удалить логотип магазина",tint=MaterialTheme.colorScheme.error)}
                }
                DropdownSelector("Готовый значок",listOf("")+appearanceOptions().map{it.first},if(logo==null)icon else null,
                    {key->appearanceOptions().find{it.first==key}?.second?:"Обычный значок магазина"},
                    {key->if(!busy&&key!=null){icon=key;logo=null}},allowNone=false,
                    itemVector={appearanceIcon(it)?:Icons.Filled.Store})
                if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                TextButton(onClick=onDismiss,enabled=!busy,modifier=Modifier.weight(1f)){Text("Отмена")}
                Button(onClick={onApply(icon,logo)},enabled=!busy,modifier=Modifier.weight(1f).testTag("shop-symbol-apply")){Text("Применить")}
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
