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
import androidx.compose.ui.graphics.Brush
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
    filterType: InteractiveEffectType,
    face: NormalizedFace?
) {
    if (filterType == InteractiveEffectType.NONE) return

    val infiniteTransition = rememberInfiniteTransition(label = "faceAnim")

    // Fast continuous loop (1.2s) for rain / snow / sparkles
    val fastLoop by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fastLoop"
    )

    // Medium loop (2.4s) for kaleidoscope / hat bounce
    val medLoop by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "medLoop"
    )

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
        targetValue = if (face?.isMouthOpen == true) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tongueDrop"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0 || h <= 0) return@Canvas

        // 1. Fullscreen Atmospheric Effects (Work even if face is momentarily obscured)
        when (filterType) {
            InteractiveEffectType.RAIN -> drawRainEffect(w, h, fastLoop)
            InteractiveEffectType.SNOW -> drawSnowEffect(w, h, fastLoop)
            InteractiveEffectType.KALEIDOSCOPE -> drawKaleidoscopeEffect(w, h, medLoop)
            InteractiveEffectType.TWINKLES -> drawTwinklesEffect(w, h, medLoop, face)
            else -> {}
        }

        // 2. Face-Tracked AR Effects (Require detected face)
        if (face != null) {
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
                    InteractiveEffectType.SHADES -> drawCoolShades(leftEye, rightEye, faceRect)
                    InteractiveEffectType.PARTY_HAT -> drawPartyHat(faceRect, earBounce)
                    InteractiveEffectType.TEARS -> drawDramaTears(leftEye, rightEye, faceRect, fastLoop)
                    InteractiveEffectType.DOGGY -> drawDoggyFilter(faceRect, nose, mouthBottom, earBounce, tongueDrop)
                    InteractiveEffectType.KITTY -> drawKittyFilter(faceRect, nose, earBounce)
                    else -> {}
                }
            }
        }
    }
}

// 1. RAIN: Fast streaks and translucent screen droplets
private fun DrawScope.drawRainEffect(w: Float, h: Float, progress: Float) {
    val numDrops = 40
    for (i in 0 until numDrops) {
        val seed = i * 97
        val x = (seed * 13) % w.toInt()
        val speedMultiplier = 1f + (i % 4) * 0.3f
        val y = ((progress * speedMultiplier + (i * 0.025f)) % 1f) * (h + 80f) - 40f
        val dropLen = 35f * speedMultiplier

        drawLine(
            color = Color(0x99B0BEC5),
            start = Offset(x.toFloat(), y),
            end = Offset(x.toFloat() - 4f, y + dropLen),
            strokeWidth = 3f
        )
    }

    // A few sliding glass droplets
    val droplets = listOf(
        Offset(w * 0.22f, ((progress * 0.3f + 0.1f) % 1f) * h),
        Offset(w * 0.55f, ((progress * 0.25f + 0.4f) % 1f) * h),
        Offset(w * 0.78f, ((progress * 0.35f + 0.7f) % 1f) * h)
    )
    droplets.forEach { pos ->
        drawCircle(Color(0x88CFD8DC), 12f, pos)
        drawCircle(Color.White.copy(alpha = 0.8f), 3f, Offset(pos.x - 3f, pos.y - 3f))
    }
}

// 2. SNOW: Soft drifting snowflakes and crystal vignette
private fun DrawScope.drawSnowEffect(w: Float, h: Float, progress: Float) {
    val numFlakes = 36
    for (i in 0 until numFlakes) {
        val seed = i * 73
        val baseX = (seed * 19) % w.toInt()
        val sway = sin(((progress * 4f + i).toDouble())).toFloat() * 20f
        val x = (baseX + sway).coerceIn(0f, w)
        val speed = 0.8f + (i % 3) * 0.4f
        val y = ((progress * speed + (i * 0.028f)) % 1f) * (h + 40f) - 20f
        val flakeRad = 6f + (i % 5) * 3f

        drawCircle(Color.White.copy(alpha = 0.85f), flakeRad, Offset(x, y))
        drawCircle(Color(0xFFE0F7FA), flakeRad * 0.5f, Offset(x, y))
    }

    // Frosty corner vignette
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color(0x22B2EBF2)),
            center = Offset(w * 0.5f, h * 0.5f),
            radius = w * 0.75f
        )
    )
}

