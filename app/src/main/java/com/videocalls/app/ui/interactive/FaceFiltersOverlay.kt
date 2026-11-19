package com.videocalls.app.ui.interactive

import android.graphics.Bitmap
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
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
    face: NormalizedFace?,
    cameraFrame: Bitmap? = null
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
            InteractiveEffectType.KALEIDOSCOPE -> drawKaleidoscopeEffect(w, h, medLoop, cameraFrame)
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

// 3. KALEIDOSCOPE: Live camera multi-facet radial reflection (WhatsApp style)
private fun DrawScope.drawKaleidoscopeEffect(
    w: Float,
    h: Float,
    progress: Float,
    cameraFrame: Bitmap?
) {
    val cx = w * 0.5f
    val cy = h * 0.5f

    if (cameraFrame != null && !cameraFrame.isRecycled) {
        val img = cameraFrame.asImageBitmap()
        val imgW = w.toInt()
        val imgH = h.toInt()

        // 1. Top Sector: Vertically Mirrored Reflection (upside down user face)
        val topSector = Path().apply {
            moveTo(cx, cy)
            lineTo(0f, 0f)
            lineTo(w, 0f)
            close()
        }
        clipPath(topSector) {
            scale(1f, -1f, pivot = Offset(cx, cy)) {
                drawImage(
                    image = img,
                    dstOffset = IntOffset(0, 0),
                    dstSize = IntSize(imgW, imgH),
                    filterQuality = FilterQuality.Low
                )
            }
        }

        // 2. Bottom Sector: Vertically Mirrored Reflection (upside down torso)
        val bottomSector = Path().apply {
            moveTo(cx, cy)
            lineTo(0f, h)
            lineTo(w, h)
            close()
        }
        clipPath(bottomSector) {
            scale(1f, -1f, pivot = Offset(cx, cy)) {
                drawImage(
                    image = img,
                    dstOffset = IntOffset(0, 0),
                    dstSize = IntSize(imgW, imgH),
                    filterQuality = FilterQuality.Low
                )
            }
        }

        // 3. Left Sector: Horizontally Mirrored Reflection
        val leftSector = Path().apply {
            moveTo(cx, cy)
            lineTo(0f, 0f)
            lineTo(0f, h)
            close()
        }
        clipPath(leftSector) {
            scale(-1f, 1f, pivot = Offset(cx, cy)) {
                drawImage(
                    image = img,
                    dstOffset = IntOffset(0, 0),
                    dstSize = IntSize(imgW, imgH),
                    filterQuality = FilterQuality.Low
                )
            }
        }

        // 4. Right Sector: Horizontally Mirrored Reflection
        val rightSector = Path().apply {
            moveTo(cx, cy)
            lineTo(w, 0f)
            lineTo(w, h)
            close()
        }
        clipPath(rightSector) {
            scale(-1f, 1f, pivot = Offset(cx, cy)) {
                drawImage(
                    image = img,
                    dstOffset = IntOffset(0, 0),
                    dstSize = IntSize(imgW, imgH),
                    filterQuality = FilterQuality.Low
                )
            }
        }
    }

    // 5. Facet Seams (Crystal refraction diagonal lines from corners to center)
    drawLine(Color.White.copy(alpha = 0.55f), Offset(0f, 0f), Offset(cx, cy), strokeWidth = 3f)
    drawLine(Color.White.copy(alpha = 0.55f), Offset(w, 0f), Offset(cx, cy), strokeWidth = 3f)
    drawLine(Color.White.copy(alpha = 0.55f), Offset(0f, h), Offset(cx, cy), strokeWidth = 3f)
    drawLine(Color.White.copy(alpha = 0.55f), Offset(w, h), Offset(cx, cy), strokeWidth = 3f)

    // 6. Top-Center Prismatic Light Flare (WhatsApp signature beam shining downwards)
    val beamW = w * 0.16f
    val beamPath = Path().apply {
        moveTo(cx - beamW, 0f)
        lineTo(cx + beamW, 0f)
        lineTo(cx + beamW * 2.2f, cy * 0.9f)
        lineTo(cx - beamW * 2.2f, cy * 0.9f)
        close()
    }
    drawPath(
        path = beamPath,
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.85f),
                Color(0x88E0F7FA),
                Color.Transparent
            ),
            startY = 0f,
            endY = cy * 0.9f
        )
    )

    // 7. Subtle prismatic crystal edge glow
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color(0x33B2EBF2), Color(0x55000000)),
            center = Offset(cx, cy),
            radius = w * 0.85f
        )
    )
}

