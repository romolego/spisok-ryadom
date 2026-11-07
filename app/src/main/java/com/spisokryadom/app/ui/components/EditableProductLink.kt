package com.spisokryadom.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
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
    val clipboard=LocalClipboardManager.current
    val urls=remember(draft){WebLinks.extract(draft)}
    LaunchedEffect(draft){if(urls.none{it==selected})selected=urls.singleOrNull()}
    fun edit(){draft=value;selected=WebLinks.extract(value).singleOrNull();editing=true;error=null}
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start=12.dp,end=4.dp),verticalAlignment=Alignment.CenterVertically){
            Text(value.ifBlank{label},Modifier.weight(1f).clickable(enabled=enabled){
                if(value.isBlank())edit() else {
                    val link=WebLinks.extract(value).singleOrNull()
                    if(link==null)error="Проверьте ссылку через карандаш"
                    else try{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(link)))}catch(_:Exception){error="Не удалось открыть ссылку"}
                }
            }.padding(vertical=12.dp),maxLines=2,overflow=TextOverflow.Ellipsis,
                color=if(value.isBlank())MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary)
            IconButton(onClick=::edit,enabled=enabled){Icon(if(value.isBlank())Icons.Filled.Add else Icons.Filled.Edit,
                if(value.isBlank())"Добавить: $label"else "Изменить: $label")}
        }
    }
    error?.let{AlertDialog(onDismissRequest={error=null},text={Text(it)},confirmButton={TextButton(onClick={error=null}){Text("Понятно")}})}
    if(editing)EditorSheet(label,{editing=false},actions={
        TextButton(onClick={editing=false}){Text("Отмена")}
        Button(onClick={onApply(if(draft.isBlank())"" else selected?:return@Button);editing=false},enabled=draft.isBlank()||selected!=null){Text("Применить")}
    }){
        AppTextField(draft,{draft=it},label={Text("Ссылка или скопированный текст")},minLines=2,maxLines=6,allowVoice=false,
            trailingIcon={IconButton(onClick={clipboard.getText()?.text?.let{draft=it}}){Icon(Icons.Filled.ContentPaste,"Вставить из буфера")}},
            topActions=true,modifier=Modifier.fillMaxWidth(),inputModifier=Modifier.testTag("product-link-input"))
        if(urls.size>1)DropdownSelector("Ссылка",urls,selected,{it},{selected=it},allowNone=false)
        else if(urls.isEmpty()&&draft.isNotBlank())Text("Не найдена ссылка http:// или https://",color=MaterialTheme.colorScheme.error)
    }
}
