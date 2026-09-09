with open("app/src/main/java/com/example/ui/screens/FacultyDashboardScreen.kt", "r") as f:
    content = f.read()

# Make sure AutoAwesome, Create, and Settings are imported
imports = """import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Settings
"""
content = content.replace("import androidx.compose.material.icons.filled.Add", imports + "import androidx.compose.material.icons.filled.Add")

with open("app/src/main/java/com/example/ui/screens/FacultyDashboardScreen.kt", "w") as f:
    f.write(content)

with open("app/src/main/java/com/example/ui/screens/ManageClassesScreen.kt", "r") as f:
    content2 = f.read()

content2 = content2.replace("import androidx.compose.material.icons.filled.Delete", "import androidx.compose.material.icons.filled.Delete\nimport androidx.compose.material.icons.filled.Edit")

with open("app/src/main/java/com/example/ui/screens/ManageClassesScreen.kt", "w") as f:
    f.write(content2)