// 3. KALEIDOSCOPE: Mesmerizing rainbow faceted prismatic crystal symmetry
private fun DrawScope.drawKaleidoscopeEffect(w: Float, h: Float, progress: Float) {
    val center = Offset(w * 0.5f, h * 0.5f)
    val maxRadius = w * 0.65f
    val numSegments = 12
    val rot = progress * 360f

    val spectrumColors = listOf(
        Color(0x33FF0055),
        Color(0x33FF7700),
        Color(0x33FFDD00),
        Color(0x3300FF66),
        Color(0x3300DDFF),
        Color(0x339900FF)
    )

    rotate(rot, pivot = center) {
        for (i in 0 until numSegments) {
            val angle1 = (i * (360f / numSegments)) * (Math.PI / 180.0)
            val angle2 = ((i + 1) * (360f / numSegments)) * (Math.PI / 180.0)

            val p1 = Offset(center.x + cos(angle1).toFloat() * maxRadius, center.y + sin(angle1).toFloat() * maxRadius)
            val p2 = Offset(center.x + cos(angle2).toFloat() * maxRadius, center.y + sin(angle2).toFloat() * maxRadius)

            val path = Path().apply {
                moveTo(center.x, center.y)
                lineTo(p1.x, p1.y)
                lineTo(p2.x, p2.y)
                close()
            }
            drawPath(path, spectrumColors[i % spectrumColors.size])
            drawPath(path, Color.White.copy(alpha = 0.4f), style = Stroke(width = 2f))
        }
    }
}

// 4. TWINKLES: Orbiting sparkling golden & cyan magical stars
private fun DrawScope.drawTwinklesEffect(w: Float, h: Float, progress: Float, face: NormalizedFace?) {
    val center = if (face != null) {
        Offset(face.bounds.centerX() * w, face.bounds.centerY() * h)
    } else {
        Offset(w * 0.5f, h * 0.4f)
    }

    val starColors = listOf(Color(0xFFFFD700), Color(0xFFFF69B4), Color(0xFF00FFFF), Color(0xFF76FF03))
    val numStars = 8

    for (i in 0 until numStars) {
        val angle = (progress * 2 * Math.PI.toFloat() + (i * 2 * Math.PI.toFloat() / numStars))
        val dist = w * (0.28f + 0.08f * sin((progress * 4f + i).toDouble()).toFloat())
        val sx = center.x + cos(angle.toDouble()).toFloat() * dist
        val sy = center.y + sin(angle.toDouble()).toFloat() * (dist * 0.8f)

        val starSize = w * 0.035f * (0.7f + 0.3f * sin((progress * 6f + i).toDouble()).toFloat())
        drawSparkleStar(Offset(sx, sy), starSize, starColors[i % starColors.size])
    }
}

// 5. SHADES: Cool black aviator sunglasses with white diagonal sheen reflection
private fun DrawScope.drawCoolShades(leftEye: Offset, rightEye: Offset, faceRect: Rect) {
    val glassesCenter = Offset((leftEye.x + rightEye.x) * 0.5f, (leftEye.y + rightEye.y) * 0.5f)
    val span = (rightEye.x - leftEye.x).coerceAtLeast(faceRect.width * 0.4f)
    val glassWidth = span * 0.72f
    val glassHeight = glassWidth * 0.65f

    // Nose bridge
    drawLine(
        color = Color(0xFF1E293B),
        start = Offset(glassesCenter.x - glassWidth * 0.35f, glassesCenter.y),
        end = Offset(glassesCenter.x + glassWidth * 0.35f, glassesCenter.y),
        strokeWidth = 10f
    )

    // Left Lens
    val leftLensCenter = Offset(glassesCenter.x - glassWidth * 0.6f, glassesCenter.y)
    drawShadesLens(leftLensCenter, glassWidth, glassHeight)

    // Right Lens
    val rightLensCenter = Offset(glassesCenter.x + glassWidth * 0.6f, glassesCenter.y)
    drawShadesLens(rightLensCenter, glassWidth, glassHeight)
}

private fun DrawScope.drawShadesLens(center: Offset, w: Float, h: Float) {
    // Outer black frame
    drawRoundRect(
        color = Color(0xFF0F172A),
        topLeft = Offset(center.x - w * 0.5f, center.y - h * 0.5f),
        size = Size(w, h),
        cornerRadius = CornerRadius(16f, 16f)
    )
    // Dark glass
    drawRoundRect(
        color = Color(0xEE1E1B4B),
        topLeft = Offset(center.x - w * 0.44f, center.y - h * 0.42f),
        size = Size(w * 0.88f, h * 0.84f),
        cornerRadius = CornerRadius(12f, 12f)
    )
    // Diagonal white sheen reflection
    drawLine(
        color = Color.White.copy(alpha = 0.6f),
        start = Offset(center.x - w * 0.25f, center.y - h * 0.35f),
        end = Offset(center.x + w * 0.2f, center.y + h * 0.35f),
        strokeWidth = 6f
    )
}

