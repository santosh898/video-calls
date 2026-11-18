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
    currentFilter: InteractiveFilterType,
    onFilterSelected: (InteractiveFilterType) -> Unit,
    currentGame: InteractiveGameType,
    onGameSelected: (InteractiveGameType) -> Unit,
    target: FilterTarget,
    onTargetChanged: (FilterTarget) -> Unit,
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
                Text(
                    text = "Kids Fun Zone",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Target Selector: "Apply To:"
            Text(
                text = "Apply Filter To:",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TargetChoiceChip(
                    title = "Grandparent / Friend (Incoming)",
                    icon = "📺",
                    isSelected = target == FilterTarget.REMOTE,
                    onClick = { onTargetChanged(FilterTarget.REMOTE) },
                    modifier = Modifier.weight(1f)
                )
                TargetChoiceChip(
                    title = "Me (Self)",
                    icon = "🤳",
                    isSelected = target == FilterTarget.LOCAL,
                    onClick = { onTargetChanged(FilterTarget.LOCAL) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 2. Interactive Face Filters
            Text(
                text = "Face Filters:",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InteractiveFilterType.values().forEach { filter ->
                    val isSelected = currentFilter == filter
                    FilterButton(
                        name = filter.displayName,
                        icon = filter.icon,
                        isSelected = isSelected,
                        onClick = { onFilterSelected(filter) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 3. Toddler Games
            Text(
                text = "Play Games Together:",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.7f)
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

            Spacer(modifier = Modifier.height(10.dp))

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
                            text = "Prevent accidental call hang-up",
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
                        checkedTrackColor = Color(0xFF10B981)
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TargetChoiceChip(
    title: String,
    icon: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) Color(0xFF0284C7).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF38BDF8)) else null,
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(text = icon, fontSize = 20.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Color(0xFF38BDF8) else Color.White
            )
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
                    if (isSelected) Color(0xFF0284C7).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.1f),
                    CircleShape
                )
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.2f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(text = icon, fontSize = 26.sp)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.8f)
        )
    }
}
