package com.videocalls.app.ui.interactive

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.videocalls.app.R
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt
import kotlin.random.Random

data class FlyingChar(
    val id: Long,
    val resId: Int,
    val text: String,
    var x: Float,
    var y: Float,
    var vy: Float,
    var vx: Float,
    val rotation: Float,
    val spawnTime: Long,
    var scale: Float = 0.2f
)

class FlyingLettersManager(
    val onLocalSpawn: (gameType: InteractiveGameType, index: Int, normX: Float, normY: Float, rotation: Float) -> Unit
) {
    val items = mutableStateListOf<FlyingChar>()
    private var nextId = 1L
    private var lastUpdateTime = 0L

    var alphabetIndex by mutableStateOf(0)
    var numberIndex by mutableStateOf(0)

    val alphabetDrawables = listOf(
        R.drawable.letter_a,
        R.drawable.letter_b,
        R.drawable.letter_c,
        R.drawable.letter_d,
        R.drawable.letter_e
    )
    val alphabetLabels = listOf("A", "B", "C", "D", "E")

    val numberDrawables = listOf(
        R.drawable.number_1,
        R.drawable.number_2,
        R.drawable.number_3,
        R.drawable.number_4,
        R.drawable.number_5,
        R.drawable.number_6,
        R.drawable.number_7,
        R.drawable.number_8,
        R.drawable.number_9,
        R.drawable.number_10
    )
    val numberLabels = (1..10).map { it.toString() }

    fun spawnLocal(
        tapX: Float,
        tapY: Float,
        viewportWidth: Float,
        viewportHeight: Float,
        gameType: InteractiveGameType,
        particleManager: ParticleManager? = null
    ) {
        val (resId, label, index) = if (gameType == InteractiveGameType.ALPHABET) {
            val idx = alphabetIndex % alphabetDrawables.size
            alphabetIndex = (alphabetIndex + 1) % alphabetDrawables.size
            Triple(alphabetDrawables[idx], alphabetLabels[idx], idx)
        } else {
            val idx = numberIndex % numberDrawables.size
            numberIndex = (numberIndex + 1) % numberDrawables.size
            Triple(numberDrawables[idx], numberLabels[idx], idx)
        }

        val rotation = (Random.nextFloat() - 0.5f) * 32f // -16°..+16° random skew
        val vx = (Random.nextFloat() - 0.5f) * 3f
        val vy = -11f // initial upward pop burst

        val item = FlyingChar(
            id = nextId++,
            resId = resId,
            text = label,
            x = tapX,
            y = tapY,
            vy = vy,
            vx = vx,
            rotation = rotation,
            spawnTime = System.currentTimeMillis()
        )
        items.add(item)

        particleManager?.burst(tapX, tapY, 18)
        KidsSoundManager.playPop()

        val normX = if (viewportWidth > 0) tapX / viewportWidth else 0.5f
        val normY = if (viewportHeight > 0) tapY / viewportHeight else 0.5f
        onLocalSpawn(gameType, index, normX, normY, rotation)
    }

    fun spawnRemote(
        gameType: InteractiveGameType,
        index: Int,
        normX: Float,
        normY: Float,
        rotation: Float,
        viewportWidth: Float,
        viewportHeight: Float,
        particleManager: ParticleManager? = null
    ) {
        val (resId, label) = if (gameType == InteractiveGameType.ALPHABET) {
            val safeIdx = index.coerceIn(0, alphabetDrawables.size - 1)
            Pair(alphabetDrawables[safeIdx], alphabetLabels[safeIdx])
        } else {
            val safeIdx = index.coerceIn(0, numberDrawables.size - 1)
            Pair(numberDrawables[safeIdx], numberLabels[safeIdx])
        }

        val realX = normX * viewportWidth
        val realY = normY * viewportHeight
        val vx = (Random.nextFloat() - 0.5f) * 3f
        val vy = -11f

        val item = FlyingChar(
            id = nextId++,
            resId = resId,
            text = label,
            x = realX,
            y = realY,
            vy = vy,
            vx = vx,
            rotation = rotation,
            spawnTime = System.currentTimeMillis()
        )
        items.add(item)

        particleManager?.burst(realX, realY, 18)
        KidsSoundManager.playPop()
    }

    fun update(timeMillis: Long, viewportHeight: Float) {
        if (lastUpdateTime == 0L) lastUpdateTime = timeMillis
        val dt = ((timeMillis - lastUpdateTime).toFloat() / 16.67f).coerceIn(0.5f, 2.5f)
        lastUpdateTime = timeMillis

        val iterator = items.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            val age = (timeMillis - item.spawnTime).coerceAtLeast(0L)

            // Scale animation: starts small (0.2), grows rapidly to 1.15 at 200ms, settles to 1.0 at 360ms
            item.scale = when {
                age < 200L -> 0.2f + (0.95f * (age / 200f))
                age < 360L -> 1.15f - (0.15f * ((age - 200L) / 160f))
                else -> 1.0f
            }

            // Gravity acceleration downwards
            item.vy += 0.7f * dt
            item.y += item.vy * dt
            item.x += item.vx * dt

            // Remove when completely dropped off bottom of screen
            if (item.y > viewportHeight + 250f) {
                iterator.remove()
            }
        }
    }
}

@Composable
fun FlyingLettersOverlay(
    modifier: Modifier = Modifier,
    gameType: InteractiveGameType,
    manager: FlyingLettersManager,
    particleManager: ParticleManager
) {
    var screenWidth by remember { mutableFloatStateOf(1080f) }
    var screenHeight by remember { mutableFloatStateOf(1920f) }

    LaunchedEffect(Unit) {
        while (isActive) {
            withInfiniteAnimationFrameMillis { time ->
                manager.update(time, screenHeight)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                if (size.width > 0 && size.height > 0) {
                    screenWidth = size.width.toFloat()
                    screenHeight = size.height.toFloat()
                }
            }
            .pointerInput(gameType) {
                detectTapGestures { offset ->
                    manager.spawnLocal(
                        tapX = offset.x,
                        tapY = offset.y,
                        viewportWidth = screenWidth,
                        viewportHeight = screenHeight,
                        gameType = gameType,
                        particleManager = particleManager
                    )
                }
            }
    ) {
        manager.items.forEach { item ->
            key(item.id) {
                Image(
                    painter = painterResource(id = item.resId),
                    contentDescription = item.text,
                    modifier = Modifier
                        .size(130.dp)
                        .offset {
                            // Center image around current (x, y)
                            val halfW = 65.dp.toPx()
                            val halfH = 65.dp.toPx()
                            IntOffset(
                                (item.x - halfW).roundToInt(),
                                (item.y - halfH).roundToInt()
                            )
                        }
                        .graphicsLayer {
                            scaleX = item.scale
                            scaleY = item.scale
                            rotationZ = item.rotation
                        }
                )
            }
        }
    }
}
