with open("app/src/main/java/com/example/ui/screens/ImportTimetableScreen.kt", "r") as f:
    content = f.read()

content = content.replace("@Composable\nfun ReviewImportData", "@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nfun ReviewImportData")

with open("app/src/main/java/com/example/ui/screens/ImportTimetableScreen.kt", "w") as f:
    f.write(content)
