plugins {
    id("com.android.library") version "8.13.2" apply false
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    id("com.google.dagger.hilt.android") version "2.58" apply false
    id("com.google.devtools.ksp") version "2.3.9" apply false
    // اختبارات بصرية (screenshot tests) بدون الحاجة لجهاز/محاكي — تعمل فوق Robolectric
    // الموجود بالفعل في core. راجع core/src/test/.../screenshot لأمثلة الاستخدام.
    id("app.cash.paparazzi") version "2.0.0-alpha05" apply false
}
