package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.data.*
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

data class ScheduleBlockState(
    val id: String = UUID.randomUUID().toString(),
    var timeRange: String = "",
    var selectedDays: Set<String> = emptySet(),
    var location: String = ""
)

// --- Custom Interactivity Modifiers & Composables ---

private fun Modifier.bounceClick(
    haptic: HapticFeedback? = null,
    onClick: () -> Unit
) = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 400f),
        label = "bounce"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                isPressed = true
                haptic?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                val up = waitForUpOrCancellation()
                isPressed = false
                if (up != null) {
                    onClick()
                }
            }
        }
}

@Composable
fun StaggeredEntrance(index: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 60L) // Staggered by 60ms
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 100f)) { it / 4 }
    ) {
        content()
    }
}

@Composable
fun AnimatedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    baseColor: Color,
    accentColor: Color,
    isError: Boolean = false,
    haptic: HapticFeedback? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    var lastLength by remember { mutableStateOf(value.length) }
    var breathToggle by remember { mutableStateOf(false) }
    
    LaunchedEffect(value) {
        if (value.length != lastLength) {
            breathToggle = !breathToggle
            lastLength = value.length
        }
    }
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) (if (breathToggle) 1.01f else 1.02f) else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = 300f),
        label = "breathe"
    )
    
    val shadowAlpha by animateFloatAsState(if (isFocused) 0.15f else 0f, label = "shadow")
    val borderColor by animateColorAsState(if (isFocused) accentColor else Color.Transparent, label = "border")
    
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal) },
        isError = isError,
        shape = RoundedCornerShape(20.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = baseColor.copy(alpha = 0.4f),
            unfocusedContainerColor = baseColor.copy(alpha = 0.2f),
            focusedBorderColor = borderColor,
            unfocusedBorderColor = Color.Transparent,
        ),
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (isFocused) 12.dp else 0.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = accentColor.copy(alpha = shadowAlpha),
                ambientColor = accentColor.copy(alpha = shadowAlpha)
            )
            .onFocusChanged { 
                if (it.isFocused && !isFocused) {
                    haptic?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                isFocused = it.isFocused 
            }
    )
}

private fun Modifier.overlayFadeEdges(
    color: Color,
    right: Boolean = false
) = this.drawWithContent {
    drawContent()
    if (right) {
        drawRect(
            brush = Brush.horizontalGradient(listOf(Color.Transparent, color), startX = size.width - 40.dp.toPx(), endX = size.width),
            topLeft = Offset(size.width - 40.dp.toPx(), 0f),
            size = Size(40.dp.toPx(), size.height)
        )
    }
}

