plugins {
    id("java")
}

group = "com.github.arl1s.weft"
version = "1.0-SNAPSHOT"

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