import os

content = """package com.example.ui.screens

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

fun Modifier.bounceClick(
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

fun Modifier.overlayFadeEdges(
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
    val gridDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val gridHours = listOf(
        "08:00 AM - 09:00 AM",
        "09:00 AM - 10:00 AM",
        "10:00 AM - 11:00 AM",
        "11:00 AM - 12:00 PM",
        "12:00 PM - 01:00 PM",
        "01:00 PM - 02:00 PM",
        "02:00 PM - 03:00 PM",
        "03:00 PM - 04:00 PM",
        "04:00 PM - 05:00 PM",
        "05:00 PM - 06:00 PM",
        "06:00 PM - 07:00 PM",
        "07:00 PM - 08:00 PM"
    )
    var selectedGridCells by remember { mutableStateOf(setOf<Pair<String, String>>()) }
    
    // Bulk add state
    var bulkStudentsText by remember { mutableStateOf("") }
    var isBulkAddMode by remember { mutableStateOf(false) }
    
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
                        IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.bounceClick(haptic) {}) {
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
                                Text("${scheduleBlocks.size + selectedGridCells.size} class slots · ${students.size} students", color = colorScheme.onSurfaceVariant)
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
                                    onValueChange = { courseName = it },
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
                                        Text("Tap slots to select your classes. Ideal for 1-hour lectures.", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp))
                                        
                                        Row(modifier = Modifier.fillMaxWidth()) {
                                            // Left Sticky Column
                                            Column(modifier = Modifier.width(80.dp)) {
                                                // Empty space for the header row
                                                Box(modifier = Modifier.height(36.dp)) 
                                                // Time labels
                                                gridHours.forEach { hour ->
                                                    Box(
                                                        modifier = Modifier.height(52.dp),
                                                        contentAlignment = Alignment.CenterStart
                                                    ) {
                                                        Text(hour.split(" - ")[0], style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                                                    }
                                                }
                                            }
                                            
                                            // Scrollable Days Column
                                            Column(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .overlayFadeEdges(colorScheme.surface, right = true)
                                                    .horizontalScroll(rememberScrollState())
                                            ) {
                                                // Header
                                                Row(modifier = Modifier.height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    gridDays.forEach { day ->
                                                        Box(
                                                            modifier = Modifier
                                                                .width(52.dp)
                                                                .padding(horizontal = 4.dp)
                                                                .fillMaxHeight()
                                                                .background(colorScheme.background, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(day, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = accentColor)
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.width(32.dp)) // Extra padding for the fade edge
                                                }
                                                
                                                // Grid Cells
                                                gridHours.forEach { hour ->
                                                    Row(modifier = Modifier.height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                                                        gridDays.forEachIndexed { colIndex, day ->
                                                            val isSelected = selectedGridCells.contains(Pair(day, hour))
                                                            val cellScale by animateFloatAsState(if (isSelected) 1f else 0.95f, spring(dampingRatio = 0.5f), label = "cellScale")
                                                            val cellColor by animateColorAsState(if (isSelected) accentColor else Color.Transparent, label = "cellColor")
                                                            
                                                            val colBg = if (colIndex % 2 == 1) colorScheme.background.copy(alpha = 0.5f) else Color.Transparent
                                                            
                                                            Box(
                                                                modifier = Modifier
                                                                    .padding(horizontal = 4.dp)
                                                                    .size(44.dp)
                                                                    .clip(RoundedCornerShape(12.dp))
                                                                    .background(colBg)
                                                                    .bounceClick(haptic) {
                                                                        val newSet = selectedGridCells.toMutableSet()
                                                                        val pair = Pair(day, hour)
                                                                        if(isSelected) newSet.remove(pair) else newSet.add(pair)
                                                                        selectedGridCells = newSet
                                                                    },
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                // Selection fill
                                                                Box(
                                                                    modifier = Modifier
                                                                        .fillMaxSize()
                                                                        .graphicsLayer {
                                                                            scaleX = cellScale
                                                                            scaleY = cellScale
                                                                            alpha = if (isSelected) 1f else 0f
                                                                        }
                                                                        .background(cellColor, RoundedCornerShape(12.dp)),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Icon(Icons.Default.Check, "Selected", tint = Color.White, modifier = Modifier.size(20.dp))
                                                                }
                                                            }
                                                        }
                                                        Spacer(modifier = Modifier.width(32.dp))
                                                    }
                                                }
                                            }
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
                                                            onClick = {},
                                                            modifier = Modifier.bounceClick(haptic) {
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
                                            onClick = {},
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(56.dp)
                                                .bounceClick(haptic) {
                                                    val newBlocks = scheduleBlocks.toMutableList()
                                                    newBlocks.add(ScheduleBlockState())
                                                    scheduleBlocks = newBlocks
                                                },
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

                    // Students Section
                    StaggeredEntrance(index = 3) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(8.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha = 0.05f)),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Students", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    TextButton(
                                        onClick = {},
                                        modifier = Modifier.bounceClick(haptic) { isBulkAddMode = !isBulkAddMode }
                                    ) {
                                        Text(if (isBulkAddMode) "Manual Add" else "Bulk Add", fontWeight = FontWeight.Bold)
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
                                            onClick = {},
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(50.dp)
                                                .bounceClick(haptic) {
                                                    val lines = bulkStudentsText.split("\\n").filter { it.isNotBlank() }
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
                                                    onClick = {},
                                                    modifier = Modifier.bounceClick(haptic) {
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
                    
                    StaggeredEntrance(index = 4) {
                        Button(
                            onClick = {},
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .shadow(8.dp, RoundedCornerShape(20.dp), spotColor = accentColor.copy(alpha = 0.5f))
                                .bounceClick(haptic) {
                                    if (courseName.isBlank()) {
                                        Toast.makeText(context, "Course name is required.", Toast.LENGTH_SHORT).show()
                                        return@bounceClick
                                    }
                                    if (scheduleBlocks.any { it.selectedDays.isEmpty() }) {
                                        Toast.makeText(context, "All time slots must have at least one day selected.", Toast.LENGTH_SHORT).show()
                                        return@bounceClick
                                    }
                                    
                                    val finalSchedules = scheduleBlocks.filter { it.timeRange.isNotBlank() }.flatMap { block ->
                                        block.selectedDays.map { day ->
                                            ScheduleImport(day = day, time = block.timeRange, location = block.location.takeIf { it.isNotBlank() })
                                        }
                                    } + selectedGridCells.map { cell ->
                                        ScheduleImport(day = cell.first, time = cell.second, location = defaultLocation.takeIf { it.isNotBlank() })
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
    }
}
"""

with open("app/src/main/java/com/example/ui/screens/AddEditBatchScreen.kt", "w") as f:
    f.write(content)
