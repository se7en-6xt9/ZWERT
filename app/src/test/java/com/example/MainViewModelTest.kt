package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.viewmodel.MainViewModel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainViewModelTest {
    @Test
    fun testViewModelInstantiation() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        try {
            val viewModel = MainViewModel(app)
            println("ViewModel instantiated successfully!")
        } catch (e: Throwable) {
            e.printStackTrace()
            throw e
        }
    }
}
