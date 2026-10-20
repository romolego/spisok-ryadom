package com.spisokryadom.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spisokryadom.app.data.entity.*

data class PendingPurchase(val id:Long,val quantity:Double,val unit:String,val context:String,
 val shopId:Long?=null,val shopName:String?=null,val branch:ShopBranchEntity?=null,val annotation:String?=null)
fun pendingPurchases(entries:List<ShoppingListEntryEntity>,shops:Map<Long,ShopEntity>,branches:Map<Long,ShopBranchEntity>):List<PendingPurchase> = entries.sortedBy{it.id}.map{e->
 PendingPurchase(e.id,e.quantity,e.unit,listOfNotNull(shops[e.assignedShopId]?.name?:if(e.awaitingShopCityId!=null)"Магазин не настроен"else "Без магазина",branches[e.assignedBranchId]?.address,e.note,e.comment).distinct().joinToString(" · "),e.assignedShopId,shops[e.assignedShopId]?.name,branches[e.assignedBranchId],listOfNotNull(e.note,e.comment).joinToString(" · ").ifBlank{null})
}

/** Each visible control owns exactly the displayed purchase ID and unit. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable fun MultiplePurchaseCard(name:String,rows:List<PendingPurchase>,onAdjust:(Long,Int)->Unit,onSet:(Long,Double)->Unit,onOpen:()->Unit,modifier:Modifier=Modifier,favorite:Boolean=false,accent:String="") {
 var expanded by androidx.compose.runtime.saveable.rememberSaveable{mutableStateOf(false)}
 OutlinedCard(
  modifier=modifier.fillMaxWidth().then(Modifier.combinedClickable(onClick=onOpen,onLongClick=onOpen)),
  colors=CardDefaults.outlinedCardColors(containerColor=favoriteBackground(favorite,MaterialTheme.colorScheme.primaryContainer)),
  border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.primary.copy(alpha=.2f))
 ){
  Column(Modifier.fillMaxWidth().padding(12.dp)){
   Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
    Text(name,Modifier.weight(1f),fontWeight=favoriteWeight(favorite),style=MaterialTheme.typography.bodyLarge)
    if(favorite&&favoriteStyle().star)Icon(androidx.compose.material.icons.Icons.Filled.Star,"Избранный товар",Modifier.size(16.dp),tint=MaterialTheme.colorScheme.primary)
    Icon(androidx.compose.material.icons.Icons.Filled.CheckCircle,"В списке",tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(18.dp))
    TextButton(onClick={expanded=!expanded}){Text("Покупок: ${rows.size}")}
   }
   accentColor(accent)?.let{Box(Modifier.width(36.dp).height(2.dp).background(it))}
   androidx.compose.animation.AnimatedVisibility(expanded){Column{rows.forEach{row->key(row.id){
    HorizontalDivider()
    Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
     val labels=LocalPlaceLabels.current
     val context=row.shopName?.let{name->listOfNotNull(if(row.branch==null)labels.shop(row.shopId?:0,name)else labels.place(name,row.branch).text,row.annotation).joinToString(" · ")}?:row.context
     Text(context,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,maxLines=3,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
     InlineQuantityControl(row.quantity,row.unit,{onAdjust(row.id,1)},{onAdjust(row.id,-1)},{onSet(row.id,it)})
    }
   }}}}
  }
 }
}
