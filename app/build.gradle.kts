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
        versionCode = 100
        versionName = "0.9.10-play-preparation"
        buildConfigField("String", "MONETIZATION_BASE_URL", "\"${providers.gradleProperty("KHABIR_MONETIZATION_BASE_URL").orNull.orEmpty()}\"")
        listOf("PRIVACY_POLICY_URL", "ACCOUNT_DELETION_URL").forEach { key ->
            buildConfigField("String", key, "\"${providers.gradleProperty("KHABIR_$key").orNull.orEmpty()}\"")
        }
        listOf("BANNER", "INTERSTITIAL", "REWARDED").forEach { kind ->
            buildConfigField("String", "ADMOB_${kind}_ID", "\"${providers.gradleProperty("KHABIR_ADMOB_${kind}_ID").orNull.orEmpty()}\"")
        }
        manifestPlaceholders["admobAppId"] = providers.gradleProperty("KHABIR_ADMOB_APP_ID").getOrElse("ca-app-pub-3940256099942544~3347511713")
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
            applicationIdSuffix = ".test.modular"
        }
        create("trial") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".trial"
            isDebuggable = true
            // Runtime/device validation should keep the full Kotlin runtime so AndroidJUnitRunner
            // can start reliably. The distributable hardened APK remains minified below.
            isMinifyEnabled = false
            isShrinkResources = false
            matchingFallbacks += listOf("debug")
            testProguardFiles("proguard-test-rules.pro")
        }
        create("hardened") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".preview.v0910"
            versionNameSuffix = "-preview"
            manifestPlaceholders["appLabel"] = "سجل الخبير — تجربة 0.9.10"
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            matchingFallbacks += listOf("release")
            buildConfigField("String", "TEST_ACTIVATION_CODE", "\"\"")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        release {
            manifestPlaceholders["appLabel"] = "سجل الخبير"
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("String", "TEST_ACTIVATION_CODE", "\"\"")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
            versionCode = 101
            versionName = "1.0.0"
            buildConfigField("String", "MODULE_MODE", "\"COMBINED\"")
            manifestPlaceholders["appLabel"] = "سجل الخبير — النسخة المجمعة"
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
    testOptions.unitTests.all {
        it.testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

tasks.withType<JavaCompile>().configureEach {
    doFirst {
        delete(fileTree(layout.buildDirectory.dir("generated/ksp")) {
            include("**/java/byRounds/**")
        })
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.21")
    implementation("com.android.billingclient:billing-ktx:9.1.0")
    implementation("com.google.android.gms:play-services-ads:25.5.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
    implementation(project(":core"))
    implementation(project(":feature-auth"))
    implementation(project(":feature-cases"))
    implementation(project(":feature-notifications"))
    implementation(project(":feature-reports"))
    implementation(project(":feature-agenda"))
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
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

    val cameraXVersion = "1.4.2"
    implementation("androidx.camera:camera-core:$cameraXVersion")
    implementation("androidx.camera:camera-camera2:$cameraXVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraXVersion")
    implementation("androidx.camera:camera-view:$cameraXVersion")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    implementation("com.google.dagger:hilt-android:2.58")
    ksp("com.google.dagger:hilt-android-compiler:2.58")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    androidTestImplementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.21")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
}


val validatePlayRelease by tasks.registering {
    doLast {
        check(uploadSigningReady) { "A stable upload keystore is required for the Play release." }
        listOf("KHABIR_FIREBASE_API_KEY", "KHABIR_FIREBASE_APP_ID", "KHABIR_FIREBASE_PROJECT_ID", "KHABIR_GOOGLE_WEB_CLIENT_ID", "KHABIR_MONETIZATION_BASE_URL", "KHABIR_PRIVACY_POLICY_URL", "KHABIR_ACCOUNT_DELETION_URL").forEach { key ->
            check(!providers.gradleProperty(key).orNull.isNullOrBlank()) { "Missing release configuration: $key" }
        }
        listOf(
            "KHABIR_MONETIZATION_BASE_URL",
            "KHABIR_PRIVACY_POLICY_URL",
            "KHABIR_ACCOUNT_DELETION_URL"
        ).forEach { key ->
            val value = providers.gradleProperty(key).orNull.orEmpty()
            check(value.startsWith("https://") && value.length > "https://x.y".length) {
                "A public HTTPS URL is required for $key"
            }
        }
        providers.gradleProperty("KHABIR_GEMINI_VISION_ENDPOINT").orNull
            ?.takeIf { it.isNotBlank() }
            ?.let { value -> check(value.startsWith("https://")) { "KHABIR_GEMINI_VISION_ENDPOINT must use HTTPS" } }
        listOf("KHABIR_ADMOB_APP_ID", "KHABIR_ADMOB_BANNER_ID", "KHABIR_ADMOB_INTERSTITIAL_ID", "KHABIR_ADMOB_REWARDED_ID").forEach { key ->
            val value = providers.gradleProperty(key).orNull.orEmpty()
            check(value.startsWith("ca-app-pub-") && !value.contains("3940256099942544")) { "Production AdMob configuration required: $key" }
        }
    }
}
tasks.matching { it.name == "preCombinedReleaseBuild" }.configureEach { dependsOn(validatePlayRelease) }

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
