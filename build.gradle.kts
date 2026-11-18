plugins {
    id("com.android.application") version "8.9.2"
    id("org.jetbrains.kotlin.android") version "2.2.20"
}
android {
    namespace = "com.infinicanvas"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.infinicanvas"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "0.7.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("io.github.gretchycat.libinfinicanvas:canvas-android:0.1.0")
    implementation("io.github.gretchycat.libinfiniselect:select-android:0.1.0")
    testImplementation("junit:junit:4.13.2")
}
