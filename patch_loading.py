with open("app/src/main/java/com/example/ui/screens/ImportTimetableScreen.kt", "r") as f:
    content = f.read()

target = """            if (isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(aiStatusText, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {"""

replacement = """            if (isLoading) {
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
                        onClick = {"""

content = content.replace(target, replacement)

target2 = """                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Strict JSON", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {"""
replacement2 = """                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Strict JSON", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        enabled = !isLoading,
                        onClick = {"""
content = content.replace(target2, replacement2)

target3 = """                        Text("Use AI", fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(40.dp))"""
replacement3 = """                        Text("Use AI", fontWeight = FontWeight.Bold)
                    }
                }
            Spacer(modifier = Modifier.height(40.dp))"""
content = content.replace(target3, replacement3)

with open("app/src/main/java/com/example/ui/screens/ImportTimetableScreen.kt", "w") as f:
    f.write(content)
