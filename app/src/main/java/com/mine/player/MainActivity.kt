package com.mine.player

import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mine.player.audio.PlayerViewModel
import com.mine.player.data.SettingsRepository
import com.mine.player.ui.MinePlayerRoot
import com.mine.player.ui.theme.MinePlayerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settingsRepo = SettingsRepository(applicationContext)
        // Read the persisted theme synchronously so the very first frame already uses the
        // right palette. Without this the initial default is dark, and a light-mode user
        // gets a black flash before DataStore emits the real value.
        val initialSettings = runBlocking { settingsRepo.settings.first() }
        val systemDarkNow = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val initialDark = if (initialSettings.followSystemTheme) systemDarkNow else initialSettings.darkTheme
        window.setBackgroundDrawable(
            ColorDrawable(if (initialDark) 0xFF000000.toInt() else 0xFFF5F6F8.toInt())
        )
        setContent {
            val settings by settingsRepo.settings.collectAsState(initial = initialSettings)
            val dark = if (settings.followSystemTheme) isSystemInDarkTheme() else settings.darkTheme
            val ctx = LocalContext.current
            val accent = if (settings.accentFollowSystem && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)).primary
            } else {
                Color(settings.accentColor)
            }
            MinePlayerTheme(dark = dark, accent = accent) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val viewModel: PlayerViewModel = viewModel()
                    MinePlayerRoot(
                        viewModel = viewModel,
                        settings = settings,
                        settingsRepo = settingsRepo,
                    )
                }
            }
        }
    }
}
