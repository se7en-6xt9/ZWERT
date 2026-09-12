with open("app/src/main/java/com/example/ui/screens/AddEditBatchScreen.kt", "r") as f:
    content = f.read()

# Let's completely recreate the file. I have the entire original file in memory.
original_file = """package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.data.BatchImport
import com.example.data.CourseImport
import com.example.data.ScheduleImport
import com.example.data.StudentImport
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.util.UUID

data class ScheduleBlockState(
    val id: String = UUID.randomUUID().toString(),
    var timeRange: String = "",
    var selectedDays: Set<String> = emptySet(),
    var location: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditBatchScreen(
    navController: NavController,
    viewModel: MainViewModel,
    batchId: String? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var isLoading by remember { mutableStateOf(false) }
    
    // Form state
    var courseName by remember { mutableStateOf("") }
    var courseCode by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var semester by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("") }
    var defaultLocation by remember { mutableStateOf("") }
    
    var scheduleBlocks by remember { mutableStateOf(listOf<ScheduleBlockState>()) }
    var students by remember { mutableStateOf(listOf<StudentImport>()) }
    
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
                
                // Group existing schedules by time and location to create blocks
                val blocks = mutableListOf<ScheduleBlockState>()
                val grouped = (batch.weeklySchedule ?: emptyList()).groupBy { "${it.time}|${it.location}" }
                for ((_, scheds) in grouped) {
                    if (scheds.isEmpty()) continue
                    val first = scheds.first()
                    blocks.add(
                        ScheduleBlockState(
                            timeRange = first.time ?: "",
                            location = first.location ?: "",
                            selectedDays = scheds.mapNotNull { it.day }.toSet()
                        )
                    )
                }
                scheduleBlocks = blocks
                
                students = batch.students ?: emptyList()
            }
            isLoading = false
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (batchId == null) "Add New Class" else "Edit Class") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
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
                // Summary Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Summary", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("${scheduleBlocks.size} class times · ${students.size} students", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }

                // Batch Details
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Class Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        
                        OutlinedTextField(
                            value = courseName,
                            onValueChange = { courseName = it },
                            label = { Text("Course Name *") },
                            modifier = Modifier.fillMaxWidth(),
                            isError = courseName.isBlank()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            OutlinedTextField(
                                value = courseCode,
                                onValueChange = { courseCode = it },
                                label = { Text("Course Code (Optional)") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = section,
                                onValueChange = { section = it },
                                label = { Text("Section (Optional)") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            OutlinedTextField(
                                value = year,
                                onValueChange = { year = it },
                                label = { Text("Year (Optional)") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = semester,
                                onValueChange = { semester = it },
                                label = { Text("Semester (Optional)") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = defaultLocation,
                            onValueChange = { defaultLocation = it },
                            label = { Text("Default Room (Optional)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Weekly Schedule Builder
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Weekly Schedule", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        
                        scheduleBlocks.forEachIndexed { index, block ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Time Slot ${index + 1}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                        IconButton(onClick = {
                                            val newBlocks = scheduleBlocks.toMutableList()
                                            newBlocks.removeAt(index)
                                            scheduleBlocks = newBlocks
                                        }) {
                                            Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                    
                                    OutlinedTextField(
                                        value = block.timeRange,
                                        onValueChange = { newTime ->
                                            val newBlocks = scheduleBlocks.toMutableList()
                                            newBlocks[index] = block.copy(timeRange = newTime)
                                            scheduleBlocks = newBlocks
                                        },
                                        label = { Text("Time (e.g., 10:00 AM - 11:30 AM)") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    
                                    Text("Select Days", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    
                                    @OptIn(ExperimentalLayoutApi::class)
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
                                                label = { Text(dayOpt) }
                                            )
                                        }
                                    }
                                    
                                    OutlinedTextField(
                                        value = block.location,
                                        onValueChange = { newLoc ->
                                            val newBlocks = scheduleBlocks.toMutableList()
                                            newBlocks[index] = block.copy(location = newLoc)
                                            scheduleBlocks = newBlocks
                                        },
                                        label = { Text("Location Override (Optional)") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                        
                        OutlinedButton(
                            onClick = {
                                val newBlocks = scheduleBlocks.toMutableList()
                                newBlocks.add(ScheduleBlockState())
                                scheduleBlocks = newBlocks
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, "Add")
                            Spacer(Modifier.width(8.dp))
                            Text("Add Time Slot")
                        }
                    }
                }

                // Students Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Students", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            TextButton(onClick = { isBulkAddMode = !isBulkAddMode }) {
                                Text(if (isBulkAddMode) "Manual Add" else "Bulk Add")
                            }
                        }
                        
                        AnimatedVisibility(visible = isBulkAddMode) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = bulkStudentsText,
                                    onValueChange = { bulkStudentsText = it },
                                    label = { Text("Paste students (Name, RollNo)") },
                                    modifier = Modifier.fillMaxWidth().height(150.dp),
                                    placeholder = { Text("John Doe, 101\\nJane Smith, 102") }
                                )
                                Button(
                                    onClick = {
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
                                    modifier = Modifier.fillMaxWidth()
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
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = student.name ?: "",
                                            onValueChange = {
                                                val newStudents = students.toMutableList()
                                                newStudents[index] = student.copy(name = it)
                                                students = newStudents
                                            },
                                            label = { Text("Name") },
                                            modifier = Modifier.weight(1.5f)
                                        )
                                        OutlinedTextField(
                                            value = student.rollNumber ?: "",
                                            onValueChange = {
                                                val newStudents = students.toMutableList()
                                                newStudents[index] = student.copy(rollNumber = it)
                                                students = newStudents
                                            },
                                            label = { Text("Roll No") },
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = {
                                            val newStudents = students.toMutableList()
                                            newStudents.removeAt(index)
                                            students = newStudents
                                        }) {
                                            Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                                
                                OutlinedButton(
                                    onClick = {
                                        val newStudents = students.toMutableList()
                                        newStudents.add(StudentImport(UUID.randomUUID().toString(), "", ""))
                                        students = newStudents
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Add, "Add")
                                    Spacer(Modifier.width(8.dp))
                                    Text("Add Student")
                                }
                            }
                        }
                    }
                }
                
                Button(
                    onClick = {
                        if (courseName.isBlank()) {
                            Toast.makeText(context, "Course name is required.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (scheduleBlocks.any { it.selectedDays.isEmpty() }) {
                            Toast.makeText(context, "All time slots must have at least one day selected.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        val finalSchedules = scheduleBlocks.flatMap { block ->
                            block.selectedDays.map { day ->
                                ScheduleImport(day = day, time = block.timeRange, location = block.location.takeIf { it.isNotBlank() })
                            }
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
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Save Class", fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
"""

with open("app/src/main/java/com/example/ui/screens/AddEditBatchScreen.kt", "w") as f:
    f.write(original_file)
