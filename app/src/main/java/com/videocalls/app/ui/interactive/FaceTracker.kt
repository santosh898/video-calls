package com.videocalls.app.ui.interactive

import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import org.webrtc.VideoTrack
import org.webrtc.YuvHelper
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class FaceTracker : VideoSink {
    private val _detectedFace = MutableStateFlow<NormalizedFace?>(null)
    val detectedFace: StateFlow<NormalizedFace?> = _detectedFace.asStateFlow()

    private var activeTrack: VideoTrack? = null
    private var isMirror: Boolean = false

    private val isProcessing = AtomicBoolean(false)
    private var lastFrameTime = 0L
    private val frameIntervalMs = 70L // ~14 fps max, keeps CPU and battery usage low

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.15f)
            .build()
    )

    fun attach(track: VideoTrack?, mirror: Boolean = false) {
        if (activeTrack == track && isMirror == mirror) return
        detach()
        activeTrack = track
        isMirror = mirror
        track?.addSink(this)
        Log.d(TAG, "FaceTracker attached to track: ${track?.id()}, mirror=$mirror")
    }

    fun detach() {
        activeTrack?.removeSink(this)
        activeTrack = null
        _detectedFace.value = null
        Log.d(TAG, "FaceTracker detached")
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

            val rotatedWidth = if (rotationDegrees == 90 || rotationDegrees == 270) height.toFloat() else width.toFloat()
            val rotatedHeight = if (rotationDegrees == 90 || rotationDegrees == 270) width.toFloat() else height.toFloat()

            detector.process(inputImage)
                .addOnSuccessListener { faces ->
                    val face = faces.firstOrNull()
                    if (face != null) {
                        _detectedFace.value = mapFaceToNormalized(face, rotatedWidth, rotatedHeight, isMirror)
                    } else {
                        _detectedFace.value = null
                    }
                    isProcessing.set(false)
                }
                .addOnFailureListener {
                    isProcessing.set(false)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error in FaceTracker onFrame", e)
            isProcessing.set(false)
        }
    }

    private fun mapFaceToNormalized(
        face: Face,
        width: Float,
        height: Float,
        mirror: Boolean
    ): NormalizedFace {
        fun normX(x: Float): Float {
            val n = (x / width).coerceIn(0f, 1f)
            return if (mirror) 1f - n else n
        }
        fun normY(y: Float): Float {
            return (y / height).coerceIn(0f, 1f)
        }

        fun mapPoint(p: PointF?): PointF? {
            p ?: return null
            return PointF(normX(p.x), normY(p.y))
        }

        val bbox = face.boundingBox
        val left = normX(bbox.left.toFloat())
        val right = normX(bbox.right.toFloat())
        val top = normY(bbox.top.toFloat())
        val bottom = normY(bbox.bottom.toFloat())
        val normBounds = RectF(
            minOf(left, right),
            minOf(top, bottom),
            maxOf(left, right),
            maxOf(top, bottom)
        )

        val leftEye = mapPoint(face.getLandmark(FaceLandmark.LEFT_EYE)?.position)
        val rightEye = mapPoint(face.getLandmark(FaceLandmark.RIGHT_EYE)?.position)
        val noseBase = mapPoint(face.getLandmark(FaceLandmark.NOSE_BASE)?.position)
        val leftEar = mapPoint(face.getLandmark(FaceLandmark.LEFT_EAR)?.position)
        val rightEar = mapPoint(face.getLandmark(FaceLandmark.RIGHT_EAR)?.position)
        val mouthLeft = mapPoint(face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position)
        val mouthRight = mapPoint(face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position)
        val mouthBottom = mapPoint(face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position)

        val rawNose = face.getLandmark(FaceLandmark.NOSE_BASE)?.position
        val rawMouthBottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position
        val rawMouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position
        val rawMouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position
        val rawLeftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val rawRightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position

        // Detect mouth open / tongue trigger using calibrated scale-independent facial proportions
        val isMouthOpen = run {
            if (rawNose != null && rawMouthBottom != null) {
                val noseToMouth = kotlin.math.hypot(rawMouthBottom.x - rawNose.x, rawMouthBottom.y - rawNose.y)
                val bboxRatio = if (bbox.height() > 0) noseToMouth / bbox.height() else 0f

                // Primary check: Mouth Aspect Ratio (MAR) when mouth corners are detected
                if (rawMouthLeft != null && rawMouthRight != null) {
                    val mw = kotlin.math.hypot(rawMouthRight.x - rawMouthLeft.x, rawMouthRight.y - rawMouthLeft.y)
                    val mcX = (rawMouthLeft.x + rawMouthRight.x) * 0.5f
                    val mcY = (rawMouthLeft.y + rawMouthRight.y) * 0.5f
                    val drop = kotlin.math.hypot(rawMouthBottom.x - mcX, rawMouthBottom.y - mcY)

                    // On relaxed/closed lips, drop / mw is 0.18-0.28.
                    // When jaw drops / mouth opens, drop / mw exceeds 0.44.
                    if (mw > 0f && (drop / mw) > 0.44f && bboxRatio > 0.27f) {
                        return@run true
                    }
                }

                // Secondary check: Rigid skull distance (eye-to-nose vs nose-to-mouth)
                if (rawLeftEye != null && rawRightEye != null) {
                    val eyeCX = (rawLeftEye.x + rawRightEye.x) * 0.5f
                    val eyeCY = (rawLeftEye.y + rawRightEye.y) * 0.5f
                    val eyeToNose = kotlin.math.hypot(rawNose.x - eyeCX, rawNose.y - eyeCY)
                    // On relaxed face, noseToMouth / eyeToNose is 0.85-1.15.
                    // When jaw drops, it exceeds 1.30.
                    if (eyeToNose > 0f && (noseToMouth / eyeToNose) > 1.30f && bboxRatio > 0.285f) {
                        return@run true
                    }
                }

                // Fallback for extreme jaw drop if other landmarks missing
                if (bboxRatio > 0.31f) {
                    return@run true
                }
            }
            false
        }

        return NormalizedFace(
            bounds = normBounds,
            leftEye = if (mirror) rightEye else leftEye,
            rightEye = if (mirror) leftEye else rightEye,
            noseBase = noseBase,
            leftEar = if (mirror) rightEar else leftEar,
            rightEar = if (mirror) leftEar else rightEar,
            mouthLeft = if (mirror) mouthRight else mouthLeft,
            mouthRight = if (mirror) mouthLeft else mouthRight,
            mouthBottom = mouthBottom,
            headEulerZ = if (mirror) -face.headEulerAngleZ else face.headEulerAngleZ,
            headEulerY = if (mirror) -face.headEulerAngleY else face.headEulerAngleY,
            isMouthOpen = isMouthOpen
        )
    }

    fun release() {
        detach()
        detector.close()
    }

    companion object {
        private const val TAG = "FaceTracker"
    }
}
