plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}
android {
    namespace = "com.khabir.notifications"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.khabir.notifications.standalone"
        minSdk = 26
        targetSdk = 36
        versionCode = 74
        versionName = "0.7.4-notifications"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    sourceSets["main"].assets.srcDir("../app/src/main/assets")
}
dependencies {
    implementation("com.google.dagger:hilt-android:2.51.1")
    implementation(project(":core"))
    implementation(project(":feature-notifications"))
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")
}

// Same KSP snapshot exclusion as the integration app.
tasks.withType<JavaCompile>().configureEach {
    doFirst {
        delete(fileTree(layout.buildDirectory.dir("generated/ksp")) { include("**/java/byRounds/**") })
    }
}
