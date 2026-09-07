    fun wipeAllMyData(onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                // 1. Wipe local DB
                repository.wipeAllData()
                
                // 2. Wipe Firestore data (if any exists) for this user
                val userId = auth.currentUser?.uid
                if (userId != null) {
                    val collections = listOf("batches", "students", "attendance")
                    for (collection in collections) {
                        val ref = firestore.collection("users").document(userId).collection(collection)
                        val snapshot = ref.get().await()
                        for (doc in snapshot.documents) {
                            doc.reference.delete().await()
                        }
                    }
                }
                onComplete()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error wiping data", e)
                onComplete()
            }
        }
    }
