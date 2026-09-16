plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("app.cash.paparazzi")
}
android {
    namespace = "com.khabir.core"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        buildConfigField("String", "ACTIVATION_ENDPOINT", "\"" + providers.gradleProperty("KHABIR_ACTIVATION_ENDPOINT").orNull.orEmpty().replace("\"", "\\\"") + "\"")
        buildConfigField("String", "GEMINI_VISION_ENDPOINT", "\"" + providers.gradleProperty("KHABIR_GEMINI_VISION_ENDPOINT").orNull.orEmpty().replace("\"", "\\\"") + "\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"" + providers.gradleProperty("KHABIR_FIREBASE_API_KEY").orNull.orEmpty().replace("\"", "\\\"") + "\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"" + providers.gradleProperty("KHABIR_FIREBASE_APP_ID").orNull.orEmpty().replace("\"", "\\\"") + "\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"" + providers.gradleProperty("KHABIR_FIREBASE_PROJECT_ID").orNull.orEmpty().replace("\"", "\\\"") + "\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"" + providers.gradleProperty("KHABIR_GOOGLE_WEB_CLIENT_ID").orNull.orEmpty().replace("\"", "\\\"") + "\"")
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    testOptions.unitTests.isIncludeAndroidResources = true
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    api(composeBom)
    androidTestImplementation(composeBom)

    api("androidx.core:core-ktx:1.13.1")
    api("androidx.exifinterface:exifinterface:1.3.7")
    api("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    api("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    api("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    api("androidx.activity:activity-compose:1.9.1")
    api("androidx.compose.ui:ui")
    api("androidx.compose.ui:ui-graphics")
    api("androidx.compose.ui:ui-tooling-preview")
    api("androidx.compose.material3:material3")
    api("androidx.compose.material:material-icons-extended")
    api("androidx.navigation:navigation-compose:2.7.7")
    api("androidx.credentials:credentials:1.3.0")
    api("androidx.credentials:credentials-play-services-auth:1.3.0")
    api("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    api("com.google.firebase:firebase-auth:23.0.0")
    api("com.google.firebase:firebase-common:21.0.0")

    // The internal camera is the default. Google Lens is an explicit optional action.
    val cameraXVersion = "1.3.4"
    api("androidx.camera:camera-core:$cameraXVersion")
    api("androidx.camera:camera-camera2:$cameraXVersion")
    api("androidx.camera:camera-lifecycle:$cameraXVersion")
    api("androidx.camera:camera-view:$cameraXVersion")

    // Gemini uses a protected HTTPS gateway configured at build time. The API key
    // remains on the protected gateway and is never packaged inside this app.

    api("androidx.room:room-runtime:2.6.1")
    api("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    api("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")
    api("androidx.hilt:hilt-navigation-compose:1.2.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Tesseract 5 LSTM engine matching the bundled offline Arabic model.
    api("cz.adaptech.tesseract4android:tesseract4android:4.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

