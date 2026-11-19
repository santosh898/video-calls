package com.videocalls.app.ui.interactive

import android.graphics.PointF
import android.graphics.RectF

enum class InteractiveEffectType(val displayName: String, val icon: String) {
    NONE("None", "❌"),
    SHADES("Cool Shades", "🕶️"),
    PARTY_HAT("Party Hat", "🥳"),
    RAIN("Rain Drops", "🌧️"),
    SNOW("Snow Blizzard", "❄️"),
    TEARS("Drama Tears", "😭"),
    TWINKLES("Magic Stars", "✨"),
    KALEIDOSCOPE("Kaleidoscope", "🌀"),
    DOGGY("Puppy Dog", "🐶"),
    KITTY("Kitty Cat", "🐱")
}

typealias InteractiveFilterType = InteractiveEffectType

enum class InteractiveBackgroundType(val displayName: String, val icon: String) {
    NONE("None", "❌"),
    SPACE("Space Galaxy", "🚀"),
    UNDERWATER("Under the Sea", "🐠"),
    SUNNY_BEACH("Sunny Beach", "🏖️"),
    CYBER_CITY("Cyber City", "🌆"),
    COZY_ROOM("Cozy Room", "🛋️"),
    FANTASY_CASTLE("Magic Castle", "🏰"),
    CARTOON_CARS("Cartoon Cars", "🚗"),
    CANDY_LAND("Candy Land", "🍭"),
    RAINBOW("Rainbow Sky", "🌈")
}

enum class InteractiveGameType(val displayName: String, val icon: String) {
    NONE("None", "❌"),
    BUBBLE_POP("Pop Bubbles", "🫧"),
    PEEK_A_BOO("Peek-a-boo", "🙈")
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
