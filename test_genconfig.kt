import com.google.ai.client.generativeai.type.generationConfig
import com.google.ai.client.generativeai.type.Schema
import com.google.ai.client.generativeai.type.FunctionType

fun test() {
    val schema = Schema(name="", description="", type=FunctionType.STRING)
    val g = generationConfig {
        responseMimeType = "application/json"
        responseSchema = schema
    }
}
