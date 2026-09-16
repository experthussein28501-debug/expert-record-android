plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

val uploadStoreFilePath = providers.gradleProperty("KHABIR_UPLOAD_STORE_FILE").orNull
val uploadStorePassword = providers.gradleProperty("KHABIR_UPLOAD_STORE_PASSWORD").orNull
val uploadKeyAlias = providers.gradleProperty("KHABIR_UPLOAD_KEY_ALIAS").orNull
val uploadKeyPassword = providers.gradleProperty("KHABIR_UPLOAD_KEY_PASSWORD").orNull
val geminiVisionEndpoint = providers.gradleProperty("KHABIR_GEMINI_VISION_ENDPOINT").orNull.orEmpty()
val firebaseApiKey = providers.gradleProperty("KHABIR_FIREBASE_API_KEY").orNull.orEmpty()
val firebaseApplicationId = providers.gradleProperty("KHABIR_FIREBASE_APP_ID").orNull.orEmpty()
val firebaseProjectId = providers.gradleProperty("KHABIR_FIREBASE_PROJECT_ID").orNull.orEmpty()
val googleWebClientId = providers.gradleProperty("KHABIR_GOOGLE_WEB_CLIENT_ID").orNull.orEmpty()
val testActivationCode = providers.gradleProperty("KHABIR_TEST_ACTIVATION_CODE").orNull.orEmpty()
val activationEndpoint = providers.gradleProperty("KHABIR_ACTIVATION_ENDPOINT").orNull.orEmpty()
val uploadSigningReady = listOf(
    uploadStoreFilePath,
    uploadStorePassword,
    uploadKeyAlias,
    uploadKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.khabir.app"
    testBuildType = providers.gradleProperty("KHABIR_TEST_BUILD_TYPE").getOrElse("debug")
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.khabir.app"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        minSdk = 26
        targetSdk = 36
        versionCode = 90
        versionName = "0.9.0"
        buildConfigField("String", "ACTIVATION_ENDPOINT", "\"${activationEndpoint.replace("\"", "\\\"")}\"")
        buildConfigField("String", "GEMINI_VISION_ENDPOINT", "\"${geminiVisionEndpoint.replace("\"", "\\\"")}\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"${firebaseApiKey.replace("\"", "\\\"")}\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"${firebaseApplicationId.replace("\"", "\\\"")}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${firebaseProjectId.replace("\"", "\\\"")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${googleWebClientId.replace("\"", "\\\"")}\"")
        buildConfigField("String", "TEST_ACTIVATION_CODE", "\"${testActivationCode.replace("\"", "\\\"")}\"")
    }

    signingConfigs {
        if (uploadSigningReady) {
            create("upload") {
                storeFile = file(requireNotNull(uploadStoreFilePath))
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            // A separate install identity prevents signature/version conflicts with
            // any older test APK already present on the user's phone.
            applicationIdSuffix = ".test.modular"
        }
        create("trial") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".trial"
            isDebuggable = true
            isMinifyEnabled = true
            isShrinkResources = true
            matchingFallbacks += listOf("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        release {
            isMinifyEnabled = false
            if (uploadSigningReady) signingConfig = signingConfigs.getByName("upload")
        }
    }

    flavorDimensions += "module"
    productFlavors {
        create("archive") {
            dimension = "module"
            applicationIdSuffix = ".archive"
            versionNameSuffix = "-archive"
            buildConfigField("String", "MODULE_MODE", "\"ARCHIVE\"")
            manifestPlaceholders["appLabel"] = "سجل الخبير — المرحلة الأولى"
        }
        create("combined") {
            dimension = "module"
            applicationIdSuffix = ".combined"
            versionNameSuffix = "-combined"
            buildConfigField("String", "MODULE_MODE", "\"COMBINED\"")
            manifestPlaceholders["appLabel"] = "سجل الخبير — النسخة المجمعة 0.9.0"
        }
        create("notifications") {
            dimension = "module"
            applicationIdSuffix = ".notifications"
            versionNameSuffix = "-notifications"
            buildConfigField("String", "MODULE_MODE", "\"NOTIFICATIONS\"")
            manifestPlaceholders["appLabel"] = "خبير — الإخطارات"
        }
        create("reports") {
            dimension = "module"
            applicationIdSuffix = ".reports"
            versionNameSuffix = "-reports"
            buildConfigField("String", "MODULE_MODE", "\"REPORTS\"")
            manifestPlaceholders["appLabel"] = "خبير — التقارير 0.8.2"
        }
        create("sirkis") {
            dimension = "module"
            applicationIdSuffix = ".sirkis"
            versionNameSuffix = "-sirkis"
            buildConfigField("String", "MODULE_MODE", "\"SIRKIS\"")
            manifestPlaceholders["appLabel"] = "خبير — سركي الإخطارات"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions.unitTests.all {
        it.testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

// KSP can retain its internal byRounds snapshots beside the final generated
// sources on a fresh build. They contain the same Java classes, so exclude the
// snapshots before javac to keep clean and CI builds deterministic.
tasks.withType<JavaCompile>().configureEach {
    doFirst {
        delete(fileTree(layout.buildDirectory.dir("generated/ksp")) {
            include("**/java/byRounds/**")
        })
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":feature-auth"))
    implementation(project(":feature-cases"))
    implementation(project(":feature-notifications"))
    implementation(project(":feature-reports"))
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.google.firebase:firebase-auth:23.0.0")
    implementation("com.google.firebase:firebase-common:21.0.0")

    // The internal camera is the default. Google Lens is an explicit optional action.
    val cameraXVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraXVersion")
    implementation("androidx.camera:camera-camera2:$cameraXVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraXVersion")
    implementation("androidx.camera:camera-view:$cameraXVersion")

    // Gemini uses a protected HTTPS gateway configured at build time. The API key
    // remains on the protected gateway and is never packaged inside this app.

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Tesseract 5 LSTM engine matching the bundled offline Arabic model.
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

