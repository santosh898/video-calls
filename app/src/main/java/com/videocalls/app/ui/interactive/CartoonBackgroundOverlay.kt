package com.videocalls.app.ui.interactive

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
fun CartoonBackgroundOverlay(
    modifier: Modifier = Modifier,
    backgroundType: InteractiveBackgroundType
) {
    if (backgroundType == InteractiveBackgroundType.NONE) return

    val infiniteTransition = rememberInfiniteTransition(label = "cartoonBg")

    // Slow continuous motion progress 0f..1f (6 seconds loop)
    val floatAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "floatAnim"
    )

    // Gentle bounce progress -1f..1f (1.5 seconds)
    val bounceAnim by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounceAnim"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0 || h <= 0) return@Canvas

        when (backgroundType) {
            InteractiveBackgroundType.SPACE -> drawSpaceBackground(w, h, floatAnim, bounceAnim)
            InteractiveBackgroundType.UNDERWATER -> drawUnderwaterBackground(w, h, floatAnim, bounceAnim)
            InteractiveBackgroundType.JUNGLE -> drawJungleBackground(w, h, floatAnim, bounceAnim)
            InteractiveBackgroundType.CANDY_LAND -> drawCandyLandBackground(w, h, floatAnim, bounceAnim)
            InteractiveBackgroundType.RAINBOW -> drawRainbowBackground(w, h, floatAnim, bounceAnim)
            InteractiveBackgroundType.DINO -> drawDinoBackground(w, h, floatAnim, bounceAnim)
            InteractiveBackgroundType.NONE -> {}
        }
    }
}

// 1. SPACE GALAXY: Orbiting cartoon rocket, Saturn with rings, smiling moon, twinkling stars
private fun DrawScope.drawSpaceBackground(w: Float, h: Float, floatProgress: Float, bounce: Float) {
    // Top-right smiling cartoon crescent moon
    val moonCenter = Offset(w * 0.85f, h * 0.12f + bounce * 6f)
    drawCircle(
        color = Color(0xFFFFD54F),
        radius = w * 0.08f,
        center = moonCenter
    )
    drawCircle(
        color = Color(0x33000000),
        radius = w * 0.07f,
        center = Offset(moonCenter.x - w * 0.025f, moonCenter.y - w * 0.015f)
    )

    // Top-left Saturn with ring
    val saturnCenter = Offset(w * 0.15f, h * 0.13f - bounce * 5f)
    drawCircle(
        color = Color(0xFFFFB74D),
        radius = w * 0.065f,
        center = saturnCenter
    )
    // Saturn ring
    rotate(25f, pivot = saturnCenter) {
        drawOval(
            color = Color(0xFFFFE082),
            topLeft = Offset(saturnCenter.x - w * 0.11f, saturnCenter.y - w * 0.025f),
            size = Size(w * 0.22f, w * 0.05f),
            style = Stroke(width = 8f)
        )
    }

    // Twinkling cartoon 4-pointed stars
    val starPositions = listOf(
        Offset(w * 0.35f, h * 0.08f),
        Offset(w * 0.65f, h * 0.15f),
        Offset(w * 0.12f, h * 0.35f),
        Offset(w * 0.88f, h * 0.42f),
        Offset(w * 0.25f, h * 0.75f),
        Offset(w * 0.8f, h * 0.78f)
    )
    starPositions.forEachIndexed { i, pos ->
        val scale = 0.7f + 0.3f * sin((floatProgress * 2 * Math.PI.toFloat() + i).toDouble()).toFloat()
        drawStar(pos, w * 0.025f * scale, Color(0xFFFFF59D))
    }

    // Orbiting cartoon red rocket ship
    val rocketAngle = floatProgress * 2 * Math.PI.toFloat()
    val rx = w * 0.5f + cos(rocketAngle.toDouble()).toFloat() * (w * 0.42f)
    val ry = h * 0.48f + sin(rocketAngle.toDouble()).toFloat() * (h * 0.35f)
    val rocketPivot = Offset(rx, ry)
    val rocketRot = (rocketAngle * 180f / Math.PI.toFloat()) + 90f

    rotate(rocketRot, pivot = rocketPivot) {
        // Rocket fuselage
        val path = Path().apply {
            moveTo(rx, ry - w * 0.06f)
            lineTo(rx + w * 0.025f, ry + w * 0.035f)
            lineTo(rx - w * 0.025f, ry + w * 0.035f)
            close()
        }
        drawPath(path, Color(0xFFEF4444))

        // White window
        drawCircle(Color.White, w * 0.012f, Offset(rx, ry - w * 0.01f))
        drawCircle(Color(0xFF38BDF8), w * 0.009f, Offset(rx, ry - w * 0.01f))

        // Fiery flame booster
        val flameLen = w * (0.025f + 0.012f * (bounce + 1f) * 0.5f)
        val flame = Path().apply {
            moveTo(rx - w * 0.015f, ry + w * 0.035f)
            lineTo(rx, ry + w * 0.035f + flameLen)
            lineTo(rx + w * 0.015f, ry + w * 0.035f)
            close()
        }
        drawPath(flame, Color(0xFFFBBF24))
    }
}

