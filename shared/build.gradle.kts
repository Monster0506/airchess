plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    android {
        namespace = "com.airchess.shared"
        compileSdk = 37
        minSdk = 26

        withHostTest {}
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
            export(project(":chess"))
            export(project(":protocol"))
        }
    }

    // Stockfish is a prebuilt static library (ios/stockfish/build.sh, macOS only); cinterop
    // embeds the slice that matches each target so tests and the app link the engine.
    val stockfish = rootProject.layout.projectDirectory.dir("ios/stockfish")
    mapOf(iosArm64() to "ios-arm64", iosSimulatorArm64() to "ios-arm64-simulator")
        .forEach { (target, slice) ->
            target.compilations.getByName("main").cinterops.create("stockfish") {
                definitionFile.set(layout.projectDirectory.file("src/nativeInterop/cinterop/stockfish.def"))
                includeDirs(stockfish.dir("include").asFile)
                extraOpts("-libraryPath", stockfish.dir("build/Stockfish.xcframework/$slice").asFile.path)
            }
        }

    sourceSets {
        commonMain.dependencies {
            api(project(":chess"))
            api(project(":protocol"))
            api(libs.kotlinx.coroutines.core)
            api(libs.androidx.lifecycle.viewmodel)
            implementation(libs.kotlinx.io.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
