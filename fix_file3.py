with open("app/src/main/java/com/example/ui/screens/AddEditBatchScreen.kt", "r") as f:
    content = f.read()

# Make sure it has FlowRow imports
if "FlowRow" not in content:
    content = content.replace("import androidx.compose.foundation.layout.*", "import androidx.compose.foundation.layout.*\nimport androidx.compose.foundation.layout.ExperimentalLayoutApi\nimport androidx.compose.foundation.layout.FlowRow\n")

if "data class ScheduleBlockState" not in content:
    content += """

data class ScheduleBlockState(
    val id: String = java.util.UUID.randomUUID().toString(),
    var timeRange: String = "",
    var selectedDays: Set<String> = emptySet(),
    var location: String = ""
)
"""

with open("app/src/main/java/com/example/ui/screens/AddEditBatchScreen.kt", "w") as f:
    f.write(content)