// 4. SPARKLES: Real camera lens starburst cross-flare glints (WhatsApp Kirakira style)
private fun DrawScope.drawTwinklesEffect(
    w: Float,
    h: Float,
    progress: Float,
    face: NormalizedFace?
) {
    val faceRect = if (face != null) {
        Rect(
            left = face.bounds.left * w,
            top = face.bounds.top * h,
            right = face.bounds.right * w,
            bottom = face.bounds.bottom * h
        )
    } else {
        Rect(w * 0.25f, h * 0.25f, w * 0.75f, h * 0.75f)
    }

    val leftEye = face?.leftEye?.let { Offset(it.x * w, it.y * h) }
        ?: Offset(faceRect.left + faceRect.width * 0.35f, faceRect.top + faceRect.height * 0.35f)
    val rightEye = face?.rightEye?.let { Offset(it.x * w, it.y * h) }
        ?: Offset(faceRect.left + faceRect.width * 0.65f, faceRect.top + faceRect.height * 0.35f)

    // 12 authentic sparkle positions (face specular points + dark hair + ambient background)
    val sparklePositions = listOf(
        // Face specular highlights
        Offset(leftEye.x - faceRect.width * 0.10f, leftEye.y - faceRect.height * 0.12f),
        Offset(rightEye.x + faceRect.width * 0.12f, rightEye.y - faceRect.height * 0.15f),
        Offset(rightEye.x + faceRect.width * 0.08f, rightEye.y + faceRect.height * 0.10f),
        Offset(faceRect.center.x - faceRect.width * 0.25f, faceRect.top - faceRect.height * 0.08f),
        Offset(faceRect.center.x + faceRect.width * 0.20f, faceRect.top - faceRect.height * 0.06f),
        Offset(faceRect.center.x, faceRect.top - faceRect.height * 0.16f),
        Offset(faceRect.left + faceRect.width * 0.08f, faceRect.center.y + faceRect.height * 0.05f),
        // Ambient background / hair highlights
        Offset(w * 0.16f, h * 0.24f),
        Offset(w * 0.84f, h * 0.26f),
        Offset(w * 0.24f, h * 0.44f),
        Offset(w * 0.78f, h * 0.46f),
        Offset(w * 0.50f, h * 0.12f)
    )

    val baseSize = (w * 0.055f).coerceIn(24f, 60f)

    for (i in sparklePositions.indices) {
        val pos = sparklePositions[i]
        val phase = (progress + i * 0.14f) % 1.0f

        // Independent twinkle bell-curve envelope
        val envelope = if (phase < 0.20f) {
            0f // dormant
        } else if (phase < 0.48f) {
            (phase - 0.20f) / 0.28f // rising
        } else if (phase < 0.58f) {
            1f // peak gleam
        } else if (phase < 0.86f) {
            ((0.86f - phase) / 0.28f).coerceIn(0f, 1f) // fading
        } else {
            0f // dormant
        }

        if (envelope > 0.02f) {
            val size = baseSize * (0.65f + 0.45f * envelope)
            val rot = 12f + i * 31f + phase * 22f
            drawStarburstFlare(
                center = pos,
                size = size,
                alpha = envelope,
                rotation = rot
            )
        }
    }
}

private fun DrawScope.drawStarburstFlare(
    center: Offset,
    size: Float,
    alpha: Float,
    rotation: Float
) {
    if (alpha <= 0.01f || size <= 1f) return
    val rayLen = size
    val rayThick = size * 0.09f
    val diagLen = size * 0.45f
    val diagThick = size * 0.06f

    rotate(rotation, pivot = center) {
        // 1. Soft radial glow halo
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.95f * alpha),
                    Color(0x6680D8FF).copy(alpha = 0.6f * alpha),
                    Color.Transparent
                ),
                center = center,
                radius = size * 0.40f
            ),
            radius = size * 0.40f,
            center = center
        )

        // 2. Primary 4 Cross Needles (Horizontal and Vertical)
        val vNeedle = Path().apply {
            moveTo(center.x, center.y - rayLen)
            lineTo(center.x + rayThick, center.y)
            lineTo(center.x, center.y + rayLen)
            lineTo(center.x - rayThick, center.y)
            close()
        }
        drawPath(vNeedle, Color.White.copy(alpha = 0.95f * alpha))

        val hNeedle = Path().apply {
            moveTo(center.x - rayLen, center.y)
            lineTo(center.x, center.y - rayThick)
            lineTo(center.x + rayLen, center.y)
            lineTo(center.x, center.y + rayThick)
            close()
        }
        drawPath(hNeedle, Color.White.copy(alpha = 0.95f * alpha))

        // 3. Secondary 4 Diagonal Needles (45 degrees)
        rotate(45f, pivot = center) {
            val diagV = Path().apply {
                moveTo(center.x, center.y - diagLen)
                lineTo(center.x + diagThick, center.y)
                lineTo(center.x, center.y + diagLen)
                lineTo(center.x - diagThick, center.y)
                close()
            }
            drawPath(diagV, Color(0xFFE0F7FA).copy(alpha = 0.85f * alpha))

            val diagH = Path().apply {
                moveTo(center.x - diagLen, center.y)
                lineTo(center.x, center.y - diagThick)
                lineTo(center.x + diagLen, center.y)
                lineTo(center.x, center.y + diagThick)
                close()
            }
            drawPath(diagH, Color(0xFFE0F7FA).copy(alpha = 0.85f * alpha))
        }

        // 4. Central intense white specular core
        drawCircle(
            color = Color.White.copy(alpha = alpha),
            radius = size * 0.08f,
            center = center
        )
    }
}

