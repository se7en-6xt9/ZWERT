                        composable("attendance_report/{courseId}") { backStackEntry ->
                            val courseId = backStackEntry.arguments?.getString("courseId") ?: return@composable
                            com.example.ui.screens.AttendanceReportScreen(navController = navController, viewModel = viewModel, courseId = courseId)
                        }
