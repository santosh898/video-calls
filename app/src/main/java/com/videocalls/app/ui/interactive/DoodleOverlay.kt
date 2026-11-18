package com.videocalls.app.ui.interactive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class DoodleManager(
    val onStrokeFinished: (DoodleStroke) -> Unit,
    val onCleared: () -> Unit
) {
    val strokes = mutableStateListOf<DoodleStroke>()
    var currentColor by mutableStateOf(Color(0xFFFFD700)) // Gold
    var currentStrokeWidth by mutableStateOf(16f)

    private val currentPoints = mutableListOf<DoodlePoint>()

    fun startStroke(normX: Float, normY: Float) {
        currentPoints.clear()
        currentPoints.add(DoodlePoint(normX, normY))
    }

    fun addPoint(normX: Float, normY: Float) {
        currentPoints.add(DoodlePoint(normX, normY))
    }

    fun endStroke() {
        if (currentPoints.size > 1) {
            val stroke = DoodleStroke(
                color = currentColor.value.toLong(),
                strokeWidth = currentStrokeWidth,
                points = currentPoints.toList()
            )
            strokes.add(stroke)
            onStrokeFinished(stroke)
        }
        currentPoints.clear()
    }

    fun addRemoteStroke(stroke: DoodleStroke) {
        strokes.add(stroke)
    }

    fun clear() {
        strokes.clear()
        onCleared()
    }
}

@Composable
fun DoodleOverlay(
    modifier: Modifier = Modifier,
    manager: DoodleManager
) {
    val palette = listOf(
        Color(0xFFFFD700), // Gold
        Color(0xFFFF1493), // Deep Pink
        Color(0xFF00FFFF), // Cyan
        Color(0xFF39FF14), // Neon Lime
        Color(0xFFFF4500)  // Orange
    )

    Box(modifier = modifier.fillMaxSize()) {
        // Drawing Surface
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val normX = (offset.x / size.width).coerceIn(0f, 1f)
                            val normY = (offset.y / size.height).coerceIn(0f, 1f)
                            manager.startStroke(normX, normY)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val normX = (change.position.x / size.width).coerceIn(0f, 1f)
                            val normY = (change.position.y / size.height).coerceIn(0f, 1f)
                            manager.addPoint(normX, normY)
                        },
                        onDragEnd = {
                            manager.endStroke()
                        },
                        onDragCancel = {
                            manager.endStroke()
                        }
                    )
                }
        ) {
            val w = size.width
            val h = size.height

            for (stroke in manager.strokes) {
                if (stroke.points.size < 2) continue
                val path = Path()
                val p0 = stroke.points.first()
                path.moveTo(p0.x * w, p0.y * h)
                for (i in 1 until stroke.points.size) {
                    val p = stroke.points[i]
                    path.lineTo(p.x * w, p.y * h)
                }
                drawPath(
                    path = path,
                    color = Color(stroke.color.toULong()),
                    style = Stroke(
                        width = stroke.strokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }

        // Color Picker & Clear Button Bar at Top
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1E293B).copy(alpha = 0.9f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                palette.forEach { color ->
                    val isSelected = manager.currentColor == color
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(color, CircleShape)
                            .border(
                                width = if (isSelected) 3.dp else 0.dp,
                                color = if (isSelected) Color.White else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { manager.currentColor = color }
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Clear button
                IconButton(
                    onClick = { manager.clear() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Clear Doodle",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
