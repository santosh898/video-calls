package com.videocalls.app.ui.interactive

import android.graphics.PointF
import android.graphics.RectF

enum class InteractiveFilterType(val displayName: String, val icon: String) {
    NONE("None", "❌"),
    DOGGY("Puppy", "🐶"),
    KITTY("Kitty", "🐱"),
    SUPERHERO("Hero", "🦸"),
    FUNNY_GLASSES("Silly", "🥸"),
    MAGIC_CROWN("Star King", "👑")
}

enum class InteractiveGameType(val displayName: String, val icon: String) {
    NONE("None", "❌"),
    BUBBLE_POP("Pop Bubbles", "🫧"),
    PEEK_A_BOO("Peek-a-boo", "🙈"),
    DOODLE("Magic Draw", "🎨")
}

data class NormalizedFace(
    val bounds: RectF, // 0f..1f
    val leftEye: PointF? = null,
    val rightEye: PointF? = null,
    val noseBase: PointF? = null,
    val leftEar: PointF? = null,
    val rightEar: PointF? = null,
    val mouthLeft: PointF? = null,
    val mouthRight: PointF? = null,
    val mouthBottom: PointF? = null,
    val headEulerZ: Float = 0f, // Tilt angle in degrees
    val headEulerY: Float = 0f, // Rotation / turn angle in degrees
    val isMouthOpen: Boolean = false
)

data class DoodlePoint(val x: Float, val y: Float)
data class DoodleStroke(
    val color: Long,
    val strokeWidth: Float,
    val points: List<DoodlePoint>
)
