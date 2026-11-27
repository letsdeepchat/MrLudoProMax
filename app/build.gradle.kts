import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Robolectric's own downloader ignores the build's repositories; let Gradle fetch the SDK jar instead.
val robolectricSdk: Configuration by configurations.creating { isTransitive = false }
val robolectricJars = layout.buildDirectory.dir("robolectric-jars")
val copyRobolectricJars = tasks.register<Copy>("copyRobolectricJars") { from(robolectricSdk); into(robolectricJars) }

android {
    namespace = "com.ludosovereign.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ludosovereign"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "1.5.0"
        // Ship only the languages we translate (also trims library strings and the generated locale list).
        resourceConfigurations += listOf("en", "hi", "ta", "te", "kn", "mr", "gu", "bn", "ml", "es", "ar", "fr")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Debug-signed so the release APK installs for testing; replace with a real keystore to publish.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    packaging { resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "DebugProbesKt.bin", "kotlin-tooling-metadata.json") }
    androidResources { generateLocaleConfig = true }
    testOptions { unitTests { isIncludeAndroidResources = true; all { it.maxHeapSize = "2g"; it.systemProperty("robolectric.pixelCopyRenderMode", "hardware"); it.systemProperty("robolectric.offline", "true"); it.systemProperty("robolectric.dependency.dir", robolectricJars.get().asFile.absolutePath); it.dependsOn(copyRobolectricJars) } } }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:sound"))
    implementation(project(":core:session"))
    implementation(project(":feature:home"))
    implementation(project(":feature:setup"))
    implementation(project(":feature:board"))
    implementation(project(":feature:audiostudio"))
    implementation(project(":feature:stats"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    robolectricSdk("org.robolectric:android-all-instrumented:15-robolectric-12650502-i7")
}
