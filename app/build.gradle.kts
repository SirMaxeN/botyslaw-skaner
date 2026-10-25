import java.util.Properties

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "eu.komars.forgescanner"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.sirmaxen.BotyslawSkaner"
        minSdk = 26
        targetSdk = 36
        versionCode = 19
        versionName = "1.0.10"
        buildConfigField("boolean", "PLAY_DISTRIBUTION", "false")
        testInstrumentationRunner = "eu.komars.forgescanner.ScannerSmokeInstrumentation"
    }
    buildFeatures { buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    signingConfigs {
        create("production") {
            val properties = Properties()
            val file = rootProject.file(".signing/release.properties")
            if (file.exists()) {
                file.inputStream().use { properties.load(it) }
                storeFile = rootProject.file(".signing/production.jks")
                storePassword = properties.getProperty("storePassword")
                keyAlias = "scanner"
                keyPassword = properties.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("production")
        }
    }
    buildTypes.create("play") {
        initWith(buildTypes.getByName("release"))
        buildConfigField("boolean", "PLAY_DISTRIBUTION", "true")
        matchingFallbacks += listOf("release")
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies {
    implementation("com.google.mlkit:text-recognition:16.0.1")
    testImplementation("junit:junit:4.13.2")
}

// Private replay fixtures are optional, but changing them must invalidate Gradle's test cache.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    listOf("SCANNER_VIDEO_FIXTURE", "SCANNER_FAST_FIXTURE", "SCANNER_LATEST_FIXTURE",
        "SCANNER_GRID_FIXTURE", "SCANNER_MOUNT_SCREEN", "SCANNER_CONTROLLED_FIXTURE", "SCANNER_SPARSE_FIXTURE").forEach { name ->
        val path = System.getenv(name)
        inputs.property(name, path ?: "absent")
        if (!path.isNullOrBlank()) inputs.file(path)
    }
}
