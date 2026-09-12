import re

with open("app/src/main/java/com/example/ui/screens/ManageClassesScreen.kt", "r") as f:
    content = f.read()

# Add FAB
if "floatingActionButton" not in content:
    content = content.replace("        topBar = {", "        floatingActionButton = {\n            FloatingActionButton(onClick = { navController.navigate(\"add_edit_batch\") }, containerColor = MaterialTheme.colorScheme.primary) {\n                Icon(androidx.compose.material.icons.Icons.Default.Add, contentDescription = \"Add Class\")\n            }\n        },\n        topBar = {")

# Ensure Icon import
if "import androidx.compose.material.icons.filled.Add" not in content:
    content = content.replace("import androidx.compose.material.icons.filled.Delete", "import androidx.compose.material.icons.filled.Add\nimport androidx.compose.material.icons.filled.Delete")

with open("app/src/main/java/com/example/ui/screens/ManageClassesScreen.kt", "w") as f:
    f.write(content)
