package com.example.ui.screens

import android.annotation.SuppressLint
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import com.example.data.CourseEntity
import com.example.data.ScheduleSlotEntity
import com.example.ui.components.FloatingGlassNavBar
import com.example.ui.util.SoundFeedbackHelper
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDashboardScreen(navController: NavController, viewModel: MainViewModel) {
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val accentColor = Color(0xFF6366F1) // Indigo accent matching design system

    val colorScheme = if (isDarkTheme) {
        darkColorScheme(
            primary = accentColor,
            surface = Color(0xFF1E1E24).copy(alpha = 0.6f),
            background = Color(0xFF121212)
        )
    } else {
        lightColorScheme(
            primary = accentColor,
            surface = Color(0xFFFFFFFF).copy(alpha = 0.7f),
            background = Color(0xFFF0F4F8)
        )
    }

    val animatedPrimary by animateColorAsState(colorScheme.primary, tween(500), label = "primary")
    val animatedSurface by animateColorAsState(colorScheme.surface, tween(500), label = "surface")
    val animatedBackground by animateColorAsState(colorScheme.background, tween(500), label = "background")

    val animatedScheme = colorScheme.copy(
        primary = animatedPrimary,
        surface = animatedSurface,
        background = animatedBackground
    )

    MaterialTheme(colorScheme = animatedScheme) {
        StudentDashboardContent(
            navController = navController,
            viewModel = viewModel,
            isDarkTheme = isDarkTheme,
            accentColor = accentColor
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDashboardContent(
    navController: NavController,
    viewModel: MainViewModel,
    isDarkTheme: Boolean,
    accentColor: Color
) {
    val today = remember { LocalDate.now() }
    val currentDayIndex = maxOf(0, today.dayOfWeek.value - 1)

    var currentLiveTime by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15000L)
            currentLiveTime = LocalTime.now()
        }
    }

    val weekDates = remember {
        val startOfWeek = today.minusDays(currentDayIndex.toLong())
        (0..6).map { startOfWeek.plusDays(it.toLong()) }
    }

    val pagerState = rememberPagerState(initialPage = currentDayIndex, pageCount = { 7 })
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    var currentTab by remember { mutableIntStateOf(0) }

    // Auto-sync from cloud if logged in
    LaunchedEffect(Unit) {
        viewModel.syncDataFromFirebase()
    }

    val allCourses by viewModel.getAllCourses().collectAsState(initial = emptyList())
    val allSlots by viewModel.getAllScheduleSlots().collectAsState(initial = emptyList())
    val courseMap = remember(allCourses) { allCourses.associateBy { it.id } }
    val allAttendance by viewModel.getAllAttendance().collectAsState(initial = emptyList())

    // Map course ID to this student's attendance stats for that specific course (present, total)
    val courseAttendanceMap = remember(allAttendance, allCourses, allSlots) {
        val selfAttendance = allAttendance.filter { it.studentId == "self" }
        allCourses.associate { course ->
            val courseRecords = selfAttendance.filter {
                allSlots.any { s -> s.courseId == course.id && s.id == it.scheduleSlotId } ||
                it.scheduleSlotId.contains(course.id)
            }
            val p = courseRecords.count { it.status.equals("P", ignoreCase = true) || it.status.equals("present", ignoreCase = true) }
            course.id to Pair(p, courseRecords.size)
        }
    }

    // Overall attendance stats
    val attendanceStats = remember(allAttendance) {
        val selfRecords = allAttendance.filter { it.studentId == "self" }
        val total = selfRecords.size
        val present = selfRecords.count { it.status.equals("P", ignoreCase = true) || it.status.equals("present", ignoreCase = true) }
        val pct = if (total > 0) (present * 100f) / total else 0f
        Triple(present, total, pct)
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedGradientMesh(accentColor = accentColor, isDark = isDarkTheme)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                Spacer(modifier = Modifier.height(6.dp))

                // 1. ELEVATED STUDENT PROFILE HEADER (Merged with overall stats)
                ElevatedStudentProfileHeader(
                    viewModel = viewModel,
                    navController = navController,
                    dateStr = today.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                    stats = attendanceStats,
                    accentColor = accentColor,
                    isDarkTheme = isDarkTheme
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 3. DAY-SELECTOR ROW WITH SLIDING INDICATOR & EDGE FADES
                DaySelectorCard(
                    weekDates = weekDates,
                    pagerState = pagerState,
                    today = today,
                    isDarkTheme = isDarkTheme,
                    coroutineScope = coroutineScope,
                    haptic = haptic
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 4. HORIZONTAL PAGER FOR SWIPEABLE SCHEDULE DAYS
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    val pageDate = weekDates[page]
                    val isTodayPage = pageDate == today
                    val dayName = pageDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                    val flow = remember(dayName) { viewModel.getScheduleForDay(dayName) }
                    val scheduleSlots by flow.collectAsState(initial = emptyList())
                    var isLoading by remember { mutableStateOf(true) }
                    val listState = rememberLazyListState()

                    LaunchedEffect(page) {
                        isLoading = true
                        delay(200)
                        isLoading = false
                    }

                    LaunchedEffect(scheduleSlots, isLoading) {
                        if (!isLoading && scheduleSlots.isNotEmpty() && isTodayPage) {
                            val liveIndex = scheduleSlots.indexOfFirst { slot ->
                                isSlotLive(slot, LocalTime.now())
                            }
                            if (liveIndex >= 0) {
                                delay(200)
                                listState.animateScrollToItem(liveIndex)
                            }
                        }
                    }

                    AnimatedContent(
                        targetState = isLoading,
                        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                        label = "load_anim"
                    ) { loading ->
                        if (loading) {
                            LazyColumn(
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(3) { SkeletonCard() }
                            }
                        } else if (scheduleSlots.isEmpty()) {
                            EmptyStudentScheduleIllustration(
                                day = dayName,
                                onImportAI = { navController.navigate("import_timetable") }
                            )
                        } else {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                itemsIndexed(scheduleSlots, key = { _, slot -> slot.id }) { index, slot ->
                                    val isLive = isTodayPage && isSlotLive(slot, currentLiveTime)
                                    val timeHint = if (isTodayPage) getRelativeTimeHint(slot, currentLiveTime) else null
                                    val dateStr = pageDate.format(DateTimeFormatter.ISO_LOCAL_DATE)

                                    // Check if student already marked attendance for this slot on this date
                                    val attendanceRecord = allAttendance.firstOrNull {
                                        it.date == dateStr && it.scheduleSlotId == slot.id && it.studentId == "self"
                                    }
                                    val isMarkedPresent = attendanceRecord?.status?.equals("P", ignoreCase = true) == true ||
                                            attendanceRecord?.status?.equals("present", ignoreCase = true) == true

                                    val subjectStats = courseAttendanceMap[slot.courseId]

                                    StaggeredAnimatedItem(index = index) {
                                        StudentGlassLectureCard(
                                            slot = slot,
                                            course = courseMap[slot.courseId],
                                            isLive = isLive,
                                            isMarkedPresent = isMarkedPresent,
                                            timeHint = timeHint,
                                            subjectStats = subjectStats,
                                            onMarkSelfAttendance = {
                                                val ctx = context
                                                if (isMarkedPresent) {
                                                    // Undo / Unmark attendance if clicked again
                                                    SoundFeedbackHelper.performSuccessHaptic(ctx)
                                                    viewModel.deleteAttendance(
                                                        date = dateStr,
                                                        slotId = slot.id,
                                                        studentId = "self"
                                                    )
                                                } else {
                                                    // Mark attendance as Present
                                                    SoundFeedbackHelper.playApplePaySuccessDing(ctx)
                                                    SoundFeedbackHelper.performSuccessHaptic(ctx)
                                                    viewModel.markSelfAttendance(
                                                        date = dateStr,
                                                        slotId = slot.id,
                                                        courseId = slot.courseId,
                                                        status = "P"
                                                    )
                                                }
                                            },
                                            onClick = {
                                                if (slot.courseId.isNotBlank()) {
                                                    navController.navigate("student_subject_detail/${slot.courseId}")
                                                } else {
                                                    navController.navigate("student_report")
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. FLOATING GLASS NAVIGATION BAR FOR STUDENT
            FloatingGlassNavBar(
                selectedTabIndex = currentTab,
                onTabSelected = { currentTab = it },
                onNavigateSchedule = { currentTab = 0 },
                onNavigateAIImport = { navController.navigate("import_timetable") },
                onNavigateAddClass = { navController.navigate("add_edit_batch") },
                onNavigateManageClasses = { navController.navigate("manage_classes") },
                onNavigateProfile = { navController.navigate("profile") },
                isDarkTheme = isDarkTheme,
                role = "student",
                onNavigateReport = { navController.navigate("student_report") },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            )
        }
    }
}

/**
 * Modern tactile audio feedback when self attendance is recorded
 */
private fun playAudioFeedback() {
    try {
        val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
    } catch (e: Exception) {
        // Audio tone generation failed or muted; ignore safely
    }
}

@Composable
fun ElevatedStudentProfileHeader(
    viewModel: MainViewModel,
    navController: NavController,
    dateStr: String,
    stats: Triple<Int, Int, Float>,
    accentColor: Color,
    isDarkTheme: Boolean
) {
    val userProfile by viewModel.userProfile.collectAsState()
    val isConnected by viewModel.isNetworkConnected.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val name = userProfile?.name?.takeIf { it.isNotBlank() } ?: "Sakshi Sharma"
    val initials = name.split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString("")
    val branchInfo = userProfile?.branchSectionYear?.takeIf { it.isNotBlank() } ?: "B.Tech CSE • 4th Sem • Sec A"

    val pct = stats.third
    val isEligible = pct >= 75f || stats.second == 0
    val statusColor = when {
        stats.second == 0 -> Color(0xFF6B7280)
        pct >= 75f -> Color(0xFF10B981)
        pct >= 50f -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }

    var isLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isLoaded = true }

    val animatedPct by animateFloatAsState(
        targetValue = if (isLoaded) pct else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "headerPctAnim"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "profileScale"
    )
    val haptic = LocalHapticFeedback.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(26.dp),
                spotColor = accentColor.copy(alpha = 0.20f),
                ambientColor = Color.Black.copy(alpha = 0.10f)
            ),
        shape = RoundedCornerShape(26.dp),
        color = if (isDarkTheme) Color(0xFF1E293B) else Color.White,
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
            // 1. TOP HEADER ROW: AVATAR + NAME & DETAILS + PROFILE CHEVRON
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            navController.navigate("profile")
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar with smooth gradient and active status badge
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .shadow(4.dp, CircleShape)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF10B981), Color(0xFF059669))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials.ifEmpty { "SS" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }

                    // Online / Cloud sync indicator dot
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(if (isDarkTheme) Color(0xFF1E293B) else Color.White)
                            .padding(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(
                                    if (isSyncing) Color(0xFFF59E0B)
                                    else if (isConnected) Color(0xFF10B981)
                                    else Color(0xFF94A3B8)
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = name,
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
                        text = "$branchInfo • $dateStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Quick Theme Toggle button (Dark / Light) with SharedPreferences persistence
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (isDarkTheme) Color(0xFF334155).copy(alpha = 0.6f) else Color(0xFFF1F5F9))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.toggleDarkTheme()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                        contentDescription = "Toggle Theme",
                        tint = if (isDarkTheme) Color(0xFFF59E0B) else Color(0xFF6366F1),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Profile chevron button
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (isDarkTheme) Color(0xFF334155).copy(alpha = 0.6f) else Color(0xFFF1F5F9)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Profile",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            val pendingSyncCount by viewModel.pendingSyncCount.collectAsState(initial = 0)
            val isEngineSyncing by viewModel.isEngineSyncing.collectAsState(initial = false)
            val isOnline by viewModel.isNetworkConnected.collectAsState()

            Spacer(modifier = Modifier.height(10.dp))

            // Sync Status Pill (Offline-First SSOT Indicator)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (pendingSyncCount > 0 || !isOnline) Color(0xFFFEF3C7).copy(alpha = 0.8f)
                        else Color(0xFFF0FDF4).copy(alpha = 0.8f)
                    )
                    .clickable {
                        viewModel.triggerSync()
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isEngineSyncing) Icons.Default.Sync else if (pendingSyncCount > 0 || !isOnline) Icons.Default.CloudOff else Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = if (pendingSyncCount > 0 || !isOnline) Color(0xFFD97706) else Color(0xFF059669),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isEngineSyncing) "Syncing with cloud..." else if (pendingSyncCount > 0) "$pendingSyncCount offline queued • Tap to sync" else if (!isOnline) "Offline • Local SSOT active" else "Synced with cloud",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (pendingSyncCount > 0 || !isOnline) Color(0xFF92400E) else Color(0xFF065F46)
                    )
                }

                Text(
                    text = "SSOT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (pendingSyncCount > 0 || !isOnline) Color(0xFFB45309) else Color(0xFF047857)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(
                color = if (isDarkTheme) Color(0xFF334155).copy(alpha = 0.6f) else Color(0xFFF1F5F9),
                thickness = 1.dp
            )
            Spacer(modifier = Modifier.height(14.dp))

            // 2. INTEGRATED STATS ROW: ATTENDANCE SUMMARY + PROGRESS RING
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        navController.navigate("student_report")
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (stats.second > 0) "${String.format(Locale.ENGLISH, "%.1f", animatedPct)}%" else "—",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = statusColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = statusColor.copy(alpha = 0.14f),
                            border = BorderStroke(0.8.dp, statusColor.copy(alpha = 0.32f))
                        ) {
                            Text(
                                text = when {
                                    stats.second == 0 -> "No sessions yet"
                                    pct >= 75f -> "Eligible (≥75%)"
                                    pct >= 50f -> "Borderline"
                                    else -> "Shortage Alert"
                                },
                                color = statusColor,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (stats.second > 0) "${stats.first} of ${stats.second} lectures attended" else "Attendance will track as classes occur",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Smooth Circular Progress Ring with tap register indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(52.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { if (stats.second > 0) (animatedPct / 100f).coerceIn(0f, 1f) else 0f },
                            modifier = Modifier.size(52.dp),
                            strokeWidth = 5.dp,
                            color = statusColor,
                            trackColor = if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0)
                        )
                        Icon(
                            imageVector = if (isEligible) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "View Register",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Student Lecture Card:
 * Reuses the card design from FacultyDashboardScreen, but removes the faculty "Students" count
 * and provides a one-tap "Mark Self Attendance" action that prevents double-marking.
 */
@Composable
fun StudentGlassLectureCard(
    slot: ScheduleSlotEntity,
    course: CourseEntity? = null,
    isLive: Boolean = false,
    isMarkedPresent: Boolean = false,
    timeHint: String? = null,
    subjectStats: Pair<Int, Int>? = null,
    onMarkSelfAttendance: () -> Unit,
    onClick: () -> Unit
) {
    val subjectColors = listOf(
        Color(0xFF6366F1), // Indigo
        Color(0xFF10B981), // Emerald
        Color(0xFF06B6D4), // Cyan
        Color(0xFFF59E0B), // Amber
        Color(0xFFEC4899), // Pink
        Color(0xFF8B5CF6)  // Violet
    )

    val liveGreen = Color(0xFF10B981)
    val targetBarColor = if (isLive) liveGreen else subjectColors[abs(slot.courseId.hashCode()) % subjectColors.size]
    val barColor by animateColorAsState(targetBarColor, tween(500), label = "barColor")

    val targetBgColor = if (isLive) liveGreen.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
    val bgColor by animateColorAsState(targetBgColor, tween(500), label = "bgColor")

    val targetBorderColor = if (isLive) liveGreen.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.18f)
    val borderColor by animateColorAsState(targetBorderColor, tween(500), label = "borderColor")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = if (isLive) liveGreen.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.16f),
                ambientColor = Color.Black.copy(alpha = 0.08f)
            )
            .bounceClick(scaleDown = 0.98f, onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .fillMaxHeight()
                    .background(barColor)
            )

            Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
                val courseName = course?.name?.takeIf { it.isNotBlank() }
                val courseCode = course?.code?.takeIf { it.isNotBlank() }

                val subjectText: String
                val batchText: String

                if (!courseName.isNullOrBlank()) {
                    subjectText = courseName
                    batchText = if (!courseCode.isNullOrBlank() && courseCode != courseName) {
                        if (slot.section.isNotBlank()) "$courseCode • Sec ${slot.section}" else courseCode
                    } else if (slot.section.isNotBlank()) {
                        "Sec ${slot.section}"
                    } else {
                        ""
                    }
                } else if (!courseCode.isNullOrBlank()) {
                    subjectText = courseCode
                    batchText = if (slot.section.isNotBlank()) "Sec ${slot.section}" else ""
                } else {
                    subjectText = "Course Lecture"
                    batchText = if (slot.section.isNotBlank()) "Sec ${slot.section}" else ""
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = subjectText,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 24.sp
                        )
                        if (batchText.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = batchText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isLive) liveGreen else MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.bounceClick(scaleDown = 0.95f) {}
                        ) {
                            Text(
                                text = "${slot.startTime} - ${slot.endTime}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isLive) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }

                        if (timeHint != null) {
                            Spacer(modifier = Modifier.height(5.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isLive) liveGreen.copy(alpha = 0.14f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                border = BorderStroke(1.dp, if (isLive) liveGreen.copy(alpha = 0.35f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    if (isLive) {
                                        val infiniteTransition = rememberInfiniteTransition(label = "cardPulse")
                                        val alpha by infiniteTransition.animateFloat(
                                            initialValue = 0.3f,
                                            targetValue = 1f,
                                            animationSpec = infiniteRepeatable(
                                                animation = tween(800),
                                                repeatMode = RepeatMode.Reverse
                                            ),
                                            label = "pulseAlpha"
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(liveGreen.copy(alpha = alpha))
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = timeHint,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = if (isLive) liveGreen else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        } else if (isLive) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                                val alpha by infiniteTransition.animateFloat(
                                    initialValue = 0.2f,
                                    targetValue = 1f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(800),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "pulseAlpha"
                                )
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(liveGreen.copy(alpha = alpha))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("LIVE", color = liveGreen, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Location & Section (Hiding faculty students count)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, "Location", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = slot.room.ifBlank { "Main Hall" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (slot.section.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = "Sec ${slot.section}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // Per-Subject Attendance Pill Badge
                if (subjectStats != null && subjectStats.second > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    val subPct = (subjectStats.first * 100f) / subjectStats.second
                    val subColor = when {
                        subPct >= 75f -> Color(0xFF10B981)
                        subPct >= 50f -> Color(0xFFF59E0B)
                        else -> Color(0xFFEF4444)
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = subColor.copy(alpha = 0.10f),
                        border = BorderStroke(0.8.dp, subColor.copy(alpha = 0.28f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(subColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Your Attendance: ${String.format(Locale.ENGLISH, "%.1f", subPct)}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = subColor
                                )
                            }
                            Text(
                                text = "${subjectStats.first}/${subjectStats.second} attended",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 6. STUDENT SELF ATTENDANCE BUTTON
                StudentSelfAttendanceButton(
                    isMarked = isMarkedPresent,
                    onMark = onMarkSelfAttendance
                )
            }
        }
    }
}

/**
 * Animated "Mark Self Attendance" button with rich spring scale compression (0.96x),
 * circular checkmark morph, radial glow ripple effect, Apple Pay chime audio, and haptic feedback.
 */
@Composable
fun StudentSelfAttendanceButton(
    isMarked: Boolean,
    onMark: () -> Unit
) {
    val context = LocalContext.current
    var isPressedAnim by remember { mutableStateOf(false) }

    // Subtle scale compression (0.96x) with fluid spring animation
    val scale by animateFloatAsState(
        targetValue = if (isPressedAnim) 0.96f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "buttonSpringScale"
    )

    // Gentle radial glow effect expanding from the attendance badge
    val glowAlpha by animateFloatAsState(
        targetValue = if (isMarked) 0.40f else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "glowAlpha"
    )

    val buttonGradient = if (isMarked) {
        Brush.horizontalGradient(listOf(Color(0xFF10B981), Color(0xFF059669)))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFF6366F1), Color(0xFF4F46E5)))
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // Radial glow background expansion
        if (glowAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF10B981).copy(alpha = glowAlpha),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
            )
        }

        Box(
            modifier = Modifier
                .graphicsLayer(scaleX = scale, scaleY = scale)
                .fillMaxWidth()
                .height(50.dp)
                .shadow(
                    elevation = if (isMarked) 3.dp else 6.dp,
                    shape = RoundedCornerShape(16.dp),
                    spotColor = if (isMarked) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFF6366F1).copy(alpha = 0.45f)
                )
                .clip(RoundedCornerShape(16.dp))
                .background(buttonGradient)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    isPressedAnim = true
                    if (!isMarked) {
                        SoundFeedbackHelper.playApplePaySuccessDing(context)
                        SoundFeedbackHelper.performSuccessHaptic(context)
                    } else {
                        // Tactile haptic feedback when toggling / unmarking
                        SoundFeedbackHelper.performSuccessHaptic(context)
                    }
                    onMark()
                },
            contentAlignment = Alignment.Center
        ) {
            LaunchedEffect(isPressedAnim) {
                if (isPressedAnim) {
                    delay(180)
                    isPressedAnim = false
                }
            }

            AnimatedContent(
                targetState = isMarked,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(280)) + scaleIn(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        initialScale = 0.82f
                    )) togetherWith (fadeOut(animationSpec = tween(140)) + scaleOut(targetScale = 0.9f))
                },
                label = "markedStateMorph"
            ) { marked ->
                if (marked) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Circular checkmark morph with clean spring animation
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Attended",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Marked Present • Tap to unmark",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Mark Present",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStudentScheduleIllustration(
    day: String,
    onImportAI: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.EventAvailable,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "No Classes on $day!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "You don't have any scheduled sessions for this day. Import your routine with AI or check upcoming days.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        FilledTonalButton(
            onClick = onImportAI,
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Import Timetable with AI", fontWeight = FontWeight.SemiBold)
        }
    }
}
