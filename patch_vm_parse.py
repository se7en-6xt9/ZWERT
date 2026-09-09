with open("app/src/main/java/com/example/viewmodel/MainViewModel.kt", "r") as f:
    content = f.read()

patch = """    fun parseTimetableJson(jsonString: String): com.example.data.ImportTimetableData? {
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
                        // Re-assign batchId in case it was null
                        val updatedBatch = batch.copy(batchId = docId)
                        batchesRef.document(docId).set(updatedBatch).await()
                    }
                }

                // Proceed to save local state
                repository.processTimetableImport(data)
                onSuccess()
            } catch (e: Throwable) {
                android.util.Log.e("FirebaseSync", "Write failed: ${e.message}")
                onError("Error parsing or syncing JSON: ${e.message}")
            }
        }
    }
"""

# Replace the original importTimetableFromJson with the new parsed ones.
import re
content = re.sub(r'fun importTimetableFromJson.*?// Delete locally', patch + '\n\n    // Delete locally', content, flags=re.DOTALL)

with open("app/src/main/java/com/example/viewmodel/MainViewModel.kt", "w") as f:
    f.write(content)
