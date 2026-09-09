sed -i '/\[versions\]/a generativeai = "0.9.0"' gradle/libs.versions.toml
sed -i '/\[libraries\]/a generativeai = { module = "com.google.ai.client.generativeai:generativeai", version.ref = "generativeai" }' gradle/libs.versions.toml
