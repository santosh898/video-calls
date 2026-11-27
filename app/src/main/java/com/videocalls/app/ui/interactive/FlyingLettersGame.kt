package com.videocalls.app.ui.interactive

import android.graphics.BitmapFactory
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.videocalls.app.R
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

data class FlyingChar(
    val id: Long,
    val resId: Int,
    val text: String,
    val isAlphabet: Boolean,
    val index: Int,
    val startX: Float,
    val startY: Float,
    var x: Float,
    var y: Float,
    var vy: Float,
    val rotation: Float,
    val spawnTime: Long,
    var currentWidth: Float = 0f
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
        val isAlpha = (gameType == InteractiveGameType.ALPHABET)
        val (resId, label, index) = if (isAlpha) {
            val idx = alphabetIndex % alphabetDrawables.size
            alphabetIndex = (alphabetIndex + 1) % alphabetDrawables.size
            Triple(alphabetDrawables[idx], alphabetLabels[idx], idx)
        } else {
            val idx = numberIndex % numberDrawables.size
            numberIndex = (numberIndex + 1) % numberDrawables.size
            Triple(numberDrawables[idx], numberLabels[idx], idx)
        }

        val rotation = (Random.nextFloat() - 0.5f) * 28f // -14°..+14° random angle skew
        val vy = -13f // Initial upward pop burst

        val startW = if (viewportWidth > 0) viewportWidth * 0.14f else 120f
        val item = FlyingChar(
            id = nextId++,
            resId = resId,
            text = label,
            isAlphabet = isAlpha,
            index = index,
            startX = tapX,
            startY = tapY,
            x = tapX,
            y = tapY,
            vy = vy,
            rotation = rotation,
            spawnTime = System.currentTimeMillis(),
            currentWidth = startW
        )
        items.add(item)

        particleManager?.burst(tapX, tapY, 20)
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
        val isAlpha = (gameType == InteractiveGameType.ALPHABET)
        val (resId, label) = if (isAlpha) {
            val safeIdx = index.coerceIn(0, alphabetDrawables.size - 1)
            Pair(alphabetDrawables[safeIdx], alphabetLabels[safeIdx])
        } else {
            val safeIdx = index.coerceIn(0, numberDrawables.size - 1)
            Pair(numberDrawables[safeIdx], numberLabels[safeIdx])
        }

        val realX = normX * viewportWidth
        val realY = normY * viewportHeight
        val vy = -13f

        val startW = if (viewportWidth > 0) viewportWidth * 0.14f else 120f
        val item = FlyingChar(
            id = nextId++,
            resId = resId,
            text = label,
            isAlphabet = isAlpha,
            index = index,
            startX = realX,
            startY = realY,
            x = realX,
            y = realY,
            vy = vy,
            rotation = rotation,
            spawnTime = System.currentTimeMillis(),
            currentWidth = startW
        )
        items.add(item)

        particleManager?.burst(realX, realY, 20)
        KidsSoundManager.playPop()
    }

    fun update(timeMillis: Long, viewportWidth: Float, viewportHeight: Float) {
        if (lastUpdateTime == 0L) lastUpdateTime = timeMillis
        val dt = ((timeMillis - lastUpdateTime).toFloat() / 16.67f).coerceIn(0.5f, 2.5f)
        lastUpdateTime = timeMillis

        val iterator = items.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            val age = (timeMillis - item.spawnTime).coerceAtLeast(0L)

            // 1. Expansion: Starts at fingertip size (~14% screen width), gets big to full screen width (~94% screen width)
            val startW = (viewportWidth * 0.14f).coerceAtLeast(80f)
            val targetW = (viewportWidth * 0.94f).coerceAtLeast(320f)

            // Expand over 420ms with smooth cubic ease-out
            val progress = (age / 420f).coerceIn(0f, 1f)
            val scaleCurve = if (progress < 0.85f) {
                val t = progress / 0.85f
                1f - (1f - t) * (1f - t) * (1f - t)
            } else {
                val t = (progress - 0.85f) / 0.15f
                1.0f + 0.04f * sin(t * Math.PI.toFloat())
            }
            item.currentWidth = startW + (targetW - startW) * scaleCurve

            // 2. Horizontal centering: As it gets big to screen width, it floats toward horizontal center (viewportWidth / 2f)
            val centerProgress = (age / 380f).coerceIn(0f, 1f)
            val centerEase = centerProgress * centerProgress * (3f - 2f * centerProgress) // smoothstep
            val targetX = viewportWidth / 2f
            item.x = item.startX + (targetX - item.startX) * centerEase

            // 3. Gravity downward acceleration
            item.vy += 0.52f * dt
            item.y += item.vy * dt

            // 4. Removal: Only remove when top edge of giant letter has completely dropped off bottom of screen
            val halfH = item.currentWidth * 0.65f
            if (item.y - halfH > viewportHeight + 50f) {
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
    val context = LocalContext.current
    var screenWidth by remember { mutableFloatStateOf(1080f) }
    var screenHeight by remember { mutableFloatStateOf(1920f) }
    var frameTick by remember { mutableLongStateOf(0L) }

    // Preload Bitmaps as ImageBitmaps once
    val alphabetBitmaps = remember {
        manager.alphabetDrawables.map { resId ->
            BitmapFactory.decodeResource(context.resources, resId).asImageBitmap()
        }
    }
    val numberBitmaps = remember {
        manager.numberDrawables.map { resId ->
            BitmapFactory.decodeResource(context.resources, resId).asImageBitmap()
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            withInfiniteAnimationFrameMillis { time ->
                manager.update(time, screenWidth, screenHeight)
                frameTick = time
            }
        }
    }

    Canvas(
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
        val _tick = frameTick // Invalidate on every vsync frame for 60fps/120fps smooth animation

        for (item in manager.items) {
            val bitmap = if (item.isAlphabet) {
                alphabetBitmaps.getOrNull(item.index) ?: continue
            } else {
                numberBitmaps.getOrNull(item.index) ?: continue
            }

            val aspect = bitmap.height.toFloat() / bitmap.width.toFloat()
            val drawW = item.currentWidth
            val drawH = drawW * aspect

            withTransform({
                rotate(item.rotation, pivot = Offset(item.x, item.y))
            }) {
                drawImage(
                    image = bitmap,
                    dstOffset = IntOffset(
                        (item.x - drawW / 2f).roundToInt(),
                        (item.y - drawH / 2f).roundToInt()
                    ),
                    dstSize = IntSize(drawW.roundToInt(), drawH.roundToInt()),
                    filterQuality = FilterQuality.High
                )
            }
        }
    }
}
