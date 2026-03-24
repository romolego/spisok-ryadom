package com.spisokryadom.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.spisokryadom.app.navigation.AppNavGraph
import com.spisokryadom.app.ui.theme.SpisokRyadomTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpisokRyadomTheme {
                AppNavGraph()
            }
        }
    }
}
