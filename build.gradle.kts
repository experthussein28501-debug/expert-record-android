plugins {
    id("com.android.library") version "8.13.0" apply false
    id("com.android.application") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
    // اختبارات بصرية (screenshot tests) بدون الحاجة لجهاز/محاكي — تعمل فوق Robolectric
    // الموجود بالفعل في core. راجع core/src/test/.../screenshot لأمثلة الاستخدام.
    id("app.cash.paparazzi") version "2.0.0-alpha01" apply false
}
