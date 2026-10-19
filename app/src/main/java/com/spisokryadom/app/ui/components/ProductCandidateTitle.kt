package com.spisokryadom.app.ui.components
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spisokryadom.app.data.entity.ProductEntity

@Composable fun ProductCandidateTitle(product:ProductEntity,modifier:Modifier=Modifier,style:TextStyle=MaterialTheme.typography.bodyLarge){
 Row(modifier,verticalAlignment=Alignment.CenterVertically){
  Text(product.name,Modifier.weight(1f,fill=false),style=style,maxLines=2,overflow=TextOverflow.Ellipsis)
  if(product.isFavorite){Spacer(Modifier.width(6.dp));Icon(Icons.Filled.Star,"Избранный товар: ${product.name}",Modifier.size(18.dp),tint=MaterialTheme.colorScheme.primary)}
 }
}
