package com.example.ui.screens

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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportTimetableScreen(navController: NavController, viewModel: MainViewModel) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var jsonText by remember { mutableStateOf("") }
    var currentStep by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }

    val promptText = """
        Mera timetable/schedule yeh hai: [apna timetable text ya photo yahan paste karo]
        
        Isse niche diye gaye JSON schema me convert karo. Rules:
        1. Har unique student-group (year+semester+section+course) ek "batch" hai.
        2. Ek batch hafte me jitni baar milta hai, utni entries "weeklySchedule" me daalo — class 1 hour ho ya 6 hour, koi restriction nahi, jo bhi actual time hai wahi likho (e.g. "6:00 AM - 11:00 AM", "7:00 PM - 8:00 PM").
        3. Students sirf ek baar, "students" array me daalo.
        4. Missing info "null" rakho, kabhi guess mat karo.
        5. Sirf valid JSON return karo, koi extra text nahi.
        
        Schema:
        {
          "teacher": { "name": null, "id": null },
          "batches": [
            {
              "batchId": "",
              "year": null,
              "semester": null,
              "course": { "code": null, "name": null },
              "section": null,
              "location": null,
              "weeklySchedule": [ { "day": "", "time": null, "location": null } ],
              "students": [ { "id": "", "name": "", "rollNumber": null } ]
            }
          ]
        }
    """.trimIndent()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val reader = InputStreamReader(inputStream)
                jsonText = reader.readText()
                reader.close()
                currentStep = 1
            } catch (e: Exception) {
                Toast.makeText(context, "Error reading file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text("Import Timetable", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        floatingActionButton = {
            if (currentStep == 1 && jsonText.isNotBlank()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        isLoading = true
                        coroutineScope.launch {
                            delay(800) // Simulated processing time
                            viewModel.importTimetableFromJson(
                                jsonString = jsonText,
                                onSuccess = {
                                    isLoading = false
                                    Toast.makeText(context, "Timetable Imported Successfully!", Toast.LENGTH_LONG).show()
                                    navController.popBackStack()
                                },
                                onError = { errorMsg ->
                                    isLoading = false
                                    Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Default.Check, null) },
                    text = { Text("Confirm & Import", fontWeight = FontWeight.Bold) }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Step Navigation Tabs
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TabRow(
                    selectedTabIndex = currentStep,
                    containerColor = Color.Transparent,
                    modifier = Modifier.fillMaxWidth(0.8f),
                    indicator = {}
                ) {
                    Tab(
                        selected = currentStep == 0,
                        onClick = { currentStep = 0 },
                        text = { Text("1. Instructions", fontWeight = if(currentStep == 0) FontWeight.Bold else FontWeight.Normal) }
                    )
                    Tab(
                        selected = currentStep == 1,
                        onClick = { currentStep = 1 },
                        text = { Text("2. Upload/Paste", fontWeight = if(currentStep == 1) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }

            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    slideInHorizontally(
                        initialOffsetX = { if (targetState > initialState) it else -it }
                    ) + fadeIn() togetherWith slideOutHorizontally(
                        targetOffsetX = { if (targetState > initialState) -it else it }
                    ) + fadeOut()
                },
                label = "step_anim"
            ) { step ->
                if (step == 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        InstructionCard(
                            step = "1",
                            text = "Open any free AI chatbot (Claude, ChatGPT, Gemini) on your phone or PC."
                        )
                        InstructionCard(
                            step = "2",
                            text = "Copy the prompt below and paste it into the chatbot, along with a photo or text of your timetable."
                        )
                        InstructionCard(
                            step = "3",
                            text = "Copy the JSON the AI gives you back."
                        )
                        InstructionCard(
                            step = "4",
                            text = "Switch to the Upload tab here to paste or upload it."
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(promptText))
                                Toast.makeText(context, "Prompt Copied to Clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Copy Prompt to Clipboard", fontWeight = FontWeight.Bold)
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { currentStep = 1 },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Next: Paste JSON", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = null)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        
                        Button(
                            onClick = { filePickerLauncher.launch("application/json") },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Upload .json File", fontWeight = FontWeight.Bold)
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            HorizontalDivider(modifier = Modifier.weight(1f))
                            Text(" OR ", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
                            HorizontalDivider(modifier = Modifier.weight(1f))
                        }

                        OutlinedTextField(
                            value = jsonText,
                            onValueChange = { jsonText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            placeholder = { Text("Paste your JSON here...") },
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )

                        if (isLoading) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("Parsing Schedule...", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(80.dp)) // padding for FAB
                    }
                }
            }
        }
    }
}

@Composable
fun InstructionCard(step: String, text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(step, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
