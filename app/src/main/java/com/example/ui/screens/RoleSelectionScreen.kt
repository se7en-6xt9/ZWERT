package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material3.*
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.viewmodel.MainViewModel

@Composable
fun RoleSelectionScreen(
    navController: NavController,
    viewModel: MainViewModel
) {
    val haptic = LocalHapticFeedback.current
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    var selectedRole by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val bgGradient = if (isDarkTheme) {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF0F172A), Color(0xFF1E1B4B), Color(0xFF0F172A))
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(Color(0xFFF8FAFC), Color(0xFFEEF2FF), Color(0xFFF1F5F9))
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 500.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Badge / Icon
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                        )
                    )
                    .shadow(12.dp, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = "Attentis",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Welcome to Attentis",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (isDarkTheme) Color.White else Color(0xFF0F172A)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Select how you will be using the application. Your role can also be customized later in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B),
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Role Card: Teacher
            RoleOptionCard(
                title = "Continue as Teacher",
                description = "Manage batches, import timetables with AI, track student attendance, and generate reports.",
                icon = Icons.Default.SupervisorAccount,
                accentColor = Color(0xFF6366F1),
                isSelected = selectedRole == "teacher",
                isDarkTheme = isDarkTheme,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    selectedRole = "teacher"
                }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Role Card: Student
            RoleOptionCard(
                title = "Continue as Student",
                description = "View your enrolled schedule, mark self-attendance during class, and monitor your course percentages.",
                icon = Icons.Default.School,
                accentColor = Color(0xFF10B981),
                isSelected = selectedRole == "student",
                isDarkTheme = isDarkTheme,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    selectedRole = "student"
                }
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Continue Button
            Button(
                onClick = {
                    val role = selectedRole ?: return@Button
                    isSaving = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.setUserRole(role) {
                        isSaving = false
                        navController.navigate("profile_setup") {
                            popUpTo("role_selection") { inclusive = true }
                        }
                    }
                },
                enabled = selectedRole != null && !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6366F1),
                    disabledContainerColor = if (isDarkTheme) Color(0xFF334155) else Color(0xFFCBD5E1)
                )
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Continue",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleOptionCard(
    title: String,
    description: String,
    icon: ImageVector,
    accentColor: Color,
    isSelected: Boolean,
    isDarkTheme: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else if (isSelected) 1.02f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "roleCardScale"
    )

    val cardBg = if (isDarkTheme) {
        if (isSelected) accentColor.copy(alpha = 0.18f) else Color(0xFF1E293B).copy(alpha = 0.85f)
    } else {
        if (isSelected) accentColor.copy(alpha = 0.08f) else Color.White
    }

    val borderColor = if (isSelected) {
        accentColor
    } else {
        if (isDarkTheme) Color.White.copy(alpha = 0.1f) else Color(0xFFE2E8F0)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(22.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(22.dp),
        color = cardBg,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        shadowElevation = if (isSelected) 8.dp else 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(accentColor.copy(alpha = if (isSelected) 0.25f else 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDarkTheme) Color.White else Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B),
                    lineHeight = 16.sp
                )
            }

            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = accentColor,
                    unselectedColor = if (isDarkTheme) Color(0xFF64748B) else Color(0xFFCBD5E1)
                )
            )
        }
    }
}
