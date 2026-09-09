with open("app/src/main/java/com/example/ui/screens/ImportTimetableScreen.kt", "r") as f:
    content = f.read()

patch = """
                            if (rawText.isBlank() && selectedBitmap == null) {
                                Toast.makeText(context, "Add text or an image", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            
                            val apiKey = com.example.BuildConfig.GEMINI_API_KEY
                            if (apiKey.isBlank()) {
                                Toast.makeText(context, "API Key missing! Add it in the Secrets panel.", Toast.LENGTH_LONG).show()
                                return@Button
                            }

                            isLoading = true
                            aiStatusText = "AI is thinking..."
                            coroutineScope.launch {
                                val aiResult = com.example.viewmodel.AiHelper.parseTimetableData(rawText, selectedBitmap, apiKey)
"""

content = content.replace("""                            if (rawText.isBlank() && selectedBitmap == null) {
                                Toast.makeText(context, "Add text or an image", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            
                            isLoading = true
                            aiStatusText = "AI is thinking..."
                            coroutineScope.launch {
                                val aiResult = com.example.viewmodel.AiHelper.parseTimetableData(rawText, selectedBitmap, com.example.BuildConfig.GEMINI_API_KEY)""", patch)

with open("app/src/main/java/com/example/ui/screens/ImportTimetableScreen.kt", "w") as f:
    f.write(content)
