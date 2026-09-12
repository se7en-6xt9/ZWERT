import re

with open("app/src/main/java/com/example/ui/screens/AddEditBatchScreen.kt", "r") as f:
    content = f.read()

# Extract ScheduleBlockState
pattern = r'(data class ScheduleBlockState\([^)]+\))'
match = re.search(pattern, content)
if match:
    state_class = match.group(1)
    content = content.replace(state_class, "")
    
    # Put it at the very end of the file
    content = content + "\n\n" + state_class

# Also fix `schedules` references that were missed
content = content.replace("${schedules.size} class times", "${scheduleBlocks.size} class times")

with open("app/src/main/java/com/example/ui/screens/AddEditBatchScreen.kt", "w") as f:
    f.write(content)
