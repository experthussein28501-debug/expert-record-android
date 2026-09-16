plugins {
    id("com.android.library") version "8.13.0" apply false
    id("com.android.application") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
    // اختبارات بصرية (screenshot tests) بدون الحاجة لجهاز/محاكي — تعمل فوق Robolectric
    // الموجود بالفعل في core. راجع core/src/test/.../screenshot لأمثلة الاستخدام.
    id("app.cash.paparazzi") version "1.3.5" apply false
}
