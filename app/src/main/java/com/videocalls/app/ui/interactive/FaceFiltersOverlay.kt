package com.videocalls.app.ui.interactive

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.videocalls.app.R
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun FaceFiltersOverlay(
    modifier: Modifier = Modifier,
    filterType: InteractiveEffectType,
    face: NormalizedFace?
) {
    if (filterType == InteractiveEffectType.NONE) return

    // Preload high-quality 3D transparent PNG assets from Microsoft Fluent UI
    val shadesBitmap = ImageBitmap.imageResource(id = R.drawable.effect_shades)
    val partyHatBitmap = ImageBitmap.imageResource(id = R.drawable.effect_party_hat)
    val dropletBitmap = ImageBitmap.imageResource(id = R.drawable.effect_droplet)
    val rainCloudBitmap = ImageBitmap.imageResource(id = R.drawable.effect_rain_cloud)
    val snowflakeBitmap = ImageBitmap.imageResource(id = R.drawable.effect_snowflake)
    val twinklesBitmap = ImageBitmap.imageResource(id = R.drawable.effect_twinkles)
    val starBitmap = ImageBitmap.imageResource(id = R.drawable.effect_glowing_star)
    val puppyBitmap = ImageBitmap.imageResource(id = R.drawable.effect_puppy)
    val kittyBitmap = ImageBitmap.imageResource(id = R.drawable.effect_kitty)

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
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "earBounce"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0 || h <= 0) return@Canvas

        // 1. Fullscreen Atmospheric Effects
        when (filterType) {
            InteractiveEffectType.RAIN -> drawRainEffect(w, h, fastLoop, rainCloudBitmap, dropletBitmap)
            InteractiveEffectType.SNOW -> drawSnowEffect(w, h, fastLoop, snowflakeBitmap)
            InteractiveEffectType.KALEIDOSCOPE -> drawKaleidoscopeEffect(w, h, medLoop, starBitmap)
            InteractiveEffectType.TWINKLES -> drawTwinklesEffect(w, h, medLoop, face, twinklesBitmap, starBitmap)
            else -> {}
        }

        // 2. Face-Tracked AR 3D Effects
        if (face != null) {
            val faceRect = Rect(
                left = face.bounds.left * w,
                top = face.bounds.top * h,
                right = face.bounds.right * w,
                bottom = face.bounds.bottom * h
            )

            val leftEye = face.leftEye?.let { Offset(it.x * w, it.y * h) }
                ?: Offset(faceRect.left + faceRect.width * 0.3f, faceRect.top + faceRect.height * 0.35f)
            val rightEye = face.rightEye?.let { Offset(it.x * w, it.y * h) }
                ?: Offset(faceRect.left + faceRect.width * 0.7f, faceRect.top + faceRect.height * 0.35f)

            when (filterType) {
                InteractiveEffectType.SHADES -> {
                    val eyeCenter = Offset((leftEye.x + rightEye.x) * 0.5f, (leftEye.y + rightEye.y) * 0.5f)
                    val eyeSpan = (rightEye.x - leftEye.x).coerceAtLeast(faceRect.width * 0.45f)
                    val glassW = (faceRect.width * 0.95f).coerceAtLeast(eyeSpan * 2.1f)
                    drawAsset(shadesBitmap, eyeCenter, glassW, rotation = face.headEulerZ)
                }
                InteractiveEffectType.PARTY_HAT -> {
                    val hatW = faceRect.width * 0.75f
                    val hatH = hatW * (partyHatBitmap.height.toFloat() / partyHatBitmap.width.toFloat())
                    val hatCenter = Offset(faceRect.center.x, faceRect.top - hatH * 0.28f + earBounce)
                    drawAsset(partyHatBitmap, hatCenter, hatW, rotation = face.headEulerZ)
                }
                InteractiveEffectType.TEARS -> {
                    drawDramaTears(leftEye, rightEye, faceRect, fastLoop, dropletBitmap, face.headEulerZ)
                }
                InteractiveEffectType.DOGGY -> {
                    val maskW = faceRect.width * 1.15f
                    val maskCenter = Offset(faceRect.center.x, faceRect.top + faceRect.height * 0.4f + earBounce)
                    drawAsset(puppyBitmap, maskCenter, maskW, rotation = face.headEulerZ)
                }
                InteractiveEffectType.KITTY -> {
                    val maskW = faceRect.width * 1.15f
                    val maskCenter = Offset(faceRect.center.x, faceRect.top + faceRect.height * 0.4f - earBounce)
                    drawAsset(kittyBitmap, maskCenter, maskW, rotation = face.headEulerZ)
                }
                else -> {}
            }
        }
    }
}

// 1. RAIN: 3D Floating Rain Cloud + Cascading 3D Droplets
private fun DrawScope.drawRainEffect(
    w: Float,
    h: Float,
    progress: Float,
    cloudBitmap: ImageBitmap,
    dropletBitmap: ImageBitmap
) {
    // 3D Rain cloud bobbing at top center
    val cloudW = w * 0.5f
    val cloudCenter = Offset(w * 0.5f + sin(progress * 2 * Math.PI.toFloat()) * 20f, h * 0.12f)
    drawAsset(cloudBitmap, cloudCenter, cloudW)

    // Falling 3D raindrops across screen
    val numDrops = 30
    for (i in 0 until numDrops) {
        val seed = i * 97
        val x = (seed * 31) % w.toInt()
        val speedMultiplier = 0.9f + (i % 5) * 0.25f
        val y = ((progress * speedMultiplier + (i * 0.033f)) % 1f) * (h + 80f) - 40f
        val dropSize = 22f + (i % 4) * 5f

        drawAsset(dropletBitmap, Offset(x.toFloat(), y), dropSize, alpha = 0.85f)
    }
}

