with open("app/src/main/java/com/example/viewmodel/MainViewModel.kt", "r") as f:
    content = f.read()

import re

# Add getBatchForEdit and saveSingleBatch to MainViewModel
patch = """
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
                    val batchesRef = currentFirestore.collection("users").document(uid).collection("batches")
                    batchesRef.document(docId).set(updatedBatch).await()
                }

                // Instead of processing full data, we process just this batch. We need to clear its old schedules/students first.
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
"""

content = re.sub(r'    fun getAllCourses\(\) = repository.dao.getAllCourses\(\)', patch + '\n    fun getAllCourses() = repository.dao.getAllCourses()', content)

with open("app/src/main/java/com/example/viewmodel/MainViewModel.kt", "w") as f:
    f.write(content)
