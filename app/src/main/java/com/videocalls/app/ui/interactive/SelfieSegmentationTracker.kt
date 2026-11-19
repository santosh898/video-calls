package com.videocalls.app.ui.interactive

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import org.webrtc.VideoTrack
import org.webrtc.YuvHelper
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class SelfieSegmentationTracker : VideoSink {
    private val _maskBitmap = MutableStateFlow<Bitmap?>(null)
    val maskBitmap: StateFlow<Bitmap?> = _maskBitmap.asStateFlow()

    private var activeTrack: VideoTrack? = null
    private var isMirror: Boolean = false

    private val isProcessing = AtomicBoolean(false)
    private var lastFrameTime = 0L
    private val frameIntervalMs = 60L // ~16 fps max for smooth segmentation with low CPU usage

    private val segmenter = Segmentation.getClient(
        SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
            .setStreamModeSmoothingRatio(0.25f)
            .build()
    )

    fun attach(track: VideoTrack?, mirror: Boolean = false) {
        if (activeTrack == track && isMirror == mirror) return
        detach()
        activeTrack = track
        isMirror = mirror
        track?.addSink(this)
        Log.d(TAG, "SelfieSegmentationTracker attached to track: ${track?.id()}, mirror=$mirror")
    }

    fun detach() {
        activeTrack?.removeSink(this)
        activeTrack = null
        _maskBitmap.value = null
        Log.d(TAG, "SelfieSegmentationTracker detached")
    }

    override fun onFrame(videoFrame: VideoFrame) {
        val now = System.currentTimeMillis()
        if (now - lastFrameTime < frameIntervalMs) return
        if (!isProcessing.compareAndSet(false, true)) return
        lastFrameTime = now

        try {
            val i420 = videoFrame.buffer.toI420() ?: run {
                isProcessing.set(false)
                return
            }

            val width = i420.width
            val height = i420.height
            val nv21Size = width * height * 3 / 2
            val nv21Buffer = ByteBuffer.allocateDirect(nv21Size)

            // Swapping U and V transforms I420 into NV21 (VU interleaved)
            YuvHelper.I420ToNV12(
                i420.dataY, i420.strideY,
                i420.dataV, i420.strideV,
                i420.dataU, i420.strideU,
                nv21Buffer, width, height
            )
            i420.release()

            val rotationDegrees = videoFrame.rotation
            val inputImage = InputImage.fromByteBuffer(
                nv21Buffer,
                width,
                height,
                rotationDegrees,
                InputImage.IMAGE_FORMAT_NV21
            )

            segmenter.process(inputImage)
                .addOnSuccessListener { segmentationMask ->
                    val maskBuffer = segmentationMask.buffer
                    val maskWidth = segmentationMask.width
                    val maskHeight = segmentationMask.height
                    maskBuffer.rewind()

                    val pixels = IntArray(maskWidth * maskHeight)
                    for (i in 0 until (maskWidth * maskHeight)) {
                        val conf = maskBuffer.float
                        // For DstOut: Person pixels get high alpha to punch out the background overlay.
                        // Room pixels get 0 alpha so the cartoon background covers them completely.
                        val alpha = if (conf >= 0.70f) {
                            255
                        } else if (conf <= 0.25f) {
                            0
                        } else {
                            ((conf - 0.25f) / 0.45f * 255f).toInt().coerceIn(0, 255)
                        }
                        pixels[i] = (alpha shl 24) or 0x00FFFFFF
                    }

                    val rawBitmap = Bitmap.createBitmap(maskWidth, maskHeight, Bitmap.Config.ARGB_8888)
                    rawBitmap.setPixels(pixels, 0, maskWidth, 0, 0, maskWidth, maskHeight)

                    if (isMirror) {
                        val matrix = Matrix().apply { preScale(-1f, 1f) }
                        val mirrored = Bitmap.createBitmap(rawBitmap, 0, 0, maskWidth, maskHeight, matrix, false)
                        rawBitmap.recycle()
                        _maskBitmap.value = mirrored
                    } else {
                        _maskBitmap.value = rawBitmap
                    }
                    isProcessing.set(false)
                }
                .addOnFailureListener {
                    isProcessing.set(false)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error in SelfieSegmentationTracker onFrame", e)
            isProcessing.set(false)
        }
    }

    fun release() {
        detach()
        segmenter.close()
    }

    companion object {
        private const val TAG = "SelfieSegTracker"
    }
}
