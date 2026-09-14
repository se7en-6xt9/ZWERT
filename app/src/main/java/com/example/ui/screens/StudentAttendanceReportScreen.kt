package com.example.ui.screens

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import com.example.data.AttendanceRecordEntity
import com.example.data.CourseEntity
import com.example.data.ScheduleSlotEntity
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

@SuppressLint("NewApi")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentAttendanceReportScreen(
    navController: NavController,
    viewModel: MainViewModel
) {
    BackHandler { navController.popBackStack() }

    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()

    val courses by viewModel.getAllCourses().collectAsState(initial = emptyList())
    val allAttendance by viewModel.getAllAttendance().collectAsState(initial = emptyList())
    val userProfile by viewModel.userProfile.collectAsState()

    var allSlots by remember { mutableStateOf<List<ScheduleSlotEntity>>(emptyList()) }
    LaunchedEffect(Unit) {
        allSlots = viewModel.getAllScheduleSlotsSync()
    }

    var searchQuery by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }

    // Dynamic bidirectional infinite scroll range state
    var pastDaysCount by remember { mutableIntStateOf(60) }
    var futureDaysCount by remember { mutableIntStateOf(30) }

    val filteredCourses = remember(courses, searchQuery) {
        if (searchQuery.isBlank()) courses
        else courses.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.code.contains(searchQuery, ignoreCase = true)
        }
    }

    // Generate date columns from past to future
    val today = remember { LocalDate.now() }
    val generatedDates = remember(pastDaysCount, futureDaysCount) {
        val list = mutableListOf<LocalDate>()
        for (i in -pastDaysCount.toLong()..futureDaysCount.toLong()) {
            list.add(today.plusDays(i))
        }
        list
    }

    // Grid Dimensions
    val cellWidth = 72.dp
    val cellWidthPx = with(density) { cellWidth.toPx() }
    val leftColWidth = 190.dp
    val headerHeight = 72.dp
    val rowHeight = 64.dp
    val gridBorderColor = if (isDarkTheme) Color(0xFF2D3748) else Color(0xFFE2E8F0)

    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    // Scroll to today on first appearance
    var hasScrolledToToday by remember { mutableStateOf(false) }
    LaunchedEffect(generatedDates) {
        if (!hasScrolledToToday && generatedDates.isNotEmpty()) {
            val todayIdx = generatedDates.indexOf(today)
            if (todayIdx >= 0) {
                hScroll.scrollTo((todayIdx * cellWidthPx).toInt())
                hasScrolledToToday = true
            }
        }
    }

    // Load more dates dynamically when reaching scroll bounds
    LaunchedEffect(hScroll.value, hScroll.maxValue) {
        if (hScroll.maxValue > 0 && hScroll.value >= hScroll.maxValue - 200) {
            futureDaysCount += 30
        }
        if (hScroll.value <= 100 && pastDaysCount < 180) {
            pastDaysCount += 30
        }
    }

    // Quick Date Picker Dialog State
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    )

    // Cell Click Modal / BottomSheet State for quick status change
    var selectedCellSession by remember {
        mutableStateOf<Triple<CourseEntity, LocalDate, ScheduleSlotEntity?>?>(null)
    }

    // Compute student stats
    val stats = remember(allAttendance, courses, allSlots) {
        var totalMarked = 0
        var totalPresent = 0
        allAttendance.forEach { rec ->
            if (rec.studentId == "self") {
                totalMarked++
                if (rec.status.equals("P", ignoreCase = true) || rec.status.equals("present", ignoreCase = true)) {
                    totalPresent++
                }
            }
        }
        val pct = if (totalMarked > 0) (totalPresent * 100f) / totalMarked else 0f
        Triple(totalPresent, totalMarked, pct)
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Attendance Register",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge
                            )
                            val studentName = userProfile?.name?.takeIf { it.isNotBlank() } ?: "Student"
                            val branch = userProfile?.branchSectionYear?.takeIf { it.isNotBlank() } ?: "B.Tech CSE"
                            Text(
                                text = "$studentName • $branch",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Search Toggle Button
                        IconButton(onClick = { showSearchBar = !showSearchBar }) {
                            Icon(
                                imageVector = if (showSearchBar) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Search"
                            )
                        }

                        // Calendar Jump Picker Button
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Select Date")
                        }

                        // Jump to Today Button
                        TextButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                coroutineScope.launch {
                                    val todayIdx = generatedDates.indexOf(today)
                                    if (todayIdx >= 0) {
                                        hScroll.animateScrollTo((todayIdx * cellWidthPx).toInt())
                                    }
                                }
                            }
                        ) {
                            Text("Today", fontWeight = FontWeight.Bold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )

                AnimatedVisibility(visible = showSearchBar) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Filter courses by name or code...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                // Overall Stats Banner Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFEEF2FF)
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isDarkTheme) Color(0xFF334155) else Color(0xFFC7D2FE)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val pct = stats.third
                                val pctColor = if (pct >= 75f) Color(0xFF10B981) else Color(0xFFEF4444)
                                Text(
                                    text = "${String.format(Locale.ENGLISH, "%.1f", pct)}%",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = pctColor
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = pctColor.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, pctColor.copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        text = if (pct >= 75f) "Eligible (≥75%)" else "Shortage Alert",
                                        color = pctColor,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${stats.first} Present out of ${stats.second} recorded sessions",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Visual Mini Progress Ring or Indicator
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { (stats.third / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.size(44.dp),
                                strokeWidth = 5.dp,
                                color = if (stats.third >= 75f) Color(0xFF10B981) else Color(0xFFEF4444),
                                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                            Icon(
                                imageVector = if (stats.third >= 75f) Icons.Default.Check else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (stats.third >= 75f) Color(0xFF10B981) else Color(0xFFEF4444),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (filteredCourses.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isBlank()) "No courses enrolled yet" else "No matching courses",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // SPREADSHEET MATRIX (COURSES × DATES)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    // 1. DATA CELLS (Both Horizontal & Vertical Scrollable)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = leftColWidth, top = headerHeight)
                            .horizontalScroll(hScroll)
                            .verticalScroll(vScroll)
                    ) {
                        Column {
                            filteredCourses.forEachIndexed { courseIndex, course ->
                                val courseSlots = allSlots.filter { it.courseId == course.id }
                                val slotDayMap = courseSlots.groupBy { it.dayOfWeek.trim().lowercase() }

                                Row(
                                    modifier = Modifier
                                        .height(rowHeight)
                                        .background(
                                            if (courseIndex % 2 == 0) MaterialTheme.colorScheme.surface
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                        )
                                ) {
                                    generatedDates.forEach { date ->
                                        val fullDay = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase()
                                        val shortDay = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).lowercase()
                                        val matchingSlots = slotDayMap[fullDay] ?: slotDayMap[shortDay] ?: emptyList()
                                        val hasSlot = matchingSlots.isNotEmpty()
                                        val activeSlot = matchingSlots.firstOrNull()

                                        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                                        // Look up attendance record for this date and course/slot
                                        val record = allAttendance.firstOrNull {
                                            it.date == dateStr &&
                                            (it.studentId == "self") &&
                                            (activeSlot == null || it.scheduleSlotId == activeSlot.id)
                                        }

                                        val status = record?.status?.uppercase()
                                        val isToday = date == today

                                        Box(
                                            modifier = Modifier
                                                .width(cellWidth)
                                                .fillMaxHeight()
                                                .border(0.5.dp, gridBorderColor)
                                                .background(
                                                    if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
                                                    else Color.Transparent
                                                )
                                                .clickable(enabled = hasSlot || true) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    // Toggle or open cell session
                                                    selectedCellSession = Triple(course, date, activeSlot)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            when (status) {
                                                "P", "PRESENT" -> {
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = Color(0xFF10B981).copy(alpha = 0.18f),
                                                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                                                        modifier = Modifier.size(34.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Text(
                                                                text = "P",
                                                                color = Color(0xFF059669),
                                                                fontWeight = FontWeight.ExtraBold,
                                                                fontSize = 14.sp
                                                            )
                                                        }
                                                    }
                                                }
                                                "A", "ABSENT" -> {
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = Color(0xFFEF4444).copy(alpha = 0.18f),
                                                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                                                        modifier = Modifier.size(34.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Text(
                                                                text = "A",
                                                                color = Color(0xFFDC2626),
                                                                fontWeight = FontWeight.ExtraBold,
                                                                fontSize = 14.sp
                                                            )
                                                        }
                                                    }
                                                }
                                                "L", "LATE" -> {
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = Color(0xFFF59E0B).copy(alpha = 0.18f),
                                                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f)),
                                                        modifier = Modifier.size(34.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Text(
                                                                text = "L",
                                                                color = Color(0xFFD97706),
                                                                fontWeight = FontWeight.ExtraBold,
                                                                fontSize = 14.sp
                                                            )
                                                        }
                                                    }
                                                }
                                                else -> {
                                                    if (hasSlot) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(8.dp)
                                                                .clip(CircleShape)
                                                                .background(
                                                                    if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                                                )
                                                        )
                                                    } else {
                                                        Text(
                                                            text = "-",
                                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                                            fontSize = 14.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. STICKY TOP HEADER ROW (Dates - horizontal scroll only)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(headerHeight)
                            .padding(start = leftColWidth)
                            .horizontalScroll(hScroll)
                            .background(MaterialTheme.colorScheme.surface)
                            .shadow(2.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxHeight()) {
                            generatedDates.forEach { date ->
                                val isToday = date == today
                                val dayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                                val monthName = date.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

                                Box(
                                    modifier = Modifier
                                        .width(cellWidth)
                                        .fillMaxHeight()
                                        .border(0.5.dp, gridBorderColor)
                                        .background(
                                            if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            else MaterialTheme.colorScheme.surface
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = dayName,
                                            fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = date.dayOfMonth.toString(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = monthName,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                        if (isToday) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(4.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. STICKY LEFT COLUMN (Courses - vertical scroll only)
                    Box(
                        modifier = Modifier
                            .width(leftColWidth)
                            .fillMaxHeight()
                            .padding(top = headerHeight)
                            .verticalScroll(vScroll)
                            .background(MaterialTheme.colorScheme.surface)
                            .shadow(2.dp)
                    ) {
                        Column {
                            filteredCourses.forEachIndexed { index, course ->
                                val courseAttendance = allAttendance.filter {
                                    it.studentId == "self" && (
                                        allSlots.any { s -> s.courseId == course.id && s.id == it.scheduleSlotId } ||
                                        it.scheduleSlotId.contains(course.id)
                                    )
                                }
                                val presentCount = courseAttendance.count { it.status.equals("P", ignoreCase = true) }
                                val totalCount = courseAttendance.size
                                val coursePct = if (totalCount > 0) (presentCount * 100f) / totalCount else 0f
                                val isSafe = coursePct >= 75f

                                Box(
                                    modifier = Modifier
                                        .width(leftColWidth)
                                        .height(rowHeight)
                                        .border(0.5.dp, gridBorderColor)
                                        .background(
                                            if (index % 2 == 0) MaterialTheme.colorScheme.surface
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Column(verticalArrangement = Arrangement.Center) {
                                        Text(
                                            text = course.name,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = course.code.ifBlank { "Course" },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (totalCount > 0) {
                                                Text(
                                                    text = "${coursePct.toInt()}%",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = if (isSafe) Color(0xFF10B981) else Color(0xFFEF4444)
                                                )
                                            } else {
                                                Text(
                                                    text = "New",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. FIXED TOP-LEFT CORNER CELL (Header anchor)
                    Box(
                        modifier = Modifier
                            .size(width = leftColWidth, height = headerHeight)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(0.5.dp, gridBorderColor)
                            .shadow(4.dp)
                            .padding(12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Column {
                            Text(
                                text = "Enrolled Courses",
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${filteredCourses.size} Subjects",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Sheet or Dialog for Toggling/Setting Session Attendance
    if (selectedCellSession != null) {
        val (course, date, slot) = selectedCellSession!!
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val slotId = slot?.id ?: "slot_self_${course.id}"
        val record = allAttendance.firstOrNull {
            it.date == dateStr && it.studentId == "self" && (slot == null || it.scheduleSlotId == slot.id)
        }
        val currentStatus = record?.status ?: "NONE"

        AlertDialog(
            onDismissRequest = { selectedCellSession = null },
            title = {
                Column {
                    Text(course.name, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${date.format(DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy"))} • ${slot?.startTime ?: "Class"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "Change attendance status for this session:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Mark Present
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.markSelfAttendance(dateStr, slotId, course.id, "P")
                                selectedCellSession = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Present (P)", fontWeight = FontWeight.Bold)
                        }

                        // Mark Absent
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.markSelfAttendance(dateStr, slotId, course.id, "A")
                                selectedCellSession = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFEF4444),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Absent (A)", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.deleteAttendance(dateStr, slotId, "self")
                            selectedCellSession = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Clear Status", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedCellSession = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Date Picker Dialog
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDatePicker = false
                        val selectedMillis = datePickerState.selectedDateMillis
                        if (selectedMillis != null) {
                            val pickedDate = Instant.ofEpochMilli(selectedMillis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                            coroutineScope.launch {
                                val targetIdx = generatedDates.indexOf(pickedDate)
                                if (targetIdx >= 0) {
                                    hScroll.animateScrollTo((targetIdx * cellWidthPx).toInt())
                                } else {
                                    // Expand range if outside
                                    if (pickedDate.isBefore(today)) {
                                        pastDaysCount = maxOf(pastDaysCount, java.time.temporal.ChronoUnit.DAYS.between(pickedDate, today).toInt() + 15)
                                    } else {
                                        futureDaysCount = maxOf(futureDaysCount, java.time.temporal.ChronoUnit.DAYS.between(today, pickedDate).toInt() + 15)
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Text("Jump To Date")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
