package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.data.CourseEntity
import com.example.data.ScheduleSlotEntity
import com.example.data.StudentEntity
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LectureViewScreen(navController: NavController, viewModel: MainViewModel, slotId: String) {
    BackHandler { navController.popBackStack() }

    var slot by remember { mutableStateOf<ScheduleSlotEntity?>(null) }
    var course by remember { mutableStateOf<CourseEntity?>(null) }
    var students by remember { mutableStateOf<List<StudentEntity>>(emptyList()) }
    val currentDate = remember { LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(slotId) {
        slot = viewModel.getScheduleSlotById(slotId)
        slot?.let { s ->
            course = viewModel.getCourseById(s.courseId)
            students = viewModel.getStudentsByCourseSync(s.courseId)
        }
    }

    val attendanceRecords by viewModel.getAttendanceForSession(currentDate, slotId).collectAsState(initial = emptyList())
    val presentCount = attendanceRecords.count { it.status == "P" }
    val absentCount = attendanceRecords.count { it.status == "A" }
    val lateCount = attendanceRecords.count { it.status == "L" }
    val totalCount = students.size
    val markedCount = presentCount + absentCount + lateCount
    
    var quizExpanded by remember { mutableStateOf(false) }

    // Animated states for stats
    val animPresent by animateIntAsState(presentCount, label = "present")
    val animAbsent by animateIntAsState(absentCount, label = "absent")
    val animLate by animateIntAsState(lateCount, label = "late")
    val animMarked by animateIntAsState(markedCount, label = "marked")
    val animTotal by animateIntAsState(totalCount, label = "total")
    val animProgress by animateFloatAsState(if (totalCount == 0) 0f else markedCount.toFloat() / totalCount, label = "progress")

    // Entrance animation state
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF6750A4),
            background = Color.White,
            surface = Color.White,
            surfaceVariant = Color(0xFFF3F4F6)
        )
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color(0xFFFAFAFA), // slightly off-white for contrast with cards
            topBar = {
                TopAppBar(
                    title = { Text("Take Attendance", fontWeight = FontWeight.Bold, color = Color.Black) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.Black)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            course?.id?.let { navController.navigate("attendance_report/$it") }
                        }) {
                            Icon(Icons.Default.Assessment, contentDescription = "Report", tint = Color.Black)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFFAFAFA))
                )
            }
        ) { paddingValues ->
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(400)) + slideInVertically(
                    initialOffsetY = { it / 8 },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    // Proper Session Info Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = course?.name ?: "Loading...",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = course?.code ?: "",
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Info Grid
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                InfoItem(icon = Icons.Default.Schedule, text = "${slot?.startTime ?: ""} - ${slot?.endTime ?: ""}")
                                InfoItem(icon = Icons.Default.LocationOn, text = slot?.room ?: "")
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                InfoItem(icon = Icons.Default.CalendarToday, text = LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM, yyyy")))
                                InfoItem(icon = Icons.Default.Class, text = "Section ${slot?.section ?: ""}")
                            }
                        }
                    }

                    // Progress Bar
                    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Completion", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                            Text("$animMarked of $animTotal Marked", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { animProgress },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color(0xFFE0E0E0)
                        )
                    }

                    // Stats Row
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatCard("Present", animPresent, Color(0xFF4CAF50), Modifier.weight(1f))
                        StatCard("Absent", animAbsent, Color(0xFFF44336), Modifier.weight(1f))
                        StatCard("Late", animLate, Color(0xFFFF9800), Modifier.weight(1f))
                    }

                    // Quick Actions
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val studentIds = students.map { it.id }
                                viewModel.markAllStudentsAttendance(currentDate, slotId, studentIds, "P")
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFE8F5E9), contentColor = Color(0xFF2E7D32)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Mark All Present", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Main Swipeable List
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(students, key = { it.id }) { student ->
                            val record = attendanceRecords.find { it.studentId == student.id }
                            val currentStatus = record?.status
                            
                            SwipeableAttendanceCard(
                                student = student,
                                status = currentStatus,
                                onSwipeRight = {
                                    viewModel.markAttendance(currentDate, slotId, student.id, "P")
                                    coroutineScope.launch {
                                        val result = snackbarHostState.showSnackbar("Marked ${student.name} Present", actionLabel = "Undo", duration = SnackbarDuration.Short)
                                        if (result == SnackbarResult.ActionPerformed) viewModel.markAttendance(currentDate, slotId, student.id, "NONE")
                                    }
                                },
                                onSwipeLeft = {
                                    viewModel.markAttendance(currentDate, slotId, student.id, "A")
                                    coroutineScope.launch {
                                        val result = snackbarHostState.showSnackbar("Marked ${student.name} Absent", actionLabel = "Undo", duration = SnackbarDuration.Short)
                                        if (result == SnackbarResult.ActionPerformed) viewModel.markAttendance(currentDate, slotId, student.id, "NONE")
                                    }
                                },
                                onLateClick = {
                                    viewModel.markAttendance(currentDate, slotId, student.id, "L")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun StatCard(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.DarkGray)
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
                SwipeToDismissBoxValue.StartToEnd -> Color(0xFF4CAF50) // Green
                SwipeToDismissBoxValue.EndToStart -> Color(0xFFF44336) // Red
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
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(color)
                    .padding(horizontal = 24.dp),
                contentAlignment = alignment
            ) {
                if (direction != SwipeToDismissBoxValue.Settled) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        },
        content = {
            val cardBackgroundColor = when (status) {
                "P" -> Color(0xFFE8F5E9)
                "A" -> Color(0xFFFFEBEE)
                "L" -> Color(0xFFFFF3E0)
                else -> Color.White
            }
            val borderColor = when (status) {
                "P" -> Color(0xFF4CAF50).copy(alpha = 0.5f)
                "A" -> Color(0xFFF44336).copy(alpha = 0.5f)
                "L" -> Color(0xFFFF9800).copy(alpha = 0.5f)
                else -> Color(0xFFE0E0E0)
            }
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = cardBackgroundColor,
                shadowElevation = if (status == null) 1.dp else 0.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF3F4F6)),
                        contentAlignment = Alignment.Center
                    ) {
                        val initials = student.name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")
                        Text(
                            text = if (initials.isNotBlank()) initials else "S",
                            fontWeight = FontWeight.Bold,
                            color = Color.DarkGray
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    // Details
                    Column(modifier = Modifier.weight(1f)) {
                        Text(student.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge, color = Color.Black)
                        Text(student.rollNumber, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    }
                    
                    // Status Badge / Late Action
                    if (status != null) {
                        val statusText = when (status) { "P" -> "Present"; "A" -> "Absent"; "L" -> "Late"; else -> "" }
                        val statusColor = when (status) { "P" -> Color(0xFF4CAF50); "A" -> Color(0xFFF44336); "L" -> Color(0xFFFF9800); else -> Color.Gray }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = statusColor,
                            modifier = Modifier.clickable { onLateClick() } 
                        ) {
                            Text(statusText, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    } else {
                        // Late Button Icon
                        IconButton(onClick = onLateClick, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.AccessTime, contentDescription = "Late", tint = Color.Gray, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        }
    )
}
