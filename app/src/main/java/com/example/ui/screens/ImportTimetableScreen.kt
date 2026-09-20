package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.data.BatchImport
import com.example.data.CourseImport
import com.example.data.ImportTimetableData
import com.example.data.ScheduleImport
import com.example.viewmodel.AiHelper
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportTimetableScreen(navController: NavController, viewModel: MainViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val userRole by viewModel.userRole.collectAsState()
    val isStudent = userRole.equals("student", ignoreCase = true)

    var rawText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var aiStatusText by remember { mutableStateOf("") }
    var reviewData by remember { mutableStateOf<ImportTimetableData?>(null) }

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileMimeType by remember { mutableStateOf<String?>(null) }
    var selectedFileName by remember { mutableStateOf("") }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            try {
                selectedBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source)
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedFileUri = uri
            selectedImageUri = null
            selectedBitmap = null
            selectedFileMimeType = context.contentResolver.getType(uri)
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val displayNameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (displayNameIndex != -1) {
                        selectedFileName = it.getString(displayNameIndex) ?: "document"
                    }
                }
            }
        }
    }

    if (reviewData != null) {
        ReviewImportData(
            data = reviewData!!,
            isStudent = isStudent,
            onConfirm = { finalData ->
                isLoading = true
                viewModel.saveReviewedTimetable(
                    data = finalData,
                    onSuccess = {
                        isLoading = false
                        Toast.makeText(context, "Timetable saved and synced successfully!", Toast.LENGTH_LONG).show()
                        navController.popBackStack()
                    },
                    onError = { err ->
                        isLoading = false
                        Toast.makeText(context, "Save failed: $err", Toast.LENGTH_LONG).show()
                    }
                )
            },
            onCancel = { reviewData = null }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isStudent) "AI Timetable Import (Student)" else "AI Timetable Import (Teacher)",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                // Informational Banner
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isStudent) "Smart Schedule Scanner" else "Smart Timetable Parser",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isStudent)
                                    "Paste your class routine or take a photo of your schedule. Gemini will organize all subjects and timings for you!"
                                else
                                    "Paste timetable notes or upload schedule photos/documents. Gemini will extract batches, timings, and rosters automatically!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                // Input section: Text input
                Text(
                    text = "1. Enter Schedule Notes / Message",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    label = { Text("e.g. Maths Monday 9am Room 101, Physics Wed 11am Lab 2...") },
                    placeholder = {
                        Text(
                            if (isStudent)
                                "Paste your routine text or informal message here..."
                            else
                                "Enter batch names, days, timings, classrooms, or student roster..."
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                // Input section: Photo / Document attachment
                Text(
                    text = "2. Or Attach Photo / Document",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedCard(
                        onClick = {
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Attach Photo", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("Timetable / Screenshot", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    OutlinedCard(
                        onClick = {
                            documentPickerLauncher.launch(
                                arrayOf("application/pdf", "text/plain", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Attach Document", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("PDF / DOC / Text", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Selected Image Preview
                if (selectedBitmap != null) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                            Image(
                                bitmap = selectedBitmap!!.asImageBitmap(),
                                contentDescription = "Attached Schedule Photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            IconButton(
                                onClick = {
                                    selectedBitmap = null
                                    selectedImageUri = null
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove photo", tint = Color.White)
                            }
                        }
                    }
                }

                // Selected Document Preview
                if (selectedFileUri != null && selectedFileName.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = selectedFileName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                            IconButton(
                                onClick = {
                                    selectedFileUri = null
                                    selectedFileName = ""
                                    selectedFileMimeType = null
                                }
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove document")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Extract Button
                Button(
                    onClick = {
                        if (rawText.isBlank() && selectedBitmap == null && selectedFileUri == null) {
                            Toast.makeText(context, "Please enter some schedule notes or attach an image/doc.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        isLoading = true
                        aiStatusText = "Analyzing input with Gemini AI..."
                        coroutineScope.launch {
                            var fileBytes: ByteArray? = null
                            var fileMime: String? = null
                            if (selectedFileUri != null) {
                                try {
                                    context.contentResolver.openInputStream(selectedFileUri!!)?.use { inputStream ->
                                        fileBytes = inputStream.readBytes()
                                        fileMime = selectedFileMimeType ?: "application/pdf"
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("ImportTimetable", "Error reading document file", e)
                                }
                            }

                            var errorMessage = "AI extraction could not be completed."
                            val aiResult = try {
                                AiHelper.parseTimetableData(
                                    rawText = rawText,
                                    image = selectedBitmap,
                                    apiKey = com.example.BuildConfig.GEMINI_API_KEY,
                                    fileBytes = fileBytes,
                                    fileMimeType = fileMime,
                                    userRole = userRole ?: "teacher"
                                )
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "Failed to contact AI service"
                                null
                            }

                            if (aiResult != null) {
                                aiStatusText = "Structuring extracted schedule..."
                                val parsed = viewModel.parseTimetableJson(aiResult)
                                if (parsed != null) {
                                    reviewData = parsed
                                } else {
                                    Toast.makeText(context, "AI extracted raw text but JSON parsing failed.", Toast.LENGTH_LONG).show()
                                }
                                isLoading = false
                            } else {
                                isLoading = false
                                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(aiStatusText, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Extract Schedule with AI", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewImportData(
    data: ImportTimetableData,
    isStudent: Boolean,
    onConfirm: (ImportTimetableData) -> Unit,
    onCancel: () -> Unit
) {
    var editableBatches by remember { mutableStateOf(data.batches ?: emptyList()) }
    val missingFields = remember { data.missingFields ?: emptyList() }
    val summary = remember { data.summary ?: "" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Extracted Schedule") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. AI Summary Card
            if (summary.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("AI Analysis Summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            // 2. Missing Fields Notification Card
            if (missingFields.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = Color(0xFFD97706))
                            Text(
                                "Missing / Incomplete Fields Detected (${missingFields.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB45309)
                            )
                        }
                        Text(
                            "The AI fitted all available data, but could not detect these fields in your input. You can fill them in below before saving:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        missingFields.forEach { field ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Color(0xFFD97706)))
                                Text(field, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            // 3. Empty state if no classes were extracted
            if (editableBatches.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(36.dp))
                        Text("No Classes Recognized", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        Text(
                            "The input did not contain enough recognizable timetable details. You can tap 'Add Class' below to add them manually, or go back to provide clearer input.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // 4. Batches / Subjects list
            editableBatches.forEachIndexed { index, batch ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isStudent) "Subject #${index + 1}" else "Batch #${index + 1}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = {
                                    val updated = editableBatches.toMutableList()
                                    updated.removeAt(index)
                                    editableBatches = updated
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Batch", tint = MaterialTheme.colorScheme.error)
                            }
                        }

                        OutlinedTextField(
                            value = batch.course?.name ?: "",
                            onValueChange = { newValue ->
                                val updatedBatches = editableBatches.toMutableList()
                                updatedBatches[index] = batch.copy(
                                    course = batch.course?.copy(name = newValue) ?: CourseImport(name = newValue)
                                )
                                editableBatches = updatedBatches
                            },
                            label = { Text("Course / Subject Name *") },
                            modifier = Modifier.fillMaxWidth(),
                            isError = batch.course?.name.isNullOrBlank()
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = batch.section ?: "",
                                onValueChange = { newValue ->
                                    val updatedBatches = editableBatches.toMutableList()
                                    updatedBatches[index] = batch.copy(section = newValue)
                                    editableBatches = updatedBatches
                                },
                                label = { Text("Section / Class") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = batch.location ?: "",
                                onValueChange = { newValue ->
                                    val updatedBatches = editableBatches.toMutableList()
                                    updatedBatches[index] = batch.copy(location = newValue)
                                    editableBatches = updatedBatches
                                },
                                label = { Text("Room / Location") },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Schedule Slots
                        val schedules = batch.weeklySchedule ?: emptyList()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Weekly Timings (${schedules.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            TextButton(
                                onClick = {
                                    val updatedBatches = editableBatches.toMutableList()
                                    val updatedSchedules = schedules.toMutableList()
                                    updatedSchedules.add(ScheduleImport(day = "Monday", time = "09:00 - 10:00 AM", location = batch.location))
                                    updatedBatches[index] = batch.copy(weeklySchedule = updatedSchedules)
                                    editableBatches = updatedBatches
                                }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Slot")
                            }
                        }

                        schedules.forEachIndexed { sIndex, sched ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = sched.day ?: "",
                                    onValueChange = { newDay ->
                                        val updatedBatches = editableBatches.toMutableList()
                                        val updatedSchedules = schedules.toMutableList()
                                        updatedSchedules[sIndex] = sched.copy(day = newDay)
                                        updatedBatches[index] = batch.copy(weeklySchedule = updatedSchedules)
                                        editableBatches = updatedBatches
                                    },
                                    label = { Text("Day") },
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = sched.time ?: "",
                                    onValueChange = { newTime ->
                                        val updatedBatches = editableBatches.toMutableList()
                                        val updatedSchedules = schedules.toMutableList()
                                        updatedSchedules[sIndex] = sched.copy(time = newTime)
                                        updatedBatches[index] = batch.copy(weeklySchedule = updatedSchedules)
                                        editableBatches = updatedBatches
                                    },
                                    label = { Text("Time") },
                                    modifier = Modifier.weight(1.3f)
                                )
                                IconButton(
                                    onClick = {
                                        val updatedBatches = editableBatches.toMutableList()
                                        val updatedSchedules = schedules.toMutableList()
                                        updatedSchedules.removeAt(sIndex)
                                        updatedBatches[index] = batch.copy(weeklySchedule = updatedSchedules)
                                        editableBatches = updatedBatches
                                    }
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Delete Slot", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        if (!isStudent) {
                            val students = batch.students ?: emptyList()
                            val withEmail = students.count { !it.email.isNullOrBlank() }
                            val missingEmail = students.size - withEmail
                            var showRoster by remember { mutableStateOf(false) }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                "Enrolled Students: ${students.size}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            if (students.isNotEmpty()) {
                                                Text(
                                                    text = if (missingEmail == 0) "✓ All $withEmail students linked for live sync"
                                                           else "⚠ $missingEmail student(s) missing campus email",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (missingEmail == 0) Color(0xFF10B981) else Color(0xFFF59E0B),
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                        if (students.isNotEmpty()) {
                                            TextButton(
                                                onClick = { showRoster = !showRoster },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(if (showRoster) "Hide Roster" else "View / Edit Emails", fontSize = 12.sp)
                                            }
                                        }
                                    }

                                    if (showRoster && students.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        students.forEachIndexed { sIdx, st ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    st.name ?: "Student",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    modifier = Modifier.weight(0.4f),
                                                    maxLines = 1
                                                )
                                                OutlinedTextField(
                                                    value = st.email ?: "",
                                                    onValueChange = { newEmail ->
                                                        val updatedBatches = editableBatches.toMutableList()
                                                        val updatedStudents = students.toMutableList()
                                                        updatedStudents[sIdx] = st.copy(email = newEmail)
                                                        updatedBatches[index] = batch.copy(students = updatedStudents)
                                                        editableBatches = updatedBatches
                                                    },
                                                    placeholder = { Text("Campus email", fontSize = 11.sp) },
                                                    singleLine = true,
                                                    modifier = Modifier.weight(0.6f),
                                                    textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
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

            // Add Manual Batch Button
            OutlinedButton(
                onClick = {
                    val updated = editableBatches.toMutableList()
                    updated.add(
                        BatchImport(
                            course = CourseImport(name = "New Subject"),
                            section = "A",
                            location = "Room 101",
                            weeklySchedule = listOf(ScheduleImport(day = "Monday", time = "09:00 - 10:00 AM")),
                            students = emptyList()
                        )
                    )
                    editableBatches = updated
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isStudent) "Add Another Subject" else "Add Another Batch")
            }

            // Confirm & Save
            Button(
                onClick = {
                    val validBatches = editableBatches.filter { !it.course?.name.isNullOrBlank() }
                    if (validBatches.isEmpty()) {
                        return@Button
                    }
                    onConfirm(data.copy(batches = validBatches))
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = editableBatches.any { !it.course?.name.isNullOrBlank() },
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Confirm & Save Schedule", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
