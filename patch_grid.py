import re

with open("app/src/main/java/com/example/ui/screens/AddEditBatchScreen.kt", "r") as f:
    content = f.read()

old_grid_hours = """    val gridHours = listOf(
        "08:00 AM - 09:00 AM",
        "09:00 AM - 10:00 AM",
        "10:00 AM - 11:00 AM",
        "11:00 AM - 12:00 PM",
        "12:00 PM - 01:00 PM",
        "01:00 PM - 02:00 PM",
        "02:00 PM - 03:00 PM",
        "03:00 PM - 04:00 PM",
        "04:00 PM - 05:00 PM",
        "05:00 PM - 06:00 PM",
        "06:00 PM - 07:00 PM",
        "07:00 PM - 08:00 PM"
    )"""

new_grid_hours = """    val gridHours = (0..23).map { hour ->
        val amPmStart = if (hour < 12) "AM" else "PM"
        val startHour = if (hour % 12 == 0) 12 else hour % 12
        val amPmEnd = if ((hour + 1) < 12 || (hour + 1) == 24) "AM" else "PM"
        val endHour = if ((hour + 1) % 12 == 0) 12 else (hour + 1) % 12
        String.format("%02d:00 %s - %02d:00 %s", startHour, amPmStart, endHour, amPmEnd)
    }
    var isGridFullScreenOpen by remember { mutableStateOf(false) }
    var allBookedGridCells by remember { mutableStateOf(setOf<Pair<String, String>>()) }
    
    LaunchedEffect(batchId) {
        try {
            val allSlots = viewModel.getAllScheduleSlotsSync()
            val booked = mutableSetOf<Pair<String, String>>()
            for (slot in allSlots) {
                if (batchId != null && slot.courseId == batchId) continue
                val timeString = if (slot.endTime.isNotBlank()) "${slot.startTime} - ${slot.endTime}" else slot.startTime
                booked.add(Pair(slot.dayOfWeek, timeString))
            }
            allBookedGridCells = booked
        } catch(e: Exception) {}
    }"""

content = content.replace(old_grid_hours, new_grid_hours)

with open("app/src/main/java/com/example/ui/screens/AddEditBatchScreen.kt", "w") as f:
    f.write(content)
