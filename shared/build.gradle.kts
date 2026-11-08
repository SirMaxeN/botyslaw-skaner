import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

// Platform-independent scan and battle logic shared by Android (:app) and iOS (ios/, framework BotyslawShared).
plugins { id("org.jetbrains.kotlin.multiplatform") }

kotlin {
    jvm { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

    // iOS targets build only on macOS; on Windows they are skipped (see gradle.properties).
    val xcframework = XCFramework("BotyslawShared")
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "BotyslawShared"
            isStatic = true
            xcframework.add(this)
        }
    }

    sourceSets {
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
