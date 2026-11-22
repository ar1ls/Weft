import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("java")
    id("maven-publish")
    id("com.gradleup.shadow") version "9.6.1"
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
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/license/mit")
                    }
                }
            }
        }
    }
}

tasks.withType<Jar>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    from(layout.projectDirectory.file("LICENSE")) {
        into("META-INF")
        rename { "LICENSE-Weft" }
    }
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version.toString(),
            "Weft-Commit" to commitSha
        )
    }
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    filesMatching("META-INF/services/**") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    mergeServiceFiles()
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.EC")
    exclude("module-info.class", "META-INF/versions/**/module-info.class")
    manifest.attributes("Multi-Release" to "true")
}

tasks.assemble {
    dependsOn(tasks.named("shadowJar"))
}

val runtimeArtifacts = configurations.runtimeClasspath.get().incoming.artifacts
val runtimeDependencyReport = layout.buildDirectory.file("reports/distribution/Weft-$version-dependencies.txt")

tasks.register("writeRuntimeDependencies") {
    group = "distribution"
    description = "Writes the resolved runtime artifacts included in the fat JAR."
    inputs.files(configurations.runtimeClasspath)
    outputs.file(runtimeDependencyReport)
    doLast {
        val report = runtimeDependencyReport.get().asFile
        report.parentFile.mkdirs()
        report.writeText(runtimeArtifacts.artifacts.map {
            "${it.id.componentIdentifier.displayName} (${it.file.name})"
        }.sorted().joinToString(separator = "\n", postfix = "\n"))
    }
}

repositories {
    mavenCentral()
}

dependencies {

    implementation(libs.asm)
    implementation(libs.asmtree)

    implementation(libs.slf4jApi)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

}

tasks.test {
    useJUnitPlatform()
}
