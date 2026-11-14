plugins {
    id("java")
    id("maven-publish")
}

val commitSha = providers.gradleProperty("commitSha").orElse(
    providers.exec {
        workingDir(layout.projectDirectory.asFile)
        commandLine("git", "rev-parse", "--verify", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.map { it.trim() }
).get().lowercase()

if (!Regex("(?:[0-9a-f]{40}|[0-9a-f]{64})").matches(commitSha)) {
    throw GradleException("Cannot determine the commit SHA. Use a Git checkout or pass -PcommitSha=<full SHA>.")
}

val releaseVersion = providers.gradleProperty("releaseVersion").orNull
if (releaseVersion != null && !Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)").matches(releaseVersion)) {
    throw GradleException("releaseVersion must use MAJOR.MINOR.PATCH without a leading v, for example 1.0.0.")
}

group = "com.github.arl1s.weft"
version = releaseVersion ?: "dev-${commitSha.take(12)}"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") { from(components["java"]) }
    }
}

tasks.withType<Jar>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version.toString(),
            "Weft-Commit" to commitSha
        )
    }
}

repositories {
    mavenCentral()
}

dependencies {

    implementation(libs.asm)
    implementation(libs.asmtree)

    implementation(libs.log4j)
    implementation(libs.log4jApi)
    implementation(libs.slf4jApi)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    implementation(libs.caffeine)


    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

}

tasks.test {
    useJUnitPlatform()
}
