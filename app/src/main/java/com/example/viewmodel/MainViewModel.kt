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
import com.example.models.CourseUpload
import com.example.models.StudentUpload
import com.example.models.ScheduleSlotUpload
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
                
                if (currentAuth.currentUser != null) {
                    syncDataFromFirebase()
                }

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

    fun syncDataFromFirebase(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val uid = auth?.currentUser?.uid
            if (uid != null && firestore != null) {
                try {
                    val batchesRef = firestore!!.collection("users").document(uid).collection("batches")
                    val snapshot = batchesRef.get().await()
                    
                    val batches = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(com.example.data.BatchImport::class.java)?.copy(batchId = doc.id)
                    }
                    
                    if (batches.isNotEmpty()) {
                        repository.wipeAllData() // Wipe old local data before sync
                        val importData = com.example.data.ImportTimetableData(teacher = null, batches = batches)
                        repository.processTimetableImport(importData)
                        Log.d("FirebaseSync", "Synced ${batches.size} batches from Firestore")
                        
                        // Sync attendance
                        val attendanceRef = firestore!!.collection("users").document(uid).collection("attendance")
                        val attSnapshot = attendanceRef.get().await()
                        val attendanceRecords = attSnapshot.documents.mapNotNull { doc ->
                            doc.toObject(AttendanceRecordEntity::class.java)
                        }
                        attendanceRecords.forEach { record ->
                            repository.saveAttendance(record)
                        }
                        Log.d("FirebaseSync", "Synced ${attendanceRecords.size} attendance records from Firestore")
                    } else {
                        Log.d("FirebaseSync", "No batches found on Firestore for this user")
                    }
                } catch (e: Exception) {
                    Log.e("FirebaseSync", "Sync failed: ${e.message}")
                }
            }
            onComplete()
        }
    }

    fun signInWithGoogleToken(idToken: String, onSuccess: (Boolean) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val currentAuth = auth
                if (currentAuth == null) {
                    onError("Firebase Auth is not available on this device.")
                    return@launch
                }
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                currentAuth.signInWithCredential(credential).await()
                syncDataFromFirebase {
                    viewModelScope.launch {
                        var hasProfile = false
                        val uid = currentAuth.currentUser?.uid
                        if (uid != null && firestore != null) {
                            try {
                                val doc = firestore!!.collection("users").document(uid).collection("profile").document("info").get().await()
                                hasProfile = doc.exists()
                            } catch (e: Exception) {
                                Log.e("Profile", "Error checking profile", e)
                            }
                        }
                        onSuccess(hasProfile)
                    }
                }
            } catch (e: Throwable) {
                Log.e("Auth", "Google sign-in failed", e)
                onError(e.message ?: "Authentication failed")
            }
        }
    }

    fun saveUserProfile(name: String, subject: String, institute: String, onComplete: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val uid = auth?.currentUser?.uid
                if (uid != null && firestore != null) {
                    val profileData = hashMapOf(
                        "name" to name,
                        "subject" to subject,
                        "institute" to institute
                    )
                    firestore!!.collection("users").document(uid).collection("profile").document("info").set(profileData).await()
                    onComplete()
                } else {
                    onError("Auth or Firestore not initialized")
                }
            } catch (e: Exception) {
                Log.e("Profile", "Error saving profile", e)
                onError(e.message ?: "Error saving profile")
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
        viewModelScope.launch {
            try {
                val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
                
                val courses = listOf(
                    CourseUpload("CSE-4SEM-A-DBMS", "Database Management Systems", "CS301", 4, listOf(
                        StudentUpload("S1", "Sakshi Sharma", "24BCS025"),
                        StudentUpload("S2", "Rahul Verma", "24BCS026"),
                        StudentUpload("S3", "Priya Singh", "24BCS027")
                    )),
                    CourseUpload("CSE-4SEM-B-DSA", "Data Structures & Algorithms", "CS302", 4, listOf(
                        StudentUpload("S4", "Amit Kumar", "24BCS028"),
                        StudentUpload("S5", "Neha Gupta", "24BCS029")
                    )),
                    CourseUpload("CSE-6SEM-A-OS", "Operating Systems", "CS303", 4, listOf(
                        StudentUpload("S6", "Vikram Singh", "24BCS030")
                    )),
                    CourseUpload("ECE-4SEM-A-CN", "Computer Networks", "CS304", 4, listOf(
                        StudentUpload("S7", "Pooja Patel", "24BCS031")
                    )),
                    CourseUpload("IT-5SEM-A-SE", "Software Engineering", "CS305", 4, listOf(
                        StudentUpload("S8", "Arjun Reddy", "24BCS032")
                    ))
                )

                val weeklySchedule = mutableListOf<ScheduleSlotUpload>()
                var slotIdCounter = 1

                for (day in days) {
                    weeklySchedule.add(ScheduleSlotUpload("slot_${slotIdCounter++}", "CSE-4SEM-A-DBMS", day, "09:00", "10:30", "Room 401", "A"))
                    weeklySchedule.add(ScheduleSlotUpload("slot_${slotIdCounter++}", "CSE-4SEM-B-DSA", day, "10:30", "11:30", "Lab 2", "B"))
                    // Break 11:30 - 12:00
                    weeklySchedule.add(ScheduleSlotUpload("slot_${slotIdCounter++}", "CSE-6SEM-A-OS", day, "12:00", "13:30", "Room 305", "A"))
                    // Break 13:30 - 14:30
                    weeklySchedule.add(ScheduleSlotUpload("slot_${slotIdCounter++}", "ECE-4SEM-A-CN", day, "14:30", "15:30", "Lab 1", "C"))
                    weeklySchedule.add(ScheduleSlotUpload("slot_${slotIdCounter++}", "IT-5SEM-A-SE", day, "15:30", "17:00", "Room 201", "A"))
                }
                
                val data = UploadData(courses, weeklySchedule)
                repository.processUploadData(data)
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

    fun deleteCourse(courseId: String, onComplete: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val currentAuth = auth
                val currentFirestore = firestore
                val uid = currentAuth?.currentUser?.uid

                if (currentFirestore != null && uid != null) {
                    Log.d("FirebaseSync", "Deleting course: $courseId from Firestore")
                    currentFirestore.collection("users").document(uid)
                        .collection("batches").document(courseId).delete().await()
                }

                // Delete locally
                repository.dao.deleteCourseById(courseId)
                repository.dao.deleteStudentsByCourseId(courseId)
                repository.dao.deleteScheduleSlotsByCourseId(courseId)
                
                onComplete()
            } catch (e: Throwable) {
                Log.e("FirebaseSync", "Failed to delete course: ${e.message}")
                onError("Failed to delete class: ${e.message}")
            }
        }
    }

    fun getAllCourses() = repository.dao.getAllCourses()
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
            val uid = auth?.currentUser?.uid
            val attendanceRef = if (firestore != null && uid != null) {
                firestore!!.collection("users").document(uid).collection("attendance")
            } else null
            
            if (status == "NONE") {
                repository.deleteAttendance(date, slotId, studentId)
                attendanceRef?.document("${date}_${slotId}_$studentId")?.delete()
            } else {
                repository.deleteAttendance(date, slotId, studentId)
                val record = AttendanceRecordEntity(
                    date = date,
                    scheduleSlotId = slotId,
                    studentId = studentId,
                    status = status
                )
                repository.saveAttendance(record)
                attendanceRef?.document("${date}_${slotId}_$studentId")?.set(record)
            }
        }
    }

    suspend fun getStudentAttendanceForCourse(studentId: String, courseId: String): List<AttendanceRecordEntity> {
        return emptyList()
    }
    
    fun markAllStudentsAttendance(date: String, slotId: String, studentIds: List<String>, status: String) {
        viewModelScope.launch {
            val uid = auth?.currentUser?.uid
            val batch = if (firestore != null && uid != null) firestore!!.batch() else null
            val attendanceRef = if (firestore != null && uid != null) {
                firestore!!.collection("users").document(uid).collection("attendance")
            } else null
            
            studentIds.forEach { studentId ->
                val record = AttendanceRecordEntity(
                    date = date,
                    scheduleSlotId = slotId,
                    studentId = studentId,
                    status = status
                )
                repository.saveAttendance(record)
                if (batch != null && attendanceRef != null) {
                    batch.set(attendanceRef.document("${date}_${slotId}_$studentId"), record)
                }
            }
            batch?.commit()
        }
    }

    fun loadUserProfile(onSuccess: (com.example.models.UserProfile?) -> Unit) {
        viewModelScope.launch {
            val uid = auth?.currentUser?.uid
            if (uid != null && firestore != null) {
                try {
                    val doc = firestore!!.collection("users").document(uid).collection("profile").document("info").get().await()
                    if (doc.exists()) {
                        val profile = doc.toObject(com.example.models.UserProfile::class.java)
                        onSuccess(profile)
                    } else {
                        onSuccess(null)
                    }
                } catch (e: Exception) {
                    onSuccess(null)
                }
            } else {
                onSuccess(null)
            }
        }
    }
}
