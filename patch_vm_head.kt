package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.AppDatabase
import com.example.data.Repository
import com.example.data.CourseEntity
import com.example.data.ScheduleSlotEntity
import com.example.data.StudentEntity
import com.example.data.AttendanceRecordEntity
import com.example.models.UploadData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val repository: Repository
        get() {
            val userId = auth.currentUser?.uid ?: "default_user"
            val dao = AppDatabase.getDatabase(getApplication(), userId).appDao()
            return Repository(dao)
        }

    private val _isFaculty = MutableStateFlow(true)
    val isFaculty: StateFlow<Boolean> = _isFaculty.asStateFlow()

    private val _authState = MutableStateFlow(auth.currentUser != null)
    val authState: StateFlow<Boolean> = _authState.asStateFlow()

    private val _currentUserEmail = MutableStateFlow(auth.currentUser?.email ?: "")
    val currentUserEmail: StateFlow<String> = _currentUserEmail.asStateFlow()

    init {
        auth.addAuthStateListener { firebaseAuth ->
            _authState.value = firebaseAuth.currentUser != null
            _currentUserEmail.value = firebaseAuth.currentUser?.email ?: ""
        }
    }
