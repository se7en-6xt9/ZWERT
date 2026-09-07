package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.data.AttendanceRecordEntity
import com.example.data.ScheduleSlotEntity
import com.example.data.StudentEntity
import com.example.viewmodel.MainViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceReportScreen(navController: NavController, viewModel: MainViewModel, courseId: String) {
    BackHandler { navController.popBackStack() }

    val verticalScrollState = rememberScrollState()
    var students by remember { mutableStateOf<List<StudentEntity>>(emptyList()) }
    val slots by viewModel.getScheduleSlotsForCourse(courseId).collectAsState(initial = emptyList())
    val attendance by viewModel.getAttendanceForCourse(courseId).collectAsState(initial = emptyList())
    
    LaunchedEffect(courseId) {
        students = viewModel.getStudentsByCourseSync(courseId)
    }

    // Generate Dates
    val generatedDates = remember(slots) {
        if (slots.isEmpty()) return@remember emptyList<Pair<LocalDate, ScheduleSlotEntity>>()
        
        val today = LocalDate.now()
        val list = mutableListOf<Pair<LocalDate, ScheduleSlotEntity>>()
        // Look back 30 days and forward 30 days for sessions
        for (i in -30L..30L) {
            val d = today.plusDays(i)
            val dayName = d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            // A day might have multiple slots
            slots.filter { it.dayOfWeek.equals(dayName, ignoreCase = true) }.forEach { slot ->
                list.add(Pair(d, slot))
            }
        }
        list
    }

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

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color(0xFFFAFAFA))
            ) {
                // Scrollable Grid Area
                val horizontalScrollState = rememberScrollState()
                
                // Set default scroll position to roughly the middle (today) if needed
                LaunchedEffect(generatedDates) {
                    // Try to scroll to center/today
                    val todayIndex = generatedDates.indexOfFirst { it.first == LocalDate.now() }
                    if (todayIndex != -1) {
                        // Very rough estimation, 80dp per column
                        horizontalScrollState.scrollTo(todayIndex * 240) // 80dp * 3 pixel density approx
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(horizontalScrollState)
                        .padding(start = 140.dp, end = 60.dp) // Leave space for sticky columns
                ) {
                    generatedDates.forEach { (date, slot) ->
                        Column(
                            modifier = Modifier
                                .width(64.dp)
                                .verticalScroll(verticalScrollState)
                        ) {
                            // Header Cell
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp)
                                    .background(Color.White)
                                    .padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(date.format(DateTimeFormatter.ofPattern("dd MMM")), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                                Text(date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontSize = 10.sp)
                                if (date == LocalDate.now()) {
                                    Box(modifier = Modifier.height(2.dp).width(20.dp).background(Color(0xFF6750A4), CircleShape).padding(top = 2.dp))
                                }
                            }
                            Divider(color = Color(0xFFE0E0E0))
                            
                            // Cells
                            students.forEach { student ->
                                val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                                val record = attendance.find { it.studentId == student.id && it.date == dateStr && it.scheduleSlotId == slot.id }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .background(if (date == LocalDate.now()) Color(0xFFF3E5F5).copy(alpha = 0.3f) else Color.Transparent)
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (record != null) {
                                        val color = when (record.status) { "P" -> Color(0xFF4CAF50); "A" -> Color(0xFFF44336); "L" -> Color(0xFFFF9800); else -> Color.Gray }
                                        Surface(shape = CircleShape, color = color.copy(alpha = 0.2f), modifier = Modifier.size(32.dp)) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(record.status, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    } else {
                                        Text("-", color = Color.LightGray)
                                    }
                                }
                                Divider(color = Color(0xFFF0F0F0))
                            }
                        }
                    }
                }

                // Sticky Left Column (Names)
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(140.dp)
                        .shadow(4.dp, RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                        .background(Color.White)
                        .verticalScroll(verticalScrollState)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(60.dp).padding(start = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text("Student", fontWeight = FontWeight.Bold, color = Color.DarkGray, style = MaterialTheme.typography.labelMedium)
                    }
                    Divider(color = Color(0xFFE0E0E0))
                    students.forEach { student ->
                        Column(
                            modifier = Modifier.fillMaxWidth().height(52.dp).padding(start = 16.dp, end = 8.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(student.name, fontWeight = FontWeight.SemiBold, color = Color.Black, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(student.rollNumber, color = Color.Gray, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                        Divider(color = Color(0xFFF0F0F0))
                    }
                }

                // Sticky Right Column (Percentages)
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(60.dp)
                        .shadow((-4).dp, RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                        .background(Color.White)
                        .verticalScroll(verticalScrollState)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Att. %", fontWeight = FontWeight.Bold, color = Color.DarkGray, style = MaterialTheme.typography.labelMedium)
                    }
                    Divider(color = Color(0xFFE0E0E0))
                    students.forEach { student ->
                        // Calculate percentage based on ALL sessions shown
                        val studentRecords = attendance.filter { it.studentId == student.id }
                        val presents = studentRecords.count { it.status == "P" || it.status == "L" }
                        val total = studentRecords.size.takeIf { it > 0 } ?: 1
                        val percentage = (presents.toFloat() / total * 100).toInt()
                        
                        Box(
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$percentage%", fontWeight = FontWeight.Bold, color = if (percentage >= 75) Color(0xFF4CAF50) else Color(0xFFF44336), style = MaterialTheme.typography.labelMedium)
                        }
                        Divider(color = Color(0xFFF0F0F0))
                    }
                }
            }
        }
    }
}