// 2. SNOW: 3D Snowflakes drifting, rotating, and swirling
private fun DrawScope.drawSnowEffect(
    w: Float,
    h: Float,
    progress: Float,
    snowflakeBitmap: ImageBitmap
) {
    val numFlakes = 26
    for (i in 0 until numFlakes) {
        val seed = i * 73
        val baseX = (seed * 37) % w.toInt()
        val sway = sin((progress * 3f + i).toDouble()).toFloat() * 25f
        val x = (baseX + sway).coerceIn(0f, w)
        val speed = 0.65f + (i % 4) * 0.3f
        val y = ((progress * speed + (i * 0.038f)) % 1f) * (h + 60f) - 30f
        val flakeSize = 28f + (i % 5) * 8f
        val flakeRot = (progress * 360f * (if (i % 2 == 0) 1f else -1f) + i * 45f)

        drawAsset(snowflakeBitmap, Offset(x, y), flakeSize, alpha = 0.9f, rotation = flakeRot)
    }

    // Frosty edge vignette
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color(0x33B2EBF2)),
            center = Offset(w * 0.5f, h * 0.5f),
            radius = w * 0.8f
        )
    )
}

// 3. KALEIDOSCOPE: Prismatic rotating kaleidoscope with 3D glowing stars
private fun DrawScope.drawKaleidoscopeEffect(
    w: Float,
    h: Float,
    progress: Float,
    starBitmap: ImageBitmap
) {
    val center = Offset(w * 0.5f, h * 0.5f)
    val maxRadius = w * 0.7f
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
            drawPath(path, Color.White.copy(alpha = 0.35f), style = Stroke(width = 2f))
        }
    }

    // Rotating 3D stars around circle
    val starDist = w * 0.38f
    for (s in 0..5) {
        val sAngle = (rot + s * 60f) * (Math.PI / 180.0)
        val sx = center.x + cos(sAngle).toFloat() * starDist
        val sy = center.y + sin(sAngle).toFloat() * starDist
        drawAsset(starBitmap, Offset(sx, sy), 36f, rotation = rot * 2f)
    }
}

// 4. TWINKLES: Orbiting 3D magic sparkles and stars
private fun DrawScope.drawTwinklesEffect(
    w: Float,
    h: Float,
    progress: Float,
    face: NormalizedFace?,
    twinklesBitmap: ImageBitmap,
    starBitmap: ImageBitmap
) {
    val center = if (face != null) {
        Offset(face.bounds.centerX() * w, face.bounds.centerY() * h)
    } else {
        Offset(w * 0.5f, h * 0.42f)
    }

    val numItems = 8
    for (i in 0 until numItems) {
        val angle = (progress * 2 * Math.PI.toFloat() + (i * 2 * Math.PI.toFloat() / numItems))
        val dist = w * (0.32f + 0.06f * sin((progress * 4f + i).toDouble()).toFloat())
        val sx = center.x + cos(angle.toDouble()).toFloat() * dist
        val sy = center.y + sin(angle.toDouble()).toFloat() * (dist * 0.85f)

        val size = w * 0.09f * (0.8f + 0.25f * sin((progress * 6f + i).toDouble()).toFloat())
        val bitmap = if (i % 2 == 0) twinklesBitmap else starBitmap
        drawAsset(bitmap, Offset(sx, sy), size, rotation = progress * 180f + i * 30f)
    }
}

// 5. DRAMA TEARS: Streams of cascading 3D droplets from eye corners
private fun DrawScope.drawDramaTears(
    leftEye: Offset,
    rightEye: Offset,
    faceRect: Rect,
    progress: Float,
    dropletBitmap: ImageBitmap,
    rotationZ: Float
) {
    val streamLen = faceRect.height * 0.65f
    val dropProgress = (progress * 1.5f) % 1f

    rotate(rotationZ, pivot = faceRect.center) {
        for (side in listOf(-1, 1)) {
            val eyeX = if (side == -1) leftEye.x - faceRect.width * 0.05f else rightEye.x + faceRect.width * 0.05f
            val startY = leftEye.y + 10f

            for (d in 0..3) {
                val p = ((dropProgress + d * 0.25f) % 1f)
                val dy = startY + p * streamLen
                val dx = eyeX + side * sin(p * 3.14f) * 12f
                val dropSize = 24f + p * 16f
                drawAsset(dropletBitmap, Offset(dx, dy), dropSize, alpha = 0.9f)
            }
        }
    }
}

// Generic 3D asset draw helper
private fun DrawScope.drawAsset(
    image: ImageBitmap,
    center: Offset,
    width: Float,
    height: Float = width * (image.height.toFloat() / image.width.toFloat()),
    alpha: Float = 1.0f,
    rotation: Float = 0f
) {
    if (width <= 0f || height <= 0f) return
    val left = (center.x - width / 2f).toInt()
    val top = (center.y - height / 2f).toInt()
    val w = width.toInt().coerceAtLeast(1)
    val h = height.toInt().coerceAtLeast(1)

    if (rotation != 0f) {
        rotate(rotation, pivot = center) {
            drawImage(
                image = image,
                dstOffset = IntOffset(left, top),
                dstSize = IntSize(w, h),
                alpha = alpha
            )
        }
    } else {
        drawImage(
            image = image,
            dstOffset = IntOffset(left, top),
            dstSize = IntSize(w, h),
            alpha = alpha
        )
    }
}
