package com.videocalls.app.ui.interactive

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun FaceFiltersOverlay(
    modifier: Modifier = Modifier,
    filterType: InteractiveFilterType,
    face: NormalizedFace?
) {
    if (filterType == InteractiveFilterType.NONE || face == null) return

    val infiniteTransition = rememberInfiniteTransition(label = "faceAnim")
    val earBounce by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "earBounce"
    )
    val tongueDrop by animateFloatAsState(
        targetValue = if (face.isMouthOpen) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tongueDrop"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        val faceRect = Rect(
            left = face.bounds.left * w,
            top = face.bounds.top * h,
            right = face.bounds.right * w,
            bottom = face.bounds.bottom * h
        )

        val nose = face.noseBase?.let { Offset(it.x * w, it.y * h) }
            ?: Offset(faceRect.center.x, faceRect.center.y)
        val leftEye = face.leftEye?.let { Offset(it.x * w, it.y * h) }
            ?: Offset(faceRect.left + faceRect.width * 0.3f, faceRect.top + faceRect.height * 0.35f)
        val rightEye = face.rightEye?.let { Offset(it.x * w, it.y * h) }
            ?: Offset(faceRect.left + faceRect.width * 0.7f, faceRect.top + faceRect.height * 0.35f)
        val mouthBottom = face.mouthBottom?.let { Offset(it.x * w, it.y * h) }
            ?: Offset(faceRect.center.x, faceRect.bottom - faceRect.height * 0.15f)

        rotate(face.headEulerZ, pivot = faceRect.center) {
            when (filterType) {
                InteractiveFilterType.DOGGY -> {
                    drawDoggyFilter(faceRect, nose, mouthBottom, earBounce, tongueDrop)
                }
                InteractiveFilterType.KITTY -> {
                    drawKittyFilter(faceRect, nose, earBounce)
                }
                InteractiveFilterType.SUPERHERO -> {
                    drawSuperheroFilter(faceRect, leftEye, rightEye)
                }
                InteractiveFilterType.FUNNY_GLASSES -> {
                    drawFunnyGlassesFilter(faceRect, leftEye, rightEye, nose)
                }
                InteractiveFilterType.MAGIC_CROWN -> {
                    drawMagicCrownFilter(faceRect, earBounce)
                }
                InteractiveFilterType.NONE -> {}
            }
        }
    }
}

