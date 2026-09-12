import re

with open("app/src/main/java/com/example/ui/screens/AddEditBatchScreen.kt", "r") as f:
    content = f.read()

# 1. Add new imports if not present
new_imports = """
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
"""
for imp in new_imports.strip().split("\n"):
    if imp not in content:
        content = content.replace("import androidx.compose.ui.unit.dp\n", f"import androidx.compose.ui.unit.dp\n{imp}\n")


# 2. Add Theme inside AddEditBatchScreen
theme_code = """
    var isLoading by remember { mutableStateOf(false) }
    
    val accentColor = Color(0xFF6750A4)
    val colorScheme = lightColorScheme(
        primary = accentColor,
        surface = Color(0xFFFFFFFF).copy(alpha = 0.9f),
        background = Color(0xFFF0F4F8),
        surfaceVariant = Color(0xFFFFFFFF),
        onSurfaceVariant = Color(0xFF49454F)
    )
"""
content = content.replace("    var isLoading by remember { mutableStateOf(false) }", theme_code)

# Wrap Scaffold with MaterialTheme
content = content.replace("    Scaffold(", "    MaterialTheme(colorScheme = colorScheme) {\n    Scaffold(")

# Put the closing brace at the very end before closing the function
content = content.replace("        }\n    }\n}\n", "        }\n    }\n    }\n}\n")


# 3. Add Grid state
grid_state_code = """
    // Mode toggle
    var scheduleInputMode by remember { mutableStateOf("Blocks") } // "Blocks" or "Grid"
    
    // Grid State
    val gridDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val gridHours = listOf(
        "08:00 AM - 09:00 AM",
        "09:00 AM - 10:00 AM",
        "10:00 AM - 11:00 AM",
        "11:00 AM - 12:00 PM",
        "12:00 PM - 01:00 PM",
        "01:00 PM - 02:00 PM",
        "02:00 PM - 03:00 PM",
        "03:00 PM - 04:00 PM",
        "04:00 PM - 05:00 PM",
        "05:00 PM - 06:00 PM"
    )
    var selectedGridCells by remember { mutableStateOf(setOf<Pair<String, String>>()) }
"""
content = content.replace("    // Bulk add state", grid_state_code + "\n    // Bulk add state")


# 4. Update the Weekly Schedule Builder UI
old_ui_start = 'Text("Weekly Schedule", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)'
old_ui_end = 'Text("Add Time Slot")\n                        }'
pattern = re.escape(old_ui_start) + r'.*?' + re.escape(old_ui_end)

new_ui = """Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("Weekly Schedule", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            
                            // Segmented Toggle
                            Row(
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).padding(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (scheduleInputMode == "Blocks") MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                        .clickable { scheduleInputMode = "Blocks" }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Blocks", color = if (scheduleInputMode == "Blocks") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (scheduleInputMode == "Grid") MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                        .clickable { scheduleInputMode = "Grid" }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Grid", color = if (scheduleInputMode == "Grid") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                        
                        AnimatedVisibility(visible = scheduleInputMode == "Grid") {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text("Tap slots to select your classes. Ideal for 1-hour lectures.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
                                
                                Column(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                    // Header
                                    Row(modifier = Modifier.padding(bottom = 8.dp)) {
                                        Spacer(modifier = Modifier.width(80.dp))
                                        gridDays.forEach { day ->
                                            Text(day, modifier = Modifier.width(52.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                    
                                    // Rows
                                    gridHours.forEach { hour ->
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                            Text(hour.split(" - ")[0], modifier = Modifier.width(80.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            
                                            gridDays.forEach { day ->
                                                val isSelected = selectedGridCells.contains(Pair(day, hour))
                                                Box(
                                                    modifier = Modifier
                                                        .padding(horizontal = 4.dp)
                                                        .size(44.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                                        .clickable {
                                                            val newSet = selectedGridCells.toMutableSet()
                                                            val pair = Pair(day, hour)
                                                            if(isSelected) newSet.remove(pair) else newSet.add(pair)
                                                            selectedGridCells = newSet
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (isSelected) {
                                                        Icon(Icons.Default.Check, "Selected", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        AnimatedVisibility(visible = scheduleInputMode == "Blocks") {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                scheduleBlocks.forEachIndexed { index, block ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
                        }"""
content = re.sub(pattern, new_ui, content, flags=re.DOTALL)


# 5. Fix Save Logic
save_logic_start = 'val finalSchedules = scheduleBlocks.flatMap { block ->'
save_logic_end = 'location = block.location.takeIf { it.isNotBlank() })\n                            }\n                        }'

new_save_logic = """val finalSchedules = scheduleBlocks.filter { it.timeRange.isNotBlank() }.flatMap { block ->
                            block.selectedDays.map { day ->
                                ScheduleImport(day = day, time = block.timeRange, location = block.location.takeIf { it.isNotBlank() })
                            }
                        } + selectedGridCells.map { cell ->
                            ScheduleImport(day = cell.first, time = cell.second, location = defaultLocation.takeIf { it.isNotBlank() })
                        }"""
content = re.sub(re.escape(save_logic_start) + r'.*?' + re.escape(save_logic_end), new_save_logic, content, flags=re.DOTALL)

with open("app/src/main/java/com/example/ui/screens/AddEditBatchScreen.kt", "w") as f:
    f.write(content)