// 5. DRAMA TEARS: Authentic WhatsApp-style cartoon teardrops dripping down each cheek
private fun DrawScope.drawDramaTears(
    leftEye: Offset,
    rightEye: Offset,
    faceRect: Rect,
    progress: Float,
    rotationZ: Float
) {
    val tearW = (faceRect.width * 0.09f).coerceIn(24f, 75f)
    val tearH = tearW * 1.45f
    val dripDist = faceRect.height * 0.38f

    rotate(rotationZ, pivot = faceRect.center) {
        // Left Eye: 2 staggered dripping cartoon teardrops
        drawEyeTearDrops(
            eyeCenter = leftEye,
            tearW = tearW,
            tearH = tearH,
            dripDist = dripDist,
            progress = progress
        )

        // Right Eye: 2 staggered dripping cartoon teardrops
        drawEyeTearDrops(
            eyeCenter = rightEye,
            tearW = tearW,
            tearH = tearH,
            dripDist = dripDist,
            progress = progress
        )
    }
}

private fun DrawScope.drawEyeTearDrops(
    eyeCenter: Offset,
    tearW: Float,
    tearH: Float,
    dripDist: Float,
    progress: Float
) {
    val startY = eyeCenter.y + tearH * 0.65f

    // 2 staggered drops per eye (matching WhatsApp Image 1)
    for (i in 0..1) {
        val dropPhase = (progress + i * 0.5f) % 1.0f
        val dropY = startY + dropPhase * dripDist

        // Smooth fade-in at eyelid and fade-out towards jaw
        val alpha = if (dropPhase < 0.15f) {
            dropPhase / 0.15f
        } else if (dropPhase > 0.82f) {
            ((1f - dropPhase) / 0.18f).coerceIn(0f, 1f)
        } else {
            1f
        }

        val scale = 0.88f + 0.22f * dropPhase
        drawCartoonTearDrop(
            center = Offset(eyeCenter.x, dropY),
            width = tearW * scale,
            height = tearH * scale,
            alpha = alpha
        )
    }
}

private fun DrawScope.drawCartoonTearDrop(
    center: Offset,
    width: Float,
    height: Float,
    alpha: Float
) {
    if (alpha <= 0.02f || width <= 1f || height <= 1f) return
    val w = width
    val h = height
    val cx = center.x
    val cy = center.y

    val path = Path().apply {
        moveTo(cx, cy - h * 0.5f) // sharp top point
        cubicTo(
            cx + w * 0.18f, cy - h * 0.15f,
            cx + w * 0.50f, cy + h * 0.05f,
            cx + w * 0.50f, cy + h * 0.24f
        )
        // bottom rounded bulb
        arcTo(
            rect = Rect(cx - w * 0.50f, cy - h * 0.02f, cx + w * 0.50f, cy + h * 0.50f),
            startAngleDegrees = 0f,
            sweepAngleDegrees = 180f,
            forceMoveTo = false
        )
        cubicTo(
            cx - w * 0.50f, cy + h * 0.05f,
            cx - w * 0.18f, cy - h * 0.15f,
            cx, cy - h * 0.5f
        )
        close()
    }

    // 1. Darker cyan/blue crisp outline
    drawPath(
        path = path,
        color = Color(0xFF0288D1).copy(alpha = 0.95f * alpha),
        style = Stroke(width = (w * 0.08f).coerceIn(2.5f, 5f))
    )

    // 2. Solid vibrant cyan water drop fill
    drawPath(
        path = path,
        color = Color(0xFF4FC3F7).copy(alpha = 0.95f * alpha)
    )

    // 3. Cute white curved glint highlight at upper-left of bulb
    drawCircle(
        color = Color.White.copy(alpha = 0.92f * alpha),
        radius = w * 0.12f,
        center = Offset(cx - w * 0.18f, cy + h * 0.14f)
    )
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
