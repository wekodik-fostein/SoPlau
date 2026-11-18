package com.soplau.rp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.soplau.rp.ui.AppRoot
import com.soplau.rp.ui.theme.SoPlauTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val container = (LocalContext.current.applicationContext as SoPlauApp).container
            val settings by container.settings.settings.collectAsState(initial = null)
            SoPlauTheme(themeChoice = settings?.theme) {
                AppRoot(container)
            }
        }
    }
}
