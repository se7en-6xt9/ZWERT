package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.FacultyDashboardScreen
import com.example.ui.screens.LectureViewScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.StudentDashboardScreen
import com.example.ui.theme.AppTheme
import com.example.viewmodel.MainViewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Firebase
        try {
            FirebaseApp.initializeApp(applicationContext)
            val db: FirebaseFirestore = FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        enableEdgeToEdge()
        setContent {
            AppTheme {
                val navController = rememberNavController()
                val snackbarHostState = remember { SnackbarHostState() }
                
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { innerPadding ->
                    NavHost(
                        navController = navController, 
                        startDestination = "login",
                        enterTransition = {
                            slideIntoContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                                animationSpec = tween(300)
                            ) + fadeIn(animationSpec = tween(300))
                        },
                        exitTransition = {
                            slideOutOfContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                                animationSpec = tween(300)
                            ) + fadeOut(animationSpec = tween(300))
                        },
                        popEnterTransition = {
                            slideIntoContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                                animationSpec = tween(300)
                            ) + fadeIn(animationSpec = tween(300))
                        },
                        popExitTransition = {
                            slideOutOfContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                                animationSpec = tween(300)
                            ) + fadeOut(animationSpec = tween(300))
                        }
                    ) {
                        composable("login") {
                            LoginScreen(navController = navController, viewModel = viewModel, snackbarHostState = snackbarHostState)
                        }
                        composable("faculty_dashboard") {
                            FacultyDashboardScreen(navController = navController, viewModel = viewModel)
                        }
                        composable("student_dashboard") {
                            StudentDashboardScreen(navController = navController, viewModel = viewModel)
                        }
                        composable("lecture_view/{slotId}") { backStackEntry ->
                            val slotId = backStackEntry.arguments?.getString("slotId") ?: return@composable
                            LectureViewScreen(navController = navController, viewModel = viewModel, slotId = slotId)
                        }
                        composable("profile") {
                            com.example.ui.screens.ProfileScreen(navController = navController, viewModel = viewModel)
                        }
                        composable("import_timetable") {
                            com.example.ui.screens.ImportTimetableScreen(navController = navController, viewModel = viewModel)
                        }
                        composable("attendance_report/{courseId}") { backStackEntry ->
                            val courseId = backStackEntry.arguments?.getString("courseId") ?: return@composable
                            com.example.ui.screens.AttendanceReportScreen(navController = navController, viewModel = viewModel, courseId = courseId)
                        }
                    }
                }
            }
        }
    }
}

