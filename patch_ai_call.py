with open("app/src/main/java/com/example/ui/screens/ImportTimetableScreen.kt", "r") as f:
    content = f.read()

import re

new_call = """                                var fileBytes: ByteArray? = null
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
                                val aiResult = com.example.viewmodel.AiHelper.parseTimetableData(
                                    rawText = rawText,
                                    image = selectedBitmap,
                                    apiKey = apiKey,
                                    fileBytes = fileBytes,
                                    fileMimeType = fileMime
                                )"""

content = content.replace("val aiResult = com.example.viewmodel.AiHelper.parseTimetableData(rawText, selectedBitmap, apiKey)", new_call)

with open("app/src/main/java/com/example/ui/screens/ImportTimetableScreen.kt", "w") as f:
    f.write(content)
