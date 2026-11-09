pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "XhynStockLayer"
include(":layer")
// Optional harness runs the actual translated base DEX, converted to JVM bytecode.
// No stock classes or assets are committed, and no test code enters the APK.
if (providers.gradleProperty("stockUiTestJar").isPresent) {
    include(":baseUiTests")
    project(":baseUiTests").projectDir = file("base-ui/host-tests")
}
