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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import com.example.data.CourseEntity
import com.example.data.ScheduleSlotEntity
import com.example.ui.components.FloatingGlassNavBar
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
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    var currentTab by remember { mutableIntStateOf(0) }

    // Auto-sync from cloud if logged in
    LaunchedEffect(Unit) {
        viewModel.syncDataFromFirebase()
    }

    val allCourses by viewModel.getAllCourses().collectAsState(initial = emptyList())
    val courseMap = remember(allCourses) { allCourses.associateBy { it.id } }
    val allAttendance by viewModel.getAllAttendance().collectAsState(initial = emptyList())

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

                // 1. ELEVATED STUDENT PROFILE HEADER
                ElevatedStudentProfileHeader(
                    viewModel = viewModel,
                    navController = navController,
                    dateStr = today.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                    attendancePercentage = attendanceStats.third,
                    accentColor = accentColor
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 2. QUICK ATTENDANCE SUMMARY PILL CARD
                StudentSummaryPill(
                    stats = attendanceStats,
                    isDarkTheme = isDarkTheme,
                    onClickReport = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        navController.navigate("student_report")
                    }
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
                            EmptyStudentScheduleIllustration(dayName)
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

                                    StaggeredAnimatedItem(index = index) {
                                        StudentGlassLectureCard(
                                            slot = slot,
                                            course = courseMap[slot.courseId],
                                            isLive = isLive,
                                            isMarkedPresent = isMarkedPresent,
                                            timeHint = timeHint,
                                            onMarkSelfAttendance = {
                                                playAudioFeedback()
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.markSelfAttendance(
                                                    date = dateStr,
                                                    slotId = slot.id,
                                                    courseId = slot.courseId,
                                                    status = "P"
                                                )
                                            },
                                            onClick = {
                                                navController.navigate("student_report")
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
                onNavigateAIImport = { navController.navigate("student_report") },
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
    attendancePercentage: Float,
    accentColor: Color
) {
    val userProfile by viewModel.userProfile.collectAsState()
    val isConnected by viewModel.isNetworkConnected.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val name = userProfile?.name?.takeIf { it.isNotBlank() } ?: "Sakshi Sharma"
    val initials = name.split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString("")

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
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    navController.navigate("profile")
                }
            ),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isPressed) accentColor.copy(alpha = 0.40f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
        ),
        shadowElevation = if (isPressed) 1.dp else 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Modern gradient avatar with active status dot
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF10B981), Color(0xFF059669))
                            )
                        )
                        .shadow(4.dp, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials.ifEmpty { "ST" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }

                // Online/Syncing Status Indicator Dot
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
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

            // Student Name, Role Subtitle & Branch info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.28f))
                    ) {
                        Text(
                            text = "Student",
                            color = Color(0xFF059669),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                val branchInfo = userProfile?.branchSectionYear?.takeIf { it.isNotBlank() } ?: "B.Tech CSE • Sec A"
                Text(
                    text = "$branchInfo • $dateStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            // Interactive chevron indicator
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        if (isPressed) accentColor.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "View Profile",
                    tint = if (isPressed) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Compact high-information summary pill showing overall attendance % and criteria status
 */
@Composable
fun StudentSummaryPill(
    stats: Triple<Int, Int, Float>,
    isDarkTheme: Boolean,
    onClickReport: () -> Unit
) {
    val pct = stats.third
    val isEligible = pct >= 75f
    val statusColor = if (isEligible) Color(0xFF10B981) else Color(0xFFEF4444)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .bounceClick(scaleDown = 0.98f, onClick = onClickReport),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDarkTheme) Color(0xFF1E293B).copy(alpha = 0.8f) else Color(0xFFEEF2FF)
        ),
        border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFC7D2FE).copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isEligible) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "Attendance Overview",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${stats.first} of ${stats.second} lectures attended",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${String.format(Locale.ENGLISH, "%.1f", pct)}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = statusColor
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
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

                Spacer(modifier = Modifier.height(16.dp))

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

                Spacer(modifier = Modifier.height(20.dp))

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
 * Animated "Mark Self Attendance" button with green feedback, tactile bounce, and double-marking prevention.
 */
@Composable
fun StudentSelfAttendanceButton(
    isMarked: Boolean,
    onMark: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    val containerColor by animateColorAsState(
        if (isMarked) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
        label = "btnColor"
    )

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(containerColor)
                .bounceClick(scaleDown = if (isMarked) 0.99f else 0.96f) {
                    if (!isMarked) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onMark()
                    } else {
                        // Already marked feedback
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = isMarked,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.92f) togetherWith
                            fadeOut(animationSpec = tween(150))
                },
                label = "markedState"
            ) { marked ->
                if (marked) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Attended",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Attended • Present",
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
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Mark Self Attendance",
                            color = MaterialTheme.colorScheme.onPrimary,
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
fun EmptyStudentScheduleIllustration(day: String) {
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
            text = "You don't have any scheduled sessions for this day. Check upcoming days or view your full attendance register.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
