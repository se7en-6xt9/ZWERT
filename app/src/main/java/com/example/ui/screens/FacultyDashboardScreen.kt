package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import com.example.data.ScheduleSlotEntity
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
    var isDarkTheme by remember { mutableStateOf(false) }
    val accentColor = Color(0xFF6750A4) // Fixed Purple Accent

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
            onThemeToggle = { isDarkTheme = !isDarkTheme },
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
            delay(30000L) // 30 seconds
            currentLiveTime = LocalTime.now()
        }
    }
    
    val weekDates = remember {
        val startOfWeek = today.minusDays(currentDayIndex.toLong())
        (0..6).map { startOfWeek.plusDays(it.toLong()) }
    }

    val pagerState = rememberPagerState(initialPage = currentDayIndex, pageCount = { 7 })
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent, 
        topBar = {
            LargeTopAppBar(
                title = { 
                    DashboardInfoBlock(
                        viewModel = viewModel, 
                        navController = navController,
                        dateStr = today.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onThemeToggle() 
                    }) {
                        Icon(if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode, "Toggle Theme")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                ),
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            ModernBottomNavigation()
        },
        floatingActionButton = {
            ExpandableFAB(navController = navController)
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedGradientMesh(accentColor = accentColor, isDark = isDarkTheme)
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Innovative Day Selector
                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = Color.Transparent,
                    edgePadding = 16.dp,
                    divider = {},
                    indicator = { tabPositions ->
                        if (pagerState.currentPage < tabPositions.size) {
                            val currentPage = pagerState.currentPage
                            val fraction = pagerState.currentPageOffsetFraction
                            val currentTab = tabPositions[currentPage]
                            
                            val nextTabIndex = if (fraction > 0) minOf(currentPage + 1, tabPositions.lastIndex) else maxOf(currentPage - 1, 0)
                            val nextTab = tabPositions[nextTabIndex]
                            
                            val targetLeft = currentTab.left + (nextTab.left - currentTab.left) * abs(fraction)
                            val targetWidth = currentTab.width + (nextTab.width - currentTab.width) * abs(fraction)
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .wrapContentSize(Alignment.CenterStart)
                                    .offset(x = targetLeft)
                                    .width(targetWidth)
                                    .fillMaxHeight()
                                    .padding(vertical = 8.dp, horizontal = 4.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp))
                                    .zIndex(-1f)
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
                            modifier = Modifier.height(84.dp).zIndex(1f),
                            selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(date.dayOfMonth.toString(), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                                
                                Spacer(modifier = Modifier.height(6.dp))
                                if (date == today) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                                    )
                                } else {
                                    Box(modifier = Modifier.size(6.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

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
                        delay(300) // Simulated load
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
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(3) { SkeletonCard() }
                            }
                        } else if (scheduleSlots.isEmpty()) {
                            EmptyStateIllustration(dayName)
                        } else {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                itemsIndexed(scheduleSlots, key = { _, slot -> slot.id }) { index, slot ->
                                    val isLive = isTodayPage && isSlotLive(slot, currentLiveTime)
                                    StaggeredAnimatedItem(index = index) {
                                        GlassLectureCard(
                                            slot = slot,
                                            isLive = isLive,
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
    }
}

@Composable
fun DashboardInfoBlock(viewModel: MainViewModel, navController: NavController, dateStr: String) {
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable { navController.navigate("profile") }
                .padding(vertical = 4.dp, horizontal = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text("YT", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Prof. Yash Thakur", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Text(" • CSE", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Faculty Dashboard • $dateStr",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun GlassLectureCard(slot: ScheduleSlotEntity, isLive: Boolean = false, onClick: () -> Unit) {
    val subjectColors = listOf(Color(0xFFE57373), Color(0xFF81C784), Color(0xFF64B5F6), Color(0xFFFFD54F), Color(0xFFBA68C8))
    
    val liveGreen = Color(0xFF4CAF50)
    val targetBarColor = if (isLive) liveGreen else subjectColors[abs(slot.courseId.hashCode()) % subjectColors.size]
    val barColor by animateColorAsState(targetBarColor, tween(500), label = "barColor")
    
    val targetBgColor = if (isLive) liveGreen.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
    val bgColor by animateColorAsState(targetBgColor, tween(500), label = "bgColor")
    
    val targetBorderColor = if (isLive) liveGreen.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.15f)
    val borderColor by animateColorAsState(targetBorderColor, tween(500), label = "borderColor")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(onClick = onClick),
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
                val parts = slot.courseId.split("-")
                var subjectText = slot.courseId
                var batchText = ""

                if (parts.size >= 4) {
                    val branch = parts[0]
                    val semStr = parts[1]
                    val subjectCode = parts.drop(3).joinToString("-") 
                    
                    var admissionYearText = ""
                    val sem = if (semStr.endsWith("SEM", ignoreCase = true)) {
                        val num = semStr.dropLast(3)
                        val suffix = when (num) {
                            "1" -> "1st"
                            "2" -> "2nd"
                            "3" -> "3rd"
                            "4" -> "4th"
                            "5" -> "5th"
                            "6" -> "6th"
                            "7" -> "7th"
                            "8" -> "8th"
                            else -> num
                        }
                        val currentYear = LocalDate.now().year
                        val currentMonth = LocalDate.now().monthValue
                        val academicYearStart = if (currentMonth >= 7) currentYear else currentYear - 1
                        val semInt = num.toIntOrNull() ?: 1
                        val admissionYear = academicYearStart - ((semInt - 1) / 2)
                        admissionYearText = " - $admissionYear"
                        "$suffix Sem"
                    } else semStr
                    
                    val expandedSubject = when(subjectCode.uppercase()) {
                        "DBMS" -> "Database Management Systems"
                        "OS" -> "Operating Systems"
                        "CN" -> "Computer Networks"
                        "DSA" -> "Data Structures & Algorithms"
                        "AI" -> "Artificial Intelligence"
                        "ML" -> "Machine Learning"
                        "SE" -> "Software Engineering"
                        "CS301" -> "Computer Architecture"
                        "CS302" -> "Computer Networks"
                        else -> subjectCode
                    }
                    subjectText = expandedSubject
                    batchText = "$branch - $sem$admissionYearText"
                } else if (parts.size == 3) {
                    val branch = parts[0]
                    val semStr = parts[1]
                    var admissionYearText = ""
                    val sem = if (semStr.endsWith("SEM", ignoreCase = true)) {
                        val num = semStr.dropLast(3)
                        val suffix = when (num) { 
                            "1" -> "1st"
                            "2" -> "2nd"
                            "3" -> "3rd"
                            "4" -> "4th"
                            else -> num 
                        }
                        val currentYear = LocalDate.now().year
                        val currentMonth = LocalDate.now().monthValue
                        val academicYearStart = if (currentMonth >= 7) currentYear else currentYear - 1
                        val semInt = num.toIntOrNull() ?: 1
                        val admissionYear = academicYearStart - ((semInt - 1) / 2)
                        admissionYearText = " - $admissionYear"
                        "$suffix Sem"
                    } else semStr
                    subjectText = parts[2]
                    batchText = "$branch - $sem$admissionYearText"
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
                            modifier = Modifier.bounceClick {}
                        ) {
                            Text(
                                text = "${slot.startTime} - ${slot.endTime}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isLive) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        
                        if (isLive) {
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, "Location", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = slot.room,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PeopleAlt, "Students", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Sec ${slot.section}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                AnimatedAttendanceButton(onClick = onClick)
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
                .height(48.dp)
                .clip(RoundedCornerShape(16.dp))
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
                    Icon(Icons.Default.Upload, "Import Timetable")
                }
                SmallFloatingActionButton(
                    onClick = { navController.navigate("manage_classes") },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Default.Edit, "Edit")
                }
                SmallFloatingActionButton(
                    onClick = { navController.navigate("manage_classes") },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.error
                ) {
                    Icon(Icons.Default.Delete, "Delete")
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
