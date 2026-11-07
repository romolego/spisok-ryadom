package com.spisokryadom.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spisokryadom.app.util.WebLinks

@Composable fun EditableProductLink(label:String,value:String,onApply:(String)->Unit,enabled:Boolean=true) {
    var editing by rememberSaveable(label){mutableStateOf(false)}
    var draft by rememberSaveable(label){mutableStateOf("")}
    var selected by rememberSaveable(label){mutableStateOf<String?>(null)}
    var error by remember{mutableStateOf<String?>(null)}
    val context=LocalContext.current
    val urls=remember(draft){WebLinks.extract(draft)}
    LaunchedEffect(draft){if(urls.none{it==selected})selected=urls.singleOrNull()}
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start=12.dp,end=4.dp),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f).clickable(enabled=enabled){
                if(value.isBlank()){draft="";editing=true}
                else {
                    val link=WebLinks.extract(value).singleOrNull()
                    if(link==null)error="Проверьте ссылку через карандаш"
                    else try{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(link)))}catch(_:Exception){error="Не удалось открыть ссылку"}
                }
            }.padding(vertical=10.dp)){
                Text(label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value.ifBlank{"Добавить ссылку"},maxLines=2,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.bodyMedium)
            }
            if(value.isNotBlank())Icon(Icons.AutoMirrored.Filled.OpenInNew,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.primary)
            IconButton(onClick={draft=value;selected=WebLinks.extract(value).singleOrNull();editing=true;error=null},enabled=enabled){Icon(Icons.Filled.Edit,"Изменить: $label")}
        }
    }
    error?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
    if(editing)EditorSheet(label,{editing=false},actions={
        TextButton(onClick={editing=false}){Text("Отмена")}
        Button(onClick={onApply(if(draft.isBlank())"" else selected?:return@Button);editing=false},enabled=draft.isBlank()||selected!=null){Text("Применить")}
    }){
        AppTextField(draft,{draft=it},label={Text("Ссылка или скопированный текст")},minLines=2,maxLines=6,allowVoice=false,modifier=Modifier.fillMaxWidth().testTag("product-link-input"))
        if(urls.size>1){
            Text("В тексте несколько ссылок. Выберите нужную.",style=MaterialTheme.typography.bodySmall)
            DropdownSelector("Ссылка",urls,selected,{it},{selected=it},allowNone=false)
        }else if(urls.size==1)Text(urls.single(),style=MaterialTheme.typography.bodySmall)
        else if(draft.isNotBlank())Text("В тексте не найдена ссылка http:// или https://",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
    }
}
