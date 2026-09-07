package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.screens.FacultyDashboardScreen
import com.example.viewmodel.MainViewModel
import com.google.firebase.FirebaseApp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider

@RunWith(AndroidJUnit4::class)
class FacultyDashboardScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testFacultyDashboardScreen() {
        FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext())
        composeTestRule.setContent {
            val navController = rememberNavController()
            val viewModel = MainViewModel(ApplicationProvider.getApplicationContext())
            FacultyDashboardScreen(navController = navController, viewModel = viewModel)
        }
        composeTestRule.waitForIdle()
    }
}
