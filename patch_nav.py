with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

import re

nav_patch = """                        composable("manage_classes") {
                            com.example.ui.screens.ManageClassesScreen(navController = navController, viewModel = viewModel)
                        }
                        composable("add_edit_batch?batchId={batchId}", arguments = listOf(androidx.navigation.navArgument("batchId") { nullable = true; defaultValue = null })) { backStackEntry ->
                            val batchId = backStackEntry.arguments?.getString("batchId")
                            com.example.ui.screens.AddEditBatchScreen(navController = navController, viewModel = viewModel, batchId = batchId)
                        }"""

content = content.replace("""                        composable("manage_classes") {
                            com.example.ui.screens.ManageClassesScreen(navController = navController, viewModel = viewModel)
                        }""", nav_patch)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)
