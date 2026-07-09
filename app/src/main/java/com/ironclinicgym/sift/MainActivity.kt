package com.ironclinicgym.sift

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironclinicgym.sift.ui.navigation.SiftNavHost
import com.ironclinicgym.sift.ui.theme.SiftTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appPrefs = (application as SiftApp).container.appPreferences
        setContent {
            val themeMode by appPrefs.themeMode().collectAsStateWithLifecycle(initialValue = "auto")
            SiftTheme(themeMode = themeMode) {
                SiftNavHost()
            }
        }
    }
}