// 1. DOGGY FILTER
private fun DrawScope.drawDoggyFilter(
    faceRect: Rect,
    nose: Offset,
    mouthBottom: Offset,
    bounce: Float,
    tongueProgress: Float
) {
    val earWidth = faceRect.width * 0.34f
    val earHeight = faceRect.height * 0.52f
    val earTop = faceRect.top - earHeight * 0.35f

    val brownColor = Color(0xFF8B4513)
    val innerPink = Color(0xFFFFB6C1)

    // Left Floppy Ear
    rotate(-15f + bounce, pivot = Offset(faceRect.left, earTop)) {
        val leftEarPath = Path().apply {
            moveTo(faceRect.left + earWidth * 0.3f, earTop)
            cubicTo(
                faceRect.left - earWidth * 0.4f, earTop + earHeight * 0.3f,
                faceRect.left - earWidth * 0.5f, earTop + earHeight * 0.8f,
                faceRect.left, earTop + earHeight
            )
            cubicTo(
                faceRect.left + earWidth * 0.6f, earTop + earHeight * 0.9f,
                faceRect.left + earWidth * 0.7f, earTop + earHeight * 0.4f,
                faceRect.left + earWidth * 0.3f, earTop
            )
            close()
        }
        drawPath(leftEarPath, color = brownColor, style = Fill)
        drawPath(leftEarPath, color = innerPink, style = Stroke(width = 8f))
    }

    // Right Floppy Ear
    rotate(15f - bounce, pivot = Offset(faceRect.right, earTop)) {
        val rightEarPath = Path().apply {
            moveTo(faceRect.right - earWidth * 0.3f, earTop)
            cubicTo(
                faceRect.right + earWidth * 0.4f, earTop + earHeight * 0.3f,
                faceRect.right + earWidth * 0.5f, earTop + earHeight * 0.8f,
                faceRect.right, earTop + earHeight
            )
            cubicTo(
                faceRect.right - earWidth * 0.6f, earTop + earHeight * 0.9f,
                faceRect.right - earWidth * 0.7f, earTop + earHeight * 0.4f,
                faceRect.right - earWidth * 0.3f, earTop
            )
            close()
        }
        drawPath(rightEarPath, color = brownColor, style = Fill)
        drawPath(rightEarPath, color = innerPink, style = Stroke(width = 8f))
    }

    // Doggy Snout & Nose
    val noseRadius = faceRect.width * 0.11f
    drawCircle(
        color = Color(0xFFF5DEB3), // Wheat muzzle background
        radius = noseRadius * 1.6f,
        center = Offset(nose.x, nose.y + noseRadius * 0.3f)
    )
    // Black button nose
    val nosePath = Path().apply {
        moveTo(nose.x - noseRadius, nose.y)
        quadraticTo(nose.x, nose.y - noseRadius * 0.6f, nose.x + noseRadius, nose.y)
        quadraticTo(nose.x, nose.y + noseRadius * 0.9f, nose.x - noseRadius, nose.y)
        close()
    }
    drawPath(nosePath, color = Color(0xFF1E1E1E), style = Fill)
    // White nose glint
    drawCircle(
        color = Color.White.copy(alpha = 0.8f),
        radius = noseRadius * 0.25f,
        center = Offset(nose.x - noseRadius * 0.3f, nose.y - noseRadius * 0.1f)
    )

    // Animated Dropping Tongue
    if (tongueProgress > 0.05f) {
        val tongueWidth = faceRect.width * 0.22f
        val maxTongueLen = faceRect.height * 0.32f
        val currentTongueLen = maxTongueLen * tongueProgress

        val tongueY = mouthBottom.y
        val tonguePath = Path().apply {
            moveTo(nose.x - tongueWidth / 2f, tongueY)
            lineTo(nose.x - tongueWidth / 2f, tongueY + currentTongueLen * 0.7f)
            quadraticTo(
                nose.x, tongueY + currentTongueLen,
                nose.x + tongueWidth / 2f, tongueY + currentTongueLen * 0.7f
            )
            lineTo(nose.x + tongueWidth / 2f, tongueY)
            close()
        }
        drawPath(tonguePath, color = Color(0xFFFF4D6D), style = Fill)
        // Center crease on tongue
        drawLine(
            color = Color(0xFFCC1F41),
            start = Offset(nose.x, tongueY + 4f),
            end = Offset(nose.x, tongueY + currentTongueLen * 0.75f),
            strokeWidth = 4f
        )
    }
}

