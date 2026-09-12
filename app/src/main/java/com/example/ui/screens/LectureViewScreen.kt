package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
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
    val markedCount = presentCount + absentCount + lateCount

    val animPresent by animateIntAsState(presentCount, label = "present")
    val animAbsent by animateIntAsState(absentCount, label = "absent")
    val animLate by animateIntAsState(lateCount, label = "late")
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
                    shadowElevation = 12.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
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
                                        .size(8.dp)
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
                                text = "$animPresent P • $animAbsent A • $animLate L",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

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
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF6750A4),
                                disabledContainerColor = Color(0xFFE2E8F0),
                                disabledContentColor = Color(0xFF94A3B8)
                            )
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color.White,
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Saving to Cloud & Local...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (hasUnsavedChanges) "Save & Submit Register" else "Attendance Submitted",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
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
                // Session Info Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val courseName = course?.name?.takeIf { it.isNotBlank() } ?: "Class Attendance"
                        val courseCode = course?.code?.takeIf { it.isNotBlank() }

                        Row(verticalAlignment = Alignment.Top) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = courseName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                if (!slot?.section.isNullOrBlank()) {
                                    Text(
                                        text = "Section ${slot?.section}",
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
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            InfoItem(icon = Icons.Default.Schedule, text = "${slot?.startTime ?: ""} - ${slot?.endTime ?: ""}")
                            InfoItem(icon = Icons.Default.LocationOn, text = slot?.room ?: "Room")
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
                                    StatCard("Late", animLate, Color(0xFFD97706), Modifier.weight(1f))
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
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFDCFCE7), contentColor = Color(0xFF15803D)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Mark All Present", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (students.isEmpty()) {
                                    Text("No students in this class batch", color = Color.Gray)
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
                                    val currentStatus = localAttendance[currentStudent.id]

                                    SwipeableSingleStudentCard(
                                        student = currentStudent,
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
 * Swipeable Card Component for Card View Mode
 * Supports smooth spring-physics swipe: Right -> Present, Left -> Absent, Tap -> Late
 */
@Composable
fun SwipeableSingleStudentCard(
    student: StudentEntity,
    status: String?,
    currentIndex: Int,
    totalCount: Int,
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
    onLateClick: () -> Unit,
    onPreviousClick: () -> Unit,
    canGoPrevious: Boolean
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val swipeThreshold = screenWidthPx * 0.28f

    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember(student.id) { Animatable(0f) }
    val offsetY = remember(student.id) { Animatable(0f) }

    val rotation = (offsetX.value / screenWidthPx) * 20f
    val rightProgress = (offsetX.value / swipeThreshold).coerceIn(0f, 1f)
    val leftProgress = (-offsetX.value / swipeThreshold).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Step Indicator Pill
        Row(
            modifier = Modifier.padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = Color(0xFFEDE9FE),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Student ${currentIndex + 1} of $totalCount",
                    color = Color(0xFF6D28D9),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // Swipeable Main Card
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .height(340.dp)
                .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                .rotate(rotation)
                .pointerInput(student.id) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                offsetX.snapTo(offsetX.value + dragAmount.x)
                                offsetY.snapTo(offsetY.value + dragAmount.y * 0.25f)
                            }
                        },
                        onDragEnd = {
                            coroutineScope.launch {
                                if (offsetX.value > swipeThreshold) {
                                    // Fling off to the right
                                    offsetX.animateTo(screenWidthPx * 1.3f, tween(200))
                                    onSwipeRight()
                                } else if (offsetX.value < -swipeThreshold) {
                                    // Fling off to the left
                                    offsetX.animateTo(-screenWidthPx * 1.3f, tween(200))
                                    onSwipeLeft()
                                } else {
                                    // Spring back smoothly
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
                .shadow(8.dp, RoundedCornerShape(24.dp), ambientColor = Color(0x1F000000))
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .border(
                    width = 1.5.dp,
                    color = when {
                        rightProgress > 0.15f -> Color(0xFF16A34A).copy(alpha = rightProgress)
                        leftProgress > 0.15f -> Color(0xFFDC2626).copy(alpha = leftProgress)
                        else -> Color(0xFFE2E8F0)
                    },
                    shape = RoundedCornerShape(24.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            // Background Dynamic Gradient/Tint while dragging
            if (rightProgress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF22C55E).copy(alpha = rightProgress * 0.18f))
                )
            } else if (leftProgress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFEF4444).copy(alpha = leftProgress * 0.18f))
                )
            }

            // Card Interior Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Large Avatar Circle
                val initials = student.name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEDE9FE))
                        .border(2.dp, Color(0xFFC4B5FD), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (initials.isNotBlank()) initials else "S",
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF6D28D9),
                        fontSize = 32.sp
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = student.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Roll No: ${student.rollNumber}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF64748B)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Current Marking Badge
                if (status != null) {
                    val (badgeText, badgeBg, badgeFg) = when (status) {
                        "P" -> Triple("Marked: Present", Color(0xFFDCFCE7), Color(0xFF15803D))
                        "A" -> Triple("Marked: Absent", Color(0xFFFEE2E2), Color(0xFFB91C1C))
                        "L" -> Triple("Marked: Late", Color(0xFFFEF3C7), Color(0xFFB45309))
                        else -> Triple("Unmarked", Color(0xFFF1F5F9), Color(0xFF64748B))
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = badgeBg
                    ) {
                        Text(
                            text = badgeText,
                            color = badgeFg,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                } else {
                    Text(
                        text = "Swipe Right for Present • Left for Absent",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            // Swipe Overlay Stamps
            if (rightProgress > 0.2f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(20.dp)
                        .rotate(-15f)
                        .border(3.dp, Color(0xFF16A34A), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PRESENT", fontWeight = FontWeight.Black, color = Color(0xFF16A34A), fontSize = 18.sp)
                    }
                }
            } else if (leftProgress > 0.2f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(20.dp)
                        .rotate(15f)
                        .border(3.dp, Color(0xFFDC2626), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ABSENT", fontWeight = FontWeight.Black, color = Color(0xFFDC2626), fontSize = 18.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Action Buttons Row Below Card
        Row(
            modifier = Modifier.fillMaxWidth(0.92f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Previous Student Button
            IconButton(
                onClick = onPreviousClick,
                enabled = canGoPrevious,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (canGoPrevious) Color.White else Color(0xFFF1F5F9))
                    .border(1.dp, Color(0xFFE2E8F0), CircleShape)
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Previous",
                    tint = if (canGoPrevious) Color(0xFF475569) else Color(0xFFCBD5E1)
                )
            }

            // Tap Mark Absent
            FilledTonalIconButton(
                onClick = onSwipeLeft,
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color(0xFFFEE2E2),
                    contentColor = Color(0xFFDC2626)
                )
            ) {
                Icon(Icons.Default.Close, contentDescription = "Mark Absent", modifier = Modifier.size(28.dp))
            }

            // Tap Mark Late (Pill Button)
            FilledTonalButton(
                onClick = onLateClick,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color(0xFFFEF3C7),
                    contentColor = Color(0xFFB45309)
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Default.AccessTime, contentDescription = "Mark Late", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Late", fontWeight = FontWeight.Bold)
            }

            // Tap Mark Present
            FilledTonalIconButton(
                onClick = onSwipeRight,
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color(0xFFDCFCE7),
                    contentColor = Color(0xFF16A34A)
                )
            ) {
                Icon(Icons.Default.Check, contentDescription = "Mark Present", modifier = Modifier.size(28.dp))
            }
        }
    }
}

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
            .padding(vertical = 12.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEDE9FE)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.DoneAll, contentDescription = null, tint = Color(0xFF6D28D9), modifier = Modifier.size(38.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "All Students Evaluated!",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                "All $total students have been reviewed for this session. You can review the register in List View or submit now.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryPill("Present", present, Color(0xFF16A34A), Color(0xFFDCFCE7))
                SummaryPill("Absent", absent, Color(0xFFDC2626), Color(0xFFFEE2E2))
                SummaryPill("Late", late, Color(0xFFD97706), Color(0xFFFEF3C7))
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = onReviewList,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.FormatListBulleted, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Review & Tweak in List View")
            }

            Spacer(modifier = Modifier.height(10.dp))

            TextButton(onClick = onRestartCards) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Start Over from First Card")
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
                else -> Color.White
            }
            val borderColor = when (status) {
                "P" -> Color(0xFF86EFAC)
                "A" -> Color(0xFFFCA5A5)
                "L" -> Color(0xFFFDE68A)
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
