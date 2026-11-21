package com.mlw.serieustracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mlw.serieustracker.ui.AppNavigation
import com.mlw.serieustracker.ui.theme.SerieusTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SerieusTrackerTheme {
                AppNavigation()
            }
        }
    }
}
