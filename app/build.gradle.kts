plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.princebooks.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.princebooks.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.3"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
}