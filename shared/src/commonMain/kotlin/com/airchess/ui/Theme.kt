package com.airchess.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun AirChessTheme(platform: PlatformServices, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = platform.systemColorScheme(dark) ?: if (dark) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = scheme, content = content)
}
