import java.util.Properties

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    testBuildType = providers.gradleProperty("scannerTestBuildType").getOrElse("debug")
    namespace = "eu.komars.forgescanner"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.sirmaxen.BotyslawSkaner"
        manifestPlaceholders["appLabel"] = "Botysław Skaner"
        minSdk = 26
        targetSdk = 36
        versionCode = 38
        versionName = "1.4.1"
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
    buildTypes.create("autoTest") {
        initWith(buildTypes.getByName("debug"))
        applicationIdSuffix = ".autotest"
        versionNameSuffix = "-auto-test"
        manifestPlaceholders["appLabel"] = "Botysław WALKA Test"
        matchingFallbacks += listOf("debug")
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
    implementation("com.google.android.play:app-update:2.1.0")
    testImplementation("junit:junit:4.13.2")
}

// Private replay fixtures are optional, but changing them must invalidate Gradle's test cache.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    listOf("SCANNER_VIDEO_FIXTURE", "SCANNER_FAST_FIXTURE", "SCANNER_LATEST_FIXTURE",
        "SCANNER_GRID_FIXTURE", "SCANNER_MOUNT_SCREEN", "SCANNER_AUTO_ONE_MOUNT_GRAY", "SCANNER_UI_PROFILES", "SCANNER_CONTROLLED_FIXTURE", "SCANNER_SPARSE_FIXTURE", "SCANNER_PARTIAL_TAIL_FIXTURE").forEach { name ->
        val path = System.getenv(name)
        inputs.property(name, path ?: "absent")
        if (!path.isNullOrBlank()) {
            if (name == "SCANNER_UI_PROFILES") inputs.dir(path) else inputs.file(path)
        }
    }
}

// User-provided battle setup screens are local optional regression inputs.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    listOf("BATTLE_SETUP_PROFILES", "BATTLE_SETUP_HOME", "BATTLE_SETUP_WEAPON", "BATTLE_SETUP_FULL_HOME").forEach { name ->
        val paths = System.getenv(name)
        inputs.property(name, paths ?: "absent")
        if (!paths.isNullOrBlank()) inputs.files(paths.split(';'))
    }
}

// Real roster card examples must invalidate the local replay test cache.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    val path = System.getenv("ROSTER_ROW_FIXTURES")
    inputs.property("ROSTER_ROW_FIXTURES", path ?: "absent")
    if (!path.isNullOrBlank()) inputs.dir(path)
}
