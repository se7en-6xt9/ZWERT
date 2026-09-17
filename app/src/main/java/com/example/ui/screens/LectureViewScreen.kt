package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.data.CourseEntity
import com.example.data.ScheduleSlotEntity
import com.example.data.StudentEntity
import com.example.ui.util.SoundFeedbackHelper
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

enum class AttendanceMode {
    LIST, CARD
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LectureViewScreen(navController: NavController, viewModel: MainViewModel, slotId: String) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var slot by remember { mutableStateOf<ScheduleSlotEntity?>(null) }
    var course by remember { mutableStateOf<CourseEntity?>(null) }
    var students by remember { mutableStateOf<List<StudentEntity>>(emptyList()) }
    val currentDate = remember { LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) }

    // Session-level local attendance map: studentId -> "P" | "A" | "L" | "NONE"
    val localAttendance = remember { mutableStateMapOf<String, String>() }
    var initialLoaded by remember { mutableStateOf(false) }
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    var attendanceMode by remember { mutableStateOf(AttendanceMode.LIST) }
    var cardIndex by remember { mutableIntStateOf(0) }
    var isSoundEnabled by remember { mutableStateOf(SoundFeedbackHelper.isSoundEnabled(context)) }
    var isSessionInfoExpanded by remember { mutableStateOf(false) }

    var showDiscardDialog by remember { mutableStateOf(false) }
    var showSubmitSuccessDialog by remember { mutableStateOf(false) }

    // Load initial data
    LaunchedEffect(slotId) {
        slot = viewModel.getScheduleSlotById(slotId)
        slot?.let { s ->
            course = viewModel.getCourseById(s.courseId)
            val stList = viewModel.getStudentsByCourseSync(s.courseId)
            students = stList
        }
    }

    // Load existing saved records into local map on initial open
    val savedRecords by viewModel.getAttendanceForSession(currentDate, slotId).collectAsState(initial = emptyList())
    LaunchedEffect(savedRecords) {
        if (!initialLoaded && savedRecords.isNotEmpty()) {
            savedRecords.forEach { record ->
                localAttendance[record.studentId] = record.status
            }
            initialLoaded = true
        }
    }

    // Intercept back navigation if changes are unsaved
    BackHandler(enabled = hasUnsavedChanges) {
        showDiscardDialog = true
    }

    // Stats calculated from shared local in-memory session state
    val totalCount = students.size
    val presentCount = localAttendance.values.count { it == "P" }
    val absentCount = localAttendance.values.count { it == "A" }
    val lateCount = localAttendance.values.count { it == "L" }
    val cancelledCount = localAttendance.values.count { it == "CANCELLED" || it == "C" }
    val markedCount = presentCount + absentCount + lateCount + cancelledCount

    val animPresent by animateIntAsState(presentCount, label = "present")
    val animAbsent by animateIntAsState(absentCount, label = "absent")
    val animLate by animateIntAsState(lateCount, label = "late")
    val animCancelled by animateIntAsState(cancelledCount, label = "cancelled")
    val animMarked by animateIntAsState(markedCount, label = "marked")
    val animTotal by animateIntAsState(totalCount, label = "total")
    val animProgress by animateFloatAsState(if (totalCount == 0) 0f else markedCount.toFloat() / totalCount, label = "progress")

    // Helper functions for marking with sound + haptic feedback
    fun markStudentLocal(student: StudentEntity, status: String, showUndo: Boolean = true) {
        val prevStatus = localAttendance[student.id]
        localAttendance[student.id] = status
        hasUnsavedChanges = true

        when (status) {
            "P" -> {
                SoundFeedbackHelper.playPresentSound(context)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            "A" -> {
                SoundFeedbackHelper.playAbsentSound(context)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            "L" -> {
                SoundFeedbackHelper.playLateSound(context)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            else -> {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }

        if (showUndo) {
            val statusLabel = when (status) {
                "P" -> "Present"
                "A" -> "Absent"
                "L" -> "Late"
                else -> "Unmarked"
            }
            coroutineScope.launch {
                val res = snackbarHostState.showSnackbar(
                    message = "Marked ${student.name} $statusLabel",
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Short
                )
                if (res == SnackbarResult.ActionPerformed) {
                    if (prevStatus != null) {
                        localAttendance[student.id] = prevStatus
                    } else {
                        localAttendance.remove(student.id)
                    }
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF6750A4),
            primaryContainer = Color(0xFFEADDFF),
            onPrimaryContainer = Color(0xFF21005D),
            background = Color(0xFFF9FAFB),
            surface = Color.White,
            surfaceVariant = Color(0xFFF3F4F6)
        )
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color(0xFFF8F9FA),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Take Attendance", fontWeight = FontWeight.Bold, color = Color(0xFF1E293B), fontSize = 18.sp)
                            Text(
                                if (hasUnsavedChanges) "Unsaved local session" else "Saved",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (hasUnsavedChanges) Color(0xFFEA580C) else Color(0xFF10B981),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (hasUnsavedChanges) {
                                showDiscardDialog = true
                            } else {
                                navController.popBackStack()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF1E293B))
                        }
                    },
                    actions = {
                        // Sound feedback toggle
                        IconButton(onClick = {
                            isSoundEnabled = SoundFeedbackHelper.toggleSound(context)
                            val msg = if (isSoundEnabled) "Sound feedback turned on" else "Sound feedback muted"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                imageVector = if (isSoundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = if (isSoundEnabled) "Mute Sound" else "Enable Sound",
                                tint = if (isSoundEnabled) Color(0xFF6750A4) else Color(0xFF94A3B8)
                            )
                        }

                        // Report view navigation
                        IconButton(onClick = {
                            course?.id?.let { navController.navigate("attendance_report/$it") }
                        }) {
                            Icon(Icons.Default.Assessment, contentDescription = "Report", tint = Color(0xFF1E293B))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            },
            bottomBar = {
                // Prominent Final Submit Bottom Bar
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (hasUnsavedChanges) Color(0xFFEA580C) else Color(0xFF10B981))
                                )
                                Text(
                                    text = "$animMarked of $animTotal Marked",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF475569)
                                )
                            }

                            Text(
                                text = if (animCancelled > 0) "$animPresent P • $animAbsent A • $animCancelled C" else "$animPresent P • $animAbsent A • $animLate L",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                if (course == null || slot == null) return@Button
                                isSubmitting = true
                                viewModel.submitSessionAttendance(
                                    date = currentDate,
                                    slotId = slotId,
                                    courseId = course!!.id,
                                    attendanceMap = localAttendance.toMap(),
                                    onSuccess = {
                                        isSubmitting = false
                                        hasUnsavedChanges = false
                                        showSubmitSuccessDialog = true
                                    },
                                    onError = { err ->
                                        isSubmitting = false
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Error saving: $err", duration = SnackbarDuration.Long)
                                        }
                                    }
                                )
                            },
                            enabled = !isSubmitting && (markedCount > 0 || hasUnsavedChanges),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF6750A4),
                                disabledContainerColor = Color(0xFFE2E8F0),
                                disabledContentColor = Color(0xFF94A3B8)
                            )
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Saving to Cloud & Local...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (hasUnsavedChanges) "Save & Submit Register" else "Attendance Submitted",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp
                                )
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Session Info Card (Full in List Mode or when expanded; Compact Strip in Card View)
                val courseName = course?.name?.takeIf { it.isNotBlank() } ?: "Class Attendance"
                val courseCode = course?.code?.takeIf { it.isNotBlank() }
                val timeRange = "${slot?.startTime ?: ""} - ${slot?.endTime ?: ""}".trim()
                val roomText = slot?.room ?: "Room"
                val sectionText = if (!slot?.section.isNullOrBlank()) "Sec ${slot?.section}" else ""

                if (attendanceMode == AttendanceMode.LIST || isSessionInfoExpanded) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = courseName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                    if (sectionText.isNotBlank()) {
                                        Text(
                                            text = sectionText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }
                                if (!courseCode.isNullOrBlank()) {
                                    Surface(
                                        color = Color(0xFFEDE9FE),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = courseCode,
                                            color = Color(0xFF6D28D9),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                if (attendanceMode == AttendanceMode.CARD) {
                                    IconButton(
                                        onClick = { isSessionInfoExpanded = false },
                                        modifier = Modifier.size(28.dp).padding(start = 4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ExpandLess,
                                            contentDescription = "Collapse info",
                                            tint = Color(0xFF64748B)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                InfoItem(icon = Icons.Default.Schedule, text = timeRange)
                                InfoItem(icon = Icons.Default.LocationOn, text = roomText)
                            }
                        }
                    }
                } else {
                    // Sleek Compact Bar for Card View to preserve maximum height on small screens
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable { isSessionInfoExpanded = true },
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        shadowElevation = 0.5.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (!courseCode.isNullOrBlank()) {
                                    Surface(
                                        color = Color(0xFFEDE9FE),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = courseCode,
                                            color = Color(0xFF6D28D9),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = courseName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (sectionText.isNotBlank()) {
                                    Text(
                                        text = " • $sectionText",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF64748B),
                                        maxLines = 1
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (roomText.isNotBlank()) roomText else timeRange,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF64748B),
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.ExpandMore,
                                    contentDescription = "Expand info",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Mode Selector Toggle (List View vs Card View)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFE2E8F0).copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                    ) {
                        // List View Segment
                        val isList = attendanceMode == AttendanceMode.LIST
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (isList) Color.White else Color.Transparent)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    attendanceMode = AttendanceMode.LIST
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.FormatListBulleted,
                                    contentDescription = null,
                                    tint = if (isList) Color(0xFF6750A4) else Color(0xFF64748B),
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "List View",
                                    fontWeight = if (isList) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isList) Color(0xFF6750A4) else Color(0xFF64748B),
                                    fontSize = 13.sp
                                )
                            }
                        }

                        // Card View Segment
                        val isCard = attendanceMode == AttendanceMode.CARD
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (isCard) Color.White else Color.Transparent)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    attendanceMode = AttendanceMode.CARD
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.ViewCarousel,
                                    contentDescription = null,
                                    tint = if (isCard) Color(0xFF6750A4) else Color(0xFF64748B),
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Card View",
                                    fontWeight = if (isCard) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isCard) Color(0xFF6750A4) else Color(0xFF64748B),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // Progress Bar
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Session Progress", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                        Text("$animMarked of $animTotal Evaluated", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF6750A4))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { animProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = Color(0xFF6750A4),
                        trackColor = Color(0xFFE2E8F0)
                    )
                }

                // Main Content Switching based on Mode
                AnimatedContent(
                    targetState = attendanceMode,
                    transitionSpec = {
                        fadeIn(tween(250)) togetherWith fadeOut(tween(200))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    label = "modeTransition"
                ) { mode ->
                    when (mode) {
                        AttendanceMode.LIST -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Stats Summary Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    StatCard("Present", animPresent, Color(0xFF16A34A), Modifier.weight(1f))
                                    StatCard("Absent", animAbsent, Color(0xFFDC2626), Modifier.weight(1f))
                                    if (animCancelled > 0) {
                                        StatCard("Cancelled", animCancelled, Color(0xFF64748B), Modifier.weight(1f))
                                    } else {
                                        StatCard("Late", animLate, Color(0xFFD97706), Modifier.weight(1f))
                                    }
                                }

                                // Quick Actions
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = {
                                            students.forEach { st ->
                                                localAttendance[st.id] = "P"
                                            }
                                            hasUnsavedChanges = true
                                            SoundFeedbackHelper.playPresentSound(context)
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Marked all ${students.size} students Present", duration = SnackbarDuration.Short)
                                            }
                                        },
                                        modifier = Modifier.weight(1.2f),
                                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFDCFCE7), contentColor = Color(0xFF15803D)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Mark All Present", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }

                                    FilledTonalButton(
                                        onClick = {
                                            students.forEach { st ->
                                                localAttendance[st.id] = "CANCELLED"
                                            }
                                            hasUnsavedChanges = true
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Class marked as Cancelled (Tap Save to submit)", duration = SnackbarDuration.Short)
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFF1F5F9), contentColor = Color(0xFF475569)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.EventBusy, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Cancel Class", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }

                                // Students List View
                                LazyColumn(
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(students, key = { it.id }) { student ->
                                        val currentStatus = localAttendance[student.id]

                                        SwipeableAttendanceCard(
                                            student = student,
                                            status = currentStatus,
                                            onSwipeRight = {
                                                markStudentLocal(student, "P")
                                            },
                                            onSwipeLeft = {
                                                markStudentLocal(student, "A")
                                            },
                                            onLateClick = {
                                                markStudentLocal(student, "L")
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        AttendanceMode.CARD -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (students.isEmpty()) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.PeopleOutline, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(48.dp))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("No students enrolled in this class batch", color = Color(0xFF64748B), style = MaterialTheme.typography.bodyMedium)
                                    }
                                } else if (cardIndex >= students.size) {
                                    // All students evaluated summary view
                                    CardEvaluationSummary(
                                        total = students.size,
                                        present = presentCount,
                                        absent = absentCount,
                                        late = lateCount,
                                        onReviewList = {
                                            attendanceMode = AttendanceMode.LIST
                                        },
                                        onRestartCards = {
                                            cardIndex = 0
                                        }
                                    )
                                } else {
                                    val currentStudent = students[cardIndex]
                                    val nextStudent = if (cardIndex + 1 < students.size) students[cardIndex + 1] else null
                                    val currentStatus = localAttendance[currentStudent.id]

                                    SwipeableSingleStudentCard(
                                        student = currentStudent,
                                        nextStudent = nextStudent,
                                        localAttendance = localAttendance,
                                        status = currentStatus,
                                        currentIndex = cardIndex,
                                        totalCount = students.size,
                                        onSwipeRight = {
                                            markStudentLocal(currentStudent, "P")
                                            if (cardIndex < students.size) cardIndex++
                                        },
                                        onSwipeLeft = {
                                            markStudentLocal(currentStudent, "A")
                                            if (cardIndex < students.size) cardIndex++
                                        },
                                        onLateClick = {
                                            markStudentLocal(currentStudent, "L")
                                            if (cardIndex < students.size) cardIndex++
                                        },
                                        onPreviousClick = {
                                            if (cardIndex > 0) cardIndex--
                                        },
                                        canGoPrevious = cardIndex > 0
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Discard Confirmation Dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            icon = { Icon(Icons.Default.WarningAmber, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(32.dp)) },
            title = { Text("Unsaved Attendance", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A)) },
            text = {
                Text(
                    "You have unsaved attendance for this session. Marking only saves permanently to the cloud when you tap 'Save & Submit Register'.\n\nDiscard these changes and exit?",
                    color = Color(0xFF475569),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardDialog = false
                        hasUnsavedChanges = false
                        navController.popBackStack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Discard Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Keep Editing", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Submit Success Dialog
    if (showSubmitSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSubmitSuccessDialog = false },
            icon = {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFDCFCE7)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(36.dp))
                }
            },
            title = {
                Text(
                    "Attendance Submitted!",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = Color(0xFF0F172A)
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Session attendance has been successfully synchronized and saved to the cloud database.",
                        textAlign = TextAlign.Center,
                        color = Color(0xFF475569),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFDCFCE7)) {
                            Text(
                                "$presentCount Present",
                                color = Color(0xFF15803D),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFFEE2E2)) {
                            Text(
                                "$absentCount Absent",
                                color = Color(0xFFB91C1C),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFFEF3C7)) {
                            Text(
                                "$lateCount Late",
                                color = Color(0xFFB45309),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSubmitSuccessDialog = false
                        navController.popBackStack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                ) {
                    Text("Return to Dashboard", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

/**
 * Modern Responsive Swipeable Card Component for Card View Mode
 * Automatically adapts to compact / small screen heights with stacked deck effect
 */
@Composable
fun SwipeableSingleStudentCard(
    student: StudentEntity,
    nextStudent: StudentEntity?,
    localAttendance: Map<String, String>,
    status: String?,
    currentIndex: Int,
    totalCount: Int,
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
    onLateClick: () -> Unit,
    onPreviousClick: () -> Unit,
    canGoPrevious: Boolean
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val maxH = maxHeight
        val maxW = maxWidth
        val isSmallScreen = maxH < 490.dp
        val isTinyScreen = maxH < 410.dp

        val density = LocalDensity.current
        val screenWidthPx = with(density) { maxW.toPx() }
        val swipeThreshold = screenWidthPx * 0.28f

        // Dynamic card height calculation based on available container height
        val cardHeight = when {
            isTinyScreen -> 220.dp
            isSmallScreen -> (maxH - 96.dp).coerceIn(230.dp, 290.dp)
            else -> (maxH - 105.dp).coerceIn(280.dp, 350.dp)
        }
        val cardWidthFraction = if (isSmallScreen) 0.94f else 0.90f
        val spacingBetweenCardAndButtons = if (isSmallScreen) 10.dp else 16.dp

        val offsetX = remember(student.id) { Animatable(0f) }
        val offsetY = remember(student.id) { Animatable(0f) }

        val rotation = (offsetX.value / screenWidthPx) * 16f
        val rightProgress = (offsetX.value / swipeThreshold).coerceIn(0f, 1f)
        val leftProgress = (-offsetX.value / swipeThreshold).coerceIn(0f, 1f)
        val dragFraction = kotlin.math.abs(offsetX.value) / swipeThreshold

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isTinyScreen) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Stack container: background card (if nextStudent) + active swipe card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardHeight + (if (isSmallScreen) 8.dp else 12.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Next Student Card in Stack (Deck preview)
                if (nextStudent != null) {
                    val stackScale = androidx.compose.ui.util.lerp(0.92f, 0.98f, dragFraction.coerceIn(0f, 1f))
                    val stackOffsetY = androidx.compose.ui.unit.lerp(if (isSmallScreen) 8.dp else 12.dp, 2.dp, dragFraction.coerceIn(0f, 1f))
                    val stackAlpha = androidx.compose.ui.util.lerp(0.65f, 0.95f, dragFraction.coerceIn(0f, 1f))

                    StudentCardSurface(
                        student = nextStudent,
                        status = localAttendance[nextStudent.id],
                        currentIndex = currentIndex + 1,
                        totalCount = totalCount,
                        isSmallScreen = isSmallScreen,
                        modifier = Modifier
                            .fillMaxWidth(cardWidthFraction)
                            .height(cardHeight)
                            .offset(y = stackOffsetY)
                            .scale(stackScale)
                            .alpha(stackAlpha)
                            .shadow(4.dp, RoundedCornerShape(26.dp), spotColor = Color.Black.copy(alpha = 0.08f))
                            .clip(RoundedCornerShape(26.dp))
                            .background(Color(0xFFF8FAFC))
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(26.dp))
                    )
                }

                // Top Active Swipable Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth(cardWidthFraction)
                        .height(cardHeight)
                        .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                        .rotate(rotation)
                        .pointerInput(student.id) {
                            detectDragGestures(
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    coroutineScope.launch {
                                        offsetX.snapTo(offsetX.value + dragAmount.x)
                                        offsetY.snapTo(offsetY.value + dragAmount.y * 0.20f)
                                    }
                                },
                                onDragEnd = {
                                    coroutineScope.launch {
                                        if (offsetX.value > swipeThreshold) {
                                            offsetX.animateTo(screenWidthPx * 1.3f, tween(180))
                                            onSwipeRight()
                                        } else if (offsetX.value < -swipeThreshold) {
                                            offsetX.animateTo(-screenWidthPx * 1.3f, tween(180))
                                            onSwipeLeft()
                                        } else {
                                            launch {
                                                offsetX.animateTo(
                                                    0f,
                                                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                                                )
                                            }
                                            launch {
                                                offsetY.animateTo(
                                                    0f,
                                                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                                                )
                                            }
                                        }
                                    }
                                }
                            )
                        }
                        .shadow(
                            elevation = if (dragFraction > 0.1f) 14.dp else 6.dp,
                            shape = RoundedCornerShape(26.dp),
                            spotColor = when {
                                rightProgress > 0.15f -> Color(0xFF10B981).copy(alpha = 0.35f)
                                leftProgress > 0.15f -> Color(0xFFEF4444).copy(alpha = 0.35f)
                                else -> Color.Black.copy(alpha = 0.10f)
                            }
                        )
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color.White)
                        .border(
                            width = if (rightProgress > 0.1f || leftProgress > 0.1f) 2.dp else 1.2.dp,
                            brush = when {
                                rightProgress > 0.15f -> Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF34D399)))
                                leftProgress > 0.15f -> Brush.linearGradient(listOf(Color(0xFFEF4444), Color(0xFFF87171)))
                                else -> Brush.linearGradient(listOf(Color(0xFFE2E8F0), Color(0xFFCBD5E1)))
                            },
                            shape = RoundedCornerShape(26.dp)
                        )
                ) {
                    // Reactive Background Tint while swiping
                    if (rightProgress > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF22C55E).copy(alpha = rightProgress * 0.15f))
                        )
                    } else if (leftProgress > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFFEF4444).copy(alpha = leftProgress * 0.15f))
                        )
                    }

                    // Card interior content
                    StudentCardSurface(
                        student = student,
                        status = status,
                        currentIndex = currentIndex,
                        totalCount = totalCount,
                        isSmallScreen = isSmallScreen,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Interactive Stamp Overlays
                    if (rightProgress > 0.16f) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(if (isSmallScreen) 12.dp else 16.dp)
                                .rotate(-13f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.92f))
                                .border(2.5.dp, Color(0xFF10B981), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("PRESENT", fontWeight = FontWeight.Black, color = Color(0xFF10B981), fontSize = if (isSmallScreen) 14.sp else 16.sp)
                            }
                        }
                    } else if (leftProgress > 0.16f) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(if (isSmallScreen) 12.dp else 16.dp)
                                .rotate(13f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.92f))
                                .border(2.5.dp, Color(0xFFEF4444), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Cancel, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("ABSENT", fontWeight = FontWeight.Black, color = Color(0xFFEF4444), fontSize = if (isSmallScreen) 14.sp else 16.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacingBetweenCardAndButtons))

            // Action Buttons Row Below Card
            Row(
                modifier = Modifier.fillMaxWidth(cardWidthFraction),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous Student Button
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPreviousClick()
                    },
                    enabled = canGoPrevious,
                    modifier = Modifier
                        .size(if (isSmallScreen) 44.dp else 48.dp)
                        .clip(CircleShape)
                        .background(if (canGoPrevious) Color.White else Color(0xFFF1F5F9))
                        .border(1.dp, if (canGoPrevious) Color(0xFFCBD5E1) else Color(0xFFE2E8F0), CircleShape)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Previous Student",
                        tint = if (canGoPrevious) Color(0xFF475569) else Color(0xFFCBD5E1),
                        modifier = Modifier.size(if (isSmallScreen) 20.dp else 22.dp)
                    )
                }

                // Tap Mark Absent
                FilledTonalIconButton(
                    onClick = onSwipeLeft,
                    modifier = Modifier
                        .size(if (isSmallScreen) 50.dp else 56.dp)
                        .border(1.2.dp, Color(0xFFFECACA), CircleShape),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color(0xFFFEE2E2),
                        contentColor = Color(0xFFDC2626)
                    )
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Mark Absent", modifier = Modifier.size(if (isSmallScreen) 24.dp else 28.dp))
                }

                // Tap Mark Late (Pill Button)
                FilledTonalButton(
                    onClick = onLateClick,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFFFEF3C7),
                        contentColor = Color(0xFFB45309)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.2.dp, Color(0xFFFDE68A)),
                    contentPadding = PaddingValues(
                        horizontal = if (isSmallScreen) 14.dp else 18.dp,
                        vertical = if (isSmallScreen) 8.dp else 10.dp
                    ),
                    modifier = Modifier.height(if (isSmallScreen) 44.dp else 48.dp)
                ) {
                    Icon(Icons.Default.AccessTime, contentDescription = "Mark Late", modifier = Modifier.size(if (isSmallScreen) 16.dp else 18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Late", fontWeight = FontWeight.Bold, fontSize = if (isSmallScreen) 13.sp else 14.sp)
                }

                // Tap Mark Present
                FilledTonalIconButton(
                    onClick = onSwipeRight,
                    modifier = Modifier
                        .size(if (isSmallScreen) 50.dp else 56.dp)
                        .border(1.2.dp, Color(0xFFBBF7D0), CircleShape),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color(0xFFDCFCE7),
                        contentColor = Color(0xFF16A34A)
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Mark Present", modifier = Modifier.size(if (isSmallScreen) 24.dp else 28.dp))
                }
            }
        }
    }
}

