with open("app/src/main/java/com/example/ui/screens/AddEditBatchScreen.kt", "r") as f:
    content = f.read()

if "import androidx.compose.foundation.clickable" not in content:
    content = content.replace("import androidx.compose.foundation.horizontalScroll", "import androidx.compose.foundation.horizontalScroll\nimport androidx.compose.foundation.clickable")

with open("app/src/main/java/com/example/ui/screens/AddEditBatchScreen.kt", "w") as f:
    f.write(content)
