sed -i '/fun loadUserProfile/,/^    }/d' app/src/main/java/com/example/viewmodel/MainViewModel.kt
sed -i '/^}$/d' app/src/main/java/com/example/viewmodel/MainViewModel.kt
cat << 'INNER_EOF' >> app/src/main/java/com/example/viewmodel/MainViewModel.kt
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
INNER_EOF
