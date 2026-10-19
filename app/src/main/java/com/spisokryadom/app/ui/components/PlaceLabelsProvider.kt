package com.spisokryadom.app.ui.components

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spisokryadom.app.domain.PlaceLabels
import com.spisokryadom.app.data.repository.ShopRepository
import kotlinx.coroutines.flow.combine

val LocalPlaceLabels=staticCompositionLocalOf{PlaceLabels()}

@Composable fun ProvidePlaceLabels(repository:ShopRepository,content:@Composable ()->Unit) {
    val source=remember(repository){combine(repository.observeBranches(),repository.venues()){b,v->PlaceLabels(b,v)}}
    val labels by source.collectAsStateWithLifecycle(initialValue=PlaceLabels())
    CompositionLocalProvider(LocalPlaceLabels provides labels,content=content)
}
