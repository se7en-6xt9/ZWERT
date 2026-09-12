package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

data class GlassTabItem(
    val id: Int,
    val icon: ImageVector,
    val label: String,
    val isCenterAction: Boolean = false,
    val onClick: () -> Unit
)

/**
 * Floating iOS-style Glassmorphic Navigation Bar with Jelly/Water-Drop Morphing Selection Indicator.
 */
@Composable
fun FloatingGlassNavBar(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onNavigateSchedule: () -> Unit,
    onNavigateAIImport: () -> Unit,
    onNavigateAddClass: () -> Unit,
    onNavigateManageClasses: () -> Unit,
    onNavigateProfile: () -> Unit,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    val tabs = remember {
        listOf(
            GlassTabItem(0, Icons.Default.Event, "Schedule", onClick = onNavigateSchedule),
            GlassTabItem(1, Icons.Default.AutoAwesome, "AI Import", onClick = onNavigateAIImport),
            GlassTabItem(2, Icons.Default.Add, "Add Class", isCenterAction = true, onClick = onNavigateAddClass),
            GlassTabItem(3, Icons.Default.Create, "Manage", onClick = onNavigateManageClasses),
            GlassTabItem(4, Icons.Default.Person, "Profile", onClick = onNavigateProfile)
        )
    }

    // Coordinates of each tab center for the morphing water-drop indicator
    val tabCenters = remember { mutableStateMapOf<Int, Float>() }
    val tabWidths = remember { mutableStateMapOf<Int, Float>() }

    // State for the jelly stretch/squash morphing effect
    var activeTab by remember { mutableIntStateOf(selectedTabIndex) }
    var previousTab by remember { mutableIntStateOf(selectedTabIndex) }
    var isMorphing by remember { mutableStateOf(false) }

    LaunchedEffect(selectedTabIndex) {
        if (selectedTabIndex != activeTab) {
            previousTab = activeTab
            activeTab = selectedTabIndex
            isMorphing = true
            delay(220)
            isMorphing = false
        }
    }

    // Morph stretch along horizontal axis during travel
    val morphStretchX by animateFloatAsState(
        targetValue = if (isMorphing) 1.35f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "morphStretchX"
    )

    // Morph squash along vertical axis during travel to preserve perceived volume
    val morphStretchY by animateFloatAsState(
        targetValue = if (isMorphing) 0.82f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "morphStretchY"
    )

    // Animated target center position with liquid spring damping
    val targetCenterX = tabCenters[activeTab] ?: 0f
    val animatedCenterX by animateFloatAsState(
        targetValue = targetCenterX,
        animationSpec = spring(
            dampingRatio = 0.65f, // Liquid spring
            stiffness = Spring.StiffnessLow
        ),
        label = "liquidIndicatorCenterX"
    )

    // Glass styling colors
    val glassBg = if (isDarkTheme) {
        Color(0xFF16161E).copy(alpha = 0.74f)
    } else {
        Color(0xFFFFFFFF).copy(alpha = 0.78f)
    }

    val glassBorderColor = if (isDarkTheme) {
        Color.White.copy(alpha = 0.14f)
    } else {
        Color.White.copy(alpha = 0.75f)
    }

    val accentGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF6366F1), // Indigo
            Color(0xFF8B5CF6), // Violet
            Color(0xFF7C3AED)  // Purple
        )
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        // Floating Capsule Glass Shell
        Surface(
            modifier = Modifier
                .shadow(
                    elevation = 20.dp,
                    shape = RoundedCornerShape(36.dp),
                    spotColor = if (isDarkTheme) Color(0xFF6366F1).copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.18f),
                    ambientColor = Color.Black.copy(alpha = 0.14f)
                ),
            shape = RoundedCornerShape(36.dp),
            color = glassBg,
            border = BorderStroke(1.2.dp, glassBorderColor)
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .height(60.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                // Jelly / Water-Drop Selection Indicator (Organic morphing blob)
                if (tabCenters.isNotEmpty() && animatedCenterX > 0f) {
                    val currentBlobWidth = tabWidths[activeTab] ?: 48.dp.value
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                translationX = animatedCenterX - (currentBlobWidth * 0.5f)
                                scaleX = morphStretchX
                                scaleY = morphStretchY
                            }
                            .size(width = currentBlobWidth.dp, height = 44.dp)
                            .shadow(
                                elevation = 10.dp,
                                shape = RoundedCornerShape(22.dp),
                                spotColor = Color(0xFF6366F1).copy(alpha = 0.60f),
                                ambientColor = Color(0xFF8B5CF6).copy(alpha = 0.35f)
                            )
                            .clip(RoundedCornerShape(22.dp))
                            .background(accentGradient)
                    )
                }

                // Row of Icon Button Objects
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEach { tab ->
                        val isSelected = activeTab == tab.id
                        GlassTabButton(
                            item = tab,
                            isSelected = isSelected,
                            isDarkTheme = isDarkTheme,
                            onPositioned = { centerX, width ->
                                tabCenters[tab.id] = centerX
                                tabWidths[tab.id] = width
                            },
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onTabSelected(tab.id)
                                tab.onClick()
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual rounded button object in the glass bar.
 */
@Composable
private fun GlassTabButton(
    item: GlassTabItem,
    isSelected: Boolean,
    isDarkTheme: Boolean,
    onPositioned: (centerX: Float, width: Float) -> Unit,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Spring scale bounce on press
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.86f else if (isSelected) 1.05f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tabBtnScale"
    )

    val contentColor = when {
        isSelected -> Color.White
        item.isCenterAction -> if (isDarkTheme) Color(0xFFA5B4FC) else Color(0xFF4F46E5)
        isDarkTheme -> Color(0xFF94A3B8)
        else -> Color(0xFF64748B)
    }

    val buttonWidth = if (item.isCenterAction) 52.dp else 46.dp
    val buttonHeight = if (item.isCenterAction) 50.dp else 44.dp

    Box(
        modifier = Modifier
            .width(buttonWidth)
            .height(buttonHeight)
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInParent()
                onPositioned(bounds.center.x, bounds.width)
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(18.dp))
            // Subtle discrete bounded container background for non-selected items
            .background(
                if (item.isCenterAction && !isSelected) {
                    if (isDarkTheme) Color(0xFF6366F1).copy(alpha = 0.22f) else Color(0xFF6366F1).copy(alpha = 0.12f)
                } else if (!isSelected) {
                    if (isDarkTheme) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f)
                } else {
                    Color.Transparent
                }
            )
            .border(
                BorderStroke(
                    width = 1.dp,
                    color = if (item.isCenterAction && !isSelected) {
                        Color(0xFF6366F1).copy(alpha = 0.40f)
                    } else if (!isSelected) {
                        if (isDarkTheme) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.05f)
                    } else {
                        Color.Transparent
                    }
                ),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = contentColor,
            modifier = Modifier.size(if (item.isCenterAction) 24.dp else 22.dp)
        )
    }
}
