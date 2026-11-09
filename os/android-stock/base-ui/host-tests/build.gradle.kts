plugins { id("com.android.application") }
android {
    namespace = "com.xhyn.baseuitests"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig { applicationId = "com.xhyn.ivrecomp"; minSdk = 29; targetSdk = 35 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    androidResources {
        additionalParameters += listOf("--stable-ids", file("stock-resource-ids.txt").absolutePath)
    }
    sourceSets["main"].res.srcDir(providers.gradleProperty("stockUiResources").get())
    sourceSets["main"].assets.srcDir(providers.gradleProperty("stockUiAssets").get())
}
dependencies {
    testImplementation(files(providers.gradleProperty("stockUiTestJar").get()))
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
