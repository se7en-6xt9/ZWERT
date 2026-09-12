with open("app/src/main/java/com/example/ui/screens/AddEditBatchScreen.kt", "r") as f:
    content = f.read()

# I will fix the top first
import re
content = re.sub(r'package com\.example\.ui\.screens.*?import android\.widget\.Toast', 'package com.example.ui.screens\n\nimport android.widget.Toast', content, flags=re.DOTALL)

with open("app/src/main/java/com/example/ui/screens/AddEditBatchScreen.kt", "w") as f:
    f.write(content)
