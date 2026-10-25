package com.spisokryadom.app.ui.components

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import java.util.Locale

internal fun voiceSearchIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
    .putExtra(RecognizerIntent.EXTRA_PROMPT, "Скажите текст")
    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

internal fun voiceSearchResult(resultCode: Int, data: Intent?): String? =
    if (resultCode != Activity.RESULT_OK) null else data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        ?.firstOrNull { it.isNotBlank() }?.trim()

/** Uses the installed system speech activity. A result only changes the search query. */
@Composable
fun VoiceSearchButton(onQuery: (String) -> Unit, enabled: Boolean = true, description:String="Голосовой поиск") {
    var error by remember { mutableStateOf<String?>(null) }
    val query by rememberUpdatedState(onQuery)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        voiceSearchResult(result.resultCode, result.data)?.let(query)
    }
    IconButton(onClick = {
        try { launcher.launch(voiceSearchIntent()) }
        catch (_: android.content.ActivityNotFoundException) { error = "На телефоне не найден голосовой ввод. Можно использовать микрофон клавиатуры." }
        catch (_: SecurityException) { error = "Не удалось открыть голосовой ввод. Можно использовать микрофон клавиатуры." }
    }, enabled = enabled) { Icon(Icons.Filled.Mic, description) }
    error?.let { AlertDialog(onDismissRequest = { error = null }, text = { Text(it) }, confirmButton = {
        TextButton(onClick = { error = null }) { Text("Понятно") }
    }) }
}
