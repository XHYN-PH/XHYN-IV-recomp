// LibertyRecomp Android — :app module.
//
// Gradle never runs CMake. The native side (libmain.so, librexruntime.so,
// the GPU plugins, libSDL3.so, libc++_shared.so) is millions of lines of
// recompiled PowerPC and is built by os/android/scripts/build_native.sh,
// which stages the libraries into src/main/jniLibs and the title resources
// into src/main/assets/Resources. This module only packages them.
//
// The SDL Java classes come from the same SDL3 tree the native library is
// built from: SDLActivity refuses to start when the versions differ.

plugins {
    id("com.android.application")
}

val sdlJavaDir = rootProject.file(
    "../../glue/rexglue-sdk-main/thirdparty/sdl3/android-project/app/src/main/java")

android {
    namespace   = "com.libertyrecomp"
    compileSdk  = 35
    buildToolsVersion = "35.0.0"
    ndkVersion  = "29.0.14206865"

    defaultConfig {
        applicationId = "com.xhyn.ivrecomp"
        // 28: ASharedMemory (26) for guest memory and AAudio (27) for audio.
        minSdk        = 29
        targetSdk     = 35
        versionCode = 38
        versionName   = "0.5.9.3-xhyn-r2-test10"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    sourceSets["main"].java.srcDirs("src/main/java", sdlJavaDir)

    val releaseStore = System.getenv("XHYN_RELEASE_KEYSTORE")
    if (!releaseStore.isNullOrBlank()) {
        signingConfigs.create("xhynRelease") {
            storeFile = file(releaseStore)
            storePassword = System.getenv("XHYN_RELEASE_STORE_PASSWORD")
            keyAlias = System.getenv("XHYN_RELEASE_KEY_ALIAS") ?: "xhyn"
            keyPassword = System.getenv("XHYN_RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            isDebuggable    = true
            isJniDebuggable = true
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            if (!releaseStore.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("xhynRelease")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            // Unpack the libraries into nativeLibraryDir: the runtime finds its
            // GPU plugins by real path next to itself, and a Turnip driver
            // loaded through libadrenotools needs real files as well.
            useLegacyPackaging = true
            // build_native.sh decides what is stripped.
            keepDebugSymbols += "**/*.so"
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}

tasks.withType<Test>().configureEach {
    maxHeapSize = "1536m"
    maxParallelForks = 1
    for (key in listOf("http.proxyHost", "http.proxyPort", "https.proxyHost", "https.proxyPort", "xhyn.screenshots")) {
        System.getProperty(key)?.let { systemProperty(key, it) }
    }
}
