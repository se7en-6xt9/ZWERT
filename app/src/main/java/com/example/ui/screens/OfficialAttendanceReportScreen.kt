package com.example.ui.screens

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.data.OfficialAttendanceEntity
import com.example.data.OfficialClassEntity
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

data class OfficialSubjectSummary(
    val courseId: String,
    val courseName: String,
    val courseCode: String,
    val facultyName: String,
    val section: String,
    val room: String,
    val slots: List<OfficialClassEntity>
)

data class OfficialDateCol(
    val date: LocalDate,
    val dateStr: String,
    val isToday: Boolean,
    val dayName: String,
    val monthName: String,
    val dayOfMonthStr: String,
    val dayOfWeekFull: String,
    val dayOfWeekShort: String
)

@SuppressLint("NewApi")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficialAttendanceReportScreen(
    navController: NavController,
    viewModel: MainViewModel
) {
    BackHandler { navController.popBackStack() }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()

    val officialClasses by viewModel.activeOfficialClasses.collectAsState()
    val officialAttendance by viewModel.officialAttendance.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val studentEmail = remember { viewModel.getStudentEmail() }

    var isSyncingFeed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.populateDemoOfficialClassesIfEmpty()
        viewModel.syncOfficialClassesFromLocalSlots()
        viewModel.syncOfficialStudentFeed()
    }

    var searchQuery by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }

    // Dynamic bidirectional infinite scroll range state (15 days window)
    var pastDaysCount by remember { mutableIntStateOf(15) }
    var futureDaysCount by remember { mutableIntStateOf(15) }

    // Distinct official subjects grouped by courseId
    val officialSubjects = remember(officialClasses) {
        officialClasses.groupBy { it.courseId }.map { (cId, slots) ->
            val first = slots.first()
            OfficialSubjectSummary(
                courseId = cId,
                courseName = first.courseName,
                courseCode = first.courseCode,
                facultyName = first.facultyName,
                section = first.section,
                room = first.room,
                slots = slots
            )
        }
    }

    val filteredSubjects = remember(officialSubjects, searchQuery) {
        if (searchQuery.isBlank()) officialSubjects
        else officialSubjects.filter {
            it.courseName.contains(searchQuery, ignoreCase = true) ||
            it.courseCode.contains(searchQuery, ignoreCase = true) ||
            it.facultyName.contains(searchQuery, ignoreCase = true)
        }
    }

    // Generate and precompute date columns from past to future
    val today = remember { LocalDate.now() }
    val dateCols = remember(pastDaysCount, futureDaysCount) {
        val list = ArrayList<OfficialDateCol>(pastDaysCount + futureDaysCount + 1)
        val todayDate = LocalDate.now()
        for (i in -pastDaysCount.toLong()..futureDaysCount.toLong()) {
            val d = todayDate.plusDays(i)
            list.add(
                OfficialDateCol(
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

    // Precompute attendance lookup map for O(1) instant cell access (strictly isolated official_attendance)
    val attendanceStatusMap = remember(officialAttendance) {
        val map = HashMap<String, OfficialAttendanceEntity>(officialAttendance.size * 2 + 16)
        for (rec in officialAttendance) {
            val keySlot = "${rec.date}_${rec.slotId}"
            val keyCourse = "${rec.date}_${rec.courseId}"
            map[keySlot] = rec
            map[keyCourse] = rec
        }
        map
    }

    // Precompute stats per official subject
    val subjectStatsMap = remember(officialAttendance, officialSubjects) {
        val map = HashMap<String, Pair<Int, Int>>(officialSubjects.size + 8)
        for (s in officialSubjects) {
            val cRecords = officialAttendance.filter { it.courseId == s.courseId }
            val pCount = cRecords.count {
                it.status.equals("P", true) || it.status.equals("PRESENT", true) ||
                it.status.equals("L", true) || it.status.equals("LATE", true)
            }
            map[s.courseId] = Pair(pCount, cRecords.size)
        }
        map
    }

    // Grid Dimensions
    val cellWidth = 72.dp
    val cellWidthPx = with(density) { cellWidth.toPx() }
    val leftColWidth = 220.dp
    val headerHeight = 72.dp
    val rowHeight = 76.dp

    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    // Scroll to today on first appearance
    var hasScrolledToToday by remember { mutableStateOf(false) }
    LaunchedEffect(dateCols) {
        if (!hasScrolledToToday && dateCols.isNotEmpty()) {
            val todayIdx = dateCols.indexOfFirst { it.isToday }
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

    // Cell Click Modal / BottomSheet State for session inspection
    var selectedCellSession by remember {
        mutableStateOf<Triple<OfficialSubjectSummary, LocalDate, OfficialAttendanceEntity?>?>(null)
    }

    // Compute comprehensive stats across official attendance
    val overallPresent = remember(officialAttendance) {
        officialAttendance.count { it.status.equals("P", ignoreCase = true) || it.status.equals("PRESENT", ignoreCase = true) }
    }
    val overallTotal = remember(officialAttendance) {
        officialAttendance.size
    }
    val overallPct = remember(overallPresent, overallTotal) {
        if (overallTotal > 0) (overallPresent * 100f) / overallTotal else 0f
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Official Attendance Register",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Text(
                                text = "Faculty Verified • Live ERP Sync",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Sync Feed button
                        IconButton(
                            onClick = {
                                isSyncingFeed = true
                                viewModel.syncOfficialStudentFeed { msg, _ ->
                                    isSyncingFeed = false
                                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            if (isSyncingFeed) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Sync, contentDescription = "Sync Feed")
                            }
                        }

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
                                    val todayIdx = dateCols.indexOfFirst { it.isToday }
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
                        placeholder = { Text("Filter by subject or faculty...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                // Official Header Card
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
                    label = "officialRegisterOverallPct"
                )

                val studentName = userProfile?.name?.takeIf { it.isNotBlank() } ?: "Student"
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
                        // 1. TOP HEADER: AVATAR + STUDENT NAME + OFFICIAL FEED EMAIL BADGE
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
                                            listOf(Color(0xFF6366F1), Color(0xFF4F46E5))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = studentInitials.ifEmpty { "ST" },
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
                                        color = Color(0xFF6366F1).copy(alpha = 0.14f),
                                        border = BorderStroke(0.8.dp, Color(0xFF6366F1).copy(alpha = 0.32f))
                                    ) {
                                        Text(
                                            text = "Official ERP",
                                            color = Color(0xFF4F46E5),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = if (studentEmail.isNotBlank()) "Enrolled: $studentEmail" else "Campus Roster Feed",
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
                                                overallTotal == 0 -> "No faculty marks yet"
                                                overallPct >= 75f -> "Eligible (≥75%)"
                                                overallPct >= 50f -> "Low (50-74%)"
                                                else -> "Critical (<50%)"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = overallColor,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Official Faculty-Recorded Attendance",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Quick Stats Pill Box
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isDarkTheme) Color(0xFF0F172A) else Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "$overallPresent",
                                            fontWeight = FontWeight.ExtraBold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color(0xFF10B981)
                                        )
                                        Text(
                                            text = "Attended",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(26.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "$overallTotal",
                                            fontWeight = FontWeight.ExtraBold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Conducted",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
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
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (filteredSubjects.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isBlank()) "No official classes loaded yet" else "No matching subjects",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Sync your enrolled campus feed to view official class registers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    viewModel.syncOfficialClassesFromLocalSlots()
                                    viewModel.syncOfficialStudentFeed { msg, _ ->
                                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sync Official Feed")
                        }
                    }
                }
            } else {
                // SPREADSHEET MATRIX (SUBJECTS × DATES)
                Box(modifier = Modifier.fillMaxSize()) {
                    // 1. DATA CELLS (Both Horizontal & Vertical Scrollable)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = leftColWidth, top = headerHeight)
                            .clipToBounds()
                            .horizontalScroll(hScroll)
                            .verticalScroll(vScroll)
                    ) {
                        Column {
                            filteredSubjects.forEachIndexed { subjectIndex, subject ->
                                Row(
                                    modifier = Modifier
                                        .height(rowHeight)
                                        .background(
                                            if (subjectIndex % 2 == 0) MaterialTheme.colorScheme.surface
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                        )
                                ) {
                                    dateCols.forEach { col ->
                                        val hasSlotOnDay = subject.slots.any { slot ->
                                            val d = slot.dayOfWeek.trim().lowercase()
                                            d == col.dayOfWeekFull || d == col.dayOfWeekShort ||
                                            (d.startsWith("mon") && col.dayOfWeekShort.startsWith("mon")) ||
                                            (d.startsWith("tue") && col.dayOfWeekShort.startsWith("tue")) ||
                                            (d.startsWith("wed") && col.dayOfWeekShort.startsWith("wed")) ||
                                            (d.startsWith("thu") && col.dayOfWeekShort.startsWith("thu")) ||
                                            (d.startsWith("fri") && col.dayOfWeekShort.startsWith("fri")) ||
                                            (d.startsWith("sat") && col.dayOfWeekShort.startsWith("sat")) ||
                                            (d.startsWith("sun") && col.dayOfWeekShort.startsWith("sun"))
                                        }

                                        // Lookup official attendance record for this course/slot on this date
                                        val attRecord = subject.slots.firstNotNullOfOrNull { slot ->
                                            attendanceStatusMap["${col.dateStr}_${slot.slotId}"]
                                        } ?: attendanceStatusMap["${col.dateStr}_${subject.courseId}"]

                                        val status = attRecord?.status?.uppercase()
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
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    selectedCellSession = Triple(subject, col.date, attRecord)
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
                                                            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text("P", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
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
                                                            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text("A", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                                                    }
                                                }
                                                "CANCELLED", "C" -> {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(34.dp)
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .background(Color(0xFFF59E0B).copy(alpha = 0.18f))
                                                            .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text("C", color = Color(0xFFF59E0B), fontWeight = FontWeight.Black, fontSize = 13.sp)
                                                    }
                                                }
                                                else -> {
                                                    if (hasSlotOnDay) {
                                                        // Scheduled class slot awaiting teacher mark
                                                        Box(
                                                            modifier = Modifier
                                                                .size(24.dp)
                                                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = "·",
                                                                color = MaterialTheme.colorScheme.outline,
                                                                fontSize = 18.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    } else {
                                                        // No slot on this day
                                                        Text(
                                                            text = "—",
                                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                                            fontSize = 12.sp
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

                    // 2. FIXED STICKY TOP ROW: DATES HEADER (Scrolls horizontally with data)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = leftColWidth)
                            .height(headerHeight)
                            .clipToBounds()
                            .horizontalScroll(hScroll)
                    ) {
                        Row {
                            dateCols.forEach { col ->
                                val isToday = col.isToday
                                Box(
                                    modifier = Modifier
                                        .width(cellWidth)
                                        .height(headerHeight)
                                        .background(
                                            if (isToday) {
                                                if (isDarkTheme) Color(0xFF2E1A47) else Color(0xFFEEF2FF)
                                            } else {
                                                MaterialTheme.colorScheme.surface
                                            }
                                        )
                                        .border(
                                            0.5.dp,
                                            if (isDarkTheme) Color(0xFF334155).copy(alpha = 0.35f)
                                            else Color(0xFFE2E8F0)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = col.dayName.uppercase(),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            fontWeight = if (isToday) FontWeight.Black else FontWeight.Bold,
                                            color = if (isToday) Color(0xFF6366F1) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = col.dayOfMonthStr,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = if (isToday) FontWeight.Black else FontWeight.Bold,
                                            color = if (isToday) Color(0xFF6366F1) else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isToday) {
                                            Box(
                                                modifier = Modifier
                                                    .size(4.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF6366F1))
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. FIXED STICKY LEFT COLUMN: SUBJECTS (Scrolls vertically with data)
                    Box(
                        modifier = Modifier
                            .width(leftColWidth)
                            .fillMaxHeight()
                            .padding(top = headerHeight)
                            .clipToBounds()
                            .verticalScroll(vScroll)
                            .shadow(3.dp)
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        Column {
                            filteredSubjects.forEachIndexed { subjectIndex, subject ->
                                val stats = subjectStatsMap[subject.courseId]
                                val subPct = if (stats != null && stats.second > 0) (stats.first * 100f) / stats.second else 0f
                                val subColor = when {
                                    stats == null || stats.second == 0 -> Color(0xFF6B7280)
                                    subPct >= 75f -> Color(0xFF10B981)
                                    subPct >= 50f -> Color(0xFFF59E0B)
                                    else -> Color(0xFFEF4444)
                                }

                                Row(
                                    modifier = Modifier
                                        .width(leftColWidth)
                                        .height(rowHeight)
                                        .background(
                                            if (subjectIndex % 2 == 0) MaterialTheme.colorScheme.surface
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                        )
                                        .border(
                                            0.5.dp,
                                            if (isDarkTheme) Color(0xFF334155).copy(alpha = 0.35f)
                                            else Color(0xFFE2E8F0)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = subject.courseName,
                                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.5.sp),
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (subject.section.isNotBlank()) "Sec ${subject.section} • ${subject.facultyName}" else subject.facultyName,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = subColor.copy(alpha = 0.12f),
                                            border = BorderStroke(0.6.dp, subColor.copy(alpha = 0.30f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(5.dp)
                                                        .clip(CircleShape)
                                                        .background(subColor)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = if (stats != null && stats.second > 0) {
                                                        "${String.format(Locale.ENGLISH, "%.0f", subPct)}% (${stats.first}/${stats.second})"
                                                    } else {
                                                        "No marks yet"
                                                    },
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                                                    fontWeight = FontWeight.Bold,
                                                    color = subColor
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. TOP-LEFT CORNER PINNED BOX: "OFFICIAL SUBJECTS"
                    Box(
                        modifier = Modifier
                            .width(leftColWidth)
                            .height(headerHeight)
                            .shadow(4.dp)
                            .background(
                                if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFF1F5F9)
                            )
                            .border(
                                1.dp,
                                if (isDarkTheme) Color(0xFF334155) else Color(0xFFCBD5E1)
                            )
                            .padding(10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Column {
                            Text(
                                text = "OFFICIAL SUBJECTS",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${filteredSubjects.size} enrolled classes",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // Detail Modal for selected cell session
    if (selectedCellSession != null) {
        val (subject, date, att) = selectedCellSession!!
        val dateFormatted = date.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy"))
        val statusText = when (att?.status?.uppercase()) {
            "P", "PRESENT" -> "Marked Present by Faculty ✓"
            "A", "ABSENT" -> "Marked Absent by Faculty"
            "CANCELLED", "C" -> "Class Cancelled by Faculty"
            else -> "Awaiting Faculty Attendance Record"
        }
        val statusColor = when (att?.status?.uppercase()) {
            "P", "PRESENT" -> Color(0xFF10B981)
            "A", "ABSENT" -> Color(0xFFEF4444)
            "CANCELLED", "C" -> Color(0xFFF59E0B)
            else -> MaterialTheme.colorScheme.outline
        }

        AlertDialog(
            onDismissRequest = { selectedCellSession = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Official Session Record",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = statusColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    Text(
                        text = subject.courseName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Faculty: ${subject.facultyName}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = dateFormatted,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (subject.section.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Section: ${subject.section} • Room: ${subject.room}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Official ERP records are maintained and updated directly by your course faculty.",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
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
                                val targetIdx = dateCols.indexOfFirst { it.date == pickedDate }
                                if (targetIdx >= 0) {
                                    hScroll.animateScrollTo((targetIdx * cellWidthPx).toInt())
                                } else {
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
