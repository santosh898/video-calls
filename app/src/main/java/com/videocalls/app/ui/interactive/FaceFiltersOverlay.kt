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
import androidx.compose.ui.graphics.FilterQuality
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

    // High quality real AR accessory assets (Snapchat-style ears, nose, tongue, glasses, party hat)
    val dogEarsBitmap = ImageBitmap.imageResource(id = R.drawable.filter_dog_ears)
    val dogNoseBitmap = ImageBitmap.imageResource(id = R.drawable.filter_dog_nose)
    val dogTongueBitmap = ImageBitmap.imageResource(id = R.drawable.filter_dog_tongue)
    val sunglassesBitmap = ImageBitmap.imageResource(id = R.drawable.filter_sunglasses)
    val partyHatBitmap = ImageBitmap.imageResource(id = R.drawable.filter_party_hat)
    val catEarsBitmap = ImageBitmap.imageResource(id = R.drawable.filter_cat_ears)

    val infiniteTransition = rememberInfiniteTransition(label = "faceAnim")

    // Fast continuous loop (1.2s) for rain / snow
    val fastLoop by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fastLoop"
    )

    // Medium loop (2.4s) for kaleidoscope
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
        initialValue = -4f,
        targetValue = 4f,
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

        // 1. Fullscreen Atmospheric Effects
        when (filterType) {
            InteractiveEffectType.RAIN -> drawRainEffect(w, h, fastLoop)
            InteractiveEffectType.SNOW -> drawSnowEffect(w, h, fastLoop)
            InteractiveEffectType.KALEIDOSCOPE -> drawKaleidoscopeEffect(w, h, medLoop)
            InteractiveEffectType.TWINKLES -> drawTwinklesEffect(w, h, medLoop, face)
            else -> {}
        }

        // 2. Face-Tracked Real AR Effects
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

            when (filterType) {
                InteractiveEffectType.SHADES -> {
                    // High-res realistic black sunglasses tracking eye centers and head rotation
                    val eyeCenter = Offset((leftEye.x + rightEye.x) * 0.5f, (leftEye.y + rightEye.y) * 0.5f)
                    val glassesW = faceRect.width * 0.96f
                    drawAsset(sunglassesBitmap, eyeCenter, glassesW, rotation = face.headEulerZ)
                }

                InteractiveEffectType.PARTY_HAT -> {
                    // Real festive cone party hat sitting on forehead/head with physics bounce
                    val hatW = faceRect.width * 0.62f
                    val hatH = hatW * (partyHatBitmap.height.toFloat() / partyHatBitmap.width.toFloat())
                    val hatCenter = Offset(faceRect.center.x, faceRect.top - hatH * 0.45f + earBounce)
                    drawAsset(partyHatBitmap, hatCenter, hatW, rotation = face.headEulerZ)
                }

                InteractiveEffectType.DOGGY -> {
                    // Real Snapchat Dog Filter: Floppy ears on head + nose on nose bridge + tongue when mouth open
                    val earsW = faceRect.width * 1.15f
                    val earsH = earsW * (dogEarsBitmap.height.toFloat() / dogEarsBitmap.width.toFloat())
                    val earsCenter = Offset(faceRect.center.x, faceRect.top - earsH * 0.22f + earBounce)
                    drawAsset(dogEarsBitmap, earsCenter, earsW, rotation = face.headEulerZ)

                    val noseW = faceRect.width * 0.32f
                    drawAsset(dogNoseBitmap, nose, noseW, rotation = face.headEulerZ)

                    if (tongueDrop > 0.05f) {
                        val tongueW = faceRect.width * 0.32f * tongueDrop
                        val tongueH = tongueW * (dogTongueBitmap.height.toFloat() / dogTongueBitmap.width.toFloat())
                        val tongueCenter = Offset(nose.x, mouthBottom.y + tongueH * 0.35f)
                        drawAsset(dogTongueBitmap, tongueCenter, tongueW, rotation = face.headEulerZ)
                    }
                }

                InteractiveEffectType.KITTY -> {
                    // Real cute cat ears on head + pink nose + whiskers
                    val earsW = faceRect.width * 1.05f
                    val earsH = earsW * (catEarsBitmap.height.toFloat() / catEarsBitmap.width.toFloat())
                    val earsCenter = Offset(faceRect.center.x, faceRect.top - earsH * 0.28f - earBounce)
                    drawAsset(catEarsBitmap, earsCenter, earsW, rotation = face.headEulerZ)

                    rotate(face.headEulerZ, pivot = faceRect.center) {
                        // Cute pink heart nose
                        drawCircle(Color(0xFFF48FB1), faceRect.width * 0.05f, nose)

                        // Whiskers (3 left, 3 right)
                        for (i in -1..1) {
                            val yOff = i * 14f
                            drawLine(Color.White, Offset(nose.x - faceRect.width * 0.08f, nose.y + yOff), Offset(nose.x - faceRect.width * 0.35f, nose.y + yOff * 1.5f), strokeWidth = 5f)
                            drawLine(Color.White, Offset(nose.x + faceRect.width * 0.08f, nose.y + yOff), Offset(nose.x + faceRect.width * 0.35f, nose.y + yOff * 1.5f), strokeWidth = 5f)
                        }
                    }
                }

                InteractiveEffectType.TEARS -> {
                    drawDramaTears(leftEye, rightEye, faceRect, fastLoop, face.headEulerZ)
                }

                else -> {}
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

// 2. SNOW (ORIGINAL BLIZZARD): Soft drifting snowflakes and crystal vignette
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

// 5. DRAMA TEARS: Bubbly cartoon water fountain cascading from eye corners
private fun DrawScope.drawDramaTears(leftEye: Offset, rightEye: Offset, faceRect: Rect, progress: Float, rotationZ: Float) {
    val dropSpeed = (progress * 180f)
    rotate(rotationZ, pivot = faceRect.center) {
        drawTearStream(Offset(leftEye.x - faceRect.width * 0.08f, leftEye.y + 10f), faceRect.height * 0.6f, dropSpeed)
        drawTearStream(Offset(rightEye.x + faceRect.width * 0.08f, rightEye.y + 10f), faceRect.height * 0.6f, dropSpeed)
    }
}

private fun DrawScope.drawTearStream(start: Offset, maxLen: Float, dropOffset: Float) {
    val path = Path().apply {
        moveTo(start.x, start.y)
        quadraticTo(start.x - 15f, start.y + maxLen * 0.5f, start.x, start.y + maxLen)
        lineTo(start.x + 12f, start.y + maxLen)
        quadraticTo(start.x + 20f, start.y + maxLen * 0.5f, start.x + 8f, start.y)
        close()
    }
    drawPath(path, Color(0x9938BDF8))

    for (d in 0..2) {
        val dy = (dropOffset + d * (maxLen / 3f)) % maxLen
        val tearY = start.y + dy
        drawCircle(Color(0xFF0284C7), 14f, Offset(start.x + 4f, tearY))
        drawCircle(Color.White, 5f, Offset(start.x + 1f, tearY - 4f))
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

// Real AR asset drawing helper
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
                filterQuality = FilterQuality.Medium,
                alpha = alpha
            )
        }
    } else {
        drawImage(
            image = image,
            dstOffset = IntOffset(left, top),
            dstSize = IntSize(w, h),
            filterQuality = FilterQuality.Medium,
            alpha = alpha
        )
    }
}
