package com.airchess.media

import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.view.TextureView
import android.widget.FrameLayout
import java.util.concurrent.CountDownLatch
import org.webrtc.EglBase
import org.webrtc.EglRenderer
import org.webrtc.GlRectDrawer
import org.webrtc.ThreadUtils
import org.webrtc.VideoFrame
import org.webrtc.VideoSink

internal class VideoTexture(context: Context, shared: EglBase.Context) : FrameLayout(context), VideoSink, TextureView.SurfaceTextureListener {
    private val renderer = EglRenderer("AirChessVideo")
    private val texture = TextureView(context)
    private val transform = Matrix()
    @Volatile private var frameAspect = 1f
    @Volatile private var released = false

    init {
        setBackgroundColor(Color.BLACK)
        renderer.init(shared, EglBase.CONFIG_PLAIN, GlRectDrawer())
        renderer.setLayoutAspectRatio(0f)
        texture.surfaceTextureListener = this
        texture.isOpaque = false
        addView(texture, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onFrame(frame: VideoFrame) {
        if (released) { return }
        val aspect = frame.rotatedWidth.toFloat() / frame.rotatedHeight
        if (frameAspect != aspect) {
            frameAspect = aspect
            post(::fit)
        }
        renderer.onFrame(frame)
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        if (released) { return }
        renderer.createEglSurface(surface)
        fit()
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) { fit() }
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        if (!released) {
            val detached = CountDownLatch(1)
            renderer.releaseEglSurface(detached::countDown)
            ThreadUtils.awaitUninterruptibly(detached)
        }
        return true
    }

    fun release() {
        if (released) { return }
        released = true
        texture.surfaceTextureListener = null
        renderer.release()
    }

    private fun fit() {
        if (texture.width == 0 || texture.height == 0 || released) { return }
        val layoutAspect = texture.width.toFloat() / texture.height
        val x = minOf(1f, frameAspect / layoutAspect)
        val y = minOf(1f, layoutAspect / frameAspect)
        transform.setScale(x, y, texture.width / 2f, texture.height / 2f)
        texture.setTransform(transform)
    }
}
