import com.google.ai.client.generativeai.type.Schema
import com.google.ai.client.generativeai.type.Type
fun test() {
    val s = Schema(
        name = "test",
        description = "test",
        type = com.google.ai.client.generativeai.type.FunctionType.OBJECT
    )
}
