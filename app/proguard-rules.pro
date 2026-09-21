# Native OCR JNI entry points must retain their names.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
# OCR instrumentation exercises this public service on optimized builds.
-keep class com.khabir.app.data.ocr.ArabicPetitionOcrService { public *; }
-keep class com.khabir.app.data.ocr.ArabicPetitionOcrService$Result** { *; }
# Other app classes remain eligible for R8 obfuscation and shrinking.
