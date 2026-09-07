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
