package com.example.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.data.AttendanceRecordEntity
import com.example.data.CourseEntity
import com.example.data.ScheduleSlotEntity
import com.example.viewmodel.MainViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentSubjectDetailScreen(
    navController: NavController,
    viewModel: MainViewModel,
    courseId: String
) {
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val courses by viewModel.getAllCourses().collectAsState(initial = emptyList())
    val course = courses.find { it.id == courseId } ?: CourseEntity(
        id = courseId,
        name = "Subject Details",
        code = "COURSE",
        credits = 4
    )
    val allSlots by viewModel.getScheduleSlotsForCourse(courseId).collectAsState(initial = emptyList())
    val allAttendance by viewModel.getAllAttendance().collectAsState(initial = emptyList())

    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    // Tone generator for attendance correction feedback
    val toneGenerator = remember {
        try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        } catch (_: Exception) {
            null
        }
    }

    // Filter attendance records for this course only
    val subjectAttendance = remember(allAttendance, courseId, allSlots) {
        allAttendance.filter { rec ->
            rec.studentId == "self" && (
                allSlots.any { s -> s.id == rec.scheduleSlotId } ||
                rec.scheduleSlotId.contains(courseId)
            )
        }.sortedByDescending { it.date }
    }

    val presentCount = subjectAttendance.count { it.status.equals("P", ignoreCase = true) || it.status.equals("present", ignoreCase = true) }
    val absentCount = subjectAttendance.count { it.status.equals("A", ignoreCase = true) || it.status.equals("absent", ignoreCase = true) }
    val totalHeld = subjectAttendance.size
    val attendancePct = if (totalHeld > 0) (presentCount * 100f) / totalHeld else 0f

    // Color code: green (>=75%), amber (50-74.9%), red (<50%)
    val statusColor = when {
        totalHeld == 0 -> Color(0xFF6B7280)
        attendancePct >= 75f -> Color(0xFF10B981)
        attendancePct >= 50f -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }

    // Interactive correction dialog state
    var recordToEdit by remember { mutableStateOf<AttendanceRecordEntity?>(null) }

    // Filter mode for session history: ALL, PRESENT, ABSENT
    var selectedFilter by remember { mutableStateOf("ALL") }
    val filteredRecords = remember(subjectAttendance, selectedFilter) {
        when (selectedFilter) {
            "PRESENT" -> subjectAttendance.filter { it.status.equals("P", ignoreCase = true) || it.status.equals("present", ignoreCase = true) }
            "ABSENT" -> subjectAttendance.filter { it.status.equals("A", ignoreCase = true) || it.status.equals("absent", ignoreCase = true) }
            else -> subjectAttendance
        }
    }

    // Calculate Monthly breakdown
    val monthlyData = remember(subjectAttendance) {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val groups = subjectAttendance.groupBy { rec ->
            try {
                val parsed = LocalDate.parse(rec.date, formatter)
                YearMonth.from(parsed)
            } catch (_: Exception) {
                YearMonth.now()
            }
        }
        groups.entries.sortedByDescending { it.key }.map { entry ->
            val monthP = entry.value.count { it.status.equals("P", ignoreCase = true) || it.status.equals("present", ignoreCase = true) }
            val monthTotal = entry.value.size
            val monthPct = if (monthTotal > 0) (monthP * 100f) / monthTotal else 0f
            Triple(entry.key, monthPct, "$monthP/$monthTotal")
        }
    }

    // Attendance projection calculation (bunk/attend planner)
    val projectionInfo = remember(totalHeld, presentCount, attendancePct) {
        if (totalHeld == 0) {
            ProjectionResult(
                headline = "No classes recorded yet",
                detail = "Attendance requirements will be calculated once your first session is held.",
                icon = Icons.Default.Lightbulb,
                color = Color(0xFF6B7280),
                isSafe = true
            )
        } else if (attendancePct < 75f) {
            // Formula: (present + k) / (total + k) >= 0.75 => k >= 3*total - 4*present
            val needed = ceil((3.0 * totalHeld - 4.0 * presentCount)).toInt().coerceAtLeast(1)
            ProjectionResult(
                headline = "Attendance Deficit • Urgent",
                detail = "You must attend the next $needed consecutive class${if (needed > 1) "es" else ""} without absence to reach the 75% eligibility mark.",
                icon = Icons.Default.PriorityHigh,
                color = Color(0xFFEF4444),
                isSafe = false
            )
        } else {
            // Formula: present / (total + m) >= 0.75 => 0.75 * m <= present - 0.75 * total => m <= (4*present - 3*total)/3
            val canMiss = floor((4.0 * presentCount - 3.0 * totalHeld) / 3.0).toInt().coerceAtLeast(0)
            if (canMiss > 0) {
                ProjectionResult(
                    headline = "Attendance In Good Standing",
                    detail = "You can safely miss up to $canMiss class${if (canMiss > 1) "es" else ""} without falling below the 75% minimum threshold.",
                    icon = Icons.Default.CheckCircle,
                    color = Color(0xFF10B981),
                    isSafe = true
                )
            } else {
                ProjectionResult(
                    headline = "On The 75% Boundary",
                    detail = "You are currently meeting the 75% mark. Do not miss the next class to maintain your examination eligibility.",
                    icon = Icons.Default.Warning,
                    color = Color(0xFFF59E0B),
                    isSafe = true
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = course.name,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val code = course.code.ifBlank { "Subject" }
                        val credits = if (course.credits > 0) " • ${course.credits} Credits" else ""
                        Text(
                            text = "$code$credits",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            navController.popBackStack()
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
            }

            // 1. HERO STAT BLOCK
            item {
                SubjectHeroStatCard(
                    attendancePct = attendancePct,
                    totalHeld = totalHeld,
                    presentCount = presentCount,
                    absentCount = absentCount,
                    statusColor = statusColor,
                    isDarkTheme = isDarkTheme
                )
            }

            // 2. SMART PROJECTION BANNER
            item {
                SubjectProjectionCard(
                    projection = projectionInfo,
                    isDarkTheme = isDarkTheme
                )
            }

            // 3. MONTHLY SUMMARY BREAKDOWN (if multiple months or data exists)
            if (monthlyData.isNotEmpty()) {
                item {
                    SubjectMonthlyBreakdownCard(
                        monthlyData = monthlyData,
                        isDarkTheme = isDarkTheme
                    )
                }
            }

            // 4. WEEKLY SCHEDULE SUMMARY CHIP ROW
            if (allSlots.isNotEmpty()) {
                item {
                    SubjectScheduleSlotsRow(
                        slots = allSlots,
                        isDarkTheme = isDarkTheme
                    )
                }
            }

            // 5. SESSION HISTORY LOG HEADER & FILTERS
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Session History",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${filteredRecords.size} recorded",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Filter chips: All, Present, Absent
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = selectedFilter == "ALL",
                            onClick = { selectedFilter = "ALL" },
                            label = { Text("All (${subjectAttendance.size})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                        FilterChip(
                            selected = selectedFilter == "PRESENT",
                            onClick = { selectedFilter = "PRESENT" },
                            label = { Text("Present ($presentCount)") },
                            leadingIcon = {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF10B981).copy(alpha = 0.2f),
                                selectedLabelColor = Color(0xFF047857)
                            )
                        )
                        FilterChip(
                            selected = selectedFilter == "ABSENT",
                            onClick = { selectedFilter = "ABSENT" },
                            label = { Text("Absent ($absentCount)") },
                            leadingIcon = {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFEF4444).copy(alpha = 0.2f),
                                selectedLabelColor = Color(0xFFB91C1C)
                            )
                        )
                    }
                }
            }

            // 6. SESSION HISTORY LIST
            if (filteredRecords.isEmpty()) {
                item {
                    SubjectEmptyHistoryCard(
                        totalHeld = totalHeld,
                        filter = selectedFilter,
                        isDarkTheme = isDarkTheme
                    )
                }
            } else {
                items(filteredRecords, key = { "${it.date}_${it.scheduleSlotId}" }) { record ->
                    val slot = allSlots.find { it.id == record.scheduleSlotId }
                    SessionHistoryItem(
                        record = record,
                        slot = slot,
                        isDarkTheme = isDarkTheme,
                        onTap = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            recordToEdit = record
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Interactive Edit Attendance Dialog
    recordToEdit?.let { record ->
        val isCurrentPresent = record.status.equals("P", ignoreCase = true) || record.status.equals("present", ignoreCase = true)
        AlertDialog(
            onDismissRequest = { recordToEdit = null },
            title = {
                Text(
                    text = "Update Attendance",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Session on ${record.date}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Currently marked: ${if (isCurrentPresent) "Present (P)" else "Absent (A)"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Select corrected status:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                try {
                                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
                                } catch (_: Exception) {}
                                viewModel.updateStudentAttendanceStatus(
                                    date = record.date,
                                    slotId = record.scheduleSlotId,
                                    courseId = courseId,
                                    newStatus = "P"
                                )
                                recordToEdit = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Present")
                        }

                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                try {
                                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 80)
                                } catch (_: Exception) {}
                                viewModel.updateStudentAttendanceStatus(
                                    date = record.date,
                                    slotId = record.scheduleSlotId,
                                    courseId = courseId,
                                    newStatus = "A"
                                )
                                recordToEdit = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFEF4444),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Absent")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAttendance(
                            date = record.date,
                            slotId = record.scheduleSlotId,
                            studentId = "self"
                        )
                        recordToEdit = null
                    }
                ) {
                    Text("Clear Entry", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }
}

data class ProjectionResult(
    val headline: String,
    val detail: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color,
    val isSafe: Boolean
)

@Composable
fun SubjectHeroStatCard(
    attendancePct: Float,
    totalHeld: Int,
    presentCount: Int,
    absentCount: Int,
    statusColor: Color,
    isDarkTheme: Boolean
) {
    val animatedPct by animateFloatAsState(
        targetValue = attendancePct,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "animatedPct"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = statusColor.copy(alpha = 0.25f),
                ambientColor = statusColor.copy(alpha = 0.12f)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White
        ),
        border = BorderStroke(
            1.dp,
            if (isDarkTheme) Color(0xFF334155) else statusColor.copy(alpha = 0.25f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (totalHeld > 0) "${String.format(Locale.ENGLISH, "%.1f", animatedPct)}%" else "—",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Black,
                            color = statusColor
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = statusColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = when {
                                totalHeld == 0 -> "No sessions yet"
                                attendancePct >= 75f -> "Eligible (≥75%)"
                                attendancePct >= 50f -> "Borderline (50-75%)"
                                else -> "At Risk (<50%)"
                            },
                            color = statusColor,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Prominent Circular Progress Ring
                Box(
                    modifier = Modifier.size(76.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { if (totalHeld > 0) (animatedPct / 100f).coerceIn(0f, 1f) else 0f },
                        modifier = Modifier.size(76.dp),
                        strokeWidth = 7.dp,
                        color = statusColor,
                        trackColor = if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0)
                    )
                    Icon(
                        imageVector = when {
                            totalHeld == 0 -> Icons.Default.Event
                            attendancePct >= 75f -> Icons.Default.Check
                            else -> Icons.Default.PriorityHigh
                        },
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider(color = if (isDarkTheme) Color(0xFF334155) else Color(0xFFF1F5F9))
            Spacer(modifier = Modifier.height(16.dp))

            // 3-Metric Breakdown Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                SubjectMetricCell(
                    label = "Present",
                    value = "$presentCount",
                    color = Color(0xFF10B981),
                    icon = Icons.Default.CheckCircle
                )
                SubjectMetricCell(
                    label = "Absent",
                    value = "$absentCount",
                    color = Color(0xFFEF4444),
                    icon = Icons.Default.EventBusy
                )
                SubjectMetricCell(
                    label = "Total Held",
                    value = "$totalHeld",
                    color = MaterialTheme.colorScheme.onSurface,
                    icon = Icons.Default.CalendarMonth
                )
            }
        }
    }
}

@Composable
fun SubjectMetricCell(
    label: String,
    value: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = color
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = value,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                color = color
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SubjectProjectionCard(
    projection: ProjectionResult,
    isDarkTheme: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDarkTheme) Color(0xFF1E293B) else projection.color.copy(alpha = 0.08f)
        ),
        border = BorderStroke(1.dp, projection.color.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(projection.color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = projection.icon,
                    contentDescription = null,
                    tint = projection.color,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = projection.headline,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = projection.color
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = projection.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
fun SubjectMonthlyBreakdownCard(
    monthlyData: List<Triple<YearMonth, Float, String>>,
    isDarkTheme: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White
        ),
        border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Monthly Performance",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    Icons.Default.TrendingUp,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            monthlyData.forEach { (yearMonth, pct, ratio) ->
                val monthName = yearMonth.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                val mColor = if (pct >= 75f) Color(0xFF10B981) else if (pct >= 50f) Color(0xFFF59E0B) else Color(0xFFEF4444)

                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$monthName ${yearMonth.year}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "($ratio)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${pct.toInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = mColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = { (pct / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = mColor,
                        trackColor = if (isDarkTheme) Color(0xFF334155) else Color(0xFFF1F5F9)
                    )
                }
            }
        }
    }
}

