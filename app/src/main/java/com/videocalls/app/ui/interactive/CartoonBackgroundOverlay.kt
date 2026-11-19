package com.videocalls.app.ui.interactive

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.videocalls.app.R
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CartoonBackgroundOverlay(
    modifier: Modifier = Modifier,
    backgroundType: InteractiveBackgroundType
) {
    if (backgroundType == InteractiveBackgroundType.NONE) return

    val bgResId = when (backgroundType) {
        InteractiveBackgroundType.SPACE -> R.drawable.bg_space
        InteractiveBackgroundType.UNDERWATER -> R.drawable.bg_underwater
        InteractiveBackgroundType.JUNGLE -> R.drawable.bg_jungle
        InteractiveBackgroundType.CANDY_LAND -> R.drawable.bg_candy
        InteractiveBackgroundType.RAINBOW -> R.drawable.bg_rainbow
        InteractiveBackgroundType.DINO -> R.drawable.bg_dino
        InteractiveBackgroundType.NONE -> null
    } ?: return

    // 3D animated sprites
    val rocketBitmap = ImageBitmap.imageResource(id = R.drawable.effect_rocket)
    val fishBitmap = ImageBitmap.imageResource(id = R.drawable.effect_fish)
    val bubblesBitmap = ImageBitmap.imageResource(id = R.drawable.effect_bubbles)
    val lollipopBitmap = ImageBitmap.imageResource(id = R.drawable.effect_lollipop)
    val starBitmap = ImageBitmap.imageResource(id = R.drawable.effect_glowing_star)
    val rainbowArcBitmap = ImageBitmap.imageResource(id = R.drawable.effect_rainbow_arc)
    val dinoBitmap = ImageBitmap.imageResource(id = R.drawable.effect_sauropod)
    val twinklesBitmap = ImageBitmap.imageResource(id = R.drawable.effect_twinkles)

    val infiniteTransition = rememberInfiniteTransition(label = "cartoonBg")

    // Slow continuous motion (6 seconds loop)
    val floatAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "floatAnim"
    )

    // Gentle bounce (-1f..1f, 1.6s)
    val bounceAnim by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounceAnim"
    )

    Box(modifier = modifier.fillMaxSize()) {
        // High-res cartoon background art with 65% opacity so caller video remains visible
        Image(
            painter = painterResource(id = bgResId),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.65f,
            modifier = Modifier.fillMaxSize()
        )

        // Animated 3D characters and atmospheric overlay
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0 || h <= 0) return@Canvas

            when (backgroundType) {
                InteractiveBackgroundType.SPACE -> {
                    // Flying 3D Rocket along orbital arc
                    val rocketAngle = floatAnim * 2 * Math.PI.toFloat()
                    val rx = w * 0.5f + cos(rocketAngle.toDouble()).toFloat() * (w * 0.42f)
                    val ry = h * 0.42f + sin(rocketAngle.toDouble()).toFloat() * (h * 0.32f)
                    val rRot = (rocketAngle * 180f / Math.PI.toFloat()) + 45f
                    drawAsset(rocketBitmap, Offset(rx, ry), w * 0.18f, rotation = rRot)

                    // Twinkling stars
                    val starPositions = listOf(
                        Offset(w * 0.2f, h * 0.12f),
                        Offset(w * 0.8f, h * 0.15f),
                        Offset(w * 0.15f, h * 0.75f),
                        Offset(w * 0.85f, h * 0.72f)
                    )
                    starPositions.forEachIndexed { i, pos ->
                        val scale = 0.75f + 0.25f * sin((floatAnim * 4 * Math.PI.toFloat() + i).toDouble()).toFloat()
                        drawAsset(twinklesBitmap, pos, w * 0.1f * scale)
                    }
                }

                InteractiveBackgroundType.UNDERWATER -> {
                    // Swimming 3D Tropical Fish from left to right
                    val fishX = (floatAnim * (w + w * 0.3f) - w * 0.15f)
                    val fishY = h * 0.32f + bounceAnim * 16f
                    drawAsset(fishBitmap, Offset(fishX, fishY), w * 0.2f)

                    // Rising 3D bubbles
                    val bubbleY1 = ((1f - floatAnim) * (h + 80f)) - 40f
                    val bubbleY2 = ((1f - (floatAnim + 0.5f) % 1f) * (h + 80f)) - 40f
                    drawAsset(bubblesBitmap, Offset(w * 0.15f, bubbleY1), w * 0.16f, alpha = 0.85f)
                    drawAsset(bubblesBitmap, Offset(w * 0.82f, bubbleY2), w * 0.14f, alpha = 0.85f)
                }

                InteractiveBackgroundType.CANDY_LAND -> {
                    // Left Bobbing 3D Giant Lollipop
                    val leftCenter = Offset(w * 0.14f, h * 0.78f + bounceAnim * 10f)
                    drawAsset(lollipopBitmap, leftCenter, w * 0.22f, rotation = bounceAnim * 12f)

                    // Right Bobbing 3D Giant Lollipop
                    val rightCenter = Offset(w * 0.86f, h * 0.76f - bounceAnim * 10f)
                    drawAsset(lollipopBitmap, rightCenter, w * 0.2f, rotation = -bounceAnim * 12f)
                }

                InteractiveBackgroundType.RAINBOW -> {
                    // 3D Rainbow Arc hovering at top
                    val arcCenter = Offset(w * 0.5f, h * 0.18f + bounceAnim * 8f)
                    drawAsset(rainbowArcBitmap, arcCenter, w * 0.55f)

                    // Glowing stars
                    val s1 = Offset(w * 0.15f, h * 0.22f)
                    val s2 = Offset(w * 0.85f, h * 0.22f)
                    drawAsset(starBitmap, s1, w * 0.12f, rotation = floatAnim * 180f)
                    drawAsset(starBitmap, s2, w * 0.12f, rotation = -floatAnim * 180f)
                }

                InteractiveBackgroundType.DINO -> {
                    // 3D Sauropod Dinosaur peeking bottom corner and waving
                    val dinoCenter = Offset(w * 0.22f, h * 0.85f + bounceAnim * 8f)
                    drawAsset(dinoBitmap, dinoCenter, w * 0.32f, rotation = bounceAnim * 5f)
                }

                InteractiveBackgroundType.JUNGLE -> {
                    // Floating tropical sparkles and leaf accents
                    val jPos1 = Offset(w * 0.16f, h * 0.18f + bounceAnim * 6f)
                    val jPos2 = Offset(w * 0.84f, h * 0.18f - bounceAnim * 6f)
                    drawAsset(twinklesBitmap, jPos1, w * 0.11f)
                    drawAsset(twinklesBitmap, jPos2, w * 0.11f)
                }

                InteractiveBackgroundType.NONE -> {}
            }
        }
    }
}

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
