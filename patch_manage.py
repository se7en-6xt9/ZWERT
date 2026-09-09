with open("app/src/main/java/com/example/ui/screens/ManageClassesScreen.kt", "r") as f:
    content = f.read()

import re

manage_ui = """                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = course.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = course.code,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { navController.navigate("add_edit_batch?batchId=${course.id}") },
                                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(androidx.compose.material.icons.Icons.Default.Edit, contentDescription = "Edit Class")
                            }
                            IconButton(
                                onClick = { courseToDelete = course.id },
                                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Class")
                            }
                        }"""

content = re.sub(r'                        Row\(.*?                            IconButton\(\n                                onClick = \{ courseToDelete = course\.id \},.*?                            \}\n                        \}', manage_ui, content, flags=re.DOTALL)


# Also add a manual add button to the Manage Classes top app bar, or just let them use the FAB on dashboard.
with open("app/src/main/java/com/example/ui/screens/ManageClassesScreen.kt", "w") as f:
    f.write(content)