@Composable
fun SubjectScheduleSlotsRow(
    slots: List<ScheduleSlotEntity>,
    isDarkTheme: Boolean
) {
    Column {
        Text(
            text = "Weekly Schedule Slots",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(slots) { slot ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFEEF2FF),
                    border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFC7D2FE))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "${slot.dayOfWeek.take(3)} • ${slot.startTime}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (slot.room.isNotBlank()) {
                                Text(
                                    text = slot.room,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SessionHistoryItem(
    record: AttendanceRecordEntity,
    slot: ScheduleSlotEntity?,
    isDarkTheme: Boolean,
    onTap: () -> Unit
) {
    val isPresent = record.status.equals("P", ignoreCase = true) || record.status.equals("present", ignoreCase = true)
    val itemColor = if (isPresent) Color(0xFF10B981) else Color(0xFFEF4444)

    val dateFormatted = remember(record.date) {
        try {
            val d = LocalDate.parse(record.date)
            val dayName = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
            val monthName = d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
            "$dayName, ${d.dayOfMonth} $monthName ${d.year}"
        } catch (_: Exception) {
            record.date
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White
        ),
        border = BorderStroke(
            1.dp,
            if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(itemColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPresent) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null,
                        tint = itemColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = dateFormatted,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val slotTime = slot?.let { "${it.startTime} - ${it.endTime}" } ?: "Recorded Session"
                    Text(
                        text = "$slotTime • Tap to edit",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = itemColor.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, itemColor.copy(alpha = 0.35f))
            ) {
                Text(
                    text = if (isPresent) "PRESENT" else "ABSENT",
                    color = itemColor,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
fun SubjectEmptyHistoryCard(
    totalHeld: Int,
    filter: String,
    isDarkTheme: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFF8FAFC)
        ),
        border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Event,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = if (totalHeld == 0) "No Sessions Recorded" else "No $filter Sessions",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (totalHeld == 0)
                    "Mark your presence from the Student Dashboard when attending scheduled lectures."
                else
                    "Try selecting a different filter above to view your attendance history.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
