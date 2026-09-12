with open("app/src/main/java/com/example/ui/screens/ImportTimetableScreen.kt", "r") as f:
    content = f.read()

import re

# Add filePickerLauncher
launcher_code = """    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            try {
                selectedBitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                    android.graphics.ImageDecoder.decodeBitmap(source)
                } else {
                    @Suppress("DEPRECATION")
                    android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Failed to load image", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    var selectedFileUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var selectedFileMimeType by remember { mutableStateOf<String?>(null) }
    var selectedFileName by remember { mutableStateOf<String>("") }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedFileUri = uri
            selectedImageUri = null
            selectedBitmap = null
            selectedFileMimeType = context.contentResolver.getType(uri)
            
            // Get file name
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val displayNameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (displayNameIndex != -1) {
                        selectedFileName = it.getString(displayNameIndex)
                    }
                }
            }
        }
    }"""

content = re.sub(r'    val imagePickerLauncher = rememberLauncherForActivityResult\(.*?    \}', launcher_code, content, flags=re.DOTALL)

# Add Document button in UI
ui_buttons = """                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = { 
                            selectedFileUri = null
                            selectedImageUri = null
                            selectedBitmap = null
                            imagePickerLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            ) 
                        },
                        modifier = Modifier.weight(1f).height(100.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(androidx.compose.material.icons.Icons.Default.Image, contentDescription = "Gallery")
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Gallery")
                        }
                    }
                    Button(
                        onClick = { 
                            selectedFileUri = null
                            selectedImageUri = null
                            selectedBitmap = null
                            documentPickerLauncher.launch(arrayOf("application/pdf")) 
                        },
                        modifier = Modifier.weight(1f).height(100.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(androidx.compose.material.icons.Icons.Default.Description, contentDescription = "Document")
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Document")
                        }
                    }
                }"""
content = re.sub(r'                Button\(\n                    onClick = \{ imagePickerLauncher.launch\([^)]+\) \}.*?                \}', ui_buttons, content, flags=re.DOTALL)

with open("app/src/main/java/com/example/ui/screens/ImportTimetableScreen.kt", "w") as f:
    f.write(content)
