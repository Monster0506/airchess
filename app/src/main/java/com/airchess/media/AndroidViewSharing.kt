package com.airchess.media

import android.content.Context
import android.view.View
import com.airchess.viewmodel.ChessViewModel

/** WebRTC view sharing on Android: owns the scene capture and the one live session. */
class AndroidViewSharing(context: Context) : ViewSharing {
    private val appContext = context.applicationContext
    private var session: LiveCameraSession? = null

    val capture = SceneCapture()

    override val isReady: Boolean get() = capture.isReady

    override fun open(): ViewSharingSession =
        session ?: LiveCameraSession(appContext, capture).also { session = it }

    fun createView(context: Context, layout: CameraLayout, onSwap: () -> Unit): View? =
        session?.createView(context, layout, onSwap)

    fun updateView(view: View, layout: CameraLayout) {
        session?.updateView(view, layout)
    }

    fun releaseView(view: View) {
        session?.releaseView(view)
    }
}

/** MainActivity builds every Android view model with an [AndroidViewSharing]. */
val ChessViewModel.androidSharing: AndroidViewSharing
    get() = viewSharing as AndroidViewSharing