// 2. KITTY FILTER
private fun DrawScope.drawKittyFilter(faceRect: Rect, nose: Offset, bounce: Float) {
    val earSize = faceRect.width * 0.32f
    val earY = faceRect.top - earSize * 0.4f

    // Left Cat Ear (Triangle)
    val leftEar = Path().apply {
        moveTo(faceRect.left, earY + earSize * 0.8f)
        lineTo(faceRect.left + earSize * 0.3f, earY)
        lineTo(faceRect.left + earSize, earY + earSize * 0.9f)
        close()
    }
    drawPath(leftEar, color = Color(0xFFFF9EAA), style = Fill)
    // Inner ear
    val innerLeftEar = Path().apply {
        moveTo(faceRect.left + earSize * 0.15f, earY + earSize * 0.75f)
        lineTo(faceRect.left + earSize * 0.35f, earY + earSize * 0.2f)
        lineTo(faceRect.left + earSize * 0.8f, earY + earSize * 0.8f)
        close()
    }
    drawPath(innerLeftEar, color = Color(0xFFFF4081), style = Fill)

    // Right Cat Ear (Triangle)
    val rightEar = Path().apply {
        moveTo(faceRect.right - earSize, earY + earSize * 0.9f)
        lineTo(faceRect.right - earSize * 0.3f, earY)
        lineTo(faceRect.right, earY + earSize * 0.8f)
        close()
    }
    drawPath(rightEar, color = Color(0xFFFF9EAA), style = Fill)
    // Inner ear
    val innerRightEar = Path().apply {
        moveTo(faceRect.right - earSize * 0.8f, earY + earSize * 0.8f)
        lineTo(faceRect.right - earSize * 0.35f, earY + earSize * 0.2f)
        lineTo(faceRect.right - earSize * 0.15f, earY + earSize * 0.75f)
        close()
    }
    drawPath(innerRightEar, color = Color(0xFFFF4081), style = Fill)

    // Pink Cat Nose (Inverted Triangle)
    val noseSize = faceRect.width * 0.08f
    val catNose = Path().apply {
        moveTo(nose.x - noseSize, nose.y - noseSize * 0.5f)
        lineTo(nose.x + noseSize, nose.y - noseSize * 0.5f)
        lineTo(nose.x, nose.y + noseSize * 0.6f)
        close()
    }
    drawPath(catNose, color = Color(0xFFFF4081), style = Fill)

    // Whiskers (3 on each side)
    val whiskerLen = faceRect.width * 0.35f
    val whiskerColor = Color.White
    val whiskerStroke = 5f

    for (i in -1..1) {
        val dy = i * faceRect.height * 0.04f
        val tilt = i * 6f
        // Left Whiskers
        drawLine(
            color = whiskerColor,
            start = Offset(nose.x - faceRect.width * 0.12f, nose.y + dy),
            end = Offset(nose.x - faceRect.width * 0.12f - whiskerLen, nose.y + dy + tilt),
            strokeWidth = whiskerStroke
        )
        // Right Whiskers
        drawLine(
            color = whiskerColor,
            start = Offset(nose.x + faceRect.width * 0.12f, nose.y + dy),
            end = Offset(nose.x + faceRect.width * 0.12f + whiskerLen, nose.y + dy + tilt),
            strokeWidth = whiskerStroke
        )
    }
}

// 3. SUPERHERO MASK
private fun DrawScope.drawSuperheroFilter(faceRect: Rect, leftEye: Offset, rightEye: Offset) {
    val maskHeight = faceRect.height * 0.32f
    val maskTop = leftEye.y - maskHeight * 0.45f
    val eyeHoleWidth = faceRect.width * 0.22f
    val eyeHoleHeight = maskHeight * 0.45f

    val maskPath = Path().apply {
        moveTo(faceRect.left - faceRect.width * 0.05f, leftEye.y)
        quadraticTo(
            faceRect.center.x, maskTop - maskHeight * 0.2f,
            faceRect.right + faceRect.width * 0.05f, rightEye.y
        )
        quadraticTo(
            faceRect.right, maskTop + maskHeight,
            faceRect.center.x, maskTop + maskHeight * 0.75f
        )
        quadraticTo(
            faceRect.left, maskTop + maskHeight,
            faceRect.left - faceRect.width * 0.05f, leftEye.y
        )
        close()
    }
    drawPath(maskPath, color = Color(0xFFE50914), style = Fill) // Hero Red
    drawPath(maskPath, color = Color(0xFFFFD700), style = Stroke(width = 6f)) // Gold border

    // Eye cutouts
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.85f),
        topLeft = Offset(leftEye.x - eyeHoleWidth / 2f, leftEye.y - eyeHoleHeight / 2f),
        size = Size(eyeHoleWidth, eyeHoleHeight),
        cornerRadius = CornerRadius(eyeHoleWidth / 2f, eyeHoleHeight / 2f)
    )
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.85f),
        topLeft = Offset(rightEye.x - eyeHoleWidth / 2f, rightEye.y - eyeHoleHeight / 2f),
        size = Size(eyeHoleWidth, eyeHoleHeight),
        cornerRadius = CornerRadius(eyeHoleWidth / 2f, eyeHoleHeight / 2f)
    )

    // Star badge on forehead
    val starCenter = Offset(faceRect.center.x, maskTop + maskHeight * 0.15f)
    val starRadius = faceRect.width * 0.06f
    drawCircle(color = Color(0xFFFFD700), radius = starRadius, center = starCenter)
}

