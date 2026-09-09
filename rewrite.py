import re

with open("app/src/main/java/com/example/ui/screens/ImportTimetableScreen.kt", "r") as f:
    content = f.read()

# We need to change the two places where viewModel.importTimetableFromJson is called
# to instead use viewModel.parseTimetableJson, and set it to a state variable.

replacement1 = """                            val parsed = viewModel.parseTimetableJson(rawText)
                            if (parsed != null) {
                                reviewData = parsed
                            } else {
                                Toast.makeText(context, "Failed to parse JSON", Toast.LENGTH_LONG).show()
                            }
                            isLoading = false"""

content = re.sub(r'viewModel\.importTimetableFromJson\(rawText,.*?onError = \{ error ->.*?\}\n                            \)', replacement1, content, flags=re.DOTALL)

replacement2 = """                                    val parsed = viewModel.parseTimetableJson(cleanJson)
                                    if (parsed != null) {
                                        reviewData = parsed
                                    } else {
                                        Toast.makeText(context, "AI output could not be parsed into schema.", Toast.LENGTH_LONG).show()
                                    }
                                    isLoading = false"""

content = re.sub(r'viewModel\.importTimetableFromJson\(cleanJson,.*?onError = \{ error ->.*?\}\n                                    \)', replacement2, content, flags=re.DOTALL)

# Now we need to add `var reviewData by remember { mutableStateOf<com.example.data.ImportTimetableData?>(null) }`
# at the top of the Composable.
content = content.replace("var aiStatusText by remember { mutableStateOf(\"\") }", "var aiStatusText by remember { mutableStateOf(\"\") }\n    var reviewData by remember { mutableStateOf<com.example.data.ImportTimetableData?>(null) }")

# And in the UI, if reviewData != null, show ReviewUI, else show the current Column.
content = content.replace("Scaffold(", """
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

    Scaffold(""")

# Now append ReviewImportData composable at the bottom
review_composable = """
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
"""

with open("app/src/main/java/com/example/ui/screens/ImportTimetableScreen.kt", "w") as f:
    f.write(content + "\n" + review_composable)