// 6. PARTY HAT: Striped cone hat with pom-pom on forehead/head top
private fun DrawScope.drawPartyHat(faceRect: Rect, bounce: Float) {
    val hatBaseCenter = Offset(faceRect.center.x, faceRect.top + faceRect.height * 0.05f)
    val hatWidth = faceRect.width * 0.55f
    val hatHeight = faceRect.width * 0.75f

    val hatPath = Path().apply {
        moveTo(hatBaseCenter.x, hatBaseCenter.y - hatHeight)
        lineTo(hatBaseCenter.x + hatWidth * 0.5f, hatBaseCenter.y)
        lineTo(hatBaseCenter.x - hatWidth * 0.5f, hatBaseCenter.y)
        close()
    }
    // Cone body (vibrant yellow/orange)
    drawPath(hatPath, Color(0xFFFBBF24))

    // Diagonal festive stripes
    val stripe1 = Path().apply {
        moveTo(hatBaseCenter.x - hatWidth * 0.2f, hatBaseCenter.y - hatHeight * 0.4f)
        lineTo(hatBaseCenter.x + hatWidth * 0.3f, hatBaseCenter.y - hatHeight * 0.25f)
        lineTo(hatBaseCenter.x + hatWidth * 0.4f, hatBaseCenter.y - hatHeight * 0.15f)
        lineTo(hatBaseCenter.x - hatWidth * 0.3f, hatBaseCenter.y - hatHeight * 0.3f)
        close()
    }
    drawPath(stripe1, Color(0xFFEC4899))

    // Pom-pom on hat tip
    val tip = Offset(hatBaseCenter.x, hatBaseCenter.y - hatHeight + bounce * 2f)
    drawCircle(Color.White, hatWidth * 0.14f, tip)
    drawCircle(Color(0xFF38BDF8), hatWidth * 0.1f, tip)

    // Zig-zag brim
    drawRoundRect(
        color = Color(0xFF38BDF8),
        topLeft = Offset(hatBaseCenter.x - hatWidth * 0.52f, hatBaseCenter.y - 6f),
        size = Size(hatWidth * 1.04f, 14f),
        cornerRadius = CornerRadius(6f, 6f)
    )
}

// 7. DRAMA TEARS: Bubbly cartoon water fountain cascading from eye corners
private fun DrawScope.drawDramaTears(leftEye: Offset, rightEye: Offset, faceRect: Rect, progress: Float) {
    val dropSpeed = (progress * 180f)

    // Left Eye Tears
    drawTearStream(Offset(leftEye.x - faceRect.width * 0.08f, leftEye.y + 10f), faceRect.height * 0.6f, dropSpeed)

    // Right Eye Tears
    drawTearStream(Offset(rightEye.x + faceRect.width * 0.08f, rightEye.y + 10f), faceRect.height * 0.6f, dropSpeed)
}

private fun DrawScope.drawTearStream(start: Offset, maxLen: Float, dropOffset: Float) {
    // Streaming blue stream
    val path = Path().apply {
        moveTo(start.x, start.y)
        quadraticTo(start.x - 15f, start.y + maxLen * 0.5f, start.x, start.y + maxLen)
        lineTo(start.x + 12f, start.y + maxLen)
        quadraticTo(start.x + 20f, start.y + maxLen * 0.5f, start.x + 8f, start.y)
        close()
    }
    drawPath(path, Color(0x9938BDF8))

    // Big tear drops cascading down
    for (d in 0..2) {
        val dy = (dropOffset + d * (maxLen / 3f)) % maxLen
        val tearY = start.y + dy
        drawCircle(Color(0xFF0284C7), 14f, Offset(start.x + 4f, tearY))
        drawCircle(Color.White, 5f, Offset(start.x + 1f, tearY - 4f))
    }
}

