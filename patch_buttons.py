with open("app/src/main/java/com/example/ui/screens/ImportTimetableScreen.kt", "r") as f:
    content = f.read()

import re

# Remove the old Row of buttons
content = re.sub(r'            Row\(horizontalArrangement = Arrangement.spacedBy\(16.dp\), modifier = Modifier.fillMaxWidth\(\)\) \{.*?            \}', '', content, flags=re.DOTALL)

# Insert the new Row
new_buttons = """            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { 
                        selectedFileUri = null
                        selectedFileName = ""
                        selectedFileMimeType = null
                        documentPickerLauncher.launch(arrayOf("application/pdf", "image/*", "text/plain")) 
                    },
                    modifier = Modifier.weight(1f).height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(androidx.compose.material.icons.Icons.Default.UploadFile, contentDescription = null)
                        Text("Document", fontWeight = FontWeight.Bold)
                    }
                }
                Button(
                    onClick = { 
                        selectedFileUri = null
                        selectedFileName = ""
                        selectedFileMimeType = null
                        imagePickerLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) 
                    },
                    modifier = Modifier.weight(1f).height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                        Text("Gallery", fontWeight = FontWeight.Bold)
                    }
                }
            }"""

content = content.replace("            if (selectedFileUri != null) {", new_buttons + "\n            if (selectedFileUri != null) {")

with open("app/src/main/java/com/example/ui/screens/ImportTimetableScreen.kt", "w") as f:
    f.write(content)
