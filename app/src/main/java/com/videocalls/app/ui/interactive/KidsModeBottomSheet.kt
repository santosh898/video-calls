package com.videocalls.app.ui.interactive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KidsModeBottomSheet(
    onDismissRequest: () -> Unit,
    currentBackground: InteractiveBackgroundType,
    onBackgroundSelected: (InteractiveBackgroundType) -> Unit,
    currentEffect: InteractiveEffectType,
    onEffectSelected: (InteractiveEffectType) -> Unit,
    currentGame: InteractiveGameType,
    onGameSelected: (InteractiveGameType) -> Unit,
    sparklesEnabled: Boolean,
    onSparklesToggled: (Boolean) -> Unit,
    toddlerLockEnabled: Boolean,
    onToddlerLockToggled: (Boolean) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = Color(0xFF1E293B),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.4f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "🧸", fontSize = 26.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Kids Fun Zone",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "👆 Tap video to cycle effects • 👉 Swipe to change scenery",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF38BDF8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Cartoon Backgrounds
            Text(
                text = "Cartoon Backgrounds:",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InteractiveBackgroundType.values().forEach { bg ->
                    val isSelected = currentBackground == bg
                    FilterButton(
                        name = bg.displayName,
                        icon = bg.icon,
                        isSelected = isSelected,
                        onClick = { onBackgroundSelected(bg) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 2. Interactive Effects
            Text(
                text = "Fun Effects:",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InteractiveEffectType.values().forEach { effect ->
                    val isSelected = currentEffect == effect
                    FilterButton(
                        name = effect.displayName,
                        icon = effect.icon,
                        isSelected = isSelected,
                        onClick = { onEffectSelected(effect) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 3. Toddler Games
            Text(
                text = "Play Games Together:",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InteractiveGameType.values().forEach { game ->
                    val isSelected = currentGame == game
                    FilterButton(
                        name = game.displayName,
                        icon = game.icon,
                        isSelected = isSelected,
                        onClick = { onGameSelected(game) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4. Sparkles & Toddler Lock Options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "✨", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Touch Sparkles & Bursts",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "Sparkle stars on tap or swipe",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
                Switch(
                    checked = sparklesEnabled,
                    onCheckedChange = onSparklesToggled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF10B981)
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🔒", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Toddler Call Lock",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "Prevents accidental back or hang up",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
                Switch(
                    checked = toddlerLockEnabled,
                    onCheckedChange = onToddlerLockToggled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFF59E0B)
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FilterButton(
    name: String,
    icon: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(
                    color = if (isSelected) Color(0xFF0284C7) else Color.White.copy(alpha = 0.1f),
                    shape = CircleShape
                )
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.2f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(text = icon, fontSize = 28.sp)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.9f)
        )
    }
}
