package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.ui.screens.CrashActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import java.io.PrintWriter
import java.io.StringWriter

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // If this is the crash reporting process, do not perform full app initialization
        if (isCrashProcess()) {
            return
        }

        // Initialize Firebase with auto fallback
        initFirebaseSafely()

        // Set uncaught exception handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            Log.e("AttentisCrash", "Uncaught exception in thread ${thread.name}", exception)

            try {
                val sw = StringWriter()
                exception.printStackTrace(PrintWriter(sw))
                val stackTrace = sw.toString()

                val intent = Intent(this, CrashActivity::class.java).apply {
                    putExtra("EXTRA_STACK_TRACE", stackTrace)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.e("AttentisCrash", "Failed to launch CrashActivity", e)
            }

            // Small delay to allow the IPC launch of the separate :crash process
            try {
                Thread.sleep(500)
            } catch (_: InterruptedException) {}

            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(10)
        }
    }

    private fun isCrashProcess(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                getProcessName().endsWith(":crash")
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun initFirebaseSafely() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                // Try standard initialization (reads values generated from google-services.json)
                try {
                    FirebaseApp.initializeApp(this)
                } catch (e: Exception) {
                    Log.w("MyApplication", "Standard FirebaseApp init threw exception, trying fallback options", e)
                }
            }

            // If still empty (e.g. google-services.json was not present in build environment)
            if (FirebaseApp.getApps(this).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApplicationId("1:89592870844:android:81dd3dd72fa269ab245db9")
                    .setApiKey("AIzaSyCW2mwKGlghZp3ROvg30T-1j5PggWVfVa4")
                    .setProjectId("attentis-f0c3d")
                    .setStorageBucket("attentis-f0c3d.firebasestorage.app")
                    .setGcmSenderId("89592870844")
                    .build()
                FirebaseApp.initializeApp(this, options)
                Log.i("MyApplication", "FirebaseApp initialized via fallback FirebaseOptions")
            } else {
                Log.i("MyApplication", "FirebaseApp initialized successfully")
            }

            // Configure Firestore offline persistence with persistent cache
            try {
                val db = FirebaseFirestore.getInstance()
                val settings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(
                        PersistentCacheSettings.newBuilder()
                            .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                            .build()
                    )
                    .build()
                db.firestoreSettings = settings
                Log.i("MyApplication", "Firestore offline persistence enabled successfully with unlimited local cache.")
            } catch (e: Exception) {
                Log.w("MyApplication", "Firestore offline settings already set or error: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e("MyApplication", "Fatal error during Firebase initialization", e)
        }
    }
}
