package com.printplace.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.printplace.app.ui.navigation.PrintPlaceNavGraph
import com.printplace.app.ui.theme.PrintPlaceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PrintPlaceTheme {
                PrintPlaceNavGraph()
            }
        }
    }
}
