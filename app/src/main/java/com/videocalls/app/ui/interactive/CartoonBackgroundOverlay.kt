package com.videocalls.app.ui.interactive

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.videocalls.app.R

@Composable
fun CartoonBackgroundOverlay(
    modifier: Modifier = Modifier,
    backgroundType: InteractiveBackgroundType,
    maskBitmap: Bitmap? = null
) {
    if (backgroundType == InteractiveBackgroundType.NONE) return

    val bgResId = when (backgroundType) {
        InteractiveBackgroundType.SPACE -> R.drawable.bg_space
        InteractiveBackgroundType.UNDERWATER -> R.drawable.bg_underwater
        InteractiveBackgroundType.SUNNY_BEACH -> R.drawable.bg_sunny_beach
        InteractiveBackgroundType.CYBER_CITY -> R.drawable.bg_cyber_city
        InteractiveBackgroundType.COZY_ROOM -> R.drawable.bg_cozy_room
        InteractiveBackgroundType.FANTASY_CASTLE -> R.drawable.bg_fantasy
        InteractiveBackgroundType.CANDY_LAND -> R.drawable.bg_candy
        InteractiveBackgroundType.RAINBOW -> R.drawable.bg_rainbow
        InteractiveBackgroundType.NONE -> null
    } ?: return

    val bgImageBitmap = ImageBitmap.imageResource(id = bgResId)

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0 || h <= 0) return@Canvas

            // 1. Draw portrait cartoon background image nicely framed without aggressive zoom
            val bgScale = maxOf(w / bgImageBitmap.width.toFloat(), h / bgImageBitmap.height.toFloat())
            val bgW = (bgImageBitmap.width * bgScale).toInt()
            val bgH = (bgImageBitmap.height * bgScale).toInt()
            val bgOffX = ((w - bgW) / 2f).toInt()
            val bgOffY = if (bgH > h) ((h - bgH) * 0.25f).toInt() else ((h - bgH) / 2f).toInt()

            drawImage(
                image = bgImageBitmap,
                dstOffset = IntOffset(bgOffX, bgOffY),
                dstSize = IntSize(bgW, bgH),
                filterQuality = FilterQuality.Medium,
                alpha = if (maskBitmap != null) 1.0f else 0.6f
            )

            // 2. Punch out person cutout using DstOut with the ML Kit Segmentation Mask (like GMeet / Teams)
            if (maskBitmap != null && !maskBitmap.isRecycled) {
                val maskImg = maskBitmap.asImageBitmap()
                val mScale = maxOf(w / maskImg.width.toFloat(), h / maskImg.height.toFloat())
                val mW = (maskImg.width * mScale).toInt()
                val mH = (maskImg.height * mScale).toInt()
                val mOffX = ((w - mW) / 2f).toInt()
                val mOffY = ((h - mH) / 2f).toInt()

                drawImage(
                    image = maskImg,
                    dstOffset = IntOffset(mOffX, mOffY),
                    dstSize = IntSize(mW, mH),
                    blendMode = BlendMode.DstOut,
                    filterQuality = FilterQuality.Medium
                )
            }
        }
    }
}
