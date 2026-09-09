package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val text = inputStream?.bufferedReader().use { it?.readText() }
                if (text != null) {
                    rawText = text
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to read file", Toast.LENGTH_SHORT).show()
            }
        }
    }

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

    
    if (reviewData != null) {
        ReviewImportData(
            data = reviewData!!,
            onConfirm = { updatedData ->
                isLoading = true
                viewModel.saveReviewedTimetable(updatedData, 
                    onSuccess = {
                        isLoading = false
                        Toast.makeText(context, "Saved Successfully!", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    },
                    onError = { error ->
                        isLoading = false
                        Toast.makeText(context, "Failed to save: $error", Toast.LENGTH_LONG).show()
                    }
                )
            },
            onCancel = { reviewData = null }
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text("Import Data", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("AI-Powered Data Entry", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Upload a photo, paste JSON, or add raw text. The AI will extract the timetable for you.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            
            
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { filePickerLauncher.launch("*/*") },
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Text File", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { imagePickerLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Photo", fontWeight = FontWeight.Bold)
                }
            }

            if (selectedBitmap != null) {
                Box(modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(16.dp)).background(Color.LightGray)) {
                    Image(
                        bitmap = selectedBitmap!!.asImageBitmap(),
                        contentDescription = "Selected Timetable Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                    IconButton(
                        onClick = { 
                            selectedBitmap = null 
                            selectedImageUri = null
                        },
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Remove Image", tint = Color.White)
                    }
                }
            }
            
            OutlinedTextField(
                value = rawText,
                onValueChange = { rawText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                placeholder = { Text("Paste JSON or raw text here...") },
                shape = RoundedCornerShape(16.dp)
            )
            
            if (isLoading) {
                androidx.compose.ui.window.Dialog(onDismissRequest = { }) {
                    androidx.compose.material3.Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 4.dp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = aiStatusText.ifBlank { "Processing..." },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        enabled = !isLoading,
                        onClick = {
                            if (rawText.isBlank()) {
                                Toast.makeText(context, "Please paste some data", Toast.LENGTH_SHORT).show()
                                return@OutlinedButton
                            }
                            isLoading = true
                                                        val parsed = viewModel.parseTimetableJson(rawText)
                            if (parsed != null) {
                                reviewData = parsed
                            } else {
                                Toast.makeText(context, "Failed to parse JSON", Toast.LENGTH_LONG).show()
                            }
                            isLoading = false
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Strict JSON", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {

                            if (rawText.isBlank() && selectedBitmap == null) {
                                Toast.makeText(context, "Add text or an image", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            
                            val apiKey = com.example.BuildConfig.GEMINI_API_KEY
                            if (apiKey.isBlank()) {
                                Toast.makeText(context, "API Key missing! Add it in the Secrets panel.", Toast.LENGTH_LONG).show()
                                return@Button
                            }

                            isLoading = true
                            aiStatusText = "AI is thinking..."
                            coroutineScope.launch {
                                val aiResult = com.example.viewmodel.AiHelper.parseTimetableData(rawText, selectedBitmap, apiKey)

                                if (aiResult != null) {
                                    aiStatusText = "Saving data..."
                                    // Aggressively clean JSON by finding the first { and last }
                                    var cleanJson = aiResult.replace("```json", "").replace("```", "").trim()
                                    val startIndex = cleanJson.indexOf('{')
                                    val endIndex = cleanJson.lastIndexOf('}')
                                    if (startIndex != -1 && endIndex != -1 && endIndex >= startIndex) {
                                        cleanJson = cleanJson.substring(startIndex, endIndex + 1)
                                    }
                                    
                                                                        val parsed = viewModel.parseTimetableJson(cleanJson)
                                    if (parsed != null) {
                                        reviewData = parsed
                                    } else {
                                        Toast.makeText(context, "AI output could not be parsed into schema.", Toast.LENGTH_LONG).show()
                                    }
                                    isLoading = false
                                } else {
                                    isLoading = false
                                    Toast.makeText(context, "AI failed to extract the data", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
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
                            modifier = Modifier.fillMaxWidth(),
                            isError = batch.section.isNullOrBlank()
                        )
                        
                        OutlinedTextField(
                            value = batch.location ?: "",
                            onValueChange = { newValue ->
                                val updatedBatches = editableBatches.toMutableList()
                                updatedBatches[index] = batch.copy(location = newValue)
                                editableBatches = updatedBatches
                            },
                            label = { Text("Default Room/Location") },
                            modifier = Modifier.fillMaxWidth(),
                            isError = batch.location.isNullOrBlank()
                        )
                        
                        val schedules = batch.weeklySchedule ?: emptyList()
                        Text("Schedules: ${schedules.size}", style = MaterialTheme.typography.bodySmall)
                        schedules.forEach { sched ->
                            Text("- ${sched.day}: ${sched.time} (${sched.location ?: "No room"})", style = MaterialTheme.typography.bodySmall)
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
