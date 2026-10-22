package com.glavakartela.iconrecolorliquid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** Entry point: hosts the Compose UI. */
class MainActivity : ComponentActivity() {
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LiquidScreen(resumeTick) }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }
}
