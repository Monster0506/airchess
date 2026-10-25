package com.airchess.media

import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import io.github.sceneview.RenderInvalidator
import io.github.sceneview.rememberSurfaceMirrorer
import io.github.sceneview.utils.SurfaceMirrorer
import org.webrtc.CapturerObserver
import org.webrtc.EglBase
import org.webrtc.SurfaceTextureHelper

class SceneCapture {
    private var mirror: SurfaceMirrorer? = null
    private var invalidator: RenderInvalidator? = null
    private var surface: Surface? = null
    private var helper: SurfaceTextureHelper? = null
    private var observer: CapturerObserver? = null
    private var onLost: (() -> Unit)? = null
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val keepAlive = object : Runnable {
        override fun run() {
            if (helper == null) { return }
            // On-demand 3D scenes go idle; late-opened remote views still need recent frames.
            invalidator?.requestRender()
            main.postDelayed(this, KEEPALIVE_MS)
        }
    }

    val isReady: Boolean get() = mirror != null

    @Composable
    fun rememberMirror(wake: RenderInvalidator? = null): SurfaceMirrorer {
        val current = rememberSurfaceMirrorer()
        DisposableEffect(this, current, wake) {
            bind(current, wake)
            onDispose { unbind(current) }
        }
        return current
    }

    internal fun start(context: EglBase.Context, target: CapturerObserver, lost: () -> Unit) {
        check(isReady) { "Open the AR or 3D board before sharing" }
        stop()
        observer = target
        onLost = lost
        val texture = SurfaceTextureHelper.create("AirChessView", context)
            ?: error("Could not capture the rendered view")
        helper = texture
        texture.setTextureSize(WIDTH, HEIGHT)
        texture.startListening(target::onFrameCaptured)
        surface = Surface(texture.surfaceTexture)
        attach()
        target.onCapturerStarted(true)
        main.post(keepAlive)
    }

    internal fun stop() {
        main.removeCallbacks(keepAlive)
        surface?.let { mirror?.stopMirroring(it) }
        helper?.stopListening()
        observer?.onCapturerStopped()
        observer = null
        onLost = null
        surface?.release()
        surface = null
        helper?.dispose()
        helper = null
    }

    private fun bind(current: SurfaceMirrorer, wake: RenderInvalidator?) {
        if (mirror === current) { return }
        surface?.let { mirror?.stopMirroring(it) }
        mirror = current
        invalidator = wake
        attach()
    }

    private fun unbind(current: SurfaceMirrorer) {
        if (mirror !== current) { return }
        surface?.let(current::stopMirroring)
        mirror = null
        invalidator = null
        if (helper == null) { return }
        main.post { if (mirror == null && helper != null) { onLost?.invoke() } }
    }

    private fun attach() {
        surface?.let {
            mirror?.startMirroring(it, width = WIDTH, height = HEIGHT)
            // Mirroring is not snapshot state; wake an already-idle 3D scene once.
            invalidator?.requestRender()
        }
    }

    companion object {
        internal const val WIDTH = 640
        internal const val HEIGHT = 1280
        internal const val FPS = 15
        private const val KEEPALIVE_MS = 1000L
    }
}
