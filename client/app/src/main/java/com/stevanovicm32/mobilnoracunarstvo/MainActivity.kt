package com.stevanovicm32.mobilnoracunarstvo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.stevanovicm32.mobilnoracunarstvo.ui.navigation.AppNavHost
import com.stevanovicm32.mobilnoracunarstvo.ui.theme.MobilnoRacunarstvoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MobilnoRacunarstvoTheme {
                AppNavHost()
            }
        }
    }
}
