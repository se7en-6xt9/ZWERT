sed -i '/\[versions\]/a ktor = "2.3.11"' gradle/libs.versions.toml
sed -i '/\[libraries\]/a ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }\nktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }' gradle/libs.versions.toml
sed -i '/implementation(libs.generativeai)/a \
  implementation(libs.ktor.client.core)\n  implementation(libs.ktor.client.okhttp)' app/build.gradle.kts
