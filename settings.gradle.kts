pluginManagement {
    resolutionStrategy.eachPlugin {
        if (requested.id.id.startsWith("com.android.")) useModule("com.android.tools.build:gradle:8.9.2")
        if (requested.id.id.startsWith("org.jetbrains.kotlin.")) useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.20")
    }
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "InfiniCanvas"
val dependencyRoot = file("libraries")
check(dependencyRoot.resolve("libinfinicanvas/canvas-android/src/main/java/com/infinifolder/preview/PaletteDocument.kt").isFile) {
    "Run ./build.sh first to initialize the pinned source dependencies."
}
System.setProperty("infini.dependencies", dependencyRoot.canonicalPath)
includeBuild(dependencyRoot.resolve("libinfinistream"))
includeBuild(dependencyRoot.resolve("libinfinicanvas"))
includeBuild(dependencyRoot.resolve("libinfiniselect"))
