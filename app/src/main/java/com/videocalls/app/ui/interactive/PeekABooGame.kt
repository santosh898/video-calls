package com.videocalls.app.ui.interactive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PeekABooOverlay(
    modifier: Modifier = Modifier,
    isOpen: Boolean,
    onToggle: (Boolean) -> Unit,
    particleManager: ParticleManager,
    onExit: (() -> Unit)? = null
) {
    val curtainProgress by animateFloatAsState(
        targetValue = if (isOpen) 0f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 200f),
        label = "curtain"
    )

    var showBanner by remember { mutableStateOf(false) }

    LaunchedEffect(isOpen) {
        if (isOpen) {
            showBanner = true
            KidsSoundManager.playPeekABoo()
            kotlinx.coroutines.delay(2200L)
            showBanner = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                val nextState = !isOpen
                onToggle(nextState)
                if (nextState) {
                    // Tapped to open: burst particles
                    particleManager.burst(500f, 600f, 25)
                }
            }
    ) {
        // Red Velvet Curtains drawn on Canvas
        if (curtainProgress > 0.01f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val curtainWidth = (w / 2f) * curtainProgress

                val curtainBrushLeft = Brush.horizontalGradient(
                    colors = listOf(Color(0xFF8B0000), Color(0xFFDC143C), Color(0xFF800000)),
                    startX = 0f,
                    endX = curtainWidth
                )
                val curtainBrushRight = Brush.horizontalGradient(
                    colors = listOf(Color(0xFF800000), Color(0xFFDC143C), Color(0xFF8B0000)),
                    startX = w - curtainWidth,
                    endX = w
                )

                // Left curtain
                drawRect(
                    brush = curtainBrushLeft,
                    topLeft = Offset(0f, 0f),
                    size = Size(curtainWidth, h)
                )
                // Gold fringe on left curtain edge
                drawRect(
                    color = Color(0xFFFFD700),
                    topLeft = Offset(curtainWidth - 8f, 0f),
                    size = Size(8f, h)
                )

                // Right curtain
                drawRect(
                    brush = curtainBrushRight,
                    topLeft = Offset(w - curtainWidth, 0f),
                    size = Size(curtainWidth, h)
                )
                // Gold fringe on right curtain edge
                drawRect(
                    color = Color(0xFFFFD700),
                    topLeft = Offset(w - curtainWidth, 0f),
                    size = Size(8f, h)
                )
            }
        }

        // Tap Button in Center when closed
        if (!isOpen) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = Color(0xFFFFD700),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clickable {
                        onToggle(true)
                        particleManager.burst(500f, 600f, 30)
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🙈", fontSize = 28.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "TAP TO PEEK!",
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF1E1E1E),
                        fontSize = 18.sp
                    )
                }
            }
        }

        // Cheerful "PEEK-A-BOO!" Banner when opened
        AnimatedVisibility(
            visible = showBanner,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFFEC4899),
                shadowElevation = 10.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🎉", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "PEEK-A-BOO! 😃",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp
                    )
                }
            }
        }

        // Top Exit Game Button
        if (onExit != null) {
            Surface(
                onClick = onExit,
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E293B).copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🙈 Peek-a-boo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = "✕ Exit", color = Color(0xFFF87171), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}
