import re

with open("app/src/main/java/com/example/ui/screens/AddEditBatchScreen.kt", "r") as f:
    content = f.read()

# 1. Add the ScheduleBlockState class
data_class = """
data class ScheduleBlockState(
    val id: String = java.util.UUID.randomUUID().toString(),
    var timeRange: String = "",
    var selectedDays: Set<String> = emptySet(),
    var location: String = ""
)
"""

if "data class ScheduleBlockState" not in content:
    content = content.replace("package com.example.ui.screens\n", "package com.example.ui.screens\n\n" + data_class)

# 2. Replace state definitions
old_state = "var schedules by remember { mutableStateOf(listOf<ScheduleImport>()) }"
new_state = """var scheduleBlocks by remember { mutableStateOf(listOf<ScheduleBlockState>()) }
    var students by remember { mutableStateOf(listOf<StudentImport>()) }"""
content = re.sub(r'var schedules by remember .*?\n.*?var students by remember', new_state, content, flags=re.DOTALL)

# 3. Update LaunchedEffect
old_effect = """                schedules = batch.weeklySchedule ?: emptyList()
                students = batch.students ?: emptyList()"""
new_effect = """                students = batch.students ?: emptyList()
                
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
                scheduleBlocks = blocks"""
content = content.replace(old_effect, new_effect)

# 4. Replace Weekly Schedule Builder UI
old_weekly_ui = r'// Weekly Schedule Builder.*?// Students Section'
new_weekly_ui = """// Weekly Schedule Builder
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

                // Students Section"""
content = re.sub(old_weekly_ui, new_weekly_ui, content, flags=re.DOTALL)

# 5. Fix Save Button
old_save_logic = """                        if (schedules.any { it.day.isNullOrBlank() }) {
                            Toast.makeText(context, "All schedules must have a day selected.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        val finalBatch = BatchImport(
                            batchId = batchId ?: UUID.randomUUID().toString(),
                            year = year,
                            semester = semester,
                            course = CourseImport(code = courseCode, name = courseName),
                            section = section,
                            location = defaultLocation,
                            weeklySchedule = schedules,
                            students = students
                        )"""

new_save_logic = """                        if (scheduleBlocks.any { it.selectedDays.isEmpty() }) {
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
                        )"""
content = content.replace(old_save_logic, new_save_logic)

with open("app/src/main/java/com/example/ui/screens/AddEditBatchScreen.kt", "w") as f:
    f.write(content)

