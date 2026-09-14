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
import com.example.ui.components.FloatingGlassNavBar
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
        viewModel.autoMarkPastClassesAsAbsent()
    }

    var searchQuery by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }

    // Dynamic bidirectional infinite scroll range state (15 days window)
    var pastDaysCount by remember { mutableIntStateOf(15) }
    var futureDaysCount by remember { mutableIntStateOf(15) }

    val filteredCourses = remember(courses, searchQuery) {
        if (searchQuery.isBlank()) courses
        else courses.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.code.contains(searchQuery, ignoreCase = true)
        }
    }

    // Generate and precompute date columns from past to future
    val today = remember { LocalDate.now() }
    val studentDateCols = remember(pastDaysCount, futureDaysCount) {
        val list = ArrayList<StudentDateCol>(pastDaysCount + futureDaysCount + 1)
        val todayDate = LocalDate.now()
        for (i in -pastDaysCount.toLong()..futureDaysCount.toLong()) {
            val d = todayDate.plusDays(i)
            list.add(
                StudentDateCol(
                    date = d,
                    dateStr = d.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    isToday = d == todayDate,
                    dayName = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    monthName = d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    dayOfMonthStr = d.dayOfMonth.toString(),
                    dayOfWeekFull = d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase(),
                    dayOfWeekShort = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).lowercase()
                )
            )
        }
        list
    }

    // Precompute attendance lookup map for O(1) instant cell access (strictly course & slot keyed)
    val attendanceStatusMap = remember(allAttendance) {
        val map = HashMap<String, String>(allAttendance.size * 2 + 16)
        for (rec in allAttendance) {
            if (rec.studentId == "self") {
                val status = rec.status.uppercase()
                if (rec.scheduleSlotId.isNotBlank()) {
                    map["${rec.date}_${rec.scheduleSlotId}"] = status
                }
                if (rec.courseId.isNotBlank()) {
                    map["${rec.date}_${rec.courseId}"] = status
                }
            }
        }
        map
    }

    // Precompute slots map per course
    val courseSlotsMap = remember(allSlots, courses) {
        courses.associate { course ->
            val slots = allSlots.filter { it.courseId == course.id }
            val dayMap = slots.groupBy { it.dayOfWeek.trim().lowercase() }
            course.id to (slots to dayMap)
        }
    }

    // Precompute stats per course
    val courseStatsMap = remember(allAttendance, courses, allSlots) {
        val map = HashMap<String, Pair<Int, Int>>(courses.size + 8)
        val selfRecords = allAttendance.filter { it.studentId == "self" }
        for (c in courses) {
            val courseSlotIds = allSlots.filter { it.courseId == c.id }.map { it.id }.toSet()
            val cRecords = selfRecords.filter {
                (it.courseId == c.id) ||
                (it.scheduleSlotId.isNotBlank() && courseSlotIds.contains(it.scheduleSlotId)) ||
                it.scheduleSlotId.contains(c.id)
            }
            val pCount = cRecords.count {
                it.status.equals("P", true) || it.status.equals("PRESENT", true) ||
                it.status.equals("L", true) || it.status.equals("LATE", true)
            }
            map[c.id] = Pair(pCount, cRecords.size)
        }
        map
    }

    // Grid Dimensions
    val cellWidth = 72.dp
    val cellWidthPx = with(density) { cellWidth.toPx() }
    val leftColWidth = 215.dp
    val headerHeight = 72.dp
    val rowHeight = 72.dp
    val gridBorderColor = if (isDarkTheme) Color(0xFF2D3748) else Color(0xFFE2E8F0)

    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    // Scroll to today on first appearance
    var hasScrolledToToday by remember { mutableStateOf(false) }
    LaunchedEffect(studentDateCols) {
        if (!hasScrolledToToday && studentDateCols.isNotEmpty()) {
            val todayIdx = studentDateCols.indexOfFirst { it.isToday }
            if (todayIdx >= 0) {
                hScroll.scrollTo((todayIdx * cellWidthPx).toInt())
                hasScrolledToToday = true
            }
        }
    }

    // Load more dates dynamically in 15-day chunks when reaching scroll bounds
    LaunchedEffect(hScroll.value, hScroll.maxValue) {
        if (hScroll.maxValue > 0 && hScroll.value >= hScroll.maxValue - 200) {
            futureDaysCount += 15
        }
        if (hScroll.value <= 100 && pastDaysCount < 180) {
            pastDaysCount += 15
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

    // Compute comprehensive student stats across all courses
    val overallPresent = remember(allAttendance) {
        allAttendance.count { it.studentId == "self" && (it.status.equals("P", ignoreCase = true) || it.status.equals("present", ignoreCase = true)) }
    }
    val overallTotal = remember(allAttendance) {
        allAttendance.count { it.studentId == "self" }
    }
    val overallPct = remember(overallPresent, overallTotal) {
        if (overallTotal > 0) (overallPresent * 100f) / overallTotal else 0f
    }

    // Compute per-subject health summary
    val (atRiskSubjectsCount, totalSubjectsWithSessions) = remember(allAttendance, courses, allSlots) {
        var atRisk = 0
        var withSessions = 0
        courses.forEach { course ->
            val cAttendance = allAttendance.filter {
                it.studentId == "self" && (
                    allSlots.any { s -> s.courseId == course.id && s.id == it.scheduleSlotId } ||
                    it.scheduleSlotId.contains(course.id)
                )
            }
            if (cAttendance.isNotEmpty()) {
                withSessions++
                val p = cAttendance.count { it.status.equals("P", ignoreCase = true) || it.status.equals("present", ignoreCase = true) }
                val cPct = (p * 100f) / cAttendance.size
                if (cPct < 75f) {
                    atRisk++
                }
            }
        }
        Pair(atRisk, withSessions)
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
                        Text(
                            text = "Attendance Register",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
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
                                    val todayIdx = studentDateCols.indexOfFirst { it.isToday }
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

                // Merged Student Profile & Overall Attendance Stats Card
                val overallColor = when {
                    overallTotal == 0 -> Color(0xFF6B7280)
                    overallPct >= 75f -> Color(0xFF10B981)
                    overallPct >= 50f -> Color(0xFFF59E0B)
                    else -> Color(0xFFEF4444)
                }

                var isCardAnimated by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { isCardAnimated = true }
                val animatedOverallPct by animateFloatAsState(
                    targetValue = if (isCardAnimated) overallPct else 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "registerOverallPct"
                )

                val studentName = userProfile?.name?.takeIf { it.isNotBlank() } ?: "Sakshi Sharma"
                val branch = userProfile?.branchSectionYear?.takeIf { it.isNotBlank() } ?: "B.Tech CSE • 4th Sem • Sec A"
                val studentInitials = studentName.split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString("")

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .shadow(
                            elevation = 4.dp,
                            shape = RoundedCornerShape(26.dp),
                            spotColor = overallColor.copy(alpha = 0.22f),
                            ambientColor = Color.Black.copy(alpha = 0.08f)
                        ),
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        // 1. TOP HEADER: AVATAR + STUDENT NAME + BRANCH + STUDENT PILL
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .shadow(3.dp, CircleShape)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF10B981), Color(0xFF059669))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = studentInitials.ifEmpty { "SS" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = studentName,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF10B981).copy(alpha = 0.14f),
                                        border = BorderStroke(0.8.dp, Color(0xFF10B981).copy(alpha = 0.32f))
                                    ) {
                                        Text(
                                            text = "Student",
                                            color = Color(0xFF059669),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = branch,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(
                            color = if (isDarkTheme) Color(0xFF334155).copy(alpha = 0.6f) else Color(0xFFF1F5F9),
                            thickness = 1.dp
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        // 2. OVERALL ATTENDANCE STATS ROW
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (overallTotal > 0) "${String.format(Locale.ENGLISH, "%.1f", animatedOverallPct)}%" else "—",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Black,
                                        color = overallColor
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = overallColor.copy(alpha = 0.14f),
                                        border = BorderStroke(0.8.dp, overallColor.copy(alpha = 0.32f))
                                    ) {
                                        Text(
                                            text = when {
                                                overallTotal == 0 -> "No sessions yet"
                                                overallPct >= 75f -> "Eligible (≥75%)"
                                                overallPct >= 50f -> "Borderline"
                                                else -> "Shortage Alert"
                                            },
                                            color = overallColor,
                                            fontWeight = FontWeight.ExtraBold,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "$overallPresent attended out of $overallTotal recorded sessions across all subjects",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Circular progress indicator with ambient glow
                            Box(
                                modifier = Modifier.size(54.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(overallColor.copy(alpha = 0.12f))
                                )
                                CircularProgressIndicator(
                                    progress = { if (overallTotal > 0) (animatedOverallPct / 100f).coerceIn(0f, 1f) else 0f },
                                    modifier = Modifier.size(52.dp),
                                    strokeWidth = 5.5.dp,
                                    color = overallColor,
                                    trackColor = if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0)
                                )
                                Icon(
                                    imageVector = if (overallTotal == 0) Icons.Default.Event else if (overallPct >= 75f) Icons.Default.Check else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = overallColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(
                            color = if (isDarkTheme) Color(0xFF334155).copy(alpha = 0.5f) else Color(0xFFF1F5F9),
                            thickness = 1.dp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // 3. Bottom health indicator & subject navigation prompt
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (atRiskSubjectsCount > 0) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFEF4444).copy(alpha = 0.12f),
                                    border = BorderStroke(0.8.dp, Color(0xFFEF4444).copy(alpha = 0.30f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "$atRiskSubjectsCount subject${if (atRiskSubjectsCount > 1) "s" else ""} at risk (<75%)",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFEF4444)
                                        )
                                    }
                                }
                            } else if (totalSubjectsWithSessions > 0) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.12f),
                                    border = BorderStroke(0.8.dp, Color(0xFF10B981).copy(alpha = 0.30f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "All $totalSubjectsWithSessions subjects on track (≥75%)",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF10B981)
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Event,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Ready to record sessions",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "Tap a subject below for details ›",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
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
                                val (courseSlots, slotDayMap) = courseSlotsMap[course.id] ?: (emptyList<ScheduleSlotEntity>() to emptyMap<String, List<ScheduleSlotEntity>>())

                                Row(
                                    modifier = Modifier
                                        .height(rowHeight)
                                        .background(
                                            if (courseIndex % 2 == 0) MaterialTheme.colorScheme.surface
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                        )
                                ) {
                                    studentDateCols.forEach { col ->
                                        val matchingSlots = slotDayMap[col.dayOfWeekFull] ?: slotDayMap[col.dayOfWeekShort] ?: emptyList()
                                        val hasSlot = matchingSlots.isNotEmpty()
                                        val activeSlot = matchingSlots.firstOrNull()

                                        // Exact status lookup for THIS specific course and slot
                                        val status = matchingSlots.firstNotNullOfOrNull { slot ->
                                            attendanceStatusMap["${col.dateStr}_${slot.id}"]
                                        } ?: attendanceStatusMap["${col.dateStr}_${course.id}"]

                                        val isToday = col.isToday

                                        Box(
                                            modifier = Modifier
                                                .width(cellWidth)
                                                .fillMaxHeight()
                                                .border(
                                                    0.5.dp,
                                                    if (isDarkTheme) Color(0xFF334155).copy(alpha = 0.25f)
                                                    else Color(0xFFE2E8F0).copy(alpha = 0.5f)
                                                )
                                                .background(
                                                    if (isToday) Color(0xFF6366F1).copy(alpha = 0.04f)
                                                    else Color.Transparent
                                                )
                                                .clickable(enabled = hasSlot || true) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    // Toggle or open cell session
                                                    selectedCellSession = Triple(course, col.date, activeSlot)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            when (status) {
                                                "P", "PRESENT" -> {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(34.dp)
                                                            .shadow(
                                                                elevation = 3.dp,
                                                                shape = RoundedCornerShape(10.dp),
                                                                spotColor = Color(0xFF10B981).copy(alpha = 0.45f)
                                                            )
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .background(
                                                                Brush.linearGradient(
                                                                    listOf(Color(0xFF10B981), Color(0xFF059669))
                                                                )
                                                            )
                                                            .border(
                                                                width = 1.dp,
                                                                brush = Brush.linearGradient(
                                                                    listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.15f))
                                                                ),
                                                                shape = RoundedCornerShape(10.dp)
                                                             ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = "P",
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Black,
                                                            fontSize = 14.sp
                                                        )
                                                    }
                                                }
                                                "A", "ABSENT" -> {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(34.dp)
                                                            .shadow(
                                                                elevation = 3.dp,
                                                                shape = RoundedCornerShape(10.dp),
                                                                spotColor = Color(0xFFEF4444).copy(alpha = 0.45f)
                                                            )
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .background(
                                                                Brush.linearGradient(
                                                                    listOf(Color(0xFFEF4444), Color(0xFFDC2626))
                                                                )
                                                            )
                                                            .border(
                                                                width = 1.dp,
                                                                brush = Brush.linearGradient(
                                                                    listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.15f))
                                                                ),
                                                                shape = RoundedCornerShape(10.dp)
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = "A",
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Black,
                                                            fontSize = 14.sp
                                                        )
                                                    }
                                                }
                                                "L", "LATE" -> {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(34.dp)
                                                            .shadow(
                                                                elevation = 3.dp,
                                                                shape = RoundedCornerShape(10.dp),
                                                                spotColor = Color(0xFFF59E0B).copy(alpha = 0.45f)
                                                            )
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .background(
                                                                Brush.linearGradient(
                                                                    listOf(Color(0xFFF59E0B), Color(0xFFD97706))
                                                                )
                                                            )
                                                            .border(
                                                                width = 1.dp,
                                                                brush = Brush.linearGradient(
                                                                    listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.15f))
                                                                ),
                                                                shape = RoundedCornerShape(10.dp)
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = "L",
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Black,
                                                            fontSize = 14.sp
                                                        )
                                                    }
                                                }
                                                else -> {
                                                    if (hasSlot) {
                                                        Box(
                                                            modifier = Modifier.size(20.dp),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(16.dp)
                                                                    .clip(CircleShape)
                                                                    .background(
                                                                        if (isToday) Color(0xFF6366F1).copy(alpha = 0.15f)
                                                                        else Color(0xFF94A3B8).copy(alpha = 0.12f)
                                                                    )
                                                            )
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(7.dp)
                                                                    .clip(CircleShape)
                                                                    .background(
                                                                        if (isToday) Color(0xFF6366F1)
                                                                        else if (isDarkTheme) Color(0xFF64748B) else Color(0xFF94A3B8)
                                                                    )
                                                            )
                                                        }
                                                    } else {
                                                        Text(
                                                            text = "·",
                                                            color = if (isDarkTheme) Color(0xFF475569) else Color(0xFFCBD5E1),
                                                            fontSize = 18.sp,
                                                            fontWeight = FontWeight.Bold
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
                            .background(if (isDarkTheme) Color(0xFF0F172A) else Color(0xFFF8FAFC))
                            .shadow(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxHeight(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            studentDateCols.forEachIndexed { dateIdx, col ->
                                val isToday = col.isToday

                                val interactionSource = remember { MutableInteractionSource() }
                                val isPressed by interactionSource.collectIsPressedAsState()
                                val scale by animateFloatAsState(
                                    targetValue = if (isPressed) 0.94f else if (isToday) 1.02f else 1f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    ),
                                    label = "datePressScale"
                                )

                                Box(
                                    modifier = Modifier
                                        .width(cellWidth)
                                        .fillMaxHeight()
                                        .padding(horizontal = 3.dp, vertical = 3.dp)
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                        }
                                        .shadow(
                                            elevation = if (isToday) 6.dp else if (isPressed) 1.dp else 2.dp,
                                            shape = RoundedCornerShape(16.dp),
                                            spotColor = if (isToday) Color(0xFF6366F1).copy(alpha = 0.50f) else Color.Black.copy(alpha = 0.08f)
                                        )
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if (isToday) {
                                                Brush.verticalGradient(
                                                    listOf(Color(0xFF4F46E5), Color(0xFF6366F1))
                                                )
                                            } else {
                                                Brush.verticalGradient(
                                                    if (isDarkTheme) listOf(Color(0xFF1E293B), Color(0xFF1E293B))
                                                    else listOf(Color.White, Color(0xFFF8FAFC))
                                                )
                                            }
                                        )
                                        .border(
                                            width = 1.dp,
                                            brush = if (isToday) {
                                                Brush.linearGradient(
                                                    listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.2f))
                                                )
                                            } else {
                                                Brush.linearGradient(
                                                    if (isDarkTheme) listOf(Color(0xFF334155), Color(0xFF1E293B))
                                                    else listOf(Color(0xFFE2E8F0), Color(0xFFF1F5F9))
                                                )
                                            },
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .clickable(
                                            interactionSource = interactionSource,
                                            indication = null
                                        ) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            coroutineScope.launch {
                                                hScroll.animateScrollTo((dateIdx * cellWidthPx).toInt())
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = col.dayName,
                                            fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.SemiBold,
                                            fontSize = 11.sp,
                                            color = if (isToday) Color.White.copy(alpha = 0.85f) else Color(0xFF64748B)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = col.dayOfMonthStr,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 16.sp,
                                            color = if (isToday) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = col.monthName,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isToday) Color.White.copy(alpha = 0.75f) else Color(0xFF94A3B8)
                                        )
                                        if (isToday) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(5.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.White)
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
                            .background(if (isDarkTheme) Color(0xFF0F172A) else Color(0xFFF8FAFC))
                            .shadow(3.dp)
                    ) {
                        Column {
                            filteredCourses.forEachIndexed { index, course ->
                                val (presentCount, totalCount) = courseStatsMap[course.id] ?: Pair(0, 0)
                                val coursePct = if (totalCount > 0) (presentCount * 100f) / totalCount else 0f
                                val statusColor = when {
                                    totalCount == 0 -> Color(0xFF64748B)
                                    coursePct >= 75f -> Color(0xFF10B981)
                                    coursePct >= 50f -> Color(0xFFF59E0B)
                                    else -> Color(0xFFEF4444)
                                }

                                val interactionSource = remember { MutableInteractionSource() }
                                val isPressed by interactionSource.collectIsPressedAsState()
                                val scale by animateFloatAsState(
                                    targetValue = if (isPressed) 0.96f else 1f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    ),
                                    label = "courseRowScale"
                                )
                                val chevronOffset by animateDpAsState(
                                    targetValue = if (isPressed) 3.dp else 0.dp,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                                    label = "chevronOffset"
                                )

                                var isRowLoaded by remember { mutableStateOf(false) }
                                LaunchedEffect(Unit) { isRowLoaded = true }
                                val animatedBarPct by animateFloatAsState(
                                    targetValue = if (isRowLoaded && totalCount > 0) coursePct else 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                    ),
                                    label = "barPctAnim"
                                )

                                Box(
                                    modifier = Modifier
                                        .width(leftColWidth)
                                        .height(rowHeight)
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                        }
                                        .shadow(
                                            elevation = if (isPressed) 1.dp else 3.dp,
                                            shape = RoundedCornerShape(18.dp),
                                            spotColor = statusColor.copy(alpha = 0.20f)
                                        )
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(
                                            if (isPressed) statusColor.copy(alpha = 0.08f)
                                            else if (isDarkTheme) Color(0xFF1E293B)
                                            else Color.White
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isPressed) statusColor.copy(alpha = 0.50f)
                                            else if (isDarkTheme) Color(0xFF334155).copy(alpha = 0.6f)
                                            else Color(0xFFE2E8F0),
                                            shape = RoundedCornerShape(18.dp)
                                        )
                                        .clickable(
                                            interactionSource = interactionSource,
                                            indication = null,
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                navController.navigate("student_subject_detail/${course.id}")
                                            }
                                        )
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = course.name,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Icon(
                                                imageVector = Icons.Default.ChevronRight,
                                                contentDescription = "Open subject details",
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .offset(x = chevronOffset),
                                                tint = if (isPressed) statusColor else Color(0xFF94A3B8)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // Mini animated gradient progress bar
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(5.dp)
                                                .clip(CircleShape)
                                                .background(if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0))
                                        ) {
                                            if (totalCount > 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth(fraction = (animatedBarPct / 100f).coerceIn(0f, 1f))
                                                        .fillMaxHeight()
                                                        .clip(CircleShape)
                                                        .background(
                                                            Brush.horizontalGradient(
                                                                listOf(statusColor.copy(alpha = 0.8f), statusColor)
                                                            )
                                                        )
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = course.code.ifBlank { "Subject" },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF64748B),
                                                fontWeight = FontWeight.Medium
                                            )
                                            if (totalCount > 0) {
                                                Surface(
                                                    shape = RoundedCornerShape(7.dp),
                                                    color = statusColor.copy(alpha = 0.12f),
                                                    border = BorderStroke(0.8.dp, statusColor.copy(alpha = 0.30f))
                                                ) {
                                                    Text(
                                                        text = "${animatedBarPct.toInt()}%",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = statusColor,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            } else {
                                                Surface(
                                                    shape = RoundedCornerShape(7.dp),
                                                    color = (if (isDarkTheme) Color(0xFF334155) else Color(0xFFF1F5F9)).copy(alpha = 0.8f),
                                                    border = BorderStroke(0.8.dp, Color(0xFFCBD5E1).copy(alpha = 0.4f))
                                                ) {
                                                    Text(
                                                        text = "No sessions",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = Color(0xFF94A3B8),
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(100.dp))
                        }
                    }

                    // 4. FIXED TOP-LEFT CORNER CELL (Header anchor)
                    Box(
                        modifier = Modifier
                            .size(width = leftColWidth, height = headerHeight)
                            .background(if (isDarkTheme) Color(0xFF0F172A) else Color(0xFFF8FAFC))
                            .border(
                                1.dp,
                                if (isDarkTheme) Color(0xFF334155).copy(alpha = 0.5f) else Color(0xFFE2E8F0)
                            )
                            .shadow(3.dp)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Column {
                            Text(
                                text = "Enrolled Courses",
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color(0xFF4F46E5)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Tap row for full details ›",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }
            }

            // 5. FLOATING FROSTED GLASS BAR FOR STUDENT NAVIGATION
            FloatingGlassNavBar(
                selectedTabIndex = 1,
                onTabSelected = { tabIdx ->
                    when (tabIdx) {
                        0 -> navController.navigate("student_dashboard") {
                            popUpTo("student_dashboard") { inclusive = false }
                        }
                        1 -> { /* Already in register */ }
                        4 -> navController.navigate("profile")
                    }
                },
                onNavigateSchedule = {
                    navController.navigate("student_dashboard") {
                        popUpTo("student_dashboard") { inclusive = false }
                    }
                },
                onNavigateAIImport = {},
                onNavigateAddClass = {},
                onNavigateManageClasses = {},
                onNavigateProfile = { navController.navigate("profile") },
                isDarkTheme = isDarkTheme,
                role = "student",
                onNavigateReport = {},
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            )
        }
    }

    // Modal Sheet or Dialog for Toggling/Setting Session Attendance
    if (selectedCellSession != null) {
        val (course, date, slot) = selectedCellSession!!
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val slotId = slot?.id ?: "slot_self_${course.id}"
        val record = allAttendance.firstOrNull {
            it.date == dateStr && it.studentId == "self" && (
                (slot != null && it.scheduleSlotId == slot.id) ||
                it.courseId == course.id ||
                it.scheduleSlotId.contains(course.id)
            )
        }
        val currentStatus = record?.status?.uppercase() ?: "NONE"

        AlertDialog(
            onDismissRequest = { selectedCellSession = null },
            title = {
                Column {
                    Text(course.name, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${date.format(DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy"))} • ${slot?.startTime ?: "Scheduled Session"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "Current Status: ${if (currentStatus == "P") "Present (P)" else if (currentStatus == "A") "Absent (A)" else "Not Marked"}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (currentStatus == "P") Color(0xFF10B981) else if (currentStatus == "A") Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant
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
                            viewModel.deleteSelfAttendance(dateStr, slotId, course.id)
                            selectedCellSession = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Clear / Unmark Status", color = MaterialTheme.colorScheme.onSurface)
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
                                val targetIdx = studentDateCols.indexOfFirst { it.date == pickedDate }
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

data class StudentDateCol(
    val date: LocalDate,
    val dateStr: String,
    val isToday: Boolean,
    val dayName: String,
    val monthName: String,
    val dayOfMonthStr: String,
    val dayOfWeekFull: String,
    val dayOfWeekShort: String
)
