pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.google\\.android.*")
                includeGroupByRegex("com\\.google\\.testing.*")
            }
        }
        // Google's official Maven Central mirror first: Central rate-limits shared CI egress IPs.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.google\\.android.*")
                includeGroupByRegex("com\\.google\\.testing.*")
            }
        }
        // Google's official Maven Central mirror first: Central rate-limits shared CI egress IPs.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}

rootProject.name = "LudoSovereign"

// Pure-Kotlin modules: build and test anywhere with a JDK 17+.
include(":core:engine", ":core:ai", ":core:session", ":core:audio")

// Android modules: only when an Android SDK is available (ANDROID_HOME or local.properties sdk.dir).
val androidSdkAvailable = System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null ||
    file("local.properties").let { it.exists() && it.readText().contains("sdk.dir") }

if (androidSdkAvailable) {
    include(
        ":core:data",
        ":core:sound",
        ":core:designsystem",
        ":feature:home",
        ":feature:setup",
        ":feature:board",
        ":feature:audiostudio",
        ":feature:stats",
        ":app",
    )
} else {
    logger.lifecycle("Android SDK not found: building pure-Kotlin modules only.")
}
