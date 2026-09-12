with open("app/src/main/java/com/example/viewmodel/AiHelper.kt", "r") as f:
    content = f.read()

content = content.replace("val responseSchema = Schema(", "val myResponseSchema = Schema(")
content = content.replace("responseSchema = responseSchema", "responseSchema = myResponseSchema")

with open("app/src/main/java/com/example/viewmodel/AiHelper.kt", "w") as f:
    f.write(content)
