with open("app/src/main/java/com/example/ui/screens/ImportTimetableScreen.kt", "r") as f:
    content = f.read()

import re

click_lambda = """                    onClick = {
                        val apiKey = com.example.BuildConfig.GEMINI_API_KEY
                        if (apiKey.isBlank()) {
                            Toast.makeText(context, "API Key missing! Add it in the Secrets panel.", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        isLoading = true
                        aiStatusText = "AI is thinking..."
                        coroutineScope.launch {
                            var fileBytes: ByteArray? = null
                            var fileMime: String? = null
                            if (selectedFileUri != null) {
                                try {
                                    context.contentResolver.openInputStream(selectedFileUri!!)?.use { inputStream ->
                                        fileBytes = inputStream.readBytes()
                                        fileMime = selectedFileMimeType ?: "application/pdf"
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("ImportTimetable", "Error reading file", e)
                                }
                            }
                            
                            var errorMessage = "AI failed to extract the data"
                            val aiResult = try {
                                com.example.viewmodel.AiHelper.parseTimetableData(
                                    rawText = rawText,
                                    image = selectedBitmap,
                                    apiKey = apiKey,
                                    fileBytes = fileBytes,
                                    fileMimeType = fileMime
                                )
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "Network or API error occurred."
                                null
                            }
                            
                            if (aiResult != null) {
                                aiStatusText = "Parsing schema..."
                                val parsed = viewModel.parseTimetableJson(aiResult)
                                if (parsed != null) {
                                    reviewData = parsed
                                } else {
                                    Toast.makeText(context, "AI output could not be parsed into schema.", Toast.LENGTH_LONG).show()
                                }
                                isLoading = false
                            } else {
                                isLoading = false
                                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                            }
                        }
                    },"""

# I will match the button from `onClick = {` up to `modifier = Modifier.fillMaxWidth().height(56.dp),`
content = re.sub(r'                    onClick = \{.*?\n                    modifier = Modifier\.fillMaxWidth\(\)\.height\(56\.dp\),', click_lambda + '\n                    modifier = Modifier.fillMaxWidth().height(56.dp),', content, flags=re.DOTALL)

with open("app/src/main/java/com/example/ui/screens/ImportTimetableScreen.kt", "w") as f:
    f.write(content)
