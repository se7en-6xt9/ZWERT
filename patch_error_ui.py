with open("app/src/main/java/com/example/ui/screens/ImportTimetableScreen.kt", "r") as f:
    content = f.read()

import re

catch_block = """                            var errorMessage = "AI failed to extract the data"
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
                                    android.widget.Toast.makeText(context, "AI output could not be parsed into schema.", android.widget.Toast.LENGTH_LONG).show()
                                }
                                isLoading = false
                            } else {
                                isLoading = false
                                android.widget.Toast.makeText(context, errorMessage, android.widget.Toast.LENGTH_LONG).show()
                            }"""

content = re.sub(r'                            val aiResult = try \{.*?                            \}', catch_block, content, flags=re.DOTALL)

with open("app/src/main/java/com/example/ui/screens/ImportTimetableScreen.kt", "w") as f:
    f.write(content)
