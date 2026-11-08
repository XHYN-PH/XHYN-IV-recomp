plugins { id("com.android.application") }
android {
    namespace = "com.xhyn.stock"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "com.xhyn.ivrecomp"
        minSdk = 29
        targetSdk = 35
        versionCode = 443
        versionName = "0.9.24-xhyn-layer-test1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    sourceSets["debug"].assets.srcDir("src/test/assets")
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
