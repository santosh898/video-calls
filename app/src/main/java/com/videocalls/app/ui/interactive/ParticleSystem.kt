package com.videocalls.app.ui.interactive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class ParticleShape {
    STAR, SPARKLE, HEART, BUBBLE, CIRCLE
}

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    val size: Float,
    var alpha: Float = 1f,
    val shape: ParticleShape,
    var rotation: Float = 0f,
    val vRotation: Float = 0f,
    var life: Float = 1f,
    val decay: Float = 0.025f
)

class ParticleManager {
    val particles = mutableStateListOf<Particle>()

    private val colors = listOf(
        Color(0xFFFFD700), // Gold
        Color(0xFFFF69B4), // Hot Pink
        Color(0xFF00FFFF), // Cyan
        Color(0xFFBA55D3), // Medium Orchid
        Color(0xFF39FF14), // Neon Green
        Color(0xFFFF4500), // Orange Red
        Color(0xFFFFFFFF)  // White
    )

    fun burst(x: Float, y: Float, count: Int = 14) {
        val newParticles = (0 until count).map {
            val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
            val speed = Random.nextFloat() * 12f + 4f
            val shape = ParticleShape.values()[Random.nextInt(ParticleShape.values().size)]
            Particle(
                x = x,
                y = y,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed - 2f, // slight upward boost
                color = colors[Random.nextInt(colors.size)],
                size = Random.nextFloat() * 18f + 10f,
                shape = shape,
                rotation = Random.nextFloat() * 360f,
                vRotation = (Random.nextFloat() - 0.5f) * 16f,
                decay = Random.nextFloat() * 0.02f + 0.015f
            )
        }
        particles.addAll(newParticles)
    }

    fun update() {
        if (particles.isEmpty()) return
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.x += p.vx
            p.y += p.vy
            p.vy += 0.35f // Gravity
            p.rotation += p.vRotation
            p.life -= p.decay
            p.alpha = p.life.coerceIn(0f, 1f)
            if (p.life <= 0f) {
                iterator.remove()
            }
        }
    }
}

@Composable
fun ParticleOverlay(
    modifier: Modifier = Modifier,
    particleManager: ParticleManager,
    enabled: Boolean = false,
    onTap: ((Offset) -> Unit)? = null,
    onTapBurst: ((normX: Float, normY: Float) -> Unit)? = null
) {
    if (!enabled && particleManager.particles.isEmpty()) {
        return
    }

    LaunchedEffect(enabled, particleManager.particles.size) {
        if (!enabled && particleManager.particles.isEmpty()) return@LaunchedEffect
        while (isActive) {
            withFrameNanos {
                particleManager.update()
            }
        }
    }

    val touchModifier = if (enabled) {
        Modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    KidsSoundManager.playSparkle()
                    particleManager.burst(offset.x, offset.y, 16)
                    onTap?.invoke(offset)
                    if (size.width > 0 && size.height > 0) {
                        onTapBurst?.invoke(
                            (offset.x / size.width.toFloat()).coerceIn(0f, 1f),
                            (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                        )
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    particleManager.burst(change.position.x, change.position.y, 4)
                }
            }
    } else {
        Modifier
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .then(touchModifier)
    ) {
        for (p in particleManager.particles) {
            drawParticle(p)
        }
    }
}

private fun DrawScope.drawParticle(p: Particle) {
    val drawColor = p.color.copy(alpha = p.alpha)
    rotate(p.rotation, pivot = Offset(p.x, p.y)) {
        when (p.shape) {
            ParticleShape.CIRCLE -> {
                drawCircle(color = drawColor, radius = p.size / 2f, center = Offset(p.x, p.y))
            }
            ParticleShape.BUBBLE -> {
                drawCircle(
                    color = drawColor,
                    radius = p.size / 2f,
                    center = Offset(p.x, p.y),
                    style = Stroke(width = 3f)
                )
                // Highlight glint
                drawCircle(
                    color = Color.White.copy(alpha = p.alpha * 0.8f),
                    radius = p.size / 7f,
                    center = Offset(p.x - p.size / 6f, p.y - p.size / 6f)
                )
            }
            ParticleShape.STAR -> {
                drawStar(Offset(p.x, p.y), p.size, drawColor)
            }
            ParticleShape.SPARKLE -> {
                drawSparkle(Offset(p.x, p.y), p.size, drawColor)
            }
            ParticleShape.HEART -> {
                drawHeart(Offset(p.x, p.y), p.size, drawColor)
            }
        }
    }
}

private fun DrawScope.drawStar(center: Offset, size: Float, color: Color) {
    val path = Path()
    val outerRadius = size / 2f
    val innerRadius = outerRadius * 0.45f
    val numPoints = 5
    val step = (Math.PI / numPoints).toFloat()

    for (i in 0 until numPoints * 2) {
        val r = if (i % 2 == 0) outerRadius else innerRadius
        val angle = i * step - (Math.PI / 2).toFloat()
        val x = center.x + r * cos(angle)
        val y = center.y + r * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color = color, style = Fill)
}

private fun DrawScope.drawSparkle(center: Offset, size: Float, color: Color) {
    val half = size / 2f
    val path = Path().apply {
        moveTo(center.x, center.y - half)
        quadraticTo(center.x, center.y, center.x + half, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + half)
        quadraticTo(center.x, center.y, center.x - half, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - half)
        close()
    }
    drawPath(path, color = color, style = Fill)
}

private fun DrawScope.drawHeart(center: Offset, size: Float, color: Color) {
    val width = size
    val height = size * 0.9f
    val path = Path().apply {
        moveTo(center.x, center.y + height * 0.35f)
        cubicTo(
            center.x - width * 0.55f, center.y - height * 0.15f,
            center.x - width * 0.55f, center.y - height * 0.55f,
            center.x, center.y - height * 0.25f
        )
        cubicTo(
            center.x + width * 0.55f, center.y - height * 0.55f,
            center.x + width * 0.55f, center.y - height * 0.15f,
            center.x, center.y + height * 0.35f
        )
        close()
    }
    drawPath(path, color = color, style = Fill)
}
