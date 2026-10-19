package com.spisokryadom.app.ui.components
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

class GroupExpansion(private val prefs:SharedPreferences,private val key:String) {
 var collapsed by mutableStateOf(prefs.getStringSet(key,emptySet())!!.toSet());private set
 fun reload(){collapsed=prefs.getStringSet(key,emptySet())!!.toSet()}
 fun expand(id:String){if(id in collapsed){collapsed=collapsed-id;prefs.edit().putStringSet(key,collapsed).apply()}}
 fun toggle(id:String){val next=collapsed.toMutableSet();if(!next.add(id))next.remove(id);collapsed=next;prefs.edit().putStringSet(key,next).apply()}
}
@Composable fun rememberGroups(prefsName:String,key:String):GroupExpansion {
 val context=LocalContext.current
 val prefs=remember(prefsName){context.getSharedPreferences(prefsName,0)}
 val state=remember(prefs,key){GroupExpansion(prefs,key)}
 DisposableEffect(prefs,key){val listener=SharedPreferences.OnSharedPreferenceChangeListener{_,changed->if(changed==key)state.reload()};prefs.registerOnSharedPreferenceChangeListener(listener);onDispose{prefs.unregisterOnSharedPreferenceChangeListener(listener)}}
 return state
}
