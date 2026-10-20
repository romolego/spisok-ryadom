package com.spisokryadom.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

/** Photoshop-style saturation/value plane. The only separate channel is hue. */
@Composable
fun ColorField(hue:Float,saturation:Float,value:Float,onChange:(Float,Float)->Unit){
    val latest by rememberUpdatedState(onChange)
    val outline=MaterialTheme.colorScheme.outline
    val height=if(LocalConfiguration.current.screenHeightDp<500)100.dp else 160.dp
    Canvas(Modifier.fillMaxWidth().height(height).testTag("appearance-color-field")
        .semantics {
            contentDescription="Цветовое поле"
            stateDescription="Насыщенность ${(saturation*100).toInt()}%, светлота ${(value*100).toInt()}%"
            customActions=listOf(
                CustomAccessibilityAction("Светлее"){latest(saturation,(value+.05f).coerceAtMost(1f));true},
                CustomAccessibilityAction("Темнее"){latest(saturation,(value-.05f).coerceAtLeast(0f));true},
                CustomAccessibilityAction("Мягче"){latest((saturation-.05f).coerceAtLeast(0f),value);true},
                CustomAccessibilityAction("Насыщеннее"){latest((saturation+.05f).coerceAtMost(1f),value);true})
        }
        .pointerInput(Unit){awaitEachGesture{
            val down=awaitFirstDown();down.consume()
            latest((down.position.x/size.width).coerceIn(0f,1f),1f-(down.position.y/size.height).coerceIn(0f,1f))
            do {
                val event=awaitPointerEvent()
                val change=event.changes.firstOrNull{it.id==down.id}?:break
                change.consume()
                latest((change.position.x/size.width).coerceIn(0f,1f),1f-(change.position.y/size.height).coerceIn(0f,1f))
            }while(change.pressed)
        }}
    ){
        drawRect(Brush.horizontalGradient(listOf(Color.White,Color.hsv(hue.coerceIn(0f,359.999f),1f,1f))))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent,Color.Black)))
        drawRect(outline,style=Stroke(1.dp.toPx()))
        val p=Offset(saturation*size.width,(1f-value)*size.height)
        drawCircle(Color.Black,8.dp.toPx(),p,style=Stroke(3.dp.toPx()))
        drawCircle(Color.White,8.dp.toPx(),p,style=Stroke(1.5f.dp.toPx()))
    }
}
