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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.ui.components.ScheduleBreakCard
import com.example.ui.components.ScheduleTimelineItem
import com.example.ui.components.buildChronologicalTimeline
import com.example.ui.util.SoundFeedbackHelper
import com.example.ui.util.SubjectFormatting
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
    var timetableMode by rememberSaveable { mutableStateOf("OFFICIAL") } // "OFFICIAL" (default) or "PERSONAL"

    val officialClasses by viewModel.activeOfficialClasses.collectAsState()
    val officialAttendance by viewModel.officialAttendance.collectAsState()
    var studentEmail by remember { mutableStateOf(viewModel.getStudentEmail()) }
    var showEditEmailDialog by remember { mutableStateOf(false) }

    // Auto-sync from cloud, student official feed, and run auto-absence engine
    LaunchedEffect(Unit) {
        viewModel.syncOfficialClassesFromLocalSlots()
        viewModel.syncDataFromFirebase()
        viewModel.syncOfficialStudentFeed()
        viewModel.autoMarkPastClassesAsAbsent()
    }

    LaunchedEffect(officialClasses.size) {
        if (officialClasses.isEmpty()) {
            viewModel.syncOfficialStudentFeed()
        }
    }

    if (showEditEmailDialog) {
        var tempEmail by remember { mutableStateOf(studentEmail) }
        AlertDialog(
            onDismissRequest = { showEditEmailDialog = false },
            title = { Text("Sync Official Student Feed") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Enter your enrolled campus email to automatically sync official timetable classes scheduled by faculty.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = tempEmail,
                        onValueChange = { tempEmail = it },
                        label = { Text("Campus Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SuggestionChip(
                            onClick = { tempEmail = "student.demo@campus.edu" },
                            label = { Text("student.demo@campus.edu", fontSize = 11.sp) }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleaned = tempEmail.trim().lowercase()
                        if (cleaned.isNotBlank()) {
                            studentEmail = cleaned
                            viewModel.setStudentEmail(cleaned)
                        }
                        showEditEmailDialog = false
                    }
                ) {
                    Text("Save & Sync")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditEmailDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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
                (it.courseId == course.id) ||
                allSlots.any { s -> s.courseId == course.id && s.id == it.scheduleSlotId } ||
                it.scheduleSlotId.contains(course.id)
            }
            val p = courseRecords.count { it.status.equals("P", ignoreCase = true) || it.status.equals("present", ignoreCase = true) }
            course.id to Pair(p, courseRecords.size)
        }
    }

    // Overall attendance stats (combining personal and official ERP records)
    val attendanceStats = remember(allAttendance, officialAttendance) {
        val selfRecords = allAttendance.filter { it.studentId == "self" }
        val allCombined = (selfRecords.map { it.status } + officialAttendance.map { it.status })
        val total = allCombined.size
        val present = allCombined.count { it.equals("P", ignoreCase = true) || it.equals("present", ignoreCase = true) }
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

                    LaunchedEffect(page, timetableMode) {
                        isLoading = true
                        delay(180)
                        isLoading = false
                    }

                    if (timetableMode == "OFFICIAL") {
                        val dayOfficialClasses = remember(officialClasses, scheduleSlots, courseMap, dayName) {
                            val fromOfficial = officialClasses.filter {
                                val oDay = it.dayOfWeek.trim().lowercase()
                                val pDay = dayName.trim().lowercase()
                                oDay == pDay ||
                                (oDay.startsWith("mon") && pDay.startsWith("mon")) ||
                                (oDay.startsWith("tue") && pDay.startsWith("tue")) ||
                                (oDay.startsWith("wed") && pDay.startsWith("wed")) ||
                                (oDay.startsWith("thu") && pDay.startsWith("thu")) ||
                                (oDay.startsWith("fri") && pDay.startsWith("fri")) ||
                                (oDay.startsWith("sat") && pDay.startsWith("sat")) ||
                                (oDay.startsWith("sun") && pDay.startsWith("sun"))
                            }
                            if (fromOfficial.isNotEmpty()) {
                                fromOfficial
                            } else {
                                scheduleSlots.map { slot ->
                                    val course = courseMap[slot.courseId]
                                    com.example.data.OfficialClassEntity(
                                        slotId = slot.id,
                                        courseId = slot.courseId,
                                        courseName = course?.name ?: "Subject",
                                        courseCode = course?.code ?: "",
                                        dayOfWeek = slot.dayOfWeek,
                                        startTime = slot.startTime,
                                        endTime = slot.endTime,
                                        room = slot.room,
                                        section = slot.section,
                                        facultyName = "Prof. Rajesh Sharma",
                                        facultyEmail = "prof.rajesh@campus.edu",
                                        isHidden = false
                                    )
                                }
                            }
                        }

                        AnimatedContent(
                            targetState = isLoading,
                            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
                            label = "official_load_anim"
                        ) { loading ->
                            if (loading) {
                                LazyColumn(
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(3) { SkeletonCard() }
                                }
                            } else if (dayOfficialClasses.isEmpty()) {
                                EmptyOfficialScheduleIllustration(
                                    day = dayName,
                                    studentEmail = studentEmail,
                                    hasPersonalClasses = scheduleSlots.isNotEmpty(),
                                    personalClassCount = scheduleSlots.size,
                                    onSwitchToPersonal = { timetableMode = "PERSONAL" },
                                    onSync = { viewModel.syncOfficialStudentFeed() },
                                    onChangeEmail = { showEditEmailDialog = true },
                                    onLoadDemo = {
                                        viewModel.loadDummyData()
                                    }
                                )
                            } else {
                                LazyColumn(
                                    state = listState,
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(
                                        items = dayOfficialClasses,
                                        key = { it.slotId }
                                    ) { oClass ->
                                        val dateStr = pageDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                                        val attRecord = officialAttendance.firstOrNull {
                                            it.date == dateStr && (it.slotId == oClass.slotId || it.courseId == oClass.courseId)
                                        } ?: allAttendance.firstOrNull {
                                            it.date == dateStr && (it.scheduleSlotId == oClass.slotId || it.courseId == oClass.courseId)
                                        }?.let { localAtt ->
                                            com.example.data.OfficialAttendanceEntity(
                                                id = "${localAtt.date}_${localAtt.scheduleSlotId}",
                                                date = localAtt.date,
                                                slotId = localAtt.scheduleSlotId,
                                                courseId = localAtt.courseId,
                                                courseName = oClass.courseName,
                                                status = localAtt.status,
                                                markedAt = localAtt.markedAt
                                            )
                                        }
                                        val cancelNote = viewModel.getCancellationNote(dateStr, oClass.slotId, oClass.courseId)
                                        val isCancelled = (attRecord?.status?.equals("CANCELLED", ignoreCase = true) == true ||
                                                attRecord?.status?.equals("C", ignoreCase = true) == true ||
                                                viewModel.isSessionCancelled(dateStr, oClass.slotId, oClass.courseId))
                                        OfficialGlassLectureCard(
                                            officialClass = oClass,
                                            attendanceRecord = attRecord,
                                            isCancelled = isCancelled,
                                            cancelNote = cancelNote,
                                            onHide = {
                                                viewModel.hideOfficialClass(oClass.slotId)
                                                android.widget.Toast.makeText(context, "Class hidden. Restore anytime in Profile.", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
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
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(3) { SkeletonCard() }
                                }
                            } else if (timelineItems.isEmpty()) {
                                EmptyStudentScheduleIllustration(
                                    day = dayName,
                                    onImportAI = { navController.navigate("import_timetable") }
                                )
                            } else {
                                LazyColumn(
                                    state = listState,
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
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

                                                // Check if student already marked attendance for this specific slot & course on this date
                                                val attendanceRecord = allAttendance.firstOrNull {
                                                    it.date == dateStr && it.studentId == "self" && (
                                                        it.scheduleSlotId == slot.id ||
                                                        (it.courseId == slot.courseId && it.courseId.isNotBlank())
                                                    )
                                                }
                                                val isMarkedPresent = attendanceRecord?.status?.equals("P", ignoreCase = true) == true ||
                                                        attendanceRecord?.status?.equals("present", ignoreCase = true) == true
                                                val isTeacherCancelled = viewModel.isSessionCancelled(dateStr, slot.id, slot.courseId)
                                                val isMarkedCancelled = attendanceRecord?.status?.equals("CANCELLED", ignoreCase = true) == true ||
                                                        attendanceRecord?.status?.equals("C", ignoreCase = true) == true ||
                                                        isTeacherCancelled
                                                val cancelNote = viewModel.getCancellationNote(dateStr, slot.id, slot.courseId)

                                                val subjectStats = courseAttendanceMap[slot.courseId]

                                                StudentGlassLectureCard(
                                                    slot = slot,
                                                    course = courseMap[slot.courseId],
                                                    isLive = isLive,
                                                    isMarkedPresent = isMarkedPresent,
                                                    isMarkedCancelled = isMarkedCancelled,
                                                    isTeacherCancelled = isTeacherCancelled,
                                                    cancelNote = cancelNote,
                                                    timeHint = timeHint,
                                                    subjectStats = subjectStats,
                                                    onMarkSelfAttendance = {
                                                        val ctx = context
                                                        if (isMarkedPresent) {
                                                            // Undo / Unmark attendance if clicked again
                                                            SoundFeedbackHelper.performSuccessHaptic(ctx)
                                                            viewModel.deleteSelfAttendance(
                                                                date = dateStr,
                                                                slotId = slot.id,
                                                                courseId = slot.courseId
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
                                                    onMarkCancelled = {
                                                        val ctx = context
                                                        if (isMarkedCancelled) {
                                                            SoundFeedbackHelper.performSuccessHaptic(ctx)
                                                            viewModel.deleteSelfAttendance(
                                                                date = dateStr,
                                                                slotId = slot.id,
                                                                courseId = slot.courseId
                                                            )
                                                        } else {
                                                            SoundFeedbackHelper.performSuccessHaptic(ctx)
                                                            viewModel.markSelfAttendance(
                                                                date = dateStr,
                                                                slotId = slot.id,
                                                                courseId = slot.courseId,
                                                                status = "CANCELLED"
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
                }
            }

            // 5. UNIFIED 3-PART BOTTOM CAPSULE NAVBAR (Official • Personal • Profile)
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = if (isDarkTheme) Color(0xFF1E293B).copy(alpha = 0.96f) else Color.White.copy(alpha = 0.96f),
                tonalElevation = 10.dp,
                shadowElevation = 12.dp,
                border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF334155) else accentColor.copy(alpha = 0.25f)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isOfficial = (timetableMode == "OFFICIAL")
                    val isPersonal = (timetableMode == "PERSONAL")

                    // 1. OFFICIAL TIMETABLE TAB
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (isOfficial) accentColor else Color.Transparent)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                timetableMode = "OFFICIAL"
                            }
                            .padding(vertical = 9.dp, horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.School,
                                contentDescription = "Official Timetable",
                                tint = if (isOfficial) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Official",
                                fontWeight = if (isOfficial) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip,
                                color = if (isOfficial) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 2. PERSONAL TIMETABLE TAB
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (isPersonal) accentColor else Color.Transparent)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                timetableMode = "PERSONAL"
                            }
                            .padding(vertical = 9.dp, horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = "Personal Timetable",
                                tint = if (isPersonal) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Personal",
                                fontWeight = if (isPersonal) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip,
                                color = if (isPersonal) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 3. PROFILE TAB
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.Transparent)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                navController.navigate("profile")
                            }
                            .padding(vertical = 9.dp, horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.AccountCircle,
                                contentDescription = "Profile",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Profile",
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
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

    val pendingSyncCount by viewModel.pendingSyncCount.collectAsState(initial = 0)
    val isEngineSyncing by viewModel.isEngineSyncing.collectAsState(initial = false)
    val isOnline by viewModel.isNetworkConnected.collectAsState()

    val haptic = LocalHapticFeedback.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .shadow(
                elevation = 3.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = accentColor.copy(alpha = 0.15f),
                ambientColor = Color.Black.copy(alpha = 0.06f)
            ),
        shape = RoundedCornerShape(20.dp),
        color = if (isDarkTheme) Color(0xFF1E293B) else Color.White,
        border = BorderStroke(
            1.dp,
            if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // 1. TOP ROW: AVATAR (tap profile) + NAME & SUBTITLE (tap profile) + REGISTER + THEME + CHEVRON
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar with smooth gradient and active status badge
                Box(
                    modifier = Modifier
                        .clickable {
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
                                    listOf(Color(0xFF10B981), Color(0xFF059669))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials.ifEmpty { "SS" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }

                    // Online / Cloud sync indicator dot
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (isDarkTheme) Color(0xFF1E293B) else Color.White)
                            .padding(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(
                                    if (isSyncing || isEngineSyncing) Color(0xFFF59E0B)
                                    else if (isOnline && isConnected) Color(0xFF10B981)
                                    else Color(0xFF94A3B8)
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Name & Academic Details (Clickable -> Profile)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            navController.navigate("profile")
                        }
                ) {
                    Text(
                        text = "Student Dashboard",
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
                        text = "$name • $branchInfo",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Dedicated Attendance Register Button
                if (stats.second > 0) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.12f),
                        border = BorderStroke(0.8.dp, Color(0xFF10B981).copy(alpha = 0.30f)),
                        modifier = Modifier
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                navController.navigate("student_report")
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Assessment,
                                contentDescription = "Attendance Register",
                                tint = Color(0xFF059669),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "${String.format(Locale.ENGLISH, "%.0f", pct)}%",
                                color = Color(0xFF059669),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                // Quick Theme Toggle button (Dark / Light)
                Box(
                    modifier = Modifier
                        .size(32.dp)
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
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. COMPACT SECONDARY STATUS STRIP: ATTENDANCE RATIO + SSOT CLOUD SYNC
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isDarkTheme) Color(0xFF0F172A).copy(alpha = 0.5f)
                        else Color(0xFFF8FAFC)
                    )
                    .border(
                        0.8.dp,
                        if (isDarkTheme) Color(0xFF334155).copy(alpha = 0.4f)
                        else Color(0xFFE2E8F0),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Attendance Quick Stat (Clickable -> Register)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            navController.navigate("student_report")
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (stats.second > 0) "${stats.first}/${stats.second} attended (${String.format(Locale.ENGLISH, "%.1f", animatedPct)}%)" else "Attendance ready to record",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Cloud Sync Indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { viewModel.triggerSync() }
                ) {
                    Icon(
                        imageVector = if (isEngineSyncing || isSyncing) Icons.Default.Sync
                        else if (pendingSyncCount > 0 || !isOnline) Icons.Default.CloudOff
                        else Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = if (pendingSyncCount > 0 || !isOnline) Color(0xFFD97706) else Color(0xFF059669),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isEngineSyncing || isSyncing) "Syncing..."
                        else if (pendingSyncCount > 0) "$pendingSyncCount queued"
                        else if (!isOnline) "Offline"
                        else "SSOT Synced",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        fontWeight = FontWeight.Bold,
                        color = if (pendingSyncCount > 0 || !isOnline) Color(0xFFD97706) else Color(0xFF059669)
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
    isMarkedCancelled: Boolean = false,
    isTeacherCancelled: Boolean = false,
    cancelNote: String = "",
    timeHint: String? = null,
    subjectStats: Pair<Int, Int>? = null,
    onMarkSelfAttendance: () -> Unit,
    onMarkCancelled: () -> Unit = {},
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

    val isCancelled = isMarkedCancelled || isTeacherCancelled
    val liveGreen = Color(0xFF10B981)
    val isDarkTheme = isSystemInDarkTheme()
    val targetBarColor = if (isCancelled) Color(0xFFEF4444) else if (isLive) liveGreen else subjectColors[abs(slot.courseId.hashCode()) % subjectColors.size]
    val barColor by animateColorAsState(targetBarColor, tween(500), label = "barColor")

    val targetBgColor = if (isCancelled) {
        if (isDarkTheme) Color(0xFF2A0D0D) else Color(0xFFFEF2F2)
    } else if (isLive) liveGreen.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
    val bgColor by animateColorAsState(targetBgColor, tween(500), label = "bgColor")

    val targetBorderColor = if (isCancelled) Color(0xFFEF4444).copy(alpha = 0.6f) else if (isLive) liveGreen.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.18f)
    val borderColor by animateColorAsState(targetBorderColor, tween(500), label = "borderColor")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = if (isCancelled) Color(0xFFEF4444).copy(alpha = 0.3f) else if (isLive) liveGreen.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.16f),
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
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (fullSubjectName.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = fullSubjectName,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isLive) liveGreen else MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.bounceClick(scaleDown = 0.95f) {}
                        ) {
                            Text(
                                text = "${slot.startTime} - ${slot.endTime}",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                                fontWeight = FontWeight.Bold,
                                color = if (isLive) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
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

                Spacer(modifier = Modifier.height(10.dp))

                // Location & Section (Hiding faculty students count)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, "Location", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = slot.room.ifBlank { "Main Hall" },
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
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
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                            )
                        }
                    }
                }

                // Per-Subject Attendance Pill Badge
                if (subjectStats != null && subjectStats.second > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val subPct = (subjectStats.first * 100f) / subjectStats.second
                    val subColor = when {
                        subPct >= 75f -> Color(0xFF10B981)
                        subPct >= 50f -> Color(0xFFF59E0B)
                        else -> Color(0xFFEF4444)
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = subColor.copy(alpha = 0.10f),
                        border = BorderStroke(0.8.dp, subColor.copy(alpha = 0.28f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(subColor)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
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

                Spacer(modifier = Modifier.height(12.dp))

                // 6. STUDENT SELF ATTENDANCE & CLASS CANCELLED ACTION ROW
                if (isCancelled) {
                    if (cancelNote.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isDarkTheme) Color(0xFF3B1212) else Color(0xFFFEE2E2),
                            border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.EventBusy,
                                        contentDescription = null,
                                        tint = Color(0xFFDC2626),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Teacher's Cancellation Note",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF991B1B)
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = cancelNote,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isDarkTheme) Color(0xFFFECDD3) else Color(0xFF7F1D1D)
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isDarkTheme) Color(0xFF450A0A) else Color(0xFFFEE2E2),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.EventBusy,
                                    contentDescription = null,
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "CLASS CANCELLED",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFDC2626),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!isTeacherCancelled) {
                                    TextButton(
                                        onClick = onMarkCancelled,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            "Undo",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Button(
                                    onClick = onMarkSelfAttendance,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("Mark Present", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            StudentSelfAttendanceButton(
                                isMarked = isMarkedPresent,
                                onMark = onMarkSelfAttendance
                            )
                        }
                    }
                }
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
                    .height(48.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF10B981).copy(alpha = glowAlpha),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(18.dp)
                    )
            )
        }

        Box(
            modifier = Modifier
                .graphicsLayer(scaleX = scale, scaleY = scale)
                .fillMaxWidth()
                .height(44.dp)
                .shadow(
                    elevation = if (isMarked) 3.dp else 6.dp,
                    shape = RoundedCornerShape(14.dp),
                    spotColor = if (isMarked) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFF6366F1).copy(alpha = 0.45f)
                )
                .clip(RoundedCornerShape(14.dp))
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

private data class StatusBadgeConfig(
    val bg: Color,
    val border: Color,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tint: Color
)

@Composable
fun OfficialGlassLectureCard(
    officialClass: com.example.data.OfficialClassEntity,
    attendanceRecord: com.example.data.OfficialAttendanceEntity?,
    isCancelled: Boolean = false,
    cancelNote: String = "",
    onHide: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    var showHideConfirm by remember { mutableStateOf(false) }
    val attStatus = attendanceRecord?.status?.uppercase()
    val effectiveCancelled = isCancelled || attStatus == "CANCELLED" || attStatus == "C"

    val isPresent = (attStatus == "P" || attStatus == "PRESENT")
    val isAbsent = (attStatus == "A" || attStatus == "ABSENT")

    val (cardColor, borderBrush) = when {
        effectiveCancelled -> {
            val bg = if (isDark) Color(0xFF2A0D0D) else Color(0xFFFEF2F2)
            val border = Brush.verticalGradient(listOf(Color(0xFFEF4444).copy(alpha = 0.8f), Color(0xFFDC2626).copy(alpha = 0.4f)))
            Pair(bg, border)
        }
        isPresent -> {
            val bg = if (isDark) Color(0xFF0D2818) else Color(0xFFF0FDF4)
            val border = Brush.verticalGradient(listOf(Color(0xFF10B981).copy(alpha = 0.8f), Color(0xFF059669).copy(alpha = 0.4f)))
            Pair(bg, border)
        }
        isAbsent -> {
            val bg = if (isDark) Color(0xFF2D1515) else Color(0xFFFEF2F2)
            val border = Brush.verticalGradient(listOf(Color(0xFFEF4444).copy(alpha = 0.8f), Color(0xFFB91C1C).copy(alpha = 0.4f)))
            Pair(bg, border)
        }
        else -> {
            val bg = if (isDark) Color(0xFF1E293B) else Color.White
            val border = Brush.verticalGradient(
                listOf(
                    if (isDark) Color(0xFF475569) else Color(0xFFCBD5E1),
                    if (isDark) Color(0xFF334155).copy(alpha = 0.5f) else Color(0xFFF1F5F9).copy(alpha = 0.5f)
                )
            )
            Pair(bg, border)
        }
    }

    if (showHideConfirm) {
        AlertDialog(
            onDismissRequest = { showHideConfirm = false },
            title = { Text("Hide this class?", fontWeight = FontWeight.Bold) },
            text = {
                Text("This will hide '${officialClass.courseName}' from your active schedule.\n\nYou can unhide it anytime from your Profile settings.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showHideConfirm = false
                        onHide()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Hide Class")
                }
            },
            dismissButton = {
                TextButton(onClick = { showHideConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = if (effectiveCancelled) Color(0xFFEF4444).copy(alpha = 0.2f)
                else if (isPresent) Color(0xFF10B981).copy(alpha = 0.15f)
                else Color.Black.copy(alpha = 0.08f)
            ),
        shape = RoundedCornerShape(16.dp),
        color = cardColor,
        border = BorderStroke(1.dp, borderBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // ROW 1: Type Pill + Time Slot + Hide Class Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val pillBg = when {
                        effectiveCancelled -> Color(0xFFEF4444).copy(alpha = 0.15f)
                        isPresent -> Color(0xFF10B981).copy(alpha = 0.15f)
                        isAbsent -> Color(0xFFEF4444).copy(alpha = 0.15f)
                        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                    }
                    val pillText = when {
                        effectiveCancelled -> Color(0xFFDC2626)
                        isPresent -> Color(0xFF059669)
                        isAbsent -> Color(0xFFDC2626)
                        else -> MaterialTheme.colorScheme.primary
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = pillBg
                    ) {
                        Text(
                            text = officialClass.courseCode.ifBlank { "OFFICIAL" },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = pillText,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }

                    // Time display
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(
                                if (isDark) Color(0xFF0F172A).copy(alpha = 0.6f) else Color(0xFFF1F5F9),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            Icons.Default.AccessTime,
                            contentDescription = null,
                            modifier = Modifier.size(11.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${officialClass.startTime} - ${officialClass.endTime}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (effectiveCancelled) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = "CANCELLED",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { showHideConfirm = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.VisibilityOff,
                        contentDescription = "Hide Class",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ROW 2: Subject / Course Name
            Text(
                text = officialClass.courseName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                fontSize = 14.5.sp,
                color = if (effectiveCancelled) {
                    if (isDark) Color(0xFFFCA5A5) else Color(0xFF7F1D1D)
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(3.dp))

            // ROW 3: Faculty Name (small) + Room & Section to the right in one compact line
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Faculty name (smaller, compact)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(11.dp),
                        tint = Color(0xFF3B82F6)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = officialClass.facultyName.ifBlank { "Faculty Instructor" },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Room info (to the right)
                if (officialClass.room.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Icon(
                            Icons.Default.MeetingRoom,
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = officialClass.room,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Section info (to the right of room)
                if (officialClass.section.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Icon(
                            Icons.Default.Group,
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = officialClass.section,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Teacher Cancellation Note (if any)
            if (effectiveCancelled && cancelNote.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isDark) Color(0xFF3B1212) else Color(0xFFFFF1F2),
                    border = BorderStroke(1.dp, Color(0xFFFCA5A5).copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.EventBusy,
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = cancelNote,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isDark) Color(0xFFFECDD3) else Color(0xFF881337),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Live Attendance Status Badge (Matching StudentGlassLectureCard inner button shape & height)
            val badgeConfig = when {
                effectiveCancelled -> {
                    StatusBadgeConfig(
                        bg = if (isDark) Color(0xFF450A0A) else Color(0xFFFEE2E2),
                        border = Color(0xFFEF4444).copy(alpha = 0.6f),
                        label = "Class Cancelled by Faculty",
                        icon = Icons.Default.EventBusy,
                        tint = Color(0xFFDC2626)
                    )
                }
                isPresent -> {
                    StatusBadgeConfig(
                        bg = Color(0xFF10B981),
                        border = Color(0xFF059669),
                        label = "Marked Present by Faculty",
                        icon = Icons.Default.Check,
                        tint = Color.White
                    )
                }
                isAbsent -> {
                    StatusBadgeConfig(
                        bg = Color(0xFFEF4444),
                        border = Color(0xFFDC2626),
                        label = "Marked Absent by Faculty",
                        icon = Icons.Default.Close,
                        tint = Color.White
                    )
                }
                else -> {
                    StatusBadgeConfig(
                        bg = if (isDark) Color(0xFF334155).copy(alpha = 0.6f) else Color(0xFFE2E8F0),
                        border = if (isDark) Color(0xFF475569) else Color(0xFFCBD5E1),
                        label = "Official • Awaiting Attendance",
                        icon = Icons.Default.Schedule,
                        tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569)
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
                shape = RoundedCornerShape(10.dp),
                color = badgeConfig.bg,
                border = BorderStroke(0.8.dp, badgeConfig.border)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        badgeConfig.icon,
                        contentDescription = null,
                        tint = badgeConfig.tint,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        badgeConfig.label,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeConfig.tint
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "(Read Only)",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Normal,
                        color = badgeConfig.tint.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyOfficialScheduleIllustration(
    day: String,
    studentEmail: String,
    hasPersonalClasses: Boolean = false,
    personalClassCount: Int = 0,
    onSwitchToPersonal: () -> Unit = {},
    onSync: () -> Unit = {},
    onChangeEmail: () -> Unit = {},
    onLoadDemo: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.School,
                contentDescription = null,
                modifier = Modifier.size(46.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Official Classes on $day",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Classes automatically appear here when faculty adds your campus email to their course batches.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(14.dp))
        
        // Enrolled Email Chip - Clickable to change
        Surface(
            onClick = onChangeEmail,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = studentEmail.ifBlank { "student.demo@campus.edu" },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.Default.Edit, contentDescription = "Edit Email", modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        if (hasPersonalClasses) {
            Button(
                onClick = onSwitchToPersonal,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Switch to Personal Schedule ($personalClassCount classes)", fontWeight = FontWeight.SemiBold)
            }
        }
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = onSync,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Sync Feed", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            
            OutlinedButton(
                onClick = onLoadDemo,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Load All Demo Classes", fontSize = 13.sp)
            }
        }
    }
}
