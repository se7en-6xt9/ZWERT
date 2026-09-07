package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
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

import android.annotation.SuppressLint

@SuppressLint("NewApi")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceReportScreen(navController: NavController, viewModel: MainViewModel, courseId: String) {
    BackHandler { navController.popBackStack() }

    var students by remember { mutableStateOf<List<StudentEntity>>(emptyList()) }
    val slots by viewModel.getScheduleSlotsForCourse(courseId).collectAsState(initial = emptyList())
    val attendance by viewModel.getAttendanceForCourse(courseId).collectAsState(initial = emptyList())
    
    var searchQuery by remember { mutableStateOf("") }
    
    LaunchedEffect(courseId) {
        students = viewModel.getStudentsByCourseSync(courseId)
    }

    val filteredStudents = remember(students, searchQuery) {
        if (searchQuery.isBlank()) students
        else students.filter { 
            it.name.contains(searchQuery, ignoreCase = true) || 
            it.rollNumber.contains(searchQuery, ignoreCase = true) 
        }
    }

    // Generate Dates
    val generatedDates = remember(slots) {
        if (slots.isEmpty()) return@remember emptyList<Pair<LocalDate, ScheduleSlotEntity>>()
        val today = LocalDate.now()
        val list = mutableListOf<Pair<LocalDate, ScheduleSlotEntity>>()
        for (i in -45L..15L) { // Look back 45 days, forward 15 days
            val d = today.plusDays(i)
            val dayName = d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            slots.filter { it.dayOfWeek.equals(dayName, ignoreCase = true) }.forEach { slot ->
                list.add(Pair(d, slot))
            }
        }
        list
    }

    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Excel Dimensions
    val cellWidth = 72.dp
    val cellWidthPx = with(density) { cellWidth.toPx() }
    val leftColWidth = 180.dp
    val headerHeight = 64.dp
    val rowHeight = 56.dp
    val borderColor = Color(0xFFE0E0E0)
    
    // Jump to Today logic
    LaunchedEffect(generatedDates) {
        if (generatedDates.isNotEmpty()) {
            val todayIndex = generatedDates.indexOfFirst { it.first == LocalDate.now() }
            if (todayIndex >= 0) {
                hScroll.scrollTo((todayIndex * cellWidthPx).toInt())
            }
        }
    }

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
                        val idx = generatedDates.indexOfFirst { it.first == selectedDate }
                        if (idx >= 0) {
                            coroutineScope.launch { hScroll.animateScrollTo((idx * cellWidthPx).toInt()) }
                        }
                    }
                }) { Text("Jump") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Edit Cell State: stores (studentId, dateString, slotId)
    var selectedCell by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF6750A4),
            background = Color.White,
            surface = Color.White
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Attendance Report", fontWeight = FontWeight.Bold, color = Color.Black) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.Black)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            },
            containerColor = Color.White
        ) { paddingValues ->
            if (students.isEmpty() || generatedDates.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF6750A4))
                }
                return@Scaffold
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color(0xFFFAFAFA))
            ) {
                // Toolbar Area
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search student...", style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(20.dp)) },
                        modifier = Modifier.weight(1f).height(50.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = Color.White,
                            unfocusedBorderColor = Color(0xFFE0E0E0),
                            focusedContainerColor = Color.White
                        ),
                        shape = RoundedCornerShape(25.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    FilledTonalIconButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.size(50.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = "Jump to Date", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }

                Divider(color = borderColor, thickness = 1.dp)

                // Excel-style Grid Area
                Box(modifier = Modifier.fillMaxSize()) {
                    
                    // 1. Bottom-Right (Main Scrollable Grid)
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
                                Row(modifier = Modifier.height(rowHeight)) {
                                    generatedDates.forEach { (date, slot) ->
                                        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                                        val record = attendance.find { it.studentId == student.id && it.date == dateStr && it.scheduleSlotId == slot.id }
                                        val status = record?.status
                                        
                                        val isSelected = selectedCell?.first == student.id && selectedCell?.second == dateStr && selectedCell?.third == slot.id
                                        
                                        Box(
                                            modifier = Modifier
                                                .size(width = cellWidth, height = rowHeight)
                                                .background(if (isZebra) Color(0xFFF9FAFB) else Color.White)
                                                .border(
                                                    width = if (isSelected) 2.dp else 0.5.dp, 
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else borderColor
                                                )
                                                .clickable { selectedCell = Triple(student.id, dateStr, slot.id) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            // Status Badge
                                            if (status != null) {
                                                val badgeColor = when (status) { "P" -> Color(0xFF4CAF50); "A" -> Color(0xFFF44336); "L" -> Color(0xFFFF9800); else -> Color.Gray }
                                                Surface(shape = CircleShape, color = badgeColor.copy(alpha = 0.15f), modifier = Modifier.size(28.dp)) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(status, color = badgeColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                                    }
                                                }
                                            }
                                            
                                            // Popover for Quick Edit
                                            DropdownMenu(
                                                expanded = isSelected,
                                                onDismissRequest = { selectedCell = null },
                                                modifier = Modifier.background(Color.White)
                                            ) {
                                                Text(
                                                    "Session: ${slot.startTime} - ${slot.endTime}", 
                                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = Color.Gray
                                                )
                                                Divider()
                                                Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Button(onClick = { viewModel.markAttendance(dateStr, slot.id, student.id, "P"); selectedCell = null }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) { Text("P") }
                                                    Button(onClick = { viewModel.markAttendance(dateStr, slot.id, student.id, "A"); selectedCell = null }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))) { Text("A") }
                                                    Button(onClick = { viewModel.markAttendance(dateStr, slot.id, student.id, "L"); selectedCell = null }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))) { Text("L") }
                                                    TextButton(onClick = { viewModel.markAttendance(dateStr, slot.id, student.id, "NONE"); selectedCell = null }) { Text("Clear") }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Top-Right (Sticky Headers - Horizontal Scroll Sync)
                    Box(
                        modifier = Modifier
                            .padding(start = leftColWidth)
                            .fillMaxWidth()
                            .height(headerHeight)
                            .clipToBounds()
                            .background(Color.White)
                    ) {
                        Row(modifier = Modifier.offset { IntOffset(-hScroll.value, 0) }) {
                            generatedDates.forEach { (date, _) ->
                                val isToday = date == LocalDate.now()
                                Column(
                                    modifier = Modifier
                                        .size(width = cellWidth, height = headerHeight)
                                        .background(if (isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.White)
                                        .border(0.5.dp, borderColor),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    if (isToday) {
                                        Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.primary))
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                    Text(date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase(), style = MaterialTheme.typography.labelSmall, color = if (isToday) MaterialTheme.colorScheme.primary else Color.Gray, fontSize = 10.sp)
                                    Text(date.format(DateTimeFormatter.ofPattern("dd MMM")), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (isToday) MaterialTheme.colorScheme.primary else Color.DarkGray)
                                    if (isToday) Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    // 3. Bottom-Left (Sticky First Column - Vertical Scroll Sync)
                    Box(
                        modifier = Modifier
                            .padding(top = headerHeight)
                            .width(leftColWidth)
                            .fillMaxHeight()
                            .clipToBounds()
                            .background(Color.White)
                            .shadow(2.dp, spotColor = Color.Transparent) // Adds slight depth
                    ) {
                        Column(modifier = Modifier.offset { IntOffset(0, -vScroll.value) }) {
                            filteredStudents.forEachIndexed { rowIndex, student ->
                                val isZebra = rowIndex % 2 != 0
                                
                                // Calculate individual %
                                val studentRecords = attendance.filter { it.studentId == student.id }
                                val presents = studentRecords.count { it.status == "P" || it.status == "L" }
                                val total = studentRecords.size.takeIf { it > 0 } ?: 1
                                val percentage = (presents.toFloat() / total * 100).toInt()
                                val pctColor = when {
                                    percentage >= 75 -> Color(0xFF2E7D32) // Dark Green
                                    percentage >= 50 -> Color(0xFFF57C00) // Amber/Orange
                                    else -> Color(0xFFD32F2F) // Red
                                }
                                
                                Row(
                                    modifier = Modifier
                                        .size(width = leftColWidth, height = rowHeight)
                                        .background(if (isZebra) Color(0xFFF9FAFB) else Color.White)
                                        .border(0.5.dp, borderColor)
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(student.name, fontWeight = FontWeight.SemiBold, color = Color.Black, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(student.rollNumber, color = Color.Gray, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                    }
                                    Surface(
                                        color = pctColor.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            "$percentage%", 
                                            fontWeight = FontWeight.Bold, 
                                            color = pctColor, 
                                            style = MaterialTheme.typography.labelMedium,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. Top-Left Corner (Completely Frozen)
                    Box(
                        modifier = Modifier
                            .size(width = leftColWidth, height = headerHeight)
                            .background(Color.White)
                            .border(0.5.dp, borderColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Student", fontWeight = FontWeight.Bold, color = Color.DarkGray, style = MaterialTheme.typography.labelMedium)
                            Text("Att %", fontWeight = FontWeight.Bold, color = Color.DarkGray, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}
