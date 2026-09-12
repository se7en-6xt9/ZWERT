# A script to explore AddEditBatchScreen.kt contents and where to put the new UI
import re

with open("app/src/main/java/com/example/ui/screens/AddEditBatchScreen.kt", "r") as f:
    content = f.read()

print("File size:", len(content))
print("Contains Weekly Schedule Builder?", "Weekly Schedule Builder" in content)
