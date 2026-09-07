package com.example

import android.app.Application
import android.content.Intent
import android.util.Log
import com.example.ui.screens.CrashActivity
import java.io.PrintWriter
import java.io.StringWriter

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            Log.e("AttentisCrash", "Uncaught exception in thread ${thread.name}", exception)
            
            val sw = StringWriter()
            exception.printStackTrace(PrintWriter(sw))
            val stackTrace = sw.toString()
            
            val intent = Intent(this, CrashActivity::class.java).apply {
                putExtra("EXTRA_STACK_TRACE", stackTrace)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            
            // Terminate the process so the OS knows it crashed, but after our activity launches
            // defaultHandler?.uncaughtException(thread, exception)
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(1)
        }
    }
}
