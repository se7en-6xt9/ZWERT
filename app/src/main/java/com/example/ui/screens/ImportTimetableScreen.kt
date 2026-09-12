package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportTimetableScreen(navController: NavController, viewModel: MainViewModel) {
    var rawText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var aiStatusText by remember { mutableStateOf("") }
    var reviewData by remember { mutableStateOf<com.example.data.ImportTimetableData?>(null) }
    
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileMimeType by remember { mutableStateOf<String?>(null) }
    var selectedFileName by remember { mutableStateOf<String>("") }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

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
                Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
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
                        selectedFileName = it.getString(displayNameIndex)
                    }
                }
            }
        }
    }

    if (reviewData != null) {
        ReviewImportData(
            data = reviewData!!,
            onConfirm = { finalData ->
                isLoading = true
                viewModel.saveReviewedTimetable(
                    data = finalData,
                    onSuccess = {
                        isLoading = false
                        android.widget.Toast.makeText(context, "Timetable saved successfully!", android.widget.Toast.LENGTH_LONG).show()
                        navController.popBackStack()
                    },
                    onError = { err ->
                        isLoading = false
                        android.widget.Toast.makeText(context, "Save failed: $err", android.widget.Toast.LENGTH_LONG).show()
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
                title = { Text("Import Timetable", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            Text("Provide your schedule as text, a photo, or a document. The AI will extract the details automatically.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            OutlinedTextField(
                value = rawText,
                onValueChange = { rawText = it },
                label = { Text("Paste Schedule Text Here (Optional if photo/doc provided)") },
                modifier = Modifier.fillMaxWidth().height(150.dp),
                maxLines = 5
            )

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        val apiKey = com.example.BuildConfig.GEMINI_API_KEY
                        if (apiKey.isBlank()) {
                            Toast.makeText(context, "API Key missing! Add it in the Secrets panel.", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        isLoading = true
                        aiStatusText = "AI is thinking..."
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
                                    android.util.Log.e("ImportTimetable", "Error reading file", e)
                                }
                            }
                            
                            var errorMessage = "AI failed to extract the data"
                            val aiResult = try {
                                com.example.viewmodel.AiHelper.parseTimetableData(
                                    rawText = rawText,
                                    image = selectedBitmap,
                                    apiKey = apiKey,
                                    fileBytes = fileBytes,
                                    fileMimeType = fileMime
                                )
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "Network or API error occurred."
                                null
                            }
                            
                            if (aiResult != null) {
                                aiStatusText = "Parsing schema..."
                                val parsed = viewModel.parseTimetableJson(aiResult)
                                if (parsed != null) {
                                    reviewData = parsed
                                } else {
                                    Toast.makeText(context, "AI output could not be parsed into schema.", Toast.LENGTH_LONG).show()
                                }
                                isLoading = false
                            } else {
                                isLoading = false
                                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Use AI", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewImportData(
    data: com.example.data.ImportTimetableData,
    onConfirm: (com.example.data.ImportTimetableData) -> Unit,
    onCancel: () -> Unit
) {
    var editableBatches by remember { mutableStateOf(data.batches ?: emptyList()) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Extracted Data") },
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
            Text("Please review the extracted data. Fill in any missing required fields.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            if (editableBatches.isEmpty()) {
                Text("No batches were found. The AI might not have recognized any classes.", color = MaterialTheme.colorScheme.error)
            }
            
            editableBatches.forEachIndexed { index, batch ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Batch ${index + 1}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        
                        OutlinedTextField(
                            value = batch.course?.name ?: "",
                            onValueChange = { newValue ->
                                val updatedBatches = editableBatches.toMutableList()
                                updatedBatches[index] = batch.copy(course = batch.course?.copy(name = newValue) ?: com.example.data.CourseImport(null, newValue))
                                editableBatches = updatedBatches
                            },
                            label = { Text("Course Name") },
                            modifier = Modifier.fillMaxWidth(),
                            isError = batch.course?.name.isNullOrBlank()
                        )
                        
                        OutlinedTextField(
                            value = batch.section ?: "",
                            onValueChange = { newValue ->
                                val updatedBatches = editableBatches.toMutableList()
                                updatedBatches[index] = batch.copy(section = newValue)
                                editableBatches = updatedBatches
                            },
                            label = { Text("Section / Class") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        OutlinedTextField(
                            value = batch.location ?: "",
                            onValueChange = { newValue ->
                                val updatedBatches = editableBatches.toMutableList()
                                updatedBatches[index] = batch.copy(location = newValue)
                                editableBatches = updatedBatches
                            },
                            label = { Text("Default Room/Location") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        val schedules = batch.weeklySchedule ?: emptyList()
                        Text("Schedules: ${schedules.size}", style = MaterialTheme.typography.bodySmall)
                        schedules.forEachIndexed { sIndex, sched ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        
                        val students = batch.students ?: emptyList()
                        Text("Students: ${students.size}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            
            Button(
                onClick = {
                    onConfirm(data.copy(batches = editableBatches))
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = editableBatches.isNotEmpty()
            ) {
                Text("Confirm & Save", fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
