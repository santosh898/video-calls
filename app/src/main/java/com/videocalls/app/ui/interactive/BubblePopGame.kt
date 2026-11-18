package com.videocalls.app.ui.interactive

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
    val radius: Float, // dp-like size
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
        Color(0x8838BDF8), // Cyan
        Color(0x88EC4899), // Pink
        Color(0x88FBBF24), // Yellow
        Color(0x88A855F7), // Purple
        Color(0x8834D399)  // Green
    )

    fun spawnBubble(now: Long) {
        if (bubbles.size >= 12) return
        if (now - lastSpawnTime < 800L) return
        lastSpawnTime = now

        val b = Bubble(
            id = nextId++,
            x = Random.nextFloat() * 0.8f + 0.1f,
            y = 1.1f, // just below screen
            radius = Random.nextFloat() * 32f + 28f,
            speed = Random.nextFloat() * 0.003f + 0.002f,
            swayAmplitude = Random.nextFloat() * 0.02f + 0.01f,
            swayFrequency = Random.nextFloat() * 0.003f + 0.002f,
            color = bubbleColors[Random.nextInt(bubbleColors.size)]
        )
        bubbles.add(b)
    }

    fun update(timeMillis: Long) {
        spawnBubble(timeMillis)
        val iterator = bubbles.iterator()
        while (iterator.hasNext()) {
            val b = iterator.next()
            b.y -= b.speed
            b.x += sin(timeMillis * b.swayFrequency) * b.swayAmplitude
            b.x = b.x.coerceIn(0.05f, 0.95f)

            if (b.y < -0.15f) {
                iterator.remove() // Floated off top
            }
        }
    }

    fun handleTap(tapX: Float, tapY: Float, width: Float, height: Float): Boolean {
        for (i in bubbles.indices.reversed()) {
            val b = bubbles[i]
            val bx = b.x * width
            val by = b.y * height
            val dx = tapX - bx
            val dy = tapY - by
            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
            if (dist <= b.radius * 1.3f) {
                bubbles.removeAt(i)
                popCount++
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
            onBubblePopped(b.id, b.x * width, b.y * height)
        }
    }
}

@Composable
fun BubblePopOverlay(
    modifier: Modifier = Modifier,
    manager: BubbleGameManager,
    particleManager: ParticleManager
) {
    LaunchedEffect(Unit) {
        while (isActive) {
            withInfiniteAnimationFrameMillis { time ->
                manager.update(time)
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
                Text(text = "🫧", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Popped: ${manager.popCount}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val popped = manager.handleTap(offset.x, offset.y, size.width.toFloat(), size.height.toFloat())
                        if (popped) {
                            particleManager.burst(offset.x, offset.y, 22)
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height

            for (b in manager.bubbles) {
                val cx = b.x * w
                val cy = b.y * h

                // Bubble body
                drawCircle(
                    color = b.color,
                    radius = b.radius,
                    center = Offset(cx, cy)
                )
                // Outer highlight border
                drawCircle(
                    color = Color.White.copy(alpha = 0.7f),
                    radius = b.radius,
                    center = Offset(cx, cy),
                    style = Stroke(width = 3f)
                )
                // Glint (top left shine)
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = b.radius * 0.25f,
                    center = Offset(cx - b.radius * 0.35f, cy - b.radius * 0.35f)
                )
            }
        }
    }
}
