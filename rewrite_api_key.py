import re

with open("app/src/main/java/com/example/ui/screens/ImportTimetableScreen.kt", "r") as f:
    content = f.read()

# Remove apiKey state variable
content = re.sub(r'var apiKey by remember \{ mutableStateOf\("[^"]*"\) \}\n', '', content)

# Remove the OutlinedTextField for apiKey
api_key_field = r"""OutlinedTextField\(\s*value = apiKey,\s*onValueChange = \{ apiKey = it \},\s*label = \{ Text\("Gemini API Key"\) \},\s*modifier = Modifier.fillMaxWidth\(\),\s*shape = RoundedCornerShape\(12.dp\)\s*\)"""
content = re.sub(api_key_field, '', content)

# Remove API key check
api_key_check = r"""if \(apiKey.isBlank\(\)\) \{\s*Toast.makeText\(context, "API Key is required for AI", Toast.LENGTH_SHORT\).show\(\)\s*return@Button\s*\}"""
content = re.sub(api_key_check, '', content)

# Update AiHelper call to use BuildConfig
content = content.replace(
    'val aiResult = com.example.viewmodel.AiHelper.parseTimetableData(rawText, selectedBitmap, apiKey)',
    'val aiResult = com.example.viewmodel.AiHelper.parseTimetableData(rawText, selectedBitmap, com.example.BuildConfig.GEMINI_API_KEY)'
)

with open("app/src/main/java/com/example/ui/screens/ImportTimetableScreen.kt", "w") as f:
    f.write(content)
