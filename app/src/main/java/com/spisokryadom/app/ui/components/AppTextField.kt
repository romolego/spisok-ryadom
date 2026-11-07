package com.spisokryadom.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType

/** The same reversible input actions everywhere. Speech only fills the draft. */
@Composable
fun AppTextField(
    value:String, onValueChange:(String)->Unit, modifier:Modifier=Modifier,
    enabled:Boolean=true, readOnly:Boolean=false,
    label:(@Composable ()->Unit)?=null, placeholder:(@Composable ()->Unit)?=null,
    leadingIcon:(@Composable ()->Unit)?=null, trailingIcon:(@Composable ()->Unit)?=null,
    suffix:(@Composable ()->Unit)?=null, isError:Boolean=false,
    keyboardOptions:KeyboardOptions=KeyboardOptions.Default, keyboardActions:KeyboardActions=KeyboardActions.Default,
    singleLine:Boolean=false, maxLines:Int=if(singleLine)1 else Int.MAX_VALUE, minLines:Int=1,
    shape:Shape=OutlinedTextFieldDefaults.shape, allowVoice:Boolean=true,voiceDescription:String="Голосовой ввод",topActions:Boolean=false,inputModifier:Modifier=Modifier
) {
    val focus=remember{FocusRequester()}
    val voice=allowVoice&&keyboardOptions.keyboardType !in listOf(KeyboardType.Number,KeyboardType.Decimal,KeyboardType.NumberPassword,KeyboardType.Password)
    val hasActions=value.isNotEmpty()||voice||trailingIcon!=null
    val actions: @Composable ()->Unit = { Row {
        if(value.isNotEmpty()) IconButton(onClick={onValueChange("");focus.requestFocus()},enabled=enabled,modifier=Modifier.testTag("input-clear")){Icon(Icons.Filled.Clear,"Очистить")}
        else if(voice)VoiceSearchButton(onValueChange,enabled,description=voiceDescription)
        trailingIcon?.invoke()
    } }
    val field: @Composable (Modifier)->Unit = { fieldModifier ->
    OutlinedTextField(value,onValueChange,fieldModifier.focusRequester(focus),enabled=enabled,readOnly=readOnly,
        label=label,placeholder=placeholder,leadingIcon=leadingIcon,
        trailingIcon=if(topActions&&!readOnly&&hasActions){{Spacer(Modifier.width((if(value.isNotEmpty()||voice)48 else 0).dp+(if(trailingIcon!=null)48 else 0).dp))}}else if(readOnly||!hasActions)trailingIcon else {{ Row {
            if(value.isNotEmpty()) IconButton(onClick={onValueChange("");focus.requestFocus()},enabled=enabled,modifier=Modifier.testTag("input-clear")) {
                Icon(Icons.Filled.Clear,"Очистить")
            } else if(voice) {
                VoiceSearchButton(onValueChange,enabled,description=voiceDescription)
            }
            trailingIcon?.invoke()
        }}},suffix=suffix,isError=isError,keyboardOptions=keyboardOptions,keyboardActions=keyboardActions,
        singleLine=singleLine,maxLines=maxLines,minLines=minLines,shape=shape)
    }
    if(topActions)Box(modifier){field(Modifier.fillMaxWidth().then(inputModifier));if(!readOnly&&hasActions)Box(Modifier.align(Alignment.TopEnd).padding(top=8.dp,end=4.dp)){actions()}}
    else field(modifier)
}