/**
 * Surface and internal typography of a single student card
 */
@Composable
fun StudentCardSurface(
    student: StudentEntity,
    status: String?,
    currentIndex: Int,
    totalCount: Int,
    isSmallScreen: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(
                horizontal = if (isSmallScreen) 16.dp else 20.dp,
                vertical = if (isSmallScreen) 12.dp else 16.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Row: Student Index pill & Status badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color(0xFFEEF2FF),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.8.dp, Color(0xFFC7D2FE))
            ) {
                Text(
                    text = "STUDENT ${currentIndex + 1} OF $totalCount",
                    color = Color(0xFF4F46E5),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (isSmallScreen) 10.sp else 11.sp,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.5.dp)
                )
            }

            if (status != null) {
                val badge = when (status) {
                    "P" -> QuadrupleBadge("Marked: Present", Color(0xFFDCFCE7), Color(0xFF15803D), Color(0xFF86EFAC))
                    "A" -> QuadrupleBadge("Marked: Absent", Color(0xFFFEE2E2), Color(0xFFB91C1C), Color(0xFFFCA5A5))
                    "L" -> QuadrupleBadge("Marked: Late", Color(0xFFFEF3C7), Color(0xFFB45309), Color(0xFFFDE68A))
                    else -> QuadrupleBadge("Unmarked", Color(0xFFF1F5F9), Color(0xFF64748B), Color(0xFFE2E8F0))
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = badge.bg,
                    border = BorderStroke(0.8.dp, badge.border)
                ) {
                    Text(
                        text = badge.text,
                        color = badge.fg,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isSmallScreen) 10.sp else 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp)
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(0.8.dp, Color(0xFFE2E8F0))
                ) {
                    Text(
                        text = "Pending",
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = if (isSmallScreen) 10.sp else 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp)
                    )
                }
            }
        }

        // Center Content: Avatar + Name + Roll Number
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = if (isSmallScreen) 2.dp else 6.dp)
        ) {
            val avatarSize = if (isSmallScreen) 62.dp else 76.dp
            val initials = student.name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")

            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFEDE9FE), Color(0xFFDDD6FE))
                        )
                    )
                    .border(
                        width = 2.5.dp,
                        brush = Brush.linearGradient(
                            listOf(Color(0xFF6366F1), Color(0xFF8B5CF6), Color(0xFFA855F7))
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (initials.isNotBlank()) initials else "S",
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF5B21B6),
                    fontSize = if (isSmallScreen) 22.sp else 28.sp
                )
            }

            Spacer(modifier = Modifier.height(if (isSmallScreen) 6.dp else 10.dp))

            Text(
                text = student.name,
                style = if (isSmallScreen) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF0F172A),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(3.dp))

            // Roll Number Badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFF1F5F9),
                border = BorderStroke(0.8.dp, Color(0xFFE2E8F0))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Icon(
                        Icons.Default.Badge,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Roll: ${student.rollNumber}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF334155),
                        fontSize = if (isSmallScreen) 11.sp else 12.sp
                    )
                }
            }
        }

        // Bottom guide row inside card
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 2.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color(0xFFDC2626).copy(alpha = 0.6f), modifier = Modifier.size(11.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Left for Absent",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF94A3B8),
                fontSize = if (isSmallScreen) 10.sp else 11.sp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("•", color = Color(0xFFCBD5E1), fontSize = 10.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Right for Present",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF94A3B8),
                fontSize = if (isSmallScreen) 10.sp else 11.sp
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color(0xFF16A34A).copy(alpha = 0.6f), modifier = Modifier.size(11.dp))
        }
    }
}

