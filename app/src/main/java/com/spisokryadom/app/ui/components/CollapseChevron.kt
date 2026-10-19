package com.spisokryadom.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate

@Composable
fun CollapseChevron(collapsed:Boolean,description:String?,modifier:Modifier=Modifier) {
    val angle by animateFloatAsState(if(collapsed)0f else 180f,tween(260),label="collapseChevron")
    Icon(Icons.Filled.ExpandMore,description,modifier.rotate(angle),tint=MaterialTheme.colorScheme.primary)
}
