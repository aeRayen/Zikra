package com.zikra.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import com.zikra.core.designsystem.theme.ZikraTheme

/**
 * Single activity. Extends AppCompatActivity because per-app language on Android 12 and lower
 * needs it (AppLocales).
 */
class MainActivity : AppCompatActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZikraTheme {
                ZikraApp(widthSizeClass = calculateWindowSizeClass(this).widthSizeClass)
            }
        }
    }
}