// 2. UNDERWATER: Swimming clownfish, wavy kelp reefs, cartoon jellyfish, floating air bubbles
private fun DrawScope.drawUnderwaterBackground(w: Float, h: Float, floatProgress: Float, bounce: Float) {
    // Wavy coral kelp at bottom
    for (col in 0..6) {
        val kx = w * (col * 0.16f + 0.05f)
        val sway = sin((floatProgress * 2 * Math.PI.toFloat() + col).toDouble()).toFloat() * 18f
        val kelpColor = if (col % 2 == 0) Color(0xFF10B981) else Color(0xFF059669)

        val path = Path().apply {
            moveTo(kx - w * 0.03f, h)
            quadraticTo(kx + sway, h - h * 0.12f, kx + sway * 1.3f, h - h * 0.22f)
            quadraticTo(kx + sway * 0.5f, h - h * 0.12f, kx + w * 0.03f, h)
            close()
        }
        drawPath(path, kelpColor.copy(alpha = 0.85f))
    }

    // Swimming cartoon clownfish (orange with white stripes) moving across screen
    val fishX = (floatProgress * (w + w * 0.3f) - w * 0.15f)
    val fishY = h * 0.28f + bounce * 15f
    val fishLen = w * 0.13f
    val fishHeight = w * 0.08f

    // Fish body
    drawOval(
        color = Color(0xFFFF6D00),
        topLeft = Offset(fishX - fishLen * 0.5f, fishY - fishHeight * 0.5f),
        size = Size(fishLen, fishHeight)
    )
    // White stripe
    drawOval(
        color = Color.White,
        topLeft = Offset(fishX - fishLen * 0.1f, fishY - fishHeight * 0.48f),
        size = Size(fishLen * 0.25f, fishHeight * 0.96f)
    )
    // Fish tail
    val tail = Path().apply {
        moveTo(fishX - fishLen * 0.5f, fishY)
        lineTo(fishX - fishLen * 0.75f, fishY - fishHeight * 0.45f)
        lineTo(fishX - fishLen * 0.75f, fishY + fishHeight * 0.45f)
        close()
    }
    drawPath(tail, Color(0xFFFF6D00))

    // Eye
    drawCircle(Color.White, fishHeight * 0.2f, Offset(fishX + fishLen * 0.3f, fishY - fishHeight * 0.1f))
    drawCircle(Color.Black, fishHeight * 0.1f, Offset(fishX + fishLen * 0.34f, fishY - fishHeight * 0.1f))

    // Gentle translucent jellyfish in upper right
    val jx = w * 0.85f
    val jy = h * 0.22f - bounce * 18f
    drawArc(
        color = Color(0xCCF472B6),
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(jx - w * 0.07f, jy - w * 0.06f),
        size = Size(w * 0.14f, w * 0.12f)
    )
    // Jellyfish tentacles
    for (t in -2..2) {
        val tx = jx + t * w * 0.025f
        drawLine(
            color = Color(0x99F472B6),
            start = Offset(tx, jy),
            end = Offset(tx + sin((floatProgress * 6f + t).toDouble()).toFloat() * 12f, jy + w * 0.09f),
            strokeWidth = 5f
        )
    }

    // Floating bubbles
    val bubbleOffsets = listOf(
        Offset(w * 0.08f, (h * (1f - floatProgress * 1.2f)) % h),
        Offset(w * 0.92f, (h * (1f - (floatProgress + 0.5f) * 1.2f)) % h)
    )
    bubbleOffsets.forEach { pos ->
        drawCircle(Color(0x77E0F2FE), w * 0.03f, pos)
        drawCircle(Color.White.copy(alpha = 0.8f), w * 0.009f, Offset(pos.x - w * 0.01f, pos.y - w * 0.01f))
    }
}

