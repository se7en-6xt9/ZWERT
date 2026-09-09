with open("app/src/main/java/com/example/ui/screens/ImportTimetableScreen.kt", "r") as f:
    content = f.read()

target = """                                    // Sometimes AI returns markdown wrapped JSON
                                    val cleanJson = aiResult.replace("```json", "").replace("```", "").trim()
                                    viewModel.importTimetableFromJson(cleanJson,"""

replacement = """                                    // Aggressively clean JSON by finding the first { and last }
                                    var cleanJson = aiResult.replace("```json", "").replace("```", "").trim()
                                    val startIndex = cleanJson.indexOf('{')
                                    val endIndex = cleanJson.lastIndexOf('}')
                                    if (startIndex != -1 && endIndex != -1 && endIndex >= startIndex) {
                                        cleanJson = cleanJson.substring(startIndex, endIndex + 1)
                                    }
                                    
                                    viewModel.importTimetableFromJson(cleanJson,"""

content = content.replace(target, replacement)

with open("app/src/main/java/com/example/ui/screens/ImportTimetableScreen.kt", "w") as f:
    f.write(content)
