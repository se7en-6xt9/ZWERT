import re

with open("app/src/main/java/com/example/ui/screens/AddEditBatchScreen.kt", "r") as f:
    lines = f.readlines()

start_idx = -1
end_idx = -1

for i, line in enumerate(lines):
    if 'AnimatedVisibility(visible = scheduleInputMode == "Grid") {' in line:
        start_idx = i
    if 'AnimatedVisibility(visible = scheduleInputMode == "Blocks") {' in line:
        end_idx = i

if start_idx != -1 and end_idx != -1:
    new_grid_content = """                                AnimatedVisibility(visible = scheduleInputMode == "Grid") {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text("Tap the button below to open the full-screen schedule grid.", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp))
                                        
                                        Button(
                                            onClick = { isGridFullScreenOpen = true },
                                            modifier = Modifier.fillMaxWidth().height(56.dp).privateBounceClick(haptic) { isGridFullScreenOpen = true },
                                            shape = RoundedCornerShape(16.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.White)
                                        ) {
                                            Icon(Icons.Default.Fullscreen, "Open Grid")
                                            Spacer(Modifier.width(8.dp))
                                            Text("Open Full-Screen Grid", fontWeight = FontWeight.Bold)
                                        }
                                        
                                        if (selectedGridCells.isNotEmpty()) {
                                            Spacer(Modifier.height(16.dp))
                                            Text("${selectedGridCells.size} slots selected.", style = MaterialTheme.typography.labelMedium, color = accentColor, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
"""
    lines[start_idx:end_idx] = [new_grid_content]
    
with open("app/src/main/java/com/example/ui/screens/AddEditBatchScreen.kt", "w") as f:
    f.writelines(lines)
