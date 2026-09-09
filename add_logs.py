with open("app/src/main/java/com/example/viewmodel/MainViewModel.kt", "r") as f:
    content = f.read()

import re

fix = """    fun parseTimetableJson(jsonString: String): com.example.data.ImportTimetableData? {
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
                        batchesRef.document(docId).set(updatedBatch).await()
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
    }"""

content = re.sub(r'    fun parseTimetableJson.*?    fun deleteCourse', fix + '\n\n    fun deleteCourse', content, flags=re.DOTALL)

with open("app/src/main/java/com/example/viewmodel/MainViewModel.kt", "w") as f:
    f.write(content)
