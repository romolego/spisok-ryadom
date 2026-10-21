package com.spisokryadom.app.ui.shopcard

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun ShopCardBlock(title:String,action:(@Composable ()->Unit)?=null,content:@Composable ColumnScope.()->Unit){
    OutlinedCard(Modifier.fillMaxWidth(),colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface)){
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                action?.invoke()
            }
            content()
        }
    }
}

@Composable
internal fun ShopPlaceSummary(state:ShopCardUiState){
    Column(Modifier.testTag("shop-place-summary"),verticalArrangement=Arrangement.spacedBy(10.dp)){
        val memberships=state.branches.filter{it.venueId!=null}
        val isNetwork=state.networkId!=null||state.networkName.isNotBlank()
        Text(if(isNetwork)"Магазин сети"else if(memberships.isEmpty())"Самостоятельный магазин"else if(memberships.size==state.branches.size)"Магазин / павильон на рынке или в ТЦ"else "Магазин с точками на рынке / в ТЦ",
            style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
        Text(state.cities.find{it.id==state.cityId}?.name?:"Текущий город",style=MaterialTheme.typography.bodyMedium)
        state.networkName.ifBlank{state.networks.find{it.id==state.networkId}?.name.orEmpty()}.takeIf{it.isNotBlank()}?.let{
            Text("Сеть: $it",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(state.branches.isEmpty())Text("Адрес пока не указан",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        state.branches.forEach{branch->
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                Icon(Icons.Filled.LocationOn,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                Column{
                    val venue=state.venues.find{it.id==branch.venueId}?.name
                    Text(venue?:"Отдельный адрес",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                    val labels=com.spisokryadom.app.domain.PlaceLabels(state.branches,state.venues)
                    labels.detail(branch).takeIf{it.isNotBlank()}?.let{Text(it,style=MaterialTheme.typography.bodyMedium)}
                    if(branch.venueId!=null)Text(labels.address(branch).ifBlank{"Общий адрес пока не указан"},style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    branch.note?.takeIf{it.isNotBlank()}?.let{Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                }
            }
        }
    }
}