// 4. FUNNY GLASSES & MUSTACHE
private fun DrawScope.drawFunnyGlassesFilter(
    faceRect: Rect,
    leftEye: Offset,
    rightEye: Offset,
    nose: Offset
) {
    val glassRadius = faceRect.width * 0.18f
    val frameColor = Color(0xFFFF007F) // Electric Pink

    // Left Frame
    drawCircle(
        color = frameColor,
        radius = glassRadius,
        center = leftEye,
        style = Stroke(width = 12f)
    )
    // Right Frame
    drawCircle(
        color = frameColor,
        radius = glassRadius,
        center = rightEye,
        style = Stroke(width = 12f)
    )
    // Bridge between eyes
    drawLine(
        color = frameColor,
        start = Offset(leftEye.x + glassRadius, leftEye.y),
        end = Offset(rightEye.x - glassRadius, rightEye.y),
        strokeWidth = 10f
    )

    // Mustache under nose
    val mustacheWidth = faceRect.width * 0.42f
    val mustacheHeight = faceRect.height * 0.12f
    val mustacheY = nose.y + faceRect.height * 0.06f

    val mustachePath = Path().apply {
        moveTo(nose.x, mustacheY)
        cubicTo(
            nose.x - mustacheWidth * 0.4f, mustacheY - mustacheHeight * 0.5f,
            nose.x - mustacheWidth * 0.5f, mustacheY + mustacheHeight,
            nose.x - mustacheWidth * 0.55f, mustacheY + mustacheHeight * 0.2f
        )
        cubicTo(
            nose.x - mustacheWidth * 0.4f, mustacheY + mustacheHeight * 0.8f,
            nose.x - mustacheWidth * 0.15f, mustacheY + mustacheHeight * 0.3f,
            nose.x, mustacheY + mustacheHeight * 0.4f
        )
        cubicTo(
            nose.x + mustacheWidth * 0.15f, mustacheY + mustacheHeight * 0.3f,
            nose.x + mustacheWidth * 0.4f, mustacheY + mustacheHeight * 0.8f,
            nose.x + mustacheWidth * 0.55f, mustacheY + mustacheHeight * 0.2f
        )
        cubicTo(
            nose.x + mustacheWidth * 0.5f, mustacheY + mustacheHeight,
            nose.x + mustacheWidth * 0.4f, mustacheY - mustacheHeight * 0.5f,
            nose.x, mustacheY
        )
        close()
    }
    drawPath(mustachePath, color = Color(0xFF1A1A1A), style = Fill)
}

// 5. MAGIC CROWN
private fun DrawScope.drawMagicCrownFilter(faceRect: Rect, bounce: Float) {
    val crownWidth = faceRect.width * 0.85f
    val crownHeight = faceRect.height * 0.45f
    val crownTop = faceRect.top - crownHeight * 0.8f + bounce
    val crownLeft = faceRect.center.x - crownWidth / 2f

    val crownPath = Path().apply {
        moveTo(crownLeft, crownTop + crownHeight)
        lineTo(crownLeft + crownWidth, crownTop + crownHeight)
        lineTo(crownLeft + crownWidth * 0.95f, crownTop + crownHeight * 0.15f)
        lineTo(crownLeft + crownWidth * 0.72f, crownTop + crownHeight * 0.55f)
        lineTo(crownLeft + crownWidth * 0.5f, crownTop) // Middle peak
        lineTo(crownLeft + crownWidth * 0.28f, crownTop + crownHeight * 0.55f)
        lineTo(crownLeft + crownWidth * 0.05f, crownTop + crownHeight * 0.15f)
        close()
    }
    drawPath(crownPath, color = Color(0xFFFFD700), style = Fill) // Gold
    drawPath(crownPath, color = Color(0xFFDAA520), style = Stroke(width = 6f))

    // Jewels on the peaks
    val rubyColor = Color(0xFFFF1493)
    val emeraldColor = Color(0xFF00FF7F)
    val sapphireColor = Color(0xFF1E90FF)

    drawCircle(rubyColor, radius = 12f, center = Offset(crownLeft + crownWidth * 0.5f, crownTop + 6f))
    drawCircle(emeraldColor, radius = 10f, center = Offset(crownLeft + crownWidth * 0.05f, crownTop + crownHeight * 0.15f + 4f))
    drawCircle(sapphireColor, radius = 10f, center = Offset(crownLeft + crownWidth * 0.95f, crownTop + crownHeight * 0.15f + 4f))
}
