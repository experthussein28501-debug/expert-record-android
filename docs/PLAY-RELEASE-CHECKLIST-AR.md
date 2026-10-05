# تجهيز سجل الخبير للنشر على Google Play

## هوية نسخة Play الحالية

- الحزمة: `com.khabir.app.combined`
- Version Name: `0.9.17`
- Version Code: `107`
- Min SDK: 26
- Target SDK: 36
- صيغة النشر: Android App Bundle (AAB)
- اسم التطبيق في Release: **سجل الخبير**
- R8/Resource shrinking: مفعّل
- Debuggable: مغلق
- Android Backup: مغلق
- Cleartext HTTP: مغلق
- 16KB native page alignment: بوابة تحقق آلية مطلوبة قبل التسليم

> حالة 0.9.17: هذه إعدادات ومتطلبات الفحص، وليست شهادة بنجاح البناء. نسخة Preview منفصلة عن نسخة Play؛ يلزم توقيع إنتاج ثابت وإعداد الخدمات والروابط الحقيقية قبل إصدار AAB صالح للنشر.

## ما يفحصه CI قبل السماح بالنسخة

1. اختبارات Backend وnpm audit.
2. اختبارات الأمان البرمجية.
3. اختبارات Unit/Functional لكل الوحدات.
4. Android Lint على `combinedRelease`.
5. بناء APK Release موقّع بمفتاح CI مؤقت.
6. بناء AAB Release.
7. فحص package/version/targetSdk/app label.
8. منع package تجريبي مثل preview/trial/test/debug.
9. منع الصلاحيات عالية الخطورة غير المعتمدة.
10. فحص exported components.
11. فحص R8 وعدم تضمين keystore أو mapping داخل الحزمة.
12. فحص apksigner وzipalign و16KB ELF alignment.
13. فحص توقيع AAB بواسطة jarsigner.
14. اختبار Android 14 + OCR العربي + الأجندة في المحاكي.

> مفتاح CI المؤقت خاص بالاختبار فقط ولا يصلح للرفع إلى Play.

## GitHub Secrets المطلوبة للنشر الحقيقي

- `KHABIR_KEYSTORE_BASE64`
- `KHABIR_UPLOAD_STORE_PASSWORD`
- `KHABIR_UPLOAD_KEY_ALIAS`
- `KHABIR_UPLOAD_KEY_PASSWORD`
- `KHABIR_FIREBASE_API_KEY`
- `KHABIR_FIREBASE_APP_ID`
- `KHABIR_FIREBASE_PROJECT_ID`
- `KHABIR_GOOGLE_WEB_CLIENT_ID`
- `KHABIR_MONETIZATION_BASE_URL`
- `KHABIR_PRIVACY_POLICY_URL`
- `KHABIR_ACCOUNT_DELETION_URL`
- `KHABIR_ADMOB_APP_ID`
- `KHABIR_ADMOB_BANNER_ID`
- `KHABIR_ADMOB_INTERSTITIAL_ID`
- `KHABIR_ADMOB_REWARDED_ID`
- `KHABIR_GEMINI_VISION_ENDPOINT` إذا كانت بوابة Gemini المركزية مفعلة

كل روابط الإنتاج السابقة يجب أن تكون HTTPS، ولا تُخزن مفاتيح سرية داخل المستودع.

## إعداد Backend المطلوب قبل الإطلاق

- `PLAY_PACKAGE_NAME=com.khabir.app.combined`
- `PLAY_LAUNCH_AT` بتاريخ الإطلاق الحقيقي.
- `ADS_ENABLED=true` فقط عند الرغبة في تفعيل الإعلانات.
- `ADMOB_REWARDED_UNITS` بوحدات الإعلان الحقيقية.
- Service Account مخوّل لقراءة اشتراكات Google Play عبر Android Publisher API.
- Pub/Sub لإشعارات Google Play Billing إذا استُخدم `playNotifications`.

## Play Billing

- Product ID: `khabir_ad_free`
- Base plans:
  - `monthly`
  - `quarterly`
  - `halfyear`
  - `annual`

السعر النهائي يُضبط من Play Console ويُعرض للمستخدم من ProductDetails؛ لا تعتمد على الأرقام المحلية داخل الكود كأسعار متجر نهائية.

## Account deletion

داخل التطبيق يوجد:
- حذف مباشر للحساب عبر backend.
- حذف Firebase Auth.
- حذف reward sessions / reward transactions / play purchases / entitlement state.
- تسجيل خروج بعد نجاح الحذف.
- رابط خارجي `ACCOUNT_DELETION_URL` لطلب الحذف من الويب.

صفحة الحذف الخارجية يجب أن تكون عامة، تعمل بدون تثبيت التطبيق، وتذكر اسم التطبيق/المطور وتوفر وسيلة فعلية لطلب الحذف.

## Store listing / App content

قبل الإرسال للمراجعة يجب إكمال:

- Privacy Policy URL عام.
- Account deletion URL عام.
- Data Safety form مطابق للمسودة في `DATA-SAFETY-DRAFT-AR.md`.
- إقرار أن التطبيق يحتوي إعلانات إذا كانت AdMob مفعلة.
- Content rating questionnaire.
- Target audience المناسب.
- App access instructions لأن تسجيل Google مطلوب في Release.
- وصف قصير وطويل للتطبيق.
- بريد/وسيلة دعم عامة.
- أيقونة متجر 512×512.
- Feature Graphic 1024×500.
- Screenshots هاتف واضحة للصفحات الأساسية.
- Screenshots تابلت إذا تم استهداف التابلت في Store listing.

## خطة الإطلاق المقترحة

1. تشغيل Android CI حتى تنجح جميع jobs بما فيها `play_preflight`.
2. ضبط GitHub Secrets الحقيقية.
3. تشغيل workflow: **Android Release**.
4. أخذ AAB من artifact `expert-record-combined-release`.
5. إنشاء التطبيق في Play Console بالحزمة `com.khabir.app.combined`.
6. تفعيل Play App Signing واستخدام Upload Key الثابت.
7. رفع AAB إلى **Internal testing** أولًا.
8. اختبار تثبيت/تحديث النسخة من Google Play نفسه، وتسجيل Google، الاشتراك، الاستعادة، الحذف، الإعلانات، الكاميرا/OCR والتقارير.
9. الانتقال إلى Closed/Open testing حسب احتياج الحساب ثم Production.

## نقاط لا يجوز تغييرها بعد أول نشر دون خطة ترحيل

- Application ID / package name.
- App signing key في Play App Signing.
- Product ID للاشتراك إذا كان مستخدمًا فعليًا.
- معنى versionCode: يجب زيادته مع كل إصدار جديد.
