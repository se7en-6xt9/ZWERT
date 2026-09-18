package com.example.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.CourseEntity
import com.example.data.CsvParseResult
import com.example.data.CsvStudentParser
import com.example.data.StudentImport
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.launch

enum class CsvImportTargetMode {
    ONBOARD_NEW_CLASS,
    EXISTING_CLASS,
    RETURN_TO_CALLER // Used when called from AddEditBatchScreen
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkCsvImportDialog(
    viewModel: MainViewModel,
    initialCourseId: String? = null,
    targetMode: CsvImportTargetMode = CsvImportTargetMode.EXISTING_CLASS,
    onDismiss: () -> Unit,
    onImportFinished: ((List<StudentImport>) -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    val courses by viewModel.getAllCourses().collectAsState(initial = emptyList())

    var selectedMode by remember {
        mutableStateOf(
            if (targetMode == CsvImportTargetMode.RETURN_TO_CALLER) {
                CsvImportTargetMode.RETURN_TO_CALLER
            } else if (initialCourseId != null) {
                CsvImportTargetMode.EXISTING_CLASS
            } else if (courses.isEmpty()) {
                CsvImportTargetMode.ONBOARD_NEW_CLASS
            } else {
                CsvImportTargetMode.EXISTING_CLASS
            }
        )
    }

    // Class details for Onboard New Class
    var newCourseName by remember { mutableStateOf("") }
    var newCourseCode by remember { mutableStateOf("") }
    var newSection by remember { mutableStateOf("A") }
    var newRoom by remember { mutableStateOf("") }

    // Selected course for Existing Class
    var selectedCourseId by remember {
        mutableStateOf(initialCourseId ?: courses.firstOrNull()?.id ?: "")
    }
    var overwriteExistingStudents by remember { mutableStateOf(false) }

    // CSV Input state
    var rawCsvText by remember { mutableStateOf("") }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var inputTab by remember { mutableIntStateOf(0) } // 0: File Pick, 1: Paste Text

    // Parsing state
    var parseResult by remember { mutableStateOf<CsvParseResult?>(null) }
    var parsedStudentsList by remember { mutableStateOf<List<StudentImport>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredStudents = remember(parsedStudentsList, searchQuery) {
        if (searchQuery.isBlank()) parsedStudentsList
        else parsedStudentsList.filter {
            (it.name ?: "").contains(searchQuery, ignoreCase = true) ||
            (it.rollNumber ?: "").contains(searchQuery, ignoreCase = true)
        }
    }

    // Column mapping overrides
    var overrideNameCol by remember { mutableStateOf<Int?>(null) }
    var overrideRollCol by remember { mutableStateOf<Int?>(null) }
    var overrideEmailCol by remember { mutableStateOf<Int?>(null) }

    // In-flight progress
    var isImporting by remember { mutableStateOf(false) }
    var importSuccessMessage by remember { mutableStateOf<String?>(null) }

    val accentIndigo = Color(0xFF6366F1)
    val emeraldGreen = Color(0xFF10B981)
    val amberOrange = Color(0xFFF59E0B)

    fun runParser(
        content: String,
        nameCol: Int? = overrideNameCol,
        rollCol: Int? = overrideRollCol,
        emailCol: Int? = overrideEmailCol
    ) {
        if (content.isBlank()) {
            parseResult = null
            parsedStudentsList = emptyList()
            return
        }
        val result = CsvStudentParser.parseCsv(
            csvContent = content,
            overrideNameIndex = nameCol,
            overrideRollIndex = rollCol,
            overrideEmailIndex = emailCol
        )
        parseResult = result
        parsedStudentsList = result.students
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                // Get display name
                var name = "students.csv"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            name = cursor.getString(nameIndex)
                        }
                    }
                }
                selectedFileName = name

                // Read file content
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val text = stream.bufferedReader(Charsets.UTF_8).readText()
                    rawCsvText = text
                    overrideNameCol = null
                    overrideRollCol = null
                    overrideEmailCol = null
                    runParser(text)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    Toast.makeText(context, "Loaded $name: ${parseResult?.validCount ?: 0} students", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error reading CSV: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!isImporting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !isImporting, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    "Bulk Import Students",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    "Parse CSV file and upload directly to Firestore",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss, enabled = !isImporting) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                            }
                        },
                        actions = {
                            // Copy Template button
                            TextButton(
                                onClick = {
                                    val template = "Roll Number, Student Name, Department\n24BCS001, Aarav Sharma, Computer Science\n24BCS002, Ananya Patel, Computer Science"
                                    clipboardManager.setText(AnnotatedString(template))
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    Toast.makeText(context, "CSV template copied to clipboard!", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Template", style = MaterialTheme.typography.labelMedium)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                    )
                },
                bottomBar = {
                    Surface(
                        tonalElevation = 8.dp,
                        shadowElevation = 16.dp,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .navigationBarsPadding(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val canProceed = parsedStudentsList.isNotEmpty() && when (selectedMode) {
                                CsvImportTargetMode.RETURN_TO_CALLER -> true
                                CsvImportTargetMode.ONBOARD_NEW_CLASS -> newCourseName.isNotBlank()
                                CsvImportTargetMode.EXISTING_CLASS -> selectedCourseId.isNotBlank()
                            }

                            Button(
                                onClick = {
                                    if (!canProceed) return@Button
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                                    if (selectedMode == CsvImportTargetMode.RETURN_TO_CALLER) {
                                        onImportFinished?.invoke(parsedStudentsList)
                                        onDismiss()
                                        return@Button
                                    }

                                    isImporting = true
                                    if (selectedMode == CsvImportTargetMode.ONBOARD_NEW_CLASS) {
                                        viewModel.quickOnboardClassWithStudents(
                                            courseName = newCourseName.trim(),
                                            courseCode = newCourseCode.trim(),
                                            section = newSection.trim(),
                                            location = newRoom.trim(),
                                            students = parsedStudentsList,
                                            onSuccess = { createdId ->
                                                isImporting = false
                                                importSuccessMessage = "Successfully created '${newCourseName.trim()}' with ${parsedStudentsList.size} students uploaded to Firestore!"
                                                Toast.makeText(context, "Class & students onboarded to Firestore!", Toast.LENGTH_LONG).show()
                                            },
                                            onError = { err ->
                                                isImporting = false
                                                Toast.makeText(context, "Onboarding error: $err", Toast.LENGTH_LONG).show()
                                            }
                                        )
                                    } else {
                                        viewModel.bulkImportStudentsToCourse(
                                            courseId = selectedCourseId,
                                            students = parsedStudentsList,
                                            overwriteExisting = overwriteExistingStudents,
                                            onSuccess = { count ->
                                                isImporting = false
                                                val targetCourse = courses.find { it.id == selectedCourseId }
                                                importSuccessMessage = "Successfully uploaded $count students to '${targetCourse?.name ?: "Class"}' in Firestore!"
                                                Toast.makeText(context, "Imported $count students to Firestore!", Toast.LENGTH_LONG).show()
                                            },
                                            onError = { err ->
                                                isImporting = false
                                                Toast.makeText(context, "Import error: $err", Toast.LENGTH_LONG).show()
                                            }
                                        )
                                    }
                                },
                                enabled = canProceed && !isImporting,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = accentIndigo)
                            ) {
                                if (isImporting) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Text("Writing to Firestore...", fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        when (selectedMode) {
                                            CsvImportTargetMode.RETURN_TO_CALLER -> "Apply ${parsedStudentsList.size} Students"
                                            CsvImportTargetMode.ONBOARD_NEW_CLASS -> "Onboard Class & Upload (${parsedStudentsList.size})"
                                            CsvImportTargetMode.EXISTING_CLASS -> "Upload ${parsedStudentsList.size} Students to Firestore"
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }
                    }
                }
            ) { padding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {

                    // Success announcement banner
                    if (importSuccessMessage != null) {
                        item {
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = emeraldGreen.copy(alpha = 0.12f)),
                                border = BorderStroke(1.dp, emeraldGreen.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = emeraldGreen)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Sync Complete", fontWeight = FontWeight.Bold, color = emeraldGreen)
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(importSuccessMessage ?: "", style = MaterialTheme.typography.bodyMedium)
                                    Spacer(Modifier.height(12.dp))
                                    Button(
                                        onClick = onDismiss,
                                        colors = ButtonDefaults.buttonColors(containerColor = emeraldGreen),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Text("Done", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Mode Selection (if not called from AddEditBatchScreen)
                    if (targetMode != CsvImportTargetMode.RETURN_TO_CALLER) {
                        item {
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("1. Select Destination", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

                                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                        SegmentedButton(
                                            selected = selectedMode == CsvImportTargetMode.ONBOARD_NEW_CLASS,
                                            onClick = {
                                                selectedMode = CsvImportTargetMode.ONBOARD_NEW_CLASS
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            },
                                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                                        ) {
                                            Text("New Class", fontWeight = FontWeight.SemiBold)
                                        }
                                        SegmentedButton(
                                            selected = selectedMode == CsvImportTargetMode.EXISTING_CLASS,
                                            onClick = {
                                                selectedMode = CsvImportTargetMode.EXISTING_CLASS
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            },
                                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                                        ) {
                                            Text("Existing Class", fontWeight = FontWeight.SemiBold)
                                        }
                                    }

                                    if (selectedMode == CsvImportTargetMode.ONBOARD_NEW_CLASS) {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            OutlinedTextField(
                                                value = newCourseName,
                                                onValueChange = { newCourseName = it },
                                                label = { Text("Course Name * (e.g. Distributed Systems)") },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(14.dp),
                                                singleLine = true
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                OutlinedTextField(
                                                    value = newCourseCode,
                                                    onValueChange = { newCourseCode = it },
                                                    label = { Text("Code (CS402)") },
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(14.dp),
                                                    singleLine = true
                                                )
                                                OutlinedTextField(
                                                    value = newSection,
                                                    onValueChange = { newSection = it },
                                                    label = { Text("Section (A)") },
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(14.dp),
                                                    singleLine = true
                                                )
                                            }
                                        }
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            if (courses.isEmpty()) {
                                                Text(
                                                    "No existing classes found. Switch to 'New Class' to create your first class.",
                                                    color = MaterialTheme.colorScheme.error,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            } else {
                                                Text("Choose Target Class:", style = MaterialTheme.typography.labelMedium)
                                                // Horizontal chips for classes
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .horizontalScroll(rememberScrollState()),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    courses.forEach { course ->
                                                        val isSelected = course.id == selectedCourseId
                                                        FilterChip(
                                                            selected = isSelected,
                                                            onClick = {
                                                                selectedCourseId = course.id
                                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            },
                                                            label = {
                                                                Text("${course.name} (${course.code.ifBlank { "No Code" }})")
                                                            },
                                                            leadingIcon = if (isSelected) {
                                                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                                            } else null
                                                        )
                                                    }
                                                }

                                                // Overwrite checkbox
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.clickable { overwriteExistingStudents = !overwriteExistingStudents }
                                                ) {
                                                    Checkbox(
                                                        checked = overwriteExistingStudents,
                                                        onCheckedChange = { overwriteExistingStudents = it }
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        "Replace existing roster in this class",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // CSV File Upload & Input Options
                    item {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "2. Supply Student CSV",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    // Try Sample Data chip
                                    AssistChip(
                                        onClick = {
                                            val sample = CsvStudentParser.getSampleCsvString()
                                            rawCsvText = sample
                                            selectedFileName = "Sample_CS_Class.csv"
                                            inputTab = 1
                                            overrideNameCol = null
                                            overrideRollCol = null
                                            overrideEmailCol = null
                                            runParser(sample)
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            Toast.makeText(context, "Loaded sample class with sync emails!", Toast.LENGTH_SHORT).show()
                                        },
                                        label = { Text("Try Sample CSV", fontSize = 12.sp) },
                                        leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp), tint = amberOrange) }
                                    )
                                }

                                TabRow(
                                    selectedTabIndex = inputTab,
                                    containerColor = Color.Transparent,
                                    divider = {}
                                ) {
                                    Tab(
                                        selected = inputTab == 0,
                                        onClick = { inputTab = 0 },
                                        text = { Text("Choose File") },
                                        icon = { Icon(Icons.Default.UploadFile, contentDescription = null) }
                                    )
                                    Tab(
                                        selected = inputTab == 1,
                                        onClick = { inputTab = 1 },
                                        text = { Text("Paste / Edit CSV") },
                                        icon = { Icon(Icons.Default.EditNote, contentDescription = null) }
                                    )
                                }

                                if (inputTab == 0) {
                                    // File Picker Box
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                filePickerLauncher.launch(
                                                    arrayOf(
                                                        "text/*",
                                                        "text/comma-separated-values",
                                                        "text/csv",
                                                        "application/vnd.ms-excel",
                                                        "*/*"
                                                    )
                                                )
                                            },
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.5.dp, if (selectedFileName != null) emeraldGreen else accentIndigo.copy(alpha = 0.4f))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(CircleShape)
                                                    .background((if (selectedFileName != null) emeraldGreen else accentIndigo).copy(alpha = 0.12f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    if (selectedFileName != null) Icons.Default.CheckCircle else Icons.Default.CloudUpload,
                                                    contentDescription = null,
                                                    tint = if (selectedFileName != null) emeraldGreen else accentIndigo,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                            Text(
                                                selectedFileName ?: "Tap to select CSV file from device",
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center
                                            )
                                            Text(
                                                if (selectedFileName != null) "Tap to choose a different CSV file" else "Supports .csv, .txt with comma, tab, or semicolon delimiters",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                } else {
                                    // Paste Area
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = rawCsvText,
                                            onValueChange = {
                                                rawCsvText = it
                                                runParser(it)
                                            },
                                            label = { Text("Paste CSV Content (e.g. 24BCS001, John Doe)") },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(160.dp),
                                            shape = RoundedCornerShape(14.dp),
                                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                                            placeholder = { Text("Roll Number, Student Name\n24BCS001, Aarav Sharma\n24BCS002, Ananya Patel") }
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            TextButton(onClick = {
                                                val clip = clipboardManager.getText()?.text ?: ""
                                                if (clip.isNotBlank()) {
                                                    rawCsvText = clip
                                                    runParser(clip)
                                                    Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                                                }
                                            }) {
                                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Paste Clipboard")
                                            }
                                            if (rawCsvText.isNotBlank()) {
                                                TextButton(onClick = {
                                                    rawCsvText = ""
                                                    selectedFileName = null
                                                    runParser("")
                                                }) {
                                                    Text("Clear", color = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Parsing & Validation Results
                    if (parseResult != null) {
                        val res = parseResult!!
                        item {
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "3. Parsing & Validation Summary",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    // Metric badges
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        MetricBadge(
                                            label = "Valid Students",
                                            value = "${res.validCount}",
                                            color = emeraldGreen,
                                            modifier = Modifier.weight(1f)
                                        )
                                        val emailCount = res.students.count { !it.email.isNullOrBlank() }
                                        MetricBadge(
                                            label = "Live Sync Emails",
                                            value = "$emailCount",
                                            color = accentIndigo,
                                            modifier = Modifier.weight(1f)
                                        )
                                        MetricBadge(
                                            label = "Duplicates",
                                            value = "${res.duplicateCount}",
                                            color = if (res.duplicateCount > 0) amberOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    // Column mapping selector if multiple columns
                                    if (res.headers.size >= 2) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                                .padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text("Detected Columns (tap to adjust):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                // Name Column mapping dropdown
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("Student Name:", style = MaterialTheme.typography.labelSmall)
                                                    var nameExpanded by remember { mutableStateOf(false) }
                                                    OutlinedButton(
                                                        onClick = { nameExpanded = true },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        shape = RoundedCornerShape(10.dp),
                                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                                                    ) {
                                                        Text(
                                                            res.headers.getOrNull(res.nameColumnIndex) ?: "Col ${res.nameColumnIndex + 1}",
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            fontSize = 12.sp
                                                        )
                                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    }
                                                    DropdownMenu(expanded = nameExpanded, onDismissRequest = { nameExpanded = false }) {
                                                        res.headers.forEachIndexed { i, h ->
                                                            DropdownMenuItem(
                                                                text = { Text("Col ${i + 1}: $h") },
                                                                onClick = {
                                                                    overrideNameCol = i
                                                                    runParser(rawCsvText, nameCol = i)
                                                                    nameExpanded = false
                                                                }
                                                            )
                                                        }
                                                    }
                                                }

                                                // Roll Column mapping dropdown
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("Roll Number:", style = MaterialTheme.typography.labelSmall)
                                                    var rollExpanded by remember { mutableStateOf(false) }
                                                    OutlinedButton(
                                                        onClick = { rollExpanded = true },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        shape = RoundedCornerShape(10.dp),
                                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                                                    ) {
                                                        Text(
                                                            res.headers.getOrNull(res.rollColumnIndex) ?: "Col ${res.rollColumnIndex + 1}",
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            fontSize = 12.sp
                                                        )
                                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    }
                                                    DropdownMenu(expanded = rollExpanded, onDismissRequest = { rollExpanded = false }) {
                                                        res.headers.forEachIndexed { i, h ->
                                                            DropdownMenuItem(
                                                                text = { Text("Col ${i + 1}: $h") },
                                                                onClick = {
                                                                    overrideRollCol = i
                                                                    runParser(rawCsvText, rollCol = i)
                                                                    rollExpanded = false
                                                                }
                                                            )
                                                        }
                                                    }
                                                }

                                                // Email Column mapping dropdown
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("Email (Sync):", style = MaterialTheme.typography.labelSmall)
                                                    var emailExpanded by remember { mutableStateOf(false) }
                                                    OutlinedButton(
                                                        onClick = { emailExpanded = true },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        shape = RoundedCornerShape(10.dp),
                                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                                                    ) {
                                                        Text(
                                                            if (res.emailColumnIndex >= 0) {
                                                                res.headers.getOrNull(res.emailColumnIndex) ?: "Col ${res.emailColumnIndex + 1}"
                                                            } else "None",
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            fontSize = 12.sp
                                                        )
                                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    }
                                                    DropdownMenu(expanded = emailExpanded, onDismissRequest = { emailExpanded = false }) {
                                                        DropdownMenuItem(
                                                            text = { Text("None (Skip Email)") },
                                                            onClick = {
                                                                overrideEmailCol = -1
                                                                runParser(rawCsvText, emailCol = -1)
                                                                emailExpanded = false
                                                            }
                                                        )
                                                        res.headers.forEachIndexed { i, h ->
                                                            DropdownMenuItem(
                                                                text = { Text("Col ${i + 1}: $h") },
                                                                onClick = {
                                                                    overrideEmailCol = i
                                                                    runParser(rawCsvText, emailCol = i)
                                                                    emailExpanded = false
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Display warnings or errors if any
                                    if (res.errorRows.isNotEmpty()) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                                .padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                "${res.errorRows.size} row(s) skipped due to missing names:",
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.error,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                            res.errorRows.take(3).forEach { (row, reason) ->
                                                Text("• Row $row: $reason", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                                            }
                                            if (res.errorRows.size > 3) {
                                                Text("...and ${res.errorRows.size - 3} more", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Search and Filter Bar for Parsed Students
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    trailingIcon = if (searchQuery.isNotBlank()) {
                                        { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, contentDescription = "Clear") } }
                                    } else null,
                                    placeholder = { Text("Search parsed students...") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp),
                                    singleLine = true
                                )
                                Text(
                                    "${parsedStudentsList.size} Total",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }

                        // Student Cards List Preview
                        itemsIndexed(filteredStudents, key = { _, s -> s.id ?: "" }) { index, student ->
                            StudentPreviewItem(
                                student = student,
                                index = index + 1,
                                onDelete = {
                                    parsedStudentsList = parsedStudentsList.filter { it.id != student.id }
                                }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(72.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricBadge(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
        }
    }
}

@Composable
private fun StudentPreviewItem(
    student: StudentImport,
    index: Int,
    onDelete: () -> Unit
) {
    val name = student.name ?: "Unknown"
    val roll = student.rollNumber ?: ""
    val initials = name.split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString("")

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Index number
            Text(
                "$index",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(24.dp)
            )

            // Avatar
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF6366F1).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    initials.ifBlank { "?" },
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF6366F1)
                )
            }

            // Student Info
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        roll,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!student.email.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                Icons.Default.Email,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = Color(0xFF6366F1)
                            )
                            Text(
                                student.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF6366F1),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Delete action
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Remove student",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
