package com.videocalls.app.webrtc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
fun WebRtcSurfaceView(
    modifier: Modifier = Modifier,
    videoTrack: VideoTrack?,
    eglBase: EglBase,
    isMirror: Boolean = false,
    scalingType: RendererCommon.ScalingType = RendererCommon.ScalingType.SCALE_ASPECT_FILL
) {
    val context = LocalContext.current
    val renderer = remember {
        SurfaceViewRenderer(context).apply {
            init(eglBase.eglBaseContext, null)
            setScalingType(scalingType)
            setMirror(isMirror)
            setEnableHardwareScaler(true)
        }
    }

    DisposableEffect(videoTrack) {
        videoTrack?.addSink(renderer)
        onDispose {
            videoTrack?.removeSink(renderer)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            renderer.release()
        }
    }

    AndroidView(
        factory = { renderer },
        modifier = modifier
    )
}
