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
    private val auth: FirebaseAuth? by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Throwable) {
            Log.e("MainViewModel", "FirebaseAuth.getInstance() failed", e)
            null
        }
    }
    private val firestore: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Throwable) {
            Log.e("MainViewModel", "FirebaseFirestore.getInstance() failed", e)
            null
        }
    }

    private val repository: Repository
        get() {
            val userId = try { auth?.currentUser?.uid ?: "default_user" } catch (e: Throwable) { "default_user" }
            val dao = AppDatabase.getDatabase(getApplication(), userId).appDao()
            return Repository(dao)
        }

    private val _isFaculty = MutableStateFlow(true)
    val isFaculty: StateFlow<Boolean> = _isFaculty.asStateFlow()

    private val _authState = MutableStateFlow(false)
    val authState: StateFlow<Boolean> = _authState.asStateFlow()

    private val _currentUserEmail = MutableStateFlow("")
    val currentUserEmail: StateFlow<String> = _currentUserEmail.asStateFlow()

    init {
        try {
            val currentAuth = auth
            if (currentAuth != null) {
                _authState.value = currentAuth.currentUser != null
                _currentUserEmail.value = currentAuth.currentUser?.email ?: ""
                currentAuth.addAuthStateListener { firebaseAuth ->
                    _authState.value = firebaseAuth.currentUser != null
                    _currentUserEmail.value = firebaseAuth.currentUser?.email ?: ""
                }
            }
        } catch (e: Throwable) {
            Log.e("MainViewModel", "Firebase auth initialization/listener failed", e)
        }
    }

    fun setRole(isFaculty: Boolean) {
        _isFaculty.value = isFaculty
    }
    
    fun wipeAllData() {
        viewModelScope.launch {
            repository.wipeAllData()
        }
    }

    fun signInWithGoogleToken(idToken: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val currentAuth = auth
                if (currentAuth == null) {
                    onError("Firebase Auth is not available on this device.")
                    return@launch
                }
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                currentAuth.signInWithCredential(credential).await()
                onSuccess()
            } catch (e: Throwable) {
                Log.e("Auth", "Google sign-in failed", e)
                onError(e.message ?: "Authentication failed")
            }
        }
    }

    fun signOut() {
        try {
            auth?.signOut()
        } catch (e: Throwable) {
            Log.e("Auth", "Sign out failed", e)
        }
    }
    
    fun wipeAllMyData(onComplete: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                // Wipe Firestore data (if any exists) for this user FIRST
                val currentAuth = auth
                val currentFirestore = firestore
                val uid = currentAuth?.currentUser?.uid
                if (currentFirestore != null && uid != null) {
                    Log.d("FirebaseSync", "User UID: $uid")
                    Log.d("FirebaseSync", "Writing to path: users/$uid/batches") // Using generic wording for deletion as well to match user log request
                    val collections = listOf("batches", "students", "attendance")
                    for (collection in collections) {
                        val ref = currentFirestore.collection("users").document(uid).collection(collection)
                        val snapshot = ref.get().await()
                        for (doc in snapshot.documents) {
                            doc.reference.delete().await()
                        }
                    }
                    Log.d("FirebaseSync", "Write success: true")
                }
                
                // Wipe local DB ONLY AFTER Firebase confirms deletion
                repository.wipeAllData()
                onComplete()
            } catch (e: Throwable) {
                Log.e("FirebaseSync", "Write failed: ${e.message}")
                onError("Failed to wipe data: ${e.message}")
            }
        }
    }
    
    fun loadDummyData() {
        val currentDay = LocalDate.now().dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
        val dummyJson = """
        {
          "courses": [
            {
              "id": "CS301",
              "name": "Database Management Systems",
              "code": "CS301",
              "credits": 4,
              "students": [
                { "id": "S1", "name": "Sakshi Sharma", "rollNumber": "24BCS025" },
                { "id": "S2", "name": "Rahul Verma", "rollNumber": "24BCS026" },
                { "id": "S3", "name": "Priya Singh", "rollNumber": "24BCS027" }
              ]
            },
            {
              "id": "CS302",
              "name": "Data Structures & Algorithms",
              "code": "CS302",
              "credits": 4,
              "students": [
                { "id": "S1", "name": "Sakshi Sharma", "rollNumber": "24BCS025" }
              ]
            }
          ],
          "weeklySchedule": [
            {
              "id": "CS301_1",
              "courseId": "CS301",
              "dayOfWeek": "$currentDay",
              "startTime": "09:00",
              "endTime": "10:30",
              "room": "Room 401",
              "section": "A"
            },
            {
              "id": "CS302_1",
              "courseId": "CS302",
              "dayOfWeek": "$currentDay",
              "startTime": "11:00",
              "endTime": "12:30",
              "room": "Lab 2",
              "section": "B"
            }
          ]
        }
        """.trimIndent()
        viewModelScope.launch {
            try {
                val moshi = Moshi.Builder().build()
                val adapter = moshi.adapter(UploadData::class.java)
                val data = adapter.fromJson(dummyJson)
                if (data != null) {
                    repository.processUploadData(data)
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    fun importTimetableFromJson(jsonString: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val moshi = Moshi.Builder().build()
                val adapter = moshi.adapter(com.example.data.ImportTimetableData::class.java)
                val data = adapter.fromJson(jsonString)
                if (data != null) {
                    val currentAuth = auth
                    val currentFirestore = firestore
                    val uid = currentAuth?.currentUser?.uid

                    if (currentFirestore != null && uid != null) {
                        Log.d("FirebaseSync", "User UID: $uid")
                        Log.d("FirebaseSync", "Writing to path: users/$uid/batches")
                        
                        val batchesRef = currentFirestore.collection("users").document(uid).collection("batches")
                        data.batches?.forEach { batch ->
                            val docId = batch.batchId ?: java.util.UUID.randomUUID().toString()
                            batchesRef.document(docId).set(batch).await()
                        }
                        
                        Log.d("FirebaseSync", "Write success: true")
                    }

                    // Proceed to save local state ONLY AFTER Firebase succeeds
                    repository.processTimetableImport(data)
                    onSuccess()
                } else {
                    onError("Failed to parse JSON. Please check the format.")
                }
            } catch (e: Throwable) {
                Log.e("FirebaseSync", "Write failed: ${e.message}")
                onError("Error parsing or syncing JSON: ${e.message}")
            }
        }
    }

    fun getScheduleForDay(day: String) = repository.getScheduleForDay(day)
    fun getTodayScheduleSync() = repository.getScheduleForDay(LocalDate.now().dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH))
    suspend fun getCourseById(courseId: String) = repository.getCourseById(courseId)
    suspend fun getScheduleSlotById(slotId: String) = repository.getScheduleSlotById(slotId)
    suspend fun getStudentsByCourseSync(courseId: String) = repository.getStudentsByCourseSync(courseId)
    fun getStudentsByCourse(courseId: String) = repository.getStudentsByCourse(courseId)
    
    fun getAttendanceForSession(date: String, slotId: String) = repository.getAttendanceForSession(date, slotId)
    fun getScheduleSlotsForCourse(courseId: String) = repository.getScheduleSlotsForCourse(courseId)
    fun getAttendanceForCourse(courseId: String) = repository.getAttendanceForCourse(courseId)
    
    fun markAttendance(date: String, slotId: String, studentId: String, status: String) {
        viewModelScope.launch {
            if (status == "NONE") {
                repository.deleteAttendance(date, slotId, studentId)
            } else {
                repository.deleteAttendance(date, slotId, studentId)
                repository.saveAttendance(AttendanceRecordEntity(
                    date = date,
                    scheduleSlotId = slotId,
                    studentId = studentId,
                    status = status
                ))
            }
        }
    }

    suspend fun getStudentAttendanceForCourse(studentId: String, courseId: String): List<AttendanceRecordEntity> {
        return emptyList()
    }
    
    fun markAllStudentsAttendance(date: String, slotId: String, studentIds: List<String>, status: String) {
        viewModelScope.launch {
            studentIds.forEach { studentId ->
                repository.saveAttendance(AttendanceRecordEntity(
                    date = date,
                    scheduleSlotId = slotId,
                    studentId = studentId,
                    status = status
                ))
            }
        }
    }
}
