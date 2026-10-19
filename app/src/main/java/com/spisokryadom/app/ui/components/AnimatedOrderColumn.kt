package com.spisokryadom.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.constrainHeight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Stable identities retain their actual vertical position while the target order changes.
 * Placement (not scrolling or an alpha effect) animates both rows, including unequal heights.
 * Reading Animatable only during placement avoids recomposition/measurement on every frame.
 */
private class RowPosition {
    private var position:Animatable<Float, androidx.compose.animation.core.AnimationVector1D>?=null
    private var target:Int?=null
    private var job:Job?=null
    fun at(y:Int,scope:CoroutineScope):Int {
        if(position==null)position=Animatable(y.toFloat())
        if(target!=y){
            val initial=target==null;target=y
            if(!initial){job?.cancel();job=scope.launch(start=CoroutineStart.UNDISPATCHED){position!!.animateTo(y.toFloat(),tween(300))}}
        }
        return position!!.value.roundToInt()
    }
}

@Composable
fun <T> AnimatedOrderColumn(items:List<T>,itemKey:(T)->Any,modifier:Modifier=Modifier,spacing:Dp=8.dp,content:@Composable (Int,T)->Unit){
    val scope=rememberCoroutineScope()
    val positions=items.map{item->key(itemKey(item)){remember{RowPosition()}}}
    Layout(modifier=modifier.fillMaxWidth(),content={
        items.forEachIndexed{index,item->key(itemKey(item)){Box(Modifier.fillMaxWidth()){content(index,item)}}}
    }){measurables,constraints->
        val children=measurables.map{it.measure(constraints.copy(minHeight=0))}
        val gap=spacing.roundToPx()
        val height=children.sumOf{it.height}+gap*(children.size-1).coerceAtLeast(0)
        layout(constraints.maxWidth,constraints.constrainHeight(height)){
            var y=0
            children.forEachIndexed{index,child->child.placeRelative(0,positions[index].at(y,scope));y+=child.height+gap}
        }
    }
}
