with open("app/src/main/java/com/example/ui/screens/FacultyDashboardScreen.kt", "r") as f:
    content = f.read()

import re

fab_menu = """        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + slideInVertically { 50 },
            exit = fadeOut() + slideOutVertically { 50 }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                SmallFloatingActionButton(
                    onClick = { navController.navigate("import_timetable") },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) {
                    Icon(Icons.Default.AutoAwesome, "AI Import")
                }
                SmallFloatingActionButton(
                    onClick = { navController.navigate("add_edit_batch") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.Create, "Manual Add")
                }
                SmallFloatingActionButton(
                    onClick = { navController.navigate("manage_classes") },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Icon(Icons.Default.Settings, "Manage Classes")
                }
            }
        }"""

content = re.sub(r'        AnimatedVisibility\(\n            visible = expanded.*?            \}\n        \}', fab_menu, content, flags=re.DOTALL)

with open("app/src/main/java/com/example/ui/screens/FacultyDashboardScreen.kt", "w") as f:
    f.write(content)
