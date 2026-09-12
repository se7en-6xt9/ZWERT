import re

with open("app/src/main/java/com/example/ui/screens/AddEditBatchScreen.kt", "r") as f:
    content = f.read()

old_load_logic = """                val blocks = mutableListOf<ScheduleBlockState>()
                val grouped = (batch.weeklySchedule ?: emptyList()).groupBy { "${it.time}|${it.location}" }
                for ((_, scheds) in grouped) {
                    if (scheds.isEmpty()) continue
                    val first = scheds.first()
                    blocks.add(
                        ScheduleBlockState(
                            timeRange = first.time ?: "",
                            location = first.location ?: "",
                            selectedDays = scheds.mapNotNull { it.day }.toSet()
                        )
                    )
                }
                scheduleBlocks = blocks"""

new_load_logic = """                val blocks = mutableListOf<ScheduleBlockState>()
                val gridSelections = mutableSetOf<Pair<String, String>>()
                val grouped = (batch.weeklySchedule ?: emptyList()).groupBy { "${it.time}|${it.location}" }
                
                for ((_, scheds) in grouped) {
                    if (scheds.isEmpty()) continue
                    val first = scheds.first()
                    val timeString = first.time ?: ""
                    
                    // Check if this timeString exactly matches any of our grid hours
                    if (gridHours.contains(timeString) && (first.location.isNullOrBlank() || first.location == batch.location)) {
                        // Belongs in the grid!
                        scheds.forEach { s ->
                            s.day?.let { day -> gridSelections.add(Pair(day, timeString)) }
                        }
                    } else {
                        // Belongs in the manual blocks
                        blocks.add(
                            ScheduleBlockState(
                                timeRange = timeString,
                                location = first.location ?: "",
                                selectedDays = scheds.mapNotNull { it.day }.toSet()
                            )
                        )
                    }
                }
                scheduleBlocks = blocks
                selectedGridCells = gridSelections
                
                if (gridSelections.isNotEmpty() && blocks.isEmpty()) {
                    scheduleInputMode = "Grid"
                }"""

content = content.replace(old_load_logic, new_load_logic)

with open("app/src/main/java/com/example/ui/screens/AddEditBatchScreen.kt", "w") as f:
    f.write(content)
