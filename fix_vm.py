with open("app/src/main/java/com/example/viewmodel/MainViewModel.kt", "r") as f:
    content = f.read()

# I will find the patch block and replace it up to getAllCourses()
import re

fix = """    fun parseTimetableJson(jsonString: String): com.example.data.ImportTimetableData? {
        return try {
            val moshi = com.squareup.moshi.Moshi.Builder().build()
            val adapter = moshi.adapter(com.example.data.ImportTimetableData::class.java)
            adapter.fromJson(jsonString)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseSync", "Parse failed: ${e.message}")
            null
        }
    }
    
    fun saveReviewedTimetable(data: com.example.data.ImportTimetableData, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val currentAuth = auth
                val currentFirestore = firestore
                val uid = currentAuth?.currentUser?.uid

                if (currentFirestore != null && uid != null) {
                    val batchesRef = currentFirestore.collection("users").document(uid).collection("batches")
                    data.batches?.forEach { batch ->
                        val docId = batch.batchId ?: java.util.UUID.randomUUID().toString()
                        val updatedBatch = batch.copy(batchId = docId)
                        batchesRef.document(docId).set(updatedBatch).await()
                    }
                }

                repository.processTimetableImport(data)
                onSuccess()
            } catch (e: Throwable) {
                android.util.Log.e("FirebaseSync", "Write failed: ${e.message}")
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

"""

# Regex from parseTimetableJson to getAllCourses()
content = re.sub(r'    fun parseTimetableJson.*?    fun getAllCourses\(\)', fix + '    fun getAllCourses()', content, flags=re.DOTALL)

with open("app/src/main/java/com/example/viewmodel/MainViewModel.kt", "w") as f:
    f.write(content)
