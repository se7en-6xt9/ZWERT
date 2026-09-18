package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.example.ui.components.FloatingGlassNavBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import com.example.ui.components.ScheduleBreakCard
import com.example.ui.components.ScheduleTimelineItem
import com.example.ui.components.buildChronologicalTimeline
import com.example.ui.util.SubjectFormatting
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FacultyDashboardScreen(navController: NavController, viewModel: MainViewModel) {
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val accentColor = Color(0xFF6366F1) // Indigo accent matching AppTheme

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
        DashboardContent(
            navController = navController,
            viewModel = viewModel,
            isDarkTheme = isDarkTheme,
            onThemeToggle = { viewModel.toggleDarkTheme() },
            accentColor = accentColor
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(
    navController: NavController,
    viewModel: MainViewModel,
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    accentColor: Color
) {
    val today = remember { LocalDate.now() }
    val currentDayIndex = maxOf(0, today.dayOfWeek.value - 1)
    
    var currentLiveTime by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15000L) // 15 seconds
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

                // 1. ELEVATED PROFESSOR NAME PROFILE HEADER
                ElevatedFacultyProfileHeader(
                    viewModel = viewModel,
                    navController = navController,
                    dateStr = today.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                    accentColor = accentColor
                )

                Spacer(modifier = Modifier.height(8.dp))

                val isSyncing by viewModel.isSyncing.collectAsState()
                val syncProgress by viewModel.syncProgress.collectAsState()
                val showCelebration by viewModel.showCelebration.collectAsState()
                val syncStatusText by viewModel.syncStatusText.collectAsState()

                CloudSyncFeedbackBanner(
                    isSyncing = isSyncing,
                    syncProgress = syncProgress,
                    showCelebration = showCelebration,
                    syncStatusText = syncStatusText,
                    accentColor = accentColor
                )

                val allCourses by viewModel.getAllCourses().collectAsState(initial = emptyList())
                val courseMap = remember(allCourses) { allCourses.associateBy { it.id } }
                val allAttendance by viewModel.getAllAttendance().collectAsState(initial = emptyList())

                // 2. DAY-SELECTOR ROW WITH SLIDING INDICATOR & EDGE FADES
                DaySelectorCard(
                    weekDates = weekDates,
                    pagerState = pagerState,
                    today = today,
                    isDarkTheme = isDarkTheme,
                    coroutineScope = coroutineScope,
                    haptic = haptic
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Pager for swipeable days
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
                        delay(250) // Simulated load
                        isLoading = false
                    }

                    val timelineItems = remember(scheduleSlots) {
                        buildChronologicalTimeline(scheduleSlots)
                    }

                    LaunchedEffect(timelineItems, isLoading) {
                        if (!isLoading && timelineItems.isNotEmpty() && isTodayPage) {
                            val liveIndex = timelineItems.indexOfFirst { item ->
                                item is ScheduleTimelineItem.SlotItem && isSlotLive(item.slot, LocalTime.now())
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
                        } else if (timelineItems.isEmpty()) {
                            EmptyStateIllustration(dayName)
                        } else {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                itemsIndexed(
                                    items = timelineItems,
                                    key = { _, item ->
                                        when (item) {
                                            is ScheduleTimelineItem.SlotItem -> item.slot.id
                                            is ScheduleTimelineItem.BreakItem -> item.id
                                        }
                                    }
                                ) { index, item ->
                                    when (item) {
                                        is ScheduleTimelineItem.BreakItem -> {
                                            ScheduleBreakCard(breakItem = item)
                                        }
                                        is ScheduleTimelineItem.SlotItem -> {
                                            val slot = item.slot
                                            val isLive = isTodayPage && isSlotLive(slot, currentLiveTime)
                                            val timeHint = if (isTodayPage) getRelativeTimeHint(slot, currentLiveTime) else null
                                            val dateStr = pageDate.format(DateTimeFormatter.ISO_LOCAL_DATE)

                                            val sessionRecords = allAttendance.filter {
                                                it.date == dateStr && (
                                                    it.scheduleSlotId == slot.id ||
                                                    (it.courseId == slot.courseId && it.courseId.isNotBlank())
                                                )
                                            }
                                            val isCancelled = sessionRecords.any {
                                                it.status.equals("CANCELLED", ignoreCase = true) || it.status.equals("C", ignoreCase = true)
                                            }

                                            val cancelNote = viewModel.getCancellationNote(dateStr, slot.id, slot.courseId)

                                            GlassLectureCard(
                                                slot = slot,
                                                course = courseMap[slot.courseId],
                                                isLive = isLive,
                                                timeHint = timeHint,
                                                isCancelled = isCancelled,
                                                cancelNote = cancelNote,
                                                onUncancelClass = {
                                                    viewModel.uncancelClassSession(
                                                        date = dateStr,
                                                        slotId = slot.id,
                                                        courseId = slot.courseId
                                                    )
                                                },
                                                onClick = { navController.navigate("lecture_view/${slot.id}") }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. FLOATING iOS-STYLE GLASS BAR
            FloatingGlassNavBar(
                selectedTabIndex = currentTab,
                onTabSelected = { currentTab = it },
                onNavigateSchedule = { currentTab = 0 },
                onNavigateAIImport = { navController.navigate("import_timetable") },
                onNavigateAddClass = { navController.navigate("add_edit_batch") },
                onNavigateManageClasses = { navController.navigate("manage_classes") },
                onNavigateProfile = { navController.navigate("profile") },
                isDarkTheme = isDarkTheme,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            )
        }
    }
}

@Composable
fun ElevatedFacultyProfileHeader(
    viewModel: MainViewModel,
    navController: NavController,
    dateStr: String,
    accentColor: Color
) {
    val userProfile by viewModel.userProfile.collectAsState()
    val isConnected by viewModel.isNetworkConnected.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val name = userProfile?.name?.takeIf { it.isNotBlank() } ?: "Prof. Yash Thakur"
    val initials = name.split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString("")
    val haptic = LocalHapticFeedback.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isDarkTheme) Color.White.copy(alpha = 0.08f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
        ),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Row 1: Avatar + Name + Action Buttons (Attendance Register, Theme Toggle, Profile)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tappable Avatar with live status indicator
                Box(
                    modifier = Modifier.clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        navController.navigate("profile")
                    },
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .shadow(3.dp, CircleShape)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF4F46E5),
                                        Color(0xFF7C3AED),
                                        Color(0xFF9333EA)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials.ifEmpty { "YT" },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            letterSpacing = 0.5.sp
                        )
                    }

                    val statusDotColor = when {
                        !isConnected -> Color(0xFFF59E0B)
                        isSyncing -> accentColor
                        else -> Color(0xFF10B981)
                    }
                    Box(
                        modifier = Modifier
                            .size(11.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(statusDotColor)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Tappable Name & Subtitle
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            navController.navigate("profile")
                        }
                ) {
                    Text(
                        text = "Teacher Dashboard",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "$name • CSE",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Action buttons: Register & Theme Toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Dedicated Attendance Register Button
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                if (isDarkTheme) Color(0xFF334155).copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            )
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                navController.navigate("attendance_report")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Assessment,
                            contentDescription = "Attendance Register",
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Theme Toggle Button
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (isDarkTheme) Color(0xFF334155).copy(alpha = 0.7f) else Color(0xFFF1F5F9))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.toggleDarkTheme()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = "Toggle Theme",
                            tint = if (isDarkTheme) Color(0xFFF59E0B) else accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 2: Combined Compact Sync / Register Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isDarkTheme) Color(0xFF0F172A).copy(alpha = 0.7f)
                        else Color(0xFFF8FAFC)
                    )
                    .border(
                        0.8.dp,
                        if (isDarkTheme) Color(0xFF334155).copy(alpha = 0.5f)
                        else Color(0xFFE2E8F0),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val statusDotColor = when {
                    !isConnected -> Color(0xFFF59E0B)
                    isSyncing -> accentColor
                    else -> Color(0xFF10B981)
                }
                val statusText = when {
                    !isConnected -> "Offline • Persistent Cache"
                    isSyncing -> "Syncing with cloud..."
                    else -> "Cloud synced"
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { viewModel.triggerSync() }
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(statusDotColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        fontWeight = FontWeight.Medium,
                        color = statusDotColor
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = accentColor.copy(alpha = 0.12f),
                    border = BorderStroke(0.6.dp, accentColor.copy(alpha = 0.30f)),
                    modifier = Modifier.clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        navController.navigate("attendance_report")
                    }
                ) {
                    Text(
                        text = "Register Ready",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DaySelectorCard(
    weekDates: List<LocalDate>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    today: LocalDate,
    isDarkTheme: Boolean,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    val infiniteTransition = rememberInfiniteTransition(label = "todayPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val glassBg = if (isDarkTheme) {
        Color(0xFF16161E).copy(alpha = 0.74f)
    } else {
        Color(0xFFFFFFFF).copy(alpha = 0.78f)
    }

    val glassBorderColor = if (isDarkTheme) {
        Color.White.copy(alpha = 0.14f)
    } else {
        Color.White.copy(alpha = 0.75f)
    }

    val accentGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF6366F1), // Indigo
            Color(0xFF8B5CF6), // Violet
            Color(0xFF7C3AED)  // Purple
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(32.dp),
                    spotColor = if (isDarkTheme) Color(0xFF6366F1).copy(alpha = 0.30f) else Color.Black.copy(alpha = 0.18f),
                    ambientColor = Color.Black.copy(alpha = 0.14f)
                ),
            shape = RoundedCornerShape(32.dp),
            color = glassBg,
            border = BorderStroke(1.2.dp, glassBorderColor)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = Color.Transparent,
                    edgePadding = 8.dp,
                    divider = {},
                    indicator = { tabPositions ->
                        if (pagerState.currentPage < tabPositions.size) {
                            val currentTab = tabPositions[pagerState.currentPage]
                            val fraction = pagerState.currentPageOffsetFraction
                            val nextTabIndex = if (fraction > 0) {
                                minOf(pagerState.currentPage + 1, tabPositions.lastIndex)
                            } else if (fraction < 0) {
                                maxOf(pagerState.currentPage - 1, 0)
                            } else {
                                pagerState.currentPage
                            }
                            val nextTab = tabPositions[nextTabIndex]
                            val absFrac = abs(fraction)

                            val targetLeft = currentTab.left + (nextTab.left - currentTab.left) * absFrac
                            val targetWidth = currentTab.width + (nextTab.width - currentTab.width) * absFrac

                            val animatedLeft by animateDpAsState(
                                targetValue = targetLeft,
                                animationSpec = spring(
                                    dampingRatio = 0.65f,
                                    stiffness = Spring.StiffnessLow
                                ),
                                label = "dayIndicatorLeft"
                            )
                            val animatedWidth by animateDpAsState(
                                targetValue = targetWidth,
                                animationSpec = spring(
                                    dampingRatio = 0.65f,
                                    stiffness = Spring.StiffnessLow
                                ),
                                label = "dayIndicatorWidth"
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .wrapContentSize(Alignment.CenterStart)
                                    .offset(x = animatedLeft)
                                    .width(animatedWidth)
                                    .fillMaxHeight()
                                    .padding(vertical = 5.dp, horizontal = 2.dp)
                                    .shadow(
                                        elevation = 8.dp,
                                        shape = RoundedCornerShape(22.dp),
                                        spotColor = Color(0xFF6366F1).copy(alpha = 0.60f),
                                        ambientColor = Color(0xFF8B5CF6).copy(alpha = 0.35f)
                                    )
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(accentGradient)
                                    .zIndex(0f)
                            )
                        }
                    }
                ) {
                    weekDates.forEachIndexed { index, date ->
                        val isSelected = pagerState.currentPage == index
                        Tab(
                            selected = isSelected,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                coroutineScope.launch { pagerState.animateScrollToPage(index) }
                            },
                            modifier = Modifier
                                .height(52.dp)
                                .zIndex(1f),
                            selectedContentColor = Color.White,
                            unselectedContentColor = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).take(3).uppercase(),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp,
                                    letterSpacing = 0.3.sp,
                                    color = if (isSelected) Color.White else if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = date.dayOfMonth.toString(),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.5.sp,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                if (date == today) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .graphicsLayer {
                                                scaleX = pulseScale
                                                scaleY = pulseScale
                                                alpha = pulseAlpha
                                            }
                                            .clip(CircleShape)
                                            .background(if (isSelected) Color.White else Color(0xFF6366F1))
                                    )
                                }
                            }
                        }
                    }
                }

                // Soft fade-out gradient at left capsule edge
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(20.dp)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    if (isDarkTheme) Color(0xFF16161E).copy(alpha = 0.85f) else Color(0xFFFFFFFF).copy(alpha = 0.85f),
                                    Color.Transparent
                                )
                            )
                        )
                        .zIndex(2f)
                )

                // Soft fade-out gradient at right capsule edge
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(20.dp)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    if (isDarkTheme) Color(0xFF16161E).copy(alpha = 0.85f) else Color(0xFFFFFFFF).copy(alpha = 0.85f)
                                )
                            )
                        )
                        .zIndex(2f)
                )
            }
        }
    }
}