// 3. JUNGLE SAFARI: Tropical palm leaves, dangling vines, smiling cartoon monkey and toucan
private fun DrawScope.drawJungleBackground(w: Float, h: Float, floatProgress: Float, bounce: Float) {
    // Dangling vines from top
    val vineColors = listOf(Color(0xFF2E7D32), Color(0xFF388E3C), Color(0xFF1B5E20))
    for (i in 0..4) {
        val vx = w * (0.15f + i * 0.18f)
        val sway = sin((floatProgress * 3f + i).toDouble()).toFloat() * 15f
        val vineLen = h * (0.15f + (i % 3) * 0.05f)

        val vPath = Path().apply {
            moveTo(vx, 0f)
            quadraticTo(vx + sway, vineLen * 0.5f, vx + sway * 1.2f, vineLen)
        }
        drawPath(vPath, vineColors[i % vineColors.size], style = Stroke(width = 10f))

        // Cute leaf at tip
        drawCircle(Color(0xFF4CAF50), w * 0.025f, Offset(vx + sway * 1.2f, vineLen))
    }

    // Peeking cartoon monkey top left hanging on vine
    val mx = w * 0.18f
    val my = h * 0.16f + bounce * 8f
    // Monkey head
    drawCircle(Color(0xFF8D6E63), w * 0.065f, Offset(mx, my))
    // Ears
    drawCircle(Color(0xFF8D6E63), w * 0.025f, Offset(mx - w * 0.065f, my))
    drawCircle(Color(0xFFFFCCBC), w * 0.015f, Offset(mx - w * 0.065f, my))
    drawCircle(Color(0xFF8D6E63), w * 0.025f, Offset(mx + w * 0.065f, my))
    drawCircle(Color(0xFFFFCCBC), w * 0.015f, Offset(mx + w * 0.065f, my))
    // Muzzle & smile
    drawCircle(Color(0xFFFFCCBC), w * 0.045f, Offset(mx, my + w * 0.015f))
    drawCircle(Color.Black, w * 0.01f, Offset(mx - w * 0.022f, my - w * 0.01f))
    drawCircle(Color.Black, w * 0.01f, Offset(mx + w * 0.022f, my - w * 0.01f))
    drawArc(
        color = Color.Black,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(mx - w * 0.02f, my + w * 0.01f),
        size = Size(w * 0.04f, w * 0.03f),
        style = Stroke(width = 4f)
    )

    // Toucan with colorful beak on top right
    val tx = w * 0.84f
    val ty = h * 0.16f - bounce * 6f
    drawCircle(Color(0xFF212121), w * 0.055f, Offset(tx, ty))
    // Big yellow/orange beak
    val beak = Path().apply {
        moveTo(tx - w * 0.04f, ty - w * 0.02f)
        lineTo(tx - w * 0.13f, ty + w * 0.015f)
        lineTo(tx - w * 0.04f, ty + w * 0.035f)
        close()
    }
    drawPath(beak, Color(0xFFFFB300))
    // Eye
    drawCircle(Color.White, w * 0.014f, Offset(tx - w * 0.015f, ty - w * 0.01f))
    drawCircle(Color.Black, w * 0.007f, Offset(tx - w * 0.018f, ty - w * 0.01f))

    // Lush monstera palm leaves framing bottom corners
    drawPalmLeaf(Offset(0f, h), w * 0.35f, 45f)
    drawPalmLeaf(Offset(w, h), w * 0.35f, -45f)
}

