package com.example.viewmodel

import android.app.Application
import android.content.Context
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

    private val _userProfile = MutableStateFlow<com.example.models.UserProfile?>(null)
    val userProfile: StateFlow<com.example.models.UserProfile?> = _userProfile.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(false)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    fun toggleDarkTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
    }

    fun setDarkTheme(enabled: Boolean) {
        _isDarkTheme.value = enabled
    }

    private var profileListener: com.google.firebase.firestore.ListenerRegistration? = null

    init {
        try {
            val prefs = getApplication<Application>().getSharedPreferences("app_profile_prefs", Context.MODE_PRIVATE)
            val savedName = prefs.getString("profile_name", null)
            if (!savedName.isNullOrBlank()) {
                _userProfile.value = com.example.models.UserProfile(
                    savedName,
                    prefs.getString("profile_subject", "Computer Science & Engineering") ?: "Computer Science & Engineering",
                    prefs.getString("profile_institute", "Department of CSE") ?: "Department of CSE"
                )
            }

            val currentAuth = auth
            if (currentAuth != null) {
                _authState.value = currentAuth.currentUser != null
                _currentUserEmail.value = currentAuth.currentUser?.email ?: ""
                
                if (currentAuth.currentUser != null) {
                    setupFirestoreListeners()
                }

                currentAuth.addAuthStateListener { firebaseAuth ->
                    val user = firebaseAuth.currentUser
                    _authState.value = user != null
                    _currentUserEmail.value = user?.email ?: ""
                    if (user != null) {
                        setupFirestoreListeners()
                    } else {
                        clearFirestoreListeners()
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e("MainViewModel", "Firebase auth initialization/listener failed", e)
        }
    }

    private fun setupFirestoreListeners() {
        val uid = auth?.currentUser?.uid ?: return
        val db = firestore ?: return
        
        profileListener?.remove()
        profileListener = db.collection("users").document(uid).collection("profile").document("info")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("Profile", "Listen failed.", e)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val profile = snapshot.toObject(com.example.models.UserProfile::class.java)
                    _userProfile.value = profile
                } else {
                    _userProfile.value = null
                }
            }
            
        syncDataFromFirebase()
    }
    
    private fun clearFirestoreListeners() {
        profileListener?.remove()
        profileListener = null
        _userProfile.value = null
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
                    
                    val batches = mutableListOf<com.example.data.BatchImport>()
                    for (doc in snapshot.documents) {
                        var batch = doc.toObject(com.example.data.BatchImport::class.java)?.copy(batchId = doc.id)
                        if (batch != null) {
                            // Fetch students subcollection
                            val studentsSnapshot = doc.reference.collection("students").get().await()
                            val students = studentsSnapshot.documents.mapNotNull { it.toObject(com.example.data.StudentImport::class.java) }
                            batch = batch.copy(students = students)
                            batches.add(batch)
                            
                            // Sync attendance for this batch
                            val attendanceSnapshot = doc.reference.collection("attendance").get().await()
                            val attendanceRecords = attendanceSnapshot.documents.mapNotNull { it.toObject(AttendanceRecordEntity::class.java) }
                            attendanceRecords.forEach { record ->
                                repository.saveAttendance(record)
                            }
                        }
                    }
                    
                    if (batches.isNotEmpty()) {
                        repository.wipeAllData() // Wipe old local data before sync
                        val importData = com.example.data.ImportTimetableData(teacher = null, batches = batches)
                        repository.processTimetableImport(importData)
                        Log.d("FirebaseSync", "Synced ${batches.size} batches from Firestore")
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

    fun signInAnonymously(onSuccess: (Boolean) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val currentAuth = auth
                if (currentAuth == null) {
                    onSuccess(false)
                    return@launch
                }
                currentAuth.signInAnonymously().await()
                
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
                
                // If it's a fresh anonymous account, we can optionally populate it with dummy data right away,
                // but let's just let them go to profile setup first!
                
                onSuccess(hasProfile)
            } catch (e: Throwable) {
                Log.w("Auth", "Anonymous sign-in failed (likely disabled). Falling back to local offline mode.")
                // Fallback to local offline mode for demo instead of erroring out
                onSuccess(false)
            }
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
                
                // Firestore listeners setup is triggered by addAuthStateListener
                
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
            } catch (e: Throwable) {
                Log.e("Auth", "Google sign-in failed", e)
                onError(e.message ?: "Authentication failed")
            }
        }
    }

    fun saveUserProfile(name: String, subject: String, institute: String, onComplete: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val prefs = getApplication<Application>().getSharedPreferences("app_profile_prefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("profile_name", name)
                    .putString("profile_subject", subject)
                    .putString("profile_institute", institute)
                    .putBoolean("has_profile", true)
                    .apply()

                _userProfile.value = com.example.models.UserProfile(name, subject, institute)
                
                val uid = auth?.currentUser?.uid
                if (uid != null && firestore != null) {
                    val profileData = hashMapOf(
                        "name" to name,
                        "subject" to subject,
                        "institute" to institute
                    )
                    firestore!!.collection("users").document(uid).collection("profile").document("info").set(profileData).await()
                } else {
                    Log.d("Profile", "Auth not available, saved profile locally in preferences.")
                }
                onComplete()
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
                val currentAuth = auth
                val currentFirestore = firestore
                val uid = currentAuth?.currentUser?.uid
                if (currentFirestore != null && uid != null) {
                    Log.d("FirebaseSync", "User UID: $uid")
                    Log.d("FirebaseSync", "Writing to path: users/$uid/batches")
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
                
                repository.wipeAllData()
                onComplete()
            } catch (e: Throwable) {
                Log.e("FirebaseSync", "Write failed: ${e.message}")
                onError("Failed to wipe data: ${e.message}")
            }
        }
    }

    fun initDemoProfileIfNeeded() {
        val prefs = getApplication<Application>().getSharedPreferences("app_profile_prefs", Context.MODE_PRIVATE)
        val savedName = prefs.getString("profile_name", null)
        if (!savedName.isNullOrBlank()) {
            _userProfile.value = com.example.models.UserProfile(
                savedName,
                prefs.getString("profile_subject", "Computer Science & Engineering") ?: "Computer Science & Engineering",
                prefs.getString("profile_institute", "Department of CSE") ?: "Department of CSE"
            )
        } else {
            // One-time creation of demo account profile
            val defaultName = "Prof. Yash Thakur"
            val defaultSubject = "Computer Science & Engineering"
            val defaultInstitute = "Department of CSE"
            prefs.edit()
                .putString("profile_name", defaultName)
                .putString("profile_subject", defaultSubject)
                .putString("profile_institute", defaultInstitute)
                .putBoolean("has_profile", true)
                .apply()
            _userProfile.value = com.example.models.UserProfile(defaultName, defaultSubject, defaultInstitute)
        }
    }

    fun loginAsDemoFaculty(onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                setRole(true)
                initDemoProfileIfNeeded()

                val slots = repository.getAllScheduleSlotsSync()
                if (slots.isEmpty()) {
                    loadDummyDataSuspend()
                }

                try {
                    auth?.signInAnonymously()?.await()
                } catch (e: Throwable) {
                    // Anonymous auth disabled or offline; ignore for demo mode
                }
            } catch (e: Throwable) {
                Log.e("DemoLogin", "Error logging into demo faculty", e)
            } finally {
                onComplete()
            }
        }
    }

    fun loginAsDemoStudent(onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                setRole(false)
                val slots = repository.getAllScheduleSlotsSync()
                if (slots.isEmpty()) {
                    loadDummyDataSuspend()
                }

                try {
                    auth?.signInAnonymously()?.await()
                } catch (e: Throwable) {
                    // Ignore for demo mode
                }
            } catch (e: Throwable) {
                Log.e("DemoLogin", "Error logging into demo student", e)
            } finally {
                onComplete()
            }
        }
    }

    suspend fun loadDummyDataSuspend() {
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

    fun loadDummyData() {
        viewModelScope.launch {
            loadDummyDataSuspend()
        }
    }

        fun parseTimetableJson(jsonString: String): com.example.data.ImportTimetableData? {
        return try {
            val moshi = com.squareup.moshi.Moshi.Builder().build()
            val adapter = moshi.adapter(com.example.data.ImportTimetableData::class.java)
            val parsed = adapter.fromJson(jsonString)
            android.util.Log.d("TimetableImport", "Parsed successfully. Found ${parsed?.batches?.size ?: 0} batches.")
            parsed
        } catch (e: Exception) {
            android.util.Log.e("TimetableImport", "JSON Parse failed: ${e.message}")
            null
        }
    }
    
    fun saveReviewedTimetable(data: com.example.data.ImportTimetableData, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                android.util.Log.d("TimetableImport", "Starting save for ${data.batches?.size ?: 0} batches.")
                val currentAuth = auth
                val currentFirestore = firestore
                val uid = currentAuth?.currentUser?.uid

                if (currentFirestore != null && uid != null) {
                    val batchesRef = currentFirestore.collection("users").document(uid).collection("batches")
                    data.batches?.forEach { batch ->
                        val docId = batch.batchId ?: java.util.UUID.randomUUID().toString()
                        val updatedBatch = batch.copy(batchId = docId)
                        val batchRef = batchesRef.document(docId)
                        
                        val batchMeta = hashMapOf(
                            "batchId" to docId,
                            "year" to updatedBatch.year,
                            "semester" to updatedBatch.semester,
                            "course" to updatedBatch.course,
                            "section" to updatedBatch.section,
                            "location" to updatedBatch.location,
                            "weeklySchedule" to updatedBatch.weeklySchedule
                        )
                        batchRef.set(batchMeta).await()
                        
                        val studentsRef = batchRef.collection("students")
                        updatedBatch.students?.forEach { student ->
                            val studentId = student.id ?: java.util.UUID.randomUUID().toString()
                            studentsRef.document(studentId).set(student.copy(id = studentId)).await()
                        }
                        android.util.Log.d("TimetableImport", "Saved batch $docId to Firestore.")
                    }
                }

                repository.processTimetableImport(data)
                android.util.Log.d("TimetableImport", "Local database sync complete.")
                onSuccess()
            } catch (e: Throwable) {
                android.util.Log.e("TimetableImport", "Write failed: ${e.message}", e)
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
                    currentFirestore.collection("users").document(uid)
                        .collection("batches").document(courseId).delete().await()
                }

                repository.dao.deleteCourseById(courseId)
                repository.dao.deleteStudentsByCourseId(courseId)
                repository.dao.deleteScheduleSlotsByCourseId(courseId)
                
                onComplete()
            } catch (e: Throwable) {
                android.util.Log.e("FirebaseSync", "Failed to delete course: ${e.message}")
                onError("Failed to delete class: ${e.message}")
            }
        }
    }


    suspend fun getBatchForEdit(courseId: String): com.example.data.BatchImport? {
        val course = repository.getCourseById(courseId) ?: return null
        val students = repository.getStudentsByCourseSync(courseId)
        val slots = repository.getScheduleSlotsForCourseSync(courseId)
        
        val studentImports = students.map { s ->
            com.example.data.StudentImport(id = s.id, name = s.name, rollNumber = s.rollNumber)
        }
        
        val scheduleImports = slots.map { slot ->
            val timeString = if (slot.endTime.isNotBlank()) "${slot.startTime} - ${slot.endTime}" else slot.startTime
            com.example.data.ScheduleImport(day = slot.dayOfWeek, time = timeString, location = slot.room)
        }
        
        val firstSlotSection = slots.firstOrNull()?.section ?: ""
        val firstSlotLoc = slots.firstOrNull()?.room ?: ""

        return com.example.data.BatchImport(
            batchId = course.id,
            year = "", // local db doesn't store this, but user can edit
            semester = "", // local db doesn't store this
            course = com.example.data.CourseImport(code = course.code, name = course.name),
            section = firstSlotSection,
            location = firstSlotLoc,
            weeklySchedule = scheduleImports,
            students = studentImports
        )
    }

    fun saveSingleBatch(batch: com.example.data.BatchImport, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val currentAuth = auth
                val currentFirestore = firestore
                val uid = currentAuth?.currentUser?.uid

                val docId = batch.batchId ?: java.util.UUID.randomUUID().toString()
                val updatedBatch = batch.copy(batchId = docId)

                if (currentFirestore != null && uid != null) {
                    val batchRef = currentFirestore.collection("users").document(uid).collection("batches").document(docId)
                    val batchMeta = hashMapOf(
                        "batchId" to docId,
                        "year" to updatedBatch.year,
                        "semester" to updatedBatch.semester,
                        "course" to updatedBatch.course,
                        "section" to updatedBatch.section,
                        "location" to updatedBatch.location,
                        "weeklySchedule" to updatedBatch.weeklySchedule
                    )
                    batchRef.set(batchMeta).await()
                    
                    val studentsRef = batchRef.collection("students")
                    updatedBatch.students?.forEach { student ->
                        val studentId = student.id ?: java.util.UUID.randomUUID().toString()
                        studentsRef.document(studentId).set(student.copy(id = studentId)).await()
                    }
                }

                repository.dao.deleteStudentsByCourseId(docId)
                repository.dao.deleteScheduleSlotsByCourseId(docId)
                
                val dummyData = com.example.data.ImportTimetableData(teacher = null, batches = listOf(updatedBatch))
                repository.processTimetableImport(dummyData)
                
                onSuccess()
            } catch (e: Throwable) {
                android.util.Log.e("ManualEntry", "Write failed: ${e.message}", e)
                onError("Failed to save class: ${e.message}")
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
    suspend fun getAllScheduleSlotsSync() = repository.getAllScheduleSlotsSync()
    fun getAttendanceForCourse(courseId: String) = repository.getAttendanceForCourse(courseId)
    
    fun markAttendance(date: String, slotId: String, studentId: String, status: String) {
        viewModelScope.launch {
            val uid = auth?.currentUser?.uid ?: return@launch
            val db = firestore ?: return@launch
            
            // Need batchId to nest correctly.
            val slot = repository.getScheduleSlotById(slotId) ?: return@launch
            val batchId = slot.courseId
            
            val attendanceRef = db.collection("users").document(uid)
                .collection("batches").document(batchId)
                .collection("attendance")
            
            if (status == "NONE") {
                repository.deleteAttendance(date, slotId, studentId)
                attendanceRef.document("${date}_${slotId}_$studentId").delete()
            } else {
                repository.deleteAttendance(date, slotId, studentId)
                val record = AttendanceRecordEntity(
                    date = date,
                    scheduleSlotId = slotId,
                    studentId = studentId,
                    status = status
                )
                repository.saveAttendance(record)
                attendanceRef.document("${date}_${slotId}_$studentId").set(record)
            }
        }
    }

    suspend fun getStudentAttendanceForCourse(studentId: String, courseId: String): List<AttendanceRecordEntity> {
        return emptyList()
    }
    
    fun markAllStudentsAttendance(date: String, slotId: String, studentIds: List<String>, status: String) {
        viewModelScope.launch {
            val uid = auth?.currentUser?.uid ?: return@launch
            val db = firestore ?: return@launch
            val batch = db.batch()
            
            val slot = repository.getScheduleSlotById(slotId) ?: return@launch
            val batchId = slot.courseId
            
            val attendanceRef = db.collection("users").document(uid)
                .collection("batches").document(batchId)
                .collection("attendance")
            
            studentIds.forEach { studentId ->
                val record = AttendanceRecordEntity(
                    date = date,
                    scheduleSlotId = slotId,
                    studentId = studentId,
                    status = status
                )
                repository.saveAttendance(record)
                batch.set(attendanceRef.document("${date}_${slotId}_$studentId"), record)
            }
            batch.commit()
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
                        if (profile != null) {
                            _userProfile.value = profile
                            onSuccess(profile)
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    Log.w("Profile", "Firestore load failed, checking local preferences")
                }
            }
            
            if (_userProfile.value != null) {
                onSuccess(_userProfile.value)
            } else {
                val prefs = getApplication<Application>().getSharedPreferences("app_profile_prefs", Context.MODE_PRIVATE)
                val savedName = prefs.getString("profile_name", null)
                if (!savedName.isNullOrBlank()) {
                    val profile = com.example.models.UserProfile(
                        savedName,
                        prefs.getString("profile_subject", "Computer Science & Engineering") ?: "Computer Science & Engineering",
                        prefs.getString("profile_institute", "Department of CSE") ?: "Department of CSE"
                    )
                    _userProfile.value = profile
                    onSuccess(profile)
                } else {
                    onSuccess(null)
                }
            }
        }
    }
}
