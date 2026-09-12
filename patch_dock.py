import re

with open("app/src/main/java/com/example/ui/screens/FacultyDashboardScreen.kt", "r") as f:
    content = f.read()

# Make pencil icon navigate to manage_classes
# And we can just remove the settings one or change it
# Actually, the user specifically mentioned pencil -> show list
# So we update the DockItemData

old_dock = """                        com.example.ui.components.DockItemData(
                            icon = Icons.Default.Create,
                            label = "Manual Add",
                            onClick = { navController.navigate("add_edit_batch") }
                        ),
                        com.example.ui.components.DockItemData(
                            icon = Icons.Default.Settings,
                            label = "Manage",
                            onClick = { navController.navigate("manage_classes") }
                        ),"""

new_dock = """                        com.example.ui.components.DockItemData(
                            icon = Icons.Default.Add,
                            label = "Add Class",
                            onClick = { navController.navigate("add_edit_batch") }
                        ),
                        com.example.ui.components.DockItemData(
                            icon = Icons.Default.Create,
                            label = "Edit Classes",
                            onClick = { navController.navigate("manage_classes") }
                        ),"""

content = content.replace(old_dock, new_dock)

with open("app/src/main/java/com/example/ui/screens/FacultyDashboardScreen.kt", "w") as f:
    f.write(content)
