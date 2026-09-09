import re

with open("app/src/main/java/com/example/ui/screens/ImportTimetableScreen.kt", "r") as f:
    content = f.read()

# We will modify the main column to include a "Manual Prompt" card and copy button.
manual_prompt_ui = """
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Manual AI Extraction", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("If the built-in AI fails, you can use ChatGPT/Claude to generate the JSON manually. Copy this prompt and paste your timetable there.", style = MaterialTheme.typography.bodySmall)
                    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                    val promptText = \"\"\"
                        You are a timetable data extraction engine. Extract the schedule from my data and return ONLY valid JSON matching this exact schema. Do not add markdown fences.

                        Rules:
                        1. Group by year + semester + section + course into a "batch".
                        2. List every weekly occurrence in "weeklySchedule".
                        3. If students exist, list them in "students". If missing, empty array [].
                        4. For missing fields, use null.
                        5. Auto-generate IDs if missing (e.g., "batch_1").

                        Schema:
                        {
                          "teacher": { "name": "String|null", "id": "String|null" },
                          "batches": [
                            {
                              "batchId": "String",
                              "year": "String|null",
                              "semester": "String|null",
                              "course": { "code": "String|null", "name": "String|null" },
                              "section": "String|null",
                              "location": "String|null",
                              "weeklySchedule": [ { "day": "String", "time": "String|null", "location": "String|null" } ],
                              "students": [ { "id": "String", "name": "String", "rollNumber": "String|null" } ]
                            }
                          ]
                        }
                    \"\"\".trimIndent()
                    Button(
                        onClick = {
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(promptText))
                            Toast.makeText(context, "Prompt copied!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Copy AI Prompt")
                    }
                }
            }

            OutlinedTextField(
"""

content = content.replace("            OutlinedTextField(\n                value = rawText", manual_prompt_ui + "                value = rawText")


# Now we modify ReviewImportData to allow editing Day and Time.
review_ui_old = """                        val schedules = batch.weeklySchedule ?: emptyList()
                        Text("Schedules: ${schedules.size}", style = MaterialTheme.typography.bodySmall)
                        schedules.forEach { sched ->
                            Text("- ${sched.day}: ${sched.time} (${sched.location ?: "No room"})", style = MaterialTheme.typography.bodySmall)
                        }"""

review_ui_new = """                        val schedules = batch.weeklySchedule ?: emptyList()
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
                        }"""

content = content.replace(review_ui_old, review_ui_new)

with open("app/src/main/java/com/example/ui/screens/ImportTimetableScreen.kt", "w") as f:
    f.write(content)

