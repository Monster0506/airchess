import java.io.DataInputStream
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.GZIPInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val serverUrlOverride: String? = providers.gradleProperty("airchess.serverUrl").orNull
    ?: providers.fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText.orNull
        ?.let { text -> Properties().apply { load(text.reader()) }.getProperty("airchess.serverUrl") }
val defaultServerUrl = serverUrlOverride?.trim()?.ifEmpty { null } ?: "ws://localhost:8080/ws"
val releaseStore = providers.environmentVariable("AIRCHESS_KEYSTORE_FILE").orNull

// Stockfish is far too large to commit, so the official release asset is downloaded once
// into a cache outside build/, checked against a pinned SHA-256 and expanded into jniLibs.
abstract class FetchStockfish : DefaultTask() {
    @get:Input
    abstract val url: Property<String>

    @get:Input
    abstract val sha256: Property<String>

    /** Entry inside the archive to ship as libstockfish.so. */
    @get:Input
    abstract val entry: Property<String>

    @get:Input
    abstract val abi: Property<String>

    @get:Internal
    abstract val cacheDir: DirectoryProperty

    @get:OutputDirectory
    abstract val jniLibsDir: DirectoryProperty

    @TaskAction
    fun fetch() {
        val archive = cachedArchive()
        val out = jniLibsDir.get().asFile
        out.deleteRecursively()
        val target = File(out, "${abi.get()}/libstockfish.so")
        target.parentFile.mkdirs()
        extract(archive, target)
    }

    private fun cachedArchive(): File {
        val dir = cacheDir.get().asFile.apply { mkdirs() }
        val archive = File(dir, "${sha256.get()}.tar.gz")
        if (archive.isFile && digest(archive) == sha256.get()) {
            return archive
        }

        val partial = File(dir, "${archive.name}.part")
        logger.lifecycle("Downloading ${url.get()}")
        URI(url.get()).toURL().openStream().use { input ->
            partial.outputStream().buffered().use { input.copyTo(it) }
        }
        val actual = digest(partial)
        if (actual != sha256.get()) {
            partial.delete()
            throw GradleException(
                "Stockfish download checksum mismatch for ${url.get()}: " +
                    "expected ${sha256.get()}, got $actual. Refusing to build with an unverified binary.",
            )
        }
        check(partial.renameTo(archive)) { "Cannot move $partial to $archive" }
        return archive
    }

    private fun digest(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                md.update(buffer, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    // Minimal tar reader: the JDK has none, and the asset is a plain (pax-flavoured) tar.gz.
    private fun extract(archive: File, target: File) {
        val wanted = entry.get()
        DataInputStream(GZIPInputStream(archive.inputStream().buffered())).use { tar ->
            val header = ByteArray(TAR_BLOCK)
            var paxPath: String? = null
            while (true) {
                tar.readFully(header)
                if (header.all { it == 0.toByte() }) break

                val size = String(header, TAR_SIZE_OFFSET, TAR_SIZE_LENGTH, Charsets.US_ASCII)
                    .trim { it == ' ' || it == '\u0000' }.toLong(8)
                val type = header[TAR_TYPE_OFFSET].toInt().toChar()
                val padded = (size + TAR_BLOCK - 1) / TAR_BLOCK * TAR_BLOCK

                if (type == 'x') {
                    val records = ByteArray(size.toInt()).also { tar.readFully(it) }
                    paxPath = String(records, Charsets.UTF_8).lineSequence()
                        .mapNotNull { it.substringAfter(" path=", "").ifEmpty { null } }
                        .firstOrNull() ?: paxPath
                    tar.skipNBytes(padded - size)
                    continue
                }

                val name = paxPath ?: String(header, 0, TAR_NAME_LENGTH, Charsets.UTF_8).trimEnd('\u0000')
                paxPath = null
                if (name == wanted && (type == '0' || type == '\u0000')) {
                    target.outputStream().buffered().use { out ->
                        var left = size
                        val buffer = ByteArray(1 shl 16)
                        while (left > 0) {
                            val n = tar.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                            if (n < 0) throw GradleException("Truncated archive ${archive.name}")
                            out.write(buffer, 0, n)
                            left -= n
                        }
                    }
                    return
                }
                tar.skipNBytes(padded)
            }
        }
        throw GradleException("Entry $wanted not found in ${archive.name}")
    }

    private companion object {
        const val TAR_BLOCK = 512
        const val TAR_NAME_LENGTH = 100
        const val TAR_SIZE_OFFSET = 124
        const val TAR_SIZE_LENGTH = 12
        const val TAR_TYPE_OFFSET = 156
    }
}

val fetchStockfish = tasks.register<FetchStockfish>("fetchStockfish") {
    url.set("https://github.com/official-stockfish/Stockfish/releases/download/sf_19/stockfish-android-arm64-universal.tar.gz")
    sha256.set("ebb24051aa4a222b4daaf049b882ecf1163d370c128fe02316602643f4d5e426")
    entry.set("stockfish/stockfish-android-arm64-universal")
    abi.set("arm64-v8a")
    cacheDir.set(rootProject.layout.projectDirectory.dir(".cache/stockfish"))
    jniLibsDir.set(layout.buildDirectory.dir("generated/stockfish/jniLibs"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(fetchStockfish, FetchStockfish::jniLibsDir)
    }
}

android {
    namespace = "com.airchess"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.airchess"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("airchess.versionCode").orNull?.toInt() ?: 1
        versionName = providers.gradleProperty("airchess.versionName").orNull ?: "1.0"
        buildConfigField("String", "DEFAULT_SERVER_URL", "\"${defaultServerUrl.replace("\"", "")}\"")
    }

    signingConfigs {
        if (releaseStore != null) {
            create("distribution") {
                storeFile = file(releaseStore)
                storePassword = providers.environmentVariable("AIRCHESS_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("AIRCHESS_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("AIRCHESS_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("distribution") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        // Stockfish runs as a child process, so it must be extracted to nativeLibraryDir.
        jniLibs {
            useLegacyPackaging = true
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":chess"))
    implementation(project(":protocol"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)
    implementation("io.github.webrtc-sdk:android:144.7559.14")
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.websockets)
    implementation(libs.sceneview.ar)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
