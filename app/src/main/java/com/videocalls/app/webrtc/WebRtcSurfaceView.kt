package com.videocalls.app.webrtc

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

private const val TAG = "WebRtcSurfaceView"

@Composable
fun WebRtcSurfaceView(
    modifier: Modifier = Modifier,
    videoTrack: VideoTrack?,
    eglBase: EglBase,
    isMirror: Boolean = false,
    scalingType: RendererCommon.ScalingType = RendererCommon.ScalingType.SCALE_ASPECT_FILL,
    isOverlay: Boolean = false,
    onClick: (() -> Unit)? = null,
    captureFrames: Boolean = false,
    onFrameCaptured: ((android.graphics.Bitmap) -> Unit)? = null
) {
    var boundTrack by remember { mutableStateOf<VideoTrack?>(null) }
    var rendererRef by remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    DisposableEffect(rendererRef, captureFrames) {
        val renderer = rendererRef
        var isCapturing = false

        val frameListener = object : org.webrtc.EglRenderer.FrameListener {
            override fun onFrame(bitmap: android.graphics.Bitmap?) {
                if (bitmap != null) {
                    onFrameCaptured?.invoke(bitmap)
                }
                if (captureFrames && renderer != null) {
                    renderer.postDelayed({
                        if (captureFrames) {
                            try {
                                renderer.addFrameListener(this, 0.45f)
                            } catch (_: Exception) {}
                        }
                    }, 50L)
                }
            }
        }

        if (captureFrames && renderer != null) {
            isCapturing = true
            try {
                renderer.addFrameListener(frameListener, 0.45f)
            } catch (_: Exception) {}
        }

        onDispose {
            if (isCapturing && renderer != null) {
                try {
                    renderer.removeFrameListener(frameListener)
                } catch (_: Exception) {}
            }
        }
    }

    DisposableEffect(videoTrack, rendererRef) {
        val renderer = rendererRef
        if (renderer != null && videoTrack != null) {
            if (boundTrack != videoTrack) {
                boundTrack?.removeSink(renderer)
                boundTrack = videoTrack
                videoTrack.addSink(renderer)
                Log.d(TAG, "Sink added for track: ${videoTrack.id()}, isOverlay=$isOverlay")
            }
        } else if (renderer != null && videoTrack == null && boundTrack != null) {
            boundTrack?.removeSink(renderer)
            boundTrack = null
            Log.d(TAG, "Sink removed because track is null, isOverlay=$isOverlay")
        }

        onDispose {
            if (renderer != null && boundTrack != null) {
                boundTrack?.removeSink(renderer)
                boundTrack = null
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                init(eglBase.eglBaseContext, object : RendererCommon.RendererEvents {
                    override fun onFirstFrameRendered() {
                        Log.d(TAG, "FIRST FRAME RENDERED! isOverlay=$isOverlay")
                    }

                    override fun onFrameResolutionChanged(videoWidth: Int, videoHeight: Int, rotation: Int) {
                        Log.d(TAG, "Frame resolution changed: ${videoWidth}x$videoHeight, rot=$rotation, isOverlay=$isOverlay")
                    }
                })
                setScalingType(scalingType)
                setMirror(isMirror)
                setEnableHardwareScaler(true)
                if (isOverlay) {
                    setZOrderMediaOverlay(true)
                } else {
                    setZOrderMediaOverlay(false)
                }
                if (onClick != null) {
                    setOnClickListener { onClick() }
                }
                rendererRef = this
            }
        },
        update = { renderer ->
            renderer.setScalingType(scalingType)
            renderer.setMirror(isMirror)
            if (isOverlay) {
                renderer.setZOrderMediaOverlay(true)
            }
            if (onClick != null) {
                renderer.setOnClickListener { onClick() }
            } else {
                renderer.setOnClickListener(null)
            }
            if (rendererRef !== renderer) {
                rendererRef = renderer
            }
        },
        onRelease = { renderer ->
            boundTrack?.removeSink(renderer)
            boundTrack = null
            rendererRef = null
            renderer.release()
            Log.d(TAG, "Renderer released, isOverlay=$isOverlay")
        }
    )
}