// 8. DOGGY: Floppy puppy ears, black nose, playful tongue
private fun DrawScope.drawDoggyFilter(faceRect: Rect, nose: Offset, mouthBottom: Offset, earBounce: Float, tongueDrop: Float) {
    val earW = faceRect.width * 0.32f
    val earH = faceRect.height * 0.6f

    // Left Floppy Ear
    drawFloppyEar(
        topLeft = Offset(faceRect.left - earW * 0.25f, faceRect.top - earH * 0.15f + earBounce),
        size = Size(earW, earH),
        tiltAngle = -18f
    )
    // Right Floppy Ear
    drawFloppyEar(
        topLeft = Offset(faceRect.right - earW * 0.75f, faceRect.top - earH * 0.15f - earBounce),
        size = Size(earW, earH),
        tiltAngle = 18f
    )

    // Puppy Dog Nose
    val noseW = faceRect.width * 0.24f
    val noseH = noseW * 0.65f
    drawRoundRect(
        color = Color(0xFF1E1B18),
        topLeft = Offset(nose.x - noseW * 0.5f, nose.y - noseH * 0.4f),
        size = Size(noseW, noseH),
        cornerRadius = CornerRadius(noseH * 0.5f, noseH * 0.5f)
    )
    // Nose highlight
    drawOval(
        color = Color.White.copy(alpha = 0.7f),
        topLeft = Offset(nose.x - noseW * 0.25f, nose.y - noseH * 0.3f),
        size = Size(noseW * 0.35f, noseH * 0.35f)
    )

    // Tongue (drops when mouth open)
    val tongueW = faceRect.width * 0.22f
    val maxTongueH = faceRect.height * 0.32f
    val curTongueH = maxTongueH * (0.3f + 0.7f * tongueDrop)
    if (tongueDrop > 0.05f) {
        val tTop = mouthBottom.y - curTongueH * 0.15f
        drawRoundRect(
            color = Color(0xFFFF4081),
            topLeft = Offset(nose.x - tongueW * 0.5f, tTop),
            size = Size(tongueW, curTongueH),
            cornerRadius = CornerRadius(tongueW * 0.45f, tongueW * 0.45f)
        )
        drawLine(
            color = Color(0xFFC2185B),
            start = Offset(nose.x, tTop + 4f),
            end = Offset(nose.x, tTop + curTongueH * 0.75f),
            strokeWidth = 3f
        )
    }
}

private fun DrawScope.drawFloppyEar(topLeft: Offset, size: Size, tiltAngle: Float) {
    rotate(tiltAngle, pivot = Offset(topLeft.x + size.width * 0.5f, topLeft.y)) {
        drawRoundRect(
            color = Color(0xFF795548),
            topLeft = topLeft,
            size = size,
            cornerRadius = CornerRadius(size.width * 0.5f, size.width * 0.5f)
        )
        // Inner pink ear
        drawRoundRect(
            color = Color(0xFFFFAB91),
            topLeft = Offset(topLeft.x + size.width * 0.18f, topLeft.y + size.height * 0.2f),
            size = Size(size.width * 0.64f, size.height * 0.65f),
            cornerRadius = CornerRadius(size.width * 0.35f, size.width * 0.35f)
        )
    }
}

// 9. KITTY: Cute pink-inner cat ears, nose and whiskers
private fun DrawScope.drawKittyFilter(faceRect: Rect, nose: Offset, bounce: Float) {
    val earW = faceRect.width * 0.3f
    val earH = faceRect.width * 0.35f

    // Left Cat Ear (Triangle)
    val lTop = Offset(faceRect.left + faceRect.width * 0.12f, faceRect.top - earH * 0.6f + bounce)
    val lPath = Path().apply {
        moveTo(lTop.x, lTop.y)
        lineTo(faceRect.left - earW * 0.1f, faceRect.top + earH * 0.3f)
        lineTo(faceRect.left + faceRect.width * 0.3f, faceRect.top + earH * 0.1f)
        close()
    }
    drawPath(lPath, Color(0xFF424242))

    // Right Cat Ear (Triangle)
    val rTop = Offset(faceRect.right - faceRect.width * 0.12f, faceRect.top - earH * 0.6f - bounce)
    val rPath = Path().apply {
        moveTo(rTop.x, rTop.y)
        lineTo(faceRect.right - faceRect.width * 0.3f, faceRect.top + earH * 0.1f)
        lineTo(faceRect.right + earW * 0.1f, faceRect.top + earH * 0.3f)
        close()
    }
    drawPath(rPath, Color(0xFF424242))

    // Pink heart nose
    drawCircle(Color(0xFFF48FB1), faceRect.width * 0.05f, nose)

    // Whiskers (3 left, 3 right)
    for (i in -1..1) {
        val yOff = i * 14f
        drawLine(Color.White, Offset(nose.x - faceRect.width * 0.08f, nose.y + yOff), Offset(nose.x - faceRect.width * 0.35f, nose.y + yOff * 1.5f), strokeWidth = 5f)
        drawLine(Color.White, Offset(nose.x + faceRect.width * 0.08f, nose.y + yOff), Offset(nose.x + faceRect.width * 0.35f, nose.y + yOff * 1.5f), strokeWidth = 5f)
    }
}

private fun DrawScope.drawSparkleStar(center: Offset, size: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - size)
        lineTo(center.x + size * 0.25f, center.y - size * 0.25f)
        lineTo(center.x + size, center.y)
        lineTo(center.x + size * 0.25f, center.y + size * 0.25f)
        lineTo(center.x, center.y + size)
        lineTo(center.x - size * 0.25f, center.y + size * 0.25f)
        lineTo(center.x - size, center.y)
        lineTo(center.x - size * 0.25f, center.y - size * 0.25f)
        close()
    }
    drawPath(path, color)
}
