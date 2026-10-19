package com.spisokryadom.app

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.spisokryadom.app.navigation.AppNavGraph
import com.spisokryadom.app.ui.theme.SpisokRyadomTheme

class MainActivity : ComponentActivity() {
    private var widgetRequest by androidx.compose.runtime.mutableStateOf<String?>(null)
    private fun consumeWidgetRequest(){widgetRequest=null;intent=android.content.Intent(intent).setAction(android.content.Intent.ACTION_MAIN)}
    override fun onNewIntent(intent:android.content.Intent) {
        super.onNewIntent(intent);setIntent(intent)
        widgetRequest=com.spisokryadom.app.widget.WidgetIntents.destination(intent)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetRequest=com.spisokryadom.app.widget.WidgetIntents.destination(intent)
        enableEdgeToEdge()
        setContent {
            SpisokRyadomTheme {
                com.spisokryadom.app.ui.components.ProvidePlaceLabels((application as SpisokRyadomApp).container.shopRepository) { AppNavGraph(widgetRequest, ::consumeWidgetRequest) }
            }
        }
    }
}
