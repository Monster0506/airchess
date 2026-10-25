package com.airchess.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import com.airchess.model.HapticKind
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.UIKit.UIPasteboard
import platform.UIKit.UISceneActivationState
import platform.UIKit.UISelectionFeedbackGenerator
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

class IosPlatformServices : PlatformServices {

    override fun copyText(label: String, text: String) {
        UIPasteboard.generalPasteboard.string = text
    }

    override fun shareText(title: String, text: String) {
        val top = topController() ?: return
        val sheet = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)

        // iPad crashes without an anchor for the popover form of the share sheet.
        sheet.popoverPresentationController?.let { popover ->
            val view = top.view
            popover.sourceView = view
            view.bounds.useContents {
                popover.sourceRect = CGRectMake(size.width / 2, size.height / 2, 0.0, 0.0)
            }
            popover.permittedArrowDirections = 0u
        }
        top.presentViewController(sheet, animated = true, completion = null)
    }

    override fun openUrl(url: String) {
        val target = NSURL.URLWithString(url) ?: return
        UIApplication.sharedApplication.openURL(target, options = emptyMap<Any?, Any?>(), completionHandler = null)
    }

    override fun haptic(kind: HapticKind) {
        when (kind) {
            HapticKind.SELECT -> UISelectionFeedbackGenerator().selectionChanged()
            HapticKind.MOVE -> impact(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
            HapticKind.CHECK -> notify(UINotificationFeedbackType.UINotificationFeedbackTypeWarning)
            HapticKind.REJECTED -> notify(UINotificationFeedbackType.UINotificationFeedbackTypeError)
            HapticKind.GAME_OVER -> notify(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
        }
    }

    @Composable
    override fun systemColorScheme(dark: Boolean): ColorScheme? = null

    private fun impact(style: UIImpactFeedbackStyle) {
        UIImpactFeedbackGenerator(style = style).impactOccurred()
    }

    private fun notify(type: UINotificationFeedbackType) {
        UINotificationFeedbackGenerator().notificationOccurred(type)
    }

    private fun topController(): UIViewController? {
        val scenes = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>()
        val scene = scenes.firstOrNull { it.activationState == UISceneActivationState.UISceneActivationStateForegroundActive }
            ?: scenes.firstOrNull()
        val windows = scene?.windows?.filterIsInstance<UIWindow>().orEmpty()
        var top = (windows.firstOrNull { it.isKeyWindow() } ?: windows.firstOrNull())?.rootViewController
        while (top?.presentedViewController != null) {
            top = top.presentedViewController
        }
        return top
    }
}