private data class QuadrupleBadge(val text: String, val bg: Color, val fg: Color, val border: Color)

/**
 * Summary View shown once all cards in Card View mode have been reviewed
 */
@Composable
fun CardEvaluationSummary(
    total: Int,
    present: Int,
    absent: Int,
    late: Int,
    onReviewList: () -> Unit,
    onRestartCards: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEDE9FE)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.DoneAll, contentDescription = null, tint = Color(0xFF6D28D9), modifier = Modifier.size(30.dp))
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                "All Students Evaluated!",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                "All $total students have been reviewed. You can review the register in List View or submit.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryPill("Present", present, Color(0xFF16A34A), Color(0xFFDCFCE7))
                SummaryPill("Absent", absent, Color(0xFFDC2626), Color(0xFFFEE2E2))
                SummaryPill("Late", late, Color(0xFFD97706), Color(0xFFFEF3C7))
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onReviewList,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.FormatListBulleted, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Review & Tweak in List View", fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(6.dp))

            TextButton(
                onClick = onRestartCards,
                modifier = Modifier.height(36.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Start Over from First Card", fontSize = 12.5.sp)
            }
        }
    }
}

@Composable
fun SummaryPill(label: String, count: Int, textColor: Color, bgColor: Color) {
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(count.toString(), fontWeight = FontWeight.ExtraBold, color = textColor, fontSize = 18.sp)
            Text(label, style = MaterialTheme.typography.labelSmall, color = textColor, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun InfoItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Color(0xFF334155), fontWeight = FontWeight.Medium)
    }
}

