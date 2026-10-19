package com.spisokryadom.app.ui.components
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*

/** LazyColumn otherwise follows the key of its first visible item when that row moves.
 * Keep the viewport fixed for explicit reorder actions; item placement does the movement.
 */
class ReorderViewport(private val state:LazyListState){
 internal var order:Any?=null
 private var anchor:Pair<Int,Int>?=null
 fun capture(){anchor=state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset}
 fun restore(){anchor?.let{state.requestScrollToItem(it.first,it.second)};anchor=null}
}
@Composable fun rememberReorderViewport(state:LazyListState,order:Any):ReorderViewport{
 val viewport=remember(state){ReorderViewport(state)}
 SideEffect{if(viewport.order!=order){viewport.restore();viewport.order=order}}
 return viewport
}
