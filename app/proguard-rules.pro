# Keep Kotlin runtime entry points used by instrumentation and reflective callers.
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
# Keep the offline OCR service contract available to the optimized runtime test.
-keep class com.khabir.app.data.ocr.** { *; }
