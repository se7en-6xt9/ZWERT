package com.example.ui.screens

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.example.util.AttendanceExportHelper
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
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@SuppressLint("NewApi")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceReportScreen(navController: NavController, viewModel: MainViewModel, courseId: String) {
    BackHandler { navController.popBackStack() }

    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    var course by remember { mutableStateOf<CourseEntity?>(null) }
    var students by remember { mutableStateOf<List<StudentEntity>>(emptyList()) }
    val slots by viewModel.getScheduleSlotsForCourse(courseId).collectAsState(initial = emptyList())
    // Data source: genuine saved/submitted attendance records from database
    val attendance by viewModel.getAttendanceForCourse(courseId).collectAsState(initial = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }

    // Dynamic bidirectional infinite scroll range state - Initialized to 15 days past and 15 days future as requested
    var pastDaysCount by remember { mutableIntStateOf(15) }
    var futureDaysCount by remember { mutableIntStateOf(15) }

    LaunchedEffect(courseId) {
        course = viewModel.getCourseById(courseId)
        students = viewModel.getStudentsByCourseSync(courseId)
    }

    val filteredStudents = remember(students, searchQuery) {
        if (searchQuery.isBlank()) students
        else students.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.rollNumber.contains(searchQuery, ignoreCase = true)
        }
    }

    // Precomputed column information to eliminate formatting and calculation overhead inside the grid loop
    val dateColumns = remember(slots, pastDaysCount, futureDaysCount) {
        val today = LocalDate.now()
        val list = ArrayList<DateColumnInfo>()
        val daySlotMap = slots.groupBy { it.dayOfWeek.trim().lowercase() }
        val dayPattern = DateTimeFormatter.ofPattern("dd MMM")
        val dayYearPattern = DateTimeFormatter.ofPattern("dd MMM ''yy")

        for (i in -pastDaysCount.toLong()..futureDaysCount.toLong()) {
            val d = today.plusDays(i)
            val fullDay = d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase()
            val shortDay = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).lowercase()
            val isToday = d == today
            val dateStr = d.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val dayName = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase()
            val dateFormatted = d.format(if (d.year == today.year) dayPattern else dayYearPattern)

            val matchingSlots = daySlotMap[fullDay] ?: daySlotMap[shortDay]
            if (!matchingSlots.isNullOrEmpty()) {
                matchingSlots.forEach { slot ->
                    list.add(DateColumnInfo(d, slot, dateStr, isToday, dayName, dateFormatted))
                }
            } else if (slots.isEmpty()) {
                val defaultSlot = ScheduleSlotEntity(
                    id = "default_${d}_$courseId",
                    courseId = courseId,
                    dayOfWeek = d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                    startTime = "Session",
                    endTime = "",
                    room = "",
                    section = ""
                )
                list.add(DateColumnInfo(d, defaultSlot, dateStr, isToday, dayName, dateFormatted))
            }
        }
        list
    }

    // High-performance O(1) status lookup map: (studentId_dateStr_slotId) -> status string
    val attendanceStatusMap = remember(attendance) {
        val map = HashMap<String, String>(attendance.size + 16)
        for (rec in attendance) {
            map["${rec.studentId}_${rec.date}_${rec.scheduleSlotId}"] = rec.status
        }
        map
    }

    // High-performance O(1) student summary statistics map: studentId -> Pair(presentCount, totalRecorded)
    val studentStatsMap = remember(attendance, students) {
        val map = HashMap<String, Pair<Int, Int>>(students.size + 16)
        val byStudent = attendance.groupBy { it.studentId }
        for (s in students) {
            val records = byStudent[s.id] ?: emptyList()
            val pCount = records.count { it.status == "P" || it.status == "L" }
            map[s.id] = Pair(pCount, records.size)
        }
        map
    }

    // Excel Grid Dimensions
    val cellWidth = 78.dp
    val cellWidthPx = with(density) { cellWidth.toPx() }
    val leftColWidth = 195.dp
    val headerHeight = 70.dp
    val rowHeight = 60.dp
    val gridBorderColor = Color(0xFFE2E8F0)

    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    // Default to Today's column as the first visible/leftmost column on initial load
    var hasScrolledToToday by remember { mutableStateOf(false) }
    LaunchedEffect(dateColumns) {
        if (!hasScrolledToToday && dateColumns.isNotEmpty()) {
            val today = LocalDate.now()
            val todayIdx = dateColumns.indexOfFirst { it.date == today }
            val targetIdx = if (todayIdx >= 0) todayIdx else {
                dateColumns.indexOfFirst { !it.date.isBefore(today) }.takeIf { it >= 0 } ?: 0
            }
            hScroll.scrollTo((targetIdx * cellWidthPx).toInt())
            hasScrolledToToday = true
        }
    }

    // Dynamic bidirectional loading as user approaches edges (15 days increment)
    var isExtendingDates by remember { mutableStateOf(false) }
    LaunchedEffect(hScroll.value, hScroll.maxValue) {
        if (hScroll.maxValue > 0 && !isExtendingDates) {
            // Approaching right edge (future dates)
            if (hScroll.value > hScroll.maxValue - (cellWidthPx * 3)) {
                if (futureDaysCount < 120) {
                    isExtendingDates = true
                    futureDaysCount += 15
                    isExtendingDates = false
                }
            }
            // Approaching left edge (past dates)
            else if (hScroll.value < (cellWidthPx * 2)) {
                if (pastDaysCount < 120) {
                    isExtendingDates = true
                    pastDaysCount += 15
                    isExtendingDates = false
                }
            }
        }
    }

    // Date Picker Dialog
    var showDatePicker by remember { mutableStateOf(false) }
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    datePickerState.selectedDateMillis?.let { millis ->
                        val selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                        val idx = dateColumns.indexOfFirst { it.date == selectedDate }
                        if (idx >= 0) {
                            coroutineScope.launch { hScroll.animateScrollTo((idx * cellWidthPx).toInt()) }
                        } else {
                            val diff = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), selectedDate)
                            if (diff < 0) pastDaysCount = (-diff + 15).toInt().coerceAtMost(180)
                            else futureDaysCount = (diff + 15).toInt().coerceAtMost(180)
                        }
                    }
                }) { Text("Jump to Date", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Cell Detail & Correction BottomSheet State
    var activeCellDetail by remember { mutableStateOf<CellDetailData?>(null) }
    
    var showExportDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    val context = LocalContext.current

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF6750A4),
            background = Color.White,
            surface = Color.White,
            surfaceVariant = Color(0xFFF1F5F9)
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = course?.name?.takeIf { it.isNotBlank() } ?: "Attendance Register",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                fontSize = 17.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val subTitle = course?.code?.takeIf { it.isNotBlank() } ?: "Official Attendance Sheet"
                            Text(
                                text = subTitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF64748B)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF0F172A))
                        }
                    },
                    actions = {
                        // Jump to Today Shortcut Button
                        FilledTonalButton(
                            onClick = {
                                val today = LocalDate.now()
                                val todayIdx = dateColumns.indexOfFirst { it.date == today }
                                if (todayIdx >= 0) {
                                    coroutineScope.launch {
                                        hScroll.animateScrollTo((todayIdx * cellWidthPx).toInt())
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFFEDE9FE),
                                contentColor = Color(0xFF6D28D9)
                            )
                        ) {
                            Icon(Icons.Default.Today, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Today", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        // Search Toggle
                        IconButton(onClick = { showSearchBar = !showSearchBar }) {
                            Icon(
                                if (showSearchBar) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color(0xFF0F172A)
                            )
                        }

                        // Calendar Jump
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Pick Date", tint = Color(0xFF0F172A))
                        }
                        
                        // Export Button
                        IconButton(onClick = { showExportDialog = true }) {
                            Icon(Icons.Default.Share, contentDescription = "Export", tint = Color(0xFF0F172A))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            },
            containerColor = Color(0xFFF8F9FA)
        ) { paddingValues ->
            if (students.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF6750A4))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Loading class register...", color = Color.Gray)
                    }
                }
                return@Scaffold
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color(0xFFF8F9FA))
            ) {
                // Optional Search Bar
                AnimatedVisibility(visible = showSearchBar) {
                    Surface(
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth(),
                        shadowElevation = 2.dp
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Filter student by name or roll number...", fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .height(48.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Color(0xFFCBD5E1),
                                focusedBorderColor = Color(0xFF6750A4)
                            )
                        )
                    }
                }

                // Legend & Attendance Summary Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        LegendBadge("P", "Present", Color(0xFF16A34A), Color(0xFFDCFCE7))
                        LegendBadge("A", "Absent", Color(0xFFDC2626), Color(0xFFFEE2E2))
                        LegendBadge("L", "Late", Color(0xFFD97706), Color(0xFFFEF3C7))
                    }

                    Text(
                        "${filteredStudents.size} Students",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Bold
                    )
                }

                Divider(color = gridBorderColor, thickness = 1.dp)

                // EXCEL-LIKE FROZEN PANES SHEET
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White)
                ) {
                    // 1. DATA GRID (Main Bottom-Right Grid: Scrolls horizontally and vertically)
                    Box(
                        modifier = Modifier
                            .padding(start = leftColWidth, top = headerHeight)
                            .fillMaxSize()
                            .horizontalScroll(hScroll)
                            .verticalScroll(vScroll)
                    ) {
                        Column {
                            filteredStudents.forEachIndexed { rowIndex, student ->
                                val isZebra = rowIndex % 2 != 0
                                val studentRowBg = if (isZebra) Color(0xFFF8FAFC) else Color.White

                                Row(modifier = Modifier.height(rowHeight)) {
                                    dateColumns.forEach { col ->
                                        val status = attendanceStatusMap["${student.id}_${col.dateStr}_${col.slot.id}"]

                                        val cellBg = when {
                                            col.isToday -> if (isZebra) Color(0xFFF5F3FF) else Color(0xFFFAF5FF)
                                            else -> studentRowBg
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(width = cellWidth, height = rowHeight)
                                                .background(cellBg)
                                                .border(0.5.dp, if (col.isToday) Color(0xFFC4B5FD) else gridBorderColor)
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    activeCellDetail = CellDetailData(
                                                        student = student,
                                                        date = col.date,
                                                        dateStr = col.dateStr,
                                                        slot = col.slot,
                                                        currentStatus = status
                                                    )
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (status != null) {
                                                val (statusColor, statusBg, statusBorder) = when (status) {
                                                    "P" -> Triple(Color(0xFF15803D), Color(0xFFDCFCE7), Color(0xFF86EFAC))
                                                    "A" -> Triple(Color(0xFFB91C1C), Color(0xFFFEE2E2), Color(0xFFFCA5A5))
                                                    "L" -> Triple(Color(0xFFB45309), Color(0xFFFEF3C7), Color(0xFFFDE68A))
                                                    else -> Triple(Color.Gray, Color(0xFFF1F5F9), Color(0xFFCBD5E1))
                                                }

                                                Surface(
                                                    shape = CircleShape,
                                                    color = statusBg,
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, statusBorder),
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = status,
                                                            color = statusColor,
                                                            fontWeight = FontWeight.Black,
                                                            fontSize = 12.sp
                                                        )
                                                    }
                                                }
                                            } else {
                                                Text(
                                                    text = "—",
                                                    color = Color(0xFFCBD5E1),
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. FROZEN STICKY HEADER ROW (Top-Right: Syncs with hScroll)
                    Box(
                        modifier = Modifier
                            .padding(start = leftColWidth)
                            .fillMaxWidth()
                            .height(headerHeight)
                            .clipToBounds()
                            .background(Color.White)
                    ) {
                        Row(
                            modifier = Modifier
                                .horizontalScroll(hScroll)
                                .height(headerHeight)
                        ) {
                            dateColumns.forEach { col ->
                                val headerBg = if (col.isToday) Color(0xFFF3E8FF) else Color(0xFFF8FAFC)
                                val headerBorder = if (col.isToday) Color(0xFF8B5CF6) else gridBorderColor

                                Column(
                                    modifier = Modifier
                                        .size(width = cellWidth, height = headerHeight)
                                        .background(headerBg)
                                        .border(0.5.dp, headerBorder),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    // Top Accent Bar for Today
                                    if (col.isToday) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(3.5.dp)
                                                .background(Color(0xFF6750A4))
                                        )
                                        Surface(
                                            color = Color(0xFF6750A4),
                                            shape = RoundedCornerShape(3.dp),
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            Text(
                                                "TODAY",
                                                color = Color.White,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Black,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                    // STACKED DAY & DATE HEADERS (Clear and Unmistakable)
                                    Text(
                                        text = col.dayName,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 0.5.sp
                                        ),
                                        color = if (col.isToday) Color(0xFF6D28D9) else Color(0xFF0F172A)
                                    )

                                    Text(
                                        text = col.dateFormatted,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = if (col.isToday) Color(0xFF6D28D9) else Color(0xFF64748B)
                                    )

                                    if (col.slot.startTime.isNotBlank() && col.slot.startTime != "Session") {
                                        Text(
                                            text = col.slot.startTime,
                                            fontSize = 8.sp,
                                            color = Color(0xFF94A3B8),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        // Subtle bottom drop shadow on header row for Excel frozen pane effect
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .align(Alignment.BottomStart)
                                .background(Color(0x0F000000))
                        )
                    }

                    // 3. FROZEN STICKY STUDENT COLUMN (Bottom-Left: Syncs with vScroll)
                    Box(
                        modifier = Modifier
                            .padding(top = headerHeight)
                            .width(leftColWidth)
                            .fillMaxHeight()
                            .clipToBounds()
                            .background(Color.White)
                    ) {
                        Column(
                            modifier = Modifier
                                .verticalScroll(vScroll)
                                .width(leftColWidth)
                        ) {
                            filteredStudents.forEachIndexed { rowIndex, student ->
                                val isZebra = rowIndex % 2 != 0
                                val studentRowBg = if (isZebra) Color(0xFFF8FAFC) else Color.White

                                // Calculate Att % based purely on precomputed statistics map in O(1)
                                val (presentCount, totalRecorded) = studentStatsMap[student.id] ?: Pair(0, 0)
                                val percentage = if (totalRecorded > 0) {
                                    ((presentCount.toFloat() / totalRecorded) * 100).toInt()
                                } else 0

                                val (pctTextColor, pctBgColor) = when {
                                    totalRecorded == 0 -> Color(0xFF64748B) to Color(0xFFF1F5F9)
                                    percentage >= 75 -> Color(0xFF15803D) to Color(0xFFDCFCE7)
                                    percentage >= 50 -> Color(0xFFB45309) to Color(0xFFFEF3C7)
                                    else -> Color(0xFFB91C1C) to Color(0xFFFEE2E2)
                                }

                                Row(
                                    modifier = Modifier
                                        .size(width = leftColWidth, height = rowHeight)
                                        .background(studentRowBg)
                                        .border(0.5.dp, gridBorderColor)
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                                        Text(
                                            text = student.name,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A),
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = student.rollNumber,
                                            color = Color(0xFF64748B),
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Color-coded Att. % Threshold Badge
                                    Surface(
                                        color = pctBgColor,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (totalRecorded > 0) "$percentage%" else "—",
                                            fontWeight = FontWeight.ExtraBold,
                                            color = pctTextColor,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Subtle right drop shadow for frozen student column
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .align(Alignment.CenterEnd)
                                .background(Color(0x0F000000))
                        )
                    }

                    // 4. FROZEN TOP-LEFT CORNER (Pinned completely)
                    Box(
                        modifier = Modifier
                            .size(width = leftColWidth, height = headerHeight)
                            .background(Color(0xFFF1F5F9))
                            .border(0.5.dp, gridBorderColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.People, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "STUDENT",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF334155),
                                    fontSize = 11.sp,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            Text(
                                "ATT %",
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF334155),
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp
                            )
                        }

                        // Bottom and Right divider accents
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .align(Alignment.BottomStart)
                                .background(gridBorderColor)
                        )
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .fillMaxHeight()
                                .align(Alignment.CenterEnd)
                                .background(gridBorderColor)
                        )
                    }
                }
            }
        }
    }

    // Cell Detail & Correction Popover / Bottom Sheet
    activeCellDetail?.let { detail ->
        ModalBottomSheet(
            onDismissRequest = { activeCellDetail = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 36.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEDE9FE)),
                        contentAlignment = Alignment.Center
                    ) {
                        val initials = detail.student.name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")
                        Text(
                            text = if (initials.isNotBlank()) initials else "S",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6D28D9),
                            fontSize = 18.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = detail.student.name,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Roll No: ${detail.student.rollNumber}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = Color(0xFFE2E8F0))
                Spacer(modifier = Modifier.height(16.dp))

                // Session Timing Details
                val formattedFullDate = detail.date.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy"))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Session Date", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                        Text(formattedFullDate, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A), fontSize = 14.sp)
                    }
                    if (detail.slot.startTime.isNotBlank()) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Schedule Time", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                            val slotTime = if (detail.slot.endTime.isNotBlank()) "${detail.slot.startTime} - ${detail.slot.endTime}" else detail.slot.startTime
                            Text(slotTime, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A), fontSize = 14.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Current Saved Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Saved Status:", fontWeight = FontWeight.Medium, color = Color(0xFF475569))
                    val statusText = when (detail.currentStatus) {
                        "P" -> "Present"
                        "A" -> "Absent"
                        "L" -> "Late"
                        else -> "Not Marked"
                    }
                    val statusColor = when (detail.currentStatus) {
                        "P" -> Color(0xFF16A34A)
                        "A" -> Color(0xFFDC2626)
                        "L" -> Color(0xFFD97706)
                        else -> Color(0xFF64748B)
                    }
                    Surface(
                        color = statusColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = statusText,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text("Correct / Change Attendance Status:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))
                Spacer(modifier = Modifier.height(12.dp))

                // Correction Actions Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.markAttendance(detail.dateStr, detail.slot.id, detail.student.id, "P")
                            activeCellDetail = null
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Present")
                    }

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.markAttendance(detail.dateStr, detail.slot.id, detail.student.id, "A")
                            activeCellDetail = null
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Absent")
                    }

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.markAttendance(detail.dateStr, detail.slot.id, detail.student.id, "L")
                            activeCellDetail = null
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Late")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.markAttendance(detail.dateStr, detail.slot.id, detail.student.id, "NONE")
                        activeCellDetail = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF64748B))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear Recorded Attendance", color = Color(0xFF64748B))
                }
            }
        }
    }

    if (showExportDialog) {
        ExportAttendanceDialog(
            onDismiss = { showExportDialog = false },
            onExport = { options ->
                showExportDialog = false
                isExporting = true
                viewModel.exportAttendanceData(context, courseId, options) { file ->
                    isExporting = false
                    if (file != null) {
                        Toast.makeText(context, "Export successful", Toast.LENGTH_SHORT).show()
                        AttendanceExportHelper.openExportedFile(context, file, options.format)
                    } else {
                        Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    if (isExporting) {
        AlertDialog(
            onDismissRequest = { },
            confirmButton = { },
            title = { Text("Exporting...") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Generating export file...")
                }
            }
        )
    }
}

data class CellDetailData(
    val student: StudentEntity,
    val date: LocalDate,
    val dateStr: String,
    val slot: ScheduleSlotEntity,
    val currentStatus: String?
)

data class DateColumnInfo(
    val date: LocalDate,
    val slot: ScheduleSlotEntity,
    val dateStr: String,
    val isToday: Boolean,
    val dayName: String,
    val dateFormatted: String
)

@Composable
fun LegendBadge(status: String, label: String, color: Color, bgColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = bgColor,
            border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f)),
            modifier = Modifier.size(18.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(status, color = color, fontWeight = FontWeight.Bold, fontSize = 9.sp)
            }
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = Color(0xFF475569), fontWeight = FontWeight.Medium)
    }
}
