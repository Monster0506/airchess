package com.airchess.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.net.toUri
import com.airchess.model.HapticKind

class AndroidPlatformServices(
    private val context: Context,
    private val haptics: HapticFeedback,
) : PlatformServices {

    override fun copyText(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    override fun shareText(title: String, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun openUrl(url: String) {
        val view = Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(view)
    }

    override fun haptic(kind: HapticKind) {
        haptics.performHapticFeedback(kind.feedbackType())
    }

    @Composable
    override fun systemColorScheme(dark: Boolean): ColorScheme? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return null
        }
        val activityContext = LocalContext.current
        return if (dark) dynamicDarkColorScheme(activityContext) else dynamicLightColorScheme(activityContext)
    }
}

@Composable
fun rememberAndroidPlatformServices(): AndroidPlatformServices {
    val context = LocalContext.current.applicationContext
    val haptics = LocalHapticFeedback.current
    return remember(context, haptics) { AndroidPlatformServices(context, haptics) }
}

private fun HapticKind.feedbackType(): HapticFeedbackType =
    when (this) {
        HapticKind.SELECT -> HapticFeedbackType.SegmentTick
        HapticKind.MOVE -> HapticFeedbackType.Confirm
        HapticKind.CHECK -> HapticFeedbackType.LongPress
        HapticKind.REJECTED -> HapticFeedbackType.Reject
        HapticKind.GAME_OVER -> HapticFeedbackType.LongPress
    }
