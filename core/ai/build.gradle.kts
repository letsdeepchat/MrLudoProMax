plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    api(project(":core:engine"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.junit.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("failed"); showStandardStreams = true }
}

// ./gradlew :core:ai:benchmark  -> prints per-tier latency percentiles
tasks.register<JavaExec>("benchmark") {
    group = "verification"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.ludosovereign.ai.BenchmarkKt")
}