// --- Main Screen ---

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditBatchScreen(
    navController: NavController,
    viewModel: MainViewModel,
    batchId: String? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    
    var isLoading by remember { mutableStateOf(false) }
    
    // Modern Theming
    val accentColor = Color(0xFF6750A4)
    val colorScheme = lightColorScheme(
        primary = accentColor,
        surface = Color(0xFFFFFFFF),
        background = Color(0xFFF4F6F9),
        surfaceVariant = Color(0xFFFFFFFF),
        onSurfaceVariant = Color(0xFF49454F)
    )
    
    // Pastel field groups
    val fieldGroup1Color = Color(0xFFE8DEF8) // Purple tint
    val fieldGroup2Color = Color(0xFFD3E4FF) // Blue tint
    val fieldGroup3Color = Color(0xFFF2E7FE) // Pink/Purple tint
    
    // Form state
    var courseName by remember { mutableStateOf("") }
    var courseCode by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var semester by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("") }
    var defaultLocation by remember { mutableStateOf("") }
    
    var scheduleBlocks by remember { mutableStateOf(listOf<ScheduleBlockState>()) }
    var students by remember { mutableStateOf(listOf<StudentImport>()) }
    
    // Mode toggle
    var scheduleInputMode by remember { mutableStateOf("Blocks") } // "Blocks" or "Grid"
    
    // Grid State
    val gridDays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    val gridHours = (0..23).map { hour ->
        val amPmStart = if (hour < 12) "AM" else "PM"
        val startHour = if (hour % 12 == 0) 12 else hour % 12
        val amPmEnd = if ((hour + 1) < 12 || (hour + 1) == 24) "AM" else "PM"
        val endHour = if ((hour + 1) % 12 == 0) 12 else (hour + 1) % 12
        val displayStart = if (startHour == 0) 12 else startHour
        val displayEnd = if (endHour == 0) 12 else endHour
        String.format("%02d:00 %s - %02d:00 %s", displayStart, amPmStart, displayEnd, amPmEnd)
    }
    var isGridFullScreenOpen by remember { mutableStateOf(false) }
    var allBookedSlots by remember { mutableStateOf(listOf<ScheduleSlotEntity>()) }
    
    fun parseTimeMin(timeStr: String): Int {
        try {
            if (timeStr.contains("AM") || timeStr.contains("PM")) {
                val parts = timeStr.split(" ")
                if (parts.isEmpty()) return 0
                val hm = parts[0].split(":")
                var h = hm[0].toInt()
                val m = hm.getOrNull(1)?.toInt() ?: 0
                if (timeStr.contains("PM") && h < 12) h += 12
                if (timeStr.contains("AM") && h == 12) h = 0
                return h * 60 + m
            } else {
                val hm = timeStr.split(":")
                var h = hm[0].toInt()
                val m = hm.getOrNull(1)?.toInt() ?: 0
                return h * 60 + m
            }
        } catch(e: Exception) { return 0 }
    }

    val parsedBookedSlots by remember(allBookedSlots) {
        derivedStateOf {
            allBookedSlots.map { slot ->
                val startMin = parseTimeMin(slot.startTime)
                val endMin = if (slot.endTime.isNotBlank()) parseTimeMin(slot.endTime) else startMin + 60
                Triple(slot.dayOfWeek, startMin, endMin)
            }
        }
    }
    
    LaunchedEffect(batchId) {
        try {
            val allSlots = viewModel.getAllScheduleSlotsSync()
            allBookedSlots = allSlots.filter { batchId == null || it.courseId != batchId }
        } catch(e: Exception) {}
    }
    var selectedGridCells by remember { mutableStateOf(setOf<Pair<String, String>>()) }
    
    // Bulk add state
    var bulkStudentsText by remember { mutableStateOf("") }
    var isBulkAddMode by remember { mutableStateOf(false) }
    var isCsvImportModalOpen by remember { mutableStateOf(false) }
    val isFaculty by viewModel.isFaculty.collectAsState()
    val userRole by viewModel.userRole.collectAsState()
    val isTeacher = isFaculty && userRole != "student"
    
    // Load existing data if editing
    LaunchedEffect(batchId) {
        if (batchId != null) {
            isLoading = true
            val batch = viewModel.getBatchForEdit(batchId)
            if (batch != null) {
                courseName = batch.course?.name ?: ""
                courseCode = batch.course?.code ?: ""
                year = batch.year ?: ""
                semester = batch.semester ?: ""
                section = batch.section ?: ""
                defaultLocation = batch.location ?: ""
                
                val blocks = mutableListOf<ScheduleBlockState>()
                val gridSelections = mutableSetOf<Pair<String, String>>()
                val grouped = (batch.weeklySchedule ?: emptyList()).groupBy { "${it.time}|${it.location}" }
                
                for ((_, scheds) in grouped) {
                    if (scheds.isEmpty()) continue
                    val first = scheds.first()
                    val timeString = first.time ?: ""
                    
                    if (gridHours.contains(timeString) && (first.location.isNullOrBlank() || first.location == batch.location)) {
                        scheds.forEach { s ->
                            s.day?.let { day -> gridSelections.add(Pair(day, timeString)) }
                        }
                    } else {
                        blocks.add(
                            ScheduleBlockState(
                                timeRange = timeString,
                                location = first.location ?: "",
                                selectedDays = scheds.mapNotNull { it.day }.toSet()
                            )
                        )
                    }
                }
                scheduleBlocks = blocks
                selectedGridCells = gridSelections
                
                if (gridSelections.isNotEmpty() && blocks.isEmpty()) {
                    scheduleInputMode = "Grid"
                }
                
                students = batch.students ?: emptyList()
            }
            isLoading = false
        }
    }
    
    MaterialTheme(colorScheme = colorScheme) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (batchId == null) "Add New Class" else "Edit Class", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.background)
                )
            },
            containerColor = colorScheme.background
        ) { padding ->
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accentColor)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // Summary Card
                    StaggeredEntrance(index = 0) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(12.dp, RoundedCornerShape(24.dp), spotColor = accentColor.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text("Summary", fontWeight = FontWeight.Bold, color = colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                val totalSlots = scheduleBlocks.sumOf { it.selectedDays.size } + selectedGridCells.size
                                val summaryText = if (isTeacher) "$totalSlots class slots · ${students.size} students" else "$totalSlots weekly class slots"
                                Text(summaryText, color = colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Batch Details
                    StaggeredEntrance(index = 1) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(8.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha = 0.05f)),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                Text("Class Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                
                                AnimatedTextField(
                                    value = courseName,
                                    onValueChange = { newName ->
                                        val oldAbbrev = com.example.ui.util.SubjectFormatting.generateAbbreviation(courseName)
                                        courseName = newName
                                        if (courseCode.isBlank() || courseCode.equals(oldAbbrev, ignoreCase = true)) {
                                            courseCode = com.example.ui.util.SubjectFormatting.generateAbbreviation(newName)
                                        }
                                    },
                                    label = "Course Name *",
                                    modifier = Modifier.fillMaxWidth(),
                                    isError = courseName.isBlank(),
                                    baseColor = fieldGroup1Color,
                                    accentColor = accentColor,
                                    haptic = haptic
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    AnimatedTextField(
                                        value = courseCode,
                                        onValueChange = { courseCode = it },
                                        label = "Course Code",
                                        modifier = Modifier.weight(1f),
                                        baseColor = fieldGroup2Color,
                                        accentColor = Color(0xFF006A60),
                                        haptic = haptic
                                    )
                                    AnimatedTextField(
                                        value = section,
                                        onValueChange = { section = it },
                                        label = "Section",
                                        modifier = Modifier.weight(1f),
                                        baseColor = fieldGroup2Color,
                                        accentColor = Color(0xFF006A60),
                                        haptic = haptic
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    AnimatedTextField(
                                        value = year,
                                        onValueChange = { year = it },
                                        label = "Year",
                                        modifier = Modifier.weight(1f),
                                        baseColor = fieldGroup3Color,
                                        accentColor = Color(0xFF984061),
                                        haptic = haptic
                                    )
                                    AnimatedTextField(
                                        value = semester,
                                        onValueChange = { semester = it },
                                        label = "Semester",
                                        modifier = Modifier.weight(1f),
                                        baseColor = fieldGroup3Color,
                                        accentColor = Color(0xFF984061),
                                        haptic = haptic
                                    )
                                }
                                AnimatedTextField(
                                    value = defaultLocation,
                                    onValueChange = { defaultLocation = it },
                                    label = "Default Room (Optional)",
                                    modifier = Modifier.fillMaxWidth(),
                                    baseColor = colorScheme.background,
                                    accentColor = accentColor,
                                    haptic = haptic
                                )
                            }
                        }
                    }

                    // Weekly Schedule Builder
                    StaggeredEntrance(index = 2) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(8.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha = 0.05f)),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Text("Weekly Schedule", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    
                                    // Segmented Toggle
                                    val isGrid = scheduleInputMode == "Grid"
                                    val toggleOffset by animateDpAsState(if (isGrid) 80.dp else 0.dp, spring(dampingRatio = 0.7f, stiffness = 200f))
                                    
                                    Box(
                                        modifier = Modifier
                                            .background(colorScheme.background, RoundedCornerShape(24.dp))
                                            .padding(4.dp)
                                            .width(160.dp)
                                            .height(44.dp)
                                    ) {
                                        // Sliding pill
                                        Box(
                                            modifier = Modifier
                                                .offset(x = toggleOffset)
                                                .width(80.dp)
                                                .fillMaxHeight()
                                                .shadow(4.dp, RoundedCornerShape(20.dp), spotColor = accentColor.copy(alpha = 0.4f))
                                                .background(accentColor, RoundedCornerShape(20.dp))
                                        )
                                        Row(Modifier.fillMaxSize()) {
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                                    .bounceClick(haptic) { scheduleInputMode = "Blocks" },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("Blocks", color = if (!isGrid) Color.White else colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                                    .bounceClick(haptic) { scheduleInputMode = "Grid" },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("Grid", color = if (isGrid) Color.White else colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }
                                }
                                
                                AnimatedVisibility(visible = scheduleInputMode == "Grid") {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text("Tap the button below to open the full-screen schedule grid.", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp))
                                        
                                        Button(
                                            onClick = { isGridFullScreenOpen = true },
                                            modifier = Modifier.fillMaxWidth().height(56.dp),
                                            shape = RoundedCornerShape(16.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.White)
                                        ) {
                                            Icon(Icons.Default.Fullscreen, "Open Grid")
                                            Spacer(Modifier.width(8.dp))
                                            Text("Open Full-Screen Grid", fontWeight = FontWeight.Bold)
                                        }
                                        
                                        if (selectedGridCells.isNotEmpty()) {
                                            Spacer(Modifier.height(16.dp))
                                            Text("${selectedGridCells.size} slots selected.", style = MaterialTheme.typography.labelMedium, color = accentColor, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                AnimatedVisibility(visible = scheduleInputMode == "Blocks") {
                                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        scheduleBlocks.forEachIndexed { index, block ->
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(containerColor = colorScheme.background),
                                                shape = RoundedCornerShape(20.dp)
                                            ) {
                                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text("Time Slot ${index + 1}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                                        IconButton(
                                                            onClick = {
                                                                val newBlocks = scheduleBlocks.toMutableList()
                                                                newBlocks.removeAt(index)
                                                                scheduleBlocks = newBlocks
                                                            }
                                                        ) {
                                                            Icon(Icons.Default.Delete, "Remove", tint = colorScheme.error)
                                                        }
                                                    }
                                                    
                                                    AnimatedTextField(
                                                        value = block.timeRange,
                                                        onValueChange = { newTime ->
                                                            val newBlocks = scheduleBlocks.toMutableList()
                                                            newBlocks[index] = block.copy(timeRange = newTime)
                                                            scheduleBlocks = newBlocks
                                                        },
                                                        label = "Time (e.g., 10:00 AM - 11:30 AM)",
                                                        modifier = Modifier.fillMaxWidth(),
                                                        baseColor = Color(0xFFE2E2E9),
                                                        accentColor = accentColor,
                                                        haptic = haptic
                                                    )
                                                    
                                                    Text("Select Days", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                                                    
                                                    FlowRow(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { dayOpt ->
                                                            val isSelected = block.selectedDays.contains(dayOpt)
                                                            FilterChip(
                                                                selected = isSelected,
                                                                onClick = {
                                                                    val newBlocks = scheduleBlocks.toMutableList()
                                                                    val newDays = block.selectedDays.toMutableSet()
                                                                    if (isSelected) newDays.remove(dayOpt) else newDays.add(dayOpt)
                                                                    newBlocks[index] = block.copy(selectedDays = newDays)
                                                                    scheduleBlocks = newBlocks
                                                                },
                                                                label = { Text(dayOpt) },
                                                                shape = RoundedCornerShape(12.dp)
                                                            )
                                                        }
                                                    }
                                                    
                                                    AnimatedTextField(
                                                        value = block.location,
                                                        onValueChange = { newLoc ->
                                                            val newBlocks = scheduleBlocks.toMutableList()
                                                            newBlocks[index] = block.copy(location = newLoc)
                                                            scheduleBlocks = newBlocks
                                                        },
                                                        label = "Location Override (Optional)",
                                                        modifier = Modifier.fillMaxWidth(),
                                                        baseColor = Color(0xFFE2E2E9),
                                                        accentColor = accentColor,
                                                        haptic = haptic
                                                    )
                                                }
                                            }
                                        }
                                        
                                        Button(
                                            onClick = {
                                                val newBlocks = scheduleBlocks.toMutableList()
                                                newBlocks.add(ScheduleBlockState())
                                                scheduleBlocks = newBlocks
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(56.dp),
                                            shape = RoundedCornerShape(16.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.background, contentColor = accentColor)
                                        ) {
                                            Icon(Icons.Default.Add, "Add")
                                            Spacer(Modifier.width(8.dp))
                                            Text("Add Time Slot", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Students Section (Faculty only)
                    if (isTeacher) {
                        StaggeredEntrance(index = 3) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(8.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha = 0.05f)),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "Students (${students.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilledTonalButton(
                                        onClick = { isCsvImportModalOpen = true },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = Color(0xFF10B981).copy(alpha = 0.15f),
                                            contentColor = Color(0xFF059669)
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Import CSV", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    TextButton(
                                        onClick = { isBulkAddMode = !isBulkAddMode }
                                    ) {
                                        Text(if (isBulkAddMode) "Manual" else "Paste", fontWeight = FontWeight.Bold)
                                    }
                                }
                                
                                AnimatedVisibility(visible = isBulkAddMode) {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        AnimatedTextField(
                                            value = bulkStudentsText,
                                            onValueChange = { bulkStudentsText = it },
                                            label = "Paste students (Name, RollNo)",
                                            modifier = Modifier.fillMaxWidth().height(150.dp),
                                            baseColor = fieldGroup2Color,
                                            accentColor = accentColor,
                                            haptic = haptic
                                        )
                                        Button(
                                            onClick = {
                                                val lines = bulkStudentsText.split("\n").filter { it.isNotBlank() }
                                                val newStudents = lines.map { line ->
                                                    val parts = line.split(",")
                                                    val name = parts.getOrNull(0)?.trim() ?: ""
                                                    val roll = parts.getOrNull(1)?.trim() ?: ""
                                                    StudentImport(UUID.randomUUID().toString(), name, roll)
                                                }
                                                val combined = students.toMutableList()
                                                combined.addAll(newStudents)
                                                students = combined
                                                bulkStudentsText = ""
                                                isBulkAddMode = false
                                                Toast.makeText(context, "Added ${newStudents.size} students", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(50.dp),
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            Text("Process Bulk List")
                                        }
                                    }
                                }
                                
                                AnimatedVisibility(visible = !isBulkAddMode) {
                                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        students.forEachIndexed { index, student ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                AnimatedTextField(
                                                    value = student.name ?: "",
                                                    onValueChange = {
                                                        val newStudents = students.toMutableList()
                                                        newStudents[index] = student.copy(name = it)
                                                        students = newStudents
                                                    },
                                                    label = "Name",
                                                    modifier = Modifier.weight(1.5f),
                                                    baseColor = colorScheme.background,
                                                    accentColor = accentColor,
                                                    haptic = haptic
                                                )
                                                AnimatedTextField(
                                                    value = student.rollNumber ?: "",
                                                    onValueChange = {
                                                        val newStudents = students.toMutableList()
                                                        newStudents[index] = student.copy(rollNumber = it)
                                                        students = newStudents
                                                    },
                                                    label = "Roll No",
                                                    modifier = Modifier.weight(1f),
                                                    baseColor = colorScheme.background,
                                                    accentColor = accentColor,
                                                    haptic = haptic
                                                )
                                                IconButton(
                                                    onClick = {
                                                        val newStudents = students.toMutableList()
                                                        newStudents.removeAt(index)
                                                        students = newStudents
                                                    }
                                                ) {
                                                    Icon(Icons.Default.Delete, "Remove", tint = colorScheme.error)
                                                }
                                            }
                                        }
                                        
                                        Button(
                                            onClick = {},
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(56.dp)
                                                .bounceClick(haptic) {
                                                    val newStudents = students.toMutableList()
                                                    newStudents.add(StudentImport(UUID.randomUUID().toString(), "", ""))
                                                    students = newStudents
                                                },
                                            shape = RoundedCornerShape(16.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.background, contentColor = accentColor)
                                        ) {
                                            Icon(Icons.Default.Add, "Add")
                                            Spacer(Modifier.width(8.dp))
                                            Text("Add Student", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                        }
                    }
                    
                    StaggeredEntrance(index = 4) {
                        Button(
                            onClick = {
                                if (courseName.isBlank()) {
                                    Toast.makeText(context, "Course name is required.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val validBlocks = scheduleBlocks.filter { it.timeRange.isNotBlank() }
                                if (validBlocks.any { it.selectedDays.isEmpty() }) {
                                    Toast.makeText(context, "All time slots must have at least one day selected.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (validBlocks.isEmpty() && selectedGridCells.isEmpty()) {
                                    Toast.makeText(context, "Please configure at least one class schedule slot.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                fun normalizeDayToFull(d: String): String {
                                    val clean = d.trim().lowercase()
                                    return when {
                                        clean.startsWith("mon") -> "Monday"
                                        clean.startsWith("tue") -> "Tuesday"
                                        clean.startsWith("wed") -> "Wednesday"
                                        clean.startsWith("thu") -> "Thursday"
                                        clean.startsWith("fri") -> "Friday"
                                        clean.startsWith("sat") -> "Saturday"
                                        clean.startsWith("sun") -> "Sunday"
                                        else -> d.trim().replaceFirstChar { it.uppercase() }
                                    }
                                }
                                
                                val finalSchedules = validBlocks.flatMap { block ->
                                    block.selectedDays.map { day ->
                                        ScheduleImport(day = normalizeDayToFull(day), time = block.timeRange, location = block.location.takeIf { it.isNotBlank() })
                                    }
                                } + selectedGridCells.map { cell ->
                                    ScheduleImport(day = normalizeDayToFull(cell.first), time = cell.second, location = defaultLocation.takeIf { it.isNotBlank() })
                                }
                                
                                val finalBatch = BatchImport(
                                    batchId = batchId ?: UUID.randomUUID().toString(),
                                    year = year,
                                    semester = semester,
                                    course = CourseImport(code = courseCode, name = courseName),
                                    section = section,
                                    location = defaultLocation,
                                    weeklySchedule = finalSchedules,
                                    students = students
                                )
                                
                                isLoading = true
                                viewModel.saveSingleBatch(
                                    batch = finalBatch,
                                    onSuccess = {
                                        isLoading = false
                                        Toast.makeText(context, "Saved successfully!", Toast.LENGTH_SHORT).show()
                                        navController.popBackStack()
                                    },
                                    onError = { err ->
                                        isLoading = false
                                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                    }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .shadow(8.dp, RoundedCornerShape(20.dp), spotColor = accentColor.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                        ) {
                            Text("Save Class", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    
        if (isGridFullScreenOpen) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { isGridFullScreenOpen = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = false)
            ) {
                val activity = LocalContext.current as? android.app.Activity
                DisposableEffect(Unit) {
                    val original = activity?.requestedOrientation
                    // Allow full sensor rotation for this dialog
                    activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                    onDispose {
                        original?.let { activity.requestedOrientation = it }
                    }
                }
                
                val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                
                val verticalScrollState = rememberScrollState()
                val horizontalScrollState = rememberScrollState()
                val density = androidx.compose.ui.platform.LocalDensity.current
                
                val rowHeight by animateDpAsState(if (isLandscape) 44.dp else 52.dp, spring(dampingRatio = 0.8f))
                val timeColumnWidth = 110.dp
                val screenWidthDp = configuration.screenWidthDp.dp
                val availableGridWidth = screenWidthDp - timeColumnWidth
                val minDayWidth = 80.dp
                val dayWidth = if (isLandscape) maxOf(minDayWidth, availableGridWidth / gridDays.size) else 100.dp
                
                LaunchedEffect(Unit) {
                    // Scroll 8 hours down
                    val offset = with(density) { (8 * 52).dp.toPx() }.toInt()
                    verticalScrollState.scrollTo(offset)
                }
                
                Surface(modifier = Modifier.fillMaxSize(), color = colorScheme.background) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            
                            // Top Header (Portrait)
                            AnimatedVisibility(
                                visible = !isLandscape,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(onClick = { isGridFullScreenOpen = false }) {
                                            Icon(Icons.Default.Close, contentDescription = "Close")
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text("Select Slots", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                        
                                        // Legend
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(modifier = Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF388E3C)))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Available", style = MaterialTheme.typography.labelSmall)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(modifier = Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFD32F2F)))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Booked", style = MaterialTheme.typography.labelSmall)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(modifier = Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(accentColor))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Selected", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                        Spacer(Modifier.width(16.dp))
                                    }
                                    Divider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))
                                }
                            }
                            
                            // Mini Legend (Landscape)
                            AnimatedVisibility(
                                visible = isLandscape,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().background(colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(vertical = 4.dp, horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFF388E3C)))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Avail", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFFD32F2F)))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Book", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(accentColor))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Sel", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                                    }
                                }
                            }
                            
                            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                // Left Sticky Column (Times)
                                Column(modifier = Modifier.width(timeColumnWidth)) {
                                    Box(modifier = Modifier.height(rowHeight)) // Header spacing
                                    Divider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    Column(modifier = Modifier.verticalScroll(verticalScrollState)) {
                                        gridHours.forEach { hour ->
                                            Box(
                                                modifier = Modifier.height(rowHeight).fillMaxWidth().padding(horizontal = 4.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(hour, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                                            }
                                            Divider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        }
                                    }
                                }
                                
                                Divider(modifier = Modifier.fillMaxHeight().width(1.dp), color = colorScheme.outlineVariant.copy(alpha = 0.5f))
                                
                                // Scrollable Days and Cells
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .overlayFadeEdges(colorScheme.background, right = true)
                                        .horizontalScroll(horizontalScrollState)
                                ) {
                                    // Header (Days)
                                    Row(modifier = Modifier.height(rowHeight), verticalAlignment = Alignment.CenterVertically) {
                                        gridDays.forEach { day ->
                                            Box(
                                                modifier = Modifier
                                                    .width(dayWidth)
                                                    .padding(horizontal = 2.dp, vertical = 2.dp)
                                                    .fillMaxHeight()
                                                    .background(colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(day.take(3), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = accentColor)
                                            }
                                        }
                                        if (!isLandscape) Spacer(modifier = Modifier.width(40.dp))
                                    }
                                    
                                    Divider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    
                                    // Grid Cells
                                    Column(modifier = Modifier.verticalScroll(verticalScrollState)) {
                                        gridHours.forEachIndexed { rowIndex, hour ->
                                            Row(modifier = Modifier.height(rowHeight), verticalAlignment = Alignment.CenterVertically) {
                                                gridDays.forEachIndexed { colIndex, day ->
                                                    val isSelected = selectedGridCells.contains(Pair(day, hour))
                                                    
                                                    val cellStartMin = rowIndex * 60
                                                    val cellEndMin = (rowIndex + 1) * 60
                                                    val isBooked = parsedBookedSlots.any { (bDay, bStart, bEnd) ->
                                                        bDay == day && (bStart < cellEndMin && bEnd > cellStartMin)
                                                    }
                                                    
                                                    val cellScale by animateFloatAsState(if (isSelected) 1f else 0.95f, spring(dampingRatio = 0.5f), label = "cellScale")
                                                    
                                                    val cellColor = if (isSelected) accentColor else if (isBooked) Color(0xFFD32F2F) else Color(0xFF388E3C)
                                                    val colBg = if (colIndex % 2 == 1) colorScheme.surfaceVariant.copy(alpha = 0.2f) else Color.Transparent
                                                    
                                                    Box(
                                                        modifier = Modifier
                                                            .width(dayWidth)
                                                            .fillMaxHeight()
                                                            .background(colBg)
                                                            .padding(horizontal = 4.dp, vertical = 4.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .bounceClick(haptic) {
                                                                    if (isBooked && !isSelected) {
                                                                        Toast.makeText(context, "Slot is already booked and cannot be selected.", Toast.LENGTH_SHORT).show()
                                                                        return@bounceClick
                                                                    }
                                                                    val newSet = selectedGridCells.toMutableSet()
                                                                    val pair = Pair(day, hour)
                                                                    if (isSelected) {
                                                                        newSet.remove(pair)
                                                                    } else {
                                                                        newSet.add(pair)
                                                                    }
                                                                    selectedGridCells = newSet
                                                                }
                                                                .graphicsLayer {
                                                                    scaleX = cellScale
                                                                    scaleY = cellScale
                                                                }
                                                                .background(cellColor.copy(alpha = if (isSelected || isBooked) 1f else 0.15f), RoundedCornerShape(8.dp)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            if (isSelected) {
                                                                Icon(Icons.Default.Check, "Selected", tint = Color.White, modifier = Modifier.size(if (isLandscape) 16.dp else 20.dp))
                                                            } else if (isBooked) {
                                                                Icon(Icons.Default.Close, "Booked", tint = Color.White, modifier = Modifier.size(if (isLandscape) 16.dp else 20.dp))
                                                            }
                                                        }
                                                    }
                                                }
                                                if (!isLandscape) Spacer(modifier = Modifier.width(40.dp))
                                            }
                                            Divider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Floating Close Button (Landscape only)
                        AnimatedVisibility(
                            visible = isLandscape,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut(),
                            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                        ) {
                            FloatingActionButton(
                                onClick = { isGridFullScreenOpen = false },
                                containerColor = colorScheme.surface,
                                contentColor = colorScheme.onSurface,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close Grid")
                            }
                        }
                    }
                }
            }
        }

        if (isCsvImportModalOpen) {
            BulkCsvImportDialog(
                viewModel = viewModel,
                targetMode = CsvImportTargetMode.RETURN_TO_CALLER,
                onDismiss = { isCsvImportModalOpen = false },
                onImportFinished = { importedList ->
                    val combined = students.toMutableList()
                    combined.addAll(importedList)
                    students = combined
                    Toast.makeText(context, "Added ${importedList.size} students from CSV", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}