@Composable
fun GlassLectureCard(
    slot: ScheduleSlotEntity,
    course: CourseEntity? = null,
    isLive: Boolean = false,
    timeHint: String? = null,
    isCancelled: Boolean = false,
    cancelNote: String = "",
    onUncancelClass: () -> Unit = {},
    onClick: () -> Unit
) {
    val isDarkTheme = isSystemInDarkTheme()
    val subjectColors = listOf(Color(0xFFE57373), Color(0xFF81C784), Color(0xFF64B5F6), Color(0xFFFFD54F), Color(0xFFBA68C8))
    
    val liveGreen = Color(0xFF4CAF50)
    val cancelledRed = Color(0xFFEF4444)
    val targetBarColor = if (isCancelled) cancelledRed else if (isLive) liveGreen else subjectColors[abs(slot.courseId.hashCode()) % subjectColors.size]
    val barColor by animateColorAsState(targetBarColor, tween(500), label = "barColor")
    
    val targetBgColor = if (isCancelled) {
        if (isDarkTheme) Color(0xFF260D10) else Color(0xFFFEF2F2)
    } else if (isLive) {
        liveGreen.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val bgColor by animateColorAsState(targetBgColor, tween(500), label = "bgColor")
    
    val targetBorderColor = if (isCancelled) {
        Color(0xFFEF4444).copy(alpha = if (isDarkTheme) 0.70f else 0.55f)
    } else if (isLive) {
        liveGreen.copy(alpha = 0.35f)
    } else {
        Color.White.copy(alpha = 0.18f)
    }
    val borderColor by animateColorAsState(targetBorderColor, tween(500), label = "borderColor")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = if (isCancelled) Color(0xFFEF4444).copy(alpha = 0.35f) else if (isLive) liveGreen.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.16f),
                ambientColor = Color.Black.copy(alpha = 0.08f)
            )
            .bounceClick(scaleDown = 0.97f, onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(if (isCancelled) 1.2.dp else 1.dp, borderColor)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .fillMaxHeight()
                    .background(barColor)
            )
            
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp).fillMaxWidth()) {
                val shortLabel = SubjectFormatting.getShortLabel(course, slot.courseId)
                val fullName = SubjectFormatting.getFullName(course, slot.courseId)

                val subjectText = shortLabel
                val fullSubjectName = if (fullName.isNotBlank() && !fullName.equals(shortLabel, ignoreCase = true)) {
                    fullName
                } else {
                    ""
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                        Text(
                            text = subjectText,
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isCancelled) {
                                if (isDarkTheme) Color(0xFFFCA5A5) else Color(0xFF7F1D1D)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (fullSubjectName.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = fullSubjectName,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                color = if (isCancelled) {
                                    if (isDarkTheme) Color(0xFFFECDD3).copy(alpha = 0.85f) else Color(0xFF991B1B)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isCancelled) {
                                if (isDarkTheme) Color(0xFF3F1115) else Color(0xFFFEE2E2)
                            } else if (isLive) {
                                liveGreen
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                            border = if (isCancelled) BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)) else null,
                            modifier = Modifier.bounceClick(scaleDown = 0.95f) {}
                        ) {
                            Text(
                                text = "${slot.startTime} - ${slot.endTime}",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                                fontWeight = FontWeight.Bold,
                                color = if (isCancelled) {
                                    if (isDarkTheme) Color(0xFFFCA5A5) else Color(0xFFDC2626)
                                } else if (isLive) {
                                    Color.White
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                },
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }

                        if (timeHint != null && !isCancelled) {
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

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LocationOn, "Location", 
                            modifier = Modifier.size(16.dp), 
                            tint = if (isCancelled) Color(0xFFEF4444).copy(alpha = 0.85f) else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = slot.room,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            color = if (isCancelled) {
                                if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF475569)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.PeopleAlt, "Students", 
                            modifier = Modifier.size(16.dp), 
                            tint = if (isCancelled) Color(0xFFEF4444).copy(alpha = 0.85f) else MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Sec ${slot.section}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            color = if (isCancelled) {
                                if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF475569)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isCancelled) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isDarkTheme) Color(0xFF351014) else Color(0xFFEF4444).copy(alpha = 0.10f),
                        border = BorderStroke(1.2.dp, Color(0xFFEF4444).copy(alpha = if (isDarkTheme) 0.65f else 0.40f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.EventBusy,
                                        contentDescription = null,
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "CLASS CANCELLED",
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isDarkTheme) Color(0xFFFCA5A5) else Color(0xFFDC2626),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(
                                        onClick = onUncancelClass,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            "Undo",
                                            color = if (isDarkTheme) Color(0xFFFCA5A5) else Color(0xFFDC2626),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Button(
                                        onClick = onClick,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            "View Session",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            if (cancelNote.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isDarkTheme) Color(0xFF1E080A) else Color.White.copy(alpha = 0.85f),
                                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Info,
                                            contentDescription = null,
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Note to Students: $cancelNote",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isDarkTheme) Color(0xFFFECDD3) else Color(0xFF7F1D1D),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Button(
                        onClick = onClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.FactCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Mark Attendance", maxLines = 1, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedAttendanceButton(onClick: () -> Unit) {
    var isMarked by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    val containerColor by animateColorAsState(if (isMarked) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary, label = "")
    val widthFraction by animateFloatAsState(if (isMarked) 0.5f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "")

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(widthFraction)
                .height(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(containerColor)
                .bounceClick {
                    if (!isMarked) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        isMarked = true
                        onClick()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(targetState = isMarked, label = "") { marked ->
                if (marked) {
                    Icon(Icons.Default.Check, contentDescription = "Marked", tint = Color.White)
                } else {
                    Text("Mark Attendance", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AnimatedGradientMesh(accentColor: Color, isDark: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "mesh")
    val pos1 by infiniteTransition.animateFloat(0f, 1f, infiniteRepeatable(tween(15000), RepeatMode.Reverse), label = "p1")
    val pos2 by infiniteTransition.animateFloat(1f, 0f, infiniteRepeatable(tween(18000), RepeatMode.Reverse), label = "p2")

    val baseColor = if (isDark) Color(0xFF0F0F13) else Color(0xFFF4F6F9)
    val blob1 = accentColor.copy(alpha = if(isDark) 0.15f else 0.25f)
    val blob2 = if (isDark) Color(0xFF00bcd4).copy(alpha = 0.1f) else Color(0xFF03A9F4).copy(alpha = 0.15f)

    Canvas(modifier = Modifier.fillMaxSize().background(baseColor)) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(blob1, Color.Transparent),
                center = Offset(size.width * pos1, size.height * 0.3f),
                radius = size.width * 0.8f
            ),
            radius = size.width * 0.8f,
            center = Offset(size.width * pos1, size.height * 0.3f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(blob2, Color.Transparent),
                center = Offset(size.width * pos2, size.height * 0.7f),
                radius = size.width * 0.9f
            ),
            radius = size.width * 0.9f,
            center = Offset(size.width * pos2, size.height * 0.7f)
        )
    }
}

@Composable
fun StaggeredAnimatedItem(index: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 60L)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(spring(stiffness = Spring.StiffnessLow)) +
                slideInVertically(
                    initialOffsetY = { 50 },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                )
    ) {
        content()
    }
}

@Composable
fun SkeletonCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.5f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = ""
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Gray.copy(alpha = alpha))
    )
}

@Composable
fun EmptyStateIllustration(day: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.EventAvailable, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Free Day!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "No lectures scheduled for $day. Enjoy your time off or catch up on grading.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ExpandableFAB(navController: NavController) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 45f else 0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "")

    Column(horizontalAlignment = Alignment.End) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + slideInVertically { 50 },
            exit = fadeOut() + slideOutVertically { 50 }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                SmallFloatingActionButton(
                    onClick = { navController.navigate("import_timetable") },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) {
                    Icon(Icons.Default.AutoAwesome, "AI Import")
                }
                SmallFloatingActionButton(
                    onClick = { navController.navigate("add_edit_batch") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.Create, "Manual Add")
                }
                SmallFloatingActionButton(
                    onClick = { navController.navigate("manage_classes") },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Icon(Icons.Default.Settings, "Manage Classes")
                }
            }
        }
        FloatingActionButton(
            onClick = { expanded = !expanded },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Add, "Add", modifier = Modifier.rotate(rotation))
        }
    }
}

