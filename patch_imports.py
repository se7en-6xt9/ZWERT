import re

with open("app/src/main/java/com/example/ui/screens/AddEditBatchScreen.kt", "r") as f:
    content = f.read()

imports_to_add = """
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
"""

content = content.replace("import androidx.compose.foundation.layout.*", "import androidx.compose.foundation.layout.*\n" + imports_to_add)

with open("app/src/main/java/com/example/ui/screens/AddEditBatchScreen.kt", "w") as f:
    f.write(content)
