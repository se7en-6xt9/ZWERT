package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

data class DockItemData(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit
)

@Composable
fun MagnificationDock(
    items: List<DockItemData>,
    modifier: Modifier = Modifier,
    baseItemSize: Dp = 50.dp,
    magnification: Dp = 80.dp,
    distance: Dp = 200.dp
) {
    var touchX by remember { mutableStateOf<Float?>(null) }
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier
                .shadow(
                    elevation = 16.dp,
                    shape = CircleShape,
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                )
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                    shape = CircleShape
                )
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        touchX = down.position.x
                        
                        do {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            val change = event.changes.firstOrNull()
                            if (change != null) {
                                if (change.pressed) {
                                    touchX = change.position.x
                                } else {
                                    touchX = null
                                }
                            }
                        } while (event.changes.any { it.pressed })
                        
                        touchX = null
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            items.forEach { item ->
                DockItem(
                    item = item,
                    touchX = touchX,
                    baseItemSize = baseItemSize,
                    magnification = magnification,
                    distance = distance
                )
            }
        }
    }
}

@Composable
fun DockItem(
    item: DockItemData,
    touchX: Float?,
    baseItemSize: Dp,
    magnification: Dp,
    distance: Dp
) {
    var itemCenterX by remember { mutableStateOf(0f) }
    
    val density = LocalDensity.current
    val distancePx = with(density) { distance.toPx() }
    
    val targetSize = remember(touchX, itemCenterX, distancePx, baseItemSize, magnification) {
        if (touchX != null && itemCenterX != 0f) {
            val distanceFromTouch = abs(touchX - itemCenterX)
            if (distanceFromTouch < distancePx) {
                val factor = 1f - (distanceFromTouch / distancePx)
                baseItemSize + (magnification - baseItemSize) * factor
            } else {
                baseItemSize
            }
        } else {
            baseItemSize
        }
    }

    val animatedSize by animateDpAsState(
        targetValue = targetSize,
        animationSpec = spring(
            dampingRatio = 0.6f, // Similar to mass: 0.1, stiffness: 150, damping: 12
            stiffness = Spring.StiffnessLow
        ),
        label = "DockItemSize"
    )

    val isHovered = targetSize > baseItemSize + (magnification - baseItemSize) * 0.5f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
        modifier = Modifier.onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInParent()
            itemCenterX = bounds.center.x
        }
    ) {
        // Space for tooltip
        Box(
            modifier = Modifier.height(30.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = isHovered && touchX != null,
                enter = fadeIn() + slideInVertically(initialOffsetY = { 20 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { 20 })
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(animatedSize)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null, 
                    onClick = item.onClick
                )
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(animatedSize * 0.5f)
            )
        }
    }
}