@Composable
fun ModernBottomNavigation() {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        tonalElevation = 0.dp,
        windowInsets = WindowInsets(0)
    ) {
        NavigationBarItem(icon = { Icon(Icons.Default.Home, "Home") }, selected = false, onClick = {})
        NavigationBarItem(icon = { Icon(Icons.Default.Event, "Schedule") }, selected = true, onClick = {})
        NavigationBarItem(icon = { Icon(Icons.Default.History, "History") }, selected = false, onClick = {})
        NavigationBarItem(icon = { Icon(Icons.Default.Person, "Profile") }, selected = false, onClick = {})
    }
}

fun Modifier.bounceClick(
    scaleDown: Float = 0.95f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "bounce"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null, 
            onClick = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                onClick()
            }
        )
}

fun parseTimeSafely(timeStr: String): LocalTime? {
    val cleanStr = timeStr.trim().uppercase(Locale.ENGLISH)
    return try {
        if (cleanStr.contains("AM") || cleanStr.contains("PM")) {
            val formatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
            LocalTime.parse(cleanStr, formatter)
        } else {
            val parts = cleanStr.split(":")
            val h = parts[0].toInt()
            val m = parts[1].take(2).toInt() 
            LocalTime.of(h, m)
        }
    } catch (e: Exception) {
        try {
            val formatter2 = DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern("[hh:mm a][h:mm a][HH:mm][H:mm]")
                .toFormatter(Locale.ENGLISH)
            LocalTime.parse(cleanStr, formatter2)
        } catch (e2: Exception) {
            null
        }
    }
}

