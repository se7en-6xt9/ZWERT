with open("app/src/main/java/com/example/ui/screens/ImportTimetableScreen.kt", "r") as f:
    content = f.read()

import re

file_ui = """            if (selectedFileUri != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(androidx.compose.material.icons.Icons.Default.Description, contentDescription = "File", modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(selectedFileName.ifBlank { "Selected Document" }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text("PDF/Document", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { selectedFileUri = null; selectedFileName = ""; selectedFileMimeType = null }) {
                            Icon(androidx.compose.material.icons.Icons.Default.Close, contentDescription = "Remove")
                        }
                    }
                }
            }"""

content = content.replace("            if (selectedBitmap != null) {", file_ui + "\n            if (selectedBitmap != null) {")

with open("app/src/main/java/com/example/ui/screens/ImportTimetableScreen.kt", "w") as f:
    f.write(content)