@Composable
fun StatCard(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF475569), fontWeight = FontWeight.Medium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableAttendanceCard(
    student: StudentEntity,
    status: String?,
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
    onLateClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onSwipeRight()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onSwipeLeft()
                    false
                }
                else -> false
            }
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.25f }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Color(0xFF16A34A) // Green
                SwipeToDismissBoxValue.EndToStart -> Color(0xFFDC2626) // Red
                else -> Color.Transparent
            }
            val alignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.Center
            }
            val icon = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Icons.Default.CheckCircle
                SwipeToDismissBoxValue.EndToStart -> Icons.Default.Cancel
                else -> Icons.Default.Circle
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                if (direction != SwipeToDismissBoxValue.Settled) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        },
        content = {
            val cardBackgroundColor = when (status) {
                "P" -> Color(0xFFF0FDF4)
                "A" -> Color(0xFFFEF2F2)
                "L" -> Color(0xFFFFFBEB)
                "CANCELLED", "C" -> Color(0xFFF1F5F9)
                else -> Color.White
            }
            val borderColor = when (status) {
                "P" -> Color(0xFF86EFAC)
                "A" -> Color(0xFFFCA5A5)
                "L" -> Color(0xFFFDE68A)
                "CANCELLED", "C" -> Color(0xFFCBD5E1)
                else -> Color(0xFFE2E8F0)
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = cardBackgroundColor,
                shadowElevation = if (status == null) 1.dp else 0.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEDE9FE)),
                        contentAlignment = Alignment.Center
                    ) {
                        val initials = student.name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")
                        Text(
                            text = if (initials.isNotBlank()) initials else "S",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6D28D9),
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))

                    // Details
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            student.name,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF0F172A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(student.rollNumber, style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                    }

                    // Status Actions
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Quick Status Badges
                        if (status != null) {
                            val (statusText, statusColor) = when (status) {
                                "P" -> "Present" to Color(0xFF16A34A)
                                "A" -> "Absent" to Color(0xFFDC2626)
                                "L" -> "Late" to Color(0xFFD97706)
                                "CANCELLED", "C" -> "Cancelled" to Color(0xFF64748B)
                                else -> "" to Color.Gray
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = statusColor,
                                modifier = Modifier.clickable { onLateClick() }
                            ) {
                                Text(
                                    statusText,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        } else {
                            // Tap to mark Late icon
                            IconButton(
                                onClick = onLateClick,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.AccessTime, contentDescription = "Late", tint = Color(0xFF94A3B8), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    )
}
