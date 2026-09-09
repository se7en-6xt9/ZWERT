import re

with open("app/src/main/java/com/example/ui/screens/ImportTimetableScreen.kt", "r") as f:
    content = f.read()

new_content = """package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
    var apiKey by remember { mutableStateOf("AIzaSyDwM0mgO8we85qwh3Uq8QQoQdF1W8oyNBA") }
    var isLoading by remember { mutableStateOf(false) }
    var aiStatusText by remember { mutableStateOf("") }
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
            Text("AI-Powered Importer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Paste JSON, raw text, or upload a file. The AI will extract it for you.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("Gemini API Key") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Button(
                onClick = { filePickerLauncher.launch("*/*") },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Upload File", fontWeight = FontWeight.Bold)
            }
            
            OutlinedTextField(
                value = rawText,
                onValueChange = { rawText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp),
                placeholder = { Text("Paste JSON or raw text here...") },
                shape = RoundedCornerShape(16.dp)
            )
            
            if (isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(aiStatusText, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            if (rawText.isBlank()) {
                                Toast.makeText(context, "Please paste some data", Toast.LENGTH_SHORT).show()
                                return@OutlinedButton
                            }
                            isLoading = true
                            viewModel.importTimetableFromJson(rawText,
                                onSuccess = {
                                    isLoading = false
                                    Toast.makeText(context, "Import Successful!", Toast.LENGTH_SHORT).show()
                                    navController.popBackStack()
                                },
                                onError = { error ->
                                    isLoading = false
                                    Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                }
                            )
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Strict JSON", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            if (rawText.isBlank() || apiKey.isBlank()) {
                                Toast.makeText(context, "Missing data or API Key", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isLoading = true
                            aiStatusText = "AI is thinking..."
                            coroutineScope.launch {
                                val aiResult = com.example.viewmodel.AiHelper.parseTimetableData(rawText, apiKey)
                                if (aiResult != null) {
                                    aiStatusText = "Saving data..."
                                    // Sometimes AI returns markdown wrapped JSON
                                    val cleanJson = aiResult.replace("```json", "").replace("```", "").trim()
                                    viewModel.importTimetableFromJson(cleanJson,
                                        onSuccess = {
                                            isLoading = false
                                            Toast.makeText(context, "AI Import Successful!", Toast.LENGTH_SHORT).show()
                                            navController.popBackStack()
                                        },
                                        onError = { error ->
                                            isLoading = false
                                            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                        }
                                    )
                                } else {
                                    isLoading = false
                                    Toast.makeText(context, "AI failed to parse the data", Toast.LENGTH_LONG).show()
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
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
"""

with open("app/src/main/java/com/example/ui/screens/ImportTimetableScreen.kt", "w") as f:
    f.write(new_content)