// 4. CANDY KINGDOM: Giant swirl lollipops, candy cane pillars, marshmallow clouds, floating sprinkles
private fun DrawScope.drawCandyLandBackground(w: Float, h: Float, floatProgress: Float, bounce: Float) {
    // Left Giant Swirl Lollipop
    drawLollipop(Offset(w * 0.12f, h * 0.78f), w * 0.12f, floatProgress * 360f, Color(0xFFEC4899), Color(0xFFFDE047))

    // Right Giant Swirl Lollipop
    drawLollipop(Offset(w * 0.88f, h * 0.75f), w * 0.11f, -floatProgress * 360f, Color(0xFF06B6D4), Color(0xFFF43F5E))

    // Fluffy candy clouds at top
    drawCandyCloud(Offset(w * 0.25f, h * 0.09f + bounce * 6f), w * 0.15f, Color(0xFFFBCFE8))
    drawCandyCloud(Offset(w * 0.75f, h * 0.08f - bounce * 5f), w * 0.16f, Color(0xFFBAE6FD))

    // Floating colorful sprinkles
    val sprinkles = listOf(
        Triple(w * 0.15f, h * 0.25f, Color(0xFFFF4081)),
        Triple(w * 0.85f, h * 0.28f, Color(0xFF00E5FF)),
        Triple(w * 0.35f, h * 0.18f, Color(0xFFFFEA00)),
        Triple(w * 0.65f, h * 0.22f, Color(0xFF76FF03))
    )
    sprinkles.forEachIndexed { i, s ->
        val rot = (floatProgress * 180f + i * 45f)
        rotate(rot, pivot = Offset(s.first, s.second)) {
            drawRoundRect(
                color = s.third,
                topLeft = Offset(s.first - w * 0.015f, s.second - w * 0.007f),
                size = Size(w * 0.03f, w * 0.014f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
            )
        }
    }
}

// 5. RAINBOW MAGIC: Giant arched 6-color cartoon rainbow across top, smiling puffy white clouds, sunshine rays
private fun DrawScope.drawRainbowBackground(w: Float, h: Float, floatProgress: Float, bounce: Float) {
    val rainbowColors = listOf(
        Color(0xFFFF3366), // Red
        Color(0xFFFF9900), // Orange
        Color(0xFFFFDD00), // Yellow
        Color(0xFF33CC66), // Green
        Color(0xFF3399FF), // Blue
        Color(0xFF9933FF)  // Purple
    )

    val rCenter = Offset(w * 0.5f, h * 0.28f)
    val baseRadius = w * 0.55f
    val arcWidth = w * 0.022f

    rainbowColors.forEachIndexed { i, color ->
        val rad = baseRadius - i * arcWidth
        drawArc(
            color = color.copy(alpha = 0.9f),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(rCenter.x - rad, rCenter.y - rad),
            size = Size(rad * 2, rad * 2),
            style = Stroke(width = arcWidth + 2f)
        )
    }

    // Left Smiling Cloud at base of rainbow
    drawSmilingCloud(Offset(w * 0.12f, h * 0.28f + bounce * 5f), w * 0.12f)

    // Right Smiling Cloud at base of rainbow
    drawSmilingCloud(Offset(w * 0.88f, h * 0.28f - bounce * 5f), w * 0.12f)

    // Warm Sun in upper center smiling
    val sunCenter = Offset(w * 0.5f, h * 0.07f)
    drawCircle(Color(0xFFFFD54F), w * 0.07f, sunCenter)
    // Sun rays
    for (a in 0..7) {
        val angle = (a * 45f + floatProgress * 45f) * (Math.PI / 180.0)
        val sx1 = sunCenter.x + cos(angle).toFloat() * w * 0.08f
        val sy1 = sunCenter.y + sin(angle).toFloat() * w * 0.08f
        val sx2 = sunCenter.x + cos(angle).toFloat() * w * 0.11f
        val sy2 = sunCenter.y + sin(angle).toFloat() * w * 0.11f
        drawLine(Color(0xFFFFCA28), Offset(sx1, sy1), Offset(sx2, sy2), strokeWidth = 6f)
    }
}

// 6. DINO LAND: Prehistoric cartoon smoking volcano, giant ferns, friendly cartoon baby T-Rex peeking
private fun DrawScope.drawDinoBackground(w: Float, h: Float, floatProgress: Float, bounce: Float) {
    // Volcano on top right horizon
    val vx = w * 0.82f
    val vy = h * 0.16f
    val vPath = Path().apply {
        moveTo(vx - w * 0.12f, vy + w * 0.1f)
        lineTo(vx - w * 0.035f, vy)
        lineTo(vx + w * 0.035f, vy)
        lineTo(vx + w * 0.12f, vy + w * 0.1f)
        close()
    }
    drawPath(vPath, Color(0xFF5D4037))
    // Lava top
    drawLine(Color(0xFFFF5722), Offset(vx - w * 0.035f, vy), Offset(vx + w * 0.035f, vy), strokeWidth = 8f)

    // Smoke puff clouds from volcano
    val smokeY = vy - w * 0.04f - (floatProgress * w * 0.08f)
    drawCircle(Color.White.copy(alpha = 0.7f), w * 0.03f, Offset(vx, smokeY))
    drawCircle(Color.White.copy(alpha = 0.5f), w * 0.04f, Offset(vx + w * 0.02f, smokeY - w * 0.035f))

    // Baby green cartoon dinosaur peeking bottom-left
    val dx = w * 0.16f
    val dy = h * 0.88f + bounce * 6f
    // Dino head
    drawCircle(Color(0xFF4CAF50), w * 0.09f, Offset(dx, dy))
    // Cute big cartoon eye
    drawCircle(Color.White, w * 0.03f, Offset(dx + w * 0.02f, dy - w * 0.03f))
    drawCircle(Color.Black, w * 0.016f, Offset(dx + w * 0.028f, dy - w * 0.03f))
    // Cheerful smile
    drawArc(
        color = Color(0xFF1B5E20),
        startAngle = 10f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(dx - w * 0.02f, dy),
        size = Size(w * 0.06f, w * 0.04f),
        style = Stroke(width = 6f)
    )
    // Tiny little arms waving
    drawLine(Color(0xFF4CAF50), Offset(dx + w * 0.07f, dy + w * 0.03f), Offset(dx + w * 0.11f, dy - w * 0.01f), strokeWidth = 8f)

    // Prehistoric ferns bottom right
    drawPalmLeaf(Offset(w, h), w * 0.38f, -40f)
}

// Helper Drawing Functions
private fun DrawScope.drawStar(center: Offset, size: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - size)
        lineTo(center.x + size * 0.3f, center.y - size * 0.3f)
        lineTo(center.x + size, center.y)
        lineTo(center.x + size * 0.3f, center.y + size * 0.3f)
        lineTo(center.x, center.y + size)
        lineTo(center.x - size * 0.3f, center.y + size * 0.3f)
        lineTo(center.x - size, center.y)
        lineTo(center.x - size * 0.3f, center.y - size * 0.3f)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawPalmLeaf(origin: Offset, len: Float, angleDeg: Float) {
    rotate(angleDeg, pivot = origin) {
        drawLine(Color(0xFF2E7D32), origin, Offset(origin.x, origin.y - len), strokeWidth = 8f)
        for (i in 1..4) {
            val fy = origin.y - len * (i * 0.2f)
            drawLine(Color(0xFF4CAF50), Offset(origin.x, fy), Offset(origin.x - len * 0.25f, fy - len * 0.1f), strokeWidth = 6f)
            drawLine(Color(0xFF4CAF50), Offset(origin.x, fy), Offset(origin.x + len * 0.25f, fy - len * 0.1f), strokeWidth = 6f)
        }
    }
}

private fun DrawScope.drawLollipop(base: Offset, radius: Float, rotationDeg: Float, c1: Color, c2: Color) {
    // Stick
    drawLine(Color.White, base, Offset(base.x, base.y + radius * 1.5f), strokeWidth = 10f)
    // Candy Swirl Circle
    drawCircle(c1, radius, base)
    rotate(rotationDeg, pivot = base) {
        for (a in 0..3) {
            drawArc(
                color = c2,
                startAngle = a * 90f,
                sweepAngle = 45f,
                useCenter = true,
                topLeft = Offset(base.x - radius, base.y - radius),
                size = Size(radius * 2, radius * 2)
            )
        }
    }
    drawCircle(Color.White.copy(alpha = 0.8f), radius, base, style = Stroke(width = 4f))
}

private fun DrawScope.drawCandyCloud(center: Offset, size: Float, color: Color) {
    drawCircle(color, size * 0.5f, center)
    drawCircle(color, size * 0.38f, Offset(center.x - size * 0.45f, center.y + size * 0.05f))
    drawCircle(color, size * 0.42f, Offset(center.x + size * 0.45f, center.y + size * 0.05f))
}

private fun DrawScope.drawSmilingCloud(center: Offset, size: Float) {
    drawCircle(Color.White, size * 0.5f, center)
    drawCircle(Color.White, size * 0.38f, Offset(center.x - size * 0.45f, center.y + size * 0.05f))
    drawCircle(Color.White, size * 0.42f, Offset(center.x + size * 0.45f, center.y + size * 0.05f))
    // Eyes & Smile
    drawCircle(Color.Black, size * 0.08f, Offset(center.x - size * 0.18f, center.y))
    drawCircle(Color.Black, size * 0.08f, Offset(center.x + size * 0.18f, center.y))
    // Rosy pink cheeks
    drawCircle(Color(0xFFFF80AB), size * 0.09f, Offset(center.x - size * 0.32f, center.y + size * 0.1f))
    drawCircle(Color(0xFFFF80AB), size * 0.09f, Offset(center.x + size * 0.32f, center.y + size * 0.1f))
    drawArc(
        color = Color.Black,
        startAngle = 10f,
        sweepAngle = 160f,
        useCenter = false,
        topLeft = Offset(center.x - size * 0.15f, center.y + size * 0.05f),
        size = Size(size * 0.3f, size * 0.2f),
        style = Stroke(width = 4f)
    )
}
