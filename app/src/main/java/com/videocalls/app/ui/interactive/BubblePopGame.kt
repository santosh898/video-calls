package com.videocalls.app.ui.interactive

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import kotlin.math.sin
import kotlin.random.Random

data class Bubble(
    val id: Int,
    var x: Float, // 0f..1f (normalized)
    var y: Float, // 0f..1.2f (normalized, >1f is below screen)
    val radius: Float, // size in px
    val speed: Float,
    val swayAmplitude: Float,
    val swayFrequency: Float,
    val color: Color
)

class BubbleGameManager(
    val onBubblePopped: (id: Int, x: Float, y: Float) -> Unit
) {
    val bubbles = mutableStateListOf<Bubble>()
    var popCount by mutableStateOf(0)
    private var nextId = 1
    private var lastSpawnTime = 0L

    private val bubbleColors = listOf(
        Color(0x9938BDF8), // Cyan
        Color(0x99EC4899), // Pink
        Color(0x99FBBF24), // Yellow
        Color(0x99A855F7), // Purple
        Color(0x9934D399), // Green
        Color(0x99FB923C)  // Orange
    )

    fun spawnBubble(now: Long, viewportWidth: Float) {
        if (bubbles.size >= 16) return
        if (now - lastSpawnTime < 550L) return
        lastSpawnTime = now

        // Big toddler-friendly size: between 50dp and 85dp equivalent in px
        val baseSize = if (viewportWidth > 0) viewportWidth * 0.16f else 120f
        val rad = baseSize * (Random.nextFloat() * 0.5f + 0.8f)

        val b = Bubble(
            id = nextId++,
            x = Random.nextFloat() * 0.76f + 0.12f,
            y = 1.12f, // spawn below bottom edge
            radius = rad,
            speed = Random.nextFloat() * 0.0007f + 0.00035f, // very gentle, slow float
            swayAmplitude = Random.nextFloat() * 0.025f + 0.01f,
            swayFrequency = Random.nextFloat() * 0.002f + 0.001f,
            color = bubbleColors[Random.nextInt(bubbleColors.size)]
        )
        bubbles.add(b)
    }

    fun update(timeMillis: Long, viewportWidth: Float) {
        spawnBubble(timeMillis, viewportWidth)
        val iterator = bubbles.iterator()
        while (iterator.hasNext()) {
            val b = iterator.next()
            b.y -= b.speed
            b.x += sin(timeMillis * b.swayFrequency) * b.swayAmplitude
            b.x = b.x.coerceIn(0.06f, 0.94f)

            if (b.y < -0.2f) {
                iterator.remove() // Floated off top
            }
        }
    }

    fun handleTap(tapX: Float, tapY: Float, width: Float, height: Float): Boolean {
        // Very generous hit test: bubble radius * 1.8 + 60px so toddlers easily hit it
        for (i in bubbles.indices.reversed()) {
            val b = bubbles[i]
            val bx = b.x * width
            val by = b.y * height
            val dx = tapX - bx
            val dy = tapY - by
            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
            val hitRadius = b.radius * 1.8f + 60f

            if (dist <= hitRadius) {
                bubbles.removeAt(i)
                popCount++
                KidsSoundManager.playPop()
                onBubblePopped(b.id, bx, by)
                return true
            }
        }
        return false
    }

    fun popRemoteBubble(id: Int, width: Float, height: Float) {
        val index = bubbles.indexOfFirst { it.id == id }
        if (index != -1) {
            val b = bubbles.removeAt(index)
            popCount++
            KidsSoundManager.playPop()
            onBubblePopped(b.id, b.x * width, b.y * height)
        }
    }
}

@Composable
fun BubblePopOverlay(
    modifier: Modifier = Modifier,
    manager: BubbleGameManager,
    particleManager: ParticleManager,
    onTap: (() -> Unit)? = null
) {
    var screenWidth by remember { mutableFloatStateOf(1080f) }

    LaunchedEffect(screenWidth) {
        while (isActive) {
            withInfiniteAnimationFrameMillis { time ->
                manager.update(time, screenWidth)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Score Pill Top Center
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1E293B).copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 50.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "🫧", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Popped: ${manager.popCount}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onTap?.invoke()
                        val popped = manager.handleTap(offset.x, offset.y, size.width.toFloat(), size.height.toFloat())
                        if (popped) {
                            particleManager.burst(offset.x, offset.y, 24)
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            if (screenWidth != w) {
                screenWidth = w
            }

            for (b in manager.bubbles) {
                val cx = b.x * w
                val cy = b.y * h
                val rad = b.radius

                // 1. Shimmering translucent bubble body
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.25f),
                            b.color,
                            b.color.copy(alpha = 0.65f)
                        ),
                        center = Offset(cx - rad * 0.25f, cy - rad * 0.25f),
                        radius = rad
                    ),
                    radius = rad,
                    center = Offset(cx, cy)
                )

                // 2. Outer glowing crisp rim
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = rad,
                    center = Offset(cx, cy),
                    style = Stroke(width = 4f)
                )

                // 3. Crescent light reflection (top-left shiny arc)
                drawArc(
                    color = Color.White.copy(alpha = 0.9f),
                    startAngle = 180f,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset(cx - rad * 0.82f, cy - rad * 0.82f),
                    size = Size(rad * 1.64f, rad * 1.64f),
                    style = Stroke(width = rad * 0.14f)
                )

                // 4. Secondary small gleam dot (bottom-right)
                drawCircle(
                    color = Color.White.copy(alpha = 0.75f),
                    radius = rad * 0.16f,
                    center = Offset(cx + rad * 0.45f, cy + rad * 0.45f)
                )
            }
        }
    }
}
