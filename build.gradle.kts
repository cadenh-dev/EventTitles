plugins {
    `java-library`
}

group = "com.example.titles"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

val paperApi = "io.papermc.paper:paper-api:26.3.build.230-stable"

dependencies {
    compileOnly(paperApi)
    // Gson is also bundled by Paper at runtime; declared explicitly for the pure-Java LLM client.
    implementation("com.google.code.gson:gson:2.11.0")

    // Paper API (Adventure/MiniMessage) on the test classpath so pure-Java units can be tested.
    testImplementation(paperApi)
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    // Paper API 26.3 publishes Gradle metadata requiring JVM 25 (org.gradle.jvm.version=25),
    // so a Java 21 toolchain cannot resolve it. See AGENTS.md §1.
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("paper-plugin.yml") { expand(props) }
}

tasks.test {
    useJUnitPlatform()
}
