# Instrumentation test APK keep rules.
# The test runner is loaded from the manifest/reflection, so R8 can otherwise
# remove Kotlin runtime/test platform classes before AndroidJUnitRunner starts.
-keep class kotlin.** { *; }
-keep class androidx.test.** { *; }
-keep interface androidx.test.** { *; }
-dontwarn kotlin.**
