package com.spisokryadom.app.ui.bulk

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkScreen(onBack:()->Unit,viewModel:ShoppingTextViewModel=viewModel(factory=ShoppingTextViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context=LocalContext.current
    var confirmReload by remember { mutableStateOf(false) }
    var shareError by remember { mutableStateOf<String?>(null) }
    if(confirmReload)com.spisokryadom.app.ui.components.ConfirmDialog(
        "Обновить текст?","Правки сообщения будут заменены текущим списком покупок.",
        {confirmReload=false;viewModel.reloadFromBasket()},{confirmReload=false})
    Scaffold(topBar={TopAppBar(title={Text("Текстовый список",maxLines=1)},navigationIcon={
        IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Назад")}
    },actions={
        IconButton(onClick={try { shareShoppingList(context,state.text); shareError=null } catch(e:Exception) { shareError=e.message?:"Не удалось открыть отправку" }},enabled=!state.loading&&state.text.isNotBlank()) { Icon(Icons.Filled.Share,"Поделиться списком") }
        IconButton(onClick={if(state.dirty)confirmReload=true else viewModel.reloadFromBasket()},enabled=!state.loading) { Icon(Icons.Filled.Refresh,"Обновить из корзины") }
    })}) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Для чтения и отправки. Правки текста не меняют покупки.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(com.spisokryadom.app.util.messageShareUsesFile(state.text))Text("Большой список будет отправлен текстовым файлом.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            shareError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            if(state.loading)LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            OutlinedTextField(state.text,viewModel::text,label={Text("Ваш список")},enabled=!state.loading,minLines=2,
                shape=MaterialTheme.shapes.large,modifier=Modifier.fillMaxWidth().weight(1f).testTag("bulk-editor"))
            Spacer(Modifier.height(8.dp))
        }
    }
}
