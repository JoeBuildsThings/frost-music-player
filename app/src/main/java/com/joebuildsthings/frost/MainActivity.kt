package com.joebuildsthings.frost

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import com.joebuildsthings.frost.playback.PlaybackPrefs
import com.joebuildsthings.frost.ui.FrostApp
import com.joebuildsthings.frost.ui.theme.FrostTheme

class MainActivity : ComponentActivity() {

    private val vm: FrostViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val darkAtStart = getSharedPreferences(PlaybackPrefs.FILE, MODE_PRIVATE)
            .getBoolean(PlaybackPrefs.DARK_MODE, false)
        window.setBackgroundDrawable(
            ColorDrawable(if (darkAtStart) 0xFF0B1220.toInt() else 0xFFEAF2FF.toInt())
        )
        setContent {
            val dark = vm.darkMode
            DisposableEffect(dark) {
                val scrim = Color.argb(0x66, 0x00, 0x00, 0x00)
                this@MainActivity.enableEdgeToEdge(
                    statusBarStyle = if (dark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, scrim)
                    },
                    navigationBarStyle = if (dark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, scrim)
                    }
                )
                onDispose { }
            }
            FrostTheme(dark = dark) {
                FrostApp(vm)
            }
        }
    }
}
