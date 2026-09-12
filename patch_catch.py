with open("app/src/main/java/com/example/ui/screens/ImportTimetableScreen.kt", "r") as f:
    content = f.read()

import re

# Remove the duplicated/trailing catch block
content = content.replace("""                            } catch (e: Exception) {
                                null
                            }
                            
                            if (aiResult != null) {""", "")

with open("app/src/main/java/com/example/ui/screens/ImportTimetableScreen.kt", "w") as f:
    f.write(content)
