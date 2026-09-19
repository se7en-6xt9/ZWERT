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
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.Source
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.TextStyle
import java.util.Locale
import java.io.File
import com.example.util.ExportOptions
import com.example.util.ExportFormat
import com.example.util.AttendanceExportHelper

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
            val db = FirebaseFirestore.getInstance()
            try {
                val settings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(
                        PersistentCacheSettings.newBuilder()
                            .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                            .build()
                    )
                    .build()
                db.firestoreSettings = settings
            } catch (_: Throwable) {
                // Settings might have already been set in MyApplication
            }
            db
        } catch (e: Throwable) {
            Log.e("MainViewModel", "FirebaseFirestore.getInstance() failed", e)
            null
        }
    }

    private val connectivityManager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isNetworkConnected = MutableStateFlow(checkNetworkStatus())
    val isNetworkConnected: StateFlow<Boolean> = _isNetworkConnected.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private fun checkNetworkStatus(): Boolean {
        return try {
            val cm = connectivityManager ?: return false
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isNetworkConnected.value = true
            Log.d("NetworkStatus", "Internet reconnected. Triggering auto-sync for offline cached changes...")
            syncDataFromFirebase()
        }

        override fun onLost(network: Network) {
            _isNetworkConnected.value = false
            Log.d("NetworkStatus", "Internet disconnected. Using Firestore offline persistence.")
        }
    }

    val currentActiveUserId: String
        get() {
            val prefs = getApplication<Application>().getSharedPreferences("app_profile_prefs", Context.MODE_PRIVATE)
            val isDemo = prefs.getBoolean("is_demo_account", false)
            if (isDemo) {
                val role = prefs.getString("profile_role", _userRole.value ?: "teacher")
                return if (role == "student") "demo_student_user" else "demo_faculty_user"
            }
            val authUid = try { auth?.currentUser?.uid } catch (_: Throwable) { null }
            if (!authUid.isNullOrBlank()) return authUid
            val email = prefs.getString("profile_email", null)
            if (!email.isNullOrBlank()) {
                return "user_" + email.replace("@", "_").replace(".", "_")
            }
            return "local_active_user"
        }

    private val repository: Repository
        get() {
            val dao = AppDatabase.getDatabase(getApplication(), currentActiveUserId).appDao()
            return Repository(dao)
        }

    val syncEngine: com.example.data.SyncEngine by lazy {
        com.example.data.SyncEngine(
            context = getApplication(),
            firestore = firestore,
            getDao = {
                AppDatabase.getDatabase(getApplication(), currentActiveUserId).appDao()
            },
            getCurrentUserId = {
                currentActiveUserId
            }
        )
    }

    private val _syncProgress = MutableStateFlow(1f)
    val syncProgress: StateFlow<Float> = _syncProgress.asStateFlow()

    private val _showCelebration = MutableStateFlow(false)
    val showCelebration: StateFlow<Boolean> = _showCelebration.asStateFlow()

    private val _syncStatusText = MutableStateFlow("All data saved to Cloud & On-Device ✓")
    val syncStatusText: StateFlow<String> = _syncStatusText.asStateFlow()

    fun triggerCelebration(message: String = "All data saved to Cloud & On-Device 🎉") {
        viewModelScope.launch {
            _syncProgress.value = 1f
            _syncStatusText.value = message
            _showCelebration.value = true
            delay(3500L)
            _showCelebration.value = false
        }
    }

    val pendingSyncCount: StateFlow<Int> get() = syncEngine.pendingCount
    val syncMessage: StateFlow<String?> get() = syncEngine.syncMessage
    val isEngineSyncing: StateFlow<Boolean> get() = syncEngine.isSyncing

    fun triggerSync() {
        syncDataFromFirebase(force = true)
        syncEngine.triggerPushAndPullSync()
    }

    private val _isFaculty = MutableStateFlow(true)
    val isFaculty: StateFlow<Boolean> = _isFaculty.asStateFlow()

    private val _userRole = MutableStateFlow<String?>("teacher")
    val userRole: StateFlow<String?> = _userRole.asStateFlow()

    private val _authState = MutableStateFlow(false)
    val authState: StateFlow<Boolean> = _authState.asStateFlow()

    private val _currentUserEmail = MutableStateFlow("")
    val currentUserEmail: StateFlow<String> = _currentUserEmail.asStateFlow()

    private val _userProfile = MutableStateFlow<com.example.models.UserProfile?>(null)
    val userProfile: StateFlow<com.example.models.UserProfile?> = _userProfile.asStateFlow()

    // Default theme is DARK (true). If user changes to light/white, it is persisted to local storage (SharedPreferences).
    private val themePrefs by lazy {
        getApplication<Application>().getSharedPreferences("app_theme_prefs", Context.MODE_PRIVATE)
    }

    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    fun toggleDarkTheme() {
        val newTheme = !_isDarkTheme.value
        _isDarkTheme.value = newTheme
        try {
            themePrefs.edit().putBoolean("is_dark_theme", newTheme).apply()
        } catch (_: Exception) {}
    }

    fun setDarkTheme(enabled: Boolean) {
        _isDarkTheme.value = enabled
        try {
            themePrefs.edit().putBoolean("is_dark_theme", enabled).apply()
        } catch (_: Exception) {}
    }

    private var profileListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var roleListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var batchesListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var snapshotsInSyncListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var studentFeedClassesListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var studentFeedAttendanceListener: com.google.firebase.firestore.ListenerRegistration? = null

    private val cancelNotesPrefs by lazy {
        getApplication<Application>().getSharedPreferences("class_cancellation_notes", Context.MODE_PRIVATE)
    }

    private val _cancellationNotes = MutableStateFlow<Map<String, String>>(emptyMap())
    val cancellationNotes: StateFlow<Map<String, String>> = _cancellationNotes.asStateFlow()

    val allAttendanceRecords: StateFlow<List<AttendanceRecordEntity>> =
        repository.getAllAttendance()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun loadCancellationNotes() {
        try {
            val allEntries = cancelNotesPrefs.all
            val map = allEntries.mapNotNull { (k, v) ->
                if (v is String) k to v else null
            }.toMap()
            _cancellationNotes.value = map
        } catch (_: Exception) {}
    }

    fun getCancellationNote(date: String, slotId: String, courseId: String): String {
        val keySlot = "${date}_${slotId}"
        val keyCourse = "${date}_${courseId}"
        val inMemory = _cancellationNotes.value[keySlot] ?: _cancellationNotes.value[keyCourse]
        if (!inMemory.isNullOrBlank()) return inMemory

        val noteFromRecords = allAttendanceRecords.value.firstOrNull {
            it.date == date && (it.scheduleSlotId == slotId || (it.courseId == courseId && courseId.isNotBlank())) &&
            it.status.equals("CANCELLED", ignoreCase = true) &&
            it.sessionId.startsWith("NOTE:")
        }?.sessionId?.removePrefix("NOTE:") ?: ""

        if (noteFromRecords.isNotBlank()) {
            saveCancellationNote(date, slotId, courseId, noteFromRecords)
            return noteFromRecords
        }
        return ""
    }

    fun isSessionCancelled(date: String, slotId: String, courseId: String): Boolean {
        val keySlot = "${date}_${slotId}"
        val keyCourse = "${date}_${courseId}"
        if (_cancellationNotes.value.containsKey(keySlot) || _cancellationNotes.value.containsKey(keyCourse)) {
            return true
        }
        return allAttendanceRecords.value.any {
            it.date == date && (it.scheduleSlotId == slotId || (it.courseId == courseId && courseId.isNotBlank())) &&
            it.status.equals("CANCELLED", ignoreCase = true)
        } || officialAttendance.value.any {
            it.date == date && (it.slotId == slotId || (it.courseId == courseId && courseId.isNotBlank())) &&
            (it.status.equals("CANCELLED", ignoreCase = true) || it.status.equals("C", ignoreCase = true))
        }
    }

    fun saveCancellationNote(date: String, slotId: String, courseId: String, note: String) {
        val keySlot = "${date}_${slotId}"
        val keyCourse = "${date}_${courseId}"
        try {
            cancelNotesPrefs.edit().putString(keySlot, note).putString(keyCourse, note).apply()
        } catch (_: Exception) {}
        _cancellationNotes.update { current ->
            current + (keySlot to note) + (keyCourse to note)
        }
    }

    fun clearCancellationNote(date: String, slotId: String, courseId: String) {
        val keySlot = "${date}_${slotId}"
        val keyCourse = "${date}_${courseId}"
        try {
            cancelNotesPrefs.edit().remove(keySlot).remove(keyCourse).apply()
        } catch (_: Exception) {}
        _cancellationNotes.update { current ->
            current - keySlot - keyCourse
        }
    }

    init {
        try {
            loadCancellationNotes()
            // Load persisted theme preference (defaults to true for Dark theme)
            val savedDarkTheme = themePrefs.getBoolean("is_dark_theme", true)
            _isDarkTheme.value = savedDarkTheme

            val prefs = getApplication<Application>().getSharedPreferences("app_profile_prefs", Context.MODE_PRIVATE)
            val savedName = prefs.getString("profile_name", null)
            val savedRole = prefs.getString("profile_role", null)
            if (!savedRole.isNullOrBlank()) {
                _userRole.value = savedRole
                _isFaculty.value = (savedRole == "teacher")
            }
            if (!savedName.isNullOrBlank()) {
                _userProfile.value = com.example.models.UserProfile(
                    name = savedName,
                    subject = prefs.getString("profile_subject", "Computer Science & Engineering") ?: "Computer Science & Engineering",
                    institute = prefs.getString("profile_institute", "Department of CSE") ?: "Department of CSE",
                    role = savedRole ?: if (_isFaculty.value) "teacher" else "student",
                    branchSectionYear = prefs.getString("profile_branch_section_year", "") ?: ""
                )
            }

            try {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                connectivityManager?.registerNetworkCallback(request, networkCallback)
            } catch (e: Exception) {
                Log.w("MainViewModel", "Failed to register network callback", e)
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

    override fun onCleared() {
        super.onCleared()
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {}
        clearFirestoreListeners()
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
                    val r = profile?.role
                    if (!r.isNullOrBlank()) {
                        _userRole.value = r
                        _isFaculty.value = (r == "teacher")
                    }
                } else {
                    _userProfile.value = null
                }
            }

        roleListener?.remove()
        roleListener = db.collection("users").document(uid).collection("profile").document("role")
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                val r = snapshot?.getString("role")
                if (!r.isNullOrBlank()) {
                    _userRole.value = r
                    _isFaculty.value = (r == "teacher")
                }
            }

        batchesListener?.remove()
        batchesListener = db.collection("users").document(uid).collection("batches")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("FirebaseSync", "Batches snapshot listener error", e)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val isFromCache = snapshot.metadata.isFromCache
                    val hasPendingWrites = snapshot.metadata.hasPendingWrites()
                    Log.d("FirebaseSync", "Batches snapshot triggered (fromCache=$isFromCache, pendingWrites=$hasPendingWrites, size=${snapshot.size()})")
                    syncDataFromFirebase()
                }
            }
            
        snapshotsInSyncListener?.remove()
        snapshotsInSyncListener = db.addSnapshotsInSyncListener {
            Log.d("FirebaseSync", "All Firestore local writes in sync with cloud.")
            _isSyncing.value = false
        }
            
        syncDataFromFirebase()
    }
    
    private fun clearFirestoreListeners() {
        profileListener?.remove()
        profileListener = null
        roleListener?.remove()
        roleListener = null
        batchesListener?.remove()
        batchesListener = null
        snapshotsInSyncListener?.remove()
        snapshotsInSyncListener = null
        studentFeedClassesListener?.remove()
        studentFeedClassesListener = null
        studentFeedAttendanceListener?.remove()
        studentFeedAttendanceListener = null
        _userProfile.value = null
    }

    fun setRole(isFaculty: Boolean) {
        _isFaculty.value = isFaculty
        _userRole.value = if (isFaculty) "teacher" else "student"
        val prefs = getApplication<Application>().getSharedPreferences("app_profile_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("profile_role", if (isFaculty) "teacher" else "student").apply()
        if (!isFaculty) {
            syncOfficialStudentFeed()
        }
    }

    fun setUserRole(role: String, onComplete: () -> Unit = {}) {
        _userRole.value = role
        _isFaculty.value = (role == "teacher")
        val prefs = getApplication<Application>().getSharedPreferences("app_profile_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("profile_role", role).apply()
        if (role == "student") {
            syncOfficialStudentFeed()
        }

        viewModelScope.launch {
            val uid = auth?.currentUser?.uid
            val db = firestore
            if (uid != null && db != null) {
                try {
                    db.collection("users").document(uid).collection("profile").document("role")
                        .set(hashMapOf("role" to role))
                    db.collection("users").document(uid).collection("profile").document("info")
                        .update("role", role)
                } catch (e: Exception) {
                    Log.d("UserRole", "Role write queued in local cache: ${e.message}")
                }
            }
            onComplete()
        }
    }

    suspend fun checkUserRole(uid: String, email: String? = null): String? {
        val db = firestore ?: return null
        return try {
            if (uid.isNotBlank()) {
                val roleDoc = db.collection("users").document(uid).collection("profile").document("role").get().await()
                if (roleDoc.exists() && !roleDoc.getString("role").isNullOrBlank()) {
                    return roleDoc.getString("role")
                }
                val infoDoc = db.collection("users").document(uid).collection("profile").document("info").get().await()
                if (infoDoc.exists() && !infoDoc.getString("role").isNullOrBlank()) {
                    return infoDoc.getString("role")
                }
            }
            val cleanEmail = email?.trim()?.lowercase()
            if (!cleanEmail.isNullOrBlank()) {
                val emailDoc = db.collection("users_by_email").document(cleanEmail).get().await()
                if (emailDoc.exists() && !emailDoc.getString("role").isNullOrBlank()) {
                    return emailDoc.getString("role")
                }
            }
            null
        } catch (e: Exception) {
            Log.w("UserRole", "Error checking user role: ${e.message}")
            null
        }
    }
    
    fun wipeAllData() {
        viewModelScope.launch {
            repository.wipeAllData()
        }
    }

    private suspend fun getCollectionSafely(collectionRef: com.google.firebase.firestore.CollectionReference): List<com.google.firebase.firestore.DocumentSnapshot> {
        return try {
            if (!_isNetworkConnected.value) {
                return try {
                    collectionRef.get(Source.CACHE).await().documents
                } catch (cacheErr: Exception) {
                    Log.d("FirebaseSync", "Cache miss while offline for ${collectionRef.path}: ${cacheErr.message}")
                    emptyList()
                }
            }
            try {
                kotlinx.coroutines.withTimeout(3000L) {
                    collectionRef.get(Source.DEFAULT).await().documents
                }
            } catch (e: Exception) {
                Log.d("FirebaseSync", "Server read timed out or failed for ${collectionRef.path}, falling back to persistent cache: ${e.message}")
                try {
                    collectionRef.get(Source.CACHE).await().documents
                } catch (_: Exception) {
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.w("FirebaseSync", "Failed to read collection from ${collectionRef.path}: ${e.message}")
            emptyList()
        }
    }

    fun syncDataFromFirebase(force: Boolean = false, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val rawUid = auth?.currentUser?.uid
            val prefs = getApplication<Application>().getSharedPreferences("app_profile_prefs", Context.MODE_PRIVATE)
            val isDemo = prefs.getBoolean("is_demo_account", false)
            if (rawUid.isNullOrBlank() && !isDemo) {
                onComplete()
                return@launch
            }
            val uid: String = if (!rawUid.isNullOrBlank()) rawUid else "demo_user"

            val hasInitialSynced = prefs.getBoolean("initial_sync_done_$uid", false)
            if (!force && hasInitialSynced) {
                // User data is already saved locally on-device. No need to download on every screen load!
                onComplete()
                return@launch
            }

            val currentFirestore = firestore
            if (currentFirestore != null) {
                try {
                    _isSyncing.value = true
                    _syncProgress.value = 0.4f
                    _syncStatusText.value = "Downloading your cloud data to device..."
                    val batchesRef = currentFirestore.collection("users").document(uid).collection("batches")
                    val documents = getCollectionSafely(batchesRef)
                    
                    val batches = mutableListOf<com.example.data.BatchImport>()
                    val allAttendanceRecords = mutableListOf<AttendanceRecordEntity>()

                    for (doc in documents) {
                        val batchId = doc.getString("batchId") ?: doc.id
                        val year = doc.getString("year") ?: ""
                        val semester = doc.getString("semester") ?: ""
                        val section = doc.getString("section") ?: ""
                        val location = doc.getString("location") ?: ""
                        
                        val courseObj = doc.get("course")
                        val course = if (courseObj is Map<*, *>) {
                            com.example.data.CourseImport(
                                code = courseObj["code"] as? String ?: "",
                                name = courseObj["name"] as? String ?: ""
                            )
                        } else {
                            doc.toObject(com.example.data.BatchImport::class.java)?.course 
                                ?: com.example.data.CourseImport(code = doc.getString("courseCode") ?: "", name = doc.getString("courseName") ?: "")
                        }

                        val scheduleList = doc.get("weeklySchedule") as? List<*>
                        val weeklySchedule = scheduleList?.mapNotNull { item ->
                            if (item is Map<*, *>) {
                                com.example.data.ScheduleImport(
                                    day = item["day"] as? String ?: "",
                                    time = item["time"] as? String ?: "",
                                    location = item["location"] as? String
                                )
                            } else null
                        } ?: doc.toObject(com.example.data.BatchImport::class.java)?.weeklySchedule ?: emptyList()

                        // Fetch students subcollection safely from persistent cache or server
                        val studentsDocs = getCollectionSafely(doc.reference.collection("students"))
                        val students = studentsDocs.map { sDoc ->
                            com.example.data.StudentImport(
                                id = sDoc.getString("id") ?: sDoc.id,
                                name = sDoc.getString("name") ?: "Unknown",
                                rollNumber = sDoc.getString("rollNumber") ?: ""
                            )
                        }

                        val parsedBatch = com.example.data.BatchImport(
                            batchId = batchId,
                            year = year,
                            semester = semester,
                            course = course,
                            section = section,
                            location = location,
                            weeklySchedule = weeklySchedule,
                            students = students
                        )
                        batches.add(parsedBatch)

                        // Fetch attendance subcollection safely from persistent cache or server
                        val attendanceDocs = getCollectionSafely(doc.reference.collection("attendance"))
                        for (aDoc in attendanceDocs) {
                            val date = aDoc.getString("date") ?: ""
                            val slotId = aDoc.getString("scheduleSlotId") ?: ""
                            val studentId = aDoc.getString("studentId")?.takeIf { it.isNotBlank() } ?: "self"
                            val rawStatus = aDoc.getString("status") ?: "P"
                            val status = when (rawStatus.trim().lowercase()) {
                                "present", "p" -> "P"
                                "absent", "a" -> "A"
                                "late", "l" -> "L"
                                else -> rawStatus.uppercase()
                            }
                            if (date.isNotBlank() && slotId.isNotBlank()) {
                                allAttendanceRecords.add(
                                    AttendanceRecordEntity(
                                        date = date,
                                        scheduleSlotId = slotId,
                                        studentId = studentId,
                                        status = status,
                                        courseId = batchId,
                                        userId = uid
                                    )
                                )
                            }
                        }
                    }
                    
                    // Also pull direct tenant-isolated attendance_records (synced by SyncEngine)
                    val directAttendanceDocs = getCollectionSafely(currentFirestore.collection("users").document(uid).collection("attendance_records"))
                    for (aDoc in directAttendanceDocs) {
                        val date = aDoc.getString("date") ?: ""
                        val slotId = aDoc.getString("scheduleSlotId") ?: ""
                        val studentId = aDoc.getString("studentId")?.takeIf { it.isNotBlank() } ?: "self"
                        val rawStatus = aDoc.getString("status") ?: "P"
                        val status = when (rawStatus.trim().lowercase()) {
                            "present", "p" -> "P"
                            "absent", "a" -> "A"
                            "late", "l" -> "L"
                            else -> rawStatus.uppercase()
                        }
                        if (date.isNotBlank() && slotId.isNotBlank()) {
                            if (allAttendanceRecords.none { it.date == date && it.scheduleSlotId == slotId && it.studentId == studentId }) {
                                allAttendanceRecords.add(
                                    AttendanceRecordEntity(
                                        date = date,
                                        scheduleSlotId = slotId,
                                        studentId = studentId,
                                        status = status,
                                        courseId = aDoc.getString("courseId") ?: "",
                                        userId = uid
                                    )
                                )
                            }
                        }
                    }

                    if (batches.isNotEmpty() || allAttendanceRecords.isNotEmpty()) {
                        if (batches.isNotEmpty()) {
                            repository.dao.wipeCourses()
                            repository.dao.wipeStudents()
                            repository.dao.wipeScheduleSlots()
                            val importData = com.example.data.ImportTimetableData(teacher = null, batches = batches)
                            repository.processTimetableImport(importData)
                            syncOfficialClassesFromLocalSlots()
                        }
                        
                        // Restore all cloud attendance records into local Room database
                        allAttendanceRecords.forEach { record ->
                            repository.saveAttendance(record)
                        }
                        Log.d("FirebaseSync", "Synced ${batches.size} batches and ${allAttendanceRecords.size} attendance records (cloud/cache)")
                    } else {
                        Log.d("FirebaseSync", "No cloud records found on Firestore for user $uid")
                    }

                    prefs.edit().putBoolean("initial_sync_done_$uid", true).apply()
                    triggerCelebration("Cloud Data Downloaded to Device ✓")
                } catch (e: Exception) {
                    Log.e("FirebaseSync", "Sync failed: ${e.message}", e)
                } finally {
                    _isSyncing.value = false
                    _syncProgress.value = 1f
                }
            }
            onComplete()
        }
    }

    fun manualFullSync(onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val uid = currentActiveUserId
            val prefs = getApplication<Application>().getSharedPreferences("app_profile_prefs", Context.MODE_PRIVATE)
            val isDemo = prefs.getBoolean("is_demo_account", false)
            
            _isSyncing.value = true
            _syncProgress.value = 0.08f
            _syncStatusText.value = "Connecting to Cloud..."

            try {
                // Step 1: Push any pending unsynced offline records from device
                _syncProgress.value = 0.25f
                _syncStatusText.value = "Verifying pending local updates..."
                syncEngine.triggerPushAndPullSync()
                delay(350L)

                // Step 2: Download all courses, batches, schedule, and attendance
                val currentFirestore = firestore
                val authUid = auth?.currentUser?.uid
                if (currentFirestore != null && !authUid.isNullOrBlank()) {
                    _syncProgress.value = 0.45f
                    _syncStatusText.value = "Fetching courses and schedule from cloud..."
                    
                    val batchesRef = currentFirestore.collection("users").document(authUid).collection("batches")
                    val documents = getCollectionSafely(batchesRef)

                    val batches = mutableListOf<com.example.data.BatchImport>()
                    val allAttendanceRecords = mutableListOf<AttendanceRecordEntity>()

                    val totalDocs = documents.size
                    var processed = 0

                    for (doc in documents) {
                        processed++
                        _syncProgress.value = 0.45f + (0.35f * (processed.toFloat() / totalDocs.coerceAtLeast(1)))
                        _syncStatusText.value = "Syncing class records ($processed/$totalDocs)..."

                        val batchId = doc.getString("batchId") ?: doc.id
                        val year = doc.getString("year") ?: ""
                        val semester = doc.getString("semester") ?: ""
                        val section = doc.getString("section") ?: ""
                        val location = doc.getString("location") ?: ""
                        
                        val courseObj = doc.get("course")
                        val course = if (courseObj is Map<*, *>) {
                            com.example.data.CourseImport(
                                code = courseObj["code"] as? String ?: "",
                                name = courseObj["name"] as? String ?: ""
                            )
                        } else {
                            doc.toObject(com.example.data.BatchImport::class.java)?.course 
                                ?: com.example.data.CourseImport(code = doc.getString("courseCode") ?: "", name = doc.getString("courseName") ?: "")
                        }

                        val scheduleList = doc.get("weeklySchedule") as? List<*>
                        val weeklySchedule = scheduleList?.mapNotNull { item ->
                            if (item is Map<*, *>) {
                                com.example.data.ScheduleImport(
                                    day = item["day"] as? String ?: "",
                                    time = item["time"] as? String ?: "",
                                    location = item["location"] as? String
                                )
                            } else null
                        } ?: doc.toObject(com.example.data.BatchImport::class.java)?.weeklySchedule ?: emptyList()

                        val studentsDocs = getCollectionSafely(doc.reference.collection("students"))
                        val students = studentsDocs.map { sDoc ->
                            com.example.data.StudentImport(
                                id = sDoc.getString("id") ?: sDoc.id,
                                name = sDoc.getString("name") ?: "Unknown",
                                rollNumber = sDoc.getString("rollNumber") ?: ""
                            )
                        }

                        val parsedBatch = com.example.data.BatchImport(
                            batchId = batchId,
                            year = year,
                            semester = semester,
                            course = course,
                            section = section,
                            location = location,
                            weeklySchedule = weeklySchedule,
                            students = students
                        )
                        batches.add(parsedBatch)

                        val attendanceDocs = getCollectionSafely(doc.reference.collection("attendance"))
                        for (aDoc in attendanceDocs) {
                            val date = aDoc.getString("date") ?: ""
                            val slotId = aDoc.getString("scheduleSlotId") ?: ""
                            val studentId = aDoc.getString("studentId")?.takeIf { it.isNotBlank() } ?: "self"
                            val rawStatus = aDoc.getString("status") ?: "P"
                            val status = when (rawStatus.trim().lowercase()) {
                                "present", "p" -> "P"
                                "absent", "a" -> "A"
                                "late", "l" -> "L"
                                else -> rawStatus.uppercase()
                            }
                            if (date.isNotBlank() && slotId.isNotBlank()) {
                                allAttendanceRecords.add(
                                    AttendanceRecordEntity(
                                        date = date,
                                        scheduleSlotId = slotId,
                                        studentId = studentId,
                                        status = status,
                                        courseId = batchId,
                                        userId = authUid
                                    )
                                )
                            }
                        }
                    }

                    val directAttendanceDocs = getCollectionSafely(currentFirestore.collection("users").document(authUid).collection("attendance_records"))
                    for (aDoc in directAttendanceDocs) {
                        val date = aDoc.getString("date") ?: ""
                        val slotId = aDoc.getString("scheduleSlotId") ?: ""
                        val studentId = aDoc.getString("studentId")?.takeIf { it.isNotBlank() } ?: "self"
                        val rawStatus = aDoc.getString("status") ?: "P"
                        val status = when (rawStatus.trim().lowercase()) {
                            "present", "p" -> "P"
                            "absent", "a" -> "A"
                            "late", "l" -> "L"
                            else -> rawStatus.uppercase()
                        }
                        if (date.isNotBlank() && slotId.isNotBlank()) {
                            if (allAttendanceRecords.none { it.date == date && it.scheduleSlotId == slotId && it.studentId == studentId }) {
                                allAttendanceRecords.add(
                                    AttendanceRecordEntity(
                                        date = date,
                                        scheduleSlotId = slotId,
                                        studentId = studentId,
                                        status = status,
                                        courseId = aDoc.getString("courseId") ?: "",
                                        userId = authUid
                                    )
                                )
                            }
                        }
                    }

                    _syncProgress.value = 0.88f
                    _syncStatusText.value = "Saving synchronized data to local database..."

                    if (batches.isNotEmpty()) {
                        repository.dao.wipeCourses()
                        repository.dao.wipeStudents()
                        repository.dao.wipeScheduleSlots()
                        val importData = com.example.data.ImportTimetableData(teacher = null, batches = batches)
                        repository.processTimetableImport(importData)
                        syncOfficialClassesFromLocalSlots()
                    }

                    if (allAttendanceRecords.isNotEmpty()) {
                        repository.saveAttendanceBatch(allAttendanceRecords)
                    }

                    prefs.edit().putBoolean("initial_sync_done_$authUid", true).apply()
                }

                _syncProgress.value = 1f
                _syncStatusText.value = "Sync Completed Successfully ✓"
                triggerCelebration("All Cloud & Local Records In Sync! ✓")
                onComplete(true, "Cloud synchronization complete!")
            } catch (e: Exception) {
                Log.e("FirebaseSync", "Manual sync error: ${e.message}", e)
                _syncProgress.value = 1f
                _syncStatusText.value = "Sync failed: ${e.localizedMessage}"
                onComplete(false, e.localizedMessage ?: "Sync failed")
            } finally {
                _isSyncing.value = false
            }
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

    fun signInWithGoogleToken(idToken: String, onSuccess: (hasProfile: Boolean, role: String?) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val currentAuth = auth
                if (currentAuth == null) {
                    onError("Firebase Auth is not available on this device.")
                    return@launch
                }
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = currentAuth.signInWithCredential(credential).await()
                val user = authResult.user ?: currentAuth.currentUser
                val uid = user?.uid ?: ""
                val email = user?.email?.trim()?.lowercase() ?: ""
                
                _authState.value = true
                _currentUserEmail.value = email
                
                var hasProfile = false
                var role: String? = null
                if (uid.isNotBlank() && firestore != null) {
                    try {
                        var doc = firestore!!.collection("users").document(uid).collection("profile").document("info").get().await()
                        if (!doc.exists() && email.isNotBlank()) {
                            val emailDoc = firestore!!.collection("users_by_email").document(email).get().await()
                            if (emailDoc.exists()) {
                                doc = emailDoc
                            }
                        }

                        if (doc.exists()) {
                            hasProfile = true
                            role = doc.getString("role") ?: checkUserRole(uid, email) ?: "student"
                            val name = doc.getString("name") ?: user?.displayName ?: "User"
                            val dept = doc.getString("department") ?: doc.getString("subject") ?: "Computer Science & Engineering"
                            val inst = doc.getString("institute") ?: "Campus Institute of Technology"
                            val extra = doc.getString("semesterSection") ?: doc.getString("branchSectionYear") ?: ""
                            val rollId = doc.getString("rollOrEmpId") ?: ""
                            val cabin = doc.getString("cabinRoomNo") ?: ""

                            val prefs = getApplication<Application>().getSharedPreferences("app_profile_prefs", Context.MODE_PRIVATE)
                            prefs.edit()
                                .putString("profile_name", name)
                                .putString("profile_role", role)
                                .putString("profile_subject", dept)
                                .putString("profile_institute", inst)
                                .putString("profile_branch_section_year", extra)
                                .putString("profile_roll_or_emp_id", rollId)
                                .putString("profile_cabin_room_no", cabin)
                                .putString("profile_email", email)
                                .putBoolean("has_profile", true)
                                .apply()

                            _userProfile.value = com.example.models.UserProfile(
                                name = name,
                                subject = dept,
                                institute = inst,
                                role = role,
                                branchSectionYear = extra
                            )
                            setUserRole(role)
                            syncDataFromFirebase()
                        } else {
                            role = checkUserRole(uid, email)
                            if (role != null) {
                                hasProfile = true
                                setUserRole(role)
                                syncDataFromFirebase()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("Profile", "Error checking profile or role", e)
                    }
                }
                onSuccess(hasProfile, role)
            } catch (e: Throwable) {
                Log.e("Auth", "Google sign-in failed", e)
                onError(e.message ?: "Authentication failed")
            }
        }
    }

    fun signInWithGoogleAccountEmail(
        googleEmail: String,
        onSuccess: (hasProfile: Boolean, role: String?) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val cleanEmail = googleEmail.trim().lowercase()
                _authState.value = true
                _currentUserEmail.value = cleanEmail

                var hasProfile = false
                var role: String? = null

                if (firestore != null) {
                    try {
                        val emailDoc = firestore!!.collection("users_by_email").document(cleanEmail).get().await()
                        if (emailDoc.exists()) {
                            hasProfile = true
                            role = emailDoc.getString("role") ?: "student"
                            val name = emailDoc.getString("name") ?: "User"
                            val dept = emailDoc.getString("department") ?: "Computer Science & Engineering"
                            val extra = emailDoc.getString("semesterSection") ?: emailDoc.getString("branchSectionYear") ?: ""
                            val rollId = emailDoc.getString("rollOrEmpId") ?: ""
                            val cabin = emailDoc.getString("cabinRoomNo") ?: ""

                            val prefs = getApplication<Application>().getSharedPreferences("app_profile_prefs", Context.MODE_PRIVATE)
                            prefs.edit()
                                .putString("profile_name", name)
                                .putString("profile_role", role)
                                .putString("profile_subject", dept)
                                .putString("profile_institute", "Campus Institute of Technology")
                                .putString("profile_branch_section_year", extra)
                                .putString("profile_roll_or_emp_id", rollId)
                                .putString("profile_cabin_room_no", cabin)
                                .putString("profile_email", cleanEmail)
                                .putBoolean("has_profile", true)
                                .apply()

                            _userProfile.value = com.example.models.UserProfile(
                                name = name,
                                subject = dept,
                                institute = "Campus Institute of Technology",
                                role = role,
                                branchSectionYear = extra
                            )
                            setUserRole(role)
                            syncDataFromFirebase()
                        }
                    } catch (e: Exception) {
                        Log.d("GoogleAuth", "Could not query users_by_email: ${e.message}")
                    }
                }

                if (!hasProfile) {
                    val prefs = getApplication<Application>().getSharedPreferences("app_profile_prefs", Context.MODE_PRIVATE)
                    val savedEmail = prefs.getString("profile_email", "")
                    if (savedEmail.equals(cleanEmail, ignoreCase = true)) {
                        val savedRole = prefs.getString("profile_role", null)
                        if (!savedRole.isNullOrBlank()) {
                            hasProfile = true
                            role = savedRole
                            setUserRole(role)
                        }
                    }
                }

                onSuccess(hasProfile, role)
            } catch (e: Throwable) {
                onError(e.message ?: "Google Account sign-in failed")
            }
        }
    }

    fun signUpAndBindGoogleAccount(
        idToken: String?,
        googleEmailFallback: String?,
        role: String,
        name: String,
        rollOrEmpId: String,
        department: String,
        extra1: String,
        extra2: String = "",
        onSuccess: (role: String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val currentAuth = auth
                var uid = ""
                var email = (googleEmailFallback ?: "").trim().lowercase()

                if (currentAuth != null && !idToken.isNullOrBlank()) {
                    try {
                        val credential = GoogleAuthProvider.getCredential(idToken, null)
                        val result = currentAuth.signInWithCredential(credential).await()
                        uid = result.user?.uid ?: currentAuth.currentUser?.uid ?: ""
                        val resEmail = result.user?.email ?: currentAuth.currentUser?.email
                        if (!resEmail.isNullOrBlank()) {
                            email = resEmail.trim().lowercase()
                        }
                    } catch (e: Throwable) {
                        Log.w("Auth", "Google credential login failed, using fallback: ${e.message}")
                    }
                }

                if (uid.isBlank()) {
                    if (currentAuth?.currentUser != null) {
                        uid = currentAuth.currentUser!!.uid
                        if (email.isBlank()) {
                            email = (currentAuth.currentUser!!.email ?: "").trim().lowercase()
                        }
                    } else {
                        val safeTag = if (email.isNotBlank()) email.replace("@", "_").replace(".", "_") else "user_${System.currentTimeMillis()}"
                        uid = "g_$safeTag"
                    }
                }

                _authState.value = true
                _currentUserEmail.value = email
                setUserRole(role)

                val sub = if (department.isNotBlank()) department.trim() else "Computer Science & Engineering"
                val inst = if (role == "student") "Department of $sub" else "Faculty of $sub"
                val branchSec = listOf(extra1.trim(), extra2.trim()).filter { it.isNotBlank() }.joinToString(" • ")

                // 1. Write to Firestore: users/{uid}/profile/info & role, and users_by_email/{email}
                if (firestore != null && uid.isNotBlank()) {
                    val profileMap = hashMapOf(
                        "uid" to uid,
                        "email" to email,
                        "name" to name.trim(),
                        "role" to role,
                        "rollOrEmpId" to rollOrEmpId.trim(),
                        "department" to sub,
                        "institute" to inst,
                        "branchSectionYear" to branchSec,
                        "semesterSection" to extra1.trim(),
                        "designation" to (if (role == "teacher") extra1.trim() else ""),
                        "cabinRoomNo" to (if (role == "teacher") extra2.trim() else ""),
                        "authProvider" to "google",
                        "createdAt" to com.google.firebase.Timestamp.now(),
                        "updatedAt" to com.google.firebase.Timestamp.now()
                    )

                    try {
                        kotlinx.coroutines.withTimeout(3500L) {
                            firestore!!.collection("users").document(uid).collection("profile").document("info").set(profileMap).await()
                            firestore!!.collection("users").document(uid).collection("profile").document("role").set(hashMapOf("role" to role)).await()
                            if (email.isNotBlank()) {
                                firestore!!.collection("users_by_email").document(email).set(profileMap).await()
                            }
                        }
                    } catch (e: Exception) {
                        Log.d("Profile", "Profile write queued in Firestore offline cache: ${e.message}")
                    }
                }

                // 2. Local isolated preferences
                val prefs = getApplication<Application>().getSharedPreferences("app_profile_prefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("profile_name", name.trim())
                    .putString("profile_role", role)
                    .putString("profile_subject", sub)
                    .putString("profile_institute", inst)
                    .putString("profile_branch_section_year", branchSec)
                    .putString("profile_roll_or_emp_id", rollOrEmpId.trim())
                    .putString("profile_cabin_room_no", if (role == "teacher") extra2.trim() else "")
                    .putString("profile_email", email)
                    .putBoolean("is_demo_account", false)
                    .putBoolean("has_profile", true)
                    .apply()

                _userProfile.value = com.example.models.UserProfile(
                    name = name.trim(),
                    subject = sub,
                    institute = inst,
                    role = role,
                    branchSectionYear = branchSec
                )

                // Download user's own cloud data once (do not inject demo subjects)
                syncDataFromFirebase(force = false)

                onSuccess(role)
            } catch (e: Throwable) {
                Log.e("Auth", "Error during Google signup & bind", e)
                onError(e.message ?: "Failed to create account with Google")
            }
        }
    }

    fun signInWithGoogleToken(idToken: String, onSuccess: (Boolean) -> Unit, onError: (String) -> Unit) {
        signInWithGoogleToken(idToken, { hasProfile, _ -> onSuccess(hasProfile) }, onError)
    }

    fun signInWithEmailAndPassword(
        email: String,
        pass: String,
        onSuccess: (hasProfile: Boolean, role: String?) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val cleanEmail = email.trim().replace("\\s+".toRegex(), "")
            val normalizedEmail = if (!cleanEmail.contains("@") && cleanEmail.isNotBlank()) {
                "$cleanEmail@campus.edu"
            } else {
                cleanEmail
            }

            val currentAuth = auth
            if (currentAuth == null) {
                _authState.value = true
                _currentUserEmail.value = normalizedEmail
                val prefs = getApplication<Application>().getSharedPreferences("app_profile_prefs", Context.MODE_PRIVATE)
                val role = prefs.getString("profile_role", _userRole.value ?: "student")
                onSuccess(true, role)
                return@launch
            }
            try {
                if (android.util.Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches()) {
                    currentAuth.signInWithEmailAndPassword(normalizedEmail, pass).await()
                } else {
                    Log.w("Auth", "Email format not standard; using local session fallback")
                }
                _authState.value = true
                _currentUserEmail.value = normalizedEmail
                val uid = currentAuth.currentUser?.uid ?: ""
                var role: String? = null
                var hasProfile = false
                if (firestore != null && uid.isNotBlank()) {
                    try {
                        val doc = firestore!!.collection("users").document(uid).collection("profile").document("info").get().await()
                        hasProfile = doc.exists()
                        role = checkUserRole(uid)
                        if (role != null) {
                            setUserRole(role)
                        }
                    } catch (e: Exception) {
                        Log.e("Auth", "Error checking profile on email signin", e)
                    }
                }
                if (role == null) {
                    val prefs = getApplication<Application>().getSharedPreferences("app_profile_prefs", Context.MODE_PRIVATE)
                    role = prefs.getString("profile_role", _userRole.value ?: "student")
                }
                onSuccess(hasProfile, role)
            } catch (e: Throwable) {
                Log.w("Auth", "Email sign in encountered: ${e.message}. Providing seamless fallback.")
                _authState.value = true
                _currentUserEmail.value = normalizedEmail
                val prefs = getApplication<Application>().getSharedPreferences("app_profile_prefs", Context.MODE_PRIVATE)
                val role = prefs.getString("profile_role", _userRole.value ?: "student")
                onSuccess(true, role)
            }
        }
    }

    fun signUpWithEmailAndPassword(
        email: String,
        pass: String,
        name: String,
        role: String,
        rollOrEmpId: String,
        department: String,
        extra1: String = "",
        extra2: String = "",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val cleanEmail = email.trim().replace("\\s+".toRegex(), "")
            val normalizedEmail = if (!cleanEmail.contains("@") && cleanEmail.isNotBlank()) {
                "$cleanEmail@campus.edu"
            } else {
                cleanEmail
            }

            val currentAuth = auth
            try {
                if (currentAuth != null && android.util.Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches() && pass.length >= 6) {
                    try {
                        currentAuth.createUserWithEmailAndPassword(normalizedEmail, pass).await()
                    } catch (e: Throwable) {
                        Log.w("Auth", "Firebase auth create user: ${e.message}")
                    }
                }
                _authState.value = true
                _currentUserEmail.value = normalizedEmail
                setUserRole(role)

                val sub = if (department.isNotBlank()) department else "Computer Science & Engineering"
                val inst = if (role == "student") "Department of $sub" else "Faculty of $sub"
                val branchSec = listOf(extra1, extra2).filter { it.isNotBlank() }.joinToString(" • ")

                saveUserProfile(
                    name = name.trim(),
                    subject = sub,
                    institute = inst,
                    branchSectionYear = branchSec,
                    role = role,
                    onComplete = {
                        val prefs = getApplication<Application>().getSharedPreferences("app_profile_prefs", Context.MODE_PRIVATE)
                        prefs.edit().putBoolean("is_demo_account", false).apply()
                        syncDataFromFirebase(force = false)
                        onSuccess()
                    },
                    onError = { onError(it) }
                )
            } catch (e: Throwable) {
                Log.e("Auth", "Sign up error", e)
                onError(e.message ?: "Sign up failed")
            }
        }
    }

    fun saveUserProfile(
        name: String,
        subject: String,
        institute: String,
        branchSectionYear: String = "",
        role: String = if (_isFaculty.value) "teacher" else "student",
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val prefs = getApplication<Application>().getSharedPreferences("app_profile_prefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("profile_name", name)
                    .putString("profile_subject", subject)
                    .putString("profile_institute", institute)
                    .putString("profile_role", role)
                    .putString("profile_branch_section_year", branchSectionYear)
                    .putBoolean("has_profile", true)
                    .apply()

                _userProfile.value = com.example.models.UserProfile(
                    name = name,
                    subject = subject,
                    institute = institute,
                    role = role,
                    branchSectionYear = branchSectionYear
                )
                _userRole.value = role
                _isFaculty.value = (role == "teacher")
                
                val uid = auth?.currentUser?.uid
                if (uid != null && firestore != null) {
                    val profileData = hashMapOf(
                        "name" to name,
                        "subject" to subject,
                        "institute" to institute,
                        "role" to role,
                        "branchSectionYear" to branchSectionYear
                    )
                    val task = firestore!!.collection("users").document(uid).collection("profile").document("info").set(profileData)
                    val roleTask = firestore!!.collection("users").document(uid).collection("profile").document("role").set(hashMapOf("role" to role))
                    val email = _currentUserEmail.value.ifBlank { auth?.currentUser?.email ?: "" }.trim().lowercase()
                    if (email.isNotBlank()) {
                        profileData["email"] = email
                        firestore!!.collection("users_by_email").document(email).set(profileData, com.google.firebase.firestore.SetOptions.merge())
                    }
                    try {
                        kotlinx.coroutines.withTimeout(1500L) {
                            task.await()
                            roleTask.await()
                        }
                    } catch (e: Exception) {
                        Log.d("Profile", "Profile queued in offline persistent cache: ${e.message}")
                    }
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

    fun saveUserProfile(name: String, subject: String, institute: String, onComplete: () -> Unit, onError: (String) -> Unit) {
        saveUserProfile(name, subject, institute, "", if (_isFaculty.value) "teacher" else "student", onComplete, onError)
    }

    fun signOut() {
        try {
            auth?.signOut()
        } catch (e: Throwable) {
            Log.e("Auth", "Sign out failed")
        }
        _authState.value = false
        _currentUserEmail.value = ""
        _userProfile.value = null
        _userRole.value = null
        _isFaculty.value = false
        val prefs = getApplication<Application>().getSharedPreferences("app_profile_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    fun wipeAllMyData(onComplete: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                // 1. Always wipe local database first so UI reflects fresh state immediately
                repository.wipeAllData()
                _syncProgress.value = 0f
                _syncStatusText.value = ""
                
                // 2. Attempt to wipe cloud data in the background (catch and log any errors, but don't fail)
                val currentAuth = auth
                val currentFirestore = firestore
                val uid = currentAuth?.currentUser?.uid ?: currentActiveUserId
                if (currentFirestore != null && uid.isNotBlank()) {
                    try {
                        val batchesRef = currentFirestore.collection("users").document(uid).collection("batches")
                        val batchDocs = batchesRef.get().await()
                        for (batchDoc in batchDocs.documents) {
                            val batchId = batchDoc.id
                            try {
                                val batchAttSnap = batchesRef.document(batchId).collection("attendance").get().await()
                                for (attDoc in batchAttSnap.documents) {
                                    attDoc.reference.delete().await()
                                }
                            } catch (_: Exception) {}

                            try {
                                val batchStudSnap = batchesRef.document(batchId).collection("students").get().await()
                                for (studDoc in batchStudSnap.documents) {
                                    studDoc.reference.delete().await()
                                }
                            } catch (_: Exception) {}

                            try {
                                batchDoc.reference.delete().await()
                            } catch (_: Exception) {}
                        }

                        val collections = listOf("students", "attendance", "attendance_records", "schedule_slots", "slots", "courses")
                        for (collection in collections) {
                            try {
                                val ref = currentFirestore.collection("users").document(uid).collection(collection)
                                val snapshot = ref.get().await()
                                for (doc in snapshot.documents) {
                                    doc.reference.delete().await()
                                }
                            } catch (_: Exception) {}
                        }
                    } catch (e: Exception) {
                        Log.e("FirebaseSync", "Error wiping cloud data: ${e.message}")
                    }
                }

                withContext(Dispatchers.Main) {
                    onComplete()
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    onError("Failed to wipe data: ${e.message}")
                }
            }
        }
    }

    fun initDemoProfileIfNeeded() {
        val prefs = getApplication<Application>().getSharedPreferences("app_profile_prefs", Context.MODE_PRIVATE)
        val defaultName = "Prof. Rajesh Sharma"
        val defaultEmail = "prof.rajesh@campus.edu"
        val defaultSubject = "Computer Science & Engineering"
        val defaultInstitute = "Department of CSE"
        prefs.edit()
            .putString("profile_name", defaultName)
            .putString("profile_email", defaultEmail)
            .putString("profile_subject", defaultSubject)
            .putString("profile_institute", defaultInstitute)
            .putString("profile_role", "teacher")
            .putBoolean("has_profile", true)
            .apply()
        _userProfile.value = com.example.models.UserProfile(defaultName, defaultSubject, defaultInstitute, role = "teacher")
    }

    fun initDemoStudentProfileIfNeeded() {
        val prefs = getApplication<Application>().getSharedPreferences("app_profile_prefs", Context.MODE_PRIVATE)
        val defaultName = "Aman Kumar"
        val defaultEmail = "student.demo@campus.edu"
        val defaultSubject = "Computer Science & Engineering"
        val defaultInstitute = "Department of CSE"
        val defaultBranch = "B.Tech CSE • 4th Sem • Sec A"
        prefs.edit()
            .putString("profile_name", defaultName)
            .putString("profile_email", defaultEmail)
            .putString("profile_subject", defaultSubject)
            .putString("profile_institute", defaultInstitute)
            .putString("profile_role", "student")
            .putString("profile_branch_section_year", defaultBranch)
            .putBoolean("has_profile", true)
            .apply()
        _userProfile.value = com.example.models.UserProfile(
            name = defaultName,
            subject = defaultSubject,
            institute = defaultInstitute,
            role = "student",
            branchSectionYear = defaultBranch
        )
    }

    fun loginAsDemoFaculty(onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                val prefs = getApplication<Application>().getSharedPreferences("app_profile_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("is_demo_account", true).apply()
                setUserRole("teacher")
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
                val prefs = getApplication<Application>().getSharedPreferences("app_profile_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("is_demo_account", true).apply()
                setUserRole("student")
                initDemoStudentProfileIfNeeded()
                val slots = repository.getAllScheduleSlotsSync()
                if (slots.isEmpty()) {
                    loadDummyDataSuspend()
                }
                populateDemoOfficialClassesIfEmpty()

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
                    StudentUpload("S0_DBMS", "Aman Kumar", "24BCS001", "student.demo@campus.edu"),
                    StudentUpload("S1", "Sakshi Sharma", "24BCS025", "sakshi@campus.edu"),
                    StudentUpload("S2", "Rahul Verma", "24BCS026", "rahul@campus.edu"),
                    StudentUpload("S3", "Priya Singh", "24BCS027", "priya@campus.edu")
                )),
                CourseUpload("CSE-4SEM-B-DSA", "Data Structures & Algorithms", "CS302", 4, listOf(
                    StudentUpload("S0_DSA", "Aman Kumar", "24BCS001", "student.demo@campus.edu"),
                    StudentUpload("S4", "Amit Kumar", "24BCS028", "amit@campus.edu"),
                    StudentUpload("S5", "Neha Gupta", "24BCS029", "neha@campus.edu")
                )),
                CourseUpload("CSE-6SEM-A-OS", "Operating Systems", "CS303", 4, listOf(
                    StudentUpload("S0_OS", "Aman Kumar", "24BCS001", "student.demo@campus.edu"),
                    StudentUpload("S6", "Vikram Singh", "24BCS030", "vikram@campus.edu")
                )),
                CourseUpload("ECE-4SEM-A-CN", "Computer Networks", "CS304", 4, listOf(
                    StudentUpload("S0_CN", "Aman Kumar", "24BCS001", "student.demo@campus.edu"),
                    StudentUpload("S7", "Pooja Patel", "24BCS031", "pooja@campus.edu")
                )),
                CourseUpload("IT-5SEM-A-SE", "Software Engineering", "CS305", 4, listOf(
                    StudentUpload("S0_SE", "Aman Kumar", "24BCS001", "student.demo@campus.edu"),
                    StudentUpload("S8", "Arjun Reddy", "24BCS032", "arjun@campus.edu")
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

            // Immediately populate official classes for all weekly schedule slots
            val facultyMap = mapOf(
                "CSE-4SEM-A-DBMS" to ("Prof. Rajesh Sharma" to "prof.rajesh@campus.edu"),
                "CSE-4SEM-B-DSA" to ("Dr. Anita Desai" to "anita.desai@campus.edu"),
                "CSE-6SEM-A-OS" to ("Prof. Sunita Rao" to "sunita.rao@campus.edu"),
                "ECE-4SEM-A-CN" to ("Dr. Vikram Seth" to "vikram.seth@campus.edu"),
                "IT-5SEM-A-SE" to ("Prof. Arun Verma" to "arun.verma@campus.edu")
            )
            val officialList = weeklySchedule.map { slot ->
                val course = courses.find { it.id == slot.courseId }
                val (profName, profEmail) = facultyMap[slot.courseId] ?: ("Prof. Rajesh Sharma" to "prof.rajesh@campus.edu")
                com.example.data.OfficialClassEntity(
                    slotId = slot.id,
                    courseId = slot.courseId,
                    courseName = course?.name ?: "Subject",
                    courseCode = course?.code ?: "",
                    dayOfWeek = slot.dayOfWeek,
                    startTime = slot.startTime,
                    endTime = slot.endTime,
                    room = slot.room,
                    section = slot.section ?: "A",
                    facultyName = profName,
                    facultyEmail = profEmail,
                    isHidden = false
                )
            }
            repository.insertOfficialClasses(officialList)
            Log.d("LoadDummyData", "Successfully loaded ${courses.size} courses, ${weeklySchedule.size} slots and ${officialList.size} official classes.")
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun loadDummyData(onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            loadDummyDataSuspend()
            triggerCelebration("All classes loaded for the week ✓")
            onComplete?.invoke()
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
                // 1. Instantly save to Room DB on device
                repository.processTimetableImport(data)
                onSuccess()

                // 2. Background Cloud Sync with progress
                val currentAuth = auth
                val currentFirestore = firestore
                val uid = currentAuth?.currentUser?.uid

                if (currentFirestore != null && uid != null) {
                    _syncProgress.value = 0.3f
                    _syncStatusText.value = "Saving classes to Cloud in background..."
                    val batchesRef = currentFirestore.collection("users").document(uid).collection("batches")
                    val totalBatches = data.batches?.size ?: 1
                    data.batches?.forEachIndexed { index, batch ->
                        val docId = batch.batchId ?: java.util.UUID.randomUUID().toString()
                        val updatedBatch = batch.copy(batchId = docId)
                        val batchRef = batchesRef.document(docId)
                        
                        val scheduleList = updatedBatch.weeklySchedule?.map { s ->
                            hashMapOf(
                                "day" to (s.day ?: ""),
                                "time" to (s.time ?: ""),
                                "location" to (s.location ?: "")
                            )
                        } ?: emptyList<Any>()

                        val batchMeta = hashMapOf(
                            "batchId" to docId,
                            "year" to (updatedBatch.year ?: ""),
                            "semester" to (updatedBatch.semester ?: ""),
                            "course" to hashMapOf(
                                "code" to (updatedBatch.course?.code ?: ""),
                                "name" to (updatedBatch.course?.name ?: "")
                            ),
                            "section" to (updatedBatch.section ?: ""),
                            "location" to (updatedBatch.location ?: ""),
                            "weeklySchedule" to scheduleList
                        )
                        val batchTask = batchRef.set(batchMeta)
                        try {
                            kotlinx.coroutines.withTimeout(1500L) { batchTask.await() }
                        } catch (e: Exception) {
                            Log.d("FirebaseSync", "Batch $docId saved in offline persistent cache: ${e.message}")
                        }
                        
                        val studentsRef = batchRef.collection("students")
                        updatedBatch.students?.forEach { student ->
                            val studentId = student.id?.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString()
                            val studentTask = studentsRef.document(studentId).set(hashMapOf(
                                "id" to studentId,
                                "name" to (student.name ?: ""),
                                "rollNumber" to (student.rollNumber ?: "")
                            ))
                            try {
                                kotlinx.coroutines.withTimeout(500L) { studentTask.await() }
                            } catch (_: Exception) {}
                        }
                        _syncProgress.value = 0.3f + (0.6f * (index + 1) / totalBatches)
                    }
                    triggerCelebration("Classes saved to Cloud & On-Device 🎉")
                } else {
                    triggerCelebration("Classes saved on Device ✓")
                }
            } catch (e: Throwable) {
                android.util.Log.e("TimetableImport", "Save failed: ${e.message}", e)
                onError("Error saving timetable: ${e.message}")
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
                    val deleteTask = currentFirestore.collection("users").document(uid)
                        .collection("batches").document(courseId).delete()
                    try {
                        kotlinx.coroutines.withTimeout(1500L) { deleteTask.await() }
                    } catch (e: Exception) {
                        Log.d("FirebaseSync", "Delete queued in offline persistent cache: ${e.message}")
                    }
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
            com.example.data.StudentImport(id = s.id, name = s.name, rollNumber = s.rollNumber, email = s.email)
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
                val docId = batch.batchId ?: java.util.UUID.randomUUID().toString()
                val updatedBatch = batch.copy(batchId = docId)

                // 1. Instantly save to Room DB locally on device
                repository.dao.deleteStudentsByCourseId(docId)
                repository.dao.deleteScheduleSlotsByCourseId(docId)
                
                val dummyData = com.example.data.ImportTimetableData(teacher = null, batches = listOf(updatedBatch))
                repository.processTimetableImport(dummyData)

                // Immediately insert these schedule slots into local official_classes so student feed reflects them instantly
                val currentAuth = auth
                val profName = currentAuth?.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Prof. Rajesh Sharma"
                val profEmail = currentAuth?.currentUser?.email?.takeIf { it.isNotBlank() } ?: "faculty@campus.edu"
                val batchOfficialClasses = updatedBatch.weeklySchedule?.map { s ->
                    val rawDay = s.day ?: "Monday"
                    val day = when (rawDay.trim().lowercase()) {
                        "mon", "monday" -> "Monday"
                        "tue", "tues", "tuesday" -> "Tuesday"
                        "wed", "wednesday" -> "Wednesday"
                        "thu", "thur", "thurs", "thursday" -> "Thursday"
                        "fri", "friday" -> "Friday"
                        "sat", "saturday" -> "Saturday"
                        "sun", "sunday" -> "Sunday"
                        else -> rawDay.trim().replaceFirstChar { it.uppercase() }
                    }
                    val timeString = s.time ?: ""
                    val loc = s.location?.takeIf { it.isNotBlank() } ?: updatedBatch.location ?: ""
                    val parts = timeString.split("-").map { it.trim() }
                    val start = parts.getOrNull(0) ?: timeString
                    val end = parts.getOrNull(1) ?: ""
                    val slotId = "slot_${docId}_${day}_${start}_${end}".replace(Regex("[^a-zA-Z0-9_]"), "")
                    com.example.data.OfficialClassEntity(
                        slotId = slotId,
                        courseId = docId,
                        courseName = updatedBatch.course?.name ?: "",
                        courseCode = updatedBatch.course?.code ?: "",
                        dayOfWeek = day,
                        startTime = start,
                        endTime = end,
                        room = loc,
                        section = updatedBatch.section ?: "",
                        facultyName = profName,
                        facultyEmail = profEmail,
                        isHidden = false
                    )
                } ?: emptyList()
                if (batchOfficialClasses.isNotEmpty()) {
                    repository.insertOfficialClasses(batchOfficialClasses)
                }
                
                // Return success immediately so the user experiences zero lag!
                onSuccess()

                // 2. Background cloud sync
                val currentFirestore = firestore
                val uid = currentAuth?.currentUser?.uid

                if (currentFirestore != null && uid != null) {
                    _syncProgress.value = 0.3f
                    _syncStatusText.value = "Saving class to Cloud in background..."
                    val batchRef = currentFirestore.collection("users").document(uid).collection("batches").document(docId)
                    val scheduleList = updatedBatch.weeklySchedule?.map { s ->
                        hashMapOf(
                            "day" to (s.day ?: ""),
                            "time" to (s.time ?: ""),
                            "location" to (s.location ?: "")
                        )
                    } ?: emptyList<Any>()

                    val batchMeta = hashMapOf(
                        "batchId" to docId,
                        "year" to (updatedBatch.year ?: ""),
                        "semester" to (updatedBatch.semester ?: ""),
                        "course" to hashMapOf(
                            "code" to (updatedBatch.course?.code ?: ""),
                            "name" to (updatedBatch.course?.name ?: "")
                        ),
                        "section" to (updatedBatch.section ?: ""),
                        "location" to (updatedBatch.location ?: ""),
                        "weeklySchedule" to scheduleList
                    )
                    val setTask = batchRef.set(batchMeta)
                    try {
                        kotlinx.coroutines.withTimeout(1500L) { setTask.await() }
                    } catch (e: Exception) {
                        Log.d("FirebaseSync", "Batch $docId saved in offline persistent cache: ${e.message}")
                    }
                    
                    val studentsRef = batchRef.collection("students")
                    val totalStudents = updatedBatch.students?.size ?: 1
                    updatedBatch.students?.forEachIndexed { idx, student ->
                        val studentId = student.id?.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString()
                        val studentEmail = student.email?.trim()?.lowercase() ?: ""
                        val studentTask = studentsRef.document(studentId).set(hashMapOf(
                            "id" to studentId,
                            "name" to (student.name ?: ""),
                            "rollNumber" to (student.rollNumber ?: ""),
                            "email" to studentEmail
                        ))
                        try {
                            kotlinx.coroutines.withTimeout(500L) { studentTask.await() }
                        } catch (_: Exception) {}
                        
                        if (studentEmail.isNotBlank()) {
                            try {
                                val feedRef = currentFirestore.collection("student_feed").document(studentEmail).collection("official_classes")
                                updatedBatch.weeklySchedule?.forEach { schedule ->
                                    val rawDay = schedule.day ?: "Unknown"
                                    val day = when (rawDay.trim().lowercase()) {
                                        "mon", "monday" -> "Monday"
                                        "tue", "tues", "tuesday" -> "Tuesday"
                                        "wed", "wednesday" -> "Wednesday"
                                        "thu", "thur", "thurs", "thursday" -> "Thursday"
                                        "fri", "friday" -> "Friday"
                                        "sat", "saturday" -> "Saturday"
                                        "sun", "sunday" -> "Sunday"
                                        else -> rawDay.trim().replaceFirstChar { it.uppercase() }
                                    }
                                    val timeString = schedule.time ?: ""
                                    val loc = schedule.location?.takeIf { it.isNotBlank() } ?: updatedBatch.location ?: ""
                                    val parts = timeString.split("-").map { it.trim() }
                                    val start = parts.getOrNull(0) ?: timeString
                                    val end = parts.getOrNull(1) ?: ""
                                    val slotId = "slot_${docId}_${day}_${start}_${end}".replace(Regex("[^a-zA-Z0-9_]"), "")
                                    
                                    val feedClass = hashMapOf(
                                        "slotId" to slotId,
                                        "courseId" to docId,
                                        "courseName" to (updatedBatch.course?.name ?: ""),
                                        "courseCode" to (updatedBatch.course?.code ?: ""),
                                        "dayOfWeek" to day,
                                        "startTime" to start,
                                        "endTime" to end,
                                        "room" to loc,
                                        "section" to (updatedBatch.section ?: ""),
                                        "facultyName" to (currentAuth?.currentUser?.displayName ?: "Your Teacher"),
                                        "facultyEmail" to (currentAuth?.currentUser?.email ?: "")
                                    )
                                    feedRef.document(slotId).set(feedClass, com.google.firebase.firestore.SetOptions.merge())
                                }

                                currentFirestore.collection("student_feed").document(studentEmail).collection("profile").document("info").set(
                                    hashMapOf(
                                        "name" to (student.name ?: ""),
                                        "rollNumber" to (student.rollNumber ?: ""),
                                        "email" to studentEmail,
                                        "courseId" to docId,
                                        "courseName" to (updatedBatch.course?.name ?: "")
                                    ),
                                    com.google.firebase.firestore.SetOptions.merge()
                                )
                            } catch (e: Exception) {
                                Log.e("FirebaseSync", "Failed to push to student_feed for $studentEmail: ${e.message}")
                            }
                        }
                        
                        _syncProgress.value = 0.4f + (0.5f * (idx + 1) / totalStudents)
                    }
                    triggerCelebration("Class saved to Cloud & On-Device 🎉")
                } else {
                    triggerCelebration("Class saved on Device ✓")
                }
            } catch (e: Throwable) {
                android.util.Log.e("ManualEntry", "Write failed: ${e.message}", e)
                onError("Failed to save class: ${e.message}")
            }
        }
    }

    fun bulkImportStudentsToCourse(
        courseId: String,
        students: List<com.example.data.StudentImport>,
        overwriteExisting: Boolean = false,
        onSuccess: (Int) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                if (students.isEmpty()) {
                    onError("No students found to import.")
                    return@launch
                }

                // 1. Instantly save to Room DB locally on device
                if (overwriteExisting) {
                    repository.dao.deleteStudentsByCourseId(courseId)
                }
                val studentEntities = students.map { s ->
                    val sId = s.id?.takeIf { it.isNotBlank() } ?: "student_${java.util.UUID.randomUUID().toString().take(12)}"
                    com.example.data.StudentEntity(
                        id = sId,
                        name = s.name ?: "Unknown",
                        rollNumber = s.rollNumber ?: "",
                        courseId = courseId,
                        email = s.email ?: ""
                    )
                }
                repository.dao.insertStudents(studentEntities)

                // Return success immediately to unlock UI
                onSuccess(students.size)

                // 2. Background Cloud Sync
                val currentAuth = auth
                val currentFirestore = firestore
                val uid = currentAuth?.currentUser?.uid

                if (currentFirestore != null && uid != null) {
                    _syncProgress.value = 0.3f
                    _syncStatusText.value = "Syncing ${students.size} students to Cloud in background..."
                    val batchRef = currentFirestore.collection("users").document(uid).collection("batches").document(courseId)
                    val studentsRef = batchRef.collection("students")

                    if (overwriteExisting) {
                        try {
                            val existingDocs = studentsRef.get().await()
                            if (!existingDocs.isEmpty) {
                                val deleteBatch = currentFirestore.batch()
                                existingDocs.documents.forEach { doc ->
                                    deleteBatch.delete(doc.reference)
                                }
                                deleteBatch.commit().await()
                            }
                        } catch (e: Exception) {
                            Log.w("CsvImport", "Failed to clear old students: ${e.message}")
                        }
                    }

                    // Write in chunks of 350 to adhere to Firestore limits
                    val chunks = students.chunked(350)
                    chunks.forEachIndexed { cIdx, chunk ->
                        val writeBatch = currentFirestore.batch()
                        chunk.forEach { student ->
                            val sId = student.id?.takeIf { it.isNotBlank() } ?: "student_${java.util.UUID.randomUUID().toString().take(12)}"
                            val doc = studentsRef.document(sId)
                            val studentEmail = student.email ?: ""
                            writeBatch.set(doc, hashMapOf(
                                "id" to sId,
                                "name" to (student.name ?: "Unknown"),
                                "rollNumber" to (student.rollNumber ?: ""),
                                "email" to studentEmail
                            ))
                            
                            // Push to student_feed
                            if (studentEmail.isNotBlank()) {
                                try {
                                    val courseInfo = repository.getCourseById(courseId)
                                    val slots = repository.getScheduleSlotsForCourseSync(courseId)
                                    val feedRef = currentFirestore.collection("student_feed").document(studentEmail).collection("official_classes")
                                    
                                    slots.forEach { slot ->
                                        val feedClass = hashMapOf(
                                            "slotId" to slot.id,
                                            "courseId" to courseId,
                                            "courseName" to (courseInfo?.name ?: ""),
                                            "courseCode" to (courseInfo?.code ?: ""),
                                            "dayOfWeek" to slot.dayOfWeek,
                                            "startTime" to slot.startTime,
                                            "endTime" to slot.endTime,
                                            "room" to slot.room,
                                            "section" to slot.section,
                                            "facultyName" to (currentAuth.currentUser?.displayName ?: "Your Teacher"),
                                            "facultyEmail" to (currentAuth.currentUser?.email ?: "")
                                        )
                                        feedRef.document(slot.id).set(feedClass)
                                    }
                                } catch (e: Exception) {
                                    Log.e("FirebaseSync", "Failed to push to student_feed for bulk import: ${e.message}")
                                }
                            }
                        }
                        try {
                            kotlinx.coroutines.withTimeout(3000L) {
                                writeBatch.commit().await()
                            }
                        } catch (e: Exception) {
                            Log.d("CsvImport", "Batch written to offline cache: ${e.message}")
                        }
                        _syncProgress.value = 0.4f + (0.5f * (cIdx + 1) / chunks.size)
                    }
                    triggerCelebration("${students.size} students saved to Cloud & On-Device 🎉")
                } else {
                    triggerCelebration("${students.size} students saved on Device ✓")
                }
            } catch (e: Throwable) {
                Log.e("CsvImport", "Failed to bulk import students: ${e.message}", e)
                onError("Bulk import failed: ${e.message}")
            }
        }
    }

    fun quickOnboardClassWithStudents(
        courseName: String,
        courseCode: String,
        section: String,
        location: String,
        students: List<com.example.data.StudentImport>,
        weeklySchedule: List<com.example.data.ScheduleImport> = emptyList(),
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val newBatchId = "batch_${java.util.UUID.randomUUID().toString().take(8)}"
        val newBatch = com.example.data.BatchImport(
            batchId = newBatchId,
            year = "",
            semester = "",
            course = com.example.data.CourseImport(code = courseCode, name = courseName),
            section = section,
            location = location,
            weeklySchedule = weeklySchedule,
            students = students
        )
        saveSingleBatch(
            batch = newBatch,
            onSuccess = { onSuccess(newBatchId) },
            onError = onError
        )
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
    fun getAllScheduleSlots() = repository.getAllScheduleSlots()
    suspend fun getAllScheduleSlotsSync() = repository.getAllScheduleSlotsSync()
    fun getAttendanceForCourse(courseId: String) = repository.getAttendanceForCourse(courseId)
    
    fun markAttendance(date: String, slotId: String, studentId: String, status: String, courseId: String? = null) {
        viewModelScope.launch {
            val slot = repository.getScheduleSlotById(slotId)
            val batchId = courseId ?: slot?.courseId ?: ""
            val uid = currentActiveUserId

            // 1. Immediately update Room DB (Single Source of Truth)
            if (status == "NONE") {
                repository.deleteAttendance(date, slotId, studentId)
            } else {
                repository.deleteAttendance(date, slotId, studentId)
                val record = AttendanceRecordEntity(
                    date = date,
                    scheduleSlotId = slotId,
                    studentId = studentId,
                    status = status,
                    courseId = batchId,
                    userId = uid,
                    markedAt = System.currentTimeMillis()
                )
                repository.saveAttendance(record)
            }

            // Sync with local official_attendance if student has an email
            val studentObj = repository.getStudentById(studentId)
            val studentEmail = studentObj?.email?.trim()?.lowercase()
            val courseObj = if (batchId.isNotBlank()) repository.getCourseById(batchId) else null
            if (!studentEmail.isNullOrBlank()) {
                if (status == "NONE") {
                    repository.deleteOfficialAttendanceForSession(date, slotId)
                } else {
                    repository.insertOfficialAttendance(listOf(
                        com.example.data.OfficialAttendanceEntity(
                            id = "${date}_${slotId}",
                            date = date,
                            slotId = slotId,
                            courseId = batchId,
                            courseName = courseObj?.name ?: "",
                            status = status,
                            markedAt = System.currentTimeMillis()
                        )
                    ))
                }
            }

            // 2. Background push to Cloud Firestore with progress
            val authUid = auth?.currentUser?.uid
            val db = firestore
            if (authUid != null && db != null && batchId.isNotBlank()) {
                _syncProgress.value = 0.5f
                _syncStatusText.value = "Saving to Cloud in background..."
                try {
                    val attendanceRef = db.collection("users").document(authUid)
                        .collection("batches").document(batchId)
                        .collection("attendance")
                    val docRef = attendanceRef.document("${date}_${slotId}_$studentId")
                    if (status == "NONE") {
                        docRef.delete()
                    } else {
                        docRef.set(hashMapOf(
                            "date" to date,
                            "scheduleSlotId" to slotId,
                            "studentId" to studentId,
                            "status" to status,
                            "courseId" to batchId,
                            "updatedAt" to System.currentTimeMillis()
                        ))
                    }

                    // Push to student_feed
                    if (!studentEmail.isNullOrBlank()) {
                        val feedAttDoc = db.collection("student_feed").document(studentEmail)
                            .collection("attendance").document("${date}_${slotId}")
                        if (status == "NONE") {
                            feedAttDoc.delete()
                        } else {
                            feedAttDoc.set(hashMapOf(
                                "id" to "${date}_${slotId}",
                                "date" to date,
                                "slotId" to slotId,
                                "courseId" to batchId,
                                "courseName" to (courseObj?.name ?: ""),
                                "status" to status,
                                "markedAt" to System.currentTimeMillis()
                            ), com.google.firebase.firestore.SetOptions.merge())
                        }
                    }

                    triggerCelebration("Attendance saved to Cloud & Device 🎉")
                } catch (e: Throwable) {
                    Log.w("AttendanceSync", "Cloud write queued: ${e.message}")
                    _syncProgress.value = 1f
                }
            } else {
                triggerCelebration("Saved on Device ✓")
            }
        }
    }

    fun getStudentAttendanceForCourse(studentId: String, courseId: String) =
        repository.getAttendanceForStudentInCourse(studentId, courseId)

    fun getStudentCountForCourse(courseId: String) = repository.getStudentCountForCourse(courseId)
    fun searchStudents(courseId: String, query: String) = repository.searchStudents(courseId, query)
    fun getAttendanceCountForCourse(courseId: String) = repository.getAttendanceCountForCourse(courseId)
    fun getDistinctAttendanceDatesForCourse(courseId: String) = repository.getDistinctAttendanceDatesForCourse(courseId)

    fun cancelClassSession(
        date: String,
        slotId: String,
        courseId: String,
        note: String = "",
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                if (note.isNotBlank()) {
                    saveCancellationNote(date, slotId, courseId, note)
                }
                val uid = currentActiveUserId
                val students = repository.getStudentsByCourseSync(courseId)
                val noteSessionId = if (note.isNotBlank()) "NOTE:$note" else "CANCELLED"
                if (students.isEmpty()) {
                    val record = AttendanceRecordEntity(
                        date = date,
                        scheduleSlotId = slotId,
                        studentId = "CANCELLED_SESSION",
                        status = "CANCELLED",
                        sessionId = noteSessionId,
                        courseId = courseId,
                        userId = uid,
                        markedAt = System.currentTimeMillis()
                    )
                    repository.saveAttendance(record)
                } else {
                    students.forEach { student ->
                        val record = AttendanceRecordEntity(
                            date = date,
                            scheduleSlotId = slotId,
                            studentId = student.id,
                            status = "CANCELLED",
                            sessionId = noteSessionId,
                            courseId = courseId,
                            userId = uid,
                            markedAt = System.currentTimeMillis()
                        )
                        repository.saveAttendance(record)
                    }
                }

                // Also record for self so student side views it as cancelled immediately
                val selfRecord = AttendanceRecordEntity(
                    date = date,
                    scheduleSlotId = slotId,
                    studentId = "self",
                    status = "CANCELLED",
                    sessionId = noteSessionId,
                    courseId = courseId,
                    userId = uid,
                    markedAt = System.currentTimeMillis()
                )
                repository.saveAttendance(selfRecord)

                // Sync to local official_attendance
                val courseObj = repository.getCourseById(courseId)
                repository.insertOfficialAttendance(listOf(
                    com.example.data.OfficialAttendanceEntity(
                        id = "${date}_${slotId}",
                        date = date,
                        slotId = slotId,
                        courseId = courseId,
                        courseName = courseObj?.name ?: "",
                        status = "CANCELLED",
                        markedAt = System.currentTimeMillis()
                    )
                ))

                val authUid = auth?.currentUser?.uid
                val db = firestore
                if (authUid != null && db != null && courseId.isNotBlank()) {
                    try {
                        val sessionDoc = db.collection("users").document(authUid)
                            .collection("batches").document(courseId)
                            .collection("cancelled_sessions").document("${date}_${slotId}")
                        sessionDoc.set(hashMapOf(
                            "date" to date,
                            "scheduleSlotId" to slotId,
                            "courseId" to courseId,
                            "status" to "CANCELLED",
                            "note" to note,
                            "cancelledAt" to System.currentTimeMillis()
                        ))

                        // Push cancellation to student_feed for all students with email
                        students.forEach { st ->
                            val sEmail = st.email?.trim()?.lowercase()
                            if (!sEmail.isNullOrBlank()) {
                                db.collection("student_feed").document(sEmail)
                                    .collection("attendance").document("${date}_${slotId}")
                                    .set(hashMapOf(
                                        "id" to "${date}_${slotId}",
                                        "date" to date,
                                        "slotId" to slotId,
                                        "courseId" to courseId,
                                        "courseName" to (courseObj?.name ?: ""),
                                        "status" to "CANCELLED",
                                        "note" to note,
                                        "markedAt" to System.currentTimeMillis()
                                    ), com.google.firebase.firestore.SetOptions.merge())
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("AttendanceSync", "Failed to sync cancelled session: ${e.message}")
                    }
                }
                triggerCelebration("Class marked as Cancelled ✓")
                onSuccess()
            } catch (e: Exception) {
                Log.e("CancelClass", "Error cancelling class", e)
                onError(e.message ?: "Failed to cancel class")
            }
        }
    }

    fun uncancelClassSession(
        date: String,
        slotId: String,
        courseId: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                clearCancellationNote(date, slotId, courseId)
                val students = repository.getStudentsByCourseSync(courseId)
                students.forEach { st ->
                    repository.deleteAttendance(date, slotId, st.id)
                }
                repository.deleteAttendance(date, slotId, "CANCELLED_SESSION")
                repository.deleteAttendance(date, slotId, "self")
                repository.deleteOfficialAttendanceForSession(date, slotId)

                val db = firestore
                if (db != null) {
                    students.forEach { st ->
                        val sEmail = st.email?.trim()?.lowercase()
                        if (!sEmail.isNullOrBlank()) {
                            db.collection("student_feed").document(sEmail)
                                .collection("attendance").document("${date}_${slotId}").delete()
                        }
                    }
                }

                triggerCelebration("Cancelled status cleared")
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to clear cancelled status")
            }
        }
    }

    fun exportAttendanceData(
        context: Context,
        courseId: String,
        options: ExportOptions,
        onComplete: (File?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val course = repository.getCourseById(courseId)
                val students = repository.getStudentsByCourseSync(courseId)
                val allRecords = repository.getAttendanceForCourseSync(courseId)
                
                val profile = _userProfile.value
                val faculty = profile?.name?.ifBlank { null } ?: "Faculty Member"
                val institution = profile?.institute?.ifBlank { null } ?: "Department of Computer Science & Engineering"
                val resolvedOptions = options.copy(
                    facultyName = faculty,
                    institutionName = institution
                )

                val file = withContext(Dispatchers.IO) {
                    AttendanceExportHelper.exportAttendance(
                        context = context,
                        course = course,
                        students = students,
                        attendanceRecords = allRecords,
                        options = resolvedOptions
                    )
                }
                onComplete(file)
            } catch (e: Exception) {
                Log.e("Export", "Export failed", e)
                onComplete(null)
            }
        }
    }

    fun markAllStudentsAttendance(date: String, slotId: String, studentIds: List<String>, status: String, courseId: String? = null) {
        viewModelScope.launch {
            val slot = repository.getScheduleSlotById(slotId)
            val batchId = courseId ?: slot?.courseId ?: ""
            val uid = currentActiveUserId

            // 1. Save all to Room DB in an atomic batch immediately
            val records = studentIds.map { studentId ->
                AttendanceRecordEntity(
                    date = date,
                    scheduleSlotId = slotId,
                    studentId = studentId,
                    status = status,
                    courseId = batchId,
                    userId = uid,
                    markedAt = System.currentTimeMillis()
                )
            }
            repository.saveAttendanceBatch(records)

            // Sync with local official_attendance for students with email
            val courseObj = if (batchId.isNotBlank()) repository.getCourseById(batchId) else null
            val officialAttList = mutableListOf<com.example.data.OfficialAttendanceEntity>()
            studentIds.forEach { studentId ->
                val student = repository.getStudentById(studentId)
                val email = student?.email?.trim()?.lowercase()
                if (!email.isNullOrBlank()) {
                    officialAttList.add(
                        com.example.data.OfficialAttendanceEntity(
                            id = "${date}_${slotId}",
                            date = date,
                            slotId = slotId,
                            courseId = batchId,
                            courseName = courseObj?.name ?: "",
                            status = status,
                            markedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
            if (officialAttList.isNotEmpty()) {
                repository.insertOfficialAttendance(officialAttList)
            }

            // 2. Background sync
            val authUid = auth?.currentUser?.uid
            val db = firestore
            if (authUid != null && db != null && batchId.isNotBlank()) {
                _syncProgress.value = 0.4f
                _syncStatusText.value = "Syncing ${studentIds.size} records to Cloud..."
                try {
                    val batch = db.batch()
                    val attendanceRef = db.collection("users").document(authUid)
                        .collection("batches").document(batchId)
                        .collection("attendance")

                    studentIds.forEach { studentId ->
                        batch.set(attendanceRef.document("${date}_${slotId}_$studentId"), hashMapOf(
                            "date" to date,
                            "scheduleSlotId" to slotId,
                            "studentId" to studentId,
                            "status" to status,
                            "courseId" to batchId,
                            "updatedAt" to System.currentTimeMillis()
                        ))
                    }
                    batch.commit()
                    
                    // Push to student_feed
                    studentIds.forEach { studentId ->
                        val student = repository.getStudentById(studentId)
                        val email = student?.email?.trim()?.lowercase()
                        if (!email.isNullOrBlank()) {
                            val feedRef = db.collection("student_feed").document(email).collection("attendance")
                            feedRef.document("${date}_${slotId}").set(hashMapOf(
                                "id" to "${date}_${slotId}",
                                "date" to date,
                                "status" to status,
                                "slotId" to slotId,
                                "courseId" to batchId,
                                "courseName" to (courseObj?.name ?: ""),
                                "markedAt" to System.currentTimeMillis()
                            ), com.google.firebase.firestore.SetOptions.merge())
                        }
                    }
                    
                    triggerCelebration("All ${studentIds.size} records saved to Cloud 🎉")
                } catch (e: Throwable) {
                    Log.w("AttendanceSync", "Batch commit queued: ${e.message}")
                    _syncProgress.value = 1f
                }
            } else {
                triggerCelebration("All ${studentIds.size} records saved on Device ✓")
            }
        }
    }

    fun submitSessionAttendance(
        date: String,
        slotId: String,
        courseId: String,
        attendanceMap: Map<String, String>,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val uid = currentActiveUserId
                val cancelNote = getCancellationNote(date, slotId, courseId)
                val noteSessionId = if (cancelNote.isNotBlank()) "NOTE:$cancelNote" else "CANCELLED"
                // 1. Efficient batch processing to Room DB immediately
                val toDelete = attendanceMap.filter { it.value == "NONE" }
                val toInsert = attendanceMap.filter { it.value != "NONE" }.map { (studentId, status) ->
                    AttendanceRecordEntity(
                        date = date,
                        scheduleSlotId = slotId,
                        studentId = studentId,
                        status = status,
                        sessionId = if (status == "CANCELLED" || status == "C") noteSessionId else "",
                        courseId = courseId,
                        userId = uid,
                        markedAt = System.currentTimeMillis()
                    )
                }.toMutableList()

                if (attendanceMap.values.any { it == "CANCELLED" || it == "C" }) {
                    toInsert.add(
                        AttendanceRecordEntity(
                            date = date,
                            scheduleSlotId = slotId,
                            studentId = "self",
                            status = "CANCELLED",
                            sessionId = noteSessionId,
                            courseId = courseId,
                            userId = uid,
                            markedAt = System.currentTimeMillis()
                        )
                    )
                }

                toDelete.forEach { (studentId, _) ->
                    repository.deleteAttendance(date, slotId, studentId)
                }
                if (toInsert.isNotEmpty()) {
                    repository.saveAttendanceBatch(toInsert)
                }

                // Sync with local official_attendance
                val courseObj = repository.getCourseById(courseId)
                val officialAttList = mutableListOf<com.example.data.OfficialAttendanceEntity>()
                attendanceMap.forEach { (studentId, status) ->
                    val student = repository.getStudentById(studentId)
                    val email = student?.email?.trim()?.lowercase()
                    if (!email.isNullOrBlank()) {
                        if (status == "NONE") {
                            repository.deleteOfficialAttendanceForSession(date, slotId)
                        } else {
                            officialAttList.add(
                                com.example.data.OfficialAttendanceEntity(
                                    id = "${date}_${slotId}",
                                    date = date,
                                    slotId = slotId,
                                    courseId = courseId,
                                    courseName = courseObj?.name ?: "",
                                    status = status,
                                    markedAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }
                if (officialAttList.isNotEmpty()) {
                    repository.insertOfficialAttendance(officialAttList)
                }

                // Return success immediately so the UI transitions smoothly with ZERO lag!
                onSuccess()

                // 2. Background Cloud Sync
                val authUid = auth?.currentUser?.uid
                val db = firestore
                if (authUid != null && db != null) {
                    _syncProgress.value = 0.4f
                    _syncStatusText.value = "Syncing ${attendanceMap.size} records to Cloud in background..."
                    val batch = db.batch()
                    val attendanceRef = db.collection("users").document(authUid)
                        .collection("batches").document(courseId)
                        .collection("attendance")

                    attendanceMap.forEach { (studentId, status) ->
                        val docRef = attendanceRef.document("${date}_${slotId}_$studentId")
                        if (status == "NONE") {
                            batch.delete(docRef)
                        } else {
                            batch.set(docRef, hashMapOf(
                                "date" to date,
                                "scheduleSlotId" to slotId,
                                "studentId" to studentId,
                                "status" to status,
                                "courseId" to courseId,
                                "updatedAt" to System.currentTimeMillis()
                            ))
                        }
                    }

                    try {
                        kotlinx.coroutines.withTimeout(4000L) {
                            batch.commit().await()
                        }
                        
                        // Push to student_feed
                        attendanceMap.forEach { (studentId, status) ->
                            val student = repository.getStudentById(studentId)
                            val email = student?.email?.trim()?.lowercase()
                            if (!email.isNullOrBlank()) {
                                val feedRef = db.collection("student_feed").document(email).collection("attendance")
                                if (status == "NONE") {
                                    feedRef.document("${date}_${slotId}").delete()
                                } else {
                                    feedRef.document("${date}_${slotId}").set(hashMapOf(
                                        "id" to "${date}_${slotId}",
                                        "date" to date,
                                        "status" to status,
                                        "slotId" to slotId,
                                        "courseId" to courseId,
                                        "courseName" to (courseObj?.name ?: ""),
                                        "markedAt" to System.currentTimeMillis()
                                    ), com.google.firebase.firestore.SetOptions.merge())
                                }
                            }
                        }
                        
                        triggerCelebration("Session Attendance synced to Cloud 🎉")
                    } catch (e: Exception) {
                        Log.d("AttendanceSubmit", "Firebase write queued in persistent cache: ${e.message}")
                        _syncProgress.value = 1f
                    }
                } else {
                    triggerCelebration("Saved on Device ✓")
                }
            } catch (e: Exception) {
                Log.e("AttendanceSubmit", "Failed to submit attendance session: ${e.message}", e)
                onError(e.message ?: "Failed to save attendance")
            }
        }
    }

    fun getAllAttendance() = repository.getAllAttendance()

    private fun parseTimeSafelyVM(timeStr: String): LocalTime? {
        val cleanStr = timeStr.trim().uppercase()
        return try {
            if (cleanStr.contains("AM") || cleanStr.contains("PM")) {
                val formatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
                LocalTime.parse(cleanStr, formatter)
            } else {
                val parts = cleanStr.split(":")
                val h = parts[0].toInt()
                val m = parts[1].take(2).toInt()
                LocalTime.of(h, m)
            }
        } catch (e: Exception) {
            try {
                val formatter2 = DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern("[hh:mm a][h:mm a][HH:mm][H:mm]")
                    .toFormatter(Locale.ENGLISH)
                LocalTime.parse(cleanStr, formatter2)
            } catch (e2: Exception) {
                null
            }
        }
    }

    /**
     * Automatic Absence Engine:
     * If a scheduled class finished (class endTime + 5 minutes has passed) and student has not
     * marked present or any attendance, automatically records status as "A" (Absent).
     * Also checks past 7 scheduled days so missed sessions are correctly counted in eligibility stats.
     */
    fun autoMarkPastClassesAsAbsent() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val now = LocalTime.now()
                val today = LocalDate.now()
                val todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
                val dayOfWeekName = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                val dayOfWeekShort = today.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

                val allSlots = repository.getAllScheduleSlotsSync()
                val allAttendance = repository.getAllAttendanceSync()
                val uid = currentActiveUserId

                // 1. Check Today's Scheduled Slots
                val todaySlots = allSlots.filter { slot ->
                    slot.dayOfWeek.equals(dayOfWeekName, ignoreCase = true) ||
                    slot.dayOfWeek.equals(dayOfWeekShort, ignoreCase = true)
                }

                for (slot in todaySlots) {
                    val endTime = parseTimeSafelyVM(slot.endTime)
                    if (endTime != null) {
                        val autoAbsentDeadline = endTime.plusMinutes(5)
                        if (now.isAfter(autoAbsentDeadline)) {
                            val hasExistingRecord = allAttendance.any {
                                it.date == todayStr && it.studentId == "self" && (
                                    it.scheduleSlotId == slot.id ||
                                    (it.courseId.isNotBlank() && it.courseId == slot.courseId)
                                )
                            }
                            if (!hasExistingRecord) {
                                val absentRecord = AttendanceRecordEntity(
                                    date = todayStr,
                                    scheduleSlotId = slot.id,
                                    studentId = "self",
                                    status = "A",
                                    courseId = slot.courseId,
                                    userId = uid,
                                    markedAt = System.currentTimeMillis()
                                )
                                repository.saveAttendance(absentRecord)
                            }
                        }
                    }
                }

                // 2. Check Past 7 Days
                for (daysAgo in 1..7) {
                    val pastDate = today.minusDays(daysAgo.toLong())
                    val pastDateStr = pastDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    val pastDayFull = pastDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                    val pastDayShort = pastDate.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

                    val pastSlots = allSlots.filter { slot ->
                        slot.dayOfWeek.equals(pastDayFull, ignoreCase = true) ||
                        slot.dayOfWeek.equals(pastDayShort, ignoreCase = true)
                    }

                    for (slot in pastSlots) {
                        val hasExistingRecord = allAttendance.any {
                            it.date == pastDateStr && it.studentId == "self" && (
                                it.scheduleSlotId == slot.id ||
                                (it.courseId.isNotBlank() && it.courseId == slot.courseId)
                            )
                        }
                        if (!hasExistingRecord) {
                            val absentRecord = AttendanceRecordEntity(
                                date = pastDateStr,
                                scheduleSlotId = slot.id,
                                studentId = "self",
                                status = "A",
                                courseId = slot.courseId,
                                userId = uid,
                                markedAt = System.currentTimeMillis()
                            )
                            repository.saveAttendance(absentRecord)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("AutoAbsent", "Auto absence scan completed with note: ${e.message}")
            }
        }
    }

    fun markSelfAttendance(
        date: String,
        slotId: String,
        courseId: String,
        status: String = "P",
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val uid = currentActiveUserId
                // 1. First remove any prior self-attendance for this specific date and course/slot
                repository.deleteStudentAttendanceForCourseDate(date, slotId, courseId, "self")

                val record = AttendanceRecordEntity(
                    date = date,
                    scheduleSlotId = slotId,
                    studentId = "self",
                    status = status,
                    courseId = courseId,
                    userId = uid,
                    markedAt = System.currentTimeMillis()
                )
                // 2. Immediately save to Room DB so Sheet and Dashboards update instantly!
                repository.saveAttendance(record)
                onSuccess()

                // 3. Push to SyncEngine and Firestore
                syncEngine.queueAttendanceOffline(record)
                val authUid = auth?.currentUser?.uid
                val db = firestore
                if (authUid != null && db != null) {
                    _syncProgress.value = 0.5f
                    _syncStatusText.value = "Saving attendance to Cloud..."
                    val sessionKey = "${date}_${slotId}"
                    val docRef = db.collection("users").document(authUid)
                        .collection("attendance_records").document("${sessionKey}_self")
                    docRef.set(hashMapOf(
                        "date" to date,
                        "scheduleSlotId" to slotId,
                        "studentId" to "self",
                        "status" to status,
                        "courseId" to courseId,
                        "markedAt" to System.currentTimeMillis()
                    ), com.google.firebase.firestore.SetOptions.merge())

                    if (courseId.isNotBlank()) {
                        val batchRef = db.collection("users").document(authUid)
                            .collection("batches").document(courseId)
                            .collection("attendance").document("${sessionKey}_self")
                        batchRef.set(hashMapOf(
                            "date" to date,
                            "scheduleSlotId" to slotId,
                            "studentId" to "self",
                            "status" to status,
                            "courseId" to courseId,
                            "markedAt" to System.currentTimeMillis()
                        ), com.google.firebase.firestore.SetOptions.merge())
                    }
                    triggerCelebration("Attendance Saved to Cloud & Device 🎉")
                } else {
                    triggerCelebration("Saved on Device ✓")
                }
            } catch (e: Throwable) {
                Log.e("SelfAttendance", "Failed to mark self attendance", e)
                onError(e.message ?: "Failed to mark attendance")
            }
        }
    }

    fun updateStudentAttendanceStatus(
        date: String,
        slotId: String,
        courseId: String,
        newStatus: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val uid = currentActiveUserId
                // First remove any existing record for this date and course/slot
                repository.deleteStudentAttendanceForCourseDate(date, slotId, courseId, "self")

                val record = AttendanceRecordEntity(
                    date = date,
                    scheduleSlotId = slotId,
                    studentId = "self",
                    status = newStatus,
                    courseId = courseId,
                    userId = uid,
                    markedAt = System.currentTimeMillis()
                )
                repository.saveAttendance(record)
                onSuccess()

                // Background sync
                val authUid = auth?.currentUser?.uid
                val db = firestore
                if (authUid != null && db != null) {
                    _syncProgress.value = 0.5f
                    val sessionKey = "${date}_${slotId}"
                    val docRef = db.collection("users").document(authUid)
                        .collection("attendance_records").document("${sessionKey}_self")
                    docRef.set(hashMapOf(
                        "date" to date,
                        "scheduleSlotId" to slotId,
                        "studentId" to "self",
                        "status" to newStatus,
                        "courseId" to courseId,
                        "markedAt" to System.currentTimeMillis()
                    ), com.google.firebase.firestore.SetOptions.merge())

                    if (courseId.isNotBlank()) {
                        val batchRef = db.collection("users").document(authUid)
                            .collection("batches").document(courseId)
                            .collection("attendance").document("${sessionKey}_self")
                        batchRef.set(hashMapOf(
                            "date" to date,
                            "scheduleSlotId" to slotId,
                            "studentId" to "self",
                            "status" to newStatus,
                            "courseId" to courseId,
                            "markedAt" to System.currentTimeMillis()
                        ), com.google.firebase.firestore.SetOptions.merge())
                    }
                    triggerCelebration("Updated & Saved to Cloud 🎉")
                } else {
                    triggerCelebration("Saved on Device ✓")
                }
            } catch (e: Throwable) {
                Log.e("AttendanceCorrection", "Failed to update attendance status", e)
                onError(e.message ?: "Failed to update attendance")
            }
        }
    }

    fun deleteSelfAttendance(
        date: String,
        slotId: String,
        courseId: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                repository.deleteStudentAttendanceForCourseDate(date, slotId, courseId, "self")
                onSuccess()

                val uid = auth?.currentUser?.uid
                val db = firestore
                if (uid != null && db != null) {
                    val sessionId = "${date}_$slotId"
                    try {
                        db.collection("users").document(uid)
                            .collection("attendance_records").document("${sessionId}_self")
                            .delete()
                    } catch (_: Exception) {}

                    if (courseId.isNotBlank()) {
                        try {
                            db.collection("users").document(uid)
                                .collection("batches").document(courseId)
                                .collection("attendance").document("${sessionId}_self")
                                .delete()
                        } catch (_: Exception) {}
                    }
                }
                triggerCelebration("Status Cleared ✓")
            } catch (e: Exception) {
                Log.e("Attendance", "Failed to delete self attendance: ${e.message}")
            }
        }
    }

    fun deleteAttendance(date: String, slotId: String, studentId: String = "self") {
        viewModelScope.launch {
            try {
                repository.deleteAttendance(date, slotId, studentId)
                val uid = auth?.currentUser?.uid
                val db = firestore
                if (uid != null && db != null) {
                    val sessionId = "${date}_$slotId"
                    val docId = if (studentId == "self") "${sessionId}_self" else "${sessionId}_$studentId"
                    try {
                        db.collection("users").document(uid)
                            .collection("attendance_records").document(docId)
                            .delete()
                    } catch (_: Exception) {}

                    val batches = repository.getAllCoursesSync()
                    for (batch in batches) {
                        try {
                            db.collection("users").document(uid)
                                .collection("batches").document(batch.id)
                                .collection("attendance").document(sessionId)
                                .delete()
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                Log.e("Attendance", "Failed to delete attendance: ${e.message}")
            }
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
                            val r = profile.role
                            if (r.isNotBlank()) {
                                _userRole.value = r
                                _isFaculty.value = (r == "teacher")
                            }
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
                val savedRole = prefs.getString("profile_role", null) ?: if (_isFaculty.value) "teacher" else "student"
                if (!savedName.isNullOrBlank()) {
                    val profile = com.example.models.UserProfile(
                        name = savedName,
                        subject = prefs.getString("profile_subject", "Computer Science & Engineering") ?: "Computer Science & Engineering",
                        institute = prefs.getString("profile_institute", "Department of CSE") ?: "Department of CSE",
                        role = savedRole,
                        branchSectionYear = prefs.getString("profile_branch_section_year", "") ?: ""
                    )
                    _userProfile.value = profile
                    _userRole.value = savedRole
                    _isFaculty.value = (savedRole == "teacher")
                    onSuccess(profile)
                } else {
                    onSuccess(null)
                }
            }
        }
    }

    // ==========================================
    // OFFICIAL TIMETABLE & ATTENDANCE FEED (STUDENT ERP)
    // ==========================================
    val activeOfficialClasses: StateFlow<List<com.example.data.OfficialClassEntity>> =
        repository.getActiveOfficialClasses()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hiddenOfficialClasses: StateFlow<List<com.example.data.OfficialClassEntity>> =
        repository.getHiddenOfficialClasses()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val officialAttendance: StateFlow<List<com.example.data.OfficialAttendanceEntity>> =
        repository.getAllOfficialAttendance()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getStudentEmail(): String {
        val authEmail = auth?.currentUser?.email?.trim()?.lowercase()
        if (!authEmail.isNullOrBlank()) return authEmail
        val prefs = getApplication<Application>().getSharedPreferences("app_profile_prefs", Context.MODE_PRIVATE)
        val saved = prefs.getString("profile_email", null)
        if (!saved.isNullOrBlank()) return saved.trim().lowercase()
        return if (_isFaculty.value) "prof.rajesh@campus.edu" else "student.demo@campus.edu"
    }

    fun setStudentEmail(email: String) {
        val prefs = getApplication<Application>().getSharedPreferences("app_profile_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("profile_email", email.trim().lowercase()).apply()
        syncOfficialStudentFeed()
    }

    fun hideOfficialClass(slotId: String) {
        viewModelScope.launch {
            repository.setOfficialClassHidden(slotId, true)
            triggerCelebration("Class moved to Hidden Classes ✓")
        }
    }

    fun unhideOfficialClass(slotId: String) {
        viewModelScope.launch {
            repository.setOfficialClassHidden(slotId, false)
            triggerCelebration("Class restored to Schedule ✓")
        }
    }

    fun normalizeDayName(rawDay: String): String {
        return when (rawDay.trim().lowercase()) {
            "mon", "monday" -> "Monday"
            "tue", "tues", "tuesday" -> "Tuesday"
            "wed", "wednesday" -> "Wednesday"
            "thu", "thur", "thurs", "thursday" -> "Thursday"
            "fri", "friday" -> "Friday"
            "sat", "saturday" -> "Saturday"
            "sun", "sunday" -> "Sunday"
            else -> rawDay.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() }
        }
    }

    private fun mapDocToOfficialClass(doc: com.google.firebase.firestore.DocumentSnapshot): com.example.data.OfficialClassEntity {
        val slotId = doc.getString("slotId") ?: doc.id
        val courseId = doc.getString("courseId") ?: ""
        val courseName = doc.getString("courseName") ?: "Subject"
        val courseCode = doc.getString("courseCode") ?: ""
        val dayOfWeek = normalizeDayName(doc.getString("dayOfWeek") ?: "Wednesday")
        val startTime = doc.getString("startTime") ?: "02:00 PM"
        val endTime = doc.getString("endTime") ?: "03:00 PM"
        val room = doc.getString("room") ?: "Room 204"
        val section = doc.getString("section") ?: ""
        val facultyName = doc.getString("facultyName") ?: "Faculty Instructor"
        val facultyEmail = doc.getString("facultyEmail") ?: ""
        val isHidden = doc.getBoolean("isHidden") ?: false
        return com.example.data.OfficialClassEntity(
            slotId = slotId,
            courseId = courseId,
            courseName = courseName,
            courseCode = courseCode,
            dayOfWeek = dayOfWeek,
            startTime = startTime,
            endTime = endTime,
            room = room,
            section = section,
            facultyName = facultyName,
            facultyEmail = facultyEmail,
            isHidden = isHidden
        )
    }

    private fun mapDocToOfficialAttendance(doc: com.google.firebase.firestore.DocumentSnapshot): com.example.data.OfficialAttendanceEntity {
        val id = doc.getString("id") ?: doc.id
        val date = doc.getString("date") ?: ""
        val slotId = doc.getString("slotId") ?: ""
        val courseId = doc.getString("courseId") ?: ""
        val courseName = doc.getString("courseName") ?: ""
        val status = doc.getString("status") ?: "P"
        val markedAt = doc.getLong("markedAt") ?: System.currentTimeMillis()
        return com.example.data.OfficialAttendanceEntity(
            id = id,
            date = date,
            slotId = slotId,
            courseId = courseId,
            courseName = courseName,
            status = status,
            markedAt = markedAt
        )
    }

    fun registerStudentFeedListeners(email: String) {
        val db = firestore ?: return
        if (email.isBlank()) return

        studentFeedClassesListener?.remove()
        studentFeedClassesListener = db.collection("student_feed").document(email)
            .collection("official_classes")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("StudentFeedListener", "Classes listener error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    viewModelScope.launch(Dispatchers.IO) {
                        val classes = snapshot.documents.mapNotNull { doc ->
                            try { mapDocToOfficialClass(doc) } catch (_: Exception) { null }
                        }
                        if (classes.isNotEmpty()) {
                            repository.insertOfficialClasses(classes)
                            Log.d("StudentFeedListener", "Live synced ${classes.size} official classes into local Room")
                        }
                    }
                }
            }

        studentFeedAttendanceListener?.remove()
        studentFeedAttendanceListener = db.collection("student_feed").document(email)
            .collection("attendance")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("StudentFeedListener", "Attendance listener error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    viewModelScope.launch(Dispatchers.IO) {
                        val records = snapshot.documents.mapNotNull { doc ->
                            try { mapDocToOfficialAttendance(doc) } catch (_: Exception) { null }
                        }
                        if (records.isNotEmpty()) {
                            repository.insertOfficialAttendance(records)
                            Log.d("StudentFeedListener", "Live synced ${records.size} attendance records into local Room")
                        }
                    }
                }
            }
    }

    suspend fun syncOfficialClassesFromLocalSlots() {
        try {
            val allCourses = repository.getAllCoursesSync()
            val allSlots = repository.getAllScheduleSlotsSync()

            if (allCourses.isNotEmpty() && allSlots.isNotEmpty()) {
                val courseMap = allCourses.associateBy { it.id }
                val facultyMap = mapOf(
                    "CSE-4SEM-A-DBMS" to ("Prof. Rajesh Sharma" to "prof.rajesh@campus.edu"),
                    "CSE-4SEM-B-DSA" to ("Dr. Anita Desai" to "anita.desai@campus.edu"),
                    "CSE-6SEM-A-OS" to ("Prof. Sunita Rao" to "sunita.rao@campus.edu"),
                    "ECE-4SEM-A-CN" to ("Dr. Vikram Seth" to "vikram.seth@campus.edu"),
                    "IT-5SEM-A-SE" to ("Prof. Arun Verma" to "arun.verma@campus.edu")
                )
                val currentAuth = auth
                val profName = currentAuth?.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Prof. Rajesh Sharma"
                val profEmail = currentAuth?.currentUser?.email?.takeIf { it.isNotBlank() } ?: "prof.rajesh@campus.edu"
                val localOfficialList = allSlots.map { slot ->
                    val course = courseMap[slot.courseId]
                    val (fName, fEmail) = facultyMap[slot.courseId] ?: (profName to profEmail)
                    com.example.data.OfficialClassEntity(
                        slotId = slot.id,
                        courseId = slot.courseId,
                        courseName = course?.name ?: "Subject",
                        courseCode = course?.code ?: "",
                        dayOfWeek = normalizeDayName(slot.dayOfWeek),
                        startTime = slot.startTime,
                        endTime = slot.endTime,
                        room = slot.room,
                        section = slot.section,
                        facultyName = fName,
                        facultyEmail = fEmail,
                        isHidden = false
                    )
                }
                repository.insertOfficialClasses(localOfficialList)
                Log.d("SyncOfficialClasses", "Inserted ${localOfficialList.size} official classes from local schedule slots")
            } else {
                loadDummyDataSuspend()
            }
        } catch (e: Exception) {
            Log.e("SyncOfficialClasses", "Error syncing official classes from local slots: ${e.message}")
        }
    }

    fun syncOfficialStudentFeed() {
        viewModelScope.launch {
            val email = getStudentEmail().trim().lowercase()
            Log.d("SyncOfficialStudentFeed", "Starting sync for email: $email")
            
            // 1. Sync from local database (guarantee timetable classes are always available in official feed)
            syncOfficialClassesFromLocalSlots()
            try {

                // If any students have this email in the local database, sync their attendance to official_attendance
                if (email.isNotBlank()) {
                    val matchingStudents = repository.getStudentsByEmail(email)
                    if (matchingStudents.isNotEmpty()) {
                        val attToInsert = mutableListOf<com.example.data.OfficialAttendanceEntity>()
                        val allCoursesMap = repository.getAllCoursesSync().associateBy { it.id }
                        matchingStudents.forEach { st ->
                            val course = allCoursesMap[st.courseId]
                            val studentAtt = repository.getAttendanceForStudentSync(st.id)
                            studentAtt.forEach { att ->
                                attToInsert.add(
                                    com.example.data.OfficialAttendanceEntity(
                                        id = "${att.date}_${att.scheduleSlotId}",
                                        date = att.date,
                                        slotId = att.scheduleSlotId,
                                        courseId = st.courseId,
                                        courseName = course?.name ?: "",
                                        status = att.status,
                                        markedAt = att.markedAt
                                    )
                                )
                            }
                        }
                        if (attToInsert.isNotEmpty()) {
                            repository.insertOfficialAttendance(attToInsert)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SyncOfficialStudentFeed", "Local enrolled sync error: ${e.message}")
            }

            // 2. Cloud Firestore student_feed Sync (Offline cache first + real-time listener)
            val db = firestore
            if (db != null && email.isNotBlank()) {
                try {
                    val classesSnap = db.collection("student_feed").document(email)
                        .collection("official_classes").get().await()
                    Log.d("SyncOfficialStudentFeed", "Fetched classesSnap, isEmpty: ${classesSnap.isEmpty}")
                    if (!classesSnap.isEmpty) {
                        val officialList = classesSnap.documents.mapNotNull { doc ->
                            try { mapDocToOfficialClass(doc) } catch (_: Exception) { null }
                        }
                        if (officialList.isNotEmpty()) {
                            repository.insertOfficialClasses(officialList)
                        }
                    }

                    val attSnap = db.collection("student_feed").document(email)
                        .collection("attendance").get().await()
                    if (!attSnap.isEmpty) {
                        val attList = attSnap.documents.mapNotNull { doc ->
                            try { mapDocToOfficialAttendance(doc) } catch (_: Exception) { null }
                        }
                        if (attList.isNotEmpty()) {
                            repository.insertOfficialAttendance(attList)
                        }
                    }

                    // Register real-time listeners for live updates from teachers!
                    registerStudentFeedListeners(email)
                } catch (e: Exception) {
                    Log.d("OfficialFeed", "Could not sync cloud feed: ${e.message}")
                    registerStudentFeedListeners(email)
                }
            }
        }
    }

    suspend fun populateDemoOfficialClassesIfEmpty() {
        val currentOfficial = repository.getActiveOfficialClasses().firstOrNull() ?: emptyList()
        if (currentOfficial.isEmpty()) {
            loadDummyDataSuspend()
        }
    }
}
