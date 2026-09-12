with open("app/src/main/java/com/example/ui/screens/ImportTimetableScreen.kt", "r") as f:
    content = f.read()

import re

confirm_code = """            onConfirm = { finalData ->
                isLoading = true
                viewModel.saveReviewedTimetable(
                    data = finalData,
                    onSuccess = {
                        isLoading = false
                        android.widget.Toast.makeText(context, "Timetable saved successfully!", android.widget.Toast.LENGTH_LONG).show()
                        navController.popBackStack()
                    },
                    onError = { err ->
                        isLoading = false
                        android.widget.Toast.makeText(context, "Save failed: $err", android.widget.Toast.LENGTH_LONG).show()
                    }
                )
            },"""

content = re.sub(r'            onConfirm = \{ finalData ->.*?            \},', confirm_code, content, flags=re.DOTALL)

with open("app/src/main/java/com/example/ui/screens/ImportTimetableScreen.kt", "w") as f:
    f.write(content)
