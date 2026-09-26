# سجل الخبير 0.9.11 — Release Candidate

## إصلاحات هذه الجولة

- رفع وضوح الكتابة في الحقول الموحدة باستخدام لون نص كامل ووزن Medium.
- إصلاح إعادة فتح يوم الأجندة بحيث يعرض النص والمواعيد المحفوظة مباشرة.
- حفظ الموعد اليدوي عند الضغط على «حفظ اليوم» حتى لو لم يُضغط «إضافة الموعد لليوم» أولًا.
- جعل اختيار «المكتب / المحكمة» ظاهرًا كمحدد داخل الأجندة.
- إضافة تاريخ ووقت الجلسة إلى القضية وربطهما تلقائيًا بالأجندة.
- ترقية Room من schema 17 إلى 18 مع MIGRATION_17_18 دون مسح البيانات القديمة.
- توحيد رقم schema بين Room والنسخ الاحتياطي من ثابت واحد لمنع التعارض.
- إبقاء صفحة مراجعة تصوير/استيراد التقرير وإظهار «سيتم الإدراج في: ...» بوضوح.
- إضافة hanging indent للقوائم العربية المرقمة داخل محرر التقرير حتى يصطف السطر الملفوف تحت نص البند.
- إضافة بوابة ميزة «المهام» مخفية حاليًا وتدعم فتحًا زمنيًا وProduct ID للسعر لاحقًا.
- رفع versionCode إلى 102 وversionName إلى 0.9.11 وتحديث اسم النسخة المجمعة في الواجهة.
- تقليل استهلاك CI بجمع الاختبارات والبناء في Job واحد.
- إيقاف Push-trigger مؤقتًا لأن GitHub Actions الحالي يفشل قبل أول Step.
- تحديث Workflows التسليم لتستخدم APK alias ثابت، وإزالة افتراض الحجم القديم >49MB.
- منع الرفع التلقائي للـAPK إلى خدمات خارجية؛ التسليم يبقى داخل GitHub الخاص.

## اختبارات مضافة

- TimedPaidFeatureGateTest
- CaseAgendaEventMapperTest
- ArabicListHangingIndentTransformationTest
- BackupSchemaVersionTest

## حالة البناء

الكود على main، لكن GitHub Actions في الحساب الحالي ينهي الـjob قبل Checkout وبـ steps=[]؛ لذلك لا توجد نتيجة Gradle/Compile جديدة بعد هذه التعديلات. عند عودة Runner يتم تشغيل Android CI يدويًا، والذي ينفذ اختبارات الوحدات ثم :app:assembleCombinedDebug في نفس Job ويصدر expert-record-0.9.11-debug.apk.

## أمر البناء

```bash
./gradlew --no-daemon -PKHABIR_SKIP_SCREENSHOT_TESTS=true \
  :core:testDebugUnitTest \
  :feature-auth:testDebugUnitTest \
  :feature-cases:testDebugUnitTest \
  :feature-notifications:testDebugUnitTest \
  :feature-reports:testDebugUnitTest \
  :feature-agenda:testDebugUnitTest \
  :app:testCombinedDebugUnitTest

./gradlew --no-daemon :app:assembleCombinedDebug
```