fun isSlotLive(slot: ScheduleSlotEntity, now: LocalTime): Boolean {
    val start = parseTimeSafely(slot.startTime) ?: return false
    val end = parseTimeSafely(slot.endTime) ?: return false
    return !now.isBefore(start) && now.isBefore(end)
}

fun getRelativeTimeHint(slot: ScheduleSlotEntity, now: LocalTime): String? {
    val start = parseTimeSafely(slot.startTime) ?: return null
    val end = parseTimeSafely(slot.endTime) ?: return null

    if (!now.isBefore(start) && now.isBefore(end)) {
        val minutesLeft = java.time.Duration.between(now, end).toMinutes()
        return if (minutesLeft > 0) "In progress · ${minutesLeft}m left" else "In progress"
    }

    if (now.isBefore(start)) {
        val minutesUntil = java.time.Duration.between(now, start).toMinutes()
        return when {
            minutesUntil in 1..60 -> "Starts in ${minutesUntil}m"
            minutesUntil in 61..120 -> "Starts in ~1h"
            else -> null
        }
    }
    return null
}

@Composable
fun CloudSyncFeedbackBanner(
    isSyncing: Boolean,
    syncProgress: Float,
    showCelebration: Boolean,
    syncStatusText: String,
    accentColor: Color
) {
    AnimatedVisibility(
        visible = isSyncing || showCelebration,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        val animatedProgress by animateFloatAsState(
            targetValue = if (showCelebration) 1f else syncProgress.coerceIn(0.1f, 1f),
            label = "syncProgress"
        )

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (showCelebration) Color(0xFF10B981).copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ),
            border = BorderStroke(
                width = 1.dp,
                color = if (showCelebration) Color(0xFF10B981).copy(alpha = 0.4f)
                else accentColor.copy(alpha = 0.25f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (showCelebration) "🎉" else "☁️",
                            fontSize = 16.sp
                        )
                        Text(
                            text = syncStatusText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            ),
                            color = if (showCelebration) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = if (showCelebration) "100%" else "${(animatedProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = if (showCelebration) Color(0xFF10B981) else accentColor
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (showCelebration) Color(0xFF10B981) else accentColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}
